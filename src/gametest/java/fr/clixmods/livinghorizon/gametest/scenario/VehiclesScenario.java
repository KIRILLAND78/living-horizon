package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.ui.MobList;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import fr.clixmods.livinghorizon.ui.MobTypesScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Vehicles the list of mobs cannot name, met in the world. A minecart, which only seats its
 * rider, is listed but not shown until chosen; that part, and the list's two tabs, need
 * nothing else.
 *
 * <p>With Immersive Aircraft ({@code run.sh vehicles --with immersive-aircraft}), its biplane
 * and airship, which their pilot steers, are listed and shown before one is met. Left behind
 * until the server no longer sends them, their copies stand where they were, past the mobs'
 * distance (the entities' own reaches farther), as pictures with Simplified Entities. With the
 * list open over the paused game, switching the airship off and on shows at once. A player left
 * in the biplane is pictured with it from above, as the biplane is. Without the mod, that part
 * is skipped.
 */
public final class VehiclesScenario implements Scenario {
    private static final String MINECART = "minecraft:minecart";
    private static final String[] AIRCRAFT = {"immersive_aircraft:biplane", "immersive_aircraft:airship"};

    @Override
    public String name() {
        return "vehicles";
    }

    @Override
    public void run(ClientGameTestContext context) {
        boolean aircraft = FabricLoader.getInstance().isModLoaded("immersive_aircraft");
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        // From the title screen, before a world: from 26.1 the items have no components yet there.
        boolean bare = context.computeOnClient(minecraft -> {
            try {
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PIG_SPAWN_EGG);
                return false;
            } catch (RuntimeException unbound) {
                return true;
            }
        });
        Scene.log("vehicles: items without components on the title screen " + bare);
        context.setScreen(() -> new MobTypesScreen(null));
        context.waitTicks(2);
        boolean titleList = context.computeOnClient(minecraft -> minecraft.screen instanceof MobTypesScreen);
        context.setScreen(() -> null);
        if (!titleList) throw new AssertionError("vehicles: the list of mobs did not open from the title screen");
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            if (aircraft) {
                // Listed, and shown, before one is ever met: their renderers say what they draw.
                String unmet = context.computeOnClient(minecraft -> {
                    List<String> list = MobList.all().stream().map(type -> EntityType.getKey(type).toString()).toList();
                    FarConfig.adoptNewMobs(MobList.all());
                    StringBuilder wrong = new StringBuilder();
                    for (String id : AIRCRAFT) {
                        if (!list.contains(id)) wrong.append(' ').append(id).append(" is not listed before it is met;");
                        if (!FarConfig.get().mobTypes.contains(id)) wrong.append(' ').append(id).append(" is not shown before it is met;");
                    }
                    return wrong.toString();
                });
                if (!unmet.isEmpty()) throw new AssertionError("vehicles:" + unmet);
            }
            // Past 32 blocks: a mob that vanishes closer is taken as gone for good.
            Scene.summon(world, "minecart", 0, 36, 0);
            if (aircraft) {
                Scene.summon(world, AIRCRAFT[0], -8, 40, 135);
                Scene.summon(world, AIRCRAFT[1], 10, 44, 90);
            }
            context.waitTicks(100);
            String listed = context.computeOnClient(minecraft -> {
                FarConfig config = FarConfig.get();
                Scene.log("known vehicles: " + config.knownVehicles);
                StringBuilder wrong = new StringBuilder();
                if (!config.knownVehicles.contains(MINECART)) wrong.append(" the minecart was not found a vehicle;");
                if (config.mobTypes.contains(MINECART)) wrong.append(" the minecart is shown without being chosen;");
                if (config.knownVehicles.stream().anyMatch(id -> id.endsWith("_boat") || id.endsWith("_raft"))) {
                    wrong.append(" a boat was taken for a new vehicle;");
                }
                List<String> list = MobList.all().stream().map(type -> EntityType.getKey(type).toString()).toList();
                if (!list.contains(MINECART)) wrong.append(" the minecart is not in the list of mobs;");
                // As the game starts, and the Defaults button: the minecart stays off all the same.
                FarConfig.adoptNewMobs(MobList.all());
                if (config.mobTypes.contains(MINECART)) wrong.append(" the minecart is shown once the game starts again;");
                config.resetMobs(MobList.all());
                if (config.mobTypes.contains(MINECART)) wrong.append(" the minecart is shown after Defaults;");
                if (aircraft) {
                    for (String id : AIRCRAFT) {
                        if (!config.knownVehicles.contains(id)) wrong.append(' ').append(id).append(" was not found a vehicle;");
                        if (!config.mobTypes.contains(id)) wrong.append(' ').append(id).append(" is not shown from the start;");
                        if (!list.contains(id)) wrong.append(' ').append(id).append(" is not in the list of mobs;");
                    }
                }
                return wrong.toString();
            });
            if (!listed.isEmpty()) throw new AssertionError("vehicles:" + listed);
            tabs(context, aircraft);
            if (!aircraft) {
                Scene.log("vehicles: Immersive Aircraft not loaded, only the minecart was checked (run with --with immersive-aircraft)");
                return;
            }
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "near");

            // Walk away: the copies take over once the server no longer sends the aircraft.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            String built = context.computeOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                StringBuilder missing = new StringBuilder();
                for (String id : AIRCRAFT) {
                    boolean live = memory.live().stream().anyMatch(entity -> EntityType.getKey(entity.getType()).toString().equals(id));
                    MobMemory.Remembered copy = memory.shown().stream().filter(mob -> mob.type().equals(id)).findFirst().orElse(null);
                    Entity puppet = copy == null ? null : copy.puppet();
                    Scene.log(id + ": sent by the server " + live + ", copy "
                            + (puppet == null ? "none" : "at " + puppet.position() + " facing " + puppet.getYRot()));
                    if (puppet == null) missing.append(' ').append(id);
                }
                return missing.toString();
            });
            if (!built.isEmpty()) throw new AssertionError("vehicles: no copy of" + built);
            // The mobs' distance stops short of them: the entities' own, unlimited, still reaches.
            Scene.configure(context, config -> config.mobMaxDistance = 100);
            Path pictures = Scene.screenshot(context, this, "remembered");
            int impostors = context.computeOnClient(minecraft -> PolygonStats.impostors());
            // From 80 blocks up: pictures baked from above, as a ghast's are.
            // On a barrier, invisible: a fall from there kills.
            Scene.command(world, "fill -1 19 -161 1 19 -159 minecraft:barrier");
            Scene.command(world, "tp @a 0 20 -160 0 22");
            context.waitTicks(60);
            Path abovePictures = Scene.screenshot(context, this, "from-above");
            int above = context.computeOnClient(minecraft -> PolygonStats.impostors());
            Scene.screenshotAtlas(context, this);
            preview(context);
            // As much of them shows as of their models: a picture turned up to the camera round
            // their middle once sank its lower half into the ground they stand on.
            Scene.configure(context, config -> config.impostorEntities = false);
            Path aboveModels = Scene.screenshot(context, this, "from-above-models");
            Scene.configure(context, config -> config.distantEntities = false);
            Path aboveNothing = Scene.screenshot(context, this, "from-above-nothing");
            Scene.configure(context, config -> {
                config.distantEntities = true;
                config.impostorEntities = true;
            });
            int pictured = differing(abovePictures, aboveNothing, 0), modelled = differing(aboveModels, aboveNothing, 0);
            Scene.command(world, "tp @a 0 -60 -160 0 0");
            context.waitTicks(20);
            Scene.log("vehicles: " + above + " drawn as pictures from above, " + pictured + " pixels as pictures, "
                    + modelled + " as models");
            if (above < AIRCRAFT.length) throw new AssertionError("vehicles: the aircraft are not pictures from above");
            if (pictured < modelled * 0.6) {
                throw new AssertionError("vehicles: the pictures from above show " + pictured + " pixels, the models "
                        + modelled + ": sunk into the ground");
            }
            Scene.configure(context, config -> config.impostorEntities = false);
            Scene.screenshot(context, this, "remembered-models");
            Scene.log("vehicles: " + impostors + " drawn as pictures with Simplified Entities");
            if (impostors < AIRCRAFT.length) throw new AssertionError("vehicles: the aircraft are not pictures past the simplification distance");
            menu(context);
            // Show Entities off: the aircraft go, whatever the mobs do.
            Path on = Scene.screenshot(context, this, "entities-on");
            Scene.configure(context, config -> config.distantEntities = false);
            Path off = Scene.screenshot(context, this, "entities-off");
            int gone = differing(on, off, 0);
            Scene.log("vehicles: Show Entities off changes " + gone + " pixels");
            if (gone < 50) throw new AssertionError("vehicles: Show Entities off left the aircraft drawn (" + gone + " pixels)");
            pilot(context, world);
            fleet(context, world);
            aloft(context, world);
        }
    }

    /**
     * Test players left in the air on what they flew, 30 blocks up: in a biplane going, it flies
     * round in circles at its height; in an airship standing still, it hovers there. Neither
     * falls to the ground.
     */
    private void aloft(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.configure(context, config -> {
            config.offlinePlayers = true;
            config.offlineLife = true;
            config.impostorPlayers = false;
        });
        leftAloft(context, world, "Circler", AIRCRAFT[0], 0.5, "{Motion:[0.0d,0.0d,0.8d],Rotation:[0f,0f]}");
        leftAloft(context, world, "Hoverer", AIRCRAFT[1], 40.5, "{Rotation:[0f,0f]}");
        Scene.command(world, "tp @a 20.5 -50 -110.5 0 -15");
        context.waitTicks(40);
        Vec3[] before = context.computeOnClient(minecraft -> new Vec3[]{mountAt(minecraft, "Circler"), mountAt(minecraft, "Hoverer")});
        context.waitTicks(40);
        Vec3[] after = context.computeOnClient(minecraft -> new Vec3[]{mountAt(minecraft, "Circler"), mountAt(minecraft, "Hoverer")});
        Scene.screenshot(context, this, "aloft");
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
        context.waitTicks(20);
        Scene.log("vehicles: left aloft, the biplane from " + before[0] + " to " + after[0] + ", the airship from "
                + before[1] + " to " + after[1]);
        StringBuilder wrong = new StringBuilder();
        if (before[0] == null || after[0] == null || before[1] == null || after[1] == null) {
            throw new AssertionError("vehicles: a test player left in the air has no aircraft under them");
        }
        if (before[0].distanceTo(after[0]) < 4) wrong.append(" the biplane does not fly on;");
        if (Math.abs(after[0].y - before[0].y) > 0.5 || after[0].y < -50) wrong.append(" the biplane does not keep its height;");
        if (Math.hypot(after[0].x - 0.5, after[0].z + 30.5) > 100) {
            wrong.append(" the biplane flies off instead of round;");
        }
        if (before[1].distanceTo(after[1]) > 0.5) wrong.append(" the airship moves instead of hovering;");
        if (after[1].y < -50) wrong.append(" the airship came down;");
        if (!wrong.isEmpty()) throw new AssertionError("vehicles:" + wrong);
    }

    /** Rides a new {@code type} 30 blocks up at x, leaves test player {@code name} on it there, and takes it away. */
    private static void leftAloft(ClientGameTestContext context, TestSingleplayerContext world, String name, String type,
                                  double x, String nbt) {
        Scene.command(world, "tp @a " + x + " -30 -34.5 0 0");
        Scene.command(world, "summon " + type + " " + x + " -30 -30.5 " + nbt);
        // Nearest to the player: the console's nearest is the parked one by the world's spawn.
        Scene.command(world, "execute as @p at @s run ride @s mount @e[type=" + type + ",limit=1,sort=nearest]");
        context.waitTicks(3);
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test spawn " + name));
        context.waitTicks(3);
        Scene.command(world, "ride @p dismount");
        Scene.command(world, "execute at @p run kill @e[type=" + type + ",limit=1,sort=nearest]");
        context.waitTicks(5);
    }

    /** Where the aircraft under test player {@code name} is; null when they have none. */
    private static @org.jspecify.annotations.Nullable Vec3 mountAt(net.minecraft.client.Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (!spot.name().equals(name) || minecraft.level == null) continue;
            RestingPuppet rider = resting.puppet(minecraft.level, spot);
            Entity mount = rider == null ? null : rider.mount();
            return mount == null ? null : mount.position();
        }
        return null;
    }

    /**
     * Forty test players in biplanes, seen from far past the render distance, where the client
     * has no light: each plane is extracted lit, as a renderer that keeps its own copy of the
     * light reads it (Immersive Aircraft's, from 26.1). At 0 it drew the plane black, as a model
     * and in its picture.
     */
    private void fleet(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.configure(context, config -> {
            config.impostorPlayers = true;
            config.impostorEntities = true;
        });
        Scene.command(world, "tp @a 0.5 -60 -14.5 0 0");
        context.waitTicks(10);
        Scene.command(world, "summon " + AIRCRAFT[0] + " 0.5 -60 -10.5 {Rotation:[90f,0f]}");
        context.waitTicks(10);
        Scene.command(world, "ride @p mount @e[type=" + AIRCRAFT[0] + ",limit=1,sort=nearest]");
        context.waitTicks(20);
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test spawn Fleet 60 40 100"));
        context.waitTicks(5);
        Scene.command(world, "ride @p dismount");
        Scene.command(world, "kill @e[type=" + AIRCRAFT[0] + ",limit=1,sort=nearest]");
        // Floating, as a player flying over them.
        Scene.command(world, "gamemode spectator @a");
        Scene.command(world, "tp @a 0.5 30 -200.5 0 30");
        context.waitTicks(200);
        Scene.screenshot(context, this, "fleet");
        // Away until their chunks are gone, then straight back among them: as each chunk comes
        // in, the client has no light in it for a few frames, and reads 0 there.
        Scene.command(world, "tp @a 0.5 30 -700.5 0 30");
        context.waitTicks(100);
        Scene.command(world, "tp @a 0.5 -40 -10.5 0 30");
        int planes = 0, dark = 0, unlitSeen = 0;
        for (int tick = 0; tick < 60; tick++) {
            int[] counts = context.computeOnClient(minecraft -> {
                RestingPlayers resting = FarPlayerTracker.get().resting();
                int seen = 0, black = 0, unlit = 0;
                for (RestingPlayers.Spot spot : resting.spots()) {
                    if (!spot.name().startsWith("Fleet") || minecraft.level == null) continue;
                    RestingPuppet rider = resting.puppet(minecraft.level, spot);
                    Entity mount = rider == null ? null : rider.mount();
                    if (mount == null) continue;
                    net.minecraft.core.BlockPos at = mount.blockPosition().above();
                    if (minecraft.level.hasChunkAt(at)
                            && minecraft.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at) == 0) {
                        unlit++;
                    }
                    int light = keptLight(GhostRenderer.extract(minecraft.getEntityRenderDispatcher(), mount, 1f));
                    if (light < 0) continue;
                    seen++;
                    if ((light >> 20 & 15) < 15) black++;
                }
                return new int[]{seen, black, unlit};
            });
            planes = Math.max(planes, counts[0]);
            dark = Math.max(dark, counts[1]);
            unlitSeen = Math.max(unlitSeen, counts[2]);
            context.waitTicks(1);
        }
        Scene.log("vehicles: fleet " + planes + " planes extracted as their chunks came in, up to " + unlitSeen
                + " in chunks without light yet, up to " + dark + " extracted without the sky's light");
        // Before 1.21.9 the light is handed over as a figure is drawn: no state keeps one.
        String unlit = dark == 0 ? "" : " up to " + dark + " of " + planes
                + " planes extracted without light as their chunks came in: drawn black";
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
        Scene.command(world, "gamemode creative @a");
        // The command's answer home before the world closes: the test framework counts every packet.
        context.waitTicks(20);
        if (!unlit.isEmpty()) throw new AssertionError("vehicles:" + unlit);
    }

    /**
     * The light a renderer keeps in its state: Immersive Aircraft's own {@code light} field from
     * 26.1, the game's {@code lightCoords} from 1.21.9; -1 when the state keeps none.
     */
    private static int keptLight(net.minecraft.client.renderer.entity.state.@org.jspecify.annotations.Nullable EntityRenderState state) {
        if (state == null) return -1;
        for (String name : new String[]{"light", "lightCoords"}) {
            for (Class<?> type = state.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(name);
                    if (field.getType() != int.class) continue;
                    field.setAccessible(true);
                    return field.getInt(state);
                } catch (NoSuchFieldException ignored) {
                    // Not on this class: its parent's, or the next name.
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return -1;
    }


    /** Picture Preview: the aircraft under its Entities tab, never under the mobs' or the players'. */
    private void preview(ClientGameTestContext context) {
        String wrong = context.computeOnClient(minecraft -> {
            StringBuilder out = new StringBuilder();
            int aircraft = 0;
            for (ImpostorAtlas.Baked row : ImpostorAtlas.baked()) {
                String type = row.key().type();
                if (!type.startsWith("immersive_aircraft:")) continue;
                aircraft++;
                ImpostorScreen.Tab tab = ImpostorScreen.tabOf(row.key());
                if (tab != ImpostorScreen.Tab.ENTITIES) out.append(' ').append(type).append(" is under ").append(tab).append(';');
            }
            if (aircraft == 0) out.append(" no picture of the aircraft;");
            return out.toString();
        });
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(2));
        context.setScreen(() -> new ImpostorScreen(null));
        context.clickScreenButton("livinghorizon.impostors.tab.entities");
        context.getInput().setCursorPos(4, 4);
        Scene.screenshot(context, this, "preview-entities");
        context.clickScreenButton("livinghorizon.impostors.tab.mobs");
        context.setScreen(() -> null);
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(1));
        if (!wrong.isEmpty()) throw new AssertionError("vehicles: Picture Preview:" + wrong);
    }

    /**
     * A test player left in a biplane, seen from above far away: pictured with it from the height
     * the biplane is seen from, never level with it as a rider on a horse is.
     */
    private void pilot(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.configure(context, config -> {
            config.distantEntities = true;
            config.impostorEntities = true;
            config.impostorPlayers = true;
        });
        Scene.command(world, "tp @a 0.5 -60 -14.5 0 0");
        context.waitTicks(10);
        Scene.command(world, "summon " + AIRCRAFT[0] + " 0.5 -60 -10.5 {Rotation:[90f,0f]}");
        context.waitTicks(10);
        Scene.command(world, "ride @p mount @e[type=" + AIRCRAFT[0] + ",limit=1,sort=nearest]");
        context.waitTicks(20);
        if (!context.computeOnClient(minecraft -> minecraft.player.isPassenger())) {
            throw new AssertionError("vehicles: the camera could not ride the biplane");
        }
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test spawn Pilot"));
        context.waitTicks(5);
        Scene.command(world, "ride @p dismount");
        Scene.command(world, "kill @e[type=" + AIRCRAFT[0] + ",limit=1,sort=nearest]");
        // 150 blocks off and 80 up, on the barrier, where the terrain is drawn already.
        Scene.command(world, "tp @a 0.5 20 -159.5 0 28");
        String why = null;
        for (int i = 0; i < 30 && !"".equals(why); i++) {
            context.waitTicks(10);
            why = context.computeOnClient(minecraft -> {
                RestingPlayers resting = FarPlayerTracker.get().resting();
                RestingPuppet rider = null;
                for (RestingPlayers.Spot spot : resting.spots()) {
                    if (spot.name().equals("Pilot") && minecraft.level != null) rider = resting.puppet(minecraft.level, spot);
                }
                if (rider == null) return "no test player";
                Entity mount = rider.mount();
                if (mount == null) return "the pilot is on nothing";
                Vec3 seat = rider.position().subtract(mount.position()).yRot((float) Math.toRadians(mount.getYRot()));
                ImpostorKey key = ImpostorKey.riding(rider, mount, seat, Mth.wrapDegrees(rider.yBodyRot - mount.getYRot()));
                if (ImpostorAtlas.sheet(key) != null) return "the pilot is pictured level with the biplane";
                for (int height = 0; height < ImpostorViews.HEIGHTS.length; height++) {
                    if (ImpostorAtlas.sheet(ImpostorKey.seenFrom(key, height)) != null) return "";
                }
                return "no picture of the pilot from a height (waiting " + ImpostorAtlas.waiting() + ")";
            });
        }
        Scene.log("vehicles: pilot " + (why.isEmpty() ? "pictured from a height" : why));
        context.waitTicks(60);
        Scene.screenshot(context, this, "pilot-above");
        Scene.configure(context, config -> config.impostorPlayers = false);
        Scene.screenshot(context, this, "pilot-above-models");
        Scene.screenshotAtlas(context, this);
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
        if (!why.isEmpty()) throw new AssertionError("vehicles: " + why);
    }

    /**
     * The list of mobs open over the game, paused under it: switching the airship off and on
     * shows at once behind the list, on the right of the screen where the list leaves the view.
     */
    private void menu(ClientGameTestContext context) {
        context.setScreen(() -> new MobTypesScreen(null));
        context.clickScreenButton("livinghorizon.options.mobTypes.tab.entities");
        context.getInput().typeChars("airship");
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(3);
        Path before = Scene.screenshot(context, this, "menu-shown");
        double[] at = context.computeOnClient(minecraft -> {
            int[] middle = minecraft.screen instanceof MobTypesScreen types ? types.switchAt(AIRCRAFT[1]) : null;
            double scale = minecraft.getWindow().getGuiScale();
            int column = minecraft.screen instanceof MobTypesScreen types ? types.columnWidth() : 0;
            return middle == null ? null : new double[]{middle[0] * scale, middle[1] * scale, column * scale};
        });
        if (at == null) throw new AssertionError("vehicles: the airship's switch is not on screen");
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(0);
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(2);
        Path off = Scene.screenshot(context, this, "menu-hidden");
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(0);
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(2);
        Path on = Scene.screenshot(context, this, "menu-shown-again");
        context.setScreen(() -> null);
        int gone = differing(before, off, (int) at[2]), back = differing(before, on, (int) at[2]);
        Scene.log("vehicles: switching the airship off changes " + gone + " pixels behind the list, back on " + back);
        if (gone < 50) throw new AssertionError("vehicles: the airship did not leave at once (" + gone + " pixels)");
        if (back > gone / 4) throw new AssertionError("vehicles: the airship did not come back at once (" + back + " pixels off)");
    }

    /**
     * The list of mobs, on each of its tabs: the vehicles met under the entities', with the boats,
     * never under the mobs'. Larger, to be read on the screenshots.
     */
    private void tabs(ClientGameTestContext context, boolean aircraft) {
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(2));
        context.setScreen(() -> new MobTypesScreen(null));
        // Off the list, where no tooltip covers it.
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(5);
        java.util.Set<String> mobs = context.computeOnClient(minecraft -> listed(minecraft.screen));
        String cow = context.computeOnClient(minecraft -> switchText(minecraft.screen, "minecraft:cow"));
        Scene.screenshot(context, this, "tab-mobs");
        context.clickScreenButton("livinghorizon.options.mobTypes.tab.entities");
        // Over the entities' tab, which says what it holds.
        double[] tab = context.computeOnClient(minecraft -> {
            double scale = minecraft.getWindow().getGuiScale();
            int middle = minecraft.screen instanceof MobTypesScreen types ? types.columnWidth() / 2 : 180;
            return new double[]{(middle + 52) * scale, 36 * scale};
        });
        context.getInput().setCursorPos(tab[0], tab[1]);
        context.waitTicks(5);
        java.util.Set<String> entities = context.computeOnClient(minecraft -> listed(minecraft.screen));
        String boat = context.computeOnClient(minecraft -> switchText(minecraft.screen, "minecraft:oak_boat"));
        Scene.screenshot(context, this, "tab-entities");
        // The search on this tab: the minecart, off since nobody steers it.
        context.getInput().setCursorPos(4, 4);
        context.getInput().typeChars("cart");
        context.waitTicks(5);
        String minecart = context.computeOnClient(minecraft -> switchText(minecraft.screen, MINECART));
        Scene.screenshot(context, this, "tab-entities-search");
        context.setScreen(() -> new MobTypesScreen(null));
        context.clickScreenButton("livinghorizon.options.mobTypes.tab.mobs");
        // The search, on the tab back in front: each letter opens the screen again.
        context.getInput().typeChars("cow");
        context.waitTicks(5);
        java.util.Set<String> searched = context.computeOnClient(minecraft -> listed(minecraft.screen));
        context.setScreen(() -> null);
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(1));
        Scene.log("vehicles: mobs tab " + mobs.size() + " kinds, entities tab " + entities);
        StringBuilder wrong = new StringBuilder();
        List<String> expected = new java.util.ArrayList<>(List.of(MINECART, "minecraft:oak_boat"));
        if (aircraft) expected.addAll(List.of(AIRCRAFT));
        for (String id : expected) {
            if (!entities.contains(id)) wrong.append(' ').append(id).append(" is not under the entities' tab;");
            if (mobs.contains(id)) wrong.append(' ').append(id).append(" is under the mobs' tab;");
        }
        if (!mobs.contains("minecraft:cow")) wrong.append(" the cow is not under the mobs' tab;");
        // A vehicle is shown or hidden; a mob roams or stands still.
        String shown = net.minecraft.client.resources.language.I18n.get("livinghorizon.options.mobTypes.mode.shown");
        String hidden = net.minecraft.client.resources.language.I18n.get("livinghorizon.options.mobTypes.mode.hidden");
        String roaming = net.minecraft.client.resources.language.I18n.get("livinghorizon.options.mobTypes.mode.animated");
        Scene.log("vehicles: switches '" + cow + "', '" + boat + "', '" + minecart + "'");
        if (!cow.endsWith(roaming)) wrong.append(" the cow's switch reads '").append(cow).append("';");
        if (!boat.endsWith(shown)) wrong.append(" the boat's switch reads '").append(boat).append("';");
        if (!minecart.endsWith(hidden)) wrong.append(" the minecart's switch reads '").append(minecart).append("';");
        if (entities.contains("minecraft:cow")) wrong.append(" the cow is under the entities' tab;");
        Scene.log("vehicles: searching cow lists " + searched);
        if (!searched.contains("minecraft:cow") || searched.size() > 4) wrong.append(" the search for cow lists ").append(searched).append(';');
        if (!wrong.isEmpty()) throw new AssertionError("vehicles:" + wrong);
    }

    private static String switchText(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen instanceof MobTypesScreen types ? types.switchText(id) : "";
    }

    private static java.util.Set<String> listed(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof MobTypesScreen types ? new java.util.TreeSet<>(types.listedKinds()) : java.util.Set.of();
    }

    /** Pixels that tell two screenshots apart, right of {@code fromX}. */
    private static int differing(Path a, Path b, int fromX) {
        try {
            BufferedImage one = ImageIO.read(a.toFile()), two = ImageIO.read(b.toFile());
            int count = 0;
            for (int y = 0; y < Math.min(one.getHeight(), two.getHeight()); y++) {
                for (int x = fromX; x < Math.min(one.getWidth(), two.getWidth()); x++) {
                    int p = one.getRGB(x, y), q = two.getRGB(x, y);
                    int d = Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255))
                            + Math.abs((p & 255) - (q & 255));
                    if (d > 40) count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
