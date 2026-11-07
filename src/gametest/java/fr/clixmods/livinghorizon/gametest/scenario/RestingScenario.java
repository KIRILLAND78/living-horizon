package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A player who logged off, 8 blocks ahead. What a server with the mod would share is handed
 * to the client by hand: the sleeper is someone who left before.
 *
 * <p>Seated first ({@code offlineLife} off), on a pillar: the pillar is broken (they fall),
 * they are looked at from afar (their name only) and from close by (when they were last seen
 * too), buried (they reappear on top), the pillar built again (back on it). Then they live:
 * at night they walk to a bed in a hut, through its shut door (never a jump of more than a
 * stroll), in the morning they get up and stay around; hit, they raise a shield; a monster
 * they cannot see leaves them calm, one in sight puts them on guard, sword drawn, until it is
 * gone; a creeper blowing up throws them, and they walk back. Last, the switch that hides
 * them all, and the debug command's test player, then a crowd of them.
 */
public final class RestingScenario implements Scenario {
    private static final String NAME = "LH_Sleeper";
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "resting";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Renamed in 1.21.11: one of the two is refused.
            Scene.command(world, "gamerule mobGriefing false");
            Scene.command(world, "gamerule mob_griefing false");
            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 stone");
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            wait(context, world, 60);
            check(context, "pillar", GROUND + 3, false);
            Scene.screenshot(context, this, "pillar");

            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 air");
            wait(context, world, 40);
            check(context, "fallen", GROUND, false);
            Scene.screenshot(context, this, "fallen");

            // Looking at them from 8 blocks: their name above them, nothing under the crosshair.
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 10");
            wait(context, world, 20);
            aimed(context, false);
            Scene.screenshot(context, this, "looked-at-far");

            // Right next to them: who they are under the crosshair too.
            Scene.command(world, "tp @a 0.5 " + GROUND + " 6 0 20");
            wait(context, world, 20);
            aimed(context, true);
            Scene.screenshot(context, this, "looked-at");

            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 1) + " 8 dirt");
            wait(context, world, 20);
            check(context, "buried", GROUND + 2, false);
            Scene.screenshot(context, this, "buried");

            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 stone");
            wait(context, world, 20);
            check(context, "rebuilt", GROUND + 3, false);
            Scene.screenshot(context, this, "rebuilt");

            // Living now, at night, with a bed a few blocks away in a hut whose door is shut:
            // they walk to it, through the door.
            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 air");
            Scene.command(world, "fill 2 " + (GROUND - 1) + " 6 6 " + (GROUND + 2) + " 11 oak_planks hollow");
            Scene.command(world, "fill 3 " + (GROUND + 2) + " 7 5 " + (GROUND + 2) + " 10 air");
            Scene.command(world, "setblock 4 " + GROUND + " 6 oak_door[half=lower,facing=north]");
            Scene.command(world, "setblock 4 " + (GROUND + 1) + " 6 oak_door[half=upper,facing=north]");
            Scene.command(world, "setblock 4 " + GROUND + " 9 red_bed[facing=north,part=foot]");
            Scene.command(world, "setblock 4 " + GROUND + " 8 red_bed[facing=north,part=head]");
            Scene.command(world, "time set midnight");
            Scene.configure(context, config -> config.offlineLife = true);
            Vec3 last = sleeper(context, p -> p.position());
            boolean inBed = false;
            for (int i = 0; i < 40 && !inBed; i++) {
                wait(context, world, 10);
                Vec3 now = sleeper(context, p -> p.position());
                // Across, not down: falling off the broken pillar is quick.
                double across = Math.hypot(now.x - last.x, now.z - last.z);
                if (across > 1.6) {
                    throw new AssertionError("resting: moved " + across + " blocks in 10 ticks: teleported, not walked");
                }
                last = now;
                inBed = sleeper(context, RestingPuppet::inBed);
                if (i % 5 == 0) Scene.log("resting night " + i + ": " + now + " " + sleeper(context, RestingPuppet::state));
            }
            Scene.log("resting in bed at night: " + inBed);
            if (!inBed) throw new AssertionError("resting: not in the bed after 400 ticks of night");
            Scene.screenshot(context, this, "bed-night");

            // Morning: up, and around their spot. The hut goes, to see them in the open.
            Scene.command(world, "time set noon");
            Scene.command(world, "fill 2 " + GROUND + " 6 6 " + (GROUND + 2) + " 11 air");
            wait(context, world, 300);
            boolean stillInBed = sleeper(context, RestingPuppet::inBed);
            double away = sleeper(context, p -> Math.hypot(p.getX() - 0.5, p.getZ() - 8.5));
            Scene.log("resting day: in bed " + stillInBed + ", " + away + " blocks from their spot");
            if (stillInBed || away > 52) throw new AssertionError("resting: in bed " + stillInBed + " at noon, " + away + " blocks away");
            Scene.screenshot(context, this, "day");

            // Hit: the shield goes up. Two blocks north of them, facing them, then a click.
            Vec3 near = sleeper(context, p -> p.position());
            Scene.command(world, "tp @a " + near.x + " " + near.y + " " + (near.z - 2) + " 0 20");
            context.waitTicks(3);
            context.runOnClient(minecraft -> {
                Vec3 at = puppet(minecraft).position().add(0, 0.6, 0);
                Vec3 eye = minecraft.player.getEyePosition();
                double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
                minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                minecraft.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
                minecraft.options.keyAttack.setDown(true);
            });
            context.waitTicks(2);
            context.runOnClient(minecraft -> minecraft.options.keyAttack.setDown(false));
            boolean blocking = sleeper(context, RestingPuppet::blocking);
            Scene.log("resting hit: shield up " + blocking);
            Scene.screenshot(context, this, "shield");
            if (!blocking) throw new AssertionError("resting: no shield up after a hit");

            // A monster they cannot see, shut in a box of stone: no need for a shield.
            Vec3 spot = sleeper(context, p -> p.position());
            Scene.command(world, "tp @a " + (spot.x + 8) + " " + GROUND + " " + spot.z + " 90 10");
            int bx = (int) Math.floor(spot.x) + 4, bz = (int) Math.floor(spot.z);
            Scene.command(world, "fill " + (bx - 1) + " " + (GROUND - 1) + " " + (bz - 1) + " " + (bx + 1) + " "
                    + (GROUND + 3) + " " + (bz + 1) + " stone hollow");
            Scene.command(world, "summon husk " + (bx + 0.5) + " " + GROUND + " " + (bz + 0.5) + " {NoAI:1b}");
            wait(context, world, 30);
            boolean unseen = sleeper(context, p -> !p.guarding() && !p.blocking());
            Scene.log("resting monster out of sight: calm " + unseen);
            if (!unseen) throw new AssertionError("resting: on guard against a husk shut in a box");
            Scene.command(world, "kill @e[type=husk]");
            Scene.command(world, "fill " + (bx - 1) + " " + GROUND + " " + (bz - 1) + " " + (bx + 1) + " "
                    + (GROUND + 3) + " " + (bz + 1) + " air");
            wait(context, world, 10);

            // A monster two blocks away: sword drawn, shield up, eyes on it; gone, calm again.
            spot = sleeper(context, p -> p.position());
            Scene.command(world, "summon husk " + (spot.x + 2) + " " + spot.y + " " + spot.z + " {NoAI:1b}");
            wait(context, world, 30);
            boolean guarding = sleeper(context, p -> p.guarding() && p.blocking()
                    && p.getMainHandItem().is(Items.IRON_SWORD));
            Scene.log("resting monster near: on guard " + guarding);
            Scene.screenshot(context, this, "guard");
            if (!guarding) throw new AssertionError("resting: not on guard with a husk two blocks away");
            Scene.command(world, "kill @e[type=husk]");
            wait(context, world, 140);
            boolean calm = sleeper(context, p -> !p.guarding() && !p.blocking() && p.getMainHandItem().isEmpty());
            Scene.log("resting monster gone: calm " + calm);
            if (!calm) throw new AssertionError("resting: still on guard once the husk is gone");

            // A creeper blowing up next to them: thrown, then back where they stood.
            Vec3 before = sleeper(context, p -> p.position());
            Scene.command(world, "tp @a " + (before.x + 12) + " " + GROUND + " " + before.z + " 90 10");
            Scene.command(world, "summon creeper " + (before.x + 2) + " " + before.y + " " + before.z
                    + " {NoAI:1b,Fuse:30,ignited:1b}");
            boolean thrown = false;
            Vec3 stood = before;
            for (int i = 0; i < 80 && !thrown; i++) {
                context.waitTick();
                Vec3 now = sleeper(context, p -> p.position());
                thrown = sleeper(context, RestingPuppet::thrown);
                if (!thrown) stood = now;
            }
            Scene.log("resting creeper: thrown " + thrown);
            if (!thrown) throw new AssertionError("resting: not thrown by the creeper");
            Scene.screenshot(context, this, "thrown");
            // Back where it stood at some point: once there, it may well go for a stroll again.
            double off = Double.MAX_VALUE;
            for (int k = 0; k < 20 && off > 0.5; k++) {
                wait(context, world, 10);
                Vec3 back = sleeper(context, p -> p.position());
                off = Math.min(off, Math.hypot(back.x - stood.x, back.z - stood.z));
            }
            Scene.log("resting creeper: " + off + " blocks from where they stood");
            if (off > 1.5) throw new AssertionError("resting: did not walk back after the blast, " + off + " blocks off");

            // Switched off: neither shown nor looked at.
            Scene.configure(context, config -> config.offlinePlayers = false);
            wait(context, world, 20);
            aimed(context, false);
            Scene.screenshot(context, this, "switched-off");

            // The debug command: a test player where the camera stands, logged off 90 minutes
            // ago, looked at from 6 blocks back (nothing under the crosshair), then from 2.5
            // (the date under the crosshair), then removed. Seated, so that it does not stroll
            // off the crosshair once it has looked around.
            Scene.configure(context, config -> {
                config.offlinePlayers = true;
                config.offlineLife = false;
            });
            Scene.command(world, "tp @a 0.5 " + GROUND + " -10.5 0 0");
            wait(context, world, 10);
            clientCommand(context, "livinghorizon debug offline test spawn Tester 90");
            Scene.command(world, "tp @a 0.5 " + GROUND + " -16.5 0 10");
            wait(context, world, 20);
            boolean far = context.computeOnClient(minecraft -> FarPlayerTracker.get().resting().lookedAt() != null);
            if (far) throw new AssertionError("resting test player: looked at from 6 blocks");
            Scene.command(world, "tp @a 0.5 " + GROUND + " -13 0 18");
            wait(context, world, 20);
            String tester = context.computeOnClient(minecraft -> {
                RestingPlayers resting = FarPlayerTracker.get().resting();
                RestingPlayers.Spot seen = resting.lookedAt();
                if (seen == null || !seen.name().equals("Tester") || !resting.isFake(seen)) return "looked at " + seen;
                long minutes = (System.currentTimeMillis() - seen.lastSeen()) / 60_000;
                return minutes >= 89 && minutes <= 91 ? null : "logged off " + minutes + " minutes ago";
            });
            if (tester != null) throw new AssertionError("resting test player: " + tester);
            Scene.log("resting test player looked at");
            Scene.screenshot(context, this, "test-player");
            clientCommand(context, "livinghorizon debug offline test clear");
            wait(context, world, 10);
            boolean gone = context.computeOnClient(minecraft -> FarPlayerTracker.get().resting().spots().stream()
                    .noneMatch(s -> s.name().equals("Tester")));
            if (!gone) throw new AssertionError("resting test player still there after clear");
            Scene.log("resting test player cleared");
            Scene.configure(context, config -> config.offlineLife = true);

            // A crowd of them: twelve, all within 20 blocks, on the ground, gone with clear.
            clientCommand(context, "livinghorizon debug offline test crowd 12 20");
            wait(context, world, 10);
            String crowd = context.computeOnClient(minecraft -> {
                RestingPlayers resting = FarPlayerTracker.get().resting();
                Vec3 me = minecraft.player.position();
                long count = resting.spots().stream().filter(resting::isFake).count();
                for (RestingPlayers.Spot s : resting.spots()) {
                    if (!resting.isFake(s)) continue;
                    double from = Math.hypot(s.x() - me.x, s.z() - me.z);
                    if (from > 20.01 || Math.abs(s.y() - GROUND) > 1e-3) return s.name() + " " + from + " blocks away, y=" + s.y();
                }
                return count == 12 ? null : count + " test players";
            });
            if (crowd != null) throw new AssertionError("resting crowd: " + crowd);
            // Each in a real player's skin, fetched from Mojang: logged, not checked, as the
            // game may have no way out to Mojang where it runs.
            wait(context, world, 100);
            long dressed = context.computeOnClient(minecraft -> FarPlayerTracker.get().resting().spots().stream()
                    .filter(rested -> rested.textures() != null).count());
            Scene.log("resting crowd: " + dressed + " of 12 in a real player's skin");
            Scene.screenshot(context, this, "crowd");
            clientCommand(context, "livinghorizon debug offline test clear");
            wait(context, world, 10);
            Scene.log("resting crowd placed and cleared");
        }
    }

    /** Types a command in the chat, as the player would: the mod's commands run on the client. */
    private static void clientCommand(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }

    /** On top of the pillar, facing the camera, logged off: as a server with the mod would share them. */
    private static final PositionsMessage.Player SLEEPER =
            new PositionsMessage.Player(NAME, 0.5, GROUND + 3, 8.5, 180f, "minecraft:overworld", false, false, null);

    /** Waits, sharing the sleeper as the server would, so that what it shares stays trusted. */
    private static void wait(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
        for (int done = 0; done < ticks; done += 10) {
            context.runOnClient(minecraft -> {
                FarPlayerTracker.get().receive(PositionsMessage.players(List.of()));
                PositionsMessage.offline(List.of(SLEEPER)).forEach(FarPlayerTracker.get()::receive);
            });
            context.waitTicks(10);
        }
    }

    private static RestingPuppet puppet(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(NAME) && level != null) return resting.puppet(level, spot);
        }
        throw new AssertionError("resting: no sleeper");
    }

    /** Something about the sleeper, read on the client. */
    private static <T> T sleeper(ClientGameTestContext context, Function<RestingPuppet, T> read) {
        return context.computeOnClient(minecraft -> read.apply(puppet(minecraft)));
    }

    /** Fails the run unless the sleeper is (or is not) the one under the crosshair. */
    private static void aimed(ClientGameTestContext context, boolean expected) {
        String aimed = context.computeOnClient(minecraft -> {
            RestingPlayers.Spot spot = FarPlayerTracker.get().resting().lookedAt();
            return spot == null ? "nobody" : spot.name();
        });
        Scene.log("resting looked at: " + aimed);
        if (expected != NAME.equals(aimed)) {
            throw new AssertionError("resting: looked at " + aimed + ", expected " + (expected ? NAME : "nobody"));
        }
    }

    /** Logs where the sleeper is, and fails the run when it is not where it should be. */
    private static void check(ClientGameTestContext context, String step, double y, boolean bed) {
        @Nullable String where = context.computeOnClient(minecraft -> {
            RestingPuppet puppet = puppet(minecraft);
            Scene.log("resting " + step + " y=" + puppet.getY() + " bed=" + puppet.inBed() + " look=" + puppet.look());
            return Math.abs(puppet.getY() - y) < 1e-3 && puppet.inBed() == bed ? null
                    : "y=" + puppet.getY() + " bed=" + puppet.inBed();
        });
        if (where != null) throw new AssertionError("resting " + step + ": expected y=" + y + " bed=" + bed + ", got " + where);
    }
}
