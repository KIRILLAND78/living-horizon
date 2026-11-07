package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.ambient.BirdFigure;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.track.LastSeen;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonEvent;
//? if <1.21.2
//import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.2 && <1.21.6
//import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the impostors look like: every figure baked so far, one row each, its eight views
 * at full size and the front one at the sizes it has far away, in real screen pixels.
 * Beside the name, where the pictures come from: shared, read back from what an earlier game
 * saved; new, baked in this game and saved for the worlds and games to come; or not saved at
 * all (players, riders, birds). Then when and in which world they were baked. Three tabs, the
 * mobs', the other entities' (boats, vehicles, mannequins) and the players' (riders with what
 * they ride included), each with the video memory its pictures take and the most the settings
 * let them take; mobs and entities share theirs. A button throws
 * every picture away and bakes them again. Opened from the settings, or
 * {@code /livinghorizon impostors}.
 */
public final class ImpostorScreen extends Screen {
    private static final String KEY = "livinghorizon.impostors.";

    /** Screen pixels of the front view drawn small: a figure 250, 500 and 1000 blocks away. */
    private static final int[] FAR = {32, 16, 8};
    private static final int TOP = 70, NAME = 140, GAP = 2, LINE = 10;
    /** A sky behind the pictures, as they are seen in the world. */
    private static final int SKY = 0xFF9DB8E8, NAME_COLOR = 0xFFFFFFFF, DIM = 0xFFA0A0A0, OVER = 0xFFFF6060;
    /** Where the pictures come from: shared from disk, new and saved, not saved. */
    private static final int SHARED_COLOR = 0xFF60E060, SAVED_COLOR = 0xFFFFE060;

    /** The pictures a tab shows. */
    public enum Tab { MOBS, ENTITIES, PLAYERS }

    /** The tab shown. Kept while the game runs. */
    private static Tab tab = Tab.MOBS;
    private final java.util.EnumMap<Tab, Button> tabs = new java.util.EnumMap<>(Tab.class);

    private final @Nullable Screen parent;
    private List<ImpostorAtlas.Baked> rows = List.of();
    private double scroll;
    private boolean dragging;
    private static final int BAR_WIDTH = 6;
    /**
     * By name, then by key: the atlas hands its figures out in order of last use, which the
     * world changes every frame, so two figures with one name would swap rows every frame.
     */
    private static final Comparator<ImpostorAtlas.Baked> ORDER = Comparator
            .comparing((ImpostorAtlas.Baked row) -> name(row).getString().toLowerCase(Locale.ROOT))
            .thenComparing(row -> row.key().type())
            .thenComparingLong(row -> row.key().appearance());

