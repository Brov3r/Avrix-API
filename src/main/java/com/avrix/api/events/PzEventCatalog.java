package com.avrix.api.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombie.Lua.LuaEventManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Dynamically discovers Project Zomboid event names from the game runtime.
 * <p>
 * The catalog is populated from {@code LuaEventManager}, so new game events become
 * available without adding constants or editing the Avrix API source.
 */
final class PzEventCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(PzEventCatalog.class);
    private static final Object LOCK = new Object();

    private static volatile Set<String> eventNames = Collections.emptySet();

    private PzEventCatalog() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Refreshes the cached event-name catalog from the running game.
     */
    static void refresh() {
        synchronized (LOCK) {
            try {
                var events = new java.util.ArrayList<zombie.Lua.Event>();
                var eventMap = new java.util.HashMap<String, zombie.Lua.Event>();
                LuaEventManager.getEvents(events, eventMap);

                Set<String> names = new LinkedHashSet<>(eventMap.keySet());
                for (zombie.Lua.Event event : events) {
                    if (event != null && event.name != null && !event.name.isBlank()) {
                        names.add(event.name);
                    }
                }

                eventNames = Collections.unmodifiableSet(new TreeSet<>(names));
                LOGGER.debug("Refreshed Project Zomboid event catalog ({} event names)", eventNames.size());
            } catch (Exception | LinkageError ex) {
                LOGGER.warn("Failed to refresh Project Zomboid event catalog", ex);
            }
        }
    }

    /**
     * Returns an immutable snapshot of all currently known Project Zomboid event names.
     *
     * @return unmodifiable, sorted set of event names
     */
    static Set<String> getEventNames() {
        Set<String> current = eventNames;
        if (current.isEmpty()) {
            refresh();
            current = eventNames;
        }
        return current;
    }
}
