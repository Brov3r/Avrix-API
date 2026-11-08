package com.avrix.api.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zombie.core.Core;
import zombie.iso.IsoCamera;
import zombie.iso.PlayerCamera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientScreenUtilsTest {

    private PlayerCamera previousCamera;
    private int previousWidth;
    private int previousHeight;

    @BeforeEach
    void setUp() {
        previousCamera = IsoCamera.cameras[0];
        previousWidth = Core.width;
        previousHeight = Core.height;

        Core.width = 1280;
        Core.height = 720;

        PlayerCamera camera = new PlayerCamera(0);
        camera.offX = 100.0f;
        camera.offY = 50.0f;
        camera.zoom = 1.0f;
        IsoCamera.cameras[0] = camera;
    }

    @AfterEach
    void tearDown() {
        IsoCamera.cameras[0] = previousCamera;
        Core.width = previousWidth;
        Core.height = previousHeight;
    }

    @Test
    void reportsWindowAndViewportDimensions() {
        assertThat(ClientScreenUtils.getScreenWidth()).isEqualTo(1280);
        assertThat(ClientScreenUtils.getScreenHeight()).isEqualTo(720);
        assertThat(ClientScreenUtils.getAspectRatio()).isEqualTo(1280.0f / 720.0f);
        assertThat(ClientScreenUtils.getViewportLeft(0)).isZero();
        assertThat(ClientScreenUtils.getViewportTop(0)).isZero();
        assertThat(ClientScreenUtils.getViewportWidth(0)).isEqualTo(1280);
        assertThat(ClientScreenUtils.getViewportHeight(0)).isEqualTo(720);
    }

    @Test
    void convertsWorldToScreenAndBack() {
        ClientScreenUtils.ScreenPoint screen = ClientScreenUtils.worldToScreen(0.0f, 0.0f, 0.0f, 0);

        assertThat(screen.x()).isEqualTo(-100.0f);
        assertThat(screen.y()).isEqualTo(-50.0f);

        ClientScreenUtils.WorldPoint world = ClientScreenUtils.screenToWorld(screen.x(), screen.y(), 0, 0);

        assertThat(world.x()).isEqualTo(0.0f);
        assertThat(world.y()).isEqualTo(0.0f);
        assertThat(world.z()).isZero();
        assertThat(ClientScreenUtils.screenToWorldX(screen.x(), screen.y(), 0, 0)).isEqualTo(0.0f);
        assertThat(ClientScreenUtils.screenToWorldY(screen.x(), screen.y(), 0, 0)).isEqualTo(0.0f);
    }

    @Test
    void checksWhetherPointsAreInsideTheViewport() {
        assertThat(ClientScreenUtils.isOnScreen(new ClientScreenUtils.ScreenPoint(10.0f, 10.0f), 0)).isTrue();
        assertThat(ClientScreenUtils.isOnScreen(new ClientScreenUtils.ScreenPoint(-1.0f, 10.0f), 0)).isFalse();
        assertThat(ClientScreenUtils.isOnScreen(new ClientScreenUtils.ScreenPoint(10.0f, 720.0f), 0)).isFalse();
        assertThat(ClientScreenUtils.isOnScreen(null, 0)).isFalse();
    }

    @Test
    void validatesPlayerIndexes() {
        assertThatThrownBy(() -> ClientScreenUtils.getViewportLeft(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientScreenUtils.getZoom(IsoCamera.cameras.length))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientScreenUtils.worldToScreen((zombie.iso.IsoObject) null, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
