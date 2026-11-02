package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.FarFog;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;

import java.nio.file.Path;

/**
 * Distant Horizons' fog on the mobs: with Distant Horizons (`--with distanthorizons`), its far
 * fog made thick a hundred blocks out, the mobs there fade into it like its terrain, and come
 * away from it with <i>DH Fog Fix</i> off. Without Distant Horizons it says so and stops.
 */
public final class DhFogScenario implements Scenario {
    private static final String[] MOBS = {"cow", "horse", "villager", "wolf", "pig", "sheep"};

    @Override
    public String name() {
        return "dhfog";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!Platform.isModLoaded("distanthorizons")) {
            Scene.log("dhfog skipped: Distant Horizons is not installed (run.sh dhfog --with distanthorizons)");
            return;
        }
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(12);
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.onlyOverTerrain = false;
            config.optDhFog = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 1, 100, 3);
            context.waitTicks(100);
            // Its far fog linear and thick from the camera to a twentieth of its render distance.
            String set = context.computeOnClient(minecraft -> farFog("LINEAR", 0f, 0.05f));
            Scene.log("dhfog Distant Horizons' fog: " + set);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            context.waitTicks(60);
            Path fogged = Scene.screenshot(context, this, "fogged");
            Scene.log("dhfog status: " + context.computeOnClient(minecraft -> FarFog.status()));
            Scene.configure(context, config -> config.optDhFog = false);
            context.waitTicks(10);
            Path clear = Scene.screenshot(context, this, "clear");
            // The mobs come away from the fog with the fix off; nothing else changes.
            FarFogScenario.compare("Distant Horizons' fog", fogged, clear);
        } finally {
            context.runOnClient(minecraft -> {
                FarConfig.get().optDhFog = true;
                minecraft.options.fov().set(70);
                minecraft.options.entityDistanceScaling().set(1.0);
                minecraft.options.cloudStatus().set(CloudStatus.FANCY);
            });
        }
    }

    /** Sets Distant Horizons' far fog through its API; what it holds afterwards. */
    private static String farFog(String falloff, float start, float end) {
        String api = "com.seibel.distanthorizons.api.", config = api + "interfaces.config.";
        try {
            Object configs = Class.forName(api + "DhApi$Delayed").getField("configs").get(null);
            Object graphics = call(config + "IDhApiConfig", configs, "graphics");
            Object fog = call(config + "client.IDhApiGraphicsConfig", graphics, "fog");
            Object far = call(config + "client.IDhApiFogConfig", fog, "farFog");
            String farType = config + "client.IDhApiFarFogConfig";
            set(call(config + "client.IDhApiFogConfig", fog, "enableDhFog"), true);
            set(call(farType, far, "farFogStartDistance"), start);
            set(call(farType, far, "farFogEndDistance"), end);
            set(call(farType, far, "farFogMinThickness"), 0f);
            set(call(farType, far, "farFogMaxThickness"), 1f);
            Class<?> falloffs = Class.forName(api + "enums.rendering.EDhApiFogFalloff");
            set(call(farType, far, "farFogFalloff"), falloffs.getField(falloff).get(null));
            return "render distance " + get(call(config + "client.IDhApiGraphicsConfig", graphics, "chunkRenderDistance"))
                    + " chunks, " + get(call(farType, far, "farFogFalloff")) + " from " + get(call(farType, far, "farFogStartDistance"))
                    + " to " + get(call(farType, far, "farFogEndDistance"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not set Distant Horizons' fog", e);
        }
    }

    private static Object call(String type, Object on, String method) throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(on);
    }

    private static void set(Object setting, Object value) throws ReflectiveOperationException {
        Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue")
                .getMethod("setValue", Object.class).invoke(setting, value);
    }

    private static Object get(Object setting) throws ReflectiveOperationException {
        return Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue")
                .getMethod("getValue").invoke(setting);
    }
}
