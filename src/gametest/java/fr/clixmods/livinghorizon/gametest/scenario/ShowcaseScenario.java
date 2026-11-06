package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.Random;

/**
 * Pictures to show the mod: a natural world at 1920x1080, a village seen from a hill with
 * herds in the fields between, the same view with the mod on and off. Tuned with
 * environment variables (LH_SHOW_*), for a person choosing the shot; it checks nothing.
 */
public final class ShowcaseScenario implements Scenario {
    @Override
    public String name() {
        return "showcase";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int envInt(String name, int fallback) {
        return Integer.parseInt(env(name, String.valueOf(fallback)));
    }

    @Override
    public void run(ClientGameTestContext context) {
        String seed = env("LH_SHOW_SEED", "living horizon");
        int distance = envInt("LH_SHOW_RD", 12);
        int fov = envInt("LH_SHOW_FOV", 50);
        long time = Long.parseLong(env("LH_SHOW_TIME", "2000"));
        int above = envInt("LH_SHOW_ABOVE", 8);
        int away = envInt("LH_SHOW_AWAY", 150);
        int villages = envInt("LH_SHOW_VILLAGES", 1);
        boolean both = !env("LH_SHOW_MODE", "both").equals("explore");

        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(10);
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed(seed))
                .create()) {
            Scene.command(world, "gamerule advance_time false");
            Scene.command(world, "gamerule spawn_monsters false");
            Scene.command(world, "weather clear");
            Scene.command(world, "time set " + time);
            Scene.command(world, "gamemode creative @a");
            // Peaceful would remove the endermen, piglins and ghasts at once.
            Scene.command(world, "difficulty normal");
            context.runOnClient(minecraft -> {
                hideGui(minecraft, true);
                minecraft.options.renderDistance().set(distance);
                minecraft.options.fov().set(fov);
                noChunkFade(minecraft);
            });
            context.waitTicks(40);
            if (fr.clixmods.livinghorizon.platform.Platform.isModLoaded("distanthorizons")) {
                Scene.log("showcase Distant Horizons: " + dhDistance(envInt("LH_SHOW_DH_RD", 48)));
            }

            for (String dimension : env("LH_SHOW_DIMS", "overworld").split(",")) {
                switch (dimension.trim()) {
                    case "nether" -> nether(context, world, both);
                    case "end" -> end(context, world, both);
                    default -> overworld(context, world, villages, away, above, both);
                }
            }
        } finally {
            context.runOnClient(minecraft -> {
                hideGui(minecraft, false);
                minecraft.options.fov().set(70);
            });
            context.getInput().resizeWindow(854, 480);
        }
    }

    private void overworld(ClientGameTestContext context, TestSingleplayerContext world, int villages, int away, int above,
                           boolean both) {
            BlockPos origin = BlockPos.ZERO;
            int skipped = 0;
            for (int v = 0; v < villages; v++) {
                BlockPos from = origin;
                BlockPos village = world.getServer().computeOnServer(server -> server.overworld()
                        .findNearestMapStructure(StructureTags.VILLAGE, from, 100, false));
                if (village == null) {
                    Scene.log("showcase no village near " + from);
                    break;
                }
                String biome = world.getServer().computeOnServer(server -> server.overworld()
                        .getBiome(village.atY(70)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
                Scene.log("showcase village " + village.getX() + " " + village.getZ() + " in " + biome);
                origin = village.offset(1200, 0, 600);
                if (!env("LH_SHOW_BIOMES", "plains,meadow,savanna,sunflower_plains").contains(biome)) {
                    v--;
                    if (++skipped > 12) break;
                    continue;
                }
                // Loads the village and its surroundings before looking for a hill.
                Scene.command(world, "tp @a " + village.getX() + " 220 " + village.getZ());
                fly(world);
                context.waitTicks(200);
                shoot(context, world, village, away, above, both, "v" + v);
            }
    }

    /**
     * Over a lava sea of the Nether: the direction from a spot above the lava where the view
     * goes farthest, with striders on the lava, hoglins, piglins and ghasts along it.
     */
    private void nether(ClientGameTestContext context, TestSingleplayerContext world, boolean both) {
        int eye = envInt("LH_SHOW_NETHER_Y", 48);
        String in = "execute in minecraft:the_nether run ";
        Scene.command(world, in + "tp @a 0 " + eye + " 0");
        fly(world);
        context.waitTicks(200);
        // The farthest open view at eye height, from a few spots round the origin.
        double[] best = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.getLevel(net.minecraft.world.level.Level.NETHER);
            double[] found = {0, 0, 0, -1};
            for (int cx = -192; cx <= 192; cx += 96) {
                for (int cz = -192; cz <= 192; cz += 96) {
                    if (!level.getBlockState(new BlockPos(cx, eye, cz)).isAir()) continue;
                    for (int i = 0; i < 24; i++) {
                        double angle = i * Math.PI * 2 / 24;
                        // Open straight ahead and towards both edges of the picture.
                        int free = Integer.MAX_VALUE;
                        for (double turn : new double[]{-0.45, 0, 0.45}) {
                            free = Math.min(free, open(level, cx, cz, eye, angle + turn) + (turn == 0 ? 0 : 40));
                        }
                        if (free > found[3]) found = new double[]{cx, cz, angle, free};
                    }
                }
            }
            return found;
        });
        double cx = best[0], cz = best[1], angle = best[2], free = best[3];
        float yaw = (float) Math.toDegrees(angle);
        Scene.log(String.format(Locale.ROOT, "showcase nether camera %.0f %d %.0f yaw %.0f, open for %.0f blocks", cx, eye, cz, yaw, free));
        Random random = new Random(11);
        String[] kinds = {"strider", "hoglin", "piglin", "strider", "zombified_piglin", "hoglin", "strider", "piglin"};
        double fx = -Math.sin(angle), fz = Math.cos(angle), rx = -fz, rz = fx;
        for (int h = 0; h < kinds.length; h++) {
            double along = 45 + random.nextDouble() * Math.max(60, free + 40);
            double side = (random.nextDouble() - 0.5) * along * 0.7;
            double hx = cx + fx * along + rx * side, hz = cz + fz * along + rz * side;
            for (int n = 0, count = 3 + random.nextInt(4); n < count; n++) {
                int x = (int) Math.floor(hx + random.nextGaussian() * 4), z = (int) Math.floor(hz + random.nextGaussian() * 4);
                int[] floor = world.getServer().computeOnServer(server -> floor(server.getLevel(net.minecraft.world.level.Level.NETHER),
                        x, z, eye + 10));
                if (floor == null) continue;
                // Striders on the lava, the others on solid ground.
                String kind = floor[1] == 1 ? "strider" : kinds[h].equals("strider") ? "hoglin" : kinds[h];
                Scene.command(world, in + "summon " + kind + " " + (x + 0.5) + " " + floor[0] + " " + (z + 0.5)
                        + " {Rotation:[" + random.nextInt(360) + "f,0f]}");
            }
        }
        for (int n = 0; n < 4; n++) {
            double along = 70 + random.nextDouble() * Math.max(60, free);
            double side = (random.nextDouble() - 0.5) * along * 0.6;
            Scene.command(world, String.format(Locale.ROOT, in + "summon ghast %.1f %d %.1f", cx + fx * along + rx * side,
                    eye + 4 + random.nextInt(14), cz + fz * along + rz * side));
        }
        Scene.command(world, String.format(Locale.ROOT, in + "tp @a %.1f %d %.1f %.1f %s", cx, eye, cz, yaw,
                env("LH_SHOW_NETHER_PITCH", "8")));
        fly(world);
        frame(context, world, both, "nether");
    }

    /** How far the view is open from a point at eye height, in blocks. */
    private static int open(ServerLevel level, double cx, double cz, int eye, double angle) {
        int free = 0;
        for (int t = 4; t <= 220; t += 4) {
            BlockPos at = BlockPos.containing(cx - Math.sin(angle) * t, eye - t * 0.08, cz + Math.cos(angle) * t);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above(12)).isAir()) break;
            free = t;
        }
        return free;
    }

    /** The top of the floor under a point (scanning down), and 1 when it is lava; null when none. */
    private static int @org.jspecify.annotations.Nullable [] floor(ServerLevel level, int x, int z, int from) {
        for (int y = from; y > level.getMinY() + 4; y--) {
            BlockPos at = new BlockPos(x, y, z);
            var state = level.getBlockState(at);
            if (state.isAir()) continue;
            boolean lava = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
            if (!lava && state.getCollisionShape(level, at).isEmpty()) continue;
            return new int[]{y + 1, lava ? 1 : 0};
        }
        return null;
    }

    /**
     * The End's main island from out over the void: the dragon gone, endermen all over the
     * island, near and far.
     */
    private void end(ClientGameTestContext context, TestSingleplayerContext world, boolean both) {
        String in = "execute in minecraft:the_end run ";
        int away = envInt("LH_SHOW_END_AWAY", 170), eye = envInt("LH_SHOW_END_Y", 85);
        double angle = Math.toRadians(Double.parseDouble(env("LH_SHOW_END_ANGLE", "200")));
        double cx = Math.cos(angle) * away, cz = Math.sin(angle) * away;
        Scene.command(world, in + "tp @a 0 90 0");
        fly(world);
        context.waitTicks(200);
        Scene.command(world, in + "kill @e[type=ender_dragon]");
        Random random = new Random(5);
        int placed = 0;
        for (int n = 0; n < 400 && placed < envInt("LH_SHOW_ENDERMEN", 40); n++) {
            int x = (int) (random.nextGaussian() * 60), z = (int) (random.nextGaussian() * 60);
            int y = world.getServer().computeOnServer(server -> server.getLevel(net.minecraft.world.level.Level.END)
                    .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            if (y < 50 || y > 75) continue;
            Scene.command(world, in + "summon enderman " + (x + 0.5) + " " + y + " " + (z + 0.5)
                    + " {Rotation:[" + random.nextInt(360) + "f,0f]}");
            placed++;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(cx, -cz));
        float pitch = (float) Math.toDegrees(Math.atan2(eye - 62, away)) + Float.parseFloat(env("LH_SHOW_TILT", "-2"));
        Scene.log(String.format(Locale.ROOT, "showcase end camera %.0f %d %.0f yaw %.0f pitch %.1f, %d endermen", cx, eye, cz, yaw,
                pitch, placed));
        Scene.command(world, String.format(Locale.ROOT, in + "tp @a %.1f %d %.1f %.1f %.1f", cx, eye, cz, yaw, pitch));
        fly(world);
        frame(context, world, both, "end");
    }

    /** Waits for the chunks, then the shot with the mod on, and off. */
    private void frame(ClientGameTestContext context, TestSingleplayerContext world, boolean both, String label) {
        settle(context, world);
        on(context);
        context.waitTicks(60);
        Scene.screenshot(context, this, label + "-on");
        if (both) {
            off(context);
            context.waitTicks(40);
            Scene.screenshot(context, this, label + "-off");
            on(context);
        }
        Scene.log("showcase " + label + " " + context.computeOnClient(minecraft -> minecraft.getFps() + " fps"));
    }

    /** Distant Horizons' render distance, in chunks, through its API; what it holds afterwards. */
    private static String dhDistance(int chunks) {
        String api = "com.seibel.distanthorizons.api.", config = api + "interfaces.config.";
        try {
            Object configs = Class.forName(api + "DhApi$Delayed").getField("configs").get(null);
            Object graphics = Class.forName(config + "IDhApiConfig").getMethod("graphics").invoke(configs);
            Object distance = Class.forName(config + "client.IDhApiGraphicsConfig").getMethod("chunkRenderDistance").invoke(graphics);
            Class<?> value = Class.forName(config + "IDhApiConfigValue");
            value.getMethod("setValue", Object.class).invoke(distance, chunks);
            return "render distance " + value.getMethod("getValue").invoke(distance) + " chunks";
        } catch (ReflectiveOperationException e) {
            return "could not set its render distance: " + e;
        }
    }

    /**
     * New chunks fade in over a time of their own from 26.x: at a few frames a second they
     * are still dithered in the shot. Through reflection, the option being missing before.
     */
    private static void noChunkFade(net.minecraft.client.Minecraft minecraft) {
        try {
            Object option = minecraft.options.getClass().getMethod("chunkSectionFadeInTime").invoke(minecraft.options);
            option.getClass().getMethod("set", Object.class).invoke(option, 0.0);
        } catch (ReflectiveOperationException e) {
            // No fade on this version.
        }
    }

    private static void hideGui(net.minecraft.client.Minecraft minecraft, boolean hide) {
        //? if >=26.2 {
        /*if (minecraft.gui.hud.isHidden() != hide) minecraft.gui.hud.toggle();
        *///?} else
        minecraft.options.hideGui = hide;
    }

    private static void fly(TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
        });
    }

    private static int ground(TestSingleplayerContext world, int x, int z) {
        return world.getServer().computeOnServer(server ->
                server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    private void shoot(ClientGameTestContext context, TestSingleplayerContext world, BlockPos village, int away, int above,
                       boolean both, String label) {
        int vy = ground(world, village.getX(), village.getZ());
        // The highest ground at that distance round the village with a clear view of it.
        double bestAngle = 0;
        int bestY = Integer.MIN_VALUE, bestBlocked = Integer.MAX_VALUE;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            double x = village.getX() + Math.cos(angle) * away, z = village.getZ() + Math.sin(angle) * away;
            int y = ground(world, (int) x, (int) z);
            double eye = Math.max(y, 63) + above;
            int blocked = 0;
            for (int t = 3; t < away - 40; t += 3) {
                double f = t / (double) away;
                double px = x + (village.getX() - x) * f, pz = z + (village.getZ() - z) * f;
                double sight = eye + (vy + 3 - eye) * f;
                int top = world.getServer().computeOnServer(server ->
                        server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) px, (int) pz));
                if (top > sight - 1) blocked++;
            }
            if (blocked < bestBlocked || blocked == bestBlocked && y > bestY) {
                bestBlocked = blocked;
                bestY = y;
                bestAngle = angle;
            }
        }
        String fixed = env("LH_SHOW_ANGLE", "");
        if (!fixed.isBlank()) {
            bestAngle = Math.toRadians(Double.parseDouble(fixed));
            bestY = ground(world, (int) (village.getX() + Math.cos(bestAngle) * away),
                    (int) (village.getZ() + Math.sin(bestAngle) * away));
        }
        double cx = village.getX() + Math.cos(bestAngle) * away, cz = village.getZ() + Math.sin(bestAngle) * away;
        double eye = Math.max(bestY, 63) + above;
        double dx = village.getX() - cx, dz = village.getZ() - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(eye + 1.6 - (vy + 2), Math.hypot(dx, dz)))
                + Float.parseFloat(env("LH_SHOW_TILT", "-2"));
        Scene.log(String.format(Locale.ROOT, "showcase %s camera %.0f %.0f %.0f yaw %.0f pitch %.1f, village ground %d, blocked %d",
                label, cx, eye, cz, yaw, pitch, vy, bestBlocked));

        herds(world, cx, cz, village, vy);
        Scene.command(world, String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f %.1f %.1f", cx, eye, cz, yaw, pitch));
        fly(world);
        settle(context, world);
        on(context);
        context.waitTicks(60);
        Scene.screenshot(context, this, label + "-on");
        if (both) {
            off(context);
            context.waitTicks(40);
            Scene.screenshot(context, this, label + "-off");
            on(context);
        }
        Scene.log("showcase " + label + " " + context.computeOnClient(minecraft -> minecraft.getFps() + " fps"));
    }

    /**
     * Lets the chunks round the camera be built and drawn, at a small window size: drawn at
     * 1920x1080 on the CPU, the frames take the cores the chunk builders need.
     */
    static void settle(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().resizeWindow(854, 480);
        context.waitTicks(60);
        for (int round = 0; round < envInt("LH_SHOW_ROUNDS", 4); round++) {
            try {
                //? if >=26.1 {
                /*world.getConnection().waitForChunksRender(1200);
                *///?} else
                world.getClientWorld().waitForChunksRender(1200);
                break;
            } catch (Throwable e) {
                Scene.log("showcase chunks not all drawn yet (" + round + ")");
            }
        }
        context.waitTicks(envInt("LH_SHOW_WAIT", 200));
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(40);
    }

    /** Herds in the fields between the hill and the village, and a few more villagers in it. */
    private static void herds(TestSingleplayerContext world, double cx, double cz, BlockPos village, int vy) {
        Random random = new Random(7);
        String[] kinds = {"cow", "sheep", "horse", "sheep", "cow", "pig", "horse", "sheep"};
        double dx = village.getX() - cx, dz = village.getZ() - cz, length = Math.hypot(dx, dz);
        double fx = dx / length, fz = dz / length, rx = -fz, rz = fx;
        for (int h = 0; h < kinds.length; h++) {
            double along = length * (0.4 + 0.75 * random.nextDouble());
            double side = (random.nextDouble() - 0.5) * along * 0.75;
            double hx = cx + fx * along + rx * side, hz = cz + fz * along + rz * side;
            int count = 3 + random.nextInt(4);
            for (int n = 0; n < count; n++) {
                summon(world, kinds[h], hx + random.nextGaussian() * 4, hz + random.nextGaussian() * 4, random);
            }
        }
        for (int n = 0; n < envInt("LH_SHOW_VILLAGERS", 8); n++) {
            summon(world, "villager", village.getX() + random.nextGaussian() * 10, village.getZ() + random.nextGaussian() * 10, random);
        }
    }

    private static void summon(TestSingleplayerContext world, String kind, double x, double z, Random random) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int y = ground(world, bx, bz);
        if (y <= 63 && !kind.equals("villager")) return; // not in the water
        Scene.command(world, "summon " + kind + " " + (bx + 0.5) + " " + y + " " + (bz + 0.5)
                + " {Rotation:[" + random.nextInt(360) + "f,0f]}");
    }

    private static void on(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            FarConfig defaults = new FarConfig();
            config.enabled = defaults.enabled;
            config.distantMobs = defaults.distantMobs;
            config.distantEntities = defaults.distantEntities;
            config.skyBirds = Boolean.parseBoolean(env("LH_SHOW_BIRDS", "true"));
            // Every kind a player's game shows, monsters too: the tests start from the shared ones only.
            config.resetMobs(fr.clixmods.livinghorizon.ui.MobList.all());
        });
    }

    private static void off(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.enabled = false;
            config.distantMobs = false;
            config.distantEntities = false;
            config.skyBirds = false;
        });
    }
}
