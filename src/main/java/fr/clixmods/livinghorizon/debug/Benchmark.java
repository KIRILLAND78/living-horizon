package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /livinghorizon benchmark}: what the mod costs, measured in the player's own world.
 *
 * <p>The player stands still; the settings go through a list of phases - the mod off, the
 * player's own settings, each quality preset, each part of the mod on its own - and the
 * time of every frame is noted in each, after a few seconds for the change to settle. The
 * mod off comes first and last, so that a drift (chunks still loading, a GPU heating up)
 * shows. The debug views are off throughout, the frame rate cap and the game's own
 * slowdowns are lifted, and everything is put back at the end. The report is a text file
 * in {@code livinghorizon/benchmarks/} of the game folder, made to be sent with a bug report.
 *
 * <p>In the world {@link BenchmarkWorld} makes, the player is put on its spot, facing south,
 * and the render distance, entity distance and field of view are fixed for the run: two
 * reports from that world, before and after an update, measure the same scene.
 *
 * <p>Only in a game started with {@code -Dlh.benchmark=true} ({@link #ON}): without it the
 * command is not there at all, not even among the suggestions.
 */
public final class Benchmark {
    /** Whether the game was started with {@code -Dlh.benchmark=true}, the only way to get the command. */
    public static final boolean ON = Boolean.getBoolean("lh.benchmark");

    /** One step: a name for the report, and what it changes in the player's settings. */
    private record Phase(String id, String what, Consumer<FarConfig> setup, boolean measured) {
        Phase(String id, String what, Consumer<FarConfig> setup) {
            this(id, what, setup, true);
        }

        boolean off() {
            return id.startsWith("off");
        }
    }

    /** What one phase measured. */
    private record Result(Phase phase, long[] frames, String tickMillis, String extractMillis, int drawn, int skipped,
                          int shown, int live, int models, int impostors, int polygons, int flyers, String queries,
                          String farDepth, boolean moved, boolean screen) {
        double seconds() {
            long total = 0;
            for (long frame : frames) total += frame;
            return total / 1e9;
        }

        double fps() {
            return frames.length == 0 ? 0 : frames.length / seconds();
        }

        double averageMillis() {
            return frames.length == 0 ? 0 : seconds() * 1000 / frames.length;
        }

        /** The frame time at this fraction of the sorted frames, in milliseconds. */
        double percentileMillis(double fraction) {
            if (frames.length == 0) return 0;
            long[] sorted = frames.clone();
            Arrays.sort(sorted);
            return sorted[Math.min(sorted.length - 1, (int) Math.floor(fraction * sorted.length))] / 1e6;
        }

        /** The frame rate over the slowest one percent of the frames: how the stutters feel. */
        double lowFps() {
            if (frames.length == 0) return 0;
            long[] sorted = frames.clone();
            Arrays.sort(sorted);
            int count = Math.max(1, sorted.length / 100);
            long total = 0;
            for (int i = sorted.length - count; i < sorted.length; i++) total += sorted[i];
            return count / (total / 1e9);
        }
    }

    /** Seconds each phase is left to settle before it is measured: copies built, pictures baked. */
    private static final double SETTLE = 4.0;
    /**
     * The first phase, not measured: the game warms up for it (code compiled as it runs, the
     * graphics card raising its clocks, the chunks around still built). Measured, a first
     * phase with the mod off came out a quarter slower than the same phase at the end.
     */
    private static final double SETTLE_FIRST = 20.0;
    public static final int DEFAULT_SECONDS = 8, MIN_SECONDS = 3, MAX_SECONDS = 60;
    /** Blocks or degrees the player may move before a phase is marked as moved. */
    private static final double MOVE = 0.05, TURN = 0.5;

    private static @Nullable List<Phase> phases;
    private static final List<Result> RESULTS = new ArrayList<>();
    private static int index;
    private static double measureSeconds;
    private static long phaseStart, lastFrame, lastOverlay;
    private static boolean measuring, moved, screen;
    private static long[] frames = new long[4096];
    private static int frameCount;
    private static double startX, startY, startZ, startYaw, startPitch;
    private static @Nullable FarConfig saved;
    private static int savedFramerate;
    private static boolean savedVsync;
    /** The view options, fixed in the benchmark world and put back after. */
    private static int savedRenderDistance, savedFov;
    private static double savedEntityDistance;
    private static boolean fixedScene;
    //? if >=1.21.2
    private static net.minecraft.client.InactivityFpsLimit savedInactivity;

    private Benchmark() {
    }

    public static boolean running() {
        return phases != null;
    }

    /** Starts it, measuring each phase for {@code seconds}. False when it cannot: no world, or already running. */
    public static boolean start(Minecraft minecraft, int seconds) {
        LocalPlayer player = minecraft.player;
        if (running() || player == null || minecraft.level == null) return false;
        measureSeconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        saved = copy(FarConfig.get());
        phases = phases();
        RESULTS.clear();
        index = -1;
        notePose(player);
        fixedScene = BenchmarkWorld.isIn(minecraft);
        savedRenderDistance = minecraft.options.renderDistance().get();
        savedEntityDistance = minecraft.options.entityDistanceScaling().get();
        savedFov = minecraft.options.fov().get();
        if (fixedScene) {
            // The same scene every run: the world's spot and view, whatever the player had.
            BenchmarkWorld.toSpot(minecraft);
            minecraft.options.renderDistance().set(BenchmarkWorld.RENDER_DISTANCE);
            minecraft.options.entityDistanceScaling().set(1.0);
            minecraft.options.fov().set(70);
            BenchmarkWorld.placeCrowd(minecraft);
        }
        // The game's caps would measure the cap, not the game.
        savedFramerate = minecraft.options.framerateLimit().get();
        savedVsync = minecraft.options.enableVsync().get();
        minecraft.options.framerateLimit().set(260);
        minecraft.options.enableVsync().set(false);
        //? if >=1.21.2 {
        // Standing still for minutes is what the game takes for being away from the keyboard.
        savedInactivity = minecraft.options.inactivityFpsLimit().get();
        minecraft.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
        //?}
        next(minecraft);
        return true;
    }

    /** How long it takes, in seconds. */
    public static int duration(int seconds) {
        double each = SETTLE + Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        return (int) Math.ceil((phases().size() - 1) * each + SETTLE_FIRST);
    }

    /** Stops it before the end; the settings are put back and nothing is written. */
    public static void stop(Minecraft minecraft) {
        if (!running()) return;
        restore(minecraft);
        say(minecraft, Component.translatable("livinghorizon.benchmark.stopped"));
    }

    /** Every frame, from the top of the game's loop. */
    public static void frame(Minecraft minecraft) {
        if (!running()) return;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) {
            restore(minecraft);
            say(minecraft, Component.translatable("livinghorizon.benchmark.aborted"));
            return;
        }
        long now = System.nanoTime();
        double elapsed = (now - phaseStart) / 1e9;
        double settle = index == 0 ? SETTLE_FIRST : SETTLE;
        // The message above the hotbar fades after a few seconds: said again while the phase runs.
        if (now - lastOverlay > 1_000_000_000L) {
            lastOverlay = now;
            overlay(minecraft, progress());
        }
        if (!measuring) {
            if (elapsed < settle) return;
            if (!phases.get(index).measured) {
                next(minecraft);
                return;
            }
            measuring = true;
            frameCount = 0;
            lastFrame = now;
            return;
        }
        if (frameCount == frames.length) frames = Arrays.copyOf(frames, frames.length * 2);
        frames[frameCount++] = now - lastFrame;
        lastFrame = now;
        if (Math.abs(player.getX() - startX) > MOVE || Math.abs(player.getY() - startY) > MOVE
                || Math.abs(player.getZ() - startZ) > MOVE || Math.abs(player.getYRot() - startYaw) > TURN
                || Math.abs(player.getXRot() - startPitch) > TURN) {
            moved = true;
        }
        if (minecraft.screen != null) screen = true;
        if (elapsed >= settle + measureSeconds) {
            RESULTS.add(result());
            next(minecraft);
        }
    }

    private static void next(Minecraft minecraft) {
        List<Phase> list = phases;
        if (list == null) return;
        index++;
        if (index >= list.size()) {
            finish(minecraft);
            return;
        }
        Phase phase = list.get(index);
        // Where the player stands once the warm-up is over: what "moved" is measured from.
        if (index == 1 && minecraft.player != null) notePose(minecraft.player);
        FarConfig config = FarConfig.get();
        set(config, saved);
        quiet(config);
        phase.setup.accept(config);
        phaseStart = System.nanoTime();
        measuring = false;
        moved = false;
        screen = false;
        lastOverlay = System.nanoTime();
        overlay(minecraft, progress());
    }

    private static void notePose(LocalPlayer player) {
        startX = player.getX();
        startY = player.getY();
        startZ = player.getZ();
        startYaw = player.getYRot();
        startPitch = player.getXRot();
    }

    /** "Benchmark 3/13: distant mobs only - stand still", in the player's language. */
    private static Component progress() {
        List<Phase> list = phases;
        if (list == null || index < 0 || index >= list.size()) return Component.empty();
        return Component.translatable("livinghorizon.benchmark.phase", index + 1, list.size(),
                Component.translatable("livinghorizon.benchmark.phase." + list.get(index).id));
    }

    private static Result result() {
        Phase phase = phases.get(index);
        return new Result(phase, Arrays.copyOf(frames, frameCount), Stats.tickMillis(), Stats.extractMillis(),
                Stats.drawn(), Stats.skipped(), FarPlayerTracker.get().mobs().shown().size(),
                FarPlayerTracker.get().mobs().live().size(), PolygonStats.models(), PolygonStats.impostors(),
                PolygonStats.polygons(), FarPlayerTracker.get().ambience().flyers().size(), OcclusionQueries.state(),
                FarDepth.state(), moved, screen);
    }

    private static void finish(Minecraft minecraft) {
        String report = report(minecraft);
        restore(minecraft);
        Path folder = minecraft.gameDirectory.toPath().resolve(LivingHorizonClient.MOD_ID).resolve("benchmarks");
        Path file = folder.resolve("benchmark-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt");
        try {
            Files.createDirectories(folder);
            Files.writeString(file, report);
            say(minecraft, Component.translatable("livinghorizon.benchmark.done", file.toAbsolutePath().toString()));
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
            say(minecraft, Component.translatable("livinghorizon.benchmark.failed", String.valueOf(e.getMessage())));
        }
        LivingHorizonClient.LOGGER.info("Living Horizon benchmark:\n{}", report);
    }

    /** The settings and options as they were, the benchmark over. */
    private static void restore(Minecraft minecraft) {
        if (saved != null) set(FarConfig.get(), saved);
        saved = null;
        phases = null;
        minecraft.options.framerateLimit().set(savedFramerate);
        minecraft.options.enableVsync().set(savedVsync);
        //? if >=1.21.2
        minecraft.options.inactivityFpsLimit().set(savedInactivity);
        if (fixedScene) {
            BenchmarkWorld.clearCrowd();
            minecraft.options.renderDistance().set(savedRenderDistance);
            minecraft.options.entityDistanceScaling().set(savedEntityDistance);
            minecraft.options.fov().set(savedFov);
        }
    }

    // --- The phases ---------------------------------------------------------------------------

    private static List<Phase> phases() {
        List<Phase> list = new ArrayList<>();
        list.add(new Phase("warmup", "warming up, your settings, not measured", config -> {
        }, false));
        list.add(new Phase("off", "mod off", Benchmark::off));
        list.add(new Phase("yours", "your settings", config -> {
        }));
        for (QualityPreset preset : QualityPreset.values()) {
            list.add(new Phase(preset.id(), "preset " + preset.id(), preset::apply));
        }
        // The mod off between the halves too: the baseline is the mean of three, and their spread is the drift.
        list.add(new Phase("off-middle", "mod off, between", Benchmark::off));
        list.add(new Phase("players", "distant players only", config -> {
            off(config);
            config.enabled = true;
        }));
        list.add(new Phase("mobs", "distant mobs only", Benchmark::mobsOnly));
        list.add(new Phase("birds", "birds only", config -> {
            off(config);
            config.skyBirds = true;
        }));
        list.add(new Phase("mobs-no-impostors", "distant mobs, Simplified Entities off", config -> {
            mobsOnly(config);
            config.impostors = false;
            config.impostorEntities = false;
        }));
        list.add(new Phase("mobs-no-occlusion", "distant mobs, Skip Hidden Mobs off", config -> {
            mobsOnly(config);
            config.hideOccludedMobs = false;
        }));
        list.add(new Phase("mobs-no-far-depth", "distant mobs, Hide Behind Far Terrain off", config -> {
            mobsOnly(config);
            config.depthOcclusion = false;
        }));
        list.add(new Phase("off-again", "mod off, again", Benchmark::off));
        return list;
    }

    private static void off(FarConfig config) {
        config.enabled = false;
        config.distantMobs = false;
        config.distantEntities = false;
        config.skyBirds = false;
    }

    private static void mobsOnly(FarConfig config) {
        off(config);
        config.distantMobs = true;
    }

    /**
     * No debug view: what is measured is what a player has. No easter egg either: they come
     * by chance, and one in a single phase would show as a cost.
     */
    private static void quiet(FarConfig config) {
        config.easterEggs = false;
        config.debugHud = false;
        config.debugBoxes = false;
        config.debugLabels = false;
        config.debugGameMobs = false;
        config.outlineGameMobs = false;
        config.outlineLiveMobs = false;
        config.outlineCopies = false;
        config.outlineForeseen = false;
        config.outlinePlayers = false;
        config.outlineImpostors = false;
        config.outlineLeftOut = false;
        config.debugDepthView = 0;
    }

    // --- The report ---------------------------------------------------------------------------

    private static String report(Minecraft minecraft) {
        StringBuilder out = new StringBuilder();
        out.append("Living Horizon benchmark, ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n\n");
        environment(minecraft, out);

        double offMillis = 0;
        int offs = 0;
        for (Result result : RESULTS) {
            if (result.phase.off() && result.frames.length > 0) {
                offMillis += result.averageMillis();
                offs++;
            }
        }
        offMillis = offs == 0 ? 0 : offMillis / offs;
        double fastest = Double.MAX_VALUE, slowest = 0;
        for (Result result : RESULTS) {
            if (!result.phase.off() || result.frames.length == 0) continue;
            fastest = Math.min(fastest, result.averageMillis());
            slowest = Math.max(slowest, result.averageMillis());
        }

        out.append("== Frames (").append(fmt(measureSeconds, 0)).append(" s measured per phase, after ")
                .append(fmt(SETTLE, 0)).append(" s to settle, after ").append(fmt(SETTLE_FIRST, 0))
                .append(" s of warm-up)\n");
        out.append(String.format(Locale.ROOT, "%-20s %8s %8s %8s %8s %8s %9s  %s%n",
                "phase", "fps", "1% low", "ms avg", "ms p99", "ms max", "cost ms", "notes"));
        for (Result result : RESULTS) {
            String notes = (result.moved ? "MOVED " : "") + (result.screen ? "SCREEN-OPEN " : "")
                    + (result.frames.length == 0 ? "NO-FRAMES" : "");
            out.append(String.format(Locale.ROOT, "%-20s %8s %8s %8s %8s %8s %9s  %s%n", result.phase.id,
                    fmt(result.fps(), 1), fmt(result.lowFps(), 1), fmt(result.averageMillis(), 2),
                    fmt(result.percentileMillis(0.99), 2), fmt(result.percentileMillis(1.0), 2),
                    (result.averageMillis() - offMillis >= 0 ? "+" : "") + fmt(result.averageMillis() - offMillis, 2),
                    notes.trim()));
        }
        out.append("cost ms: the average frame time minus the mod off's, the mean of its ").append(offs).append(" phases.\n");
        if (offs > 1) {
            out.append("drift: the mod off's phases are ").append(fmt(slowest - fastest, 2))
                    .append(" ms apart; a cost smaller than that is noise.\n");
        }
        out.append("\n");

        out.append("== What the mod did, at the end of each phase\n");
        out.append(String.format(Locale.ROOT, "%-20s %8s %8s %7s %7s %7s %6s %7s %10s %9s %6s%n", "phase", "tick ms",
                "extr. ms", "drawn", "skipped", "copies", "live", "models", "impostors", "polygons", "birds"));
        for (Result result : RESULTS) {
            out.append(String.format(Locale.ROOT, "%-20s %8s %8s %7d %7d %7d %6d %7d %10d %9d %6d%n", result.phase.id,
                    result.tickMillis, result.extractMillis, result.drawn, result.skipped, result.shown, result.live,
                    result.models, result.impostors, result.polygons, result.flyers));
        }
        out.append("tick ms: the mod's work per game tick; extr. ms: preparing the distant figures, per frame.\n\n");

        out.append("== Occlusion and far terrain depth, at the end of each phase\n");
        for (Result result : RESULTS) {
            out.append(String.format(Locale.ROOT, "%-20s queries: %s | far depth: %s%n", result.phase.id,
                    result.queries, result.farDepth));
        }
        out.append("\n== Phases\n");
        for (Result result : RESULTS) {
            out.append(String.format(Locale.ROOT, "%-20s %s%n", result.phase.id, result.phase.what));
        }
        out.append("\n== Your settings\n").append(settings(saved == null ? FarConfig.get() : saved)).append("\n");
        return out.toString();
    }

    private static void environment(Minecraft minecraft, StringBuilder out) {
        out.append("== Game\n");
        String game = version("minecraft");
        out.append("Minecraft ").append(game == null || game.isEmpty() ? minecraft.getLaunchedVersion() : game)
                .append(", Living Horizon ")
                .append(version(LivingHorizonClient.MOD_ID)).append("\n");
        out.append("Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" cores, ")
                .append(Runtime.getRuntime().maxMemory() / (1024 * 1024)).append(" MB max memory\n");
        if (DepthFar.openGl()) {
            out.append("GPU: ").append(GL11C.glGetString(GL11C.GL_RENDERER)).append(" (")
                    .append(GL11C.glGetString(GL11C.GL_VENDOR)).append("), OpenGL ")
                    .append(GL11C.glGetString(GL11C.GL_VERSION)).append("\n");
        } else {
            out.append("GPU: not OpenGL (Vulkan)\n");
        }
        out.append("Window ").append(minecraft.getWindow().getWidth()).append("x").append(minecraft.getWindow().getHeight())
                .append(", render distance ").append(minecraft.options.renderDistance().get())
                .append(", simulation distance ").append(minecraft.options.simulationDistance().get())
                .append(", entity distance ").append(fmt(minecraft.options.entityDistanceScaling().get() * 100, 0))
                .append("%, FOV ").append(minecraft.options.fov().get()).append("\n");
        out.append("World: ").append(fixedScene ? "the benchmark world (fixed spot and view: comparable between runs)"
                        : minecraft.getSingleplayerServer() != null ? "single player" : "server")
                .append(", ").append(minecraft.level == null ? "?" : minecraft.level.dimension().identifier())
                .append(", ").append(FarPlayerTracker.get().mobs().size()).append(" mobs remembered, ")
                .append(FarPlayerTracker.get().players().size()).append(" other players followed, ")
                .append(FarPlayerTracker.get().resting().spots().size()).append(" players who logged off\n");
        StringBuilder mods = new StringBuilder();
        for (String id : new String[]{"voxy", "distanthorizons", "iris", "oculus", "sodium", "embeddium", "lithium", "c2me",
                "immediatelyfast", "entityculling", "moreculling", "nvidium", "distant_friends"}) {
            String version = version(id);
            if (version != null) mods.append(mods.isEmpty() ? "" : ", ").append(id).append(version.isEmpty() ? "" : " " + version);
        }
        out.append("Mods: ").append(mods.isEmpty() ? "none of the ones looked for" : mods)
                .append(FarDepth.shaderPack() ? " (shader pack on)" : "").append("\n\n");
    }

    /** The version of a mod, "" when the loader does not tell, null when it is not loaded. */
    private static @Nullable String version(String id) {
        return Platform.modVersion(id);
    }

    private static String settings(FarConfig config) {
        StringBuilder out = new StringBuilder();
        for (var field : FarConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() == java.util.List.class) continue;
            try {
                out.append(field.getName()).append(" = ").append(field.get(config)).append("\n");
            } catch (IllegalAccessException ignored) {
                // Public fields only.
            }
        }
        QualityPreset preset = QualityPreset.of(config);
        out.append("preset = ").append(preset == null ? "custom" : preset.id()).append("\n");
        return out.toString();
    }

    private static String fmt(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    // --- Settings, copied field by field: the config object is the one the mod holds ----------

    private static FarConfig copy(FarConfig from) {
        FarConfig copy = new FarConfig();
        set(copy, from);
        return copy;
    }

    private static void set(FarConfig to, @Nullable FarConfig from) {
        if (from == null) return;
        for (var field : FarConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            try {
                Object value = field.get(from);
                field.set(to, value instanceof List<?> list ? new ArrayList<>(list) : value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // --- Messages -------------------------------------------------------------------------------

    private static void say(Minecraft minecraft, Component message) {
        //? if >=26.1 {
        /*minecraft.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        minecraft.gui.getChat().addMessage(message);
        //?}
    }

    private static void overlay(Minecraft minecraft, Component message) {
        if (minecraft.player == null) return;
        //? if >=26.1 {
        /*minecraft.player.sendOverlayMessage(message);
        *///?} else {
        minecraft.player.displayClientMessage(message, true);
        //?}
    }
}
