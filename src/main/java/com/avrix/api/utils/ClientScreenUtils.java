package com.avrix.api.utils;

import zombie.core.Core;
import zombie.iso.IsoCamera;
import zombie.iso.IsoObject;
import zombie.iso.IsoUtils;
import zombie.iso.PlayerCamera;

import java.util.Objects;

/**
 * Client-side helpers for screen geometry and world-to-screen coordinate conversion.
 * <p>
 * Methods that take a player index support split-screen viewports. Calling these helpers
 * before the game display and camera are initialized raises an {@link IllegalStateException}.
 */
public final class ClientScreenUtils {

    private ClientScreenUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the current game window width.
     *
     * @return window width in pixels
     */
    public static int getScreenWidth() {
        return Core.getInstance().getScreenWidth();
    }

    /**
     * Returns the current game window height.
     *
     * @return window height in pixels
     */
    public static int getScreenHeight() {
        return Core.getInstance().getScreenHeight();
    }

    /**
     * Returns the current game window aspect ratio.
     *
     * @return width-to-height ratio, or {@code 0} when the height is not available
     */
    public static float getAspectRatio() {
        int height = getScreenHeight();
        return height > 0 ? (float) getScreenWidth() / height : 0.0f;
    }

    /**
     * Returns the left edge of a player viewport.
     *
     * @param playerIndex player index (0-3)
     * @return viewport left coordinate in pixels
     * @throws IllegalArgumentException if the player index is out of range
     */
    public static int getViewportLeft(int playerIndex) {
        validatePlayerIndex(playerIndex);
        return IsoCamera.getScreenLeft(playerIndex);
    }

    /**
     * Returns the top edge of a player viewport.
     *
     * @param playerIndex player index (0-3)
     * @return viewport top coordinate in pixels
     * @throws IllegalArgumentException if the player index is out of range
     */
    public static int getViewportTop(int playerIndex) {
        validatePlayerIndex(playerIndex);
        return IsoCamera.getScreenTop(playerIndex);
    }

    /**
     * Returns the width of a player viewport.
     *
     * @param playerIndex player index (0-3)
     * @return viewport width in pixels
     * @throws IllegalArgumentException if the player index is out of range
     */
    public static int getViewportWidth(int playerIndex) {
        validatePlayerIndex(playerIndex);
        return IsoCamera.getScreenWidth(playerIndex);
    }

    /**
     * Returns the height of a player viewport.
     *
     * @param playerIndex player index (0-3)
     * @return viewport height in pixels
     * @throws IllegalArgumentException if the player index is out of range
     */
    public static int getViewportHeight(int playerIndex) {
        validatePlayerIndex(playerIndex);
        return IsoCamera.getScreenHeight(playerIndex);
    }

    /**
     * Returns the current camera zoom for a player.
     *
     * @param playerIndex player index (0-3)
     * @return positive zoom multiplier
     * @throws IllegalArgumentException if the player index is out of range
     * @throws IllegalStateException    if the player camera is not initialized
     */
    public static float getZoom(int playerIndex) {
        return resolveZoom(camera(playerIndex));
    }

    /**
     * Converts a world position to screen coordinates.
     *
     * @param worldX      world X coordinate
     * @param worldY      world Y coordinate
     * @param worldZ      world Z coordinate (floor)
     * @param playerIndex player index (0-3)
     * @return screen position in pixels
     * @throws IllegalArgumentException if the player index is out of range
     * @throws IllegalStateException    if the player camera is not initialized
     */
    public static ScreenPoint worldToScreen(float worldX, float worldY, float worldZ, int playerIndex) {
        PlayerCamera camera = camera(playerIndex);
        float zoom = resolveZoom(camera);
        float screenX = IsoCamera.getScreenLeft(playerIndex)
                + ((IsoUtils.XToScreen(worldX, worldY, worldZ, 0) - camera.getOffX()) / zoom);
        float screenY = IsoCamera.getScreenTop(playerIndex)
                + ((IsoUtils.YToScreen(worldX, worldY, worldZ, 0) - camera.getOffY()) / zoom);
        return new ScreenPoint(screenX, screenY);
    }

    /**
     * Converts the position of a world object to screen coordinates.
     *
     * @param object      world object
     * @param playerIndex player index (0-3)
     * @return screen position in pixels
     * @throws NullPointerException     if {@code object} is {@code null}
     * @throws IllegalArgumentException if the player index is out of range
     * @throws IllegalStateException    if the player camera is not initialized
     */
    public static ScreenPoint worldToScreen(IsoObject object, int playerIndex) {
        Objects.requireNonNull(object, "object");
        return worldToScreen(object.getX(), object.getY(), object.getZ(), playerIndex);
    }

