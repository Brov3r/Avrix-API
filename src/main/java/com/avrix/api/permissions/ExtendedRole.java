package com.avrix.api.permissions;

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
     * Visual chat/display prefix (e.g., {@code "&c[Admin]&f "}).
     */
    private volatile String prefix = "";

    /**
     * Visual chat/display suffix (e.g., {@code " &6★"}).
     */
    private volatile String suffix = "";

    /**
     * Creates a new extended role with the specified unique name.
     *
     * @param name the unique name of this role
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public ExtendedRole(String name) {
        this(name, "", Color.white);
    }

    /**
     * Creates a new extended role with full display metadata.
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
     * Adds a custom permission node directly to this role if the role is not read-only.
     *
     * @param permissionNode the permission node to grant
     * @return {@code true} if the permission was granted, {@code false} if the node is invalid,
     * already present, or if the role is read-only
     * @apiNote Permission strings are stripped and normalized to lowercase (Locale.ROOT).
     */
    public boolean addPermission(String permissionNode) {
        if (this.isReadOnly() || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        return this.customPermissions.add(normalize(permissionNode));
    }

    /**
     * Removes a custom permission node from this role if the role is not read-only.
     *
     * @param permissionNode the permission node to revoke
     * @return {@code true} if the permission was revoked, {@code false} if the node is invalid,
     * was not present, or if the role is read-only
     */
    public boolean removePermission(String permissionNode) {
        if (this.isReadOnly() || permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        return this.customPermissions.remove(normalize(permissionNode));
    }

    /**
     * Checks whether this role has been granted the specified permission node directly
     * or via its inheritance tree (parent roles).
     *
     * @param permissionNode the permission node to evaluate
     * @return {@code true} if granted directly or inherited from any parent role
     */
    public boolean hasPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        return hasPermissionInternal(normalize(permissionNode), new HashSet<>());
    }

    /**
     * Internal recursive evaluation with cycle-detection guard.
     *
     * @param normalizedTarget the normalized permission query
     * @param visitedRoles     set of visited role names to prevent infinite recursion
     * @return {@code true} if matched
     */
    private boolean hasPermissionInternal(String normalizedTarget, Set<String> visitedRoles) {
        if (!visitedRoles.add(this.getName().toLowerCase(Locale.ROOT))) {
            return false;
        }

        // Check local permissions
        if (hasLocalPermission(normalizedTarget)) {
            return true;
        }

        // Traverse inheritance tree
        for (String parentName : this.parents) {
            Role parentRole = Roles.getRole(parentName);
            if (parentRole instanceof ExtendedRole parentExtended) {
                if (parentExtended.hasPermissionInternal(normalizedTarget, visitedRoles)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks whether this specific role directly holds the specified permission node,
     * ignoring any inherited parent roles.
     *
     * @param permissionNode the permission node to evaluate
     * @return {@code true} if directly held
     */
    public boolean hasLocalPermission(String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) {
            return false;
        }

        String target = normalize(permissionNode);
        for (String granted : this.customPermissions) {
            if (matchesPermission(granted, target)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns an unmodifiable snapshot view of all custom permissions assigned directly to this role.
     *
     * @return an unmodifiable set of normalized permission strings
     */
    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(this.customPermissions);
    }

    /**
     * Clears all custom permissions from this role if the role is not read-only.
     */
    public void clearPermissions() {
        if (!this.isReadOnly()) {
            this.customPermissions.clear();
        }
    }

    /**
     * Adds a parent role to inherit permissions from.
     *
     * @param parentRoleName the name of the parent role
     * @return {@code true} if added, {@code false} if invalid, self-referencing, or already present
     */
    public boolean addParent(String parentRoleName) {
        if (this.isReadOnly() || parentRoleName == null || parentRoleName.isBlank()) {
            return false;
        }

        String cleanParent = parentRoleName.strip();
        if (cleanParent.equalsIgnoreCase(this.getName())) {
            return false; // Prevent immediate self-inheritance
        }

        return this.parents.add(cleanParent);
    }

    /**
     * Removes a parent role from the inheritance tree.
     *
     * @param parentRoleName the parent role name to remove
     * @return {@code true} if removed successfully
     */
    public boolean removeParent(String parentRoleName) {
        if (this.isReadOnly() || parentRoleName == null || parentRoleName.isBlank()) {
            return false;
        }

        return this.parents.remove(parentRoleName.strip());
    }

    /**
     * Returns an unmodifiable set of parent role names directly inherited by this role.
     *
     * @return an unmodifiable set of parent role names
     */
    public Set<String> getParents() {
        return Collections.unmodifiableSet(this.parents);
    }

    /**
     * Clears all inherited parent roles from this role.
     */
    public void clearParents() {
        if (!this.isReadOnly()) {
            this.parents.clear();
        }
    }

    /**
     * Gets the visual chat/display prefix.
     *
     * @return the prefix string, never null
     */
    public String getPrefix() {
        return this.prefix;
    }

    /**
     * Sets the visual chat/display prefix.
     *
     * @param prefix the prefix string
     */
    public void setPrefix(String prefix) {
        if (!this.isReadOnly()) {
            this.prefix = prefix != null ? prefix : "";
        }
    }

    /**
     * Gets the visual chat/display suffix.
     *
     * @return the suffix string, never null
     */
    public String getSuffix() {
        return this.suffix;
    }

    /**
     * Sets the visual chat/display suffix.
     *
     * @param suffix the suffix string
     */
    public void setSuffix(String suffix) {
        if (!this.isReadOnly()) {
            this.suffix = suffix != null ? suffix : "";
        }
    }

    /**
     * Associates arbitrary metadata with this role.
     *
     * @param key   the metadata key
     * @param value the metadata value
     */
    public void setMeta(String key, String value) {
        if (this.isReadOnly() || key == null || key.isBlank()) {
            return;
        }

        if (value == null) {
            this.metadata.remove(key.strip().toLowerCase(Locale.ROOT));
        } else {
            this.metadata.put(key.strip().toLowerCase(Locale.ROOT), value);
        }
    }

    /**
     * Retrieves a metadata value by key, resolving through inherited parents if not defined locally.
     *
     * @param key the metadata key
     * @return the metadata value, or {@code null} if not found
     */
    public String getMeta(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String normalizedKey = key.strip().toLowerCase(Locale.ROOT);
        String local = this.metadata.get(normalizedKey);
        if (local != null) {
            return local;
        }

        for (String parentName : this.parents) {
            Role parent = Roles.getRole(parentName);
            if (parent instanceof ExtendedRole parentExtended) {
                String inherited = parentExtended.getMeta(key);
                if (inherited != null) {
                    return inherited;
                }
            }
        }

        return null;
    }

    /**
     * Returns an unmodifiable snapshot view of all direct metadata key-value pairs.
     *
     * @return unmodifiable map of metadata
     */
    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(this.metadata);
    }

    /**
     * Normalizes a permission string by trimming whitespace and converting to lowercase using ROOT locale.
     *
     * @param raw the raw input string
     * @return the normalized permission string
     */
    private static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Evaluates whether a granted permission rule satisfies a requested permission node.
     *
     * @param granted   the pattern or exact permission registered in the role
     * @param requested the target permission being queried
     * @return {@code true} if {@code granted} encompasses {@code requested}
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
}