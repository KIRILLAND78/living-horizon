package fr.clixmods.livinghorizon.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The game's own "Open link?" question for one of the {@link ProjectLink}s: the Discord server,
 * the store pages or the website. Opened from the mod's screen and from Sodium's page; back to
 * where it came from, on yes and on no.
 */
public final class LinkScreen extends ConfirmLinkScreen {
    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    private final @Nullable Screen parent;
    private final ProjectLink link;

    private LinkScreen(@Nullable Screen parent, ProjectLink link) {
        super(yes -> {
            if (yes) browse(link);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(parent);
        }, uri(link), true);
        this.parent = parent;
        this.link = link;
    }

    public static void open(@Nullable Screen from, ProjectLink link) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new LinkScreen(from, link));
    }

    public ProjectLink link() {
        return link;
    }

    /** The link as the game's screen takes it: from 26.3, a URI. */
    //? if >=26.3 {
    /*private static java.net.URI uri(ProjectLink link) {
        return java.net.URI.create(link.url);
    }
    *///?} else {
    private static String uri(ProjectLink link) {
        return link.url;
    }
    //?}

    /** Opens the link in the browser: from 26.3, through the GPU library's window code. */
    private static void browse(ProjectLink link) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(uri(link));
        *///?} else {
        Util.getPlatform().openUri(uri(link));
        //?}
    }

    private boolean overSodium() {
        return SodiumReturn.below(parent);
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && overSodium()) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (closeOnRelease && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            callback.accept(false);
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && overSodium()) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (closeOnRelease && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            callback.accept(false);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}
}
