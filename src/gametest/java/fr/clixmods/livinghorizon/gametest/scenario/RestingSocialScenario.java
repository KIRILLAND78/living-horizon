package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Players who logged off, near the camera, among themselves: three close together, two of them
 * walk up to each other and chat face to face, never the third.
 */
public final class RestingSocialScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "restingsocial";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            chat(context, world);
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /** Three of them 5 blocks apart: in time two chat, face to face, and never the third. */
    private void chat(ClientGameTestContext context, TestSingleplayerContext world) {
        for (int i = 0; i < 3; i++) {
            Scene.command(world, "tp @a " + (i * 5 - 4.5) + " " + GROUND + " 0.5 " + (i * 120) + " 0");
            context.waitTicks(5);
            command(context, "livinghorizon debug offline test spawn Chatter" + i);
        }
        Scene.command(world, "tp @a 0.5 " + GROUND + " -12.5 0 20");
        String pair = null;
        for (int i = 0; i < 500 && pair == null; i++) {
            context.waitTicks(10);
            if (i % 50 == 0) {
                Scene.log("restingsocial " + i * 10 + ": " + context.computeOnClient(minecraft -> {
                    StringBuilder line = new StringBuilder();
                    for (RestingPuppet puppet : puppets(minecraft)) {
                        line.append(puppet.getName().getString()).append(' ').append(puppet.blockPosition().toShortString())
                                .append(' ').append(puppet.state()).append(puppet.partner() != null ? " (with "
                                        + puppet.partner().getName().getString() + ")" : "").append("; ");
                    }
                    return line.toString();
                }));
            }
            pair = context.computeOnClient(minecraft -> {
                List<RestingPuppet> chatting = new ArrayList<>();
                for (RestingPuppet puppet : puppets(minecraft)) {
                    if (puppet.chatting()) chatting.add(puppet);
                }
                if (chatting.size() > 2) throw new AssertionError("restingsocial: " + chatting.size() + " chatting together");
                if (chatting.size() < 2) return null;
                RestingPuppet a = chatting.get(0), b = chatting.get(1);
                if (a.partner() != b || b.partner() != a) throw new AssertionError("restingsocial: chatting with no one of the two");
                return a.getName().getString() + " and " + b.getName().getString() + ", " + a.distanceTo(b) + " apart, "
                        + facing(a, b) + " and " + facing(b, a) + " degrees off facing each other";
            });
        }
        Scene.log("restingsocial chat: " + pair);
        if (pair == null) throw new AssertionError("restingsocial: nobody chatted");
        context.waitTicks(40);
        String now = context.computeOnClient(minecraft -> {
            for (RestingPuppet puppet : puppets(minecraft)) {
                RestingPuppet other = puppet.partner();
                if (!puppet.chatting() || other == null) continue;
                if (puppet.distanceTo(other) > 3.5) return "too far apart: " + puppet.distanceTo(other);
                if (facing(puppet, other) > 40) return puppet.getName().getString() + " turned away: " + facing(puppet, other);
            }
            return null;
        });
        if (now != null) throw new AssertionError("restingsocial: " + now);
        Scene.screenshot(context, this, "chat");
    }

    /** Degrees between the way {@code a}'s body faces and the way to {@code b}. */
    private static float facing(RestingPuppet a, RestingPuppet b) {
        float toward = (float) Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ()));
        return Math.abs(Mth.wrapDegrees(toward - a.yBodyRot));
    }

    private static List<RestingPuppet> puppets(Minecraft minecraft) {
        List<RestingPuppet> all = new ArrayList<>();
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (minecraft.level != null) all.add(resting.puppet(minecraft.level, spot));
        }
        return all;
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }
}
