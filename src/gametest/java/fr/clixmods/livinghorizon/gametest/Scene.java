package fr.clixmods.livinghorizon.gametest;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * What the scenarios share: a known starting point, a flat world, mobs in rows,
 * screenshots, and log lines easy to find.
 *
 * <p>The window is 854x480 and the GUI scale 1, so a screenshot shows the world in real
 * pixels. Screenshots land in {@code build/run/clientGameTest/screenshots/}, numbered in
 * the order they are taken.
 */
public final class Scene {
    /** Every line a scenario logs starts with this, to grep the log for. */
    public static final String LOG = "LHTEST ";

    private Scene() {
    }

    public static void log(String line) {
        System.out.println(LOG + line);
    }

    /**
     * Turns on the shader pack named by {@code -Plh.shaderpack=<folder>} (run.sh's
     * {@code --shaders}), when Iris is loaded: copied into Iris's folder, chosen and loaded.
     * Through reflection, Iris being no dependency of the mod. Nothing without the property.
     */
    static void shaderPack(ClientGameTestContext context) {
        String folder = System.getProperty("livinghorizon.shaderpack", "");
        if (folder.isBlank()) return;
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
            throw new AssertionError("A shader pack was given, but Iris is not loaded");
        }
        context.runOnClient(minecraft -> {
            try {
                java.nio.file.Path source = java.nio.file.Path.of(folder);
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                java.nio.file.Path packs = (java.nio.file.Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
                java.nio.file.Path target = packs.resolve(source.getFileName());
                try (var files = java.nio.file.Files.walk(source)) {
                    for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
                        java.nio.file.Path to = target.resolve(source.relativize(file).toString());
                        if (java.nio.file.Files.isDirectory(file)) java.nio.file.Files.createDirectories(to);
                        else java.nio.file.Files.copy(file, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Object config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, source.getFileName().toString());
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
                config.getClass().getMethod("save").invoke(config);
                iris.getMethod("reload").invoke(null);
                log("shader pack " + source.getFileName());
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new IllegalStateException("Could not load the shader pack " + folder, e);
            }
        });
    }

    /** Back to the defaults between scenarios: settings, options, no screen. */
    static void reset(ClientGameTestContext context) {
        context.setScreen(() -> null);
        context.runOnClient(minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.options.fov().set(70);
            minecraft.options.renderDistance().set(12);
            minecraft.options.entityDistanceScaling().set(1.0);
            // The menus' blur is drawn on the CPU here: frames of the loading screen slow enough
            // with it for a new world to time out before it loads.
            minecraft.options.menuBackgroundBlurriness().set(0);
            FarConfig defaults = new FarConfig();
            FarConfig config = FarConfig.get();
            // Field by field: the config object is the one the mod holds.
            for (var field : FarConfig.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.set(config, field.get(defaults));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            // Birds and the easter eggs move on their own: off, so that two screenshots compare.
            config.skyBirds = false;
            config.easterEggs = false;
        });
    }

    /** Changes the mod's settings, on the render thread. Nothing is saved. */
    public static void configure(ClientGameTestContext context, Consumer<FarConfig> change) {
        context.runOnClient(minecraft -> change.accept(FarConfig.get()));
    }

    /**
     * A new flat world at noon, still: no day cycle, no weather, no mobs spawning. The
     * player stands at 0, 0 facing south (+z). Close it when done (try-with-resources).
     */
    public static TestSingleplayerContext flatWorld(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create();
        // The chunks round the player draw slowly without a graphics card.
        context.waitTicks(100);
        // The game rules took ids of their own in 1.21.11; the old names are refused there.
        //? if >=1.21.11 {
        command(world, "gamerule advance_time false");
        command(world, "gamerule spawn_mobs false");
        //?} else {
        /*command(world, "gamerule doDaylightCycle false");
        command(world, "gamerule doMobSpawning false");
        *///?}
        command(world, "weather clear");
        command(world, "time set noon");
        command(world, "tp @a 0 ~ 0 0 0");
        context.waitTicks(5);
        return world;
    }

    public static void command(TestSingleplayerContext world, String command) {
        world.getServer().runCommand(command);
    }

    /**
     * One mob standing still ({@code NoAI}) relative to the player, turned to {@code yaw}
     * (0 faces south, away from a player at the start; 180 faces the player).
     */
    public static void summon(TestSingleplayerContext world, String type, double x, double z, float yaw) {
        command(world, "execute at @p run summon " + type + " ~" + x + " ~ ~" + z
                + " {NoAI:1b,Rotation:[" + yaw + "f,0f]}");
    }

    /**
     * A row of mobs across the view, {@code z} blocks ahead, {@code spacing} apart, each
     * kind {@code each} times, turned 45 degrees more each time.
     */
    public static void row(TestSingleplayerContext world, String[] types, int each, double z, double spacing) {
        int count = types.length * each, i = 0;
        for (String type : types) {
            for (int n = 0; n < each; n++, i++) {
                summon(world, type, (i - (count - 1) / 2.0) * spacing, z, i * 45 % 360);
            }
        }
    }

    /** Takes a screenshot after letting a few frames settle; where it was saved. */
    public static java.nio.file.Path screenshot(ClientGameTestContext context, Scenario scenario, String name) {
        context.waitTicks(5);
        java.nio.file.Path path = context.takeScreenshot(scenario.name() + "-" + name);
        log("screenshot " + path);
        return path;
    }

    /**
     * The last frame the game drew itself, as it is on screen. A test screenshot draws a
     * frame of its own, outside the game's loop, where the debug gizmos (boxes, crosses)
     * cannot be collected: this one has them. Saved next to the other screenshots; where.
     */
    public static java.nio.file.Path screenshotFrame(ClientGameTestContext context, Scenario scenario, String name) {
        context.waitTicks(5);
        //? if >=1.21.11 {
        java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("screenshots").resolve("frame_" + scenario.name() + "-" + name + ".png");
        java.util.concurrent.CompletableFuture<Void> saved = new java.util.concurrent.CompletableFuture<>();
        context.runOnClient(minecraft -> net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget(),
                image -> {
                    try (image) {
                        java.nio.file.Files.createDirectories(path.getParent());
                        image.writeToFile(path);
                        saved.complete(null);
                    } catch (java.io.IOException e) {
                        saved.completeExceptionally(e);
                    }
                }));
        while (!saved.isDone()) context.waitTick();
        saved.join();
        log("screenshot " + path);
        return path;
        //?} else {
        /*return screenshot(context, scenario, name);
        *///?}
    }

