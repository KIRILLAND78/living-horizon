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

    /** The changelog files shipped in the jar read: a version and bullets, no mark left over. */
    @Test
    void shippedChangelogsParse() throws IOException {
        for (String name : List.of("en.md", "fr.md")) {
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
