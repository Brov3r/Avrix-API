package com.avrix.api.mixins.common;

import com.avrix.api.lua.LuaExposer;
import com.avrix.api.lua.script.LuaScriptLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.Lua.LuaManager;

/**
 * Mixin for {@link LuaManager} that reapplies the Avrix Lua state after the game creates
 * or recreates its Lua environment and tracks which globals belong to the game or to mods.
 */
@Mixin(LuaManager.class)
public class LuaManagerMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private LuaManagerMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Reinitializes the Avrix Lua state at the end of {@link LuaManager#init()}.
     * <p>
     * The injection point is after the game has created the Lua environment and exposed its
     * built-in classes, so custom tables can safely be added.
     *
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "init()V",
            at = @At("TAIL")
    )
    private static void afterLuaInit(CallbackInfo callback) {
        LuaScriptLoader.onLuaEnvironmentCreated();
        LuaExposer.applyPending();
        LuaScriptLoader.onLuaEnvironmentReady();
    }

    /**
     * Starts Lua global tracking before a {@code LoadDirBase} pass.
     *
     * @param sub           Lua subdirectory being loaded
     * @param onlyChecksums whether files are only checksummed
     * @param callback      injection callback provided by the mixin framework
     */
    @Inject(
            method = "LoadDirBase(Ljava/lang/String;Z)V",
            at = @At("HEAD")
    )
    private static void beforeLoadDir(String sub, boolean onlyChecksums, CallbackInfo callback) {
        LuaScriptLoader.onLoadDirBegin();
    }

    /**
     * Finishes Lua global tracking after a {@code LoadDirBase} pass.
     *
     * @param sub           Lua subdirectory being loaded
     * @param onlyChecksums whether files are only checksummed
     * @param callback      injection callback provided by the mixin framework
     */
    @Inject(
            method = "LoadDirBase(Ljava/lang/String;Z)V",
            at = @At("TAIL")
    )
    private static void afterLoadDir(String sub, boolean onlyChecksums, CallbackInfo callback) {
        LuaScriptLoader.onLoadDirEnd();
    }

    /**
     * Records each Lua file loaded by {@code LoadDirBase} so game and mod globals can be
     * distinguished for isolated environments.
     *
     * @param filename absolute Lua file path
     * @param callback injection callback provided by the mixin framework
     */
    @Inject(
            method = "RunLua(Ljava/lang/String;)Ljava/lang/Object;",
            at = @At("HEAD")
    )
    private static void trackLuaFile(String filename, CallbackInfoReturnable<Object> callback) {
        LuaScriptLoader.onLuaFileLoaded(filename);
    }
}