    /** The impostor pages as they are in memory, at half size, side by side; where it was saved. */
    public static java.nio.file.Path screenshotAtlas(ClientGameTestContext context, Scenario scenario) {
        context.setScreen(AtlasScreen::new);
        java.nio.file.Path path = screenshot(context, scenario, "atlas");
        context.setScreen(() -> null);
        return path;
    }

    /**
     * Where one baked sheet is in the atlas screenshot: left, top, width and height in its
     * pixels, laid out as {@link AtlasScreen} draws the pages. Null when the sheet is not baked.
     */
    public static int @Nullable [] atlasRect(ImpostorKey key) {
        ImpostorAtlas.Sheet sheet = ImpostorAtlas.sheet(key);
        if (sheet == null) return null;
        int width = minecraft().getWindow().getGuiScaledWidth();
        int x = 0, y = 0, line = 0;
        for (int number = 0; number < ImpostorAtlas.pages(); number++) {
            int wide = ImpostorAtlas.pageWidth(number), tall = ImpostorAtlas.pageHeight(number);
            if (tall == 0) continue;
            if (x + wide / 2 > width) {
                x = 0;
                y += line + 8;
                line = 0;
            }
            if (number == sheet.page()) {
                int rowHeight = sheet.tile();
                return new int[]{x, y + sheet.row() * rowHeight / 2, wide / 2, rowHeight / 2};
            }
            x += wide / 2 + 8;
            line = Math.max(line, tall / 2);
        }
        return null;
    }

    /** The impostor pages, for {@link #screenshotAtlas}. */
    private static final class AtlasScreen extends Screen {
        AtlasScreen() {
            super(Component.literal("atlas"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF7090B0);
            int x = 0, y = 0, line = 0;
            for (int number = 0; number < ImpostorAtlas.pages(); number++) {
                int wide = ImpostorAtlas.pageWidth(number), tall = ImpostorAtlas.pageHeight(number);
                if (tall == 0) continue;
                if (x + wide / 2 > width) {
                    x = 0;
                    y += line + 8;
                    line = 0;
                }
                var page = ImpostorAtlas.id(number);
                //? if >=1.21.6 {
                graphics.blit(page, x, y, x + wide / 2, y + tall / 2, 0f, 1f, 0f, 1f);
                //?} else {
                /*graphics.blit(net.minecraft.client.renderer.rendertype.RenderTypes::guiTextured, page, x, y, 0f, 0f, wide / 2, tall / 2, wide, tall, wide, tall);
                *///?}
                x += wide / 2 + 8;
                line = Math.max(line, tall / 2);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
