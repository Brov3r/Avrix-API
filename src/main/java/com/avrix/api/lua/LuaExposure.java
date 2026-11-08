package com.avrix.api.lua;

import se.krka.kahlua.vm.KahluaTable;

/**
 * Internal contract for a single Java-to-Lua exposure.
 * <p>
 * Implementations are stateful because the game recreates its Lua environment on every
 * {@code LuaManager.init()} call. The exposure remembers which environment it has already
 * been applied to and can remove itself from that environment.
 */
sealed interface LuaExposure permits ClassExposure, MethodExposure, GlobalFunctionExposure,
        GlobalObjectExposure {

    /**
     * Applies this exposure to the given Lua environment.
     *
     * @param environment current Lua environment
     */
    void apply(KahluaTable environment);

    /**
     * Removes this exposure from the given Lua environment.
     *
     * @param environment Lua environment to clean up
     */
    void remove(KahluaTable environment);

    /**
     * Checks whether this exposure has already been applied to the given environment.
     *
     * @param environment Lua environment to check
     * @return {@code true} if the exposure is applied to the environment
     */
    boolean isAppliedIn(KahluaTable environment);

    /**
     * Clears the tracked environment after the game replaces its Lua state.
     */
    void invalidate();

    /**
     * Checks whether this exposure touches the given Java type.
     *
     * @param type Java type to check
     * @return {@code true} if the exposure affects the type
     */
    boolean affects(Class<?> type);
}
