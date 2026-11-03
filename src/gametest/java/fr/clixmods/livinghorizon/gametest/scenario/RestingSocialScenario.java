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
 * walk up to each other and chat face to face, never the third. In the evening, by a campfire
 * and a furnace, they stand in front of them, each on a spot of its own.
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
            evening(context, world);
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

    /** A campfire and a furnace, three of them around, the evening: in time they stand in front of them. */
    private void evening(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "setblock 40 " + GROUND + " 0 campfire");
        Scene.command(world, "setblock 44 " + GROUND + " 0 furnace[facing=west]");
        for (int i = 0; i < 3; i++) {
            Scene.command(world, "tp @a " + (36.5 + i * 4) + " " + GROUND + " -5.5 0 0");
            context.waitTicks(5);
            command(context, "livinghorizon debug offline test spawn Warm" + i);
        }
        Scene.command(world, "tp @a 42.5 " + GROUND + " -10.5 0 15");
        Scene.command(world, "time set 11500");
        String found = null;
        for (int i = 0; i < 300 && found == null; i++) {
            context.waitTicks(10);
            found = context.computeOnClient(minecraft -> {
                List<String> by = new ArrayList<>();
                List<net.minecraft.core.BlockPos> spots = new ArrayList<>();
                for (RestingPuppet puppet : puppets(minecraft)) {
                    if (!puppet.warming()) continue;
                    double fire = Math.min(Math.hypot(puppet.getX() - 40.5, puppet.getZ() - 0.5),
                            Math.hypot(puppet.getX() - 44.5, puppet.getZ() - 0.5));
                    if (fire > 1.6) continue;
                    if (spots.contains(puppet.blockPosition())) throw new AssertionError("restingsocial: two on one spot by the fire");
                    spots.add(puppet.blockPosition());
                    double fx = Math.hypot(puppet.getX() - 40.5, puppet.getZ() - 0.5) < 1.6 ? 40.5 : 44.5;
                    float toward = (float) Math.toDegrees(Math.atan2(-(fx - puppet.getX()), 0.5 - puppet.getZ()));
                    by.add(puppet.getName().getString() + " at " + puppet.blockPosition().toShortString() + ", "
                            + Math.abs(Mth.wrapDegrees(toward - puppet.yBodyRot)) + " degrees off facing it, " + puppet.state());
                }
                return by.isEmpty() ? null : String.join(", ", by);
            });
        }
        Scene.log("restingsocial evening: " + found);
        if (found == null) throw new AssertionError("restingsocial: nobody by the fire in the evening");
        context.waitTicks(40);
        Scene.log("restingsocial evening, 2 s later: " + context.computeOnClient(minecraft -> {
            StringBuilder line = new StringBuilder();
            for (RestingPuppet puppet : puppets(minecraft)) {
                if (!puppet.warming()) continue;
                double fx = Math.hypot(puppet.getX() - 40.5, puppet.getZ() - 0.5) < 1.6 ? 40.5 : 44.5;
                float toward = (float) Math.toDegrees(Math.atan2(-(fx - puppet.getX()), 0.5 - puppet.getZ()));
                float off = Math.abs(Mth.wrapDegrees(toward - puppet.yBodyRot));
                if (off > 40) throw new AssertionError("restingsocial: " + puppet.getName().getString() + " turned " + off + " degrees from the fire");
                line.append(puppet.getName().getString()).append(' ').append(off).append("; ");
            }
            return line.toString();
        }));
        Scene.screenshot(context, this, "evening");
        Scene.command(world, "time set noon");
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
