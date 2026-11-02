package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What changed in each version of the mod, read in the game: the changelog the jar carries
 * ({@code assets/livinghorizon/changelog}), in the game's language when it is one of the two
 * the changelog is written in, English otherwise. Opened from the settings' header and from
 * Sodium's page.
 */
public final class ChangelogScreen extends Screen {
    private static final String KEY = "livinghorizon.changelog.";
    private static final int TOP = 32, MARGIN = 20, BAR_WIDTH = 6, TEXT = 0xFFFFFFFF, DIM = 0xFFA0A0A0;

    /** A line of the page: its place from the top of the text, and how far in it starts. */
    private record Row(FormattedCharSequence content, int x, int y, boolean bullet) {}

    private final @Nullable Screen parent;
    private List<Row> rows = List.of();
    private int contentHeight;
    private double scroll;
    private boolean dragging;

    public ChangelogScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, 20).build());
        layoutRows();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** The changelog written in the game's language, English when there is none. */
    private String read() {
        String language = minecraft.getLanguageManager().getSelected();
        String name = language.startsWith("fr") ? "fr" : "en";
        String text = resource(name);
        return text.isEmpty() && !name.equals("en") ? resource("en") : text;
    }

    private static String resource(String name) {
        try (InputStream in = ChangelogScreen.class.getResourceAsStream(
                "/assets/" + LivingHorizonClient.MOD_ID + "/changelog/" + name + ".md")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Cuts every line to the width, once per size of the window. */
    private void layoutRows() {
        List<Row> built = new ArrayList<>();
        int y = 0;
        int wrap = Math.max(100, width - 2 * MARGIN - BAR_WIDTH - 6);
        for (ChangelogText.Line line : ChangelogText.parse(read())) {
            int indent = switch (line.kind()) {
                case VERSION, PART -> 0;
                case KIND -> 8;
                case BULLET -> 20;
            };
            y += switch (line.kind()) {
                case VERSION -> built.isEmpty() ? 0 : 16;
                case PART -> 8;
                case KIND -> 5;
                case BULLET -> 3;
            };
            boolean first = true;
            for (FormattedCharSequence part : font.split(styled(line), wrap - indent)) {
                built.add(new Row(part, MARGIN + indent, y, first && line.kind() == ChangelogText.Kind.BULLET));
                y += font.lineHeight + 1;
                first = false;
            }
        }
        rows = built;
        contentHeight = y;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private static Component styled(ChangelogText.Line line) {
        ChatFormatting colour = switch (line.kind()) {
            case VERSION -> ChatFormatting.GOLD;
            case PART -> ChatFormatting.YELLOW;
            case KIND -> ChatFormatting.AQUA;
            case BULLET -> ChatFormatting.WHITE;
        };
        boolean heading = line.kind() != ChangelogText.Kind.BULLET;
        MutableComponent text = Component.empty();
        for (ChangelogText.Span span : ChangelogText.spans(line.content())) {
            text.append(Component.literal(span.content()).withStyle(Style.EMPTY
                    .withColor(span.bold() && !heading ? ChatFormatting.YELLOW : colour)
                    .withBold(heading || span.bold())
                    .withItalic(span.italic())));
        }
        return text;
    }

    private int bottom() {
        return height - 32;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bottom() - TOP));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Before 1.20.2 a screen draws its own background.
        //? if <1.20.2
        /*renderBackground(graphics);*/
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, TEXT);
        scroll = Mth.clamp(scroll, 0, maxScroll());

        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "empty"), width / 2, TOP + 20, DIM);
            return;
        }
        // In a world nothing darkens the game behind: a panel keeps the text readable.
        graphics.fill(0, TOP - 4, width, bottom() + 4, 0xB0000000);
        graphics.enableScissor(0, TOP, width, bottom());
        for (Row row : rows) {
            int y = TOP + row.y() - (int) scroll;
            if (y + font.lineHeight < TOP || y > bottom()) continue;
            if (row.bullet()) graphics.drawString(font, "•", row.x() - 8, y, DIM);
            graphics.drawString(font, row.content(), row.x(), y, TEXT);
        }
        graphics.disableScissor();
        drawScrollBar(graphics);
    }

    private int barHeight() {
        int track = bottom() - TOP;
        return Mth.clamp(track * track / Math.max(track, contentHeight), 20, track);
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

    @Override
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

    @Override
    //? if >=1.20.2 {
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    //?} else
    /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {*/
        scroll -= scrollY * 3 * (font.lineHeight + 1);
        return true;
    }
}
