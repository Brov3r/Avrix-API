package com.avrix.api.events;

import java.lang.annotation.*;

/**
 * Marks a method as an event subscriber for Avrix and Project Zomboid events.
 *
 * <h3>Examples:</h3>
 * <pre>{@code
 * // Standard vanilla event with default priority (0)
 * @SubscribeEvent(DefaultEvents.ON_PLAYER_DEATH)
 * public void onPlayerDeath(IsoPlayer player) { ... }
 *
 * // Standard event with high priority (runs earlier)
 * @SubscribeEvent(value = DefaultEvents.ON_TICK, priority = 100)
 * public void onTick() { ... }
 *
 * // Custom mod event
 * @SubscribeEvent(custom = "MyMod_OnCustomEvent", priority = -10)
 * public void onCustom(String data) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {

    /**
     * Default game event enum constant.
     */
    DefaultEvents value() default DefaultEvents.ON_TICK;

    /**
     * Custom event name. If non-empty, overrides {@link #value()}.
     */
    String custom() default "";

    /**
     * Execution priority. Higher values execute first.
     * Default is {@code 0}.
     */
    int priority() default 0;
}