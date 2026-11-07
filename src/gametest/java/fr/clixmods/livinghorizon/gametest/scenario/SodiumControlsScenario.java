package fr.clixmods.livinghorizon.gametest.scenario;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.gametest.SodiumUi;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Every row of the mod's pages in Sodium's video settings used as a player would, with the
 * mouse: each slider dragged, each choice and tick box clicked, "Apply", and every value read
 * back from the mod's settings and from the file; the preset; each button's screen opened and
 * closed back to Sodium's; "Restore defaults"; the scan. Needs Sodium 0.8 or later.
 */
public final class SodiumControlsScenario implements Scenario {
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;

    @Override
    public String name() {
        return "sodiumcontrols";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            Scene.log("sodiumcontrols: Sodium not loaded, nothing to check (run with --sodium)");
            return;
        }
        //? if sodium {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(minecraft -> {
                minecraft.options.guiScale().set(2);
                //? if <26.2 {
                minecraft.options.hideGui = true;
                //?}
            });
            // Every switch on, so that no row starts greyed out.
            Scene.configure(context, c -> {
                c.skyBirds = true;
                c.offlinePlayers = c.offlineLife = c.impostors = true;
            });
            Object firstPage = context.computeOnClient(minecraft -> SodiumUi.pages().get(0));
            List<String> failures = new ArrayList<>();

            // 1. Sliders and choices (the preset aside: it sets sliders of its own).
            openSodium(context, firstPage);
            if (!wheelScrolls(context)) failures.add("the wheel does not scroll Sodium's screen");
            Map<String, Object> shown = new LinkedHashMap<>();
            int k = 0;
            for (String name : names(context, "slider", "cycling")) {
                if (name.equals("preset")) continue;
                Object before = value(context, name);
                if (kind(context, name).equals("slider")) drag(context, name, 0.15 + 0.17 * (k++ % 5));
                else click(context, name);
                Object after = value(context, name);
                if (after.equals(before)) failures.add(name + ": unchanged at " + after);
                shown.put(name, after);
            }
            Scene.log("sodiumcontrols: " + shown);
            screenshot(context, "changed");
            apply(context);
            screenshot(context, "applied");
            check(context, shown, failures);

            // 2. Tick boxes. A row greyed out by the one before it must not change.
            shown.clear();
            for (String name : names(context, "tickbox")) {
                boolean enabled = context.computeOnClient(minecraft -> SodiumUi.enabled(control(name)));
                Object before = value(context, name);
                click(context, name);
                Object after = value(context, name);
                if (enabled == after.equals(before)) {
                    failures.add(name + (enabled ? ": click did nothing" : ": changed while greyed out"));
                }
                shown.put(name, after);
            }
            Scene.log("sodiumcontrols: " + shown);
            screenshot(context, "ticked");
            apply(context);
            check(context, shown, failures);

            // 3. The preset: one click goes to the next, and the sliders it sets show it once applied.
            openSodium(context, firstPage);
            Object preset = value(context, "preset");
            click(context, "preset");
            Object chosen = value(context, "preset");
            apply(context);
            boolean matches = context.computeOnClient(minecraft -> {
                QualityPreset p = QualityPreset.of(FarConfig.get());
                return p != null && p.name().equals(chosen.toString());
            });
            Object distance = value(context, "impostorDistance");
            int saved = context.computeOnClient(minecraft -> FarConfig.get().impostorDistance);
            Scene.log("sodiumcontrols: preset " + preset + " -> " + chosen + ", impostor distance " + distance + " / " + saved);
            if (!matches) failures.add("preset " + chosen + " not applied");
            if (!distance.equals(saved)) failures.add("preset: impostor distance shown " + distance + ", saved " + saved);
            screenshot(context, "preset");

            // 3b. Max Mobs dragged to its right end, no limit: Apply asks first. No keeps the value
            // and the row reads it again; yes sets no limit.
            int mobs = context.computeOnClient(minecraft -> FarConfig.get().maxDistantMobs);
            drag(context, "maxDistantMobs", 1.0);
            apply(context);
            screenshot(context, "unlimited-asked");
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof ConfirmScreen)) {
                failures.add("maxDistantMobs: no limit kept without asking (" + context.computeOnClient(minecraft -> FarConfig.get().maxDistantMobs) + ")");
            } else {
                clickConfirm(context, false);
                int kept = context.computeOnClient(minecraft -> FarConfig.get().maxDistantMobs);
                Object shownBack = value(context, "maxDistantMobs");
                if (kept != mobs) failures.add("maxDistantMobs: " + kept + " after no, not " + mobs);
                if (!String.valueOf(shownBack).equals(String.valueOf(mobs))) failures.add("maxDistantMobs: the row reads " + shownBack + " after no");
                if (!SodiumUi.isSodiumScreen(screen(context))) failures.add("maxDistantMobs: not back on Sodium's screen after no");
                drag(context, "maxDistantMobs", 1.0);
                apply(context);
                if (context.computeOnClient(minecraft -> minecraft.screen instanceof ConfirmScreen)) clickConfirm(context, true);
                int unlimited = context.computeOnClient(minecraft -> FarConfig.get().maxDistantMobs);
                if (unlimited != FarConfig.UNLIMITED_MOBS) failures.add("maxDistantMobs: " + unlimited + " after yes");
                Scene.log("sodiumcontrols: Unlimited asked at Apply, no kept " + kept + ", yes set " + unlimited);
            }

            // 3c. A number typed past the end on the mod's screen: the row shows it at its last place,
            // and saving another row leaves it as it is.
            context.runOnClient(minecraft -> FarConfig.get().maxDistantMobs = 10000);
            openSodium(context, firstPage);
            reveal(context, "maxDistantMobs");
            screenshot(context, "typed-past");
            drag(context, "maxDistantEntities", 0.3);
            apply(context);
            int typedPast = context.computeOnClient(minecraft -> FarConfig.get().maxDistantMobs);
            if (typedPast != 10000) failures.add("maxDistantMobs: typed 10000 became " + typedPast + " once Sodium saved");
            Scene.log("sodiumcontrols: typed 10000 past the end, kept " + typedPast);

            // 4. The buttons: each opens its screen, which goes back to Sodium's when closed with Escape.
            for (String name : names(context, "button")) {
                // These close the screen and start their work: no screen to come back from.
                if (name.equals("scanMobs") || name.equals("foreseeMobs") || name.equals("restoreDefaults")) continue;
                click(context, name);
                context.waitTicks(5);
                String opened = context.computeOnClient(minecraft -> minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
                Scene.log("sodiumcontrols: " + name + " opens " + opened + context.computeOnClient(minecraft -> {
                    try {
                        Object back = SodiumUi.field(minecraft.screen, "lastScreen");
                        return ", back to " + (back == null ? "null" : back.getClass().getSimpleName());
                    } catch (IllegalStateException e) {
                        return "";
                    }
                }));
                if (SodiumUi.isSodiumScreen(screen(context))) failures.add(name + ": nothing opened");
                screenshot(context, "button-" + name);
                // Escape, as a player closes them: Sodium's screen closes itself on its release.
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.waitTicks(5);
                if (!SodiumUi.isSodiumScreen(screen(context))) {
                    failures.add(name + ": closing went to " + context.computeOnClient(minecraft -> String.valueOf(minecraft.screen)));
                    openSodium(context, firstPage);
                } else if (!wheelScrolls(context)) {
                    failures.add(name + ": back on Sodium's screen, the wheel no longer scrolls");
                }
            }

            // 5. "Restore defaults", confirmed: the mod's settings and Sodium's rows are the defaults again.
            click(context, "restoreDefaults");
            context.waitTicks(5);
            screenshot(context, "restore-confirm");
            clickConfirm(context, true);
            context.waitTicks(5);
            FarConfig defaults = new FarConfig();
            if (!SodiumUi.isSodiumScreen(screen(context))) failures.add("restoreDefaults: not back on Sodium's screen");
            int reset = context.computeOnClient(minecraft -> FarConfig.get().impostorDistance);
            Object resetShown = value(context, "impostorDistance");
            Object birdsShown = value(context, "skyBirds");
            Scene.log("sodiumcontrols: restored, impostor distance " + resetShown + " / " + reset + ", birds " + birdsShown);
            if (reset != defaults.impostorDistance || !resetShown.equals(reset)) failures.add("restoreDefaults: impostor distance " + resetShown + " / " + reset);
            if (!birdsShown.equals(defaults.skyBirds)) failures.add("restoreDefaults: birds shown " + birdsShown);
            screenshot(context, "restored");

            // 6. The scan: back to the game, where the chat says what it reads.
            click(context, "scanMobs");
            context.waitTicks(20);
            if (screen(context) != null) failures.add("scanMobs: the screen stayed open");
            screenshot(context, "scan");

            context.setScreen(() -> null);
            context.getInput().resizeWindow(854, 480);
            //? if <26.2 {
            context.runOnClient(minecraft -> minecraft.options.hideGui = false);
            //?}
            Scene.log("sodiumcontrols: " + failures.size() + " problems " + failures);
            if (!failures.isEmpty()) throw new AssertionError("Sodium's pages: " + failures);
        }
        //?}
    }

    private static void openSodium(ClientGameTestContext context, Object page) {
        context.setScreen(() -> SodiumUi.open(page));
        context.waitTicks(3);
    }

    private static Screen screen(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> minecraft.screen);
    }

    /** The screen open now; a variable named {@code minecraft} reads it on every version. */
    private static Screen screenNow() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        return minecraft.screen;
    }

    private static Object control(String name) {
        return SodiumUi.control(screenNow(), name);
    }

    private static Object value(ClientGameTestContext context, String name) {
        return context.computeOnClient(minecraft -> SodiumUi.value(control(name)));
    }

    private static String kind(ClientGameTestContext context, String name) {
        return context.computeOnClient(minecraft -> SodiumUi.kind(control(name)));
    }

    /** The settings shown as rows of the given kinds, in page order. */
    private static List<String> names(ClientGameTestContext context, String... kinds) {
        return context.computeOnClient(minecraft -> {
            List<String> names = new ArrayList<>();
            for (Object control : SodiumUi.controls(minecraft.screen)) {
                if (List.of(kinds).contains(SodiumUi.kind(control))) names.add(SodiumUi.name(control));
            }
            return names;
        });
    }

    /** Scrolls the list with the wheel until the row is in view. */
    private static void reveal(ClientGameTestContext context, String name) {
        for (int i = 0; i < 100; i++) {
            int[] where = context.computeOnClient(minecraft -> {
                int[] b = SodiumUi.bounds(control(name));
                int[] view = SodiumUi.viewport(minecraft.screen);
                int[] list = SodiumUi.bounds(SodiumUi.list(minecraft.screen));
                // Its middle in view is enough: the last row may stop a pixel short of the bottom.
                int middle = b[1] + b[3] / 2;
                // The wheel over the scrollbar: over a row, its tooltip would take the wheel.
                return new int[]{middle < view[0] + 4 ? 1 : middle > view[1] - 4 ? -1 : 0, list[0] + list[2] - 3, (view[0] + view[1]) / 2};
            });
            if (where[0] == 0) return;
            context.getInput().setCursorPos(window(context, where[1]), window(context, where[2]));
            // Ten GUI pixels a notch.
            context.getInput().scroll(where[0] * 3);
            context.waitTicks(1);
        }
        String where = context.computeOnClient(minecraft -> java.util.Arrays.toString(SodiumUi.bounds(control(name)))
                + " in " + java.util.Arrays.toString(SodiumUi.viewport(minecraft.screen))
                + " scroll " + SodiumUi.call(SodiumUi.list(minecraft.screen), "getScrollAmount")
                + " of " + SodiumUi.field(SodiumUi.field(SodiumUi.list(minecraft.screen), "scrollbar"), "total")
                + " showing " + SodiumUi.field(SodiumUi.field(SodiumUi.list(minecraft.screen), "scrollbar"), "visible"));
        throw new IllegalStateException("Could not scroll to " + name + ": " + where);
    }

    /** One notch of the wheel over the list's scrollbar, down then up: whether the list moved. */
    private static boolean wheelScrolls(ClientGameTestContext context) {
        int[] at = context.computeOnClient(minecraft -> {
            int[] list = SodiumUi.bounds(SodiumUi.list(minecraft.screen));
            return new int[]{list[0] + list[2] - 3, list[1] + list[3] / 2};
        });
        context.getInput().setCursorPos(window(context, at[0]), window(context, at[1]));
        context.waitTicks(1);
        int before = scroll(context);
        context.getInput().scroll(-1);
        context.waitTicks(1);
        int down = scroll(context);
        context.getInput().scroll(1);
        context.waitTicks(1);
        int up = scroll(context);
        Scene.log("sodiumcontrols: wheel " + before + " -> " + down + " -> " + up);
        return down != before || up != down;
    }

    private static int scroll(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> (Integer) SodiumUi.call(SodiumUi.list(minecraft.screen), "getScrollAmount"));
    }

    private static void click(ClientGameTestContext context, String name) {
        reveal(context, name);
        int[] b = context.computeOnClient(minecraft -> SodiumUi.bounds(control(name)));
        clickAt(context, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0);
    }

    /** Holds the slider's thumb where it is, drags it to {@code fraction} of its track, lets go. */
    private static void drag(ClientGameTestContext context, String name, double fraction) {
        reveal(context, name);
        int[] b = context.computeOnClient(minecraft -> SodiumUi.bounds(control(name)));
        // Hovered, the row shows its slider.
        moveTo(context, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0);
        int[] track = context.computeOnClient(minecraft -> SodiumUi.track(control(name)));
        moveTo(context, track[0] + track[2] * 0.5, track[1]);
        context.getInput().holdMouse(LEFT);
        context.waitTicks(1);
        moveTo(context, track[0] + track[2] * fraction, track[1]);
        context.getInput().releaseMouse(LEFT);
        context.waitTicks(2);
    }

    private static void apply(ClientGameTestContext context) {
        int[] b = context.computeOnClient(minecraft -> SodiumUi.bounds(SodiumUi.applyButton(minecraft.screen)));
        clickAt(context, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0);
        context.waitTicks(3);
    }

    /** "Yes" or "No" on a confirmation, found by its place: the first button is the yes. */
    private static void clickConfirm(ClientGameTestContext context, boolean yes) {
        int[] b = context.computeOnClient(minecraft -> {
            if (!(minecraft.screen instanceof ConfirmScreen confirm)) throw new IllegalStateException("No confirmation, " + minecraft.screen);
            var buttons = confirm.children().stream()
                    .filter(c -> c instanceof net.minecraft.client.gui.components.Button)
                    .map(c -> (net.minecraft.client.gui.components.Button) c).toList();
            var button = buttons.get(yes ? 0 : buttons.size() - 1);
            return new int[]{button.getX(), button.getY(), button.getWidth(), button.getHeight()};
        });
        clickAt(context, b[0] + b[2] / 2.0, b[1] + b[3] / 2.0);
        context.waitTicks(3);
    }

    private static void clickAt(ClientGameTestContext context, double x, double y) {
        moveTo(context, x, y);
        context.getInput().pressMouse(LEFT);
        context.waitTicks(2);
    }

    private static void moveTo(ClientGameTestContext context, double x, double y) {
        context.getInput().setCursorPos(window(context, x), window(context, y));
        context.waitTicks(1);
    }

    private static double window(ClientGameTestContext context, double gui) {
        return context.computeOnClient(minecraft -> SodiumUi.window(gui));
    }

    /** What the rows showed against the mod's settings in memory and in the file. */
    private static void check(ClientGameTestContext context, Map<String, Object> shown, List<String> failures) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("livinghorizon.json");
        JsonObject saved;
        try {
            saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (java.io.IOException e) {
            failures.add("could not read " + file);
            return;
        }
        int checked = 0;
        for (var entry : shown.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            Object memory = context.computeOnClient(minecraft -> read(FarConfig.get(), name));
            String expected = expected(value);
            if (!expected.equals(String.valueOf(memory))) failures.add(name + ": shown " + expected + ", set " + memory);
            var json = saved.get(name);
            String disk = json == null ? "missing" : normal(json.getAsJsonPrimitive());
            if (!expected.equals(disk)) failures.add(name + ": shown " + expected + ", file " + disk);
            checked++;
        }
        Scene.log("sodiumcontrols: checked " + checked + " settings against memory and " + file.getFileName());
    }

    /** A field of the settings as text: whole numbers without ".0". */
    private static String read(FarConfig config, String name) {
        try {
            Object value = FarConfig.class.getField(name).get(config);
            return value instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(value);
        } catch (ReflectiveOperationException e) {
            return "no field " + name;
        }
    }

    private static String normal(com.google.gson.JsonPrimitive json) {
        if (json.isNumber()) {
            double d = json.getAsDouble();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return json.getAsString();
    }

    /** A row's value as the settings keep it: an enum by the text the file holds. */
    private static String expected(Object value) {
        if (value instanceof Enum<?> e) {
            Function<Enum<?>, String> id = v -> {
                try {
                    var field = v.getClass().getDeclaredField("id");
                    field.setAccessible(true);
                    return String.valueOf(field.get(v));
                } catch (ReflectiveOperationException ex) {
                    return v.name();
                }
            };
            return id.apply(e);
        }
        return String.valueOf(value);
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        // Out of the way, so that no tooltip covers the rows.
        context.getInput().setCursorPos(1279, 719);
        context.waitTicks(5);
        Scene.log("screenshot " + context.takeScreenshot("sodiumcontrols-" + name));
    }
}
