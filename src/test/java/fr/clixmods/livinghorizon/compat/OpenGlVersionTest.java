package fr.clixmods.livinghorizon.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No OpenGL function newer than 4.1 is called outside {@link GlCalls}, which asks the context
 * first: macOS stops at 4.1, and LWJGL aborts the JVM on a call to a function the context lacks.
 */
class OpenGlVersionTest {
    private static final Path SOURCES = Path.of("..", "..", "src", "main", "java");
    /** A call through LWJGL's classes for OpenGL 4.2 to 4.6, or an extension's. */
    private static final Pattern CALL = Pattern.compile("\\b(GL4[2-6]C?|ARB\\w+)\\.(gl\\w+)\\s*\\(");
    /**
     * Where such a call is allowed: the one-draw occlusion path, which only runs on OpenGL 4.3
     * ({@code OcclusionQueries.wordsUsable}).
     */
    private static final Set<String> ALLOWED = Set.of("OcclusionQueries.java GL42C.glMemoryBarrier");

    @Test
    void nothingPastOpenGl41OutsideGlCalls() throws IOException {
        List<String> found = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(files.size() > 50, "sources not found in " + SOURCES.toAbsolutePath());
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (name.equals("GlCalls.java")) continue;
            Matcher m = CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (m.find()) {
                String call = m.group(1) + "." + m.group(2);
                if (!ALLOWED.contains(name + " " + call)) found.add(name + ": " + call);
            }
        }
        assertTrue(found.isEmpty(), "OpenGL calls past 4.1 outside GlCalls: " + found);
    }
}
