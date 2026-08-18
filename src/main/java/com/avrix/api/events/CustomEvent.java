package com.avrix.api.events;

import java.util.Objects;

/**
 * Immutable representation of a custom mod-defined event.
 * <p>
 * Used by plugin developers to emit and listen to non-vanilla events across Java and Lua boundaries.
 *
 * @param name The unique name of the custom event
 */
public record CustomEvent(String name) implements Event {

    /**
     * Compact constructor validating event name invariants.
     *
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if name is empty or contains only whitespace
     */
    public CustomEvent {
        Objects.requireNonNull(name, "Event name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be empty or blank");
        }
    }

    /**
     * @return The unique identifier of this custom event.
     */
    @Override
    public String getName() {
        return name;
    }
}