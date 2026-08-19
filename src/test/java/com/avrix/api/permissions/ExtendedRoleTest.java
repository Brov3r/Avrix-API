package com.avrix.api.permissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import zombie.characters.Roles;
import zombie.core.Color;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying permission matching, role inheritance trees, formatting metadata,
 * immutability, and mutation guards in {@link ExtendedRole}.
 *
 * @author Avrix Engine Team
 */
@DisplayName("ExtendedRole Permission, Inheritance, and State Management Tests")
class ExtendedRoleTest {

    private static final String ROLE_NAME = "Moderator";
    private ExtendedRole role;

    @BeforeEach
    void setUp() {
        this.role = new ExtendedRole(ROLE_NAME);
        Roles.getRoles().clear();
        Roles.getRoles().add(this.role);
    }

    @Nested
    @DisplayName("Constructor and Invariant Validation")
    class InvariantTests {

        @Test
        @DisplayName("Should initialize properly with name only")
        void shouldInitializeWithName() {
            assertThat(role.getName()).isEqualTo(ROLE_NAME);
            assertThat(role.getDescription()).isEmpty();
            assertThat(role.getColor()).isEqualTo(Color.white);
            assertThat(role.getPermissions()).isEmpty();
            assertThat(role.getParents()).isEmpty();
            assertThat(role.getPrefix()).isEmpty();
            assertThat(role.getSuffix()).isEmpty();
            assertThat(role.getMetadata()).isEmpty();
            assertThat(role.isReadOnly()).isFalse();
        }

