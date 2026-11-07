package fr.clixmods.livinghorizon.gametest;

import fr.clixmods.livinghorizon.gametest.scenario.BenchmarkScenario;
import fr.clixmods.livinghorizon.gametest.scenario.BenchmarkWorldScenario;
import fr.clixmods.livinghorizon.gametest.scenario.BirdCommandScenario;
import fr.clixmods.livinghorizon.gametest.scenario.BobbyScenario;
import fr.clixmods.livinghorizon.gametest.scenario.BirdsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.DedicatedServerScenario;
import fr.clixmods.livinghorizon.gametest.scenario.DepthViewScenario;
import fr.clixmods.livinghorizon.gametest.scenario.DistancePreviewScenario;
import fr.clixmods.livinghorizon.gametest.scenario.EasterEggsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.EggFiguresScenario;
import fr.clixmods.livinghorizon.gametest.scenario.EntityDepthScenario;
import fr.clixmods.livinghorizon.gametest.scenario.DhFogScenario;
import fr.clixmods.livinghorizon.gametest.scenario.FarFogScenario;
import fr.clixmods.livinghorizon.gametest.scenario.FollowedMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.FlyersScenario;
import fr.clixmods.livinghorizon.gametest.scenario.GaitsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.StillMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.GlidersScenario;
import fr.clixmods.livinghorizon.gametest.scenario.HeadTurnScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorCrowdScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorEdgesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorLongMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorScreenScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorSharingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorSizeScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorTurnScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.LanShareScenario;
import fr.clixmods.livinghorizon.gametest.scenario.MannequinsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.MoreEggsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.LightingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.PhotonVoxyScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ImpostorShadingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OcclusionScenario;
import fr.clixmods.livinghorizon.gametest.scenario.LinksScenario;
import fr.clixmods.livinghorizon.gametest.scenario.LodistanceScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OptionsScreenScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OutlinesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OverVoidScenario;
import fr.clixmods.livinghorizon.gametest.scenario.PerchersScenario;
import fr.clixmods.livinghorizon.gametest.scenario.PerfScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RememberedMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ResizeScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestKeptScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingCrowdScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingImpostorsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingLoopScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingMountScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingSocialScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RestingTouchScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SeedCrowdScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SeedMobsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ServerWithoutModScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ShipsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.ShoreScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SittingPetsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SodiumControlsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.TypedValuesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.GuessedGroundScenario;
import fr.clixmods.livinghorizon.gametest.scenario.SodiumPagesScenario;
import fr.clixmods.livinghorizon.gametest.scenario.UnloadScenario;
import fr.clixmods.livinghorizon.gametest.scenario.VehiclesScenario;
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
            new ImpostorSizeScenario(),
            new ImpostorSharingScenario(),
            new ImpostorCrowdScenario(),
            new ImpostorEdgesScenario(),
            new ImpostorLongMobsScenario(),
            new ImpostorScreenScenario(),
            new ImpostorTurnScenario(),
            new LightingScenario(),
            new PhotonVoxyScenario(),
            new ImpostorShadingScenario(),
            new FarFogScenario(),
            new DhFogScenario(),
            new RememberedMobsScenario(),
            new SeedMobsScenario(),
            new SeedCrowdScenario(),
            new OverVoidScenario(),
            new MannequinsScenario(),
            new VehiclesScenario(),
            new ShipsScenario(),
            new GlidersScenario(),
            new OutlinesScenario(),
            new RestingScenario(),
            new RestingMountScenario(),
            new RestingImpostorsScenario(),
            new RestingLoopScenario(),
            new RestingCrowdScenario(),
            new RestingTouchScenario(),
            new RestingSocialScenario(),
            new ShoreScenario(),
            new RestKeptScenario(),
            new OptionsScreenScenario(),
            new DistancePreviewScenario(),
            new LinksScenario(),
            new SodiumPagesScenario(),
            new SodiumControlsScenario(),
            new TypedValuesScenario(),
            new GuessedGroundScenario(),
            new DepthViewScenario(),
            new EntityDepthScenario(),
            new ResizeScenario(),
            new OcclusionScenario(),
            new LodistanceScenario(),
            new UnloadScenario(),
            new FollowedMobsScenario(),
            new BobbyScenario(),
            new SittingPetsScenario(),
            new HeadTurnScenario(),
            new GaitsScenario(),
            new StillMobsScenario(),
            new FlyersScenario(),
            new EasterEggsScenario(),
            new MoreEggsScenario(),
            new EggFiguresScenario(),
            new BirdsScenario(),
            new PerchersScenario(),
            new BirdCommandScenario(),
            new LanShareScenario(),
            new DedicatedServerScenario(),
            new ServerWithoutModScenario(),
            new PerfScenario(),
            new BenchmarkScenario(),
            new BenchmarkWorldScenario());

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
