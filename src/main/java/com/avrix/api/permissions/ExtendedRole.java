package com.avrix.api.permissions;

import zombie.characters.Capability;
import zombie.characters.Role;
import zombie.characters.Roles;
import zombie.core.Color;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Extended role domain entity that enriches Project Zomboid's native {@link Role} subsystem
 * with hierarchical wildcard permissions, role inheritance trees, chat formatting metadata
 * (prefixes/suffixes), and arbitrary metadata key-value pairs.
 * <p>
 * Parent roles contribute custom permissions and metadata. Native capabilities are not inherited
 * and must be declared explicitly on each role. Negations always take precedence over grants.
 */
public class ExtendedRole extends Role {

    /**
     * Suffix used to denote wildcard permission branches (e.g. {@code "avrix.admin.*"}).
     */
    private static final String WILDCARD_SUFFIX = ".*";

    /**
     * Root wildcard character granting all possible permissions.
     */
    private static final String GLOBAL_WILDCARD = "*";

    /**
     * In-memory thread-safe storage for normalized custom permission strings directly assigned to this role.
     */
    private final Set<String> customPermissions;

    /**
     * Thread-safe collection of parent role names inherited by this role.
     */
    private final Set<String> parents;

    /**
     * Thread-safe key-value store for arbitrary plugin metadata (e.g., limits, homes, multipliers).
     */
    private final Map<String, String> metadata;

    /**
     * Visual chat/display prefix (e.g., {@code "[Admin] "}).
     */
    private volatile String prefix = "";

    /**
     * Visual chat/display suffix (e.g., {@code " ★"}).
     */
    private volatile String suffix = "";

    /**
     * Constructs a new extended role with the specified unique name and default white color.
     *
     * @param name the unique name of this role
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public ExtendedRole(String name) {
        this(name, "", Color.white);
    }

    /**
     * Constructs a new extended role with full display metadata.
     *
     * @param name        the unique name of this role
     * @param description a human-readable role description
     * @param color       the display color of this role
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public ExtendedRole(String name, String description, Color color) {
        super(sanitizeRoleName(name));
        this.customPermissions = ConcurrentHashMap.newKeySet();
        this.parents = new CopyOnWriteArraySet<>();
        this.metadata = new ConcurrentHashMap<>();

        if (description != null && !description.isEmpty()) {
            this.setDescription(description);
        }
        if (color != null) {
            this.setColor(color);
        }
    }

    /**
     * Validates and sanitizes the role name before delegating to the superclass constructor.
     *
     * @param name the raw role name
     * @return a valid stripped name
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    private static String sanitizeRoleName(String name) {
        Objects.requireNonNull(name, "Role name cannot be null");
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }
        return trimmed;
    }

    /**
     * Grants a native Project Zomboid capability to this role, bypassing the vanilla
     * {@code readOnly} restriction to ensure declarative YAML configuration is authoritative.
     *
     * @param capability the capability to grant
     * @return {@code true} if the capability set was modified, {@code false} otherwise
     */
    @Override
    public boolean addCapability(Capability capability) {
        if (capability == null || capability == Capability.None) {
            return false;
        }
        HashSet<Capability> caps = this.getCapabilities();
        if (caps != null) {
            return caps.add(capability);
        }
        return false;
    }

    /**
     * Clears all native capabilities from this role, bypassing vanilla {@code readOnly} restrictions.
     */
    @Override
    public void cleanCapability() {
        HashSet<Capability> caps = this.getCapabilities();
        if (caps != null) {
            caps.clear();
        }
    }

    /**
     * Evaluates whether this role holds the specified native Project Zomboid capability.
     * Guaranteed to return {@code true} for {@link Capability#LoginOnServer} on all roles whose
     * name is not exactly {@code banned}.
     *
     * @param capability the capability to verify
     * @return {@code true} if held, {@code false} otherwise
     */
    @Override
    public boolean hasCapability(Capability capability) {
        if (capability == null || capability == Capability.None) {
            return false;
        }
        if (capability == Capability.LoginOnServer && !"banned".equalsIgnoreCase(getName())) {
            return true;
        }
        HashSet<Capability> caps = this.getCapabilities();
        return caps != null && caps.contains(capability);
    }

