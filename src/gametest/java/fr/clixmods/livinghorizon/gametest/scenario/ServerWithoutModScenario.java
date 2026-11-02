package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;

/**
 * A server that shares nothing, as one without the mod: the client throws away what the
 * test's server sends. Ten seconds after joining, the chat says that distant players are not
 * shown there; with distant players turned off, it says nothing.
 */
public final class ServerWithoutModScenario implements Scenario {
    /** A little past the ten seconds the client waits for the server's word. */
    private static final int AFTER_WARNING = 220;

    @Override
    public String name() {
        return "nomodserver";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Network.receiver(data -> {
        });
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer()) {
            try (var connection = server.connect()) {
                context.waitTicks(AFTER_WARNING);
                context.runOnClient(minecraft -> {
                    String warning = FarPlayerTracker.get().warning();
                    if (!"livinghorizon.sharing.none".equals(warning)) {
                        throw new AssertionError("the chat was not told the server has no mod: " + warning);
                    }
                    Scene.log("nomodserver chat says " + warning);
                });
                Scene.screenshot(context, this, "chat");
            }
            // Distant players off: nothing to say about them.
            Scene.configure(context, config -> config.enabled = false);
            try (var connection = server.connect()) {
                context.waitTicks(AFTER_WARNING);
                context.runOnClient(minecraft -> {
                    String warning = FarPlayerTracker.get().warning();
                    if (warning != null) throw new AssertionError("the chat was told " + warning + " with distant players off");
                    Scene.log("nomodserver silent with distant players off");
                });
            }
        } finally {
            // As the client's entry point hands it in.
            Network.receiver(data -> Minecraft.getInstance().execute(() -> FarPlayerTracker.get().receive(data)));
        }
    }
}
