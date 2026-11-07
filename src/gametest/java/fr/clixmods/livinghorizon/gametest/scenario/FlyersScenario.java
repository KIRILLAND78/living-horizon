package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembered phantoms and ghasts fly: a phantom circles above where it was last seen, a ghast
 * drifts about it, neither ever jumping. They are summoned high in the air, then left 140 blocks
 * behind, past the distance the server sends them at; how far and how high each copy goes is
 * measured over a few hundred ticks. As impostors, each is pictured from every height it may
 * be seen from, and the pictures are looked at from below.
 */
public final class FlyersScenario implements Scenario {
    private static final String[] MOBS = {"phantom", "phantom{Size:4}", "ghast", "phantom"};

    @Override
    public String name() {
        return "flyers";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.mobTypes.add("minecraft:phantom");
            config.mobTypes.add("minecraft:ghast");
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.animationMinPixels = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "difficulty easy");
            // Phantoms burn by day.
            Scene.command(world, "time set midnight");
            for (int i = 0; i < MOBS.length; i++) {
                String type = MOBS[i].replaceAll("\\{.*", "");
                String nbt = MOBS[i].contains("{") ? MOBS[i].substring(MOBS[i].indexOf('{') + 1, MOBS[i].length() - 1) + "," : "";
                Scene.command(world, "execute at @p run summon " + type + " ~" + (i - 1.5) * 12 + " ~50 ~40 {" + nbt
                        + "NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            }
            context.waitTicks(100);
            // Past the 80 blocks these are sent at, and outside the five chunks the server moves.
            Scene.command(world, "tp @a 0 ~ -100 0 -20");
            context.waitTicks(200);

            Map<String, Double> travelled = new HashMap<>(), highest = new HashMap<>(), lowest = new HashMap<>(),
                    jump = new HashMap<>();
            Map<Entity, double[]> last = new HashMap<>();
            for (int tick = 0; tick < 400; tick++) {
                context.runOnClient(minecraft -> {
                    for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                        Entity puppet = mob.puppet();
                        if (puppet == null) continue;
                        double[] before = last.put(puppet, new double[]{puppet.getX(), puppet.getY(), puppet.getZ()});
                        double rise = puppet.getY() - mob.y();
                        highest.merge(mob.type(), rise, Math::max);
                        lowest.merge(mob.type(), rise, Math::min);
                        if (before == null) continue;
                        double step = Math.sqrt(Math.pow(puppet.getX() - before[0], 2)
                                + Math.pow(puppet.getY() - before[1], 2) + Math.pow(puppet.getZ() - before[2], 2));
                        travelled.merge(mob.type(), step, Double::sum);
                        jump.merge(mob.type(), step, Math::max);
                    }
                });
                context.waitTicks(1);
            }
            for (String type : travelled.keySet()) {
                Scene.log("flyers " + type + " travelled " + String.format("%.1f", travelled.get(type))
                        + " rise " + String.format("%.1f..%.1f", lowest.get(type), highest.get(type))
                        + " largest step " + String.format("%.2f", jump.get(type)));
            }
            // Four phantoms' worth of circling, three of them: well over a hundred blocks each.
            check(travelled, "minecraft:phantom", 300, 3000, "travelled");
            check(travelled, "minecraft:ghast", 10, 200, "travelled");
            check(jump, "minecraft:phantom", 0, 1.25, "stepped");
            check(jump, "minecraft:ghast", 0, 1.25, "stepped");
            check(highest, "minecraft:phantom", 2, 9, "rose");

            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "models");
            Scene.configure(context, config -> {
                config.impostors = true;
                config.impostorDistance = 32;
                config.impostorPixels = 0;
            });
            context.waitTicks(80);
            int[] heights = new int[1];
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    Entity puppet = mob.puppet();
                    if (puppet == null) continue;
                    ImpostorKey key = ImpostorKey.of(puppet);
                    for (int height = 0; height < ImpostorViews.HEIGHTS.length; height++) {
                        if (ImpostorAtlas.sheet(ImpostorKey.seenFrom(key, height)) != null) heights[0]++;
                    }
                }
            });
            Scene.log("flyers heights baked " + heights[0]);
            if (heights[0] < 4 * 4) throw new AssertionError("Only " + heights[0] + " sheets of flyers seen from a height");
            for (int shot = 0; shot < 4; shot++) {
                Scene.screenshot(context, this, "impostors-" + shot);
                context.waitTicks(10);
            }
            Scene.screenshotAtlas(context, this);
        }
    }

    private static void check(Map<String, Double> measured, String type, double least, double most, String what) {
        Double value = measured.get(type);
        if (value == null) throw new AssertionError("No copy of " + type + " was drawn");
        if (value < least || value > most) {
            throw new AssertionError(type + " " + what + " " + value + " blocks, expected " + least + " to " + most);
        }
    }
}
