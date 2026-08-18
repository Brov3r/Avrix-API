package com.avrix.api.events;

/**
 * Universal contract for all game and mod events in the Avrix platform.
 * <p>
 * Implemented by built-in enum {@link DefaultEvents} as well as dynamic
 * custom events via {@link CustomEvent}.
 *
 * @apiNote All implementations must return a non-null, immutable event identifier.
 */
public interface Event {

    /**
     * Retrieves the unique string identifier of this event.
     * <p>
     * Corresponds to the Lua event table key in Project Zomboid (e.g. {@code "OnPlayerUpdate"}).
     *
     * @return The unique, case-sensitive event name
     */
    String getName();

    /**
     * Factory method for creating a lightweight custom event wrapper.
     *
     * @param eventName Unique name for the custom event
     * @return An immutable {@link Event} instance
     * @throws NullPointerException     if eventName is null
     * @throws IllegalArgumentException if eventName is blank
     */
    static Event custom(String eventName) {
        return new CustomEvent(eventName);
    }
}