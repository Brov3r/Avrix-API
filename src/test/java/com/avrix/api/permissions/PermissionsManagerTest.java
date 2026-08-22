package com.avrix.api.permissions;

import com.avrix.api.events.EventManager;
import com.avrix.api.events.SubscribeEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.characters.Role;
import zombie.characters.Roles;
import zombie.core.Color;
import zombie.core.Core;
import zombie.core.raknet.UdpConnection;
import zombie.core.random.RandStandard;
import zombie.network.GameClient;
import zombie.network.GameServer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Exhaustive unit and integration test suite covering {@link ExtendedRole}, {@link PermissionsManager},
 * {@link PlayerPermission}, declarative YAML loading, SteamID64 mapping, and lifecycle events.
 */
@DisplayName("Permissions & Roles Engine Test Suite")
class PermissionsManagerTest {

    @TempDir
    Path tempFolder;

    @BeforeEach
    void setUp() throws Exception {
        try {
            RandStandard.INSTANCE.init();
        } catch (Throwable _) {
        }
        Roles.getRoles().clear();
        clearManagerCache();
    }

    @AfterEach
    void tearDown() throws Exception {
        Roles.getRoles().clear();
        clearManagerCache();
    }

    private static void clearManagerCache() throws Exception {
        Field permsField = PermissionsManager.class.getDeclaredField("CUSTOM_PLAYER_PERMISSIONS");
        permsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<?, ?> permsMap = (Map<?, ?>) permsField.get(null);
        permsMap.clear();
    }

    // =========================================================================
    // ExtendedRole Domain & Invariants Tests
    // =========================================================================

    @Nested
    @DisplayName("1. ExtendedRole Constructor & Mutation Invariants")
    class ExtendedRoleInvariantTests {

        private static final String ROLE_NAME = "Moderator";
        private ExtendedRole role;

        @BeforeEach
        void setUpRole() {
            this.role = new ExtendedRole(ROLE_NAME);
            Roles.getRoles().add(this.role);
        }

        @Test
        @DisplayName("Should initialize properly with default values")
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
        @DisplayName("Should initialize properly with full display metadata")
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

