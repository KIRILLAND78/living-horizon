package fr.clixmods.livinghorizon.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * A slider of whole numbers, which the arrow keys move one step, ten with Shift. Where the right
 * end means no limit, it also takes a typed value: a right click turns it into a text field of
 * digits only (Enter keeps the value, Escape or a click elsewhere leaves it), and any number can be
 * typed, past the slider's last one too, which the slider shows at that last place. A slider with
 * bounds takes no typing: its ends are what the mod can do. No limit itself is only the right end,
 * and with {@link Range#warns} the player is asked before it is kept, however it was reached:
 * dragged or stepped onto.
 *
 * <p>Built by the game's own option list through {@link Range}, the values of an
 * {@link OptionInstance}, so that the screens add it as any other option. The screen around it
 * hands it the right click and the keys while it is typed in: {@link SideOptionsScreen}.
 */
public final class NumberSlider extends AbstractSliderButton {
    private static final int INVALID = 0xFFFF5555;
    private static final int VALID = 0xFFE0E0E0;
    /** No limit, as the slider's values hold it: never a number a player types. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * The slider the mouse left on no limit, asked about once the button is released, wherever it
     * is: before 1.21 the game hands the release only to the widget under the cursor, and a slider
     * dragged past its end is released beside it.
     */
    private static @Nullable NumberSlider releasing;

    /** Counted by the screen at every frame: a slider not drawn in this one is scrolled out of view. */
    private static int frame;

    private final OptionInstance<Integer> option;
    private final Range range;
    private final OptionInstance.TooltipSupplier<Integer> tooltip;
    private final IntConsumer changed;
    private @Nullable EditBox field;
    private int drawnIn = -1;
    /** A typed number past the slider's last one, shown at that last place until the slider moves. */
    private @Nullable Integer beyond;

    private NumberSlider(int x, int y, int width, OptionInstance<Integer> option, Range range,
                         OptionInstance.TooltipSupplier<Integer> tooltip, IntConsumer changed) {
        super(x, y, width, 20, Component.empty(), range.toSlider(option.get()));
        this.option = option;
        this.range = range;
        this.tooltip = tooltip;
        this.changed = changed;
        show(option.get());
    }

    /**
     * The settings whose right end means no limit, here and on Sodium's page: every one asks
     * first, since none is safe without a limit. How many mobs, entities and players who logged
     * off are kept and drawn, and how far players, mobs, their animations and entities are drawn
     * grow with the world, and the frame rate and the memory with them, up to a crash.
     */
    public static boolean asksBeforeUnlimited(String setting) {
        return switch (setting) {
            case "maxDistantMobs", "maxDistantEntities", "maxOfflinePlayers",
                 "playerMaxDistance", "mobMaxDistance", "animationMaxDistance", "entityMaxDistance" -> true;
            default -> false;
        };
    }

    static void nextFrame() {
        frame++;
    }

    /** Whether a value can be typed in: only where the right end is no limit, never within bounds. */
    public boolean typable() {
        return range.unlimited;
    }

    /** Whether it was drawn in this frame: one scrolled out of the list keeps an old place. */
    public boolean shown() {
        return drawnIn == frame && visible && active;
    }

    int current() {
        if (beyond != null && value == range.toSlider(beyond)) return beyond;
        return range.fromSlider(value);
    }

    @Override
    protected void updateMessage() {
        int v = current();
        setMessage(range.label.toString(range.caption, v));
        setTooltip(tooltip.apply(v));
    }

    /** Dragged or clicked to the right end: shown, but kept only once the player says yes, on release. */
    @Override
    protected void applyValue() {
        int v = current();
        if (asks(v)) {
            releasing = this;
            return;
        }
        if (releasing == this) releasing = null;
        keep(v);
    }

    private boolean asks(int v) {
        return range.warns && v == UNLIMITED && option.get() != UNLIMITED;
    }

    private void keep(int v) {
        option.set(v);
        changed.accept(option.get());
    }

    private void show(int v) {
        value = range.toSlider(v);
        beyond = range.beyond(v) ? v : null;
        updateMessage();
    }

    /** Set to {@code v}, through the question when it is no limit. */
    void put(int v) {
        show(v);
        if (asks(v)) ask(v);
        else keep(v);
    }

    /** The player says whether no limit is what they want; no puts the slider back. */
    private void ask(int v) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen back = minecraft.screen;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) keep(v);
            show(option.get());
            minecraft.setScreen(back);
        }, Component.translatable("livinghorizon.options.unlimited", range.caption),
                Component.translatable("livinghorizon.options.unlimited.confirm")));
    }

    /**
     * One step along, from where it was before the game's own step: ten with Shift. Past the last
     * number the right arrow goes to no limit, and the left one comes back from it to that number;
     * a typed number past it steps on from there.
     */
    private void step(int from, boolean right, boolean shift) {
        int by = shift ? 10 : 1;
        int v;
        if (from == UNLIMITED) v = right ? UNLIMITED : range.max;
        else if (range.beyond(from)) v = right ? (int) Math.min((long) from + by, UNLIMITED - 1) : Math.max(from - by, range.max);
        else if (right && range.unlimited && from + by > range.max) v = from == range.max ? UNLIMITED : range.max;
        else v = Math.clamp(from + (right ? by : -by), range.min, range.max);
        put(v);
    }

    // ---- Typing -------------------------------------------------------------------------------

    @Nullable EditBox field() {
        return field;
    }

    /** What its field holds while it is typed in; null otherwise. */
    public @Nullable String typedText() {
        return field == null ? null : field.getValue();
    }

    /**
     * Turns into a text field holding the value, all of it selected so that typing replaces it; no
     * limit shows as the slider's last number. Digits only: anything else is taken out as typed or
     * pasted.
     */
    void startTyping() {
        Minecraft minecraft = Minecraft.getInstance();
        EditBox box = new EditBox(minecraft.font, getX(), getY(), getWidth(), getHeight(), getMessage());
        box.setMaxLength(Range.DIGITS);
        int v = current();
        box.setValue(String.valueOf(v == UNLIMITED ? range.max : v));
        box.setResponder(text -> {
            String digits = text.replaceAll("[^0-9]", "");
            if (!digits.equals(text)) {
                box.setValue(digits);
                return;
            }
            box.setTextColor(range.parse(text) == null ? INVALID : VALID);
        });
        box.setTextColor(VALID);
        box.setCursorPosition(box.getValue().length());
        box.setHighlightPos(0);
        box.setFocused(true);
        field = box;
    }

    /** Whether what is typed reads as a value: Enter keeps the field open on anything else. */
    boolean typedIsValid() {
        return field != null && range.parse(field.getValue()) != null;
    }

    /** Back to a slider; with {@code keep}, set to what was typed, when it reads as a value. */
    void stopTyping(boolean keep) {
        EditBox box = field;
        field = null;
        if (!keep || box == null) return;
        Integer v = range.parse(box.getValue());
        if (v != null) put(v);
    }

    // ---- Drawing and input, as each version of the game names them -----------------------------

    //? if >=26.1 {
    /*@Override
    public void extractWidgetRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawnIn = frame;
        if (field == null) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        place(field);
        field.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    *///?} else {
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawnIn = frame;
        if (field == null) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        place(field);
        field.render(graphics, mouseX, mouseY, partialTick);
    }
    //?}

    /** The field follows the slider, which the list moves as it scrolls. */
    private void place(EditBox box) {
        box.setX(getX());
        box.setY(getY());
        box.setWidth(getWidth());
    }

    //? if >=1.21.9 {
    @Override
    public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
        super.onRelease(event);
        released();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Shift as held now too: an event made by hand may not carry it.
        return arrows(event.key(), event.hasShiftDown() || Minecraft.getInstance().hasShiftDown(), () -> super.keyPressed(event));
    }
    //?} else {
    /*@Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        released();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return arrows(keyCode, (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0,
                () -> super.keyPressed(keyCode, scanCode, modifiers));
    }
    *///?}

    private void released() {
        afterRelease();
    }

    /** The mouse button released, on a slider or anywhere on the screen: no limit, dragged to, is asked about. */
    static void afterRelease() {
        NumberSlider slider = releasing;
        releasing = null;
        if (slider == null) return;
        int v = slider.current();
        if (slider.asks(v)) slider.ask(v);
    }

    /**
     * The game moves a slider by one pixel per arrow, many values on a wide range and none on a
     * short one: when it moved, the move is made one value instead.
     */
    private boolean arrows(int key, boolean shift, java.util.function.BooleanSupplier game) {
        double before = value;
        int from = current();
        boolean done = game.getAsBoolean();
        boolean arrow = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT;
        if (arrow && value != before) step(from, key == InputConstants.KEY_RIGHT, shift);
        return done;
    }

    /**
     * The values of a {@link NumberSlider}: whole numbers from {@code min} to {@code max} along the
     * slider, read as {@code label} says. With {@code unlimited}, one more place at the right end
     * is no limit ({@link #UNLIMITED}), and a number past {@code max} can be typed; with
     * {@code warns}, the player is asked before no limit is kept.
     */
    public static final class Range implements OptionInstance.ValueSet<Integer> {
        /** The longest number the field takes: a billion, short of {@link #UNLIMITED}. */
        static final int DIGITS = 9;

        final Component caption;
        final int min, max;
        final OptionInstance.CaptionBasedToString<Integer> label;
        final boolean unlimited;
        final boolean warns;

        public Range(Component caption, int min, int max, OptionInstance.CaptionBasedToString<Integer> label,
                     boolean unlimited, boolean warns) {
            this.caption = caption;
            this.min = min;
            this.max = max;
            this.label = label;
            this.unlimited = unlimited;
            this.warns = warns && unlimited;
        }

        private int places() {
            return max - min + 1 + (unlimited ? 1 : 0);
        }

        /** A typed number past the slider's last one, which only a slider with no limit keeps. */
        boolean beyond(int v) {
            return unlimited && v > max && v != UNLIMITED;
        }

        double toSlider(int v) {
            int place = unlimited && v == UNLIMITED ? places() - 1 : Math.clamp(v, min, max) - min;
            return (place + 0.5) / places();
        }

        int fromSlider(double slider) {
            int place = Math.clamp((int) Math.floor(slider * places()), 0, places() - 1);
            return unlimited && place == places() - 1 ? UNLIMITED : min + place;
        }

        /**
         * What is typed as a value: digits only, brought up to {@code min}, and down to {@code max}
         * where there is no limit to go to; past it, kept as typed. Null for anything else.
         */
        public @Nullable Integer parse(String typed) {
            if (typed.isEmpty() || typed.length() > DIGITS || !typed.chars().allMatch(c -> c >= '0' && c <= '9')) return null;
            int v = Integer.parseInt(typed);
            return Math.clamp(v, min, unlimited ? UNLIMITED - 1 : max);
        }

        //? if >=26.2 {
        /*@Override
        public Function<OptionInstance<Integer>, AbstractWidget> createButton(OptionInstance.TooltipSupplier<Integer> tooltip,
                Options options, int x, int y, int width, OptionInstance.ValueUpdateListener<? super Integer> changed) {
            return option -> new NumberSlider(x, y, width, option, this, tooltip, changed::valueChanged);
        }
        *///?} else {
        @Override
        public Function<OptionInstance<Integer>, AbstractWidget> createButton(OptionInstance.TooltipSupplier<Integer> tooltip,
                Options options, int x, int y, int width, java.util.function.Consumer<Integer> changed) {
            return option -> new NumberSlider(x, y, width, option, this, tooltip, changed::accept);
        }
        //?}

        @Override
        public Optional<Integer> validateValue(Integer value) {
            return value >= min && (value <= max || unlimited) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public Codec<Integer> codec() {
            return Codec.INT;
        }
    }
}
