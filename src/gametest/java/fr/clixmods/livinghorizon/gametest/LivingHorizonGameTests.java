package fr.clixmods.livinghorizon.gametest;

import fr.clixmods.livinghorizon.gametest.scenario.BirdCommandScenario;
import fr.clixmods.livinghorizon.gametest.scenario.BirdsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.DedicatedServerScenario;
import fr.clixmods.livinghorizon.gametest.scenario.DepthViewScenario;
import fr.clixmods.livinghorizon.gametest.scenario.EasterEggsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.EntityDepthScenario;
import fr.clixmods.livinghorizon.gametest.scenario.FollowedMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.GaitsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorCrowdScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorEdgesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorLongMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorScreenScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorSharingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.LanShareScenario;
import fr.clixmods.livinghorizon.gametest.scenario.LightingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OptionsScreenScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OutlinesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.PerchersScenario;
import fr.clixmods.livinghorizon.gametest.scenario.PerfScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RememberedMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ResizeScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingCrowdScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingImpostorsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingLoopScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingMountScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingTouchScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ShoreScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SodiumControlsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SodiumPagesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.UnloadScenario;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The client game tests: the real game, with the mod, driven by a script that takes
 * screenshots. Run with {@code ./gradlew runClientGameTest} (under {@code xvfb-run} without
 * a display); see {@code .claude/skills/game-test/SKILL.md}.
 *
 * <p>Each scenario is one {@link Scenario} in {@link #SCENARIOS}. {@code -Plh.scenario=a,b}
 * runs only those; the default runs them all. A new check is a new scenario added here.
 */
public final class LivingHorizonGameTests implements FabricClientGameTest {
    private static final List<Scenario> SCENARIOS = List.of(
            new ImpostorsScenario(),
            new ImpostorSharingScenario(),
            new ImpostorCrowdScenario(),
            new ImpostorEdgesScenario(),
            new ImpostorLongMobsScenario(),
            new ImpostorScreenScenario(),
            new LightingScenario(),
            new RememberedMobsScenario(),
            new OutlinesScenario(),
            new RestingScenario(),
            new RestingMountScenario(),
            new RestingImpostorsScenario(),
            new RestingLoopScenario(),
            new RestingCrowdScenario(),
            new RestingTouchScenario(),
            new ShoreScenario(),
            new OptionsScreenScenario(),
            new SodiumPagesScenario(),
            new SodiumControlsScenario(),
            new DepthViewScenario(),
            new EntityDepthScenario(),
            new ResizeScenario(),
            new UnloadScenario(),
            new FollowedMobsScenario(),
            new GaitsScenario(),
            new EasterEggsScenario(),
            new BirdsScenario(),
            new PerchersScenario(),
            new BirdCommandScenario(),
            new LanShareScenario(),
            new DedicatedServerScenario(),
            new PerfScenario());

    @Override
    public void runTest(ClientGameTestContext context) {
        String wanted = System.getProperty("livinghorizon.scenario", "all").trim().toLowerCase(Locale.ROOT);
        Set<String> names = Arrays.stream(wanted.split(",")).map(String::trim).collect(Collectors.toSet());
        boolean all = names.isEmpty() || names.contains("all") || names.contains("");
        Set<String> known = SCENARIOS.stream().map(Scenario::name).collect(Collectors.toSet());
        for (String name : names) {
            if (!all && !known.contains(name)) {
                throw new AssertionError("Unknown scenario '" + name + "', known: " + known);
            }
        }
        Scene.shaderPack(context);
        for (Scenario scenario : SCENARIOS) {
            if (!all && !names.contains(scenario.name())) continue;
            Scene.log("scenario " + scenario.name() + " starts");
            Scene.reset(context);
            scenario.run(context);
            Scene.log("scenario " + scenario.name() + " done");
        }
    }
}
