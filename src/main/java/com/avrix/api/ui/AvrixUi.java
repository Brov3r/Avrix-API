package com.avrix.api.ui;

import com.lixui.animation.Animator;
import com.lixui.backend.opengl.OpenGlLayer;
import com.lixui.glfw.GlfwInput;
import com.lixui.input.InputState;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontStyle;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjglx.opengl.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.core.opengl.RenderThread;
import zombie.network.GameServer;

import java.nio.DoubleBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Client-side bridge between Project Zomboid and the LixUI retained UI framework.
 * <p>
 * The overlay owns a LixUI {@link WidgetManager}, feeds it GLFW input and draws it over the
 * finished game frame. All UI state is confined to the render thread. Code that runs on the game
 * thread can use {@link #run(Runnable)} or the convenience methods to schedule work on the render
 * thread safely.
 * <p>
 * The overlay is available on clients only. Every operation is a no-op on a dedicated server.
 */
public final class AvrixUi {
    private static final Logger LOGGER = LoggerFactory.getLogger(AvrixUi.class);
    private static final float DEFAULT_FONT_SIZE = 16.0f;
    private static final float MAX_FRAME_SECONDS = 0.25f;
    private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();
    private static final PzUiPlatform PLATFORM = new PzUiPlatform();

    private static InputState input;
    private static OpenGlLayer backend;
    private static WidgetManager manager;
    private static GlfwInput inputAdapter;
    private static long inputWindow;
    private static long lastFrameNanos;
    private static int lastFramebufferWidth;
    private static int lastFramebufferHeight;
    private static int lastLogicalWidth;
    private static int lastLogicalHeight;
    private static boolean initialized;
    private static boolean failed;
    private static boolean closed;
    private static volatile boolean pointerBlocked;
    private static volatile boolean keyboardCaptured;

    /**
     * Prevents instantiation of the static UI facade.
     *
     * @throws UnsupportedOperationException always
     */
    private AvrixUi() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    /**
     * Reports whether the overlay has been initialized on the render thread.
     *
     * @return true when the overlay is ready to accept widgets and draw frames
     */
    public static boolean isReady() {
        return initialized && !failed && !closed;
    }

    /**
     * Registers a top-level widget.
     * <p>
     * The widget is attached on the render thread. The method can be called from any thread.
     *
     * @param widget widget to register
     * @param <T>    concrete widget type
     * @return the registered widget
     * @throws NullPointerException if {@code widget} is null
     */
    public static <T extends Widget> T add(T widget) {
        Objects.requireNonNull(widget, "widget");
        run(() -> manager().add(widget));
        return widget;
    }

    /**
     * Removes a top-level widget.
     * <p>
     * The widget is detached on the render thread. The method can be called from any thread.
     *
     * @param widget widget to remove
     * @throws NullPointerException if {@code widget} is null
     */
    public static void remove(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        run(() -> manager().remove(widget));
    }

    /**
     * Replaces the font used by widgets without an explicit font.
     *
     * @param font default font
     * @throws NullPointerException if {@code font} is null
     */
    public static void setDefaultFont(Font font) {
        Objects.requireNonNull(font, "font");
        run(() -> manager().setDefaultFont(font));
    }

    /**
     * Replaces the theme used by attached widgets.
     *
     * @param theme new theme
     * @throws NullPointerException if {@code theme} is null
     */
    public static void setTheme(Theme theme) {
        Objects.requireNonNull(theme, "theme");
        run(() -> manager().setTheme(theme));
    }

    /**
     * Rasterizes a font on the render thread and passes it to a callback.
     * <p>
     * The callback runs on the render thread, so the font can be assigned to widgets immediately.
     *
     * @param data      TrueType or OpenType font data
     * @param pixelSize requested font size in logical UI units
     * @param consumer  callback that receives the created font
     * @throws NullPointerException if an argument is null
     */
    public static void createFont(FontData data, float pixelSize, Consumer<Font> consumer) {
        createFont(data, pixelSize, FontStyle.REGULAR, consumer);
    }

    /**
     * Rasterizes a styled font on the render thread and passes it to a callback.
     * <p>
     * The callback runs on the render thread, so the font can be assigned to widgets immediately.
     *
     * @param data      TrueType or OpenType font data
     * @param pixelSize requested font size in logical UI units
     * @param style     synthetic bold or italic style
     * @param consumer  callback that receives the created font
     * @throws NullPointerException if an argument is null
     */
    public static void createFont(FontData data, float pixelSize, FontStyle style, Consumer<Font> consumer) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(consumer, "consumer");
        run(() -> consumer.accept(manager().context().createFont(data, pixelSize, style)));
    }

    /**
     * Runs an action with exclusive access to the overlay.
     * <p>
     * Actions are queued and executed at the beginning of the next render-thread frame. This keeps
     * widget mutation out of update and draw callbacks while remaining safe from any thread.
     *
     * @param action action to execute
     * @throws NullPointerException if {@code action} is null
     */
    public static void run(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (GameServer.server) {
            LOGGER.debug("Avrix UI is client-only; ignoring a scheduled operation on the server");
            return;
        }
        if (closed || failed) {
            LOGGER.debug("Avrix UI is unavailable; ignoring a scheduled operation");
            return;
        }
        PENDING.add(action);
    }

    /**
     * Returns the live widget manager.
     * <p>
     * The manager is single-threaded. Call this method only on the render thread; use
     * {@link #run(Runnable)} to reach it from any other thread.
     *
     * @return the active widget manager
     * @throws IllegalStateException if called outside the render thread or before initialization
     */
    public static WidgetManager manager() {
        if (!isRenderThread()) {
            throw new IllegalStateException("Use AvrixUi.run to access the widget manager from the game thread.");
        }
        if (!isReady()) {
            throw new IllegalStateException("Avrix UI is not initialized.");
        }
        return manager;
    }

    /**
     * Returns the shared LixUI context with theme, fonts, textures, animations and input state.
     * <p>
     * The context is single-threaded. Call this method only on the render thread; use
     * {@link #run(Runnable)} to reach it from any other thread.
     *
     * @return the active UI context
     * @throws IllegalStateException if called outside the render thread or before initialization
     */
    public static UiContext context() {
        return manager().context();
    }

    /**
     * Returns the drawing manager for raw primitives, text, images and transforms.
     * <p>
     * The renderer is single-threaded and is only valid while the overlay is drawing. Use a
     * {@link RawDrawWidget} to access it safely from the normal render pass.
     *
     * @return the active drawing manager
     * @throws IllegalStateException if called outside the render thread or before initialization
     */
    public static RenderManager renderer() {
        return manager().renderer();
    }

    /**
     * Returns the shared animator advanced once per overlay frame.
     * <p>
     * The animator is single-threaded. Call this method only on the render thread; use
     * {@link #run(Runnable)} to reach it from any other thread.
     *
     * @return the shared animator
     * @throws IllegalStateException if called outside the render thread or before initialization
     */
    public static Animator animator() {
        return manager().animator();
    }

    /**
     * Reports whether the overlay currently owns the pointer.
     * <p>
     * The value describes the last completed overlay frame and is safe to read from the game thread.
     *
     * @return true when a visible widget or an active pointer capture would handle a click
     */
    public static boolean blocksPointer() {
        return pointerBlocked;
    }

    /**
     * Reports whether an overlay widget holds keyboard focus.
     * <p>
     * The value describes the last completed overlay frame and is safe to read from the game thread.
     *
     * @return true when game keyboard input should be suppressed
     */
    public static boolean isKeyboardCaptured() {
        return keyboardCaptured;
    }

    /**
     * Advances and draws the overlay for the current frame.
     * <p>
     * This method is called by the client rendering mixin on the render thread and must not be
     * invoked by plugins.
     */
    public static void frame() {
        if (closed || failed || GameServer.server) {
            return;
        }
        if (!initialized && !initialize()) {
            return;
        }
        try {
            runPending();
            installInput();
            if (!resize()) {
                return;
            }
            float deltaSeconds = nextDeltaSeconds();
            manager.update(deltaSeconds);
            if (manager.widgets().isEmpty()) {
                pointerBlocked = false;
                keyboardCaptured = false;
                return;
            }
            draw();
            updateInteractionState();
        } catch (RuntimeException | Error exception) {
            fail("frame processing", exception);
        }
    }

    /**
     * Releases render resources before the game destroys its window.
     * <p>
     * This method is called by the client display mixin on the render thread and must not be
     * invoked by plugins.
     */
    public static void beforeDisplayRecreate() {
        closeInput();
        if (!initialized) {
            return;
        }
        LOGGER.warn("Display was recreated; resetting the LixUI overlay and its widgets");
        closeResources();
        initialized = false;
        lastFrameNanos = 0L;
        resetViewport();
        pointerBlocked = false;
        keyboardCaptured = false;
    }

    /**
     * Releases every overlay resource while the OpenGL context is still current.
     * <p>
     * This method is called by the client display mixin during shutdown and must not be invoked by
     * plugins.
     */
    public static void shutdown() {
        if (closed) {
            return;
        }
        // Display.destroy() is also part of the game's display fallback path, which runs before
        // the overlay ever initializes. In that case there is nothing to release and the overlay
        // must remain available for the recreated window.
        if (!initialized && manager == null && backend == null && inputAdapter == null) {
            return;
        }
        closed = true;
        closeResources();
        PLATFORM.close();
        PENDING.clear();
        pointerBlocked = false;
        keyboardCaptured = false;
        LOGGER.info("LixUI overlay shut down");
    }

    private static boolean initialize() {
        try {
            input = new InputState();
            backend = new OpenGlLayer();
            manager = new WidgetManager(input, backend, PLATFORM);
            loadDefaultFont(manager);
            initialized = true;
            lastFrameNanos = 0L;
            resetViewport();
            LOGGER.info("LixUI overlay initialized");
            return true;
        } catch (RuntimeException | Error exception) {
            fail("initialization", exception);
            return false;
        }
    }

    private static void loadDefaultFont(WidgetManager widgetManager) {
        Optional<FontData> fontData = SystemFonts.findDefault();
        if (fontData.isEmpty()) {
            LOGGER.warn("No system TrueType font was found; widgets need an explicit font");
            return;
        }
        try {
            widgetManager.setDefaultFont(widgetManager.context().createFont(fontData.get(), DEFAULT_FONT_SIZE));
        } catch (RuntimeException | Error exception) {
            LOGGER.warn("Failed to create the default LixUI font", exception);
        }
    }

    private static void runPending() {
        Runnable action;
        while ((action = PENDING.poll()) != null) {
            try {
                action.run();
            } catch (RuntimeException | Error exception) {
                LOGGER.error("A scheduled Avrix UI operation failed", exception);
            }
        }
    }

    private static void installInput() {
        long window = Display.getWindow();
        if (window == MemoryUtil.NULL) {
            return;
        }
        if (inputAdapter != null && inputWindow == window) {
            return;
        }
        if (inputAdapter != null) {
            // The window was replaced without the pre-recreate hook. Calling GLFW on the old handle
            // would be unsafe, so the stale adapters are dropped without touching the new window.
            LOGGER.warn("The game window changed unexpectedly; reinstalling the LixUI input adapter");
            inputAdapter = null;
        }
        inputAdapter = GlfwInput.install(window, input);
        inputWindow = window;
    }

    private static void resetViewport() {
        lastFramebufferWidth = 0;
        lastFramebufferHeight = 0;
        lastLogicalWidth = 0;
        lastLogicalHeight = 0;
    }

    private static boolean resize() {
        int logicalWidth = Display.getWidth();
        int logicalHeight = Display.getHeight();
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            return false;
        }
        int framebufferWidth = Display.getFramebufferWidth();
        int framebufferHeight = Display.getFramebufferHeight();
        if (framebufferWidth <= 0) {
            framebufferWidth = logicalWidth;
        }
        if (framebufferHeight <= 0) {
            framebufferHeight = logicalHeight;
        }
        if (framebufferWidth == lastFramebufferWidth
                && framebufferHeight == lastFramebufferHeight
                && logicalWidth == lastLogicalWidth
                && logicalHeight == lastLogicalHeight) {
            return true;
        }
        lastFramebufferWidth = framebufferWidth;
        lastFramebufferHeight = framebufferHeight;
        lastLogicalWidth = logicalWidth;
        lastLogicalHeight = logicalHeight;
        manager.resize(new Viewport(framebufferWidth, framebufferHeight, logicalWidth, logicalHeight));
        return true;
    }

    private static float nextDeltaSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0.0f;
        }
        float deltaSeconds = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        return Math.min(deltaSeconds, MAX_FRAME_SECONDS);
    }

    private static void draw() {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            manager.render();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    private static void updateInteractionState() {
        keyboardCaptured = manager.focused() != null;
        pointerBlocked = pointerOverUi();
    }

    private static boolean pointerOverUi() {
        long window = Display.getWindow();
        if (window == MemoryUtil.NULL) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window, x, y);
            return manager.blocksPointer((float) x.get(0), (float) y.get(0));
        }
    }

    private static void closeInput() {
        GlfwInput adapter = inputAdapter;
        inputAdapter = null;
        inputWindow = 0L;
        if (adapter == null) {
            return;
        }
        try {
            adapter.close();
        } catch (RuntimeException | Error exception) {
            LOGGER.warn("Failed to close the LixUI input adapter", exception);
        }
    }

    private static void closeResources() {
        closeInput();
        WidgetManager currentManager = manager;
        manager = null;
        if (currentManager != null) {
            try {
                currentManager.close();
            } catch (RuntimeException | Error exception) {
                LOGGER.warn("Failed to close the LixUI widget manager", exception);
            }
        }
        OpenGlLayer currentBackend = backend;
        backend = null;
        if (currentBackend != null) {
            try {
                currentBackend.close();
            } catch (RuntimeException | Error exception) {
                LOGGER.warn("Failed to close the LixUI render backend", exception);
            }
        }
        input = null;
    }

    private static void fail(String stage, Throwable cause) {
        failed = true;
        LOGGER.error("LixUI overlay failed during {} and has been disabled", stage, cause);
        closeResources();
        PENDING.clear();
        pointerBlocked = false;
        keyboardCaptured = false;
    }

    private static boolean isRenderThread() {
        return Thread.currentThread() == RenderThread.renderThread;
    }
}
