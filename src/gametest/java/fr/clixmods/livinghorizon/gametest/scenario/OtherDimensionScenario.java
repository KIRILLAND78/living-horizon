package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Mobs of the Overworld are never drawn in the Nether (issue #343: squids flying in the Nether's
 * air). Some are remembered long before the change, some are still loaded as the player goes
 * through and unload with the Overworld. Every tick in the Nether, no copy drawn may be of
 * another dimension. Forge and NeoForge never unload the old level's mobs: one is left among the
 * live mobs, as they leave it, and must be let go. Back in the Overworld, the copies come back.
 */
public final class OtherDimensionScenario implements Scenario {
    private static final String[] MOBS = {"cow", "squid", "sheep", "squid"};

    @Override
    public String name() {
        return "otherdimension";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(12);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            // Not shown by default: the player who saw them in the Nether had turned them on.
            config.mobTypes.add("minecraft:squid");
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "gamemode creative @a");
            // Remembered long before the change: left 200 blocks behind.
            Scene.row(world, MOBS, 2, 40, 5.0);
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            // Still loaded as the player goes through: they unload with the Overworld.
            Scene.row(world, MOBS, 2, 40, 5.0);
            context.waitTicks(100);
            AtomicReference<Entity> squid = new AtomicReference<>();
            int before = context.computeOnClient(minecraft -> {
                for (Entity entity : FarPlayerTracker.get().mobs().live()) {
                    if (entity.getType() == EntityType.SQUID) squid.set(entity);
                }
                return FarPlayerTracker.get().mobs().shown(minecraft.level).size();
            });
            Scene.log("otherdimension overworld shown=" + before + " remembered=" + FarPlayerTracker.get().mobs().size());
            if (before == 0) throw new AssertionError("No copy shown in the Overworld: nothing to check in the Nether");
            if (squid.get() == null) throw new AssertionError("No live squid in the Overworld");

            // On the Nether's roof, above where the mobs were remembered in the Overworld.
            Scene.command(world, "execute in minecraft:the_nether run tp @a 0 128 -160 0 0");
            int worst = 0, ticks = 0;
            for (int tick = 0; tick < 200; tick++) {
                context.waitTicks(1);
                int wrong = context.computeOnClient(minecraft -> {
                    if (minecraft.level == null) return 0;
                    String here = minecraft.level.dimension().identifier().toString();
                    int count = 0;
                    for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown(minecraft.level)) {
                        Entity puppet = mob.puppet();
                        if (!here.equals(mob.dimension()) || (puppet != null && puppet.level() != minecraft.level)) {
                            if (count == 0) Scene.log("otherdimension in " + here + ": " + mob.type()
                                    + " of " + mob.dimension() + " shown at " + mob.x() + " " + mob.y() + " " + mob.z());
                            count++;
                        }
                    }
                    return count;
                });
                if (wrong > 0) ticks++;
                worst = Math.max(worst, wrong);
                // Left among the live mobs, as Forge and NeoForge leave the old level's.
                if (tick == 40) context.runOnClient(minecraft -> live().put(squid.get().getUUID(), squid.get()));
            }
            boolean kept = context.computeOnClient(minecraft -> live().containsValue(squid.get()));
            Scene.screenshot(context, this, "nether");
            Scene.log("otherdimension nether: at most " + worst + " copies of another dimension, on " + ticks
                    + " ticks; an Overworld squid left live " + (kept ? "kept" : "let go"));

            Scene.command(world, "execute in minecraft:overworld run tp @a 0 -60 -160 0 0");
            context.waitTicks(200);
            int after = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown(minecraft.level).size());
            Scene.log("otherdimension back in the overworld shown=" + after);
            if (worst > 0) throw new AssertionError(worst + " Overworld copies drawn in the Nether, on " + ticks + " ticks");
            if (kept) throw new AssertionError("A live Overworld squid is still among the mobs drawn in the Nether");
            if (after == 0) throw new AssertionError("The Overworld's copies did not come back");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Entity> live() {
        try {
            Field live = MobMemory.class.getDeclaredField("live");
            live.setAccessible(true);
            return (Map<UUID, Entity>) live.get(FarPlayerTracker.get().mobs());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
