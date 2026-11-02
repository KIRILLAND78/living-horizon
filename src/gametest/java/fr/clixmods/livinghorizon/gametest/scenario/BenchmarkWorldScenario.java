package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.Benchmark;
import fr.clixmods.livinghorizon.debug.BenchmarkWorld;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /livinghorizon benchmark world}, from the title screen to the end: the world made,
 * nothing in it but the mobs placed (none spawned by the game), the far herds remembered,
 * and a look at it facing south. Then {@code /livinghorizon benchmark} is run in it with the
 * player turned away: it must put them back on the spot and say in its report that it ran in
 * the benchmark world. The world is closed again afterwards.
 */
public final class BenchmarkWorldScenario implements Scenario {
    @Override
    public String name() {
        return "benchmarkworld";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(BenchmarkWorld::start);
        for (int i = 0; i < 20 * 60 * 2 && context.computeOnClient(minecraft -> BenchmarkWorld.running()); i++) {
            context.waitTicks(10);
        }
        if (context.computeOnClient(minecraft -> BenchmarkWorld.running())) throw new AssertionError("The benchmark world was never ready");
        String counts = context.computeOnClient(minecraft -> {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return "no server";
            int placedCount = 0, other = 0;
            StringBuilder strays = new StringBuilder();
            for (Entity entity : server.overworld().getAllEntities()) {
                if (entity instanceof Player) continue;
                //? if >=26.1 {
                /*boolean placed = entity.entityTags().contains(BenchmarkWorld.TAG);
                *///?} else
                boolean placed = entity.getTags().contains(BenchmarkWorld.TAG);
                if (placed) {
                    placedCount++;
                } else {
                    other++;
                    if (strays.length() < 300) strays.append(entity.getType()).append(" at ").append(entity.blockPosition()).append("; ");
                }
            }
            return placedCount + " placed loaded, " + other + " others " + strays + "| remembered "
                    + FarPlayerTracker.get().mobs().size() + ", shown " + FarPlayerTracker.get().mobs().shown().size()
                    + " | player at " + minecraft.player.blockPosition() + " yaw " + minecraft.player.getYRot();
        });
        Scene.log("benchmark world: " + counts);
        if (!counts.contains(" 0 others")) throw new AssertionError("Mobs the benchmark did not place: " + counts);
        // Every mob placed is there: drawn by the game, or remembered once the scan has read it.
        int found = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().size()
                + FarPlayerTracker.get().mobs().live().size());
        Scene.log("benchmark world: " + found + " mobs remembered or sent, of " + BenchmarkWorld.mobs() + " placed");
        if (found < BenchmarkWorld.mobs()) throw new AssertionError("Mobs placed but lost: " + found + " of " + BenchmarkWorld.mobs());
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
        context.waitTicks(100);
        Scene.screenshot(context, this, "south");

        if (!context.computeOnClient(BenchmarkWorld::isIn)) throw new AssertionError("The benchmark world is not marked as one");
        // Turned away and off the spot, as a player who walked about since the world was made.
        context.runOnClient(minecraft -> {
            var server = minecraft.getSingleplayerServer();
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), "tp @a 10 ~ -10 90 20"));
        });
        context.waitTicks(20);
        boolean started = context.computeOnClient(minecraft -> Benchmark.start(minecraft, Benchmark.MIN_SECONDS));
        if (!started) throw new AssertionError("The benchmark did not start");
        context.waitTicks(100);
        String pose = context.computeOnClient(minecraft -> String.format(java.util.Locale.ROOT, "%.1f %.1f yaw %.1f pitch %.1f",
                minecraft.player.getX(), minecraft.player.getZ(), minecraft.player.getYRot(), minecraft.player.getXRot()));
        Scene.log("benchmark world: player during the benchmark at " + pose);
        // The middle of the block, where /tp puts a player given whole numbers.
        if (!pose.equals("0.5 0.5 yaw 0.0 pitch 0.0")) throw new AssertionError("Not put back on the spot: " + pose);
        int crowd = context.computeOnClient(minecraft -> BenchmarkWorld.crowdPlaced());
        Scene.log("benchmark world: " + crowd + " of " + BenchmarkWorld.crowd() + " players who logged off placed");
        if (crowd != BenchmarkWorld.crowd()) throw new AssertionError("The crowd is not all there: " + crowd + " of " + BenchmarkWorld.crowd());
        for (int i = 0; i < 600 && context.computeOnClient(minecraft -> Benchmark.running()); i++) {
            context.waitTicks(10);
        }
        if (context.computeOnClient(minecraft -> Benchmark.running())) throw new AssertionError("The benchmark never ended");
        // The server of this game shares test players back for a second or so after they are cleared.
        context.waitTicks(100);
        int left = context.computeOnClient(minecraft -> BenchmarkWorld.crowdPlaced());
        if (left != 0) throw new AssertionError("The crowd was not taken away: " + left + " left");
        Path folder = context.computeOnClient(minecraft -> minecraft.gameDirectory.toPath()).resolve("livinghorizon").resolve("benchmarks");
        try (Stream<Path> files = Files.list(folder)) {
            Path report = files.max(Comparator.comparing(Path::toString)).orElseThrow(() -> new AssertionError("No report"));
            List<String> lines = Files.readAllLines(report);
            for (String line : lines) Scene.log("| " + line);
            if (lines.stream().noneMatch(line -> line.startsWith("World: the benchmark world"))) {
                throw new AssertionError("The report does not say it ran in the benchmark world");
            }
        } catch (IOException e) {
            throw new AssertionError("Could not read the report", e);
        }
        int distance = context.computeOnClient(minecraft -> minecraft.options.renderDistance().get());
        if (distance != 16) throw new AssertionError("The render distance was not put back: " + distance);

        context.runOnClient(BenchmarkWorld::leave);
        // Closed once the integrated server's thread has ended, as the test framework counts it:
        // this world is not one the framework made, so it is not closed for us.
        for (int i = 0; i < 600 && (net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl.isServerRunning
                || context.computeOnClient(minecraft -> minecraft.level != null)); i++) {
            context.waitTicks(5);
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }
}