        @Test
        @DisplayName("Should initialize properly with full metadata")
        void shouldInitializeWithFullMetadata() {
            Color customColor = new Color(1.0f, 0.5f, 0.2f, 1.0f);
            ExtendedRole customRole = new ExtendedRole("Admin", "Server Administrator", customColor);

            assertThat(customRole.getName()).isEqualTo("Admin");
            assertThat(customRole.getDescription()).isEqualTo("Server Administrator");
            assertThat(customRole.getColor()).isEqualTo(customColor);
            assertThat(customRole.getPermissions()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should reject null or blank role name")
        void shouldRejectInvalidRoleNames(String invalidName) {
            assertThatThrownBy(() -> new ExtendedRole(invalidName))
                    .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Permission Mutation and Normalization")
    class MutationTests {

        @Test
        @DisplayName("Should add valid permission and normalize whitespace and casing")
        void shouldAddNormalizedPermission() {
            boolean added = role.addPermission("  Avrix.Command.TELEPORT  ");

            assertThat(added).isTrue();
            assertThat(role.getPermissions()).containsExactly("avrix.command.teleport");
            assertThat(role.hasPermission("avrix.command.teleport")).isTrue();
            assertThat(role.hasLocalPermission("avrix.command.teleport")).isTrue();
        }

        @Test
        @DisplayName("Should reject duplicate permissions")
        void shouldRejectDuplicatePermissions() {
            role.addPermission("avrix.kick");
            boolean duplicateAdd = role.addPermission("AVRIX.KICK");

            assertThat(duplicateAdd).isFalse();
            assertThat(role.getPermissions()).hasSize(1);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should return false when adding invalid permission string")
        void shouldRejectAddingInvalidStrings(String invalidNode) {
            assertThat(role.addPermission(invalidNode)).isFalse();
            assertThat(role.getPermissions()).isEmpty();
        }

        @Test
        @DisplayName("Should remove granted permission node")
        void shouldRemoveGrantedPermission() {
            role.addPermission("avrix.teleport");

            boolean removed = role.removePermission("  AVRIX.TELEPORT  ");

            assertThat(removed).isTrue();
            assertThat(role.getPermissions()).isEmpty();
            assertThat(role.hasPermission("avrix.teleport")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "non.existent.permission"})
        @DisplayName("Should return false when removing invalid or missing node")
        void shouldReturnFalseWhenRemovingMissingNode(String invalidNode) {
            role.addPermission("avrix.teleport");

            assertThat(role.removePermission(invalidNode)).isFalse();
            assertThat(role.getPermissions()).containsExactly("avrix.teleport");
        }

        @Test
        @DisplayName("Should clear all granted permissions")
        void shouldClearPermissions() {
            role.addPermission("perm.one");
            role.addPermission("perm.two");

            role.clearPermissions();

            assertThat(role.getPermissions()).isEmpty();
        }

        @Test
        @DisplayName("Should guarantee unmodifiable view on getPermissions()")
        void shouldReturnImmutablePermissionsView() {
            role.addPermission("perm.one");
            Set<String> permissions = role.getPermissions();

            assertThatThrownBy(() -> permissions.add("perm.injected"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should handle Turkish-I locale properly without collation errors")
        void shouldProperlyNormalizeUnderTurkishLocale() {
            Locale defaultLocale = Locale.getDefault();
            try {
                Locale.setDefault(Locale.of("tr", "TR"));

                role.addPermission("ITEM.SPAWN");
                assertThat(role.hasPermission("item.spawn")).isTrue();
                assertThat(role.hasPermission("ITEM.SPAWN")).isTrue();
            } finally {
                Locale.setDefault(defaultLocale);
            }
        }
    }

    @Nested
    @DisplayName("Role Inheritance Tests")
    class InheritanceTests {

        private ExtendedRole parentRole;
        private ExtendedRole grandParentRole;

        @BeforeEach
        void setUpHierarchy() {
            this.grandParentRole = new ExtendedRole("User");
            this.grandParentRole.addPermission("chat.speak");
            this.grandParentRole.setMeta("maxHomes", "1");

            this.parentRole = new ExtendedRole("VIP");
            this.parentRole.addPermission("chat.color");
            this.parentRole.addPermission("kit.vip");
            this.parentRole.addParent("User");
            this.parentRole.setMeta("maxHomes", "3");

            Roles.getRoles().addAll(List.of(this.grandParentRole, this.parentRole));
            role.addParent("VIP");
        }

        @Test
        @DisplayName("Should inherit permissions across multi-level inheritance tree")
        void shouldInheritPermissionsFromParents() {
            role.addPermission("moderation.kick");

            // Local permission
            assertThat(role.hasPermission("moderation.kick")).isTrue();
            assertThat(role.hasLocalPermission("moderation.kick")).isTrue();

            // Inherited from VIP
            assertThat(role.hasPermission("chat.color")).isTrue();
            assertThat(role.hasLocalPermission("chat.color")).isFalse();

            // Inherited from User through VIP
            assertThat(role.hasPermission("chat.speak")).isTrue();
            assertThat(role.hasLocalPermission("chat.speak")).isFalse();

            // Non-existent permission
            assertThat(role.hasPermission("admin.nuke")).isFalse();
        }

        @Test
        @DisplayName("Should prevent direct self-inheritance")
        void shouldRejectSelfInheritance() {
            boolean added = role.addParent(role.getName());
            assertThat(added).isFalse();
            assertThat(role.getParents()).containsExactly("VIP");
        }

        @Test
        @DisplayName("Should handle circular inheritance safely without StackOverflowError")
        void shouldHandleCircularInheritanceSafely() {
            // Create circular reference: User -> Moderator -> VIP -> User
            grandParentRole.addParent("Moderator");

            assertThat(role.hasPermission("chat.speak")).isTrue();
            assertThat(role.hasPermission("non.existent")).isFalse();
        }

        @Test
        @DisplayName("Should remove parent role from inheritance")
        void shouldRemoveParentRole() {
            boolean removed = role.removeParent("VIP");

            assertThat(removed).isTrue();
            assertThat(role.getParents()).isEmpty();
            assertThat(role.hasPermission("chat.color")).isFalse();
        }

        @Test
        @DisplayName("Should clear all parent roles")
        void shouldClearParents() {
            role.addParent("AnotherGroup");
            role.clearParents();

            assertThat(role.getParents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Metadata and Visual Formatting Tests")
    class MetadataTests {

        @Test
        @DisplayName("Should set and get visual chat prefix and suffix")
        void shouldManagePrefixAndSuffix() {
            role.setPrefix("&6[Mod]&f ");
            role.setSuffix(" &c[STAFF]");

            assertThat(role.getPrefix()).isEqualTo("&6[Mod]&f ");
            assertThat(role.getSuffix()).isEqualTo(" &c[STAFF]");
        }

        @Test
        @DisplayName("Should store and retrieve custom metadata")
        void shouldStoreLocalMetadata() {
            role.setMeta("flySpeed", "2.5");
            role.setMeta("maxClaims", "10");

            assertThat(role.getMeta("flySpeed")).isEqualTo("2.5");
            assertThat(role.getMeta("maxclaims")).isEqualTo("10"); // Case-insensitive lookup
            assertThat(role.getMetadata()).containsEntry("flyspeed", "2.5");
        }

        @Test
        @DisplayName("Should resolve inherited metadata from parent roles")
        void shouldInheritMetadataFromParent() {
            ExtendedRole parent = new ExtendedRole("ParentGroup");
            parent.setMeta("claimBlocks", "500");
            Roles.getRoles().add(parent);

            role.addParent("ParentGroup");

            // Direct override takes precedence
            role.setMeta("localKey", "localVal");

            assertThat(role.getMeta("localKey")).isEqualTo("localVal");
            assertThat(role.getMeta("claimBlocks")).isEqualTo("500");
            assertThat(role.getMeta("unknownKey")).isNull();
        }

        @Test
        @DisplayName("Should remove metadata when value is null")
        void shouldRemoveMetadataOnNullValue() {
            role.setMeta("limit", "100");
            role.setMeta("limit", null);

            assertThat(role.getMeta("limit")).isNull();
            assertThat(role.getMetadata()).doesNotContainKey("limit");
        }
    }

    @Nested
    @DisplayName("ReadOnly State Guard Tests")
    class ReadOnlyTests {

        @BeforeEach
        void setupReadOnly() {
            role.addPermission("initial.perm");
            role.addParent("InitialParent");
            role.setPrefix("[OldPrefix]");
            role.setSuffix("[OldSuffix]");
            role.setMeta("oldMeta", "true");
            role.setReadOnly();
        }

        @Test
        @DisplayName("Should reject adding permission when role is read-only")
        void shouldRejectAddPermissionWhenReadOnly() {
            boolean added = role.addPermission("another.perm");
            assertThat(added).isFalse();
            assertThat(role.getPermissions()).containsExactly("initial.perm");
        }

        @Test
        @DisplayName("Should reject adding or removing parent when role is read-only")
        void shouldRejectParentMutationsWhenReadOnly() {
            boolean addParent = role.addParent("NewParent");
            boolean removeParent = role.removeParent("InitialParent");
            role.clearParents();

            assertThat(addParent).isFalse();
            assertThat(removeParent).isFalse();
            assertThat(role.getParents()).containsExactly("InitialParent");
        }

        @Test
        @DisplayName("Should reject prefix, suffix, and meta modifications when role is read-only")
        void shouldRejectMetaMutationsWhenReadOnly() {
            role.setPrefix("[NewPrefix]");
            role.setSuffix("[NewSuffix]");
            role.setMeta("newMeta", "false");

            assertThat(role.getPrefix()).isEqualTo("[OldPrefix]");
            assertThat(role.getSuffix()).isEqualTo("[OldSuffix]");
            assertThat(role.getMeta("newMeta")).isNull();
        }
    }

    @Nested
    @DisplayName("Hierarchical and Wildcard Evaluation Tests")
    class WildcardPermissionTests {

        @Test
        @DisplayName("Global wildcard '*' should match all queried permissions")
        void globalWildcardShouldMatchEverything() {
            role.addPermission("*");

            assertThat(role.hasPermission("avrix.admin")).isTrue();
            assertThat(role.hasPermission("zomboid.item.spawn")).isTrue();
            assertThat(role.hasPermission("any.random.permission.node")).isTrue();
        }

        @Test
        @DisplayName("Sub-branch wildcard 'foo.bar.*' should match root and descendants")
        void branchWildcardShouldMatchSubNodes() {
            role.addPermission("avrix.admin.*");

            assertThat(role.hasPermission("avrix.admin")).isTrue();
            assertThat(role.hasPermission("avrix.admin.kick")).isTrue();
            assertThat(role.hasPermission("avrix.admin.ban.temp")).isTrue();

            assertThat(role.hasPermission("avrix.user")).isFalse();
            assertThat(role.hasPermission("avrix.administrator")).isFalse();
        }

        @Test
        @DisplayName("Exact permission should match only itself")
        void exactPermissionShouldNotMatchDescendants() {
            role.addPermission("avrix.command.teleport");

            assertThat(role.hasPermission("avrix.command.teleport")).isTrue();
            assertThat(role.hasPermission("avrix.command.teleport.others")).isFalse();
            assertThat(role.hasPermission("avrix.command")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("Should return false when checking blank permissions")
        void shouldReturnFalseForBlankChecks(String query) {
            role.addPermission("*");
            assertThat(role.hasPermission(query)).isFalse();
        }
    }
}