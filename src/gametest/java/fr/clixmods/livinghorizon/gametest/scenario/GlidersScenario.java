package fr.clixmods.livinghorizon.gametest.scenario;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A player gliding with an elytra, seen from afar: the server shares that they glide and how they
 * look up or down, and their copy flies, its body along the flight, its wings spread, its pitch the
 * server's, and never as a picture baked standing (pictures are on from 8 blocks here). Once they
 * land, the copy stands again, as a picture.
 *
 * <p>The glider is a fake player the game's own server lists as online but never sends as an
 * entity, as a player past the view distance is: only the position the server shares draws them.
 */
public final class GlidersScenario implements Scenario {
    private static final String NAME = "Glider";
    private static final UUID ID = UUIDUtil.createOfflinePlayerUUID(NAME);
    /** Ahead of the camera, which faces south (+z), turned west: seen from the side. */
    private static final double AHEAD = 18;
    private static final float WEST = 90f;

    @Override
    public String name() {
        return "gliders";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorPlayers = true;
            config.impostorDistance = 8;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            double ground = context.computeOnClient(minecraft -> minecraft.player.getY());
            FakePlayer[] glider = new FakePlayer[1];
            onServer(world, server -> {
                FakePlayer fake = FakePlayer.get(server.overworld(), new GameProfile(ID, NAME));
                fake.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                fake.setPos(0, ground + 4, AHEAD);
                fake.setYRot(WEST);
                fake.setXRot(20f);
                fake.startFallFlying();
                // Online, in the tab list, as a player who joined: the server publishes them.
                server.getPlayerList().getPlayers().add(fake);
                server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
                glider[0] = fake;
            });
            try {
                // What they wear comes with everything else, every five seconds.
                context.waitTicks(120);
                check(context, "gliding", puppet -> {
                    if (!puppet.isFallFlying()) throw new AssertionError("the copy is not gliding");
                    if (puppet.fallFlyTicks < 10) throw new AssertionError("the copy glides upright, " + puppet.fallFlyTicks + " ticks");
                    if (!puppet.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                        throw new AssertionError("the copy wears no elytra: " + puppet.getItemBySlot(EquipmentSlot.CHEST));
                    }
                    if (Math.abs(puppet.getXRot() - 20f) > 1f) throw new AssertionError("pitch " + puppet.getXRot() + ", expected 20");
                    if (ImpostorAtlas.sheet(ImpostorKey.of(puppet)) != null) {
                        throw new AssertionError("a picture was baked of the copy while it glides");
                    }
                });
                context.runOnClient(minecraft -> minecraft.options.fov().set(30));
                Scene.screenshot(context, this, "gliding");

                // Climbing: the body tilts up with the pitch the server shares.
                onServer(world, server -> glider[0].setXRot(-30f));
                context.waitTicks(30);
                check(context, "climbing", puppet -> {
                    if (Math.abs(puppet.getXRot() + 30f) > 1f) throw new AssertionError("pitch " + puppet.getXRot() + ", expected -30");
                });
                Scene.screenshot(context, this, "climbing");

                // Landed: standing again, and a picture past 8 blocks.
                onServer(world, server -> {
                    glider[0].stopFallFlying();
                    glider[0].setPos(0, ground, AHEAD);
                    glider[0].setXRot(0f);
                });
                context.waitTicks(60);
                check(context, "landed", puppet -> {
                    if (puppet.isFallFlying() || puppet.fallFlyTicks != 0) throw new AssertionError("the copy still glides");
                    if (puppet.getXRot() != 0f) throw new AssertionError("the copy still looks " + puppet.getXRot());
                    if (ImpostorAtlas.sheet(ImpostorKey.of(puppet)) == null) {
                        throw new AssertionError("no picture of the copy once it stands");
                    }
                });
                Scene.screenshot(context, this, "landed");
                context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            } finally {
                onServer(world, server -> {
                    server.getPlayerList().getPlayers().remove(glider[0]);
                    server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(ID)));
                });
                context.waitTicks(5);
            }
        }
    }

    private static void onServer(TestSingleplayerContext world, Consumer<MinecraftServer> action) {
        world.getServer().runOnServer(action::accept);
    }

    /** The glider's copy, as the client draws it, checked; logged with {@code step}. */
    private static void check(ClientGameTestContext context, String step, Consumer<RemotePlayer> test) {
        context.runOnClient(minecraft -> {
            FarPlayer far = FarPlayerTracker.get().players().stream().filter(player -> player.id().equals(ID))
                    .findFirst().orElseThrow(() -> new AssertionError(step + ": the glider is not followed"));
            RemotePlayer puppet = far.puppet();
            if (puppet == null || !far.showsPuppet(FarConfig.get())) {
                throw new AssertionError(step + ": the glider's copy is not drawn, " + far.source());
            }
            test.accept(puppet);
            Scene.log("gliders " + step + ": gliding=" + puppet.isFallFlying() + " ticks=" + puppet.fallFlyTicks
                    + " pitch=" + puppet.getXRot() + " y=" + puppet.getY() + " chest=" + puppet.getItemBySlot(EquipmentSlot.CHEST));
        });
    }
}
