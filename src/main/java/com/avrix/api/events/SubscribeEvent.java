package com.avrix.api.events;

import java.lang.annotation.*;

/**
 * Marks a method as an event subscriber for Avrix and Project Zomboid events.
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