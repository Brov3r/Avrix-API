package com.avrix.api;

import com.avrix.core.Environment;
import com.avrix.core.Metadata;
import com.avrix.plugins.PluginManifestParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginMetadataTest {

    private static final List<String> COMMON_MIXINS = List.of(
            "com.avrix.api.mixins.common.LuaManagerMixin",
            "com.avrix.api.mixins.common.LuaEventManagerMixin",
            "com.avrix.api.mixins.common.LuaRequireMixin",
            "com.avrix.api.mixins.common.PacketReceiveMixin"
    );

    private static final List<String> CLIENT_MIXINS = List.of(
            "com.avrix.api.mixins.client.DisplayMixin",
            "com.avrix.api.mixins.client.GameKeyboardMixin",
            "com.avrix.api.mixins.client.MouseMixin",
            "com.avrix.api.mixins.client.UIManagerMixin"
    );

    private static final List<String> SERVER_MIXINS = List.of(
            "com.avrix.api.mixins.server.GameServerEventsMixin",
            "com.avrix.api.mixins.server.GameServerCommandsMixin",
            "com.avrix.api.mixins.server.GameServerPermissionsMixin",
            "com.avrix.api.mixins.server.RolesMixin",
            "com.avrix.api.mixins.server.ServerWorldDatabaseMixin"
    );

    @Test
    void declaresSchemaTwoWithEnvironmentSpecificMixins(@TempDir Path tempDir) throws IOException {
        Metadata metadata = parsePackagedMetadata(tempDir);

        assertThat(metadata.schema()).isEqualTo(2);
        assertThat(metadata.id()).isEqualTo("avrix-api");
        assertThat(metadata.version()).isNotBlank().doesNotContain("%VERSION%");
        assertThat(metadata.mixins()).containsExactlyElementsOf(COMMON_MIXINS);
        assertThat(metadata.clientMixins()).containsExactlyElementsOf(CLIENT_MIXINS);
        assertThat(metadata.serverMixins()).containsExactlyElementsOf(SERVER_MIXINS);
        assertThat(metadata.entrypointsFor(Environment.CLIENT)).isEmpty();
        assertThat(metadata.entrypointsFor(Environment.SERVER)).isEmpty();
    }

    @Test
    void composesMixinsForEachEnvironment(@TempDir Path tempDir) throws IOException {
        Metadata metadata = parsePackagedMetadata(tempDir);

        assertThat(metadata.mixinsFor(Environment.CLIENT))
                .containsAll(COMMON_MIXINS)
                .containsAll(CLIENT_MIXINS)
                .hasSize(COMMON_MIXINS.size() + CLIENT_MIXINS.size())
                .doesNotContainAnyElementsOf(SERVER_MIXINS);
        assertThat(metadata.mixinsFor(Environment.SERVER))
                .containsAll(COMMON_MIXINS)
                .containsAll(SERVER_MIXINS)
                .hasSize(COMMON_MIXINS.size() + SERVER_MIXINS.size())
                .doesNotContainAnyElementsOf(CLIENT_MIXINS);
    }

    private Metadata parsePackagedMetadata(Path tempDir) throws IOException {
        Path metadataFile = tempDir.resolve("metadata.yml");
        try (InputStream stream = getClass().getResourceAsStream("/metadata.yml")) {
            assertThat(stream).isNotNull();
            Files.copy(stream, metadataFile);
        }
        return new PluginManifestParser().fromYaml(metadataFile);
    }
}