    public ImpostorScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 152;
        for (Tab each : Tab.values()) {
            String name = each.name().toLowerCase(Locale.ROOT);
            tabs.put(each, addRenderableWidget(Button.builder(Component.translatable(KEY + "tab." + name), b -> show(each))
                    .bounds(x, 20, 100, 20).build()));
            x += 102;
        }
        show(tab);
        int y = height - 26;
        addRenderableWidget(Button.builder(Component.translatable(KEY + "regenerate"), b -> {
                    ImpostorAtlas.regenerate();
                    scroll = 0;
                })
                .tooltip(Tooltip.create(Component.translatable(KEY + "regenerate.tooltip")))
                .bounds(width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 4, y, 150, 20).build());
    }

    /** Shows one tab, its button pressed down. */
    private void show(Tab shown) {
        if (tab != shown) scroll = 0;
        tab = shown;
        tabs.forEach((each, button) -> button.active = each != shown);
    }

    /**
     * The tab a picture is under: a player's (or a rider's), a mob's or a bird's, or one of the
     * other entities, which the list of mobs puts under its own Entities tab.
     */
    public static Tab tabOf(ImpostorKey key) {
        if (ImpostorAtlas.player(key)) return Tab.PLAYERS;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(key.type()))
                .filter(type -> !MobKinds.creature(type)).map(type -> Tab.ENTITIES).orElse(Tab.MOBS);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && SodiumReturn.below(parent)) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (closeOnRelease && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            onClose();
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && SodiumReturn.below(parent)) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (closeOnRelease && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}

    /** Pixels of a view in a row: as large as fits, never larger than the picture itself. */
    private int tile() {
        int room = width - 20 - NAME - farWidth() - 8;
        return Mth.clamp(room / ImpostorViews.VIEWS - GAP, 16, ImpostorViews.TILE);
    }

    private int farWidth() {
        int sum = 0;
        for (int size : FAR) sum += Mth.ceil(size / (float) minecraft.getWindow().getGuiScale()) + GAP;
        return sum;
    }

    /** As tall as a view, or as the four lines beside it: name, origin, date, world. */
    private int rowHeight() {
        return Math.max(tile(), 4 * LINE) + 6;
    }

    private int bottom() {
        return height - 32;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Before 1.20.2 a screen draws its own background.
        //? if <1.20.2
        /*renderBackground(graphics);*/
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, NAME_COLOR);

        // A copy to sort: before 1.21.9 the atlas hands out List.of(), which refuses sort.
        rows = new ArrayList<>(ImpostorAtlas.baked());
        rows.removeIf(row -> tabOf(row.key()) != tab);
        boolean players = tab == Tab.PLAYERS;
        boolean over = ImpostorAtlas.memory(players) > ImpostorAtlas.memoryLimit(players);
        graphics.drawCenteredString(font, status(rows.size()), width / 2, 44, over ? OVER : DIM);
        if (!rows.isEmpty()) graphics.drawCenteredString(font, origins(rows), width / 2, 56, DIM);
        rows.sort(ORDER);
        scroll = Mth.clamp(scroll, 0, maxScroll());

        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty"), width / 2, TOP + 20, DIM);
            return;
        }
        int tile = tile(), left = 10, scale = (int) minecraft.getWindow().getGuiScale();
        graphics.enableScissor(0, TOP, width, bottom());
        int y = TOP - (int) scroll;
        for (ImpostorAtlas.Baked row : rows) {
            if (y + rowHeight() > TOP && y < bottom()) drawRow(graphics, row, left, y, tile, scale);
            y += rowHeight();
        }
        graphics.disableScissor();
        drawScrollBar(graphics);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * rowHeight() - (bottom() - TOP));
    }

    private int barHeight() {
        int track = bottom() - TOP;
        return Mth.clamp(track * track / Math.max(track, rows.size() * rowHeight()), 20, track);
    }

    private int barTop() {
        int free = bottom() - TOP - barHeight();
        return TOP + (maxScroll() == 0 ? 0 : (int) (free * scroll / maxScroll()));
    }

    private void drawScrollBar(GuiGraphics graphics) {
        if (maxScroll() == 0) return;
        int x = width - BAR_WIDTH - 2;
        graphics.fill(x, TOP, x + BAR_WIDTH, bottom(), 0x80000000);
        graphics.fill(x, barTop(), x + BAR_WIDTH, barTop() + barHeight(), dragging ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    /** Puts the bar's middle at the pointer, so that a click on the track jumps there and a drag follows. */
    private void scrollTo(double mouseY) {
        int free = bottom() - TOP - barHeight();
        if (free <= 0) return;
        scroll = Mth.clamp((mouseY - TOP - barHeight() / 2.0) / free, 0, 1) * maxScroll();
    }

    private boolean onBar(double mouseX, double mouseY) {
        return maxScroll() > 0 && mouseX >= width - BAR_WIDTH - 4 && mouseY >= TOP && mouseY < bottom();
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && onBar(event.x(), event.y())) {
            dragging = true;
            scrollTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && onBar(mouseX, mouseY)) {
            dragging = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    *///?}

    private void drawRow(GuiGraphics graphics, ImpostorAtlas.Baked row, int x, int y, int tile, int scale) {
        drawLabel(graphics, row, x, y, tile);
        x += NAME;
        ImpostorAtlas.Sheet sheet = row.sheet();
        Identifier page = ImpostorAtlas.id(sheet.page());
        for (int view = 0; view < ImpostorViews.VIEWS; view++) {
            graphics.fill(x, y, x + tile, y + tile, SKY);
            blitView(graphics, page, sheet, view, x, y, tile);
            x += tile + GAP;
        }
        x += 8;
        // Real screen pixels, whatever the GUI scale: drawn scaled down from GUI units.
        for (int size : FAR) {
            int room = Mth.ceil(size / (float) scale);
            graphics.fill(x, y, x + room, y + room, SKY);
            // The GUI's pose is a 2D one from 1.21.6.
            //? if >=1.21.6 {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(1f / scale);
            blitView(graphics, page, sheet, 0, 0, 0, size);
            graphics.pose().popMatrix();
            //?} else {
            /*graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0f);
            graphics.pose().scale(1f / scale, 1f / scale, 1f);
            blitView(graphics, page, sheet, 0, 0, 0, size);
            graphics.pose().popPose();
            *///?}
            x += room + GAP;
        }
    }

    /** The name, and where, when and in which world its pictures were made, beside its views. */
    private void drawLabel(GuiGraphics graphics, ImpostorAtlas.Baked row, int x, int y, int tile) {
        ImpostorAtlas.Origin origin = row.origin();
        List<Component> lines = new ArrayList<>(4);
        lines.add(name(row));
        if (origin != null) {
            lines.add(Component.translatable(KEY + "origin." + origin.source().name().toLowerCase(Locale.ROOT)));
            String date = LastSeen.format(origin.bakedAt(), minecraft.getLanguageManager().getSelected(),
                    ZoneId.systemDefault(), FormatStyle.SHORT);
            lines.add(date == null ? Component.translatable(KEY + "baked.unknown") : Component.literal(date));
            if (!origin.world().isEmpty()) lines.add(Component.literal(origin.world()));
        }
        int top = y + Math.max(0, (tile - lines.size() * LINE) / 2);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? NAME_COLOR : i == 1 ? color(origin.source()) : DIM;
            String text = font.plainSubstrByWidth(lines.get(i).getString(), NAME - 6);
            graphics.drawString(font, text, x, top + i * LINE, color);
        }
    }

    private static int color(ImpostorAtlas.Source source) {
        return switch (source) {
            case DISK -> SHARED_COLOR;
            case SAVED -> SAVED_COLOR;
            case TEMPORARY -> DIM;
        };
    }

    /** How many of the tab's pictures come from disk, were baked and saved in this game, are not saved. */
    private static Component origins(List<ImpostorAtlas.Baked> rows) {
        Map<ImpostorAtlas.Source, Integer> counts = new EnumMap<>(ImpostorAtlas.Source.class);
        for (ImpostorAtlas.Source source : ImpostorAtlas.Source.values()) counts.put(source, 0);
        for (ImpostorAtlas.Baked row : rows) {
            if (row.origin() != null) counts.merge(row.origin().source(), 1, Integer::sum);
        }
        return Component.translatable(KEY + "origins", counts.get(ImpostorAtlas.Source.DISK),
                counts.get(ImpostorAtlas.Source.SAVED), counts.get(ImpostorAtlas.Source.TEMPORARY));
    }

    private static void blitView(GuiGraphics graphics, Identifier page, ImpostorAtlas.Sheet sheet, int view, int x, int y,
                                 int size) {
        //? if >=1.21.6 {
        float u0 = sheet.u(view), v0 = sheet.v();
        graphics.blit(page, x, y, x + size, y + size, u0, u0 + sheet.uSpan(), v0, v0 + sheet.vSpan());
        //?} else {
        /*// In pixels of the page before 1.21.6.
        int tile = sheet.tile(), wide = sheet.pageWidth(), tall = sheet.pageHeight();
        int u = view * tile, v = sheet.row() * tile;
        //? if >=1.21.2 {
        graphics.blit(RenderTypes::guiTextured, page, x, y, u, v, size, size, tile, tile, wide, tall);
        //?} else {
        /^// Blended, as the newer versions draw it: the pictures' edges are see-through.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(page, x, y, size, size, u, v, tile, tile, wide, tall);
        RenderSystem.disableBlend();
        ^///?}
        *///?}
    }

    private static Component name(ImpostorAtlas.Baked row) {
        String type = row.key().type();
        // A 3D bird: one sheet per pose and height, named after its species.
        if (type.startsWith(BirdFigure.KEY)) {
            return Component.translatable(KEY + "bird." + type.substring(BirdFigure.KEY.length()));
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(type)).map(EntityType::getDescription)
                .orElse(Component.literal(type));
    }

    /**
     * The video memory the tab's pictures take, out of the most they may take (red past it,
     * when the setting was lowered below what is in use: pictures give way as new ones come),
     * how many there are, and how many of all wait to be made.
     */
    private Component status(int pictures) {
        FarConfig config = FarConfig.get();
        if (!config.anyImpostors()) return Component.translatable(KEY + "off");
        if (ImpostorAtlas.failed()) return Component.translatable("livinghorizon.debug.f3.impostorsFailed");
        boolean players = tab == Tab.PLAYERS;
        return Component.translatable(KEY + "memory", megabytes(ImpostorAtlas.memory(players)),
                megabytes(ImpostorAtlas.memoryLimit(players)), pictures, ImpostorAtlas.waiting());
    }

    /** Bytes as megabytes, a decimal below ten: "4.7 MB", "512 MB". */
    private static Component megabytes(long bytes) {
        double value = bytes / (1024.0 * 1024.0);
        String text = value < 10 ? String.format(Locale.ROOT, "%.1f", value) : String.valueOf(Math.round(value));
        return Component.translatable("livinghorizon.options.megabytes", text);
    }

    @Override
    //? if >=1.20.2 {
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    //?} else
    /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {*/
        scroll -= scrollY * rowHeight();
        return true;
    }
}
