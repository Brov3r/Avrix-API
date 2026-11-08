package com.avrix.api.lua.script;

import java.util.Objects;

/**
 * Result of loading one custom Lua module.
 *
 * @param module canonical module identifier used for diagnostics
 * @param value  value returned by the module, or {@code null}
 * @param error  load or execution error, or {@code null}
 */
public record LuaScriptResult(String module, Object value, Throwable error) {

    /**
     * Creates a script result.
     *
     * @param module canonical module identifier
     * @param value  returned value
     * @param error  error, or {@code null}
     */
    public LuaScriptResult {
        Objects.requireNonNull(module, "module");
    }

    /**
     * Checks whether the module was loaded without an error.
     *
     * @return {@code true} if loading succeeded
     */
    public boolean isSuccess() {
        return error == null;
    }
}
