package fr.clixmods.livinghorizon.ui;

import net.minecraft.client.gui.GuiGraphics;
//? if <1.21.2
//import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.2 && <1.21.6
//import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** The pictures the mod's screens draw: 16 by 16, drawn at their own size. */
final class Icons {
    static final int SIZE = 16;

    private Icons() {}

    /** A 16 by 16 picture, its top left corner at {@code x}, {@code y}. */
    static void draw(GuiGraphics graphics, Identifier icon, int x, int y) {
        //? if >=1.21.6 {
        graphics.blit(icon, x, y, x + SIZE, y + SIZE, 0f, 1f, 0f, 1f);
        //?} elif >=1.21.2 {
        /*graphics.blit(RenderTypes::guiTextured, icon, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE, SIZE, SIZE);
        *///?} else {
        /*// Blended, as the newer versions draw it: the icons' edges may be see-through.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(icon, x, y, SIZE, SIZE, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
        *///?}
    }

    /** A sheet of text lines, its top left corner at {@code x}, {@code y}: drawn, not a texture. */
    static void changelog(GuiGraphics graphics, int x, int y) {
        int paper = 0xFFF2F2F2;
        int ink = 0xFF8A8A8A;
        graphics.fill(x + 2, y, x + 14, y + SIZE, paper);
        for (int row = 0; row < 4; row++) {
            int top = y + 3 + row * 3;
            graphics.fill(x + 4, top, x + (row == 3 ? 9 : 12), top + 1, ink);
        }
    }
}
