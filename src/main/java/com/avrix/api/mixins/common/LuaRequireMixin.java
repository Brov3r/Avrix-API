package com.avrix.api.mixins.common;

import com.avrix.api.lua.script.LuaScriptLoader;
import com.avrix.api.lua.script.LuaScriptResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.Lua.LuaManager;

import java.util.Optional;

/**
 * Mixin for {@link LuaManager.GlobalObject#require(String)} that resolves custom Lua modules
 * before the game's own module search.
 */
@Mixin(LuaManager.GlobalObject.class)
public class LuaRequireMixin {

    /**
     * Prevents instantiation of the mixin class.
     */
    private LuaRequireMixin() {
        throw new UnsupportedOperationException("Mixin classes cannot be instantiated");
    }

    /**
     * Intercepts {@code require} and returns a custom module when one is available.
     * <p>
     * When no custom source contains the module, the original game implementation runs unchanged.
     *
     * @param moduleName module name
     * @param callback   injection callback used to return a custom module
     */
    @Inject(
            method = "require(Ljava/lang/String;)Ljava/lang/Object;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void interceptRequire(String moduleName, CallbackInfoReturnable<Object> callback) {
        Optional<LuaScriptResult> result = LuaScriptLoader.tryRequire(moduleName);
        if (result.isPresent()) {
            callback.setReturnValue(result.get().value());
        }
    }
}