    /**
     * Grants a custom permission string directly to this role.
     *
     * @param permissionNode the permission node (e.g. {@code "avrix.commands.teleport"})
     * @return {@code true} if granted, {@code false} if the node is null, blank, or already present
     */
    public boolean addPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }
        return this.customPermissions.add(normalize(permissionNode));
    }

    /**
     * Revokes a custom permission string directly assigned to this role.
     *
     * @param permissionNode the permission node to revoke
     * @return {@code true} if revoked, {@code false} if not present or node is invalid
     */
    public boolean removePermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }
        return this.customPermissions.remove(normalize(permissionNode));
    }

    /**
     * Evaluates whether this role possesses the target permission node,
     * checking direct grants, explicit negations, wildcard patterns, and the inherited parent role hierarchy.
     * <p>
     * Negations always take precedence over positive grants, including grants inherited from parent roles.
     *
     * @param permissionNode the target permission node to verify
     * @return {@code true} if authorized directly or through inheritance, {@code false} otherwise
     */
    public boolean hasPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }
        return evaluatePermission(normalize(permissionNode), new HashSet<>()).isAllowed();
    }

    /**
     * Internal recursive evaluation resolving permission hierarchy with cycle detection.
     *
     * @param normalizedTarget the lowercase normalized target node
     * @param visitedRoles     set of visited role names to prevent circular inheritance loops
     * @return aggregated allow/deny decision
     */
    private PermissionDecision evaluatePermission(String normalizedTarget, Set<String> visitedRoles) {
        if (!visitedRoles.add(this.getName().toLowerCase(Locale.ROOT))) {
            return PermissionDecision.UNSET;
        }

        PermissionDecision decision = evaluateLocalPermission(normalizedTarget);
        for (String parentName : this.parents) {
            ExtendedRole parentRole = resolveParentRole(parentName);
            if (parentRole != null) {
                decision = decision.merge(parentRole.evaluatePermission(normalizedTarget, visitedRoles));
            }
        }
        return decision;
    }

    /**
     * Evaluates only the permission patterns directly assigned to this role.
     *
     * @param normalizedTarget the lowercase normalized target node
     * @return local allow/deny decision
     */
    private PermissionDecision evaluateLocalPermission(String normalizedTarget) {
        boolean allowed = false;
        boolean denied = false;
        for (String granted : this.customPermissions) {
            if (granted.startsWith("-")) {
                String positivePattern = granted.substring(1);
                if (!positivePattern.isEmpty() && matchesPermission(positivePattern, normalizedTarget)) {
                    denied = true;
                }
            } else if (matchesPermission(granted, normalizedTarget)) {
                allowed = true;
            }
        }
        return new PermissionDecision(allowed, denied);
    }

    /**
     * Checks whether this role or any of its parents explicitly denies the requested permission.
     *
     * @param permissionNode the permission node to evaluate
     * @return {@code true} if an explicit negation matches
     */
    public boolean deniesPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }
        return evaluatePermission(normalize(permissionNode), new HashSet<>()).denied();
    }

    /**
     * Checks if this specific role directly holds the specified permission node,
     * ignoring any inherited parent roles. A local explicit negation overrides local grants.
     *
     * @param permissionNode the permission node to evaluate
     * @return {@code true} if held directly via exact match or local wildcard
     */
    public boolean hasLocalPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }
        return evaluateLocalPermission(normalize(permissionNode)).isAllowed();
    }

    /**
     * Retrieves an immutable snapshot of all custom permissions directly assigned to this role.
     *
     * @return immutable set of normalized permission strings
     */
    public Set<String> getPermissions() {
        return Set.copyOf(this.customPermissions);
    }

    /**
     * Clears all direct custom permissions from this role.
     */
    public void clearPermissions() {
        this.customPermissions.clear();
    }

    /**
     * Adds an inherited parent role to this role's inheritance tree.
     *
     * @param parentRoleName the unique name of the parent role
     * @return {@code true} if added, {@code false} if invalid, already present, or self-referencing
     */
    public boolean addParent(String parentRoleName) {
        if (parentRoleName == null || parentRoleName.isBlank()) {
            return false;
        }

        String cleanParent = parentRoleName.strip();
        if (cleanParent.equalsIgnoreCase(this.getName())) {
            return false;
        }
        for (String existing : this.parents) {
            if (existing.equalsIgnoreCase(cleanParent)) {
                return false;
            }
        }

        return this.parents.add(cleanParent);
    }

    /**
     * Removes an inherited parent role from this role.
     *
     * @param parentRoleName the name of the parent role to remove
     * @return {@code true} if removed, {@code false} otherwise
     */
    public boolean removeParent(String parentRoleName) {
        if (parentRoleName == null || parentRoleName.isBlank()) {
            return false;
        }
        String cleanParent = parentRoleName.strip();
        return this.parents.removeIf(parent -> parent.equalsIgnoreCase(cleanParent));
    }

    /**
     * Retrieves an immutable snapshot of all parent role names directly inherited by this role.
     *
     * @return immutable set of parent role names
     */
    public Set<String> getParents() {
        return Set.copyOf(this.parents);
    }

    /**
     * Clears all inherited parent roles from this role.
     */
    public void clearParents() {
        this.parents.clear();
    }

    /**
     * Returns the chat and display prefix assigned to this role.
     *
     * @return the prefix string, never null
     */
    public String getPrefix() {
        return this.prefix;
    }

    /**
     * Sets the chat and display prefix for this role.
     *
     * @param prefix the prefix string to set
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
    }

    /**
     * Returns the chat and display suffix assigned to this role.
     *
     * @return the suffix string, never null
     */
    public String getSuffix() {
        return this.suffix;
    }

    /**
     * Sets the chat and display suffix for this role.
     *
     * @param suffix the suffix string to set
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix != null ? suffix : "";
    }

    /**
     * Sets or removes a custom metadata key-value pair on this role.
     *
     * @param key   the case-insensitive metadata key
     * @param value the string value, or {@code null} to remove the key
     */
    public void setMeta(String key, String value) {
        if (key == null || key.isBlank()) return;

        if (value == null) {
            this.metadata.remove(key.strip().toLowerCase(Locale.ROOT));
        } else {
            this.metadata.put(key.strip().toLowerCase(Locale.ROOT), value);
        }
    }

    /**
     * Retrieves a metadata value by key, resolving hierarchically through parent roles if absent locally.
     *
     * @param key the case-insensitive metadata key
     * @return the resolved string value, or {@code null} if not found
     */
    public String getMeta(String key) {
        return getMeta(key, new HashSet<>());
    }

    /**
     * Resolves metadata with cycle protection and case-insensitive parent lookup.
     *
     * @param key          metadata key
     * @param visitedRoles role names already visited
     * @return resolved value or {@code null}
     */
    private String getMeta(String key, Set<String> visitedRoles) {
        if (key == null || key.isBlank()) return null;
        if (!visitedRoles.add(this.getName().toLowerCase(Locale.ROOT))) return null;

        String normalizedKey = key.strip().toLowerCase(Locale.ROOT);
        String local = this.metadata.get(normalizedKey);
        if (local != null) return local;

        for (String parentName : this.parents) {
            ExtendedRole parentExtended = resolveParentRole(parentName);
            if (parentExtended != null) {
                String inherited = parentExtended.getMeta(key, visitedRoles);
                if (inherited != null) return inherited;
            }
        }

        return null;
    }

    /**
     * Removes all direct metadata entries from this role.
     */
    public void clearMetadata() {
        this.metadata.clear();
    }

    /**
     * Retrieves an immutable snapshot of all direct metadata key-value pairs.
     *
     * @return immutable map of metadata
     */
    public Map<String, String> getMetadata() {
        return Map.copyOf(this.metadata);
    }

    /**
     * Normalizes a permission string by stripping whitespace and converting to lowercase.
     *
     * @param raw the raw input string
     * @return the normalized string
     */
    private static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a parent role by name using case-insensitive matching.
     *
     * @param parentName parent role name
     * @return matching extended role or {@code null}
     */
    private static ExtendedRole resolveParentRole(String parentName) {
        if (parentName == null || parentName.isBlank()) {
            return null;
        }

        List<Role> roles = Roles.getRoles();
        if (roles != null) {
            for (Role role : roles) {
                if (role instanceof ExtendedRole extended
                        && role.getName() != null
                        && role.getName().equalsIgnoreCase(parentName.strip())) {
                    return extended;
                }
            }
        }

        Role exact = Roles.getRole(parentName);
        return exact instanceof ExtendedRole extended ? extended : null;
    }

    /**
     * Evaluates whether a granted permission pattern encompasses a requested target node.
     * Supports exact matches, global wildcard ({@code "*"}), and sub-tree wildcards ({@code "module.*"}).
     *
     * @param granted   the pattern registered on the role
     * @param requested the target node being checked
     * @return {@code true} if granted covers requested
     */
    private static boolean matchesPermission(String granted, String requested) {
        if (GLOBAL_WILDCARD.equals(granted)) {
            return true;
        }
        if (granted.equals(requested)) {
            return true;
        }
        if (granted.endsWith(WILDCARD_SUFFIX)) {
            String prefix = granted.substring(0, granted.length() - WILDCARD_SUFFIX.length());
            return !prefix.isEmpty() && (requested.equals(prefix) || requested.startsWith(prefix + "."));
        }
        return false;
    }

    /**
     * Aggregated permission decision with deny-overrides semantics.
     *
     * @param allowed whether at least one positive grant matched
     * @param denied  whether at least one explicit negation matched
     */
    private record PermissionDecision(boolean allowed, boolean denied) {

        /**
         * Neutral decision used for cycle prevention and empty roles.
         */
        private static final PermissionDecision UNSET = new PermissionDecision(false, false);

        /**
         * Merges another decision into this one.
         *
         * @param other decision to merge
         * @return merged decision
         */
        private PermissionDecision merge(PermissionDecision other) {
            return new PermissionDecision(allowed || other.allowed, denied || other.denied);
        }

        /**
         * Returns whether the aggregated result grants access.
         *
         * @return {@code true} only when allowed and not denied
         */
        private boolean isAllowed() {
            return allowed && !denied;
        }
    }
}