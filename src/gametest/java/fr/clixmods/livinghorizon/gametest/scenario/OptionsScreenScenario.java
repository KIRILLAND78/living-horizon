package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.BoxesScreen;
import fr.clixmods.livinghorizon.ui.DebugScreen;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.OutlinesScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The settings screens in a world: everything, the rows of options included, sits in the
 * column on the left, so that the game stays in view on the right; and no button's or
 * slider's text, in any of the mod's languages, is wider than it or a key left untranslated.
 */
public final class OptionsScreenScenario implements Scenario {
    /** The room a button leaves its text: the game scrolls whatever is wider, and that reads badly. */
    private static final int TEXT_MARGIN = 4;

    @Override
    public String name() {
        return "options";
    }

    @Override
    public void run(ClientGameTestContext context) {
        // The widest numbers the sliders show: their text must fit then too.
        Scene.configure(context, config -> {
            config.scanRadius = fr.clixmods.livinghorizon.track.MobScan.MAX_RADIUS;
            config.foreseeRadius = fr.clixmods.livinghorizon.track.Foresight.MAX_RADIUS;
            config.maxDistantMobs = config.maxDistantEntities = fr.clixmods.livinghorizon.FarConfig.MAX_MOBS;
            config.maxOfflinePlayers = fr.clixmods.livinghorizon.FarConfig.MAX_OFFLINE;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            List<String> problems = new ArrayList<>();
            try {
                for (String language : context.computeOnClient(OptionsScreenScenario::languages)) {
                    setLanguage(context, language);
                    look(context, language + "-settings", () -> new FarConfigScreen(null), problems);
                    look(context, language + "-debug", () -> new DebugScreen(null), problems);
                    look(context, language + "-boxes", () -> new BoxesScreen(null), problems);
                    look(context, language + "-outlines", () -> new OutlinesScreen(null), problems);
                    look(context, language + "-stats", () -> new fr.clixmods.livinghorizon.ui.StatsScreen(null), problems);
                }
            } finally {
                setLanguage(context, "en_us");
            }
            context.setScreen(() -> null);
            problems.forEach(problem -> Scene.log("options: " + problem));
            if (!problems.isEmpty()) throw new AssertionError(problems.size() + " problems on the settings screens");
        }
    }

    /** Opens the screen, checks it and takes a picture of it from the top of its list to the bottom. */
    private void look(ClientGameTestContext context, String name, Supplier<Screen> screen, List<String> problems) {
        context.setScreen(screen::get);
        context.waitTicks(5);
        problems.addAll(context.computeOnClient(minecraft -> check(minecraft, name)));
        Scene.screenshot(context, this, name + "-0");
        for (int page = 1; page < 8; page++) {
            if (!context.computeOnClient(minecraft -> scrollDown(minecraft.screen))) break;
            context.waitTicks(2);
            Scene.screenshot(context, this, name + "-" + page);
        }
    }

    private static List<String> check(Minecraft minecraft, String name) {
        List<String> problems = new ArrayList<>();
        int panel = Math.min(360, minecraft.screen.width);
        int overflow = overflow(minecraft.screen, panel);
        if (overflow > 0) problems.add(name + ": widgets " + overflow + " px past the left column");
        for (AbstractWidget widget : widgets(minecraft.screen, new ArrayList<>())) {
            if (!(widget instanceof AbstractButton) && !(widget instanceof AbstractSliderButton)) continue;
            String text = widget.getMessage().getString();
            // A key the language files do not have shows as itself.
            if (text.contains("livinghorizon.")) problems.add(name + ": untranslated \"" + text + "\"");
            int width = minecraft.font.width(widget.getMessage());
            if (width > widget.getWidth() - TEXT_MARGIN) {
                problems.add(name + ": \"" + text + "\" is " + width + " px wide, its button " + widget.getWidth());
            }
        }
        return problems;
    }

    /** Scrolls the screen's list most of a screen down; whether it moved. */
    private static boolean scrollDown(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                double before = list.scrollAmount();
                list.setScrollAmount(before + 200);
                return list.scrollAmount() != before;
            }
        }
        return false;
    }

    /** The languages the mod has a file for, English first. */
    private static List<String> languages(Minecraft minecraft) {
        List<String> codes = new ArrayList<>(minecraft.getResourceManager()
                .listResources("lang", id -> id.getNamespace().equals("livinghorizon") && id.getPath().endsWith(".json"))
                .keySet().stream()
                .map(id -> id.getPath().substring("lang/".length(), id.getPath().length() - ".json".length()))
                .sorted().toList());
        codes.remove("en_us");
        codes.add(0, "en_us");
        return codes;
    }

    /** Switches the game's language and waits for it to be read. */
    private static void setLanguage(ClientGameTestContext context, String code) {
        boolean changed = context.computeOnClient(minecraft -> {
            if (minecraft.getLanguageManager().getSelected().equals(code)) return false;
            minecraft.getLanguageManager().setSelected(code);
            minecraft.options.languageCode = code;
            minecraft.reloadResourcePacks();
            return true;
        });
        if (!changed) return;
        context.waitFor(minecraft -> minecraft.getOverlay() == null);
        context.waitTicks(5);
    }

    private static List<AbstractWidget> widgets(ContainerEventHandler parent, List<AbstractWidget> found) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget) found.add(widget);
            if (child instanceof ContainerEventHandler container) widgets(container, found);
        }
        return found;
    }

    /** How far the widgets inside {@code parent}, rows of lists included, reach past {@code panel}. */
    private static int overflow(ContainerEventHandler parent, int panel) {
        int worst = 0;
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget) worst = Math.max(worst, widget.getRight() - panel);
            if (child instanceof ContainerEventHandler container) worst = Math.max(worst, overflow(container, panel));
        }
        return worst;
    }
}
