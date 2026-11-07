package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.Benchmark;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * {@code /livinghorizon benchmark}, run to the end in a flat world with a few mobs: every
 * phase measured, the settings put back, and a report written, which is logged. The command
 * itself is hidden from players: it is there only in a game started with {@code -Dlh.benchmark=true}.
 */
public final class BenchmarkScenario implements Scenario {
    @Override
    public String name() {
        return "benchmark";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, new String[]{"cow", "sheep", "pig"}, 3, 60, 6.0);
            context.waitTicks(40);
            // The command is only there in a game started with -Dlh.benchmark=true, which this one is not.
            boolean hidden = context.computeOnClient(minecraft -> {
                // The Fabric API renamed ClientCommandManager to ClientCommands on 26.1.
                //? if <26.1 {
                var root = net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.getActiveDispatcher()
                        .getRoot().getChild("livinghorizon");
                //?} else {
                /*var root = net.fabricmc.fabric.api.client.command.v2.ClientCommands.getActiveDispatcher()
                        .getRoot().getChild("livinghorizon");
                *///?}
                var command = root == null ? null : root.getChild("benchmark");
                return command != null && !command.canUse(null);
            });
            if (!hidden) throw new AssertionError("/livinghorizon benchmark is there for players");
            int before = context.computeOnClient(minecraft -> minecraft.options.framerateLimit().get());
            boolean started = context.computeOnClient(minecraft -> Benchmark.start(minecraft, Benchmark.MIN_SECONDS));
            if (!started) throw new AssertionError("The benchmark did not start");
            for (int i = 0; i < 400 && context.computeOnClient(minecraft -> Benchmark.running()); i++) {
                context.waitTicks(10);
            }
            if (context.computeOnClient(minecraft -> Benchmark.running())) throw new AssertionError("The benchmark never ended");
            int after = context.computeOnClient(minecraft -> minecraft.options.framerateLimit().get());
            if (before != after) throw new AssertionError("The frame rate cap was not put back: " + before + " -> " + after);
            Path folder = context.computeOnClient(minecraft -> minecraft.gameDirectory.toPath())
                    .resolve("livinghorizon").resolve("benchmarks");
            try (Stream<Path> files = Files.list(folder)) {
                Path report = files.max(Comparator.comparing(Path::toString))
                        .orElseThrow(() -> new AssertionError("No report in " + folder));
                Scene.log("benchmark report " + report);
                for (String line : Files.readAllLines(report)) Scene.log("| " + line);
            } catch (IOException e) {
                throw new AssertionError("Could not read the report", e);
            }
        }
    }
}
