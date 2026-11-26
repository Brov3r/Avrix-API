package com.avrix.api.ui;

import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjglx.opengl.Display;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * LixUI platform adapter backed by the Project Zomboid GLFW window.
 * <p>
 * The adapter resolves the current window handle on every call, so it survives display
 * recreation. Standard cursors are cached and released together with the overlay.
 */
final class PzUiPlatform implements UiPlatform {
    private final Map<Cursor, Long> cursors = new EnumMap<>(Cursor.class);

    /**
     * Creates an adapter for the game window.
     */
    PzUiPlatform() {
    }

    /**
     * Returns the text currently stored in the system clipboard.
     *
     * @return the clipboard text, or an empty string when the window or clipboard is unavailable
     */
    @Override
    public String clipboardText() {
        long window = Display.getWindow();
        if (window == MemoryUtil.NULL) {
            return "";
        }
        String text = GLFW.glfwGetClipboardString(window);
        return text == null ? "" : text;
    }

    /**
     * Replaces the system clipboard text.
     *
     * @param text text to copy
     * @throws NullPointerException if {@code text} is null
     */
    @Override
    public void setClipboardText(String text) {
        Objects.requireNonNull(text, "text");
        long window = Display.getWindow();
        if (window != MemoryUtil.NULL) {
            GLFW.glfwSetClipboardString(window, text);
        }
    }

    /**
     * Changes the window cursor to the requested shape.
     *
     * @param cursor requested cursor
     * @throws NullPointerException if {@code cursor} is null
     */
    @Override
    public void setCursor(Cursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        long window = Display.getWindow();
        if (window == MemoryUtil.NULL) {
            return;
        }
        long handle = cursorHandle(cursor);
        if (handle != MemoryUtil.NULL) {
            GLFW.glfwSetCursor(window, handle);
        }
    }

    /**
     * Releases every standard cursor created by this adapter.
     */
    void close() {
        for (long cursor : cursors.values()) {
            if (cursor != MemoryUtil.NULL) {
                GLFW.glfwDestroyCursor(cursor);
            }
        }
        cursors.clear();
    }

    private long cursorHandle(Cursor cursor) {
        Long existing = cursors.get(cursor);
        if (existing != null) {
            return existing;
        }
        long created = GLFW.glfwCreateStandardCursor(shape(cursor));
        if (created != MemoryUtil.NULL) {
            cursors.put(cursor, created);
        }
        return created;
    }

    private static int shape(Cursor cursor) {
        return switch (cursor) {
            case DEFAULT -> GLFW.GLFW_ARROW_CURSOR;
            case POINTER -> GLFW.GLFW_POINTING_HAND_CURSOR;
            case TEXT -> GLFW.GLFW_IBEAM_CURSOR;
            case CROSSHAIR -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case MOVE -> GLFW.GLFW_RESIZE_ALL_CURSOR;
            case RESIZE_HORIZONTAL -> GLFW.GLFW_RESIZE_EW_CURSOR;
            case RESIZE_VERTICAL -> GLFW.GLFW_RESIZE_NS_CURSOR;
            case RESIZE_DIAGONAL -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
            case RESIZE_DIAGONAL_INVERSE -> GLFW.GLFW_RESIZE_NESW_CURSOR;
            case NOT_ALLOWED -> GLFW.GLFW_NOT_ALLOWED_CURSOR;
        };
    }
}
