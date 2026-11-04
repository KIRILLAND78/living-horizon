package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Shippy Ships' ships left on a lake ({@code run.sh ships --with shippy-ships --with ingeniumapi}):
 * once the server no longer sends them, each one has a copy where it was, the galleon, its
 * largest, included. The galleon writes its hold its own way, slot by slot out of a list the
 * client holds smaller: saving it on the client failed, and it was never remembered.
 *
 * <p>Each one is pictured too, with Simplified Entities. Ships are pictured round the middle of
 * their box, low in the hull: the galleon's masts reach eight times higher, and with half of its
 * pictures empty below it, it was cut off even at their largest and stayed a model.
 * Without Shippy Ships, nothing is checked.
 */
public final class ShipsScenario implements Scenario {
    private static final String[] SHIPS = {
            "shippy-ships:oak_galleon", "shippy-ships:oak_caravel", "shippy-ships:oak_cog", "shippy-ships:oak_barge"};

    @Override
    public String name() {
        return "ships";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("shippy-ships")) {
            Scene.log("ships: Shippy Ships not loaded, nothing checked (run with --with shippy-ships --with ingeniumapi)");
            return;
        }
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A lake ahead, past 32 blocks: a ship that vanishes closer is taken as gone for good.
            Scene.command(world, "fill -40 -63 40 40 -61 150 minecraft:water");
            Scene.summon(world, SHIPS[0], 0, 110, 90);
            Scene.summon(world, SHIPS[1], -22, 70, 90);
            Scene.summon(world, SHIPS[2], 22, 70, 90);
            Scene.summon(world, SHIPS[3], 0, 55, 90);
            context.waitTicks(100);
            // What the mod keeps of each as it leaves: the game's own save, on the client.
            String saved = context.computeOnClient(minecraft -> {
                StringBuilder wrong = new StringBuilder();
                for (String id : SHIPS) {
                    Entity ship = live(id);
                    if (ship == null) {
                        wrong.append(' ').append(id).append(" is not sent by the server;");
                        continue;
                    }
                    try {
                        int size = EntityNbt.save(ship, minecraft.level).size();
                        Scene.log("ships: " + id + " saved on the client, " + size + " keys");
                    } catch (RuntimeException e) {
                        Scene.log("ships: " + id + " not saved on the client: " + e);
                    }
                }
                return wrong.toString();
            });
            if (!saved.isEmpty()) throw new AssertionError("ships:" + saved);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "near");

            // Walk away: the copies take over once the server no longer sends the ships.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            String built = context.computeOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                StringBuilder missing = new StringBuilder();
                for (String id : SHIPS) {
                    MobMemory.Remembered copy = memory.shown().stream().filter(mob -> mob.type().equals(id)).findFirst().orElse(null);
                    Entity puppet = copy == null ? null : copy.puppet();
                    Scene.log("ships: " + id + ": sent by the server " + (live(id) != null) + ", copy "
                            + (puppet == null ? "none" : "at " + puppet.position()));
                    if (puppet == null) missing.append(' ').append(id);
                }
                return missing.toString();
            });
            Scene.screenshot(context, this, "remembered");
            // Their pictures, baked from where the copies are seen: one sheet at least per ship.
            String pictured = context.computeOnClient(minecraft -> {
                StringBuilder missing = new StringBuilder();
                for (String id : SHIPS) {
                    List<ImpostorAtlas.Baked> sheets = ImpostorAtlas.baked().stream()
                            .filter(baked -> baked.key().type().equals(id)).toList();
                    for (ImpostorAtlas.Baked baked : sheets) {
                        Scene.log("ships: " + id + " pictured " + Math.round(baked.sheet().worldSize()) + " blocks across, its middle "
                                + Math.round(baked.sheet().feet() * 100) + "% down");
                    }
                    if (sheets.isEmpty()) missing.append(' ').append(id);
                }
                return missing.toString();
            });
            Scene.screenshotAtlas(context, this);
            Scene.configure(context, config -> config.impostorEntities = false);
            Scene.screenshot(context, this, "remembered-models");
            if (!built.isEmpty()) throw new AssertionError("ships: no copy of" + built);
            if (!pictured.isEmpty()) throw new AssertionError("ships: no pictures of" + pictured);
        }
    }

    /** The ship of this kind the server sends, or null. */
    private static Entity live(String id) {
        for (Entity entity : FarPlayerTracker.get().mobs().live()) {
            if (EntityType.getKey(entity.getType()).toString().equals(id)) return entity;
        }
        return null;
    }
}
