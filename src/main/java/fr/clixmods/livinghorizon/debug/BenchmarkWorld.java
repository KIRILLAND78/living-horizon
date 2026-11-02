package fr.clixmods.livinghorizon.debug;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.mixin.CreateWorldScreenAccessor;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code /livinghorizon benchmark world}: a new single player world to run the benchmark in,
 * the same every time it is made. It is added to the world list; the world the player was in
 * is saved and closed, never changed.
 *
 * <p>Nothing in it is left to chance: a flat world of a fixed seed without structures, time
 * stopped at noon, clear weather, no mob ever spawning. A flat world spawns animals where its
 * chunks are generated, so every chunk the benchmark can see is generated first and emptied;
 * then every mob is summoned here, of a chosen kind, at a chosen place, facing a chosen way,
 * with its own fixed id and no AI. Given data, {@code /summon} leaves a mob as it is: a sheep
 * stays white, a horse keeps its coat.
 *
 * <p>The player stands at 0, 0 looking south, where the mod's parts each have a zone: mobs the
 * game draws itself, mobs it leaves to the mod past its entity distance, some of them behind a
 * wall, remembered copies far away (more than the most drawn at once by default), some behind
 * a hill, a herd past the default mob distance, and places birds land on. While the benchmark
 * runs here, a crowd of players who logged off stands among them ({@link #placeCrowd}).
 *
 * <p>Once built, a marker file in the world's folder says it is this world: {@link Benchmark}
 * then puts the player back on the spot and fixes the view, so that runs before and after
 * an update compare.
 */
public final class BenchmarkWorld {
    /** The seed, the same for everyone. */
    private static final String SEED = "20261014";
    /** Every mob placed here carries it; everything else is removed. */
    public static final String TAG = "lh_bench";
    /** Chunks around the start that are generated and emptied: past the render distance and the farthest herd. */
    private static final int RADIUS_CHUNKS = 48;
    /** Chunks along a side of what is generated at once. */
    private static final int BATCH = 16;
    /** Chunks around the player read for remembered copies once built. */
    private static final int SCAN_RADIUS = 48;
    /** The ground's top block in a flat world; mobs stand one above. */
    private static final int GROUND = -61, FEET = -60;
    /** The file in the world's folder that marks it as this world once built. */
    private static final String MARKER = "livinghorizon-benchmark";
    /** The render distance the benchmark runs at here, the game's default. */
    public static final int RENDER_DISTANCE = 12;
    private static final String[] KINDS = {"cow", "sheep", "pig", "chicken", "horse", "villager", "wolf", "cow"};
    /** The names of the crowd of players who logged off, numbered: {@code LhBench1} and on. */
    private static final String CROWD = "LhBench";
    /** The rows of the crowd, blocks south of the spot, clear of the mobs' rows, the wall and the hill. */
    private static final int[] CROWD_ROWS = {48, 80, 112, 144, 208, 240};
    /** The columns of the crowd, blocks east of the spot. */
    private static final int CROWD_FROM_X = -84, CROWD_TO_X = 84, CROWD_STEP_X = 24;

    private enum Step { LEAVE, OPEN, CREATE, JOIN, BUILD, SCAN, IDLE }

    /** A fill or a summon, placed in the world. */
    private record Piece(int x1, int y1, int z1, int x2, int y2, int z2, String command) {
        boolean summon() {
            return x1 == x2 && z1 == z2 && command.startsWith("summon");
        }
    }

    private static Step step = Step.IDLE;
    private static int wait;
    private static final List<Piece> PIECES = new ArrayList<>();
    private static int batch;
    /** Work handed to the server thread and not done yet. */
    private static volatile boolean busy;
    private static volatile boolean batchLoaded;
    private static boolean batchForced;

    private BenchmarkWorld() {
    }

    public static boolean running() {
        return step != Step.IDLE;
    }

    /** Leaves this world and makes the benchmark's. */
    public static void start(Minecraft minecraft) {
        if (running()) return;
        step = Step.LEAVE;
        wait = 0;
    }

    /** Every client tick, in a world or not. */
    public static void tick(Minecraft minecraft) {
        switch (step) {
            case IDLE -> {
            }
            case LEAVE -> {
                if (minecraft.level != null) leave(minecraft);
                step = Step.OPEN;
            }
            case OPEN -> {
                // The world is saved and the server stopped before another is made.
                if (minecraft.level != null || minecraft.getSingleplayerServer() != null) return;
                open(minecraft);
                step = Step.CREATE;
                wait = 0;
            }
            case CREATE -> {
                if (!(minecraft.screen instanceof CreateWorldScreen screen)) {
                    if (++wait > 20 * 60) fail(minecraft, "the world creation screen never opened");
                    return;
                }
                if (!configure(screen.getUiState())) {
                    fail(minecraft, "no flat world type");
                    return;
                }
                ((CreateWorldScreenAccessor) screen).livinghorizon$create();
                step = Step.JOIN;
                wait = 0;
            }
            case JOIN -> {
                if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
                // A few seconds for the spawn chunks to settle before anything is built.
                if (++wait < 60) return;
                PIECES.clear();
                PIECES.addAll(pieces());
                batch = 0;
                batchForced = false;
                step = Step.BUILD;
                say(minecraft, Component.translatable("livinghorizon.benchmark.world.building"));
                server(minecraft, BenchmarkWorld::rules);
            }
            case BUILD -> build(minecraft);
            case SCAN -> {
                if (busy) return;
                if (++wait == 20) {
                    // Far herds are never sent to this client: read from the world, as a scan does.
                    MobScan.start(minecraft, SCAN_RADIUS);
                } else if (wait > 200) {
                    step = Step.IDLE;
                    mark(minecraft);
                    say(minecraft, Component.translatable("livinghorizon.benchmark.world.ready"));
                }
            }
        }
    }

    /** Whether the player is in a world this made, built to the end. */
    public static boolean isIn(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(MARKER));
    }

    /** Puts the player back where the world was built to be seen from: 0, 0, facing south, level. */
    public static void toSpot(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        minecraft.player.setYRot(0);
        minecraft.player.setXRot(0);
        server.execute(() -> run(server, server.createCommandSourceStack().withSuppressedOutput(),
                "tp @a 0 " + FEET + " 0 0 0"));
    }

    /**
     * Places the crowd of players who logged off, the same every run: test players are never
     * saved with a world, so the benchmark places them as it starts here and {@link #clearCrowd}
     * takes them away as it ends. Near enough to be drawn as models, far enough to be pictures,
     * some behind the wall and the hill. In the default skin of their names, wearing nothing:
     * nothing is fetched, and this game's server, which shares them back, says the same of them.
     */
    public static void placeCrowd(Minecraft minecraft) {
        if (minecraft.level == null) return;
        RestingPlayers resting = FarPlayerTracker.get().resting();
        String dimension = SharedPositions.dimensionId(minecraft.level.dimension());
        int n = 0;
        for (int z : CROWD_ROWS) {
            for (int x = CROWD_FROM_X; x <= CROWD_TO_X; x += CROWD_STEP_X) {
                String name = CROWD + ++n;
                resting.fake(name, new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name), dimension,
                        x + 0.5, FEET, z + 0.5, (n * 137) % 360 - 180f, 60, null, List.of());
            }
        }
    }

    /** Takes the crowd away; test players placed by hand stay. */
    public static void clearCrowd() {
        String prefix = CROWD.toLowerCase(Locale.ROOT);
        FarPlayerTracker.get().resting().clearFakes(name -> name.startsWith(prefix));
    }

    /** How many players who logged off the crowd holds. */
    public static int crowd() {
        return CROWD_ROWS.length * ((CROWD_TO_X - CROWD_FROM_X) / CROWD_STEP_X + 1);
    }

    /** How many of the crowd are placed now. */
    public static int crowdPlaced() {
        int count = 0;
        for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
            if (spot.name().startsWith(CROWD)) count++;
        }
        return count;
    }

    private static void mark(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) return;
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
        try {
            Files.writeString(marker, "Made by /livinghorizon benchmark world: the benchmark runs here at a fixed spot.\n");
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Benchmark world: could not write {}", marker, e);
        }
    }

    private static void fail(Minecraft minecraft, String why) {
        step = Step.IDLE;
        LivingHorizonClient.LOGGER.warn("Benchmark world: {}", why);
        say(minecraft, Component.translatable("livinghorizon.benchmark.world.failed", why));
    }

    // --- Making the world ---------------------------------------------------------------------

    /** The world as it is to be made: flat, the fixed seed, no structures, creative. */
    private static boolean configure(WorldCreationUiState ui) {
        WorldCreationUiState.WorldTypeEntry flat = Stream.concat(ui.getNormalPresetList().stream(), ui.getAltPresetList().stream())
                .filter(entry -> entry.preset() != null && entry.preset().is(WorldPresets.FLAT))
                .findFirst().orElse(null);
        if (flat == null) return false;
        ui.setName(I18n.get("livinghorizon.benchmark.world.name"));
        ui.setWorldType(flat);
        ui.setSeed(SEED);
        ui.setGenerateStructures(false);
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        return true;
    }

    /** Saves and closes the world the player is in, back to the title screen. */
    public static void leave(Minecraft minecraft) {
        // As the pause screen's "Save and Quit": the connection closed first, or the integrated
        // server never stops.
        //? if >=1.21.6 {
        minecraft.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        minecraft.disconnectWithSavingScreen();
        //?} elif >=1.20.5 {
        /*minecraft.level.disconnect();
        minecraft.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.translatable("menu.savingLevel")));
        *///?} elif >=1.20.2 {
        /*minecraft.level.disconnect();
        minecraft.disconnect(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        *///?} else {
        /*minecraft.level.disconnect();
        minecraft.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        *///?}
        // The world saved, back to the title screen, as the pause screen does.
        minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
    }

    private static void open(Minecraft minecraft) {
        //? if >=1.21.9 {
        CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
        //?} else {
        /*CreateWorldScreen.openFresh(minecraft, new net.minecraft.client.gui.screens.TitleScreen());
        *///?}
    }

    // --- Building it ----------------------------------------------------------------------------

    /** The rules that keep the world still. Old and new names alike: a name the game does not know does nothing. */
    private static void rules(IntegratedServer server, CommandSourceStack source) {
        for (String rule : new String[]{"doDaylightCycle", "advance_time", "doWeatherCycle", "advance_weather",
                "doMobSpawning", "spawn_mobs", "doPatrolSpawning", "spawn_patrols", "doTraderSpawning",
                "spawn_wandering_traders", "doInsomnia", "spawn_phantoms", "doFireTick", "fire_spread_radius_around_player"}) {
            run(server, source, "gamerule " + rule + " false");
        }
        run(server, source, "gamerule randomTickSpeed 0");
        run(server, source, "gamerule random_tick_speed 0");
        run(server, source, "time set 6000");
        run(server, source, "weather clear");
        run(server, source, "tp @a 0 " + FEET + " 0 0 0");
    }

    /**
     * One batch of chunks at a time: forced loaded, generated, emptied of everything the
     * generation spawned, given its pieces, let go. Then everything is saved for the scan.
     */
    private static void build(Minecraft minecraft) {
        if (busy) return;
        int side = (2 * RADIUS_CHUNKS + BATCH - 1) / BATCH;
        if (batch >= side * side) {
            step = Step.SCAN;
            wait = 0;
            server(minecraft, (server, source) -> {
                run(server, source, "kill @e[type=!minecraft:player,tag=!" + TAG + "]");
                run(server, source, "tp @a 0 " + FEET + " 0 0 0");
                run(server, source, "save-all flush");
            });
            return;
        }
        int bx = -RADIUS_CHUNKS + (batch % side) * BATCH, bz = -RADIUS_CHUNKS + (batch / side) * BATCH;
        int x1 = bx * 16, z1 = bz * 16, x2 = (bx + BATCH) * 16 - 1, z2 = (bz + BATCH) * 16 - 1;
        if (!batchForced) {
            batchForced = true;
            batchLoaded = false;
            overlay(minecraft, Component.translatable("livinghorizon.benchmark.world.progress", batch + 1, side * side));
            server(minecraft, (server, source) -> run(server, source, "forceload add " + x1 + " " + z1 + " " + x2 + " " + z2));
            return;
        }
        if (!batchLoaded) {
            server(minecraft, (server, source) -> {
                ServerLevel level = server.overworld();
                // The entities too, which load apart from the blocks: a mob summoned before its
                // chunk's entities are loaded is never saved once the chunk is let go.
                for (int cx = bx; cx < bx + BATCH; cx++) {
                    for (int cz = bz; cz < bz + BATCH; cz++) {
                        if (!level.getChunkSource().hasChunk(cx, cz)
                                || !level.areEntitiesLoaded(chunk(cx, cz))) return;
                    }
                }
                batchLoaded = true;
            });
            return;
        }
        server(minecraft, (server, source) -> {
            run(server, source, "kill @e[type=!minecraft:player,tag=!" + TAG + "]");
            for (Piece piece : PIECES) place(server, source, piece, x1, z1, x2, z2);
            run(server, source, "forceload remove " + x1 + " " + z1 + " " + x2 + " " + z2);
        });
        batch++;
        batchForced = false;
    }

    /** A chunk's position as one number, as the level's entity lookups take it. */
    private static long chunk(int x, int z) {
        // Renamed in 26.1.
        //? if >=26.1 {
        /*return net.minecraft.world.level.ChunkPos.pack(x, z);
        *///?} else
        return net.minecraft.world.level.ChunkPos.asLong(x, z);
    }

    /** The part of a piece inside this batch: a fill is cut to it, a summon is in it or not. */
    private static void place(IntegratedServer server, CommandSourceStack source, Piece piece, int x1, int z1, int x2, int z2) {
        int lowX = Math.max(piece.x1, x1), highX = Math.min(piece.x2, x2);
        int lowZ = Math.max(piece.z1, z1), highZ = Math.min(piece.z2, z2);
        if (lowX > highX || lowZ > highZ) return;
        if (piece.summon()) {
            run(server, source, piece.command);
        } else {
            run(server, source, "fill " + lowX + " " + piece.y1 + " " + lowZ + " " + highX + " " + piece.y2 + " " + highZ
                    + " " + piece.command);
        }
    }

    /** How many mobs the world holds once built. */
    public static int mobs() {
        return (int) pieces().stream().filter(Piece::summon).count();
    }

    /** What the world holds, always the same. */
    private static List<Piece> pieces() {
        List<Piece> pieces = new ArrayList<>();
        int[] id = {0};
        // Near, 20 to 56 blocks: drawn by the game itself.
        grid(pieces, id, -36, 36, 12, 20, 56, 12, 180);
        // Middle, 72 to 136 blocks: sent by the server, past the game's entity distance, drawn by the mod.
        grid(pieces, id, -96, 96, 16, 72, 136, 16, 180);
        // A wall across the left of the middle herd, between two of its rows: the mobs behind it
        // are hidden. Never on a row: a mob inside a block suffocates.
        pieces.add(fill(-104, FEET, 96, -40, FEET + 19, 96, "minecraft:stone"));
        // Far, 192 to 480 blocks: never sent to this client, remembered copies, more than drawn at once.
        grid(pieces, id, -208, 208, 16, 192, 480, 16, 180);
        // A hill in front of the right of the far herd, short of its first row: copies behind terrain.
        for (int level = 0; level < 12; level++) {
            pieces.add(fill(64 + level * 2, FEET + level * 2, 166 + level * 2, 176 - level * 2, FEET + level * 2 + 1,
                    190 - level * 2, "minecraft:grass_block"));
        }
        // Very far, 576 to 672 blocks: past the default mob distance, for the presets that reach it.
        grid(pieces, id, -128, 128, 32, 576, 672, 32, 180);
        // Where birds land: a pond, a wheat field, a tree, a tower.
        pieces.add(fill(20, GROUND, 16, 32, GROUND, 28, "minecraft:water"));
        pieces.add(fill(-40, GROUND, 16, -24, GROUND, 28, "minecraft:farmland"));
        pieces.add(fill(-40, FEET, 16, -24, FEET, 28, "minecraft:wheat[age=7]"));
        pieces.add(fill(48, FEET, 30, 48, FEET + 5, 30, "minecraft:oak_log"));
        pieces.add(fill(45, FEET + 4, 27, 51, FEET + 7, 33, "minecraft:oak_leaves[persistent=true] keep"));
        pieces.add(fill(60, FEET, 60, 62, FEET + 24, 62, "minecraft:stone_bricks"));
        return pieces;
    }

    /** Mobs on a grid, one kind after the other, each facing {@code yaw}, each with its own fixed id. */
    private static void grid(List<Piece> pieces, int[] id, int fromX, int toX, int stepX, int fromZ, int toZ, int stepZ, int yaw) {
        for (int z = fromZ; z <= toZ; z += stepZ) {
            for (int x = fromX; x <= toX; x += stepX) {
                int n = id[0]++;
                String kind = KINDS[n % KINDS.length];
                String command = "summon minecraft:" + kind + " " + x + " " + FEET + " " + z
                        + " {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"" + TAG + "\"],Rotation:[" + yaw + "f,0f],"
                        + "UUID:[I;1279806316,1652,0," + n + "]}";
                pieces.add(new Piece(x, FEET, z, x, FEET, z, command));
            }
        }
    }

    private static Piece fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return new Piece(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2),
                Math.max(z1, z2), block);
    }

    // --- The server ----------------------------------------------------------------------------

    @FunctionalInterface
    private interface Work {
        void run(IntegratedServer server, CommandSourceStack source);
    }

    /** Hands work to the server's thread, where the world is; the next step waits for it. */
    private static void server(Minecraft minecraft, Work work) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            fail(minecraft, "the world closed");
            return;
        }
        busy = true;
        server.execute(() -> {
            try {
                work.run(server, server.createCommandSourceStack().withSuppressedOutput());
            } catch (RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Benchmark world: a step failed", e);
            } finally {
                busy = false;
            }
        });
    }

    private static void run(IntegratedServer server, CommandSourceStack source, String command) {
        server.getCommands().performPrefixedCommand(source, command);
    }

    // --- Messages -------------------------------------------------------------------------------

    private static void say(Minecraft minecraft, Component message) {
        //? if >=26.1 {
        /*minecraft.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        minecraft.gui.getChat().addMessage(message);
        //?}
    }

    private static void overlay(Minecraft minecraft, @Nullable Component message) {
        if (minecraft.player == null || message == null) return;
        //? if >=26.1 {
        /*minecraft.player.sendOverlayMessage(message);
        *///?} else {
        minecraft.player.displayClientMessage(message, true);
        //?}
    }
}
