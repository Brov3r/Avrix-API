package com.avrix.api.lua.script;

/**
 * Resolved Lua module inside a script source.
 *
 * @param source script source
 * @param path   relative path inside the source
 * @param id     canonical module identifier
 */
record LuaScriptModule(LuaScriptSource source, String path, String id) {
}
