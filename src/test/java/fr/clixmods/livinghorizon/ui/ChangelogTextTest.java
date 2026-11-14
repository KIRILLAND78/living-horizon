package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.ui.ChangelogText.Kind;
import fr.clixmods.livinghorizon.ui.ChangelogText.Line;
import fr.clixmods.livinghorizon.ui.ChangelogText.Span;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangelogTextTest {
    @Test
    void readsHeadingsAndBullets() {
        List<Line> lines = ChangelogText.parse("""
                # Living Horizon — Changelog

                ## Unreleased

                ### Client

                #### New
                - **A thing** — it works.
                  and goes on.
                """);
        assertEquals(List.of(
                new Line(Kind.VERSION, "Unreleased"),
                new Line(Kind.PART, "Client"),
                new Line(Kind.KIND, "New"),
                new Line(Kind.BULLET, "**A thing** — it works. and goes on.")), lines);
    }

    @Test
    void splitsBoldAndItalic() {
        assertEquals(List.of(
                new Span("Bold", true, false),
                new Span(" then ", false, false),
                new Span("italic", false, true),
                new Span(" and code", false, false)),
                ChangelogText.spans("**Bold** then *italic* and `code`"));
    }

    @Test
    void picksTheGameLanguage() {
        assertEquals(List.of("de_de", "de", "en"), ChangelogText.names("de_de"));
        assertEquals(List.of("pt_br", "pt", "en"), ChangelogText.names("pt_br"));
        assertEquals(List.of("zh_tw", "zh", "en"), ChangelogText.names("zh_tw"));
        assertEquals(List.of("en_us", "en"), ChangelogText.names("en_us"));
        assertEquals(List.of("en"), ChangelogText.names(""));
    }

    /** Every game language with a changelog finds it by its own name or by its first part. */
    @Test
    void everyLanguageFindsItsChangelog() throws IOException {
        Path dir = Path.of("..", "..", "changelog");
        String table = Files.readString(dir.resolve("languages.json"), StandardCharsets.UTF_8);
        java.util.regex.Matcher entry = java.util.regex.Pattern
                .compile("\"([a-z_]+)\":\\s*\\{\\s*\"locale\":\\s*\"([a-z_]+)\"").matcher(table);
        int found = 0;
        while (entry.find()) {
            found++;
            assertTrue(Files.exists(dir.resolve(entry.group(1) + ".md")), entry.group(1) + ".md");
            assertTrue(ChangelogText.names(entry.group(2)).contains(entry.group(1)),
                    entry.group(2) + " does not find " + entry.group(1) + ".md");
        }
        assertTrue(found >= 2, "no language read from languages.json");
    }

    /** The changelog files shipped in the jar read: a version and bullets, no mark left over. */
    @Test
    void shippedChangelogsParse() throws IOException {
        List<String> names;
        try (var files = Files.list(Path.of("..", "..", "changelog"))) {
            names = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".md")).sorted().toList();
        }
        assertTrue(names.size() > 2, "changelog files not found");
        for (String name : names) {
            List<Line> lines = ChangelogText.parse(Files.readString(Path.of("..", "..", "changelog", name), StandardCharsets.UTF_8));
            assertFalse(lines.isEmpty(), name);
            assertEquals(Kind.VERSION, lines.get(0).kind(), name);
            for (Line line : lines) {
                for (Span span : ChangelogText.spans(line.content())) {
                    assertFalse(span.content().contains("*"), name + ": " + line.content());
                }
            }
        }
    }
}
