package com.avrix.api.lua.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Source of custom Lua files.
 * <p>
 * A source maps relative module paths (for example {@code feature/main.lua}) to readable
 * streams. Two implementations are provided: a directory source and a JAR source.
 */
public sealed interface LuaScriptSource extends AutoCloseable
        permits DirectoryLuaScriptSource, JarLuaScriptSource {

    /**
     * Returns a stable identifier used for caching and diagnostics.
     *
     * @return source identifier
     */
    String id();

    /**
     * Checks whether the source contains the given relative Lua path.
     *
     * @param relativePath relative path such as {@code module/main.lua}
     * @return {@code true} if the file exists
     */
    boolean contains(String relativePath);

    /**
     * Opens the given relative Lua path.
     *
     * @param relativePath relative path such as {@code module/main.lua}
     * @return file stream; caller is responsible for closing it
     * @throws IOException if the file cannot be opened
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * Lists Lua files under the given directory.
     *
     * @param directory relative directory such as {@code module} or an empty string for the root
     * @return relative Lua paths
     * @throws IOException if the source cannot be read
     */
    List<String> list(String directory) throws IOException;

    /**
     * Creates a directory-backed source.
     *
     * @param root directory root
     * @return directory source
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    static LuaScriptSource directory(Path root) {
        return new DirectoryLuaScriptSource(root);
    }

    /**
     * Creates a JAR-backed source.
     *
     * @param jar    JAR path
     * @param prefix entry prefix such as {@code lua} or an empty string for the JAR root
     * @return JAR source
     * @throws IllegalArgumentException if {@code jar} is {@code null}
     */
    static LuaScriptSource jar(Path jar, String prefix) {
        return new JarLuaScriptSource(jar, prefix);
    }

    /**
     * Releases resources held by this source.
     *
     * @throws IOException if the source cannot be closed
     */
    @Override
    void close() throws IOException;
}
