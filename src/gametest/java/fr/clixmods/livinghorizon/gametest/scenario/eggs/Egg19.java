package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Campfire;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 19 at midnight: a mound of stone five blocks high built 120 blocks ahead on the
 * flat ground, where the fire must burn, the highest spot in sight. Seen from where the player
 * stands and from 60 blocks (it is out under 48); the player beside it, it is out and its ash
 * is left; the easter eggs turned off, the ash is gone too.
 */
public final class Egg19 {
    /** Blocks above the flat ground the mound's top stands. */
    private static final int MOUND = 5;

    private Egg19() {
    }

    private static Campfire campfire() {
        return FarPlayerTracker.get().ambience().campfire();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 19 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        // A stepped mound ahead, its top wide enough for the spots tried to find it.
        Scene.command(world, "execute at @p run fill ~-24 ~ ~100 ~24 ~2 ~140 minecraft:stone");
        Scene.command(world, "execute at @p run fill ~-16 ~3 ~108 ~16 ~" + (MOUND - 1) + " ~132 minecraft:stone");
        Scene.command(world, "time set midnight");
        context.waitTicks(40);
        double feet = context.computeOnClient(minecraft -> minecraft.player.getY());
        boolean on = context.computeOnClient(minecraft -> campfire().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee19 did not start");
        Vec3 fire = await(context);
        context.runOnClient(minecraft -> {
            double across = Math.hypot(fire.x - minecraft.player.getX(), fire.z - minecraft.player.getZ());
            Scene.log("eggs2 ee19 at " + fire + ", " + (int) across + " blocks, " + (fire.y - feet) + " above the player");
            lookAt(minecraft.player, fire.x, fire.y + 1.5, fire.z);
            minecraft.options.fov().set(30);
        });
        // The highest in sight: on the mound, never on the flat ground round it.
        if (fire.y < feet + 2) throw new AssertionError("ee19 at height " + fire.y + ", not on the mound");
        if (!context.computeOnClient(minecraft -> campfire().burning())) throw new AssertionError("ee19 not burning");
        context.waitTicks(20);
        Scene.screenshot(context, scenario, "ee19-fire");

        // Nearer, still farther than where it is out.
        Vec3 spot = context.computeOnClient(minecraft -> {
            LocalPlayer player = minecraft.player;
            double across = Math.hypot(fire.x - player.getX(), fire.z - player.getZ());
            double dx = (player.getX() - fire.x) / across, dz = (player.getZ() - fire.z) / across;
            return new Vec3(fire.x + dx * 60, player.getY(), fire.z + dz * 60);
        });
        Scene.command(world, "tp @a " + spot.x + " " + spot.y + " " + spot.z);
        context.waitTicks(10);
        context.runOnClient(minecraft -> {
            if (!campfire().burning()) throw new AssertionError("ee19 out at 60 blocks");
            lookAt(minecraft.player, fire.x, fire.y + 2.5, fire.z);
        });
        context.waitTicks(20);
        Scene.screenshot(context, scenario, "ee19-close");

        // Beside it: out, its ash left.
        Scene.command(world, "tp @a " + fire.x + " " + fire.y + " " + (fire.z - 4));
        context.waitTicks(10);
        context.runOnClient(minecraft -> {
            if (campfire().burning()) throw new AssertionError("ee19 still burning with the player next to it");
            if (!campfire().ash()) throw new AssertionError("ee19 left no ash");
            lookAt(minecraft.player, fire.x, fire.y, fire.z);
            minecraft.options.fov().set(70);
        });
        Scene.screenshot(context, scenario, "ee19-ash");
        // The easter eggs off: the ash is gone with them.
        Scene.configure(context, config -> config.easterEggs = false);
        context.waitTicks(2);
        Scene.configure(context, config -> config.easterEggs = true);
        if (context.computeOnClient(minecraft -> campfire().position()) != null) throw new AssertionError("ee19 ash still there");
    }

    /** Where it came, once it has: a few seconds at most, on the ground the game draws. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> campfire().position());
            if (at != null) {
                context.runOnClient(minecraft -> {
                    double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
                    double edge = minecraft.options.getEffectiveRenderDistance() * 16;
                    if (across > edge - 16) throw new AssertionError("ee19 at " + (int) across + " blocks, past the drawn " + (int) edge);
                    int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
                    if (Math.abs(at.y - ground) > 1) throw new AssertionError("ee19 at height " + at.y + ", the ground at " + ground);
                });
                return at;
            }
            context.waitTick();
        }
        throw new AssertionError("ee19 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
