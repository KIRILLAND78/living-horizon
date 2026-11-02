package fr.clixmods.livinghorizon.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The changelog's Markdown, as the screen shows it: a title or a bullet per line, and the
 * {@code **bold**} and {@code *italic*} inside it. It asks the game nothing.
 */
public final class ChangelogText {
    /** What a line is, by the number of {@code #} before it: a version, a part (server, client), a kind, or a bullet. */
    public enum Kind { VERSION, PART, KIND, BULLET }

    public record Line(Kind kind, String content) {}

    /** A run of text with one look. */
    public record Span(String content, boolean bold, boolean italic) {}

    private ChangelogText() {}

    /** The lines of a changelog file, its own title and blank lines left out. */
    public static List<Line> parse(String markdown) {
        List<Line> lines = new ArrayList<>();
        for (String raw : markdown.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("#### ")) lines.add(new Line(Kind.KIND, line.substring(5).strip()));
            else if (line.startsWith("### ")) lines.add(new Line(Kind.PART, line.substring(4).strip()));
            else if (line.startsWith("## ")) lines.add(new Line(Kind.VERSION, line.substring(3).strip()));
            else if (line.startsWith("- ")) lines.add(new Line(Kind.BULLET, line.substring(2).strip()));
            else if (!line.isEmpty() && !line.startsWith("# ") && !lines.isEmpty() && lines.get(lines.size() - 1).kind() == Kind.BULLET) {
                // A bullet carried onto the next line.
                Line last = lines.remove(lines.size() - 1);
                lines.add(new Line(Kind.BULLET, last.content() + " " + line));
            }
        }
        return lines;
    }

    /** Splits a line at its {@code **} and {@code *} marks; backticks are dropped. */
    public static List<Span> spans(String text) {
        List<Span> spans = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean bold = false, italic = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '`') continue;
            if (c == '*') {
                flush(spans, current, bold, italic);
                if (i + 1 < text.length() && text.charAt(i + 1) == '*') {
                    bold = !bold;
                    i++;
                } else {
                    italic = !italic;
                }
                continue;
            }
            current.append(c);
        }
        flush(spans, current, bold, italic);
        return spans;
    }

    private static void flush(List<Span> spans, StringBuilder current, boolean bold, boolean italic) {
        if (current.isEmpty()) return;
        spans.add(new Span(current.toString(), bold, italic));
        current.setLength(0);
    }
}
