package com.avrix.api.mixins.common;

import com.avrix.api.lua.script.LuaScriptLoader;
import com.avrix.api.lua.script.LuaScriptResult;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import zombie.Lua.LuaManager;

import java.util.Optional;

/**
 * ClassTransform mixin for {@link LuaManager.GlobalObject#require(String)} that resolves
 * custom Lua modules before the game's own module search.
 */
@CTransformer(value = LuaManager.GlobalObject.class)
public class LuaRequireMixin {

    /**
     * Intercepts {@code require} and returns a custom module when one is available.
     * <p>
     * When no custom source contains the module, the original game implementation runs
     * unchanged.
     *
     * @param moduleName module name
     * @param callback   injection callback used to return a custom module
     */
    @CInject(
            method = "require(Ljava/lang/String;)Ljava/lang/Object;",
            target = @CTarget("HEAD"),
            cancellable = true
    )
    private static void interceptRequire(String moduleName, InjectionCallback callback) {
        Optional<LuaScriptResult> result = LuaScriptLoader.tryRequire(moduleName);
        if (result.isPresent()) {
            callback.setReturnValue(result.get().value());
            callback.setCancelled(true);
        }
    }
}