    /**
     * Converts the horizontal position of a world position to a screen X coordinate.
     *
     * @param worldX      world X coordinate
     * @param worldY      world Y coordinate
     * @param worldZ      world Z coordinate (floor)
     * @param playerIndex player index (0-3)
     * @return screen X coordinate in pixels
     */
    public static float worldToScreenX(float worldX, float worldY, float worldZ, int playerIndex) {
        return worldToScreen(worldX, worldY, worldZ, playerIndex).x();
    }

    /**
     * Converts the vertical position of a world position to a screen Y coordinate.
     *
     * @param worldX      world X coordinate
     * @param worldY      world Y coordinate
     * @param worldZ      world Z coordinate (floor)
     * @param playerIndex player index (0-3)
     * @return screen Y coordinate in pixels
     */
    public static float worldToScreenY(float worldX, float worldY, float worldZ, int playerIndex) {
        return worldToScreen(worldX, worldY, worldZ, playerIndex).y();
    }

    /**
     * Converts screen coordinates to a world position on the given floor.
     *
     * @param screenX     screen X coordinate in pixels
     * @param screenY     screen Y coordinate in pixels
     * @param floor       world floor
     * @param playerIndex player index (0-3)
     * @return world position
     * @throws IllegalArgumentException if the player index is out of range
     * @throws IllegalStateException    if the player camera is not initialized
     */
    public static WorldPoint screenToWorld(float screenX, float screenY, int floor, int playerIndex) {
        PlayerCamera camera = camera(playerIndex);
        float zoom = resolveZoom(camera);
        float localX = (screenX - IsoCamera.getScreenLeft(playerIndex)) * zoom;
        float localY = (screenY - IsoCamera.getScreenTop(playerIndex)) * zoom;
        return new WorldPoint(camera.XToIso(localX, localY, floor), camera.YToIso(localX, localY, floor), floor);
    }

    /**
     * Converts a screen X coordinate to a world X coordinate on the given floor.
     *
     * @param screenX     screen X coordinate in pixels
     * @param screenY     screen Y coordinate in pixels
     * @param floor       world floor
     * @param playerIndex player index (0-3)
     * @return world X coordinate
     */
    public static float screenToWorldX(float screenX, float screenY, int floor, int playerIndex) {
        return screenToWorld(screenX, screenY, floor, playerIndex).x();
    }

    /**
     * Converts a screen Y coordinate to a world Y coordinate on the given floor.
     *
     * @param screenX     screen X coordinate in pixels
     * @param screenY     screen Y coordinate in pixels
     * @param floor       world floor
     * @param playerIndex player index (0-3)
     * @return world Y coordinate
     */
    public static float screenToWorldY(float screenX, float screenY, int floor, int playerIndex) {
        return screenToWorld(screenX, screenY, floor, playerIndex).y();
    }

    /**
     * Checks whether the screen position lies inside the player viewport.
     *
     * @param screen      screen position
     * @param playerIndex player index (0-3)
     * @return {@code true} if the point is visible inside the viewport
     */
    public static boolean isOnScreen(ScreenPoint screen, int playerIndex) {
        if (screen == null) {
            return false;
        }

        int left = getViewportLeft(playerIndex);
        int top = getViewportTop(playerIndex);
        return screen.x() >= left
                && screen.x() < left + getViewportWidth(playerIndex)
                && screen.y() >= top
                && screen.y() < top + getViewportHeight(playerIndex);
    }

    /**
     * Resolves and validates the camera for a player index.
     *
     * @param playerIndex player index (0-3)
     * @return initialized camera
     */
    private static PlayerCamera camera(int playerIndex) {
        validatePlayerIndex(playerIndex);
        PlayerCamera camera = IsoCamera.cameras[playerIndex];
        if (camera == null) {
            throw new IllegalStateException("Player camera " + playerIndex + " is not initialized");
        }
        return camera;
    }

    /**
     * Validates a split-screen player index.
     *
     * @param playerIndex player index (0-3)
     */
    private static void validatePlayerIndex(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= IsoCamera.cameras.length) {
            throw new IllegalArgumentException("Player index must be between 0 and "
                    + (IsoCamera.cameras.length - 1) + ": " + playerIndex);
        }
    }

    /**
     * Resolves the effective zoom, falling back to the camera zoom when needed.
     *
     * @param camera player camera
     * @return positive zoom multiplier
     */
    private static float resolveZoom(PlayerCamera camera) {
        float zoom = Core.getInstance().getZoom(camera.playerIndex);
        if (zoom <= 0.0f) {
            zoom = camera.zoom;
        }
        return zoom > 0.0f ? zoom : 1.0f;
    }

    /**
     * Screen-space position in pixels.
     *
     * @param x horizontal coordinate
     * @param y vertical coordinate
     */
    public record ScreenPoint(float x, float y) {
    }

    /**
     * World-space position on a specific floor.
     *
     * @param x horizontal world coordinate
     * @param y vertical world coordinate
     * @param z world floor
     */
    public record WorldPoint(float x, float y, int z) {
    }
}
