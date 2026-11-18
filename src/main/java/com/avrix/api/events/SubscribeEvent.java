package com.avrix.api.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event subscriber for Avrix and Project Zomboid events.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {

    /**
     * Default game event enum constant.
     *
     * @return default event constant
     */
    DefaultEvents value() default DefaultEvents.ON_TICK;

    /**
     * Custom event name. If non-empty, overrides {@link #value()}.
     *
     * @return custom event name, or an empty string
     */
    String custom() default "";

    /**
     * Execution priority. Higher values execute first.
     * Default is {@code 0}.
     *
     * @return listener priority
     */
    int priority() default 0;
}
