package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * A mob the server still sends, past the game's entity distance, turns its head smoothly.
 *
 * <p>The mod draws it, not the game. Entity Culling skips the tick of every entity the game
 * did not draw, and its shorter tick turns the head without moving its angle of the tick
 * before: the head swung between an old angle and the new one every tick (issue #184). Run
 * with Entity Culling in {@code -Plh.mods} to check that case; without it the head turns as
 * it always did.
 */
public final class HeadTurnScenario implements Scenario {
    private static final float[] TURNS = {90, 200, 300};

    @Override
    public String name() {
        return "headturn";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.log("headturn entity culling " + FabricLoader.getInstance().isModLoaded("entityculling"));
        context.runOnClient(minecraft -> {
            // The game stops drawing a cow at about 35 blocks: at 60 the mod draws it.
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.fov().set(30);
        });
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 60, 0);
            context.waitTicks(40);
            for (float yaw : TURNS) {
                Scene.command(world, "execute as @e[type=cow] at @s run tp @s ~ ~ ~ " + yaw + " 0");
                context.waitTicks(30);
                float gap = context.computeOnClient(minecraft -> {
                    LivingEntity cow = cow(minecraft.level == null ? java.util.List.of() : minecraft.level.entitiesForRendering());
                    if (cow == null) return Float.NaN;
                    Scene.log("headturn " + yaw + ": head " + cow.yHeadRot + ", the tick before " + cow.yHeadRotO
                            + ", body " + cow.yBodyRot + ", " + Math.round(Math.sqrt(cow.distanceToSqr(minecraft.player)))
                            + " blocks away");
                    return Math.abs(Mth.wrapDegrees(cow.yHeadRot - cow.yHeadRotO));
                });
                if (Float.isNaN(gap)) throw new AssertionError("The client has no cow");
                if (gap > 1) {
                    throw new AssertionError("A far cow's head swings " + gap + " degrees every tick after turning to " + yaw);
                }
            }
            Scene.screenshot(context, this, "turned");
        }
    }

    private static LivingEntity cow(Iterable<Entity> entities) {
        for (Entity entity : entities) {
            if (entity.getType() == EntityType.COW && entity instanceof LivingEntity cow) return cow;
        }
        return null;
    }
}
