package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Sect;
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
 * Easter egg 9 at midnight on the flat ground: the circle round its fire, seen from 72 blocks
 * (it goes under 60); then looked at through a spyglass, held up with the use key (or, where
 * the test cannot hold one up, {@link Sect#standIn}), they all turn to the player; the spyglass
 * lowered, they walk towards the player and fade before they reach them; the player at the
 * fire, it is out.
 */
public final class Egg9 {
    private Egg9() {
    }

    private static Sect sect() {
        return FarPlayerTracker.get().ambience().sect();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 9 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set midnight");
        context.waitTicks(20);
        // Running this time: the walkers are checked by the second round below.
        context.runOnClient(minecraft -> sect().standInRunning(true));
        boolean on = context.computeOnClient(minecraft -> sect().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee9 did not start");
        Vec3 fire = await(context);
        // Nearer, on the line from the fire to the player, still farther than where it goes.
        Vec3 spot = context.computeOnClient(minecraft -> {
            LocalPlayer player = minecraft.player;
            double across = Math.hypot(fire.x - player.getX(), fire.z - player.getZ());
            Scene.log("eggs2 ee9 at " + fire + ", " + (int) across + " blocks");
            if (across < 80) throw new AssertionError("ee9 at " + (int) across + " blocks, too near");
            double dx = (player.getX() - fire.x) / across, dz = (player.getZ() - fire.z) / across;
            return new Vec3(fire.x + dx * 72, player.getY(), fire.z + dz * 72);
        });
        Scene.command(world, "tp @a " + spot.x + " " + spot.y + " " + spot.z);
        context.waitTicks(30);
        context.runOnClient(minecraft -> {
            if (sect().position() == null) throw new AssertionError("ee9 gone at 72 blocks");
            lookAt(minecraft.player, fire.x, fire.y + 1.0, fire.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(20);
        // Players in their robes, no armour on.
        context.runOnClient(minecraft -> {
            int players = 0;
            for (net.minecraft.world.entity.Entity figure : sect().figures()) {
                if (!(figure instanceof net.minecraft.client.player.RemotePlayer person)) continue;
                players++;
                if (!person.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) {
                    throw new AssertionError("ee9's figure wears " + person.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
                }
            }
            if (players < 6) throw new AssertionError("ee9 has " + players + " players round the fire");
        });
        Scene.screenshot(context, scenario, "ee9-circle");
        if (context.computeOnClient(minecraft -> sect().noticed())) throw new AssertionError("ee9 noticed without a spyglass");

        // The spyglass, held up on them.
        Scene.command(world, "item replace entity @p weapon.mainhand with minecraft:spyglass");
        context.waitTicks(5);
        context.runOnClient(minecraft -> {
            minecraft.options.fov().set(70);
            lookAt(minecraft.player, fire.x, fire.y + 1.2, fire.z);
        });
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(10);
        boolean real = context.computeOnClient(minecraft -> minecraft.player.isScoping());
        if (!real) {
            // The use key held did not raise it here: the test stands in for the spyglass.
            context.runOnClient(minecraft -> sect().standIn(true));
        }
        Scene.log("eggs2 ee9 spyglass " + (real ? "held up" : "stood in for"));
        context.waitTicks(150);
        float away = context.computeOnClient(minecraft -> {
            lookAt(minecraft.player, fire.x, fire.y + 1.2, fire.z);
            return sect().turnedAway(minecraft.player.position());
        });
        Scene.log("eggs2 ee9 noticed " + context.computeOnClient(minecraft -> sect().noticed()) + ", turned away " + away);
        if (!context.computeOnClient(minecraft -> sect().noticed())) throw new AssertionError("ee9 not noticed through the spyglass");
        if (away > 15f) throw new AssertionError("ee9 not all turned to the player: " + away + " degrees off");
        Scene.screenshot(context, scenario, "ee9-noticed");

        // The spyglass lowered: they come.
        context.getInput().releaseKey(options -> options.keyUse);
        context.runOnClient(minecraft -> sect().standIn(null));
        context.waitTicks(5);
        if (context.computeOnClient(minecraft -> minecraft.player.isScoping())) throw new AssertionError("ee9 spyglass still up");
        double before = context.computeOnClient(minecraft -> sect().nearest(minecraft.player.position()));
        context.waitTicks(80);
        double after = context.computeOnClient(minecraft -> {
            lookAt(minecraft.player, fire.x, fire.y + 1.2, fire.z);
            minecraft.options.fov().set(30);
            return sect().nearest(minecraft.player.position());
        });
        Scene.log("eggs2 ee9 nearest " + (int) before + " then " + (int) after + " blocks");
        // Running, they cover more than ten blocks in those four seconds, past the time they take to stand.
        if (!(after < before - 10)) throw new AssertionError("ee9 not running in: " + before + " then " + after);
        Scene.screenshot(context, scenario, "ee9-scatter");
        // Each is gone as it comes near the player.
        int left = -1;
        for (int i = 0; i < 40 && left != 0; i++) {
            context.waitTicks(20);
            left = context.computeOnClient(minecraft -> sect().left());
        }
        if (left != 0) throw new AssertionError("ee9 still " + left + " walking after 40 seconds");
        double closest = context.computeOnClient(minecraft -> minecraft.player.position().distanceTo(fire));
        Scene.log("eggs2 ee9 all gone, the fire " + (int) closest + " blocks off");
        if (!context.computeOnClient(minecraft -> sect().busy())) throw new AssertionError("ee9 fire out too soon");

        // Again, walking: hidden behind stone once they have come a while, they are gone.
        context.runOnClient(minecraft -> {
            sect().standInRunning(false);
            sect().standIn(true);
        });
        Scene.command(world, "tp @a " + spot.x + " " + spot.y + " " + spot.z);
        on = context.computeOnClient(minecraft -> sect().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee9 did not start again");
        Vec3 again = await(context);
        context.runOnClient(minecraft -> lookAt(minecraft.player, again.x, again.y + 1.2, again.z));
        context.waitTicks(40);
        if (context.computeOnClient(minecraft -> sect().running())) throw new AssertionError("ee9 runs when told to walk");
        context.runOnClient(minecraft -> sect().standIn(false));
        context.waitTicks(180);
        int walkers = context.computeOnClient(minecraft -> sect().left());
        if (walkers == 0) throw new AssertionError("ee9 walkers gone before the wall");
        // A hollow box of stone round the player: whichever way they come, they are behind it.
        Scene.command(world, "execute at @p run fill ~-3 ~-1 ~-3 ~3 ~4 ~3 minecraft:stone hollow");
        context.waitTicks(30);
        int behind = context.computeOnClient(minecraft -> sect().left());
        Scene.log("eggs2 ee9 walking: " + walkers + " coming, " + behind + " left once behind a wall");
        if (behind != 0) throw new AssertionError("ee9 still " + behind + " walking behind a wall");
        Scene.command(world, "execute at @p run fill ~-3 ~ ~-3 ~3 ~4 ~3 minecraft:air");
        context.runOnClient(minecraft -> {
            sect().standIn(null);
            sect().standInRunning(null);
        });

        // The player at the fire: it is out.
        Scene.command(world, "tp @a " + (again.x + 6) + " " + again.y + " " + (again.z - 6));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> sect().busy())) throw new AssertionError("ee9 still there with the player next to it");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "item replace entity @p weapon.mainhand with minecraft:air");
    }

    /** Where it came, once it has: a few seconds at most, on the ground the game draws. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> sect().position());
            if (at != null) {
                context.runOnClient(minecraft -> {
                    double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
                    double edge = minecraft.options.getEffectiveRenderDistance() * 16;
                    if (across > edge - 16) throw new AssertionError("ee9 at " + (int) across + " blocks, past the drawn " + (int) edge);
                    int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
                    if (Math.abs(at.y - ground) > 1) throw new AssertionError("ee9 at height " + at.y + ", the ground at " + ground);
                });
                return at;
            }
            context.waitTick();
        }
        throw new AssertionError("ee9 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
