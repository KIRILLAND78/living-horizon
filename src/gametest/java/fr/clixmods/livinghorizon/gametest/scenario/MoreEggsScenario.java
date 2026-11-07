package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg10;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg11;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg12;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg13;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg15;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg16;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg17;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg18;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg19;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg21;
import fr.clixmods.livinghorizon.gametest.scenario.eggs.Egg9;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The easter eggs from 9 on, each called the way {@code /livinghorizon eeN} calls it, one after
 * the other, each in a place of its own of a flat world (x = N × 1000). Screenshots of each,
 * and checks that they come and go when they should. {@code -Dlivinghorizon.eggs=9,12} runs
 * only those.
 */
public final class MoreEggsScenario implements Scenario {
    @Override
    public String name() {
        return "eggs2";
    }

    /** One easter egg's part of the scenario. */
    private interface Part {
        void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario);
    }

    /** The chunks round the player received, out to 220 blocks: a chunk not received yet is empty to the bottom of the world. */
    private static boolean loadedAround(Minecraft minecraft) {
        double x = minecraft.player.getX(), z = minecraft.player.getZ();
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            int bx = Mth.floor(x - Math.sin(a) * 220), bz = Mth.floor(z + Math.cos(a) * 220);
            if (!minecraft.level.hasChunk(bx >> 4, bz >> 4)
                    || minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) <= minecraft.level.getMinY()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.easterEggs = true;
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        String wanted = System.getProperty("livinghorizon.eggs", "").trim().toLowerCase(Locale.ROOT);
        Set<String> only = wanted.isEmpty() ? Set.of()
                : Arrays.stream(wanted.split(",")).map(String::trim).collect(Collectors.toSet());
        // By number: 14 and 20 are no more.
        int[] numbers = {9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 21};
        Part[] parts = {Egg9::run, Egg10::run, Egg11::run, Egg12::run, Egg13::run, Egg15::run,
                Egg16::run, Egg17::run, Egg18::run, Egg19::run, Egg21::run};
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.runOnClient(minecraft -> {
                minecraft.options.entityDistanceScaling().set(0.5);
                minecraft.options.renderDistance().set(16);
            });
            context.waitTicks(60);
            for (int i = 0; i < parts.length; i++) {
                int number = numbers[i];
                if (!only.isEmpty() && !only.contains(String.valueOf(number))) continue;
                Scene.log("eggs2 ee" + number);
                // Its place first, loaded all round: most of them come far off, out to where the chunks end.
                Scene.command(world, "tp @a " + number * 1000 + " -60 0 0 0");
                for (int t = 0; t < 1500 && !context.computeOnClient(MoreEggsScenario::loadedAround); t++) context.waitTick();
                parts[i].run(context, world, this);
                Scene.configure(context, config -> config.easterEggs = false);
                context.waitTicks(2);
                Scene.configure(context, config -> config.easterEggs = true);
                Scene.command(world, "time set noon");
                Scene.command(world, "weather clear");
                context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            }
        }
    }
}
