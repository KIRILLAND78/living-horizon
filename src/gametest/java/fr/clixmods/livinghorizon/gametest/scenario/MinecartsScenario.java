package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Minecarts, and mobs riding a minecart or a boat, seen from past the game's entity
 * distance (the server still sends them: the mod draws them), then left behind (the mod
 * draws its remembered copies).
 *
 * <p>A minecart sent off along powered rails leaves the client at 64 blocks (entity
 * distance 0.5) while the server keeps moving it within its simulation distance (12
 * chunks): its copy must roll on and stop about where the server's cart stops, at the end
 * of the line. A villager remembered in its minecart must stay in it, not stroll off.
 */
public final class MinecartsScenario implements Scenario {
    /** The minecart a mob or a player rides: the one kind of minecart that is a vehicle. */
    private static final String[] CARTS = {"minecart"};
    /** {@code UUID:[I;0,0,0,42]}, as the game writes it. */
    private static final UUID MOVING = new UUID(0L, 42L);
    /** The minecart the villager sits in, {@code UUID:[I;0,0,0,43]}. */
    private static final UUID CARRYING = new UUID(0L, 43L);
    /** The villager riding the moving minecart, {@code UUID:[I;0,0,0,44]}. */
    private static final UUID PASSENGER = new UUID(0L, 44L);
    /** The villager sitting in the parked minecart, {@code UUID:[I;0,0,0,45]}. */
    private static final UUID SEATED = new UUID(0L, 45L);

    @Override
    public String name() {
        return "minecarts";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            // The game stops drawing a minecart at about 27 blocks; the server sends it to 64.
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
            minecraft.options.simulationDistance().set(12);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            // Minecarts only seat their riders: listed, but shown once chosen.
            for (String cart : CARTS) config.setMobShown("minecraft:" + cart, true);
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a cart that vanishes closer is taken as broken.
            Scene.row(world, CARTS, 3, 40, 3.0);
            Scene.command(world, "execute at @p run summon minecraft:minecart ~-4 ~ ~50 "
                    + "{UUID:[I;0,0,0,43],Passengers:[{id:\"minecraft:villager\",UUID:[I;0,0,0,45],NoAI:1b}]}");
            Scene.command(world, "execute at @p run summon minecraft:oak_boat ~4 ~ ~50 {Passengers:[{id:\"minecraft:cow\",NoAI:1b}]}");
            // A powered line to the south, along x = 12: the cart keeps going at 8 blocks a second.
            // A piece at a time: a fill reaching past the chunks the server has loaded does
            // nothing, and the line ends where they do.
            context.waitTicks(100);
            for (int from = 2; from < 230; from += 32) {
                int to = Math.min(230, from + 31);
                Scene.command(world, "execute at @p run fill ~12 ~-1 ~" + from + " ~12 ~-1 ~" + to + " minecraft:redstone_block");
                Scene.command(world, "execute at @p run fill ~12 ~ ~" + from + " ~12 ~ ~" + to
                        + " minecraft:powered_rail[shape=north_south,powered=true]");
            }
            context.waitTicks(40);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "sent");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));

            Scene.command(world, "execute at @p run summon minecraft:minecart ~12 ~ ~4 {UUID:[I;0,0,0,42],Motion:[0.0,0.0,0.4],"
                    + "Passengers:[{id:\"minecraft:villager\",UUID:[I;0,0,0,44],NoAI:1b}]}");
            double apart = 0;
            for (int second = 5; second <= 40; second += 5) {
                context.waitTicks(100);
                double real = serverZ(world);
                int at = second;
                context.runOnClient(minecraft -> Scene.log("minecarts t=" + at + "s server z=" + Math.round(real)
                        + " copy z=" + copyZ(MOVING) + " passenger z=" + copyZ(PASSENGER)
                        + " sent=" + FarPlayerTracker.get().mobs().live().stream().anyMatch(e -> e.getUUID().equals(MOVING))
                        + " simulation=" + (minecraft.level == null ? -1 : minecraft.level.getServerSimulationDistance())));
                double gap = context.computeOnClient(minecraft -> Math.abs(copyZ(PASSENGER) - copyZ(MOVING)));
                if (!Double.isNaN(gap)) apart = Math.max(apart, gap);
            }
            double real = serverZ(world);
            double copy = context.computeOnClient(minecraft -> copyZ(MOVING));
            Scene.log("minecarts stopped: server z=" + real + " copy z=" + copy + ", passenger at most " + apart + " from it");
            if (Double.isNaN(copy) || Math.abs(copy - real) > 4) {
                throw new AssertionError("the copy stopped at z=" + copy + ", the server's cart at z=" + real);
            }
            if (apart > 0.5) throw new AssertionError("the villager's copy was " + apart + " blocks from its minecart's");
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.command(world, "tp @a 12.5 ~2 0 0 10");
            Scene.screenshot(context, this, "line");

            // The parked ones and the riders, left 200 blocks behind.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            String problem = context.computeOnClient(minecraft -> {
                Set<String> types = new HashSet<>();
                Entity villager = null, cart = null;
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    Entity puppet = mob.puppet();
                    Scene.log("minecarts remembered " + mob.type() + " at " + Math.round(mob.x()) + " " + Math.round(mob.z())
                            + (puppet == null ? " not built" : " drawn at " + puppet.position()));
                    if (puppet == null) continue;
                    types.add(mob.type());
                    if (mob.id().equals(SEATED)) villager = puppet;
                    if (mob.id().equals(CARRYING)) cart = puppet;
                }
                return check(types, villager, cart);
            });
            Scene.screenshot(context, this, "remembered");
            if (problem != null) throw new AssertionError(problem);
        }
    }

    private static @Nullable String check(Set<String> types, @Nullable Entity villager, @Nullable Entity cart) {
        for (String type : new String[] {"minecraft:villager", "minecraft:cow", "minecraft:oak_boat", "minecraft:minecart"}) {
            if (!types.contains(type)) return type + " was not remembered: " + types;
        }
        if (villager == null || cart == null) return "the villager or its minecart is missing";
        double apart = villager.position().subtract(cart.position()).horizontalDistance();
        return apart > 0.5 ? "the villager left its minecart: " + villager.position() + ", cart " + cart.position() : null;
    }

    /** Where the server's cart is along the line. */
    private static double serverZ(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            Entity cart = server.overworld().getEntity(MOVING);
            return cart == null ? Double.NaN : cart.getZ();
        });
    }

    private static double copyZ(UUID id) {
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            Entity puppet = mob.puppet();
            if (mob.id().equals(id) && puppet != null) return puppet.getZ();
        }
        return Double.NaN;
    }
}
