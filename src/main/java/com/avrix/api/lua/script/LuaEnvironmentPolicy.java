package com.avrix.api.lua.script;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Visibility policy for an isolated custom Lua environment.
 * <p>
 * The policy controls which categories of the shared Lua environment an isolated script can
 * read through its environment fallback. Explicitly allowed globals override category flags,
 * while explicitly denied globals always win.
 * <p>
 * This is visibility isolation, not a security sandbox. Allowed tables and functions are
 * shared by reference and can still be modified by the script.
 * <p>
 * Example:
 * <pre>{@code
 * LuaEnvironmentPolicy policy = LuaEnvironmentPolicy.isolated()
 *         .toBuilder()
 *         .includeAvrixExposure(true)
 *         .allowGlobal("print")
 *         .build();
 * }</pre>
 */
public final class LuaEnvironmentPolicy {

    private final boolean includeStandardLibrary;
    private final boolean includeProjectZomboidExposure;
    private final boolean includeAvrixExposure;
    private final boolean includeGameLua;
    private final boolean includeModLua;
    private final boolean includeCustomLua;
    private final boolean includeUnknownGlobals;
    private final boolean allowVanillaRequire;
    private final Set<String> allowedGlobals;
    private final Set<String> deniedGlobals;

    private LuaEnvironmentPolicy(Builder builder) {
        includeStandardLibrary = builder.includeStandardLibrary;
        includeProjectZomboidExposure = builder.includeProjectZomboidExposure;
        includeAvrixExposure = builder.includeAvrixExposure;
        includeGameLua = builder.includeGameLua;
        includeModLua = builder.includeModLua;
        includeCustomLua = builder.includeCustomLua;
        includeUnknownGlobals = builder.includeUnknownGlobals;
        allowVanillaRequire = builder.allowVanillaRequire;
        allowedGlobals = Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedGlobals));
        deniedGlobals = Collections.unmodifiableSet(new LinkedHashSet<>(builder.deniedGlobals));
    }

    /**
     * Creates a permissive policy equivalent to the shared environment.
     *
     * @return permissive policy
     */
    public static LuaEnvironmentPolicy shared() {
        return builder().build();
    }

    /**
     * Creates a restrictive policy that exposes only standard Lua globals and custom modules.
     *
     * @return isolated policy
     */
    public static LuaEnvironmentPolicy isolated() {
        return builder()
                .includeProjectZomboidExposure(false)
                .includeAvrixExposure(false)
                .includeGameLua(false)
                .includeModLua(false)
                .includeCustomLua(false)
                .includeUnknownGlobals(false)
                .allowVanillaRequire(false)
                .build();
    }

    /**
     * Creates a policy builder with permissive defaults.
     *
     * @return policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this policy's values.
     *
     * @return policy builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.includeStandardLibrary = includeStandardLibrary;
        builder.includeProjectZomboidExposure = includeProjectZomboidExposure;
        builder.includeAvrixExposure = includeAvrixExposure;
        builder.includeGameLua = includeGameLua;
        builder.includeModLua = includeModLua;
        builder.includeCustomLua = includeCustomLua;
        builder.includeUnknownGlobals = includeUnknownGlobals;
        builder.allowVanillaRequire = allowVanillaRequire;
        builder.allowedGlobals.addAll(allowedGlobals);
        builder.deniedGlobals.addAll(deniedGlobals);
        return builder;
    }

    /**
     * Returns whether standard Kahlua globals are copied into the isolated environment.
     *
     * @return {@code true} if standard globals are available
     */
    public boolean includeStandardLibrary() {
        return includeStandardLibrary;
    }

    /**
     * Returns whether Project Zomboid's Java exposure is visible.
     *
     * @return {@code true} if the category is visible
     */
    public boolean includeProjectZomboidExposure() {
        return includeProjectZomboidExposure;
    }

    /**
     * Returns whether Avrix's Java exposure is visible.
     *
     * @return {@code true} if the category is visible
     */
    public boolean includeAvrixExposure() {
        return includeAvrixExposure;
    }

    /**
     * Returns whether globals created by game Lua are visible.
     *
     * @return {@code true} if the category is visible
     */
    public boolean includeGameLua() {
        return includeGameLua;
    }

    /**
     * Returns whether globals created by mod Lua are visible.
     *
     * @return {@code true} if the category is visible
     */
    public boolean includeModLua() {
        return includeModLua;
    }

    /**
     * Returns whether globals created by other custom scripts in the shared environment are visible.
     *
     * @return {@code true} if the category is visible
     */
    public boolean includeCustomLua() {
        return includeCustomLua;
    }

    /**
     * Returns whether globals with unknown category are visible.
     *
     * @return {@code true} if unknown globals are visible
     */
    public boolean includeUnknownGlobals() {
        return includeUnknownGlobals;
    }

    /**
     * Returns whether {@code require} is allowed to fall back to the shared environment when a
     * module is not found in custom sources.
     *
     * @return {@code true} if vanilla require fallback is allowed
     */
    public boolean allowVanillaRequire() {
        return allowVanillaRequire;
    }

    /**
     * Returns explicitly allowed globals.
     *
     * @return immutable set of global names
     */
    public Set<String> allowedGlobals() {
        return allowedGlobals;
    }

    /**
     * Returns explicitly denied globals.
     *
     * @return immutable set of global names
     */
    public Set<String> deniedGlobals() {
        return deniedGlobals;
    }

    /**
     * Builder for {@link LuaEnvironmentPolicy}.
     */
    public static final class Builder {

        private boolean includeStandardLibrary = true;
        private boolean includeProjectZomboidExposure = true;
        private boolean includeAvrixExposure = true;
        private boolean includeGameLua = true;
        private boolean includeModLua = true;
        private boolean includeCustomLua = true;
        private boolean includeUnknownGlobals = true;
        private boolean allowVanillaRequire = true;
        private final Set<String> allowedGlobals = new LinkedHashSet<>();
        private final Set<String> deniedGlobals = new LinkedHashSet<>();

        private Builder() {
        }

        /**
         * Sets whether standard Kahlua globals are copied into the isolated environment.
         *
         * @param include {@code true} to include standard globals
         * @return this builder
         */
        public Builder includeStandardLibrary(boolean include) {
            includeStandardLibrary = include;
            return this;
        }

        /**
         * Sets whether Project Zomboid's Java exposure is visible.
         *
         * @param include {@code true} to include the category
         * @return this builder
         */
        public Builder includeProjectZomboidExposure(boolean include) {
            includeProjectZomboidExposure = include;
            return this;
        }

        /**
         * Sets whether Avrix's Java exposure is visible.
         *
         * @param include {@code true} to include the category
         * @return this builder
         */
        public Builder includeAvrixExposure(boolean include) {
            includeAvrixExposure = include;
            return this;
        }

        /**
         * Sets whether game Lua globals are visible.
         *
         * @param include {@code true} to include the category
         * @return this builder
         */
        public Builder includeGameLua(boolean include) {
            includeGameLua = include;
            return this;
        }

        /**
         * Sets whether mod Lua globals are visible.
         *
         * @param include {@code true} to include the category
         * @return this builder
         */
        public Builder includeModLua(boolean include) {
            includeModLua = include;
            return this;
        }

        /**
         * Sets whether custom Lua globals from the shared environment are visible.
         *
         * @param include {@code true} to include the category
         * @return this builder
         */
        public Builder includeCustomLua(boolean include) {
            includeCustomLua = include;
            return this;
        }

        /**
         * Sets whether globals with unknown category are visible.
         *
         * @param include {@code true} to include unknown globals
         * @return this builder
         */
        public Builder includeUnknownGlobals(boolean include) {
            includeUnknownGlobals = include;
            return this;
        }

        /**
         * Sets whether {@code require} may fall back to the shared environment.
         *
         * @param allow {@code true} to allow vanilla require fallback
         * @return this builder
         */
        public Builder allowVanillaRequire(boolean allow) {
            allowVanillaRequire = allow;
            return this;
        }

        /**
         * Allows one global even when its category is disabled.
         *
         * @param name global name
         * @return this builder
         */
        public Builder allowGlobal(String name) {
            allowedGlobals.add(Objects.requireNonNull(name, "name"));
            return this;
        }

        /**
         * Allows multiple globals even when their categories are disabled.
         *
         * @param names global names
         * @return this builder
         */
        public Builder allowGlobals(Collection<String> names) {
            Objects.requireNonNull(names, "names");
            for (String name : names) {
                allowGlobal(name);
            }
            return this;
        }

        /**
         * Denies one global even when its category is enabled.
         *
         * @param name global name
         * @return this builder
         */
        public Builder denyGlobal(String name) {
            deniedGlobals.add(Objects.requireNonNull(name, "name"));
            return this;
        }

        /**
         * Denies multiple globals even when their categories are enabled.
         *
         * @param names global names
         * @return this builder
         */
        public Builder denyGlobals(Collection<String> names) {
            Objects.requireNonNull(names, "names");
            for (String name : names) {
                denyGlobal(name);
            }
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return immutable policy
         */
        public LuaEnvironmentPolicy build() {
            return new LuaEnvironmentPolicy(this);
        }
    }
}
