package com.avrix.api.ui;

import com.lixui.render.RenderManager;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.Widget;

import java.util.Objects;

/**
 * A viewport-sized widget that delegates drawing to a callback.
 * <p>
 * The callback runs on the render thread and draws in local widget coordinates, so the whole
 * {@link RenderManager} surface is available: primitives, text, images, masks, clipping and
 * transforms. The {@link FrameInfo} argument carries elapsed time for frame-based animation.
 * <p>
 * The widget is pointer-transparent by default. Subclasses can override {@link #hitTest} to build
 * interactive canvases. Use {@link #setFillViewport(boolean)} and {@link #setBounds} to limit the
 * canvas to a region.
 */
public class RawDrawWidget extends Widget {
    private final RawDraw draw;

    /**
     * Creates a full-viewport raw drawing surface.
     *
     * @param draw drawing callback invoked once per rendered frame
     * @throws NullPointerException if {@code draw} is null
     */
    public RawDrawWidget(RawDraw draw) {
        this.draw = Objects.requireNonNull(draw, "draw");
        setFillViewport(true);
    }

    /**
     * Draws the custom content for the current frame.
     *
     * @param renderer drawing manager for the current frame
     * @param frame    timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        draw.draw(renderer, frame);
    }

    /**
     * Keeps the raw drawing surface transparent to pointer input.
     *
     * @param x horizontal coordinate relative to the widget
     * @param y vertical coordinate relative to the widget
     * @return always null
     */
    @Override
    public Widget hitTest(float x, float y) {
        return null;
    }

    /**
     * Receives the LixUI drawing manager and timing information for one frame.
     */
    @FunctionalInterface
    public interface RawDraw {
        /**
         * Draws custom content in local widget coordinates.
         *
         * @param renderer drawing manager for the current frame
         * @param frame    timing information for the current frame
         */
        void draw(RenderManager renderer, FrameInfo frame);
    }
}
