package com.avrix.api.mixins.common;

import com.avrix.api.lua.LuaExposer;
import com.avrix.api.lua.script.LuaScriptLoader;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.Lua.LuaManager;

/**
 * ClassTransform mixin for {@link LuaManager} that reapplies Avrix Lua state after the game
 * creates or recreates its Lua environment and tracks which globals are game or mod Lua.
 */
@CTransformer(value = LuaManager.class)
public class LuaManagerMixin {

    /**
     * Prevents instantiation of the transformer class.
     */
    private LuaManagerMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Reinitializes Avrix Lua state at the end of {@link LuaManager#init()}.
     * <p>
     * The injection point is after the game has created the Lua environment and exposed its
     * built-in classes, so custom tables can safely be added.
     */
    @CInject(
            method = "init()V",
            target = @CTarget("TAIL")
    )
    private static void afterLuaInit() {
        LuaScriptLoader.onLuaEnvironmentCreated();
        LuaExposer.applyPending();
        LuaScriptLoader.onLuaEnvironmentReady();
    }

    /**
     * Starts Lua global tracking before a {@code LoadDirBase} pass.
     *
     * @param sub          Lua subdirectory being loaded
     * @param onlyChecksum whether files are only checksummed
     */
    @CInject(
            method = "LoadDirBase(Ljava/lang/String;Z)V",
            target = @CTarget("HEAD")
    )
    private static void beforeLoadDir(String sub, boolean onlyChecksum) {
        LuaScriptLoader.onLoadDirBegin();
    }

    /**
     * Finishes Lua global tracking after a {@code LoadDirBase} pass.
     *
     * @param sub          Lua subdirectory being loaded
     * @param onlyChecksum whether files are only checksummed
     */
    @CInject(
            method = "LoadDirBase(Ljava/lang/String;Z)V",
            target = @CTarget("TAIL")
    )
    private static void afterLoadDir(String sub, boolean onlyChecksum) {
        LuaScriptLoader.onLoadDirEnd();
    }

    /**
     * Records each Lua file loaded by {@code LoadDirBase} so game and mod globals can be
     * distinguished for isolated environments.
     *
     * @param filename absolute Lua file path
     */
    @CInject(
            method = "RunLua(Ljava/lang/String;)Ljava/lang/Object;",
            target = @CTarget("HEAD")
    )
    private static void trackLuaFile(String filename) {
        LuaScriptLoader.onLuaFileLoaded(filename);
    }
}