        @Test
        @DisplayName("Should add normalized permission and reject duplicates")
        void shouldAddAndNormalizePermission() {
            boolean added = role.addPermission("  Avrix.Command.TELEPORT  ");
            boolean duplicate = role.addPermission("AVRIX.COMMAND.TELEPORT");

            assertThat(added).isTrue();
            assertThat(duplicate).isFalse();
            assertThat(role.getPermissions()).containsExactly("avrix.command.teleport");
            assertThat(role.hasPermission("avrix.command.teleport")).isTrue();
            assertThat(role.hasLocalPermission("avrix.command.teleport")).isTrue();
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

    // =========================================================================
    // Role Inheritance & Hierarchy Tests
    // =========================================================================

    @Nested
    @DisplayName("2. Role Inheritance & Circular Safety")
    class RoleInheritanceTests {

        private ExtendedRole userRole;
        private ExtendedRole vipRole;
        private ExtendedRole modRole;

        @BeforeEach
        void setUpHierarchy() {
            this.userRole = new ExtendedRole("User");
            this.userRole.addPermission("chat.speak");
            this.userRole.setMeta("maxHomes", "1");

            this.vipRole = new ExtendedRole("VIP");
            this.vipRole.addPermission("chat.color");
            this.vipRole.addPermission("kit.vip");
            this.vipRole.addParent("User");
            this.vipRole.setMeta("maxHomes", "3");

            this.modRole = new ExtendedRole("Moderator");
            this.modRole.addPermission("moderation.kick");
            this.modRole.addParent("VIP");

            Roles.getRoles().addAll(List.of(this.userRole, this.vipRole, this.modRole));
        }

        @Test
        @DisplayName("Should inherit permissions across multi-level inheritance tree")
        void shouldInheritPermissionsFromParents() {
            assertThat(modRole.hasPermission("moderation.kick")).isTrue();
            assertThat(modRole.hasLocalPermission("moderation.kick")).isTrue();

            assertThat(modRole.hasPermission("chat.color")).isTrue();
            assertThat(modRole.hasLocalPermission("chat.color")).isFalse();

            assertThat(modRole.hasPermission("chat.speak")).isTrue();
            assertThat(modRole.hasLocalPermission("chat.speak")).isFalse();

            assertThat(modRole.hasPermission("admin.nuke")).isFalse();
        }

        @Test
        @DisplayName("Should prevent direct self-inheritance")
        void shouldRejectSelfInheritance() {
            boolean added = modRole.addParent(modRole.getName());
            assertThat(added).isFalse();
            assertThat(modRole.getParents()).containsExactly("VIP");
        }

        @Test
        @DisplayName("Should handle circular inheritance safely without StackOverflowError")
        void shouldHandleCircularInheritanceSafely() {
            userRole.addParent("Moderator");

            assertThat(modRole.hasPermission("chat.speak")).isTrue();
            assertThat(modRole.hasPermission("non.existent")).isFalse();
        }

        @Test
        @DisplayName("Should resolve and inherit metadata from parent roles")
        void shouldInheritMetadataFromParent() {
            assertThat(modRole.getMeta("maxHomes")).isEqualTo("3");

            modRole.setMeta("maxHomes", "10");
            assertThat(modRole.getMeta("maxHomes")).isEqualTo("10");
        }
    }

    // =========================================================================
    // Wildcard Permission Matching Tests
    // =========================================================================

    @Nested
    @DisplayName("3. Wildcard Permission Matching")
    class WildcardMatchingTests {

        private ExtendedRole testRole;

        @BeforeEach
        void setUpRole() {
            testRole = new ExtendedRole("TestRole");
            Roles.getRoles().add(testRole);
        }

        @Test
        @DisplayName("Global wildcard '*' should match all queried permissions")
        void globalWildcardShouldMatchEverything() {
            testRole.addPermission("*");

            assertThat(testRole.hasPermission("avrix.admin")).isTrue();
            assertThat(testRole.hasPermission("zomboid.item.spawn")).isTrue();
            assertThat(testRole.hasPermission("any.deep.nested.node")).isTrue();
        }

        @Test
        @DisplayName("Branch wildcard 'foo.bar.*' should match root and descendants")
        void branchWildcardShouldMatchSubNodes() {
            testRole.addPermission("avrix.admin.*");

            assertThat(testRole.hasPermission("avrix.admin")).isTrue();
            assertThat(testRole.hasPermission("avrix.admin.kick")).isTrue();
            assertThat(testRole.hasPermission("avrix.admin.ban.temp")).isTrue();

            assertThat(testRole.hasPermission("avrix.user")).isFalse();
            assertThat(testRole.hasPermission("avrix.administrator")).isFalse();
        }
    }

    // =========================================================================
    // PlayerPermission Domain Model Invariants
    // =========================================================================

    @Nested
    @DisplayName("4. PlayerPermission Record Invariants")
    class PlayerPermissionModelTests {

        @Test
        @DisplayName("Permanent permission should initialize with null expiration and normalized node")
        void testPermanentPermissionCreation() {
            PlayerPermission perm = PlayerPermission.permanent("  Avrix.Commands.HEAL  ");

            assertThat(perm.node()).isEqualTo("avrix.commands.heal");
            assertThat(perm.expiration()).isNull();
            assertThat(perm.isTemporary()).isFalse();
            assertThat(perm.isExpired()).isFalse();
            assertThat(perm.isNegated()).isFalse();
            assertThat(perm.cleanNode()).isEqualTo("avrix.commands.heal");
        }

        @Test
        @DisplayName("Temporary permission should correctly evaluate expiration state")
        void testTemporaryPermissionExpiration() {
            Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
            PlayerPermission activePerm = PlayerPermission.temporary("chat.color", future);

            assertThat(activePerm.isTemporary()).isTrue();
            assertThat(activePerm.isExpired()).isFalse();

            Instant past = Instant.now().minus(1, ChronoUnit.SECONDS);
            PlayerPermission expiredPerm = PlayerPermission.temporary("chat.color", past);

            assertThat(expiredPerm.isTemporary()).isTrue();
            assertThat(expiredPerm.isExpired()).isTrue();
        }

        @Test
        @DisplayName("Negated permission should be detected and return clean node")
        void testNegatedPermission() {
            PlayerPermission negated = PlayerPermission.permanent("-avrix.commands.stop");

            assertThat(negated.isNegated()).isTrue();
            assertThat(negated.cleanNode()).isEqualTo("avrix.commands.stop");
        }

        @Test
        @DisplayName("Null permission node should throw NullPointerException")
        void testNullNodeRejection() {
            assertThatThrownBy(() -> PlayerPermission.permanent(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // Direct Player Permission Grants & Expirations
    // =========================================================================

    @Nested
    @DisplayName("5. Direct Permission Grants and Time-Based Expirations")
    class DirectPermissionTests {

        @Test
        @DisplayName("Should grant and evaluate permanent personal permission")
        void shouldGrantPermanentPermission() {
            PermissionsManager.grantPermissionToPlayer("Brov3r", "custom.special.ability");
            IsoPlayer player = createMockPlayer("Brov3r", 0L, null);

            assertThat(PermissionsManager.hasPermission(player, "custom.special.ability")).isTrue();
            assertThat(PermissionsManager.getPlayerPermissions("Brov3r")).containsExactly("custom.special.ability");
        }

        @Test
        @DisplayName("Temporary permission granted by duration should expire after time elapsed")
        void shouldExpireTemporaryPermissionByDuration() throws InterruptedException {
            PermissionsManager.grantPermissionToPlayer("Speedy", "temp.speed", 80, TimeUnit.MILLISECONDS);
            IsoPlayer player = createMockPlayer("Speedy", 0L, null);

            assertThat(PermissionsManager.hasPermission(player, "temp.speed")).isTrue();

            Thread.sleep(120);

            assertThat(PermissionsManager.hasPermission(player, "temp.speed")).isFalse();
            assertThat(PermissionsManager.getPlayerPermissions("Speedy")).isEmpty();
        }

        @Test
        @DisplayName("Temporary permission granted by absolute Instant should expire when past")
        void shouldExpireTemporaryPermissionByInstant() {
            Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
            PermissionsManager.grantPermissionToPlayer("CalendarUser", "vip.feature", future);
            IsoPlayer player = createMockPlayer("CalendarUser", 0L, null);

            assertThat(PermissionsManager.hasPermission(player, "vip.feature")).isTrue();

            Collection<PlayerPermission> entries = PermissionsManager.getPlayerPermissionEntries("CalendarUser");
            assertThat(entries).hasSize(1);
            assertThat(entries.iterator().next().expiration()).isEqualTo(future);
        }

        @Test
        @DisplayName("Revoking permission should remove node immediately")
        void shouldRevokePermission() {
            PermissionsManager.grantPermissionToPlayer("TargetUser", "node.to.remove");
            IsoPlayer player = createMockPlayer("TargetUser", 0L, null);

            assertThat(PermissionsManager.hasPermission(player, "node.to.remove")).isTrue();

            boolean revoked = PermissionsManager.revokePermissionFromPlayer("TargetUser", "node.to.remove");

            assertThat(revoked).isTrue();
            assertThat(PermissionsManager.hasPermission(player, "node.to.remove")).isFalse();
        }

        @Test
        @DisplayName("Clearing player permissions should purge all entries")
        void shouldClearAllPlayerPermissions() {
            PermissionsManager.grantPermissionToPlayer("CleanMe", "node.one");
            PermissionsManager.grantPermissionToPlayer("CleanMe", "node.two");

            PermissionsManager.clearPlayerPermissions("CleanMe");

            assertThat(PermissionsManager.getPlayerPermissions("CleanMe")).isEmpty();
        }
    }

    // =========================================================================
    // Explicit Negation Tests (-node)
    // =========================================================================

    @Nested
    @DisplayName("6. Explicit Negation Node (-node) Enforcement")
    class NegationTests {

        @Test
        @DisplayName("Explicit direct negation should override wildcard granted by role")
        void explicitNegationShouldOverrideRoleWildcard() {
            ExtendedRole adminRole = new ExtendedRole("AdminGroup");
            adminRole.addPermission("avrix.admin.*");

            IsoPlayer player = createMockPlayer("SubAdmin", 0L, adminRole);

            assertThat(PermissionsManager.hasPermission(player, "avrix.admin.kick")).isTrue();
            assertThat(PermissionsManager.hasPermission(player, "avrix.admin.stop")).isTrue();

            PermissionsManager.grantPermissionToPlayer("SubAdmin", "-avrix.admin.stop");

            assertThat(PermissionsManager.hasPermission(player, "avrix.admin.kick")).isTrue();
            assertThat(PermissionsManager.hasPermission(player, "avrix.admin.stop")).isFalse();
        }

        @Test
        @DisplayName("Wildcard negation should block sub-branch")
        void wildcardNegationShouldBlockSubBranch() {
            ExtendedRole rootRole = new ExtendedRole("SuperRole");
            rootRole.addPermission("*");

            IsoPlayer player = createMockPlayer("RestrictedSuper", 0L, rootRole);

            PermissionsManager.grantPermissionToPlayer("RestrictedSuper", "-dangerous.system.*");

            assertThat(PermissionsManager.hasPermission(player, "safe.command")).isTrue();
            assertThat(PermissionsManager.hasPermission(player, "dangerous.system.reboot")).isFalse();
            assertThat(PermissionsManager.hasPermission(player, "dangerous.system.delete")).isFalse();
        }
    }

    // =========================================================================
    // SteamID64 & Username Normalization Tests
    // =========================================================================

    @Nested
    @DisplayName("7. SteamID64 & Quoted Username Identity Lookups")
    class IdentityMappingTests {

        @Test
        @DisplayName("Should grant and verify permissions by 64-bit SteamID integer string")
        void shouldAuthorizeBySteamID64() {
            long steamId64 = 76561198012345678L;
            PermissionsManager.grantPermissionToPlayer(String.valueOf(steamId64), "steam.exclusive.kit");

            IsoPlayer player = createMockPlayer("SteamPlayer", steamId64, null);

            assertThat(PermissionsManager.hasPermission(player, "steam.exclusive.kit")).isTrue();
        }

        @Test
        @DisplayName("Should handle usernames with spaces and surrounding quotes seamlessly")
        void shouldNormalizeUsernamesWithSpacesAndQuotes() {
            PermissionsManager.grantPermissionToPlayer("\"Miss Bekket\"", "vip.heal");
            PermissionsManager.grantPermissionToPlayer("'Officer Bob'", "cop.handcuff");

            IsoPlayer player1 = createMockPlayer("Miss Bekket", 0L, null);
            IsoPlayer player2 = createMockPlayer("Officer Bob", 0L, null);

            assertThat(PermissionsManager.hasPermission(player1, "vip.heal")).isTrue();
            assertThat(PermissionsManager.hasPermission(player2, "cop.handcuff")).isTrue();
        }
    }

    // =========================================================================
    // Declarative YAML Configuration Loading & Auto-Template Tests
    // =========================================================================

    @Nested
    @DisplayName("8. Declarative YAML Configuration (permissions.yml)")
    class DeclarativeYamlTests {

        @Test
        @DisplayName("Should generate default template permissions.yml when missing and parse roles")
        void shouldCreateDefaultYamlTemplateAndParse() {
            Path customConfigPath = tempFolder.resolve("avrix-core").resolve("permissions.yml");
            assertThat(Files.notExists(customConfigPath)).isTrue();

            PermissionsManager.loadPermissionsConfig(customConfigPath);

            assertThat(Files.exists(customConfigPath)).isTrue();

            Role adminRole = Roles.getRole("admin");
            Role modRole = Roles.getRole("moderator");
            Role userRole = Roles.getRole("user");

            assertThat(adminRole).isNotNull();
            assertThat(modRole).isNotNull();
            assertThat(userRole).isNotNull();

            assertThat(adminRole.hasCapability(Capability.AddItem)).isTrue();
            assertThat(modRole.hasCapability(Capability.KickUser)).isTrue();
            assertThat(userRole.hasCapability(Capability.LoginOnServer)).isTrue();
        }

        @Test
        @DisplayName("YAML file must have absolute authority and overwrite standard roles")
        void yamlShouldOverrideExistingRoles() throws IOException {
            Path customConfigPath = tempFolder.resolve("permissions.yml");

            String customYaml = """
                    groups:
                      admin:
                        description: "Overridden Admin"
                        color: "100,100,100,255"
                        prefix: "[SuperAdmin] "
                        suffix: ""
                        permissions:
                          - "custom.admin.node"
                        capabilities:
                          - "AddItem"
                        parents: []
                    
                    users:
                      "Special Hero":
                        group: "admin"
                        permissions:
                          - "hero.power"
                    """;
            Files.writeString(customConfigPath, customYaml);

            PermissionsManager.loadPermissionsConfig(customConfigPath);

            Role adminRole = Roles.getRole("admin");
            assertThat(adminRole).isInstanceOf(ExtendedRole.class);
            ExtendedRole extendedAdmin = (ExtendedRole) adminRole;

            assertThat(extendedAdmin.getDescription()).isEqualTo("Overridden Admin");
            assertThat(extendedAdmin.getPrefix()).isEqualTo("[SuperAdmin] ");
            assertThat(extendedAdmin.hasPermission("custom.admin.node")).isTrue();

            IsoPlayer heroPlayer = createMockPlayer("Special Hero", 0L, null);
            assertThat(PermissionsManager.hasPermission(heroPlayer, "hero.power")).isTrue();
        }
    }

    // =========================================================================
    // Lifecycle ServerEvents Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("9. Lifecycle ServerEvents Integration")
    class EventIntegrationTests {

        @Test
        @DisplayName("Should dispatch ServerEvents.PERMISSION_GRANTED and PERMISSION_REVOKED")
        void shouldDispatchPermissionLifecycleEvents() {
            AtomicBoolean grantedFired = new AtomicBoolean(false);
            AtomicBoolean revokedFired = new AtomicBoolean(false);

            Object listener = new Object() {
                @SubscribeEvent(custom = "OnPermissionGranted")
                public void onGranted(String username, String node, Instant exp) {
                    if ("EventGuy".equalsIgnoreCase(username) && "event.node".equals(node)) {
                        grantedFired.set(true);
                    }
                }

                @SubscribeEvent(custom = "OnPermissionRevoked")
                public void onRevoked(String username, String node) {
                    if ("EventGuy".equalsIgnoreCase(username) && "event.node".equals(node)) {
                        revokedFired.set(true);
                    }
                }
            };

            EventManager.register(listener);
            try {
                PermissionsManager.grantPermissionToPlayer("EventGuy", "event.node");
                assertThat(grantedFired.get()).isTrue();

                PermissionsManager.revokePermissionFromPlayer("EventGuy", "event.node");
                assertThat(revokedFired.get()).isTrue();
            } finally {
                EventManager.unregister(listener);
            }
        }

        @Test
        @DisplayName("Should dispatch ServerEvents.PLAYER_ROLE_ASSIGNED when role changes")
        void shouldDispatchRoleAssignmentEvent() {
            AtomicReference<String> assignedRoleName = new AtomicReference<>();

            Object listener = new Object() {
                @SubscribeEvent(custom = "OnPlayerRoleAssigned")
                public void onRoleAssigned(IsoPlayer p, Role oldR, Role newR) {
                    if (newR != null) {
                        assignedRoleName.set(newR.getName());
                    }
                }
            };

            EventManager.register(listener);
            try {
                ExtendedRole newRole = new ExtendedRole("NewRank");
                Roles.getRoles().add(newRole);

                IsoPlayer player = createMockPlayer("RankGuy", 0L, null);
                boolean success = PermissionsManager.assignRole(player, "NewRank");

                assertThat(success).isTrue();
                assertThat(assignedRoleName.get()).isEqualTo("NewRank");
            } finally {
                EventManager.unregister(listener);
            }
        }
    }


    // =========================================================================
    // 10. Advanced Lifecycle: Programmatic Roles, Connections, Capabilities & Metadata
    // =========================================================================

    @Nested
    @DisplayName("10. Programmatic Roles, Connections, Capabilities & Metadata")
    class AdvancedLifecycleAndCapabilityTests {

        @Test
        @DisplayName("createRole should register ExtendedRole with LoginOnServer capability and custom permissions")
        void shouldCreateRoleProgrammatically() {
            ExtendedRole created = PermissionsManager.createRole(
                    "CustomBuilder",
                    "Builder rank",
                    new Color(0.1f, 0.2f, 0.3f),
                    List.of(Capability.CanOpenLockedDoors),
                    "builder.build", "builder.destroy"
            );

            assertThat(created).isNotNull();
            assertThat(Roles.getRole("CustomBuilder")).isSameAs(created);
            assertThat(created.hasCapability(Capability.LoginOnServer)).isTrue();
            assertThat(created.hasCapability(Capability.CanOpenLockedDoors)).isTrue();
            assertThat(created.hasPermission("builder.build")).isTrue();
            assertThat(created.hasPermission("builder.destroy")).isTrue();
        }

        @Test
        @DisplayName("createRole should reject blank names and duplicate registrations")
        void shouldRejectInvalidRoleCreation() {
            assertThatThrownBy(() -> PermissionsManager.createRole("   ", "Desc", Color.white, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            PermissionsManager.createRole("UniqueRank", "Desc", Color.white, List.of());

            assertThatThrownBy(() -> PermissionsManager.createRole("UniqueRank", "Desc2", Color.white, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("deleteRole should remove custom role and protect read-only roles")
        void shouldHandleRoleDeletionSafely() {
            ExtendedRole custom = PermissionsManager.createRole("TempRole", "To Delete", Color.white, List.of());
            assertThat(Roles.getRole("TempRole")).isNotNull();

            boolean deleted = PermissionsManager.deleteRole("TempRole", "Admin");
            assertThat(deleted).isTrue();

            // Read-only role protection
            ExtendedRole readOnlyRole = new ExtendedRole("SystemProtected");
            readOnlyRole.setReadOnly();
            Roles.getRoles().add(readOnlyRole);

            boolean deletedReadOnly = PermissionsManager.deleteRole("SystemProtected", "Admin");
            assertThat(deletedReadOnly).isFalse();

            // Non-existent role
            assertThat(PermissionsManager.deleteRole("NonExistentRole", "Admin")).isFalse();
            assertThat(PermissionsManager.deleteRole("   ", "Admin")).isFalse();
        }

        @Test
        @DisplayName("assignRole to UdpConnection should update connection and all contained players")
        void shouldAssignRoleToConnection() {
            ExtendedRole role = PermissionsManager.createRole("ConnRank", "Desc", Color.white, List.of());

            UdpConnection connection = Mockito.mock(UdpConnection.class);
            IsoPlayer playerInConn = Mockito.mock(IsoPlayer.class);
            when(playerInConn.getUsername()).thenReturn("ConnUser");
            connection.players = new IsoPlayer[]{playerInConn};

            boolean assigned = PermissionsManager.assignRole(connection, "ConnRank");

            assertThat(assigned).isTrue();
            Mockito.verify(connection).setRole(role);
            Mockito.verify(playerInConn).setRole(role);
        }

        @Test
        @DisplayName("assignRole by string identifier should resolve target player case-insensitively")
        void shouldAssignRoleByIdentifier() {
            ExtendedRole role = PermissionsManager.createRole("VipRank", "Desc", Color.white, List.of());

            // Assigning offline/non-existent role name
            assertThat(PermissionsManager.assignRole("UnknownUser", "InvalidRank")).isFalse();
            assertThat(PermissionsManager.assignRole("   ", "VipRank")).isFalse();
        }

        @Test
        @DisplayName("hasCapability should correctly evaluate capability across Player, Connection, Role, and Username")
        void shouldEvaluateCapabilitiesCorrectly() {
            ExtendedRole roleWithCap = PermissionsManager.createRole(
                    "CapRole", "Desc", Color.white,
                    List.of(Capability.AddItem, Capability.TeleportToPlayer)
            );

            IsoPlayer player = createMockPlayer("CapGuy", 0L, roleWithCap);
            UdpConnection connection = Mockito.mock(UdpConnection.class);
            when(connection.getRole()).thenReturn(roleWithCap);

            // Role capability
            assertThat(PermissionsManager.hasCapability(roleWithCap, Capability.AddItem)).isTrue();
            assertThat(PermissionsManager.hasCapability(roleWithCap, Capability.SaveWorld)).isFalse();
            assertThat(PermissionsManager.hasCapability((Role) null, Capability.AddItem)).isFalse();
            assertThat(PermissionsManager.hasCapability(roleWithCap, Capability.None)).isFalse();

            // Connection capability
            assertThat(PermissionsManager.hasCapability(connection, Capability.AddItem)).isTrue();
            assertThat(PermissionsManager.hasCapability((UdpConnection) null, Capability.AddItem)).isFalse();

            // Player capability
            assertThat(PermissionsManager.hasCapability(player, Capability.AddItem)).isTrue();
            assertThat(PermissionsManager.hasCapability((IsoPlayer) null, Capability.AddItem)).isFalse();
        }

        @Test
        @DisplayName("hasPermission on Role should match standard Capability names passed as permission string")
        void shouldMatchCapabilityNameInRolePermissionCheck() {
            ExtendedRole role = PermissionsManager.createRole(
                    "CapStringRole", "Desc", Color.white,
                    List.of(Capability.ToggleGodModHimself)
            );

            // Queried as string permission matching Capability enum name case-insensitively
            assertThat(PermissionsManager.hasPermission(role, "ToggleGodModHimself")).isTrue();
            assertThat(PermissionsManager.hasPermission(role, "togglegodmodhimself")).isTrue();
            assertThat(PermissionsManager.hasPermission(role, "NonExistentCapability")).isFalse();
        }

        @Test
        @DisplayName("hasPermission on UdpConnection should evaluate assigned role permissions")
        void shouldEvaluatePermissionOnConnection() {
            ExtendedRole role = PermissionsManager.createRole("NetRole", "Desc", Color.white, List.of());
            role.addPermission("network.chat");

            UdpConnection connection = Mockito.mock(UdpConnection.class);
            when(connection.getRole()).thenReturn(role);

            assertThat(PermissionsManager.hasPermission(connection, "network.chat")).isTrue();
            assertThat(PermissionsManager.hasPermission(connection, "network.admin")).isFalse();
            assertThat(PermissionsManager.hasPermission((UdpConnection) null, "network.chat")).isFalse();
        }

        @Test
        @DisplayName("getRoleMeta should extract metadata from player's ExtendedRole and fallback to default")
        void shouldExtractRoleMetadata() {
            ExtendedRole metaRole = PermissionsManager.createRole("MetaRank", "Desc", Color.white, List.of());
            metaRole.setMeta("max_claims", "25");

            IsoPlayer player = createMockPlayer("MetaGuy", 0L, metaRole);

            assertThat(PermissionsManager.getRoleMeta(player, "max_claims", "1")).isEqualTo("25");
            assertThat(PermissionsManager.getRoleMeta(player, "non_existent", "default_val")).isEqualTo("default_val");
            assertThat(PermissionsManager.getRoleMeta(null, "max_claims", "fallback")).isEqualTo("fallback");
        }

        @Test
        @DisplayName("Personal explicit negation should immediately block access even if player was granted positive node")
        void personalNegationShouldWinOverPersonalPositive() {
            PermissionsManager.grantPermissionToPlayer("DualUser", "chat.send");
            PermissionsManager.grantPermissionToPlayer("DualUser", "-chat.send");

            IsoPlayer player = createMockPlayer("DualUser", 0L, null);

            assertThat(PermissionsManager.hasPermission(player, "chat.send")).isFalse();
        }

        @Test
        @DisplayName("Null and blank input guards across permission grant methods")
        void testNullAndBlankInputGuards() {
            assertThat(PermissionsManager.grantPermissionToPlayer(null, "perm")).isFalse();
            assertThat(PermissionsManager.grantPermissionToPlayer("User", null)).isFalse();
            assertThat(PermissionsManager.grantPermissionToPlayer("User", "  ")).isFalse();

            assertThat(PermissionsManager.revokePermissionFromPlayer(null, "perm")).isFalse();
            assertThat(PermissionsManager.revokePermissionFromPlayer("User", null)).isFalse();

            assertThat(PermissionsManager.hasPermission((IsoPlayer) null, "perm")).isFalse();
            assertThat(PermissionsManager.hasPermission(createMockPlayer("U", 0L, null), null)).isFalse();
            assertThat(PermissionsManager.hasPermission(createMockPlayer("U", 0L, null), "   ")).isFalse();
        }
    }

    // =========================================================================
    // 11. Debug Mode, Lazy Eviction, SteamID Role Assignment & Color Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("11. Debug Mode, Lazy Eviction, SteamID Role Assignment & Color Edge Cases")
    class EdgeCasesAndDebugModeTests {
        @Test
        @DisplayName("Core.debug in singleplayer/standalone should grant universal permission and capability bypass")
        void debugModeShouldBypassAllPermissionsAndCapabilities() {
            boolean previousDebug = zombie.core.Core.debug;
            boolean previousServer = GameServer.server;
            boolean previousClient = GameClient.client;

            try {
                // Activate standalone debug environment
                zombie.core.Core.debug = true;
                GameServer.server = false;
                GameClient.client = false;

                assertThat(Role.isUsingDebugMode()).isTrue();

                IsoPlayer playerWithoutPerms = createMockPlayer("Noob", 0L, null);
                UdpConnection connection = Mockito.mock(UdpConnection.class);

                assertThat(PermissionsManager.hasPermission(playerWithoutPerms, "unregistered.super.admin.command")).isTrue();
                assertThat(PermissionsManager.hasPermission(connection, "unregistered.super.admin.command")).isTrue();
                assertThat(PermissionsManager.hasCapability(playerWithoutPerms, Capability.AddItem)).isTrue();
                assertThat(PermissionsManager.hasCapability(connection, Capability.AddItem)).isTrue();
                assertThat(PermissionsManager.hasCapability("Noob", Capability.AddItem)).isTrue();
            } finally {
                Core.debug = previousDebug;
                GameServer.server = previousServer;
                GameClient.client = previousClient;
            }
        }

        @Test
        @DisplayName("getPlayerPermissions and getPlayerPermissionEntries should lazily evict expired permissions on read")
        void shouldLazilyEvictExpiredPermissionsOnRead() throws InterruptedException {
            PermissionsManager.grantPermissionToPlayer("LazyUser", "perm.short", 50, TimeUnit.MILLISECONDS);
            PermissionsManager.grantPermissionToPlayer("LazyUser", "perm.permanent");

            assertThat(PermissionsManager.getPlayerPermissions("LazyUser")).containsExactlyInAnyOrder("perm.short", "perm.permanent");

            Thread.sleep(80);

            // Reading should automatically filter out and purge the expired node
            Set<String> activePerms = PermissionsManager.getPlayerPermissions("LazyUser");
            assertThat(activePerms).containsExactly("perm.permanent");

            Collection<PlayerPermission> activeEntries = PermissionsManager.getPlayerPermissionEntries("LazyUser");
            assertThat(activeEntries).hasSize(1);
            assertThat(activeEntries.iterator().next().node()).isEqualTo("perm.permanent");
        }

        @Test
        @DisplayName("assignRole by SteamID string should find active player and assign role")
        void shouldAssignRoleBySteamID64String() {
            ExtendedRole vipRole = PermissionsManager.createRole("SteamVip", "VIP", Color.white, List.of());

            long steamId64 = 76561199000000001L;
            IsoPlayer steamPlayer = createMockPlayer("SteamUserX", steamId64, null);

            GameServer.server = true;
            if (GameServer.Players != null) {
                GameServer.Players.clear();
                GameServer.Players.add(steamPlayer);
            }

            try {
                boolean assigned = PermissionsManager.assignRole(String.valueOf(steamId64), "SteamVip");
                assertThat(assigned).isTrue();
                Mockito.verify(steamPlayer).setRole(vipRole);
            } finally {
                GameServer.server = false;
                if (GameServer.Players != null) {
                    GameServer.Players.clear();
                }
            }
        }

        @Test
        @DisplayName("hasCapability by username should resolve player capability")
        void shouldEvaluateCapabilityByUsername() {
            ExtendedRole capRole = PermissionsManager.createRole("CapMaster", "Desc", Color.white, List.of(Capability.CanSeePlayersStats));
            IsoPlayer player = createMockPlayer("OnlineSearchGuy", 0L, capRole);

            GameServer.server = true;
            if (GameServer.Players != null) {
                GameServer.Players.clear();
                GameServer.Players.add(player);
            }

            try {
                assertThat(PermissionsManager.hasCapability("OnlineSearchGuy", Capability.CanSeePlayersStats)).isTrue();
                assertThat(PermissionsManager.hasCapability("OnlineSearchGuy", Capability.SaveWorld)).isFalse();
                assertThat(PermissionsManager.hasCapability("   ", Capability.CanSeePlayersStats)).isFalse();
                assertThat(PermissionsManager.hasCapability("OnlineSearchGuy", null)).isFalse();
            } finally {
                GameServer.server = false;
                if (GameServer.Players != null) {
                    GameServer.Players.clear();
                }
            }
        }

        @Test
        @DisplayName("Malformed color string in YAML config should safely fallback to Color.white without crash")
        void malformedColorShouldFallbackToWhite() throws IOException {
            Path customConfigPath = tempFolder.resolve("broken-color-permissions.yml");

            String yamlWithBadColor = """
                    groups:
                      broken_color_group:
                        description: "Bad Color Role"
                        color: "not-a-number,255"
                        prefix: "[BadColor] "
                        suffix: ""
                        permissions: []
                        capabilities: []
                        parents: []
                    users: {}
                    """;
            Files.writeString(customConfigPath, yamlWithBadColor);

            PermissionsManager.loadPermissionsConfig(customConfigPath);

            Role createdRole = Roles.getRole("broken_color_group");
            assertThat(createdRole).isNotNull();
            assertThat(createdRole.getColor()).isEqualTo(Color.white);
        }

        @Test
        @DisplayName("Role permission check on null or non-ExtendedRole should fallback to standard capability matching")
        void vanillaRolePermissionFallback() {
            Role vanillaRole = new Role("VanillaUser");
            vanillaRole.addCapability(Capability.SeePlayersConnected);
            Roles.getRoles().add(vanillaRole);

            assertThat(PermissionsManager.hasPermission(vanillaRole, "SeePlayersConnected")).isTrue();
            assertThat(PermissionsManager.hasPermission(vanillaRole, "custom.non.capability")).isFalse();
            assertThat(PermissionsManager.hasPermission((Role) null, "SeePlayersConnected")).isFalse();
        }
    }
    // =========================================================================
    // Mock Helpers
    // =========================================================================

    private static IsoPlayer createMockPlayer(String username, long steamId64, Role role) {
        IsoPlayer player = Mockito.mock(IsoPlayer.class);
        when(player.getUsername()).thenReturn(username);
        when(player.getSteamID()).thenReturn(steamId64);
        when(player.getRole()).thenReturn(role != null ? role : Roles.getDefaultForUser());
        return player;
    }
}