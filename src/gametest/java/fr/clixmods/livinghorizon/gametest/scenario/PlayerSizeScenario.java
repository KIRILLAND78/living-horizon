package fr.clixmods.livinghorizon.gametest.scenario;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Profiles;
import fr.clixmods.livinghorizon.track.Puppets;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Players chosen as pictures by their size on screen, the impostor distance out of reach: test
 * players who logged off and players online (made on the client, listed in the tab list), 30 and
 * 60 blocks off, models with the size off, pictures at 64 pixels. The game draws players online
 * itself, ten times as far as other entities: it must leave the small ones to the pictures.
 */
public final class PlayerSizeScenario implements Scenario {
    private static final int GROUND = -60;
    private static final String[] NAMES = {"SizeNear", "SizeFar"};
    private static final String[] ONLINE = {"LiveNear", "LiveFar"};
    private static final List<Entity> LIVE = new ArrayList<>();
    private static final int[] DISTANCES = {30, 60};

    @Override
    public String name() {
        return "playersize";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.offlineLife = false;
            config.impostorPlayers = true;
            config.impostorDistance = 2000;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < NAMES.length; i++) {
                Scene.command(world, "tp @a 0.5 " + GROUND + " " + (DISTANCES[i] + 0.5) + " 180 0");
                context.waitTicks(10);
                command(context, "livinghorizon debug offline test spawn " + NAMES[i]);
                context.waitTicks(5);
            }
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(20);
            context.runOnClient(minecraft -> {
                LIVE.clear();
                for (int i = 0; i < ONLINE.length; i++) LIVE.add(online(minecraft, ONLINE[i], 4.5, DISTANCES[i] + 0.5));
            });
            context.waitTicks(60);

            int[] off = drawn(context, "off");
            Scene.screenshot(context, this, "off");
            Scene.configure(context, config -> config.impostorPixels = 64);
            int[] all = null;
            for (int i = 0; i < 20; i++) {
                all = drawn(context, "64px");
                if (all[1] == NAMES.length && all[3] == ONLINE.length) break;
            }
            Scene.screenshot(context, this, "64px");
            command(context, "livinghorizon debug offline test clear");
            context.runOnClient(minecraft -> LIVE.forEach(Entity::discard));

            if (off[0] != NAMES.length || off[2] != ONLINE.length) {
                throw new AssertionError("playersize: players missing: " + off[0] + " offline, " + off[2] + " online");
            }
            if (off[1] != 0 || off[3] != 0) {
                throw new AssertionError("playersize: pictures with the size off: " + off[1] + " offline, " + off[3] + " online");
            }
            if (all[1] != NAMES.length) {
                throw new AssertionError("playersize: at 64 pixels every test player should be a picture: " + all[1]);
            }
            if (all[3] != ONLINE.length) {
                throw new AssertionError("playersize: at 64 pixels every player online should be a picture: " + all[3]);
            }
        }
    }

    /** Test players drawn, and how many of them as pictures. */
    private static int[] drawn(ClientGameTestContext context, String label) {
        context.waitTicks(10);
        return context.computeOnClient(minecraft -> {
            int shown = 0, pictured = 0, online = 0, onlinePictured = 0;
            for (Entity puppet : puppets(minecraft)) {
                shown++;
                if (GhostRenderer.pictured(puppet)) pictured++;
            }
            for (Entity live : LIVE) {
                if (live.isRemoved()) continue;
                online++;
                if (GhostRenderer.pictured(live)) onlinePictured++;
            }
            Scene.log("playersize " + label + " offline=" + shown + " pictured=" + pictured + " online=" + online
                    + " pictured=" + onlinePictured + " impostors=" + PolygonStats.impostors()
                    + " models=" + PolygonStats.models());
            return new int[]{shown, pictured, online, onlinePictured};
        });
    }

    /** A player online the server does not know, standing at x, z, listed in the tab list as the server would. */
    @SuppressWarnings("unchecked")
    private static Entity online(Minecraft minecraft, String name, double x, double z) {
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        try {
            Field field = ClientPacketListener.class.getDeclaredField("playerInfoMap");
            field.setAccessible(true);
            ((Map<UUID, PlayerInfo>) field.get(minecraft.getConnection())).put(Profiles.id(profile), new PlayerInfo(profile, false));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("playersize: no tab list", e);
        }
        RemotePlayer player = Puppets.player(minecraft.level, profile);
        player.setPos(x, GROUND, z);
        player.setYRot(180);
        player.setYBodyRot(180);
        player.setYHeadRot(180);
        minecraft.level.addEntity(player);
        return player;
    }

    private static List<Entity> puppets(Minecraft minecraft) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        List<Entity> out = new ArrayList<>();
        for (RestingPlayers.Spot spot : resting.spots()) {
            for (String name : NAMES) {
                if (spot.name().equals(name)) out.add(resting.puppet(minecraft.level, spot));
            }
        }
        return out;
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }
}
