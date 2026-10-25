package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.Puppets;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mobs this client made itself, as Ambient Birds makes its birds, that go where it holds no
 * chunk: the game stops moving them there, and they would hang still for as long as the level
 * lasts. Their copy takes over, as for a mob the server stopped sending, and a flyer's copy
 * flies; once their chunk is back and the game moves them again, they are live again.
 *
 * <p>With Ambient Birds in {@code -Plh.mods} (Minecraft 1.21.2 to 1.21.10), its crows are left
 * behind as well, past the distance the server sends them at: their copies wheel over their
 * spot, wings out, as models and as impostors.
 */
public final class StrandedScenario implements Scenario {
    /** Ids of the mobs made here: far below the server's, as Ambient Birds numbers its own. */
    private static final int FIRST_ID = -300_000;

    @Override
    public String name() {
        return "stranded";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(8);
            minecraft.options.simulationDistance().set(5);
        });
        Scene.configure(context, config -> {
            config.mobTypes.add("minecraft:phantom");
            config.hideOccludedMobs = false;
            config.animationMinPixels = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Entity[] made = context.computeOnClient(minecraft -> {
                ClientLevel level = minecraft.level;
                Entity cow = make(level, EntityType.COW, FIRST_ID, 0, 0, 40);
                Entity phantom = make(level, EntityType.PHANTOM, FIRST_ID - 1, 12, 20, 40);
                return new Entity[]{cow, phantom};
            });
            context.waitTicks(20);
            context.runOnClient(minecraft -> {
                for (Entity entity : made) {
                    if (!FarPlayerTracker.get().mobs().live().contains(entity)) {
                        throw new AssertionError(entity.getType() + " made on the client is not taken for a live mob");
                    }
                }
            });

            // Off where this client holds no chunk, 25 chunks away: the game no longer moves them.
            int[] ticked = context.computeOnClient(minecraft -> {
                int[] counts = new int[made.length];
                for (int i = 0; i < made.length; i++) {
                    made[i].setPos(made[i].getX(), made[i].getY(), made[i].getZ() + 400);
                    counts[i] = made[i].tickCount;
                }
                return counts;
            });
            context.waitTicks(40);
            Map<UUID, double[]> last = new HashMap<>();
            double[] flown = new double[1];
            for (int tick = 0; tick < 200; tick++) {
                context.runOnClient(minecraft -> {
                    MobMemory memory = FarPlayerTracker.get().mobs();
                    for (Entity entity : made) {
                        if (memory.live().contains(entity)) {
                            throw new AssertionError(entity.getType() + " is still taken for a live mob where no chunk is held");
                        }
                    }
                    for (MobMemory.Remembered mob : memory.shown()) {
                        Entity puppet = mob.puppet();
                        if (puppet == null || !mob.id().equals(made[1].getUUID())) continue;
                        double[] before = last.put(mob.id(), new double[]{puppet.getX(), puppet.getY(), puppet.getZ()});
                        if (before != null) flown[0] += puppet.position().distanceTo(new Vec3(before[0], before[1], before[2]));
                    }
                });
                context.waitTicks(1);
            }
            context.runOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                for (int i = 0; i < made.length; i++) {
                    Entity entity = made[i];
                    boolean copied = memory.shown().stream().anyMatch(mob -> mob.id().equals(entity.getUUID()) && mob.puppet() != null);
                    Scene.log("stranded " + entity.getType() + " ticked " + (entity.tickCount - ticked[i])
                            + " times far away, copy drawn " + copied);
                    if (!copied) throw new AssertionError("No copy stands in for the stranded " + entity.getType());
                }
            });
            Scene.log("stranded phantom copy flew " + String.format("%.1f", flown[0]) + " blocks");
            if (flown[0] < 30) throw new AssertionError("The stranded phantom's copy flew " + flown[0] + " blocks only");

            // Back where the chunks are held: the game moves them again, and they are live again.
            context.runOnClient(minecraft -> {
                for (Entity entity : made) entity.setPos(entity.getX(), entity.getY(), entity.getZ() - 400);
            });
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                for (Entity entity : made) {
                    if (!memory.live().contains(entity)) {
                        throw new AssertionError(entity.getType() + " back among the held chunks is not live again");
                    }
                    if (memory.shown().stream().anyMatch(mob -> mob.id().equals(entity.getUUID()) && !memory.handingOver(mob))) {
                        throw new AssertionError("The copy of " + entity.getType() + " is still drawn beside it");
                    }
                }
                for (Entity entity : made) minecraft.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
            });

            if (Platform.isModLoaded("ambientbirds")) ambientBirds(context, world);
        }
    }

    /** A mob made on the client alone, {@code dx}, {@code dy}, {@code dz} blocks from the player. */
    private static Entity make(ClientLevel level, EntityType<?> type, int id, double dx, double dy, double dz) {
        Entity entity = Puppets.create(type, level);
        if (entity == null) throw new AssertionError("Could not make a " + type);
        entity.setId(id);
        LocalPlayer player = Minecraft.getInstance().player;
        entity.setPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
        level.addEntity(entity);
        return entity;
    }

    /**
     * Ambient Birds' crows, summoned high up and left 140 blocks behind, past the 80 blocks the
     * server sends them at: their copies wheel over their spot, drawn flying.
     */
    private void ambientBirds(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(16);
            minecraft.options.entityDistanceScaling().set(0.5);
        });
        Scene.configure(context, config -> {
            if (!config.mobTypes.contains("ambientbirds:crow")) config.mobTypes.add("ambientbirds:crow");
            config.impostors = false;
            config.minApparentPixels = 12;
        });
        for (int i = 0; i < 4; i++) {
            Scene.command(world, "execute at @p run summon ambientbirds:crow ~" + (i - 1.5) * 6 + " ~30 ~40"
                    + " {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        }
        context.waitTicks(100);
        Scene.command(world, "tp @a 0 ~ -100 0 -20");
        context.waitTicks(200);
        Map<UUID, double[]> last = new HashMap<>();
        double[] flown = new double[1];
        int[] flying = new int[2];
        for (int tick = 0; tick < 200; tick++) {
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    Entity puppet = mob.puppet();
                    if (puppet == null || !mob.type().equals("ambientbirds:crow")) continue;
                    double[] before = last.put(mob.id(), new double[]{puppet.getX(), puppet.getY(), puppet.getZ()});
                    if (before != null) flown[0] += puppet.position().distanceTo(new Vec3(before[0], before[1], before[2]));
                }
            });
            context.waitTicks(1);
        }
        context.runOnClient(minecraft -> {
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                Entity puppet = mob.puppet();
                if (puppet == null || !mob.type().equals("ambientbirds:crow")) continue;
                flying[1]++;
                try {
                    if ("fly".equals(puppet.getClass().getMethod("currentAnimation").invoke(puppet))) flying[0]++;
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Ambient Birds' bird has no currentAnimation", error);
                }
            }
        });
        Scene.log("stranded ambientbirds crows " + flying[1] + " copies, " + flying[0] + " drawn flying, flew "
                + String.format("%.1f", flown[0]) + " blocks");
        if (flying[1] < 4 || flying[0] < flying[1]) throw new AssertionError("Ambient Birds' crows are not drawn flying");
        if (flown[0] < 4 * 20) throw new AssertionError("Ambient Birds' crows flew " + flown[0] + " blocks only");
        context.runOnClient(minecraft -> minecraft.options.fov().set(30));
        Scene.screenshot(context, this, "ambientbirds-models");
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
        });
        context.waitTicks(80);
        Scene.screenshot(context, this, "ambientbirds-impostors");
        Scene.screenshotAtlas(context, this);
    }
}
