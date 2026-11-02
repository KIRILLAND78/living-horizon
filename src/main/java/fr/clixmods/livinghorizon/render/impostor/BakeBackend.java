package fr.clixmods.livinghorizon.render.impostor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;

/**
 * How {@link ImpostorAtlas} draws the views of a figure into a texture, reads them back and
 * writes a sheet into a page, which the game changed twice. One file per range of versions,
 * each wrapped whole in a Stonecutter guard, as {@code render/state/} is:
 * <ul>
 *   <li>{@code OverrideBake}, from 1.21.6: the game's GPU device, pointed at our target with
 *       its output overrides, in the GUI pass; read back a frame or two later.</li>
 *   <li>{@code DeviceBake}, 1.21.5: the device without overrides, our target standing in for
 *       the main one while a figure is drawn; read back a frame or two later.</li>
 *   <li>{@code GlBake}, before 1.21.5: plain OpenGL, the eight views side by side in our
 *       target, standing in for the main one, read back at once.</li>
 * </ul>
 * What every backend shares (which row, the queue, staging a figure, what is done with the
 * pictures) stays in {@link ImpostorAtlas}.
 */
interface BakeBackend {
    /** What comes of a figure's views: handed over at once, or frames later once read back. */
    interface ReadBack {
        /** Whether the pictures are still wanted: false once the pages they were for are thrown away. */
        boolean wanted();

        /**
         * The views, bottom row first, {@code render * render} pixels each; null when they
         * could not be read, with why, or are no longer wanted.
         */
        void done(int @Nullable [][] pictures, @Nullable Throwable error);
    }

    /** A new page texture with {@link ImpostorAtlas#MIPS} levels, empty at each. */
    AbstractTexture create(String label, int width, int height);

    /**
     * Draws a figure's {@link ImpostorViews#VIEWS} views, each {@code request.render()} pixels
     * square and {@code size} blocks across, and hands them to {@code readBack}, now or frames
     * later. Throws when nothing was sent: {@code readBack} is then never called.
     */
    void drawViews(Minecraft minecraft, ImpostorAtlas.Request request, double size, ReadBack readBack);

    /** Writes a strip of pixels into one level of a page, top row first. */
    void write(AbstractTexture page, int level, int x, int y, int width, int height, int[] pixels);

    /** The backend of this game version. */
    static BakeBackend of() {
        //? if >=1.21.6 {
        return new OverrideBake();
        //?} elif >=1.21.5 {
        /*return new DeviceBake();
        *///?} else {
        /*return new GlBake();
        *///?}
    }
}
