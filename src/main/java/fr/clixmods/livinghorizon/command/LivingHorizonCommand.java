package fr.clixmods.livinghorizon.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.ambient.Egg;
import fr.clixmods.livinghorizon.ambient.EggLog;
import fr.clixmods.livinghorizon.ambient.Ufo;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.Links;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.Benchmark;
import fr.clixmods.livinghorizon.debug.BenchmarkWorld;
import fr.clixmods.livinghorizon.debug.DebugHud;
import fr.clixmods.livinghorizon.debug.ProfileLines;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.LastSeen;
import fr.clixmods.livinghorizon.track.Profiles;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Foresight;
import fr.clixmods.livinghorizon.track.Gear;
import fr.clixmods.livinghorizon.track.MobScan;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * {@code /livinghorizon}, a client command: it never reaches the server. The tree is built
 * for whatever source type the loader's command dispatcher carries - Fabric's own, or the
 * game's {@code CommandSourceStack} - and turns it into a {@link Source} before anything
 * runs, so what the commands do is written once.
 */
public final class LivingHorizonCommand {
    private LivingHorizonCommand() {
    }

    /** What a command needs of the one who ran it, however the loader names it. */
    public interface Source {
        Minecraft client();

        Player player();

        void feedback(Component message);
    }

    /**
     * The tree for the game's own source type, the one NeoForge and Forge hand their client
     * commands: a client command has no server behind it, so the game's client and player
     * are the ones to use, and the answer goes to the chat.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> forGame() {
        return tree(source -> new Source() {
            @Override
            public Minecraft client() {
                return Minecraft.getInstance();
            }

            @Override
            public Player player() {
                return Minecraft.getInstance().player;
            }

            @Override
            public void feedback(Component message) {
                //? if >=26.1 {
                /*Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
                *///?} else {
                Minecraft.getInstance().gui.getChat().addMessage(message);
                //?}
            }
        });
    }

    /** The command tree, for a dispatcher whose sources {@code source} turns into a {@link Source}. */
    /** The easter eggs' numbers from 9 on: 14 and 20 are no more. */
    private static final int[] MORE_EGGS = {9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 21};

    public static <S> LiteralArgumentBuilder<S> tree(Function<S, Source> source) {
        // One word per kind of bird, so that the chat offers them all.
        LiteralArgumentBuilder<S> birds = LiteralArgumentBuilder.literal("bird");
        for (Ambience.Type type : Ambience.Type.values()) {
            birds.then(LiteralArgumentBuilder.<S>literal(type.id).executes(c -> bird(source.apply(c.getSource()), type)));
        }
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal(LivingHorizonClient.MOD_ID);
        // The easter eggs from 9 on, by number, for whoever works on them (EGGS) as the others.
        for (int egg : MORE_EGGS) {
            root.then(LiteralArgumentBuilder.<S>literal("ee" + egg).requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()),
                    "ee" + egg, (level, player) -> {
                        Egg found = FarPlayerTracker.get().ambience().egg(egg);
                        return found != null && found.summon(level, player, true);
                    })));
        }
        return root
                        .executes(c -> status(source.apply(c.getSource())))
                        .then(LiteralArgumentBuilder.<S>literal("lod").executes(c -> lod(source.apply(c.getSource()))))
                        .then(LiteralArgumentBuilder.<S>literal("voxy").executes(c -> lod(source.apply(c.getSource()))))
                        .then(LiteralArgumentBuilder.<S>literal("debug").executes(c -> {
                            // Panel and boxes together, on or off.
                            FarConfig config = FarConfig.get();
                            boolean on = !(config.debugHud || config.debugBoxes);
                            config.debugHud = on;
                            config.debugBoxes = on;
                            DebugHud.setEntry(on);
                            FarConfig.save();
                            source.apply(c.getSource()).feedback(Component.translatable(
                                    on ? "livinghorizon.debug.on" : "livinghorizon.debug.off"));
                            return 1;
                        })
                                .then(offlineTests(source)))
                        .then(LiteralArgumentBuilder.<S>literal("foresee")
                                .executes(c -> foresee(source.apply(c.getSource()), FarConfig.get().foreseeRadius))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                IntegerArgumentType.integer(Foresight.MIN_RADIUS, Foresight.MAX_RADIUS))
                                        .executes(c -> foresee(source.apply(c.getSource()), IntegerArgumentType.getInteger(c, "radius")))))
                        .then(LiteralArgumentBuilder.<S>literal("scan")
                                .executes(c -> scan(source.apply(c.getSource()), FarConfig.get().scanRadius))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                IntegerArgumentType.integer(MobScan.MIN_RADIUS))
                                        .executes(c -> scan(source.apply(c.getSource()), IntegerArgumentType.getInteger(c, "radius")))))
                        // The easter eggs, numbered, and only for whoever works on them (EGGS).
                        .then(LiteralArgumentBuilder.<S>literal("ee1").requires(c -> EGGS).executes(c -> {
                            Minecraft client = source.apply(c.getSource()).client();
                            if (client.level == null || client.player == null) return 0;
                            Ufo.Call call = FarPlayerTracker.get().ambience().ufo().summon(client.level, client.player, true);
                            source.apply(c.getSource()).feedback(Component.translatable(call == Ufo.Call.NOTHING
                                    ? NOTHING : "livinghorizon.ee1." + call.name().toLowerCase(Locale.ROOT)));
                            return call == Ufo.Call.NOTHING ? 0 : 1;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("ee2").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee2",
                                (level, player) -> FarPlayerTracker.get().ambience().pass().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee3").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee3",
                                (level, player) -> FarPlayerTracker.get().ambience().ghosts().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee4").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee4",
                                (level, player) -> FarPlayerTracker.get().ambience().herobrine().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee5").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee5",
                                (level, player) -> FarPlayerTracker.get().ambience().waver().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee6").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee6",
                                (level, player) -> FarPlayerTracker.get().ambience().bigfoot().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee7").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee7",
                                (level, player) -> FarPlayerTracker.get().ambience().ghostBoat().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee8").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee8",
                                (level, player) -> FarPlayerTracker.get().ambience().ghostVillager().summon(level, player, true))))
                        .then(birds)
                        .then(LiteralArgumentBuilder.<S>literal("config").executes(c -> {
                            // After the chat screen, which closes once the command has run.
                            Minecraft client = source.apply(c.getSource()).client();
                            client.execute(() -> client.setScreen(new FarConfigScreen(null)));
                            return 1;
                        }))
                        // Only in a game started with -Dlh.benchmark=true: not a player's command.
                        .then(LiteralArgumentBuilder.<S>literal("benchmark").requires(c -> Benchmark.ON)
                                .executes(c -> benchmark(source.apply(c.getSource()), Benchmark.DEFAULT_SECONDS))
                                .then(LiteralArgumentBuilder.<S>literal("world")
                                        // Leaving the world is not done on a typo: said first, done on "confirm".
                                        .executes(c -> {
                                            source.apply(c.getSource()).feedback(
                                                    Component.translatable("livinghorizon.benchmark.world.explain"));
                                            return 1;
                                        })
                                        .then(LiteralArgumentBuilder.<S>literal("confirm").executes(c -> {
                                            Minecraft client = source.apply(c.getSource()).client();
                                            client.execute(() -> BenchmarkWorld.start(client));
                                            return 1;
                                        })))
                                .then(LiteralArgumentBuilder.<S>literal("stop").executes(c -> {
                                    Source from = source.apply(c.getSource());
                                    from.client().execute(() -> Benchmark.stop(from.client()));
                                    return 1;
                                }))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("seconds",
                                                IntegerArgumentType.integer(Benchmark.MIN_SECONDS, Benchmark.MAX_SECONDS))
                                        .executes(c -> benchmark(source.apply(c.getSource()),
                                                IntegerArgumentType.getInteger(c, "seconds")))))
                        .then(LiteralArgumentBuilder.<S>literal("impostors").executes(c -> {
                            Minecraft client = source.apply(c.getSource()).client();
                            client.execute(() -> client.setScreen(new ImpostorScreen(null)));
                            return 1;
                        }));
    }

    /**
     * {@code /livinghorizon benchmark [seconds]}: the frame rate with the mod off, on, and each
     * part of it on its own, written to a report. The player stands still meanwhile.
     */
    private static int benchmark(Source source, int seconds) {
        if (Benchmark.running()) {
            source.feedback(Component.translatable("livinghorizon.benchmark.running"));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.benchmark.started", Benchmark.duration(seconds)));
        Minecraft client = source.client();
        // After the chat screen, which closes once the command has run.
        client.execute(() -> Benchmark.start(client, seconds));
        return 1;
    }

    /** Test players placed at most at once, and the farthest they are spread. */
    private static final int MAX_TEST_PLAYERS = 100, MAX_TEST_RADIUS = 256;
    /** Blocks around you a crowd of test players is spread over when no radius is given. */
    private static final int CROWD_RADIUS = 16;
    /** Blocks above the ground gliding test players are placed at when no height is given. */
    private static final int GLIDE_HEIGHT = 30;

    /**
     * {@code /livinghorizon debug offline test ...}: test players who logged off, to try the
     * feature alone. {@code spawn [name] [minutes] [count] [radius]} places one where you
     * stand, wearing your skin, logged off that many minutes ago, on a copy of what you ride
     * if you ride something; with a count, that many ({@code name1}, {@code name2}...), each on
     * the ground somewhere within the radius around you. {@code crowd <count> [radius]
     * [minutes]} does the same with names of their own; {@code glide <count> [radius] [height]}
     * too, that many blocks above the ground in an elytra, as if they logged off gliding.
     * {@code clear} removes them;
     * {@code list} shows everyone resting on this server. Test players are never written to
     * the file.
     */
    private static <S> LiteralArgumentBuilder<S> offlineTests(Function<S, Source> source) {
        return LiteralArgumentBuilder.<S>literal("offline").then(LiteralArgumentBuilder.<S>literal("test")
                .then(LiteralArgumentBuilder.<S>literal("spawn")
                        .executes(c -> spawnOffline(source.apply(c.getSource()), 0, null, 0, 1, 0))
                        .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                .executes(c -> spawnOffline(source.apply(c.getSource()), 0,
                                        StringArgumentType.getString(c, "name"), 0, 1, 0))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("minutes", IntegerArgumentType.integer(0))
                                        .executes(c -> spawnOffline(source.apply(c.getSource()), 0,
                                                StringArgumentType.getString(c, "name"),
                                                IntegerArgumentType.getInteger(c, "minutes"), 1, 0))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("count",
                                                        IntegerArgumentType.integer(1, MAX_TEST_PLAYERS))
                                                .executes(c -> spawnOffline(source.apply(c.getSource()), 0,
                                                        StringArgumentType.getString(c, "name"),
                                                        IntegerArgumentType.getInteger(c, "minutes"),
                                                        IntegerArgumentType.getInteger(c, "count"), CROWD_RADIUS))
                                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                                IntegerArgumentType.integer(0, MAX_TEST_RADIUS))
                                                        .executes(c -> spawnOffline(source.apply(c.getSource()), 0,
                                                                StringArgumentType.getString(c, "name"),
                                                                IntegerArgumentType.getInteger(c, "minutes"),
                                                                IntegerArgumentType.getInteger(c, "count"),
                                                                IntegerArgumentType.getInteger(c, "radius"))))))))
                .then(LiteralArgumentBuilder.<S>literal("crowd")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, MAX_TEST_PLAYERS))
                                .executes(c -> spawnOffline(source.apply(c.getSource()), 0, null, 0,
                                        IntegerArgumentType.getInteger(c, "count"), CROWD_RADIUS))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                IntegerArgumentType.integer(0, MAX_TEST_RADIUS))
                                        .executes(c -> spawnOffline(source.apply(c.getSource()), 0, null, 0,
                                                IntegerArgumentType.getInteger(c, "count"),
                                                IntegerArgumentType.getInteger(c, "radius")))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("minutes", IntegerArgumentType.integer(0))
                                                .executes(c -> spawnOffline(source.apply(c.getSource()), 0, null,
                                                        IntegerArgumentType.getInteger(c, "minutes"),
                                                        IntegerArgumentType.getInteger(c, "count"),
                                                        IntegerArgumentType.getInteger(c, "radius")))))))
                .then(LiteralArgumentBuilder.<S>literal("glide")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, MAX_TEST_PLAYERS))
                                .executes(c -> spawnOffline(source.apply(c.getSource()), GLIDE_HEIGHT, null, 0,
                                        IntegerArgumentType.getInteger(c, "count"), CROWD_RADIUS))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                IntegerArgumentType.integer(0, MAX_TEST_RADIUS))
                                        .executes(c -> spawnOffline(source.apply(c.getSource()), GLIDE_HEIGHT, null, 0,
                                                IntegerArgumentType.getInteger(c, "count"),
                                                IntegerArgumentType.getInteger(c, "radius")))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("height",
                                                        IntegerArgumentType.integer(1, MAX_TEST_RADIUS))
                                                .executes(c -> spawnOffline(source.apply(c.getSource()),
                                                        IntegerArgumentType.getInteger(c, "height"), null, 0,
                                                        IntegerArgumentType.getInteger(c, "count"),
                                                        IntegerArgumentType.getInteger(c, "radius")))))))
                .then(LiteralArgumentBuilder.<S>literal("clear").executes(c -> {
                    int removed = FarPlayerTracker.get().resting().clearFakes();
                    source.apply(c.getSource()).feedback(Component.translatable("livinghorizon.offline.test.cleared", removed));
                    return removed;
                }))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(c -> listOffline(source.apply(c.getSource())))));
    }

    /**
     * Places {@code count} test players: one alone where you stand, several on the ground
     * within {@code radius} blocks around you, each facing its own way; {@code aloft} blocks
     * above it in an elytra, as if they logged off gliding, when it is not 0.
     */
    private static int spawnOffline(Source source, int aloft, @Nullable String name, int minutesAgo, int count,
                                    int radius) {
        Player self = source.player();
        ClientPacketListener connection = source.client().getConnection();
        if (self == null || connection == null) return 0;
        RestingPlayers resting = FarPlayerTracker.get().resting();
        List<String> names = new ArrayList<>();
        int n = 1;
        for (int i = 1; i <= count; i++) {
            if (name == null) {
                while (taken(resting, "Sleeper" + n)) n++;
                names.add("Sleeper" + n++);
            } else {
                names.add(count == 1 ? name : name + i);
            }
        }
        for (String each : names) {
            if (each.length() > 16) {
                source.feedback(Component.translatable("livinghorizon.offline.test.long", each));
                return 0;
            }
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                if (Profiles.name(info.getProfile()).equalsIgnoreCase(each)) {
                    // Online: they would be woken up on the next tick.
                    source.feedback(Component.translatable("livinghorizon.offline.test.online", each));
                    return 0;
                }
            }
        }
        // On what you ride, if anything: a copy of it, which each of them keeps.
        RestingPlayers.Mount mount = aloft > 0 ? null : RestingPlayers.Mount.ridden(self);
        boolean flies = mount != null && RestingPlayers.Mount.flies(self.getRootVehicle());
        String dimension = SharedPositions.dimensionId(self.level().dimension());
        Random random = new Random();
        List<String> skins = RestingPlayers.skinsFor(names.size(), random);
        for (String each : names) {
            if (count == 1 && radius == 0 && aloft == 0) {
                // A copy of you: your skin, what you wear and hold, what you ride.
                resting.fake(each, self.getGameProfile(), dimension, self.getX(), self.getY(), self.getZ(), self.getYRot(),
                        minutesAgo, mount, Gear.of(self, self.level().registryAccess()));
                continue;
            }
            // Evenly over the disc, on the ground where the blocks are known, at your height elsewhere.
            double angle = random.nextDouble() * Math.PI * 2, distance = radius * Math.sqrt(random.nextDouble());
            double x = self.getX() + Math.cos(angle) * distance, z = self.getZ() + Math.sin(angle) * distance;
            BlockPos column = BlockPos.containing(x, self.getY(), z);
            double y = self.level().getChunkSource().hasChunk(column.getX() >> 4, column.getZ() >> 4)
                    ? self.level().getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ())
                    : self.getY();
            if (mount != null) y += mount.seatY();
            // On a mount that flies, at your height, as you fly: above the ground still where it rises higher.
            if (flies) y = Math.max(y, self.getY());
            y += aloft;
            // The default skin of its name until the real player's skin is fetched.
            resting.fake(each, new GameProfile(UUIDUtil.createOfflinePlayerUUID(each), each), dimension, x, y, z,
                    random.nextFloat() * 360f - 180f, minutesAgo,
                    mount == null ? null : mount.facing(random.nextFloat() * 360f - 180f),
                    aloft > 0 ? Gear.only(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA), self.level().registryAccess())
                            : null);
            // Each in the skin of a real player, a different one each: a crowd, not clones of you.
            resting.dressFake(each, skins.get(names.indexOf(each)));
        }
        source.feedback(aloft > 0
                ? Component.translatable("livinghorizon.offline.test.gliding", count, aloft, radius)
                : count == 1 && radius == 0
                ? Component.translatable("livinghorizon.offline.test.spawned", names.get(0))
                : Component.translatable("livinghorizon.offline.test.spawnedMany", count, radius));
        if (!FarConfig.get().offlinePlayers) {
            source.feedback(Component.translatable("livinghorizon.offline.test.switchedOff", names.get(0)));
        }
        return count;
    }

    private static boolean taken(RestingPlayers resting, String name) {
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** The game's three dimensions by their name; one of a mod by its id, as the game has no name for it. */
    private static Component dimensionName(String dimension) {
        int vanilla = PositionsMessage.LEGACY_DIMENSIONS.indexOf(dimension);
        return vanilla >= 0 ? Component.translatable("livinghorizon.offline.test.dimension." + vanilla)
                : Component.literal(dimension);
    }

    /** Everyone resting on this server: where, and since when. */
    private static int listOffline(Source source) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        Collection<RestingPlayers.Spot> spots = resting.spots();
        if (spots.isEmpty()) {
            source.feedback(Component.translatable("livinghorizon.offline.test.none"));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.offline.test.header", spots.size()));
        String language = source.client().getLanguageManager().getSelected();
        for (RestingPlayers.Spot spot : spots) {
            String date = LastSeen.format(spot.lastSeen(), language, ZoneId.systemDefault());
            source.feedback(Component.translatable("livinghorizon.offline.test.line",
                    spot.name(),
                    dimensionName(spot.dimension()),
                    Math.round(spot.x()), Math.round(spot.y()), Math.round(spot.z()),
                    date == null ? Component.translatable("livinghorizon.offline.test.unknown") : Component.literal(date),
                    resting.isFake(spot) ? Component.translatable("livinghorizon.offline.test.mark") : Component.empty()));
        }
        return spots.size();
    }

    /**
     * The easter eggs' commands, for whoever works on them ({@link EggLog#ON}). Without it
     * they are not there at all, not even among the suggestions: nothing in the game tells a
     * player which easter eggs there are.
     */
    private static final boolean EGGS = EggLog.ON;

    /** What an easter egg answers when it cannot come: the off switch, the wrong place. */
    private static final String NOTHING = "livinghorizon.ee.nothing";

    /** Calls an easter egg now; it answers {@code livinghorizon.<egg>.on}, or {@link #NOTHING}. */
    private static int egg(Source source, String egg, BiPredicate<ClientLevel, LocalPlayer> call) {
        Minecraft client = source.client();
        if (client.level == null || client.player == null) return 0;
        boolean on = call.test(client.level, client.player);
        source.feedback(Component.translatable(on ? "livinghorizon." + egg + ".on" : NOTHING));
        return on ? 1 : 0;
    }

    /** Blocks along the player's look within which the place looked at is taken for summoned birds. */
    private static final double LOOK = 128;

    /**
     * {@code /livinghorizon bird <kind>}: a group of that kind now. Birds that perch go
     * where the player looks when it suits them (a tree, water, a field), else nearby.
     */
    private static int bird(Source source, Ambience.Type type) {
        Minecraft client = source.client();
        if (client.level == null || client.player == null) return 0;
        HitResult hit = client.player.pick(LOOK, 1.0f, true);
        BlockPos looked = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
        Component name = Component.translatable("livinghorizon.options.birdTypes." + type.id);
        Ambience.Summon result = FarPlayerTracker.get().ambience().summon(type, client.level, client.player, FarConfig.get(),
                looked);
        source.feedback(Component.translatable("livinghorizon.bird." + result.name().toLowerCase(Locale.ROOT), name));
        return result == Ambience.Summon.DONE ? 1 : 0;
    }

    /** {@code /livinghorizon scan [radius]}: the mobs of the chunks around, read from a single player world. */
    private static int scan(Source source, int radius) {
        Minecraft client = source.client();
        // After the command has run, outside the command dispatcher.
        client.execute(() -> MobScan.start(client, radius));
        return MobScan.available(client) ? 1 : 0;
    }

    /**
     * {@code /livinghorizon foresee [radius]}: the first animals of the land nobody explored within
     * that many chunks, foreseen from the seed (a world played here, or a server sharing it).
     */
    private static int foresee(Source source, int radius) {
        Minecraft client = source.client();
        client.execute(() -> FarPlayerTracker.get().mobs().foresight().start(client, radius));
        return FarPlayerTracker.get().mobs().foresight().available(client) ? 1 : 0;
    }

    /**
     * {@code /livinghorizon lod} ({@code voxy} too): whether the mod can see the far terrain
     * of Voxy or Distant Horizons. Aim at a far mountain: the answer is how far the first
     * block they know along that line is.
     */
    private static int lod(Source source) {
        LodWorld.Status status = LodWorld.status();
        if (!status.loaded()) {
            source.feedback(Component.translatable("livinghorizon.lod.missing"));
            return 0;
        }
        if (status.failure() != null) {
            source.feedback(Component.translatable("livinghorizon.lod.failed", status.name(), status.failure()));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.lod.source", status.name()));
        source.feedback(Component.translatable("livinghorizon.lod.depth", FarDepth.state()));
        for (Links.Report link : Links.report()) {
            source.feedback(Component.translatable("livinghorizon.lod.link", link.mod(), link.status()));
        }
        // At once, before the probe, which reads hundreds of columns and may answer late.
        source.feedback(Component.translatable("livinghorizon.lod.details", fr.clixmods.livinghorizon.compat.DrawnGround.status()));
        Player self = source.player();
        Minecraft client = source.client();
        LodWorld.probe(self.getEyePosition(), self.getLookAngle(), 4096).whenComplete((hit, error) ->
                client.execute(() -> {
                    String details = LodWorld.details();
                    if (!details.isEmpty()) source.feedback(Component.translatable("livinghorizon.lod.details", details));
                    source.feedback(error != null
                        ? Component.translatable("livinghorizon.lod.probe.error", status.name(), String.valueOf(error.getMessage()))
                        : Double.isNaN(hit)
                        ? Component.translatable("livinghorizon.lod.probe.none", status.name())
                        : Component.translatable("livinghorizon.lod.probe.hit", status.name(), Math.round(hit)));
                }));
        return 1;
    }

    /** {@code /livinghorizon}: who is followed, from what, and how sure the position is. */
    private static int status(Source source) {
        source.feedback(Component.translatable(FarPlayerTracker.get().sharing()
                ? "livinghorizon.status.pack.on" : "livinghorizon.status.pack.off"));
        source.feedback(Component.translatable("livinghorizon.status.mobs",
                FarPlayerTracker.get().mobs().size(), FarPlayerTracker.get().mobs().shown().size()));
        source.feedback(Component.translatable("livinghorizon.status.cost", Stats.tickMillis(), Stats.extractMillis(),
                Stats.drawn(), Stats.skipped()));
        // The numbers of each section; the GPU's time is measured from now on, and shows the next time.
        source.feedback(Component.translatable("livinghorizon.debug.hud.profile"));
        for (ProfileLines.Line line : ProfileLines.lines()) source.feedback(Component.literal(line.content()));
        Collection<FarPlayer> players = FarPlayerTracker.get().players();
        if (players.isEmpty()) {
            source.feedback(Component.translatable("livinghorizon.status.none"));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.status.header", players.size()));
        Player self = source.player();
        ClientPacketListener connection = source.client().getConnection();
        for (FarPlayer player : players) {
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.id());
            String name = info == null ? player.id().toString() : Profiles.name(info.getProfile());
            String key = player.vanished() ? "vanished" : player.source().name().toLowerCase(Locale.ROOT);
            double distance = player.placed() ? Math.hypot(player.x() - self.getX(), player.z() - self.getZ()) : Double.NaN;
            double uncertainty = player.uncertainty();
            source.feedback(Component.translatable("livinghorizon.status.line",
                    name,
                    Component.translatable("livinghorizon.source." + key),
                    Double.isNaN(distance) ? "?" : String.valueOf(Math.round(distance)),
                    Double.isNaN(uncertainty) ? "?" : String.valueOf(Math.round(uncertainty))));
        }
        return players.size();
    }
}
