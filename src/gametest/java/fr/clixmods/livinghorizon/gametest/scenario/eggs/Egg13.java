package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.SkyLanterns;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 13 at midnight, from a village made 90 blocks ahead (a bell by a street): the
 * lanterns leave one after the other and rise, seen low and then high; they are all gone by
 * themselves within two minutes.
 */
public final class Egg13 {
    private Egg13() {
    }

    private static SkyLanterns lanterns() {
        return FarPlayerTracker.get().ambience().skyLanterns();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 13 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "execute at @p run fill ~-20 ~-1 ~75 ~20 ~-1 ~105 minecraft:dirt_path");
        Scene.command(world, "execute at @p run setblock ~ ~ ~90 minecraft:bell");
        // A second village, off to the side.
        Scene.command(world, "execute at @p run fill ~-140 ~-1 ~55 ~-110 ~-1 ~85 minecraft:dirt_path");
        Scene.command(world, "execute at @p run setblock ~-125 ~ ~70 minecraft:bell");
        Scene.command(world, "time set midnight");
        context.waitTicks(20);
        // Every village in sight, first: both let lanterns go.
        context.runOnClient(minecraft -> lanterns().standInEvery(true));
        boolean both = context.computeOnClient(minecraft -> lanterns().summon(minecraft.level, minecraft.player, true));
        if (!both) throw new AssertionError("ee13 found no village");
        await(context);
        int up = context.computeOnClient(minecraft -> lanterns().villages());
        Scene.log("eggs2 ee13 from every village: " + up);
        if (up != 2) throw new AssertionError("ee13 rose from " + up + " villages, not both");
        context.runOnClient(minecraft -> lookAt(minecraft.player, minecraft.player.getX() - 60, -45, minecraft.player.getZ() + 80));
        context.waitTicks(250);
        Scene.screenshot(context, scenario, "ee13-every");
        // Then one village only.
        context.runOnClient(minecraft -> lanterns().standInEvery(false));
        boolean on = context.computeOnClient(minecraft -> lanterns().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee13 found no village");
        Vec3 village = await(context);
        int one = context.computeOnClient(minecraft -> lanterns().villages());
        if (one != 1) throw new AssertionError("ee13 rose from " + one + " villages, not one");
        context.runOnClient(minecraft -> {
            lanterns().standInEvery(null);
            double across = Math.hypot(village.x - minecraft.player.getX(), village.z - minecraft.player.getZ());
            Scene.log("eggs2 ee13 at " + village + ", " + (int) across + " blocks");
            if (across < 80) throw new AssertionError("ee13 at " + (int) across + " blocks, too near");
            lookAt(minecraft.player, village.x, village.y + 15, village.z);
            minecraft.options.fov().set(30);
        });
        // Some up already, more leaving.
        context.waitTicks(250);
        int first = context.computeOnClient(minecraft -> lanterns().aloft());
        double low = context.computeOnClient(minecraft -> lanterns().rise());
        Scene.log("eggs2 ee13 " + first + " aloft, " + (int) low + " blocks up on average");
        if (first < 5) throw new AssertionError("ee13 only " + first + " lanterns aloft after 12 seconds");
        Scene.screenshot(context, scenario, "ee13-leaving");
        // All gone up, higher.
        context.waitTicks(500);
        int second = context.computeOnClient(minecraft -> lanterns().aloft());
        double high = context.computeOnClient(minecraft -> lanterns().rise());
        Scene.log("eggs2 ee13 " + second + " aloft, " + (int) high + " blocks up on average");
        if (!(high > low + 20)) throw new AssertionError("ee13 not rising: " + low + " then " + high + " blocks up");
        if (second < first) throw new AssertionError("ee13 fewer aloft after all had left: " + first + " then " + second);
        context.runOnClient(minecraft -> lookAt(minecraft.player, village.x, village.y + high, village.z));
        Scene.screenshot(context, scenario, "ee13-high");
        // Faded out high up, by themselves.
        boolean busy = true;
        for (int i = 0; i < 30 && busy; i++) {
            context.waitTicks(100);
            busy = context.computeOnClient(minecraft -> lanterns().busy());
        }
        if (busy) throw new AssertionError("ee13 still there after two minutes and a half");
        Scene.log("eggs2 ee13 all gone");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** Where they leave from, once it is found: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> lanterns().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee13 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
