package com.avrix.api.lua.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Lua source backed by a directory on the file system.
 */
final class DirectoryLuaScriptSource implements LuaScriptSource {

    private final Path root;

    /**
     * Creates a directory source.
     *
     * @param root directory root
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    DirectoryLuaScriptSource(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /**
     * Returns a stable identifier for this directory source.
     *
     * @return source identifier
     */
    @Override
    public String id() {
        return "dir:" + root;
    }

    /**
     * Checks whether the directory contains the given relative Lua path.
     *
     * @param relativePath relative Lua path
     * @return {@code true} if the file exists
     */
    @Override
    public boolean contains(String relativePath) {
        try {
            return Files.isRegularFile(resolve(relativePath));
        } catch (InvalidPathException ex) {
            return false;
        }
    }

    /**
     * Opens the given relative Lua path.
     *
     * @param relativePath relative Lua path
     * @return file stream
     * @throws IOException if the file cannot be opened
     */
    @Override
    public InputStream open(String relativePath) throws IOException {
        return Files.newInputStream(resolve(relativePath));
    }

    /**
     * Lists Lua files under the given relative directory.
     *
     * @param directory relative directory or an empty string for the root
     * @return relative Lua paths
     * @throws IOException if the directory cannot be read
     */
    @Override
    public List<String> list(String directory) throws IOException {
        Path start = directory == null || directory.isBlank() ? root : resolve(directory);
        if (!Files.isDirectory(start)) {
            return List.of();
        }

        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
                    .forEach(path -> files.add(root.relativize(path).toString().replace('\\', '/')));
        }
        return files;
    }

    /**
     * Releases the source. Directory sources do not hold any resources.
     */
    @Override
    public void close() {
        // Directory sources do not hold any resources.
    }

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be blank");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the script source: " + relativePath);
        }
        return resolved;
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
