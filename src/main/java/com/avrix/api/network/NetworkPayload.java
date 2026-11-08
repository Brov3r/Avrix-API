package com.avrix.api.network;

import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import zombie.Lua.LuaManager;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts between Java payload maps and Project Zomboid Kahlua tables.
 * <p>
 * Supported value types are strings, booleans, numbers, enums, nested maps, iterables, arrays, and
 * existing Kahlua tables. Numbers are normalized to {@link Double} because Kahlua stores all numbers
 * as doubles. {@code null} values are omitted because Kahlua tables cannot store Java nulls.
 */
public final class NetworkPayload {

    private NetworkPayload() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a Kahlua table from a string-keyed payload map.
     *
     * @param payload payload map, or {@code null} for an empty table
     * @return new Kahlua table
     * @throws IllegalArgumentException if a key or value type is unsupported
     */
    public static KahluaTable toTable(Map<String, ?> payload) {
        KahluaTable table = newTable();
        if (payload == null || payload.isEmpty()) {
            return table;
        }
        for (Map.Entry<String, ?> entry : payload.entrySet()) {
            String key = requireKey(entry.getKey());
            Object value = normalize(entry.getValue());
            if (value != null) {
                table.rawset(key, value);
            }
        }
        return table;
    }

    /**
     * Creates a Kahlua table from alternating key/value arguments.
     *
     * @param keyValues alternating string keys and values
     * @return new Kahlua table
     * @throws IllegalArgumentException if the argument count, key, or value type is invalid
     */
    public static KahluaTable toTable(Object... keyValues) {
        return toTable(toMap(keyValues));
    }

    /**
     * Creates a payload map from alternating key/value arguments.
     *
     * @param keyValues alternating string keys and values
     * @return mutable payload map
     * @throws IllegalArgumentException if the argument count, key, or value type is invalid
     */
    public static Map<String, Object> toMap(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return new LinkedHashMap<>();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Network payload key/value argument count must be even");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (!(keyValues[i] instanceof String key)) {
                throw new IllegalArgumentException("Network payload key at index " + i + " must be a string");
            }
            payload.put(requireKey(key), keyValues[i + 1]);
        }
        return payload;
    }

    /**
     * Decodes a Kahlua table into an immutable string-keyed payload map.
     *
     * @param table Kahlua table, or {@code null}
     * @return decoded payload map
     */
    public static Map<String, Object> fromTable(KahluaTable table) {
        if (table == null || table.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        KahluaTableIterator iterator = table.iterator();
        while (iterator.advance()) {
            payload.put(keyString(iterator.getKey()), denormalize(iterator.getValue()));
        }
        return Collections.unmodifiableMap(payload);
    }

    /**
     * Creates a new Kahlua table using the game platform when available.
     *
     * @return new Kahlua table
     */
    private static KahluaTable newTable() {
        return LuaManager.platform != null
                ? LuaManager.platform.newTable()
                : J2SEPlatform.getInstance().newTable();
    }

    /**
     * Validates and normalizes a payload key.
     *
     * @param key raw key
     * @return trimmed key
     */
    private static String requireKey(String key) {
        String clean = Objects.requireNonNull(key, "Network payload key cannot be null").strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Network payload key cannot be blank");
        }
        return clean;
    }

    /**
     * Converts a Java value into a Kahlua-compatible value.
     *
     * @param value source value
     * @return normalized value or {@code null} when the value should be omitted
     */
    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof KahluaTable) {
            return value;
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            return toTable(toStringMap(map));
        }
        if (value instanceof Iterable<?> iterable) {
            KahluaTable table = newTable();
            int index = 1;
            for (Object element : iterable) {
                Object normalized = normalize(element);
                if (normalized != null) {
                    table.rawset(index++, normalized);
                }
            }
            return table;
        }
        if (value.getClass().isArray()) {
            KahluaTable table = newTable();
            int length = Array.getLength(value);
            int index = 1;
            for (int i = 0; i < length; i++) {
                Object normalized = normalize(Array.get(value, i));
                if (normalized != null) {
                    table.rawset(index++, normalized);
                }
            }
            return table;
        }
        throw new IllegalArgumentException("Unsupported network payload value type: " + value.getClass().getName());
    }

    /**
     * Converts a wildcard map into a string-keyed map.
     *
     * @param map source map
     * @return string-keyed map
     */
    private static Map<String, Object> toStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Converts a Kahlua key into a stable string key.
     *
     * @param key source key
     * @return string key
     */
    private static String keyString(Object key) {
        if (key instanceof String stringKey) {
            return stringKey;
        }
        if (key instanceof Number number) {
            double value = number.doubleValue();
            if (!Double.isNaN(value) && !Double.isInfinite(value) && value == Math.rint(value)) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
        return String.valueOf(key);
    }

    /**
     * Converts a Kahlua value into a Java payload value.
     *
     * @param value source value
     * @return decoded value
     */
    private static Object denormalize(Object value) {
        if (value instanceof KahluaTable nested) {
            return fromTable(nested);
        }
        return value;
    }
}
