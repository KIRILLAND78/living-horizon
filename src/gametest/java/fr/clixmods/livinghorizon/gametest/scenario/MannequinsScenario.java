package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if >=1.21.9
import net.minecraft.client.entity.ClientAvatarEntity;
//? if >=1.21.9
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
//? if >=1.21.9
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Mannequins with skins, left behind until the mod draws its copies of them: each copy must
 * wear its mannequin's skin, never the default one, and keep its cape, hung from where it
 * stands. Two take a texture of the game's through their profile, as a server's resource
 * pack gives one, the first with a cape; a third a player's name, whose skin comes from
 * Mojang when the network allows (logged, not checked).
 *
 * <p>The server sends mannequins as far as its view distance, which follows the render
 * distance here: their copies stand past the fog, out of sight without Voxy. The check is on
 * the copies' skins and capes, and on the impostor page, where the first copy's picture must
 * show its cape: a gold block stands in for one, since the game ships no cape texture, so its
 * pixels are counted on the copy's back. The screenshots up close show the skins from the
 * front and the cape from behind. Mannequins came in 1.21.9: nothing to do before.
 */
public final class MannequinsScenario implements Scenario {
    /** A texture of the game's for each skinned mannequin, by its place across the row. */
    private static final String[] TEXTURES = {"minecraft:entity/player/wide/kai", "minecraft:entity/player/slim/efe"};
    /** What the first mannequin wears as a cape: a texture of the game's that is one plain colour. */
    private static final String CAPE = "minecraft:block/gold_block";

    @Override
    public String name() {
        return "mannequins";
    }

    @Override
    public void run(ClientGameTestContext context) {
        //? if >=1.21.9 {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(12));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 100;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a figure that vanishes closer is taken as gone for good.
            summon(world, -4, "{texture:\"" + TEXTURES[0] + "\",cape:\"" + CAPE + "\"}");
            summon(world, 0, "{texture:\"" + TEXTURES[1] + "\",model:\"slim\"}");
            summon(world, 4, "\"Notch\"");
            context.waitTicks(100);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "near");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            // Their backs, where the cape hangs, from 12 blocks behind them.
            Scene.command(world, "tp @a 0 ~ 52 180 0");
            context.waitTicks(40);
            Scene.screenshot(context, this, "behind");
            // Back where they stood 40 blocks off: a mob gone within 32 blocks is taken as killed.
            Scene.command(world, "tp @a 0 ~ 0 0 0");
            context.waitTicks(40);

            // Past the 192 blocks the server sends them at: the copies take over.
            Scene.command(world, "tp @a 0 ~ -220 0 0");
            context.waitTicks(300);
            ImpostorKey[] caped = new ImpostorKey[1];
            String failure = context.computeOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                Scene.log("mobs still sent by the server: " + memory.live().size());
                int checked = 0;
                StringBuilder wrong = new StringBuilder();
                StringBuilder capes = new StringBuilder();
                for (MobMemory.Remembered mob : memory.shown()) {
                    if (!mob.type().equals("minecraft:mannequin")) continue;
                    Entity puppet = mob.puppet();
                    if (!(puppet instanceof ClientAvatarEntity avatar)) {
                        Scene.log("mannequin copy at " + mob.x() + " not built yet");
                        continue;
                    }
                    String skin = String.valueOf(avatar.getSkin().body().texturePath());
                    Scene.log("mannequin copy at x=" + Math.round(puppet.getX()) + " wears " + skin + " cape="
                            + avatar.getSkin().cape() + " shown=" + ((Mannequin) puppet).isModelPartShown(PlayerModelPart.CAPE));
                    // Where its cape hangs from: left at 0, 0, 0, the cape reaches out towards there.
                    // Both ends, since a model is drawn between them.
                    ClientAvatarState state = avatar.avatarState();
                    double cape = 0;
                    for (float partial : new float[]{0f, 1f}) {
                        cape += Math.abs(state.getInterpolatedCloakX(partial) - Mth.lerp(partial, puppet.xo, puppet.getX()))
                                + Math.abs(state.getInterpolatedCloakY(partial) - Mth.lerp(partial, puppet.yo, puppet.getY()))
                                + Math.abs(state.getInterpolatedCloakZ(partial) - Mth.lerp(partial, puppet.zo, puppet.getZ()));
                    }
                    if (cape > 0.01) {
                        capes.append(" x=").append(Math.round(puppet.getX())).append(String.format(" %.2f blocks off", cape));
                    }
                    int place = (int) Math.round(puppet.getX() / 4) + 1;
                    if (place < 0 || place >= TEXTURES.length) continue;
                    checked++;
                    String expected = TEXTURES[place].substring("minecraft:".length());
                    if (!skin.contains(expected)) wrong.append(" x=").append(Math.round(puppet.getX())).append(" wears ").append(skin);
                    if (place == 0) {
                        if (avatar.getSkin().cape() == null) wrong.append(" x=-4 without its cape");
                        caped[0] = ImpostorKey.of(puppet);
                    }
                }
                if (checked < TEXTURES.length) return "only " + checked + " textured mannequin copies were shown";
                if (!wrong.isEmpty()) return "mannequin copies without their skin or cape:" + wrong;
                return capes.isEmpty() ? null : "mannequin copies with their cape away from them:" + capes;
            });
            context.runOnClient(minecraft -> Scene.log("mannequins impostor sheets=" + ImpostorAtlas.sheets()));
            Path atlas = Scene.screenshotAtlas(context, this);
            if (failure != null) throw new AssertionError(failure);
            // The cape in the picture: gold pixels in the caped copy's sheet, which its back views show.
            int[] rect = context.computeOnClient(minecraft -> Scene.atlasRect(caped[0]));
            if (rect == null) throw new AssertionError("the caped mannequin copy has no impostor sheet");
            int gold = goldPixels(atlas, rect);
            Scene.log("mannequins cape pixels in the sheet: " + gold);
            if (gold == 0) throw new AssertionError("the caped mannequin copy's picture shows no cape");
        }
        //?} else
        /*Scene.log("mannequins: none before 1.21.9");*/
    }

    //? if >=1.21.9 {
    /** A mannequin 40 blocks ahead of the player and {@code x} across, facing them, with this profile. */
    private static void summon(TestSingleplayerContext world, double x, String profile) {
        Scene.command(world, "execute at @p run summon minecraft:mannequin ~" + x + " ~ ~40 {Rotation:[180f,0f],profile:"
                + profile + "}");
    }

    /** Pixels of the gold block's yellow inside this part of a screenshot: left, top, width, height. */
    private static int goldPixels(Path screenshot, int[] rect) {
        BufferedImage image;
        try {
            image = ImageIO.read(screenshot.toFile());
        } catch (IOException e) {
            throw new AssertionError("could not read " + screenshot, e);
        }
        int gold = 0;
        for (int y = rect[1]; y < Math.min(image.getHeight(), rect[1] + rect[3]); y++) {
            for (int x = rect[0]; x < Math.min(image.getWidth(), rect[0] + rect[2]); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, blue = rgb & 0xFF;
                if (red > 200 && green > 170 && blue < 120) gold++;
            }
        }
        return gold;
    }
    //?}
}
