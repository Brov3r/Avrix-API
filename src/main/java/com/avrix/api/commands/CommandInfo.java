package com.avrix.api.commands;

import zombie.characters.Capability;

import java.lang.annotation.*;

/**
 * Declares command metadata, triggers, usage syntax, execution scope, and authorization rules.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandInfo {

    /**
     * Primary name/trigger of the command (without leading slash).
     *
     * @return primary command name
     */
    String name();

    /**
     * Optional aliases for the command (e.g., {@code {"tp", "teleport"}}).
     *
     * @return array of alias triggers
     */
    String[] aliases() default {};

    /**
     * Human-readable description of what the command does.
     *
     * @return command description
     */
    String description() default "";

    /**
     * Syntax usage guide (e.g. {@code "/heal [player]"}).
     *
     * @return syntax usage string
     */
    String usage() default "";

    /**
     * Custom string permission nodes required to execute this command (e.g. {@code {"avrix.commands.heal"}}).
     * <p>By default, if multiple nodes are specified, possessing <b>any</b> of them grants access (OR logic).</p>
     *
     * @return array of permission strings
     */
    String[] permission() default {};

    /**
     * Standard Project Zomboid capabilities required.
     *
     * @return array of native capabilities
     */
    Capability[] capability() default {};

    /**
     * Allowed execution environment (Chat only, Console only, or Both).
     *
     * @return the execution scope
     */
    CommandScope scope() default CommandScope.BOTH;
}