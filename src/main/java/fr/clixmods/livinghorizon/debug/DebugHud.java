package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.ModVersion;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.render.Occlusion;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.9
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The debug panel, top left, and the same lines in the F3 screen through the "Living Horizon"
 * debug option: what the mod costs, what became of every distant mob in the last frame (in the colours of their boxes), how Voxy is read and merged, how far
 * this server sends mobs, and which optimisations are on. Also draws the depth view.
 */
public final class DebugHud {
    private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA, SLOW = 0xFFFFFF55, ON = 0xFF55FF55, OFF = 0xFFFF5555;

    private DebugHud() {
    }

    public static void draw(GuiGraphics graphics) {
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        // From 1.21.9 the depth view is drawn when the GUI is (GuiRendererMixin): from 26.1 this
        // runs before the world is drawn, which would paint over it.
        //? if <1.21.9
        /*if (config.debugDepthView != 0) FarDepth.drawDebugView(config.debugDepthView);*/
        //? if >=1.20.2 {
        if (!config.debugHud || minecraft.player == null || minecraft.getDebugOverlay().showDebugScreen()) return;
        //?} else
        /*if (!config.debugHud || minecraft.player == null || minecraft.options.renderDebug) return;*/

        Font font = minecraft.font;
        int next = box(graphics, font, lines(config, minecraft), 4);
        // What each section costs, in a second column: the first is already tall.
        box(graphics, font, profileLines(), next + 8);
    }

    /** Draws lines in a box at {@code x}, top left; returns where the box ends. */
    private static int box(GuiGraphics graphics, Font font, List<Line> lines, int x) {
        int width = 0;
        for (Line line : lines) width = Math.max(width, font.width(line.text));
        int y = 4, height = lines.size() * (font.lineHeight + 1);
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 1, 0x90000000);
        for (Line line : lines) {
            graphics.drawString(font, line.text, x, y, line.color, true);
            y += font.lineHeight + 1;
        }
        return x + width + 2;
    }

    /** What each section of the mod costs: the last time, the average and the worst of five seconds. */
    private static List<Line> profileLines() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.profile"), WHITE));
        for (ProfileLines.Line line : ProfileLines.lines()) {
            lines.add(new Line(line.content(), line.severity() == 2 ? OFF : line.severity() == 1 ? SLOW : GREY));
        }
        return lines;
    }

    /** The panel's lines for the F3 screen, where the "Living Horizon" debug option shows them. */
    static List<String> f3Lines() {
        List<String> out = new ArrayList<>();
        for (Line line : lines(FarConfig.get(), Minecraft.getInstance())) {
            out.add(nearest(line.color) + line.text);
        }
        for (Line line : profileLines()) out.add(nearest(line.color) + line.text);
        return out;
    }

    //? if <1.21.9 {
    /*/^* Before 1.21.9 the F3 screen has no options a mod can add: the switch is kept here. ^/
    private static boolean entry;
    *///?}

    /** Whether the "Living Horizon" option is on in the F3 debug options (F3 + F6). */
    public static boolean entryEnabled() {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=1.21.9 {
        return minecraft != null && minecraft.debugEntries.isCurrentlyEnabled(DebugHudEntry.ID);
        //?} else {
        /*return entry;
        *///?}
    }

    /** Turns the F3 debug option on or off, as the player would in the debug options. */
    public static void setEntry(boolean on) {
        //? if >=1.21.9 {
        Minecraft.getInstance().debugEntries.setStatus(DebugHudEntry.ID,
                on ? DebugScreenEntryStatus.IN_OVERLAY : DebugScreenEntryStatus.NEVER);
        //?} else {
        /*entry = on;
        *///?}
    }

    /**
     * The sixteen legacy colours, in code order. Written out: ChatFormatting stopped carrying
     * them in 26.2, and they have not changed since the codes were introduced.
     */
    private static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
            0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** The legacy colour code closest to an ARGB colour: F3 lines are plain strings. */
    private static String nearest(int argb) {
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int code = 0; code < LEGACY.length; code++) {
            int rgb = LEGACY[code];
            long dr = ((argb >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            long dg = ((argb >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            long db = (argb & 0xFF) - (rgb & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = code;
            }
        }
        return "\u00a7" + Integer.toHexString(best);
    }

    private static List<Line> lines(FarConfig config, Minecraft minecraft) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.title") + " v" + ModVersion.get(), WHITE));
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.cost", Stats.tickMillis(), Stats.extractMillis(),
                Stats.drawn(), Stats.skipped()), GREY));
        MobMemory mobs = FarPlayerTracker.get().mobs();
        int waiting = 0, built = 0;
        for (MobMemory.Remembered mob : mobs.shown()) {
            if (mob.puppet() != null) built++;
            else if (mob.waiting()) waiting++;
        }
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.mobs", mobs.size(), mobs.shown().size(), built, waiting,
                mobs.live().size()), GREY));
        for (Mark mark : Mark.values()) {
            lines.add(new Line("■ " + mark.label() + " : " + DebugMarks.count(mark), mark.color));
        }
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.depth", FarDepth.state(),
                I18n.get(FarDepth.mergedLastFrame() ? "livinghorizon.debug.yes" : "livinghorizon.debug.no")), GREY));
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.reads", LodWorld.threads(), LodWorld.pending(),
                LodWorld.columnsKnown()), GREY));
        if (config.hideOccludedMobs) {
            lines.add(new Line(I18n.get("livinghorizon.debug.hud.hidden", Occlusion.hiddenCount()), GREY));
            lines.add(new Line(I18n.get("livinghorizon.debug.hud.queries", OcclusionQueries.state()), GREY));
        }
        StringBuilder reach = new StringBuilder();
        for (Map.Entry<MobCategory, Double> entry : mobs.reach().entrySet()) {
            if (!reach.isEmpty()) reach.append(", ");
            reach.append(entry.getKey().getName()).append(' ').append(Math.round(entry.getValue())).append(" m");
        }
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.reach", reach.isEmpty() ? "?" : reach), GREY));
        lines.add(new Line(I18n.get("livinghorizon.debug.hud.opts"), GREY));
        String[] names = {"view", "tiny", "depth", "lazy", "parallel", "cache", "occluded", "queries", "fade", "shadows", "fog", "dhfog", "background",
                "animation", "freeze"};
        boolean[] values = {config.optViewCulling, config.optTinyCulling, config.depthOcclusion, config.optLazyDepth,
                config.optParallelRead, config.optColumnCache, config.hideOccludedMobs, config.optOcclusionQueries,
                config.optDhFade, config.optVoxyShadows, config.optVoxyFog, config.optDhFog, config.optBackgroundBuild, config.optStillTiny, config.optFreezeHidden};
        for (int i = 0; i < names.length; i++) {
            lines.add(new Line("  " + (values[i] ? "✔ " : "✘ ") + I18n.get("livinghorizon.debug.opt." + names[i]),
                    values[i] ? ON : OFF));
        }
        if (config.debugDepthView != 0) {
            lines.add(new Line(I18n.get("livinghorizon.debug.hud.view",
                    I18n.get("livinghorizon.debug.depth." + config.debugDepthView)), GREY));
        }

        return lines;
    }

    private record Line(String text, int color) {
    }
}
