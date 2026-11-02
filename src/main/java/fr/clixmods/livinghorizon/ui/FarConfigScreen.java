package fr.clixmods.livinghorizon.ui;

import com.mojang.serialization.Codec;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.render.DistancePreview.Shape;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Foresight;
import fr.clixmods.livinghorizon.track.MobScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The settings, in game: the same list of buttons and sliders as the game's own option
 * screens. Opened from Mod Menu's "Configure" button, the settings key, or
 * {@code /livinghorizon config}. Every change applies at once and is written to
 * {@code config/livinghorizon.json} when the screen closes. Which mobs are shown is
 * chosen on {@link MobTypesScreen}.
 */
public final class FarConfigScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    /**
     * Beside the title: one button per {@link ProjectLink}, then the changelog's, only their icon
     * drawn over them, which says what they are.
     */
    private static final int LOGO_BUTTON = 20;
    private static final int LOGO_GAP = 2;
    /** Between the end of the title and the first icon, and from the icons to the column's edge. */
    private static final int MARGIN = 6;

    private final @Nullable Screen parent;
    private final List<Button> links = new ArrayList<>();
    private @Nullable Button changelog;
    private @Nullable StringWidget titleWidget;
    /** How far left the title and the icons were moved to fit a narrow column. */
    private int titleShift;

    public FarConfigScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "general"));
        QualityPreset preset = QualityPreset.of(c);
        list.addSmall(
                Button.builder(Component.translatable(KEY + "preset",
                                Component.translatable(KEY + "preset." + (preset == null ? "custom" : preset.id()))), b -> {
                            QualityPreset.next(FarConfig.get()).apply(FarConfig.get());
                            // A fresh screen: rebuildWidgets() would stack a second layout on the first.
                            minecraft.setScreen(new FarConfigScreen(parent));
                        })
                        .tooltip(Tooltip.create(Component.translatable(KEY + "preset.tooltip"))).build(),
                bool("easterEggs", c.easterEggs, v -> c.easterEggs = v).createButton(minecraft.options, 0, 0, 150));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "debug"), b -> minecraft.setScreen(new DebugScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "debug.tooltip"))).build(),
                null);

        // Distances take a whole row: their names are long, as the game's own are. Each is drawn
        // in the world behind the screen while it is hovered or changed (distance()).
        header(Component.translatable(KEY + "impostorsSection"));
        list.addSmall(
                bool("impostors", c.impostors, v -> c.impostors = v),
                bool("impostorEntities", c.impostorEntities, v -> c.impostorEntities = v));
        list.addSmall(bool("impostorPlayers", c.impostorPlayers, v -> c.impostorPlayers = v), null);
        list.addBig(distance(Shape.AROUND, blocks("impostorDistance", 0, FarConfig.MAX_IMPOSTOR_DISTANCE,
                c.impostorDistance, v -> c.impostorDistance = v)));
        list.addBig(unit("impostorPixels", "pixels", 0, FarConfig.MAX_IMPOSTOR_PIXELS, c.impostorPixels,
                v -> c.impostorPixels = v));
        list.addBig(unit("impostorMobMemory", "megabytes", FarConfig.MIN_MOB_MEMORY, FarConfig.MAX_MOB_MEMORY,
                c.impostorMobMemory, v -> c.impostorMobMemory = v));
        list.addBig(unit("impostorPlayerMemory", "megabytes", FarConfig.MIN_PLAYER_MEMORY, FarConfig.MAX_PLAYER_MEMORY,
                c.impostorPlayerMemory, v -> c.impostorPlayerMemory = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "impostorPreview"), b -> minecraft.setScreen(new ImpostorScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "impostorPreview.tooltip"))).build(),
                null);

        header(Component.translatable(KEY + "players"));
        list.addSmall(
                bool("enabled", c.enabled, v -> c.enabled = v),
                bool("showVehicles", c.showVehicles, v -> c.showVehicles = v));
        list.addBig(distance(Shape.AROUND, reach("playerMaxDistance", c.playerMaxDistance, v -> c.playerMaxDistance = v)));
        list.addSmall(
                bool("offlinePlayers", c.offlinePlayers, v -> c.offlinePlayers = v),
                integer("maxOfflinePlayers", 0, FarConfig.MAX_OFFLINE, c.maxOfflinePlayers,
                        v -> c.maxOfflinePlayers = v, FarConfig.UNLIMITED_OFFLINE),
                bool("offlineLife", c.offlineLife, v -> c.offlineLife = v),
                distance(Shape.AROUND, blocks("offlineRoam", 8, 128, c.offlineRoam, v -> c.offlineRoam = v)));
        list.addSmall(
                bool("offlineNames", c.offlineNames, v -> c.offlineNames = v).createButton(minecraft.options, 0, 0, 150),
                Button.builder(Component.translatable(KEY + "forgetSleepers"), b -> confirm("forgetSleepers",
                                () -> FarPlayerTracker.get().resting().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetSleepers.tooltip"))).build());

        // In three parts, as on Sodium's page: what goes for both, then the mobs, then the other entities.
        header(Component.translatable(KEY + "mobs"));
        subheader(Component.translatable(KEY + "mobs.general"));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "chooseMobs"), b -> minecraft.setScreen(new MobTypesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "chooseMobs.tooltip"))).build(),
                Button.builder(Component.translatable(KEY + "forgetMobs"), b -> confirm("forgetMobs",
                                () -> FarPlayerTracker.get().mobs().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetMobs.tooltip"))).build());
        list.addSmall(scanButton(), distanceButton(Shape.CHUNKS, chunks("scanRadius", MobScan.MIN_RADIUS,
                MobScan.MAX_RADIUS, c.scanRadius, v -> c.scanRadius = v), 150));
        list.addSmall(foreseeButton(), distanceButton(Shape.CHUNKS, chunks("foreseeRadius", Foresight.MIN_RADIUS,
                Foresight.MAX_RADIUS, c.foreseeRadius, v -> c.foreseeRadius = v), 150));
        subheader(Component.translatable(KEY + "mobs.creatures"));
        // The guessed mobs beside the mobs: Max Mobs and Mob Render Distance count both.
        list.addSmall(
                bool("distantMobs", c.distantMobs, v -> c.distantMobs = v),
                bool("seedMobs", c.seedMobs, v -> c.seedMobs = v));
        list.addSmall(
                integer("maxDistantMobs", 0, FarConfig.MAX_MOBS, c.maxDistantMobs, v -> c.maxDistantMobs = v, FarConfig.UNLIMITED_MOBS),
                bool("rememberNamedMobs", c.rememberNamedMobs, v -> c.rememberNamedMobs = v));
        list.addBig(distance(Shape.AROUND, reach("mobMaxDistance", c.mobMaxDistance, v -> c.mobMaxDistance = v)));
        list.addBig(unit("animationMinPixels", "pixels", 0, 40, c.animationMinPixels, v -> c.animationMinPixels = v));
        list.addBig(distance(Shape.AROUND, reach("animationMaxDistance", c.animationMaxDistance, v -> c.animationMaxDistance = v)));
        subheader(Component.translatable(KEY + "mobs.entities"));
        list.addSmall(
                bool("distantEntities", c.distantEntities, v -> c.distantEntities = v),
                integer("maxDistantEntities", 0, FarConfig.MAX_MOBS, c.maxDistantEntities,
                        v -> c.maxDistantEntities = v, FarConfig.UNLIMITED_MOBS));
        list.addBig(distance(Shape.AROUND, reach("entityMaxDistance", c.entityMaxDistance, v -> c.entityMaxDistance = v)));

        header(Component.translatable(KEY + "birds"));
        list.addSmall(
                bool("skyBirds", c.skyBirds, v -> c.skyBirds = v),
                unit("birdDensity", "percent", 0, 500, c.birdDensity, v -> c.birdDensity = v));
        list.addBig(distance(Shape.AROUND, blocks("birdMaxDistance", 100, 3000, c.birdMaxDistance,
                v -> c.birdMaxDistance = v)));
        list.addSmall(
                distance(Shape.ABOVE, blocks("birdMinHeight", 0, 200, c.birdMinHeight, v -> c.birdMinHeight = v)),
                unit("birdSize", "multiplier", 1, 6, c.birdSize, v -> c.birdSize = v));
        list.addSmall(
                choice("birdStyle", List.of("3d", "2d"), c.birdStyle, v -> c.birdStyle = v).createButton(minecraft.options, 0, 0, 150),
                Button.builder(Component.translatable(KEY + "chooseBirds"), b -> minecraft.setScreen(new BirdTypesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "chooseBirds.tooltip"))).build());
    }

    @Override
    protected void addTitle() {
        super.addTitle();
        links.clear();
        for (ProjectLink link : ProjectLink.values()) {
            links.add(addRenderableWidget(Button.builder(Component.empty(), b -> LinkScreen.open(this, link))
                    .size(LOGO_BUTTON, LOGO_BUTTON)
                    .tooltip(Tooltip.create(Component.translatable(KEY + link.key + ".tooltip"))).build()));
        }
        changelog = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.setScreen(new ChangelogScreen(this)))
                .size(LOGO_BUTTON, LOGO_BUTTON)
                .tooltip(Tooltip.create(Component.translatable(KEY + "changelog.tooltip"))).build());
        titleWidget = null;
        layout.visitWidgets(widget -> {
            if (widget instanceof StringWidget text && text.getMessage() == title) titleWidget = text;
        });
    }

    /**
     * The icons go where the title ends, in the middle of the header: the links, then the
     * changelog. On a narrow column the title and the icons slide left together to fit.
     */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (changelog == null) return;
        List<Button> row = new ArrayList<>(links);
        row.add(changelog);
        int rowWidth = row.size() * LOGO_BUTTON + (row.size() - 1) * LOGO_GAP;
        int start = columnWidth() / 2 + font.width(title) / 2 + MARGIN;
        titleShift = Math.max(0, start + rowWidth + MARGIN - columnWidth());
        if (titleWidget != null) titleWidget.setX(titleWidget.getX() - titleShift);
        int x = start - titleShift;
        for (Button button : row) {
            button.setX(x);
            button.setY((layout.getHeaderHeight() - LOGO_BUTTON) / 2);
            x += LOGO_BUTTON + LOGO_GAP;
        }
    }

    /** The version stays under the title when the title was moved. */
    @Override
    protected int titleCentre() {
        return super.titleCentre() - titleShift;
    }

    @Override
    protected void drawOver(GuiGraphics graphics) {
        ProjectLink[] all = ProjectLink.values();
        for (int i = 0; i < links.size() && i < all.length; i++) {
            Button button = links.get(i);
            Icons.draw(graphics, all[i].icon, button.getX() + (LOGO_BUTTON - Icons.SIZE) / 2,
                    button.getY() + (LOGO_BUTTON - Icons.SIZE) / 2);
        }
        if (changelog != null) {
            Icons.changelog(graphics, changelog.getX() + (LOGO_BUTTON - Icons.SIZE) / 2,
                    changelog.getY() + (LOGO_BUTTON - Icons.SIZE) / 2);
        }
    }

    /** Restore defaults sits beside Done: it is about every setting of the screen, not the birds. */
    @Override
    protected void addFooter() {
        //? if >=1.20.2 {
        LinearLayout row = LinearLayout.horizontal().spacing(8);
        //?} else {
        /*// Before 1.20.2 the row spreads its buttons over its own width: two of 150 and a gap of 8.
        LinearLayout row = new LinearLayout(2 * 150 + 8, 20, LinearLayout.Orientation.HORIZONTAL);
        *///?}
        row.addChild(Button.builder(Component.translatable(KEY + "restoreDefaults"), b -> restoreDefaults())
                .tooltip(Tooltip.create(Component.translatable(KEY + "restoreDefaults.tooltip"))).width(150).build());
        row.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(150).build());
        layout.addToFooter(row);
    }

    /**
     * Reads the mobs of the chunks around from the world itself: only in single player,
     * where the world is in this game. Back to the game, where the chat says what it found.
     */
    private Button scanButton() {
        boolean available = MobScan.available(minecraft);
        Button button = Button.builder(Component.translatable(KEY + "scanMobs"), b -> {
                    FarConfig.save();
                    minecraft.setScreen(null);
                    MobScan.start(minecraft, FarConfig.get().scanRadius);
                })
                .tooltip(Tooltip.create(Component.translatable(
                        KEY + (available ? "scanMobs.tooltip" : "scanMobs.unavailable"))))
                .build();
        button.active = available;
        return button;
    }

    /** <i>Guess Nearby Mobs</i>: in a world played here, or on a server that shares its seed. */
    private Button foreseeButton() {
        boolean available = FarPlayerTracker.get().mobs().foresight().available(minecraft);
        Button button = Button.builder(Component.translatable(KEY + "foreseeMobs"), b -> {
                    FarConfig.save();
                    minecraft.setScreen(null);
                    FarPlayerTracker.get().mobs().foresight().start(minecraft, FarConfig.get().foreseeRadius);
                })
                .tooltip(Tooltip.create(Component.translatable(
                        KEY + (available ? "foreseeMobs.tooltip" : "foreseeMobs.unavailable"))))
                .build();
        button.active = available;
        return button;
    }

    @Override
    public void removed() {
        FarConfig.save();
    }

    /** Asks, then puts every setting back to its default and reopens the screen to show them. */
    private void restoreDefaults() {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) FarConfig.reset(MobList.all());
            minecraft.setScreen(yes ? new FarConfigScreen(parent) : this);
        }, Component.translatable(KEY + "restoreDefaults"), Component.translatable(KEY + "restoreDefaults.confirm")));
    }

    /** Asks before forgetting: what is forgotten does not come back. */
    private void confirm(String name, Runnable action) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) action.run();
            minecraft.setScreen(this);
        }, Component.translatable(KEY + name), Component.translatable(KEY + name + ".confirm")));
    }

    static OptionInstance<Boolean> bool(String name, boolean value, Consumer<Boolean> set) {
        return OptionInstance.createBoolean(KEY + name, tooltip(name), value, set::accept);
    }

    /**
     * With {@code unlimited} (the value the settings keep for no limit, above {@code max}), one more
     * place past {@code max} is no limit, read as the {@code .max} text, and any number can be typed.
     */
    static OptionInstance<Integer> integer(String name, int min, int max, int value, Consumer<Integer> set,
                                           int unlimited) {
        boolean limitless = unlimited > max;
        int shown = limitless && value >= unlimited ? NumberSlider.UNLIMITED
                : limitless ? Math.max(value, min) : Math.clamp(value, min, max);
        return slider(name, min, max, shown,
                (caption, v) -> v == NumberSlider.UNLIMITED
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".max"))
                        : v == 0 && min == 0
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".zero"))
                        : Options.genericValueLabel(caption, v),
                limitless, v -> set.accept(v == NumberSlider.UNLIMITED ? unlimited : v));
    }

    /** A distance in blocks, said so: "512 Blocks"; zero reads as the {@code .zero} text. */
    static OptionInstance<Integer> blocks(String name, int min, int max, int value, Consumer<Integer> set) {
        return unit(name, "blocks", min, max, value, set);
    }

    /**
     * A distance in blocks up to {@link FarConfig#MAX_DISTANCE} along the slider, then no limit at the
     * right end, read as the {@code .zero} text: the settings keep no limit as 0. A farther distance
     * can be typed.
     */
    static OptionInstance<Integer> reach(String name, int value, Consumer<Integer> set) {
        return slider(name, 1, FarConfig.MAX_DISTANCE, value <= 0 ? NumberSlider.UNLIMITED : value,
                (caption, v) -> v == NumberSlider.UNLIMITED
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".zero"))
                        : Options.genericValueLabel(caption, Component.translatable(KEY + "blocks", v)),
                true, v -> set.accept(v == NumberSlider.UNLIMITED ? 0 : v));
    }

    /** A number in a unit, the way the game writes it ("30s", "6px", "2x", "100%"); zero reads as the {@code .zero} text. */
    static OptionInstance<Integer> unit(String name, String unit, int min, int max, int value, Consumer<Integer> set) {
        return slider(name, min, max, Math.clamp(value, min, max),
                (caption, v) -> v == 0 && min == 0
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".zero"))
                        : Options.genericValueLabel(caption, Component.translatable(KEY + unit, v)),
                false, set);
    }

    /** A distance in chunks, worded like the game's own render distance ("32 chunks"). */
    static OptionInstance<Integer> chunks(String name, int min, int max, int value, Consumer<Integer> set) {
        return slider(name, min, max, Math.clamp(value, min, max),
                (caption, v) -> Options.genericValueLabel(caption, Component.translatable(KEY + name + ".value", v)),
                false, set);
    }

    /**
     * A slider of whole numbers ({@link NumberSlider}). With {@code unlimited}, its right end is no
     * limit, which the settings named by {@link NumberSlider#asksBeforeUnlimited} keep only once the
     * player says yes, and it takes a typed value, past its last number too.
     */
    private static OptionInstance<Integer> slider(String name, int min, int max, int value,
                                                  OptionInstance.CaptionBasedToString<Integer> label,
                                                  boolean unlimited, Consumer<Integer> set) {
        NumberSlider.Range range = new NumberSlider.Range(Component.translatable(KEY + name), min, max, label, unlimited,
                NumberSlider.asksBeforeUnlimited(name));
        // Only a slider with no limit at its end takes a typed value, and says so.
        Component tooltip = Component.translatable(KEY + name + ".tooltip");
        if (unlimited) {
            tooltip = tooltip.copy().append("\n\n")
                    .append(Component.translatable(KEY + "typeValue").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return new OptionInstance<>(KEY + name, OptionInstance.cachedConstantTooltip(tooltip), label, range, value, set::accept);
    }

    static OptionInstance<String> choice(String name, List<String> values, String value, Consumer<String> set) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                // The cycle button puts the caption in front by itself.
                (caption, v) -> Component.translatable(KEY + name + "." + v),
                new OptionInstance.Enum<>(values, Codec.STRING), values.contains(value) ? value : values.get(0), set::accept);
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String name) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip"));
    }
}
