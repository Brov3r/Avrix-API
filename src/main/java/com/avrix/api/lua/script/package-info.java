/**
 * Custom Lua script loading and isolated Lua environments.
 * <p>
 * {@link com.avrix.api.lua.script.LuaScriptLoader} loads individual files, selected module
 * lists and recursive groups from directories or JAR files. {@code require} resolves custom
 * modules before falling back to the game implementation.
 * <p>
 * Isolated environments use {@link com.avrix.api.lua.script.LuaEnvironmentPolicy} to control
 * visibility of game Lua, mod Lua, Project Zomboid Java exposure, Avrix exposure and other
 * custom scripts.
 */
package com.avrix.api.lua.script;
