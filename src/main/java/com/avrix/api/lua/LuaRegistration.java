package com.avrix.api.lua;

/**
 * Opaque handle returned by {@link LuaExposer} registration methods.
 * <p>
 * A handle is used with {@link LuaExposer#unregister(LuaRegistration)} to remove an exposure
 * both from future Lua environments and, when possible, from the current one. Handles are
 * created only by {@link LuaExposer}.
 */
public final class LuaRegistration {

    private final long id;
    private final Object key;
    private final LuaExposure exposure;

    /**
     * Creates a registration handle.
     *
     * @param id       numeric identifier used for diagnostics
     * @param key      registry key
     * @param exposure exposure implementation
     */
    LuaRegistration(long id, Object key, LuaExposure exposure) {
        this.id = id;
        this.key = key;
        this.exposure = exposure;
    }

    /**
     * Returns the numeric identifier of this registration.
     *
     * @return registration identifier
     */
    public long id() {
        return id;
    }

    /**
     * Returns the registry key of this registration.
     *
     * @return internal registry key
     */
    Object key() {
        return key;
    }

    /**
     * Returns the exposure implementation.
     *
     * @return exposure implementation
     */
    LuaExposure exposure() {
        return exposure;
    }

    /**
     * Returns a diagnostic representation of this registration.
     *
     * @return registration description
     */
    @Override
    public String toString() {
        return "LuaRegistration[" + id + ", " + exposure + "]";
    }
}
