package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Herd;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Easter egg 15: four cows met up close, then left 150 blocks behind, so that they are
 * remembered far figures. Called while the player looks away, every one turns its head to
 * them within a second and a half; looked at, they are let go; called again and not looked
 * at, they are let go once the time is up.
 */
public final class Egg15 {
    /** Degrees a head may be off the player once turned. */
    private static final float OFF = 5f;

    private Egg15() {
    }

    private static Herd egg() {
        return FarPlayerTracker.get().ambience().herd();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 15 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        // A herd 40 blocks ahead, most of them facing away from the player.
        Scene.row(world, new String[]{"cow"}, 4, 40, 4.0);
        context.waitTicks(100);
        // Left behind: remembered and drawn far away.
        Scene.command(world, "tp @a " + 15 * 1000 + " -60 -110 0 0");
        context.waitTicks(300);
        context.runOnClient(minecraft -> {
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                Entity puppet = mob.puppet();
                Scene.log("eggs2 ee15 remembered " + mob.type() + " at " + (puppet == null ? "nowhere" : puppet.position()));
            }
        });

        // Called with the player looking away: they turn all the same.
        Vec3 at = call(context);
        Scene.log("eggs2 ee15 at " + at);
        context.waitTicks(35);
        facing(context, "turned");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, at.x, at.y + 1, at.z);
            minecraft.options.fov().set(30);
        });
        Scene.screenshot(context, scenario, "ee15-herd");
        // Seen with the naked eye: they stare on ten seconds, then go back to their lives.
        context.waitTicks(150);
        if (!context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee15 let go before ten seconds");
        boolean gone = false;
        for (int i = 0; i < 120 && !gone; i++) {
            context.waitTick();
            gone = !context.computeOnClient(minecraft -> egg().busy());
        }
        if (!gone) throw new AssertionError("ee15 still held long after ten seconds");
        Scene.screenshot(context, scenario, "ee15-released");

        // Seen through a spyglass, then the spyglass lowered: let go at once.
        call(context);
        context.waitTicks(30);
        facing(context, "turned again");
        context.runOnClient(minecraft -> {
            egg().standIn(true);
            lookAt(minecraft.player, at.x, at.y + 1, at.z);
        });
        context.waitTicks(20);
        if (!context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee15 let go while still looked at");
        context.runOnClient(minecraft -> egg().standIn(false));
        context.waitTicks(3);
        if (context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee15 still held with the spyglass lowered");
        if (!context.computeOnClient(minecraft -> egg().held().isEmpty())) throw new AssertionError("ee15 left copies held");
        context.runOnClient(minecraft -> {
            egg().standIn(null);
            minecraft.options.fov().set(70);
        });
    }

    /** Called, the player turned away from the herd (north): where it is, once found. */
    private static Vec3 call(ClientGameTestContext context) {
        boolean on = context.computeOnClient(minecraft -> {
            minecraft.player.setYRot(180f);
            minecraft.player.setXRot(0f);
            return egg().summon(minecraft.level, minecraft.player, true);
        });
        if (!on) throw new AssertionError("ee15 found no herd");
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee15 found no herd in sight");
    }

    /** Every copy held has its head turned to the player, three of them at least. */
    private static void facing(ClientGameTestContext context, String when) {
        context.runOnClient(minecraft -> {
            LocalPlayer self = minecraft.player;
            List<LivingEntity> held = egg().held();
            if (held.size() < 3) throw new AssertionError("ee15 holds " + held.size() + " copies, not a herd");
            for (LivingEntity puppet : held) {
                float to = (float) (Mth.atan2(self.getZ() - puppet.getZ(), self.getX() - puppet.getX()) * Mth.RAD_TO_DEG) - 90f;
                float off = Math.abs(Mth.wrapDegrees(puppet.yHeadRot - to));
                Scene.log("eggs2 ee15 " + when + ": head " + (int) puppet.yHeadRot + ", body " + (int) puppet.yBodyRot
                        + ", to the player " + (int) to);
                if (off > OFF) throw new AssertionError("ee15 head " + (int) off + " degrees off the player " + when);
            }
        });
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
