package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.Mca;
import fr.clixmods.livinghorizon.compat.McaLook;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Players who logged off, with Minecraft Comes Alive (MCA; {@code --with minecraft-comes-alive-reborn}):
 * one of its villagers up close first, then two players side by side 6 blocks ahead, one shared
 * with that villager's look, read as the server reads a player's, one without. The first must be drawn as MCA draws that villager,
 * the second in their skin. Without MCA it says so and checks nothing.
 */
public final class McaScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String PLAIN = "LH_Plain", DRESSED = "LH_Mca";

    @Override
    public String name() {
        return "mca";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!Platform.isModLoaded(McaLook.MOD)) {
            Scene.log("mca: Minecraft Comes Alive is not installed, nothing checked");
            return;
        }
        Scene.configure(context, config -> config.offlineLife = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // MCA greets a new player with a screen of its own ("Who are you?"), which pauses the game.
            context.waitTicks(40);
            context.runOnClient(McaScenario::closeDestiny);
            // Without data, so that MCA dresses it as it spawns: the game skips that for a summon with
            // data, and an MCA villager with no gender, clothes or hair is drawn as its eyes alone.
            Scene.command(world, "summon mca:female_villager 0.5 " + GROUND + " 5.5");
            Scene.command(world, "data merge entity @e[type=mca:female_villager,limit=1] {NoAI:1b,Rotation:[180f,0f]}");
            context.waitTicks(60);
            String look = world.getServer().computeOnServer(server -> {
                for (Entity entity : server.overworld().getAllEntities()) {
                    if (EntityType.getKey(entity.getType()).toString().equals("mca:female_villager")) {
                        return McaLook.trim(EntityNbt.save(entity, entity.level()));
                    }
                }
                return null;
            });
            if (look == null) throw new AssertionError("mca: no look read from MCA's villager");
            Scene.log("mca: look of " + look.length() + " characters");
            context.runOnClient(McaScenario::closeDestiny);
            Scene.screenshot(context, this, "villager");
            Scene.command(world, "kill @e[type=mca:female_villager]");

            List<PositionsMessage.Player> shared = List.of(
                    offline(PLAIN, -1.5, null),
                    offline(DRESSED, 1.5, look));
            share(context, shared, 80);
            int plain = context.computeOnClient(minecraft -> lookHash(minecraft, PLAIN));
            int dressed = context.computeOnClient(minecraft -> lookHash(minecraft, DRESSED));
            Scene.log("mca: handed to MCA, plain " + (plain != 0) + ", dressed " + (dressed != 0));
            context.runOnClient(McaScenario::closeDestiny);
            Scene.screenshot(context, this, "offline");
            if (plain != 0 || dressed == 0) {
                throw new AssertionError("mca: the look went to the wrong player, plain " + plain + ", dressed " + dressed);
            }

            // From 150 blocks, zoomed in: as models, then as simplified pictures, each its own.
            Scene.command(world, "tp @a 0.5 " + GROUND + " -143.5 0 0");
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.configure(context, config -> config.impostorPlayers = false);
            share(context, shared, 60);
            Scene.screenshot(context, this, "far-models");
            Scene.configure(context, config -> config.impostorPlayers = true);
            String why = null;
            for (int i = 0; i < 30 && (why = pictured(context)) != null; i++) share(context, shared, 10);
            Scene.log("mca far: " + (why == null ? "both pictured, apart" : why));
            Scene.screenshot(context, this, "far-impostors");
            Scene.screenshotAtlas(context, this);
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            if (why != null) throw new AssertionError("mca far: " + why);
        }
    }

    /** Shares the two players as a server with the mod would, for {@code ticks} ticks. */
    private static void share(ClientGameTestContext context, List<PositionsMessage.Player> shared, int ticks) {
        for (int done = 0; done < ticks; done += 10) {
            context.runOnClient(minecraft -> {
                closeDestiny(minecraft);
                FarPlayerTracker.get().receive(PositionsMessage.players(List.of()));
                PositionsMessage.offline(shared).forEach(FarPlayerTracker.get()::receive);
            });
            context.waitTicks(10);
        }
    }

    /** Null once both players have a simplified picture, each a picture of its own; else why not. */
    private static @Nullable String pictured(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> {
            ImpostorKey plain = ImpostorKey.of(puppet(minecraft, PLAIN));
            ImpostorKey dressed = ImpostorKey.of(puppet(minecraft, DRESSED));
            if (plain.equals(dressed)) return "both players share one picture";
            if (ImpostorAtlas.sheet(plain) == null || ImpostorAtlas.sheet(dressed) == null) {
                return "no picture yet (waiting " + ImpostorAtlas.waiting() + ")";
            }
            return null;
        });
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("mca: no puppet for " + name);
    }

    /**
     * Closes MCA's "Who are you?" screen, which it opens again every tick until told it may close:
     * it pauses the game, and nothing of the mod's would tick.
     */
    private static void closeDestiny(Minecraft minecraft) {
        try {
            Object destiny = Class.forName("net.conczin.mca.MCAClient").getMethod("getDestinyManager").invoke(null);
            destiny.getClass().getMethod("allowClosing").invoke(destiny);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("mca: MCA's destiny screen cannot be closed", e);
        }
        if (minecraft.screen != null) minecraft.setScreen(null);
    }

    /** A player who logged off 6 blocks ahead, facing the camera, in this look of MCA. */
    private static PositionsMessage.Player offline(String name, double x, @Nullable String mca) {
        return new PositionsMessage.Player(name, x, GROUND, 6.5, 180f, "minecraft:overworld", false, false, null, null,
                new PositionsMessage.Look(null, null, List.of(), mca), 0, false);
    }

    /** What MCA was handed for the puppet of {@code name} ({@link Mca#lookHash}), 0 for nothing. */
    private static int lookHash(Minecraft minecraft, String name) {
        return Mca.lookHash(puppet(minecraft, name));
    }
}
