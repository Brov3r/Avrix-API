package com.avrix.api.lua.script;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lua source backed by entries in a JAR file.
 * <p>
 * The JAR is opened lazily and kept open until the source is closed. This keeps
 * {@link JarFile#getJarEntry(String)} lookups cheap during repeated {@code require} calls.
 */
final class JarLuaScriptSource implements LuaScriptSource {

    private final Path jarPath;
    private final String prefix;

    private JarFile jarFile;
    private boolean closed;

    /**
     * Creates a JAR source.
     *
     * @param jar    JAR path
     * @param prefix entry prefix such as {@code lua}
     * @throws IllegalArgumentException if {@code jar} is {@code null}
     */
    JarLuaScriptSource(Path jar, String prefix) {
        this.jarPath = Objects.requireNonNull(jar, "jar").toAbsolutePath().normalize();
        this.prefix = normalizePrefix(prefix);
    }

    /**
     * Returns a stable identifier for this JAR source.
     *
     * @return source identifier
     */
    @Override
    public String id() {
        return "jar:" + jarPath + "!/" + prefix;
    }

    /**
     * Checks whether the JAR contains the given relative Lua path.
     *
     * @param relativePath relative Lua path
     * @return {@code true} if the entry exists
     */
    @Override
    public boolean contains(String relativePath) {
        try {
            return ensureOpen().getJarEntry(entryName(relativePath)) != null;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    /**
     * Opens the given relative Lua path from the JAR.
     *
     * @param relativePath relative Lua path
     * @return entry stream
     * @throws IOException if the entry does not exist or cannot be opened
     */
    @Override
    public InputStream open(String relativePath) throws IOException {
        JarEntry entry = ensureOpen().getJarEntry(entryName(relativePath));
        if (entry == null) {
            throw new FileNotFoundException("Lua entry not found: " + entryName(relativePath));
        }
        return ensureOpen().getInputStream(entry);
    }

    /**
     * Lists Lua entries under the given relative directory.
     *
     * @param directory relative directory or an empty string for the root
     * @return relative Lua paths
     * @throws IOException if the JAR cannot be read
     */
    @Override
    public List<String> list(String directory) throws IOException {
        String normalizedDirectory = normalizeDirectory(directory);
        String base;
        if (normalizedDirectory.isEmpty()) {
            base = prefix.isEmpty() ? "" : prefix + "/";
        } else {
            base = entryName(normalizedDirectory) + "/";
        }

        List<String> files = new ArrayList<>();
        Enumeration<JarEntry> entries = ensureOpen().entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(base) || !name.toLowerCase(Locale.ROOT).endsWith(".lua")) {
                continue;
            }
            String relative = prefix.isEmpty() ? name : name.substring(prefix.length() + 1);
            files.add(relative);
        }
        return files;
    }

    /**
     * Closes the underlying JAR file.
     *
     * @throws IOException if the JAR cannot be closed
     */
    @Override
    public void close() throws IOException {
        closed = true;
        if (jarFile != null) {
            jarFile.close();
            jarFile = null;
        }
    }

    private JarFile ensureOpen() throws IOException {
        if (closed) {
            throw new IllegalStateException("Lua script source is closed: " + id());
        }
        JarFile current = jarFile;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (jarFile == null) {
                jarFile = new JarFile(jarPath.toFile());
            }
            return jarFile;
        }
    }

    private String entryName(String relativePath) {
        String normalized = normalizeRelative(relativePath);
        return prefix.isEmpty() ? normalized : prefix + "/" + normalized;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("JAR prefix escapes the archive: " + prefix);
        }
        return normalized;
    }

    private static String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return "";
        }
        String normalized = directory.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be blank");
        }
        String normalized = relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Path escapes the script source: " + relativePath);
        }
        return normalized;
    }

    /**
     * Returns a diagnostic representation of this source.
     *
     * @return source description
     */
    @Override
    public String toString() {
        return id();
    }
}
