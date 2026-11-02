package fr.clixmods.livinghorizon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.clixmods.livinghorizon.ambient.Ambience;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every string the mod shows exists in every language, with the same blanks: a missing one shows as its raw key. */
class LangTest {
    private static final Path LANG = Path.of("..", "..", "src", "main", "resources", "assets", "livinghorizon", "lang");
    private static final Path SOURCES = Path.of("..", "..", "src", "main", "java");

    private static Set<String> keys(String file) throws IOException {
        return new TreeSet<>(json(file).keySet());
    }

    private static JsonObject json(String file) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every language file: English and each translation. */
    private static List<String> languages() throws IOException {
        try (Stream<Path> files = Files.list(LANG)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted().toList();
        }
    }

    /** The format specifiers of a string, in order: what the game fills in at run time. */
    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) found.add(m.group());
        return found;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:%|s)");

    @Test
    void everyLanguageHasTheSameKeysAsEnglish() throws IOException {
        Set<String> english = keys("en_us.json");
        assertTrue(languages().size() > 2, "only " + languages());
        for (String file : languages()) {
            assertEquals(english, keys(file), file);
        }
    }

    @Test
    void everyTranslationFillsTheSameBlanks() throws IOException {
        JsonObject english = json("en_us.json");
        for (String file : languages()) {
            JsonObject other = json(file);
            for (String key : english.keySet()) {
                List<String> expected = placeholders(english.get(key).getAsString());
                List<String> actual = placeholders(other.get(key).getAsString());
                expected.sort(null);
                actual.sort(null);
                assertEquals(expected, actual, file + ": " + key);
            }
        }
    }

    @Test
    void everySettingHasItsTexts() throws IOException {
        Set<String> keys = keys("en_us.json");
        // bool("name", ...), integer("name", ...), unit("name", ...) and the like in the settings screens.
        Pattern option = Pattern.compile("(?:bool|integer|choice|unit|blocks|chunks)\\(\"(\\w+)\"");
        String screen = Files.readString(SOURCES.resolve("fr/clixmods/livinghorizon/ui/FarConfigScreen.java"))
                + Files.readString(SOURCES.resolve("fr/clixmods/livinghorizon/ui/DebugScreen.java"));
        Matcher match = option.matcher(screen);
        int found = 0;
        while (match.find()) {
            found++;
            String key = "livinghorizon.options." + match.group(1);
            assertTrue(keys.contains(key), key);
            assertTrue(keys.contains(key + ".tooltip"), key + ".tooltip");
        }
        assertTrue(found >= 12, "only " + found + " settings found");

        // Every literal translation key written out in the sources.
        Pattern literal = Pattern.compile("\"(livinghorizon\\.[a-zA-Z.]+[a-zA-Z])\"");
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.endsWith(".json") || key.endsWith(".accesswidener")) continue; // file names
                    assertTrue(keys.contains(key), key + " in " + file.getFileName());
                }
            }
        }
        for (Profile.Section section : Profile.Section.values()) {
            assertTrue(keys.contains(section.langKey()), section.langKey());
        }
        for (Ambience.Type type : Ambience.Type.values()) {
            assertTrue(keys.contains("livinghorizon.options.birdTypes." + type.id), type.id);
            assertTrue(keys.contains("livinghorizon.options.birdTypes." + type.id + ".tooltip"), type.id + ".tooltip");
        }
        for (String suffix : List.of("title", "search", "shared", "shared.tooltip", "local.tooltip", "all", "none", "defaults")) {
            assertTrue(keys.contains("livinghorizon.options.mobTypes." + suffix), suffix);
        }
    }
}
