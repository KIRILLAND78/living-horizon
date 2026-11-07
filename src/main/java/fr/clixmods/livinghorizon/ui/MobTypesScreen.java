package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which mobs are shown far away: every mob of the game, one switch each, with a search
 * field. The ones a server with the mod shares are marked; the others are only shown when
 * this client met them itself. Two tabs, as in the impostor screen: the mobs, and the
 * entities that are not mobs (boats, vehicles, mannequins), where another mod's vehicle
 * shows once met.
 */
public final class MobTypesScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.mobTypes.";
    /** Where the title and the tabs under it stand; the list starts below. */
    private static final int TITLE_Y = 12, TABS_Y = 26, HEADER = 52;

    /** The tab shown: the entities that are not mobs, or the mobs. Kept while the game runs. */
    private static boolean entities;
    private @Nullable StringWidget titleWidget;
    private @Nullable Button mobsTab, entitiesTab;

    /** Every mob type, sorted by its name in the current language. */
    private final List<EntityType<?>> types;
    private final String filter;
    /** Opening a copy of itself, on another tab or search: the settings are saved once it closes. */
    private boolean reopening;
    /** The switches on screen, by mob id: updated in place by All, None and Defaults. */
    private final Map<String, CycleButton<Mode>> buttons = new LinkedHashMap<>();
    /** Those of them that are not alive: boats and other vehicles, with two choices only. */
    private final java.util.Set<String> placed = new java.util.HashSet<>();
    /** The icon drawn beside each switch: the mob's spawn egg, the boat's item. */
    private final Map<String, ItemStack> icons = new LinkedHashMap<>();

    public MobTypesScreen(@Nullable Screen parent) {
        this(parent, "");
    }

    private MobTypesScreen(@Nullable Screen parent, String filter) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
        this.filter = filter;
        types = MobList.all();
        // Kinds the list has just learnt of (another mod's vehicles): in their default mode.
        FarConfig.adoptNewMobs(types);
        types.sort(Comparator.comparing(type -> type.getDescription().getString().toLowerCase(Locale.ROOT)));
    }

    /** The title, then the two tabs, the open one pressed down. */
    @Override
    protected void addTitle() {
        // Built again when the window is resized before 1.20.5, on the same layout: one title.
        if (titleWidget == null) titleWidget = layout.addToHeader(new StringWidget(title, font));
        layout.setHeaderHeight(HEADER);
        mobsTab = addRenderableWidget(Button.builder(Component.translatable(KEY + "tab.mobs"), b -> show(false))
                .width(100).build());
        entitiesTab = addRenderableWidget(Button.builder(Component.translatable(KEY + "tab.entities"), b -> show(true))
                .tooltip(Tooltip.create(Component.translatable(KEY + "tab.entities.tooltip")))
                .width(100).build());
        mobsTab.active = entities;
        entitiesTab.active = !entities;
    }

    private void show(boolean entities) {
        if (MobTypesScreen.entities == entities) return;
        MobTypesScreen.entities = entities;
        reopen(filter);
    }

    /**
     * The same screen on another tab or search: a new one, since building this one again would
     * add a second title, list and row of buttons to its layout, the old ones left under the new.
     */
    private void reopen(String filter) {
        reopening = true;
        minecraft.setScreen(new MobTypesScreen(lastScreen, filter));
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        // The layout centres the title in the taller header: back where every other screen has it.
        if (titleWidget != null) titleWidget.setY(TITLE_Y);
        int middle = columnWidth() / 2;
        if (mobsTab != null) mobsTab.setPosition(middle - 102, TABS_Y);
        if (entitiesTab != null) entitiesTab.setPosition(middle + 2, TABS_Y);
    }

    /** The tabs stand where the other screens show the mod's version. */
    @Override
    protected boolean showsVersion() {
        return false;
    }

    /** Whether a kind is listed under the tab shown, and matches the search. */
    private boolean listed(EntityType<?> type, String needle) {
        if (MobList.creature(type) == entities) return false;
        String id = EntityType.getKey(type).toString();
        return needle.isEmpty() || type.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle)
                || id.contains(needle);
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        EditBox search = new EditBox(font, 0, 0, 310, 20, Component.translatable(KEY + "search"));
        search.setHint(Component.translatable(KEY + "search"));
        search.setValue(filter);
        search.setResponder(text -> {
            if (!text.equals(filter)) reopen(text);
        });
        list.addSmall(search, null);
        setInitialFocus(search);

        FarConfig config = FarConfig.get();
        String needle = filter.toLowerCase(Locale.ROOT).trim();
        List<AbstractWidget> switches = new ArrayList<>();
        buttons.clear();
        placed.clear();
        icons.clear();
        for (EntityType<?> type : types) {
            if (!listed(type, needle)) continue;
            String id = EntityType.getKey(type).toString();
            Component name = type.getDescription();
            boolean shared = MobKinds.TYPES.contains(id);
            Component label = shared ? Component.translatable(KEY + "shared", name) : name;
            // A boat or another vehicle never moves far away: shown or not, nothing more.
            boolean alive = MobList.alive(type);
            if (!alive) placed.add(id);
            String tooltip = (alive ? "" : "vehicle.") + (shared ? "shared.tooltip" : "local.tooltip");
            //? if >=1.21.11 {
            CycleButton<Mode> button = CycleButton.builder(Mode::label, Mode.of(id, alive))
            //?} else {
            /*CycleButton<Mode> button = CycleButton.builder(Mode::label).withInitialValue(Mode.of(id, alive))
            *///?}
                    .withValues(alive ? Mode.MOVING : Mode.PLACED)
                    .withTooltip(OptionInstance.cachedConstantTooltip(locked(id)
                            ? Component.translatable(KEY + "locked.tooltip", id, FarConfig.wholeMod(id))
                            : Component.translatable(KEY + tooltip, id)))
                    .create(0, 0, ICON_ROOM, 20, label, (b, mode) -> {
                        mode.apply(id);
                        changed();
                    });
            button.active = !locked(id);
            buttons.put(id, button);
            ItemStack icon = icon(id);
            if (!icon.isEmpty()) icons.put(id, icon);
            switches.add(button);
        }
        list.addSmall(switches);
    }

    /** Hidden for its whole mod in the file ({@code "cobblemon:*"}): a switch cannot show it. */
    private static boolean locked(String id) {
        return FarConfig.get().locksMob(id);
    }

    /** A switch leaves 20 pixels of its 150-wide cell for the icon. */
    private static final int ICON_ROOM = 130;
    private static final int PARKED = -10000;

    private static Map<String, Item> items;

    /**
     * The spawn egg of a mob, or the item of a boat: what the game itself shows for it. From
     * 26.1 an item has no components until a world's data binds them (the title screen, through
     * Mod Menu), and a stack of it throws: the switch goes without its icon there.
     */
    private static ItemStack icon(String id) {
        if (items == null) {
            items = new java.util.HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                items.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
            }
        }
        Item item = items.getOrDefault(id + "_spawn_egg", items.get(id));
        if (item == null) return ItemStack.EMPTY;
        try {
            return new ItemStack(item);
        } catch (RuntimeException unbound) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * The list only positions the rows it draws: a row scrolled out of view keeps the place
     * it had when last seen, so drawing icons from those places stacks them up. Every switch
     * is parked far off first; the ones the list draws this frame come back by themselves.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.values().forEach(button -> button.setY(PARKED));
        super.render(graphics, mouseX, mouseY, partialTick);
        if (list == null) return;
        graphics.enableScissor(list.getX(), list.getY(), list.getRight(), list.getBottom());
        for (Map.Entry<String, ItemStack> entry : icons.entrySet()) {
            CycleButton<Mode> button = buttons.get(entry.getKey());
            int y = button.getY() + 2;
            if (y + 16 <= list.getY() || y >= list.getBottom()) continue;
            graphics.renderItem(entry.getValue(), button.getX() + button.getWidth() + 2, y);
        }
        graphics.disableScissor();
    }

    /** How one kind of mob is shown far away. */
    private enum Mode {
        HIDDEN, ANIMATED, STILL, SHOWN;

        /** The choices of a mob, which walks or stays still. */
        static final List<Mode> MOVING = List.of(HIDDEN, ANIMATED, STILL);
        /** The choices of a boat or another vehicle, which stays where it was left whatever is chosen. */
        static final List<Mode> PLACED = List.of(HIDDEN, SHOWN);

        Component label() {
            return Component.translatable(KEY + "mode." + name().toLowerCase(Locale.ROOT));
        }

        static Mode of(String id, boolean alive) {
            FarConfig config = FarConfig.get();
            if (config.hidesMob(id) || !config.mobTypes.contains(id)) return HIDDEN;
            if (!alive) return SHOWN;
            return config.stillMobTypes.contains(id) ? STILL : ANIMATED;
        }

        void apply(String id) {
            FarConfig config = FarConfig.get();
            if (locked(id)) return;
            config.setMobShown(id, this != HIDDEN);
            config.stillMobTypes.remove(id);
            if (this == STILL) config.stillMobTypes.add(id);
        }
    }

    @Override
    protected void addFooter() {
        //? if >=1.20.2 {
        LinearLayout row = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        //?} else {
        /*// Before 1.20.2 the row spreads its buttons over its own width: four of 74 and three gaps of 8.
        LinearLayout row = layout.addToFooter(new LinearLayout(4 * 74 + 3 * 8, 20, LinearLayout.Orientation.HORIZONTAL));
        *///?}
        row.addChild(Button.builder(Component.translatable(KEY + "all"), b -> setAll(true)).width(74).build());
        row.addChild(Button.builder(Component.translatable(KEY + "none"), b -> setAll(false)).width(74).build());
        row.addChild(Button.builder(Component.translatable(KEY + "defaults"), b -> {
            FarConfig.get().resetMobs(MobList.all());
            refreshButtons();
            changed();
        }).width(74).build());
        row.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(74).build());
    }

    /** Every mob the tab and the search show: hidden, or shown (still ones stay still). */
    private void setAll(boolean on) {
        String needle = filter.toLowerCase(Locale.ROOT).trim();
        for (EntityType<?> type : types) {
            if (!listed(type, needle)) continue;
            String id = EntityType.getKey(type).toString();
            Mode current = Mode.of(id, MobList.alive(type));
            if (!on) Mode.HIDDEN.apply(id);
            else if (current == Mode.HIDDEN) Mode.ANIMATED.apply(id);
        }
        refreshButtons();
        changed();
    }

    /** The kinds listed now, under the tab shown and the search, by id: what the game tests read. */
    public java.util.Set<String> listedKinds() {
        return java.util.Collections.unmodifiableSet(buttons.keySet());
    }

    /** The text of a kind's switch, its name and choice, or "" when it is not listed: what the game tests read. */
    public String switchText(String id) {
        CycleButton<Mode> button = buttons.get(id);
        return button == null ? "" : button.getMessage().getString();
    }

    /** The middle of a kind's switch on screen, or null when it is not drawn: what the game tests click. */
    public int @Nullable [] switchAt(String id) {
        CycleButton<Mode> button = buttons.get(id);
        if (button == null || button.getY() == PARKED) return null;
        return new int[]{button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
    }

    /** The world behind the list follows each change at once, not once it is closed. */
    private static void changed() {
        FarPlayerTracker.get().mobs().kindsChanged();
    }

    /** Shows the config on the switches already there, without building the screen again. */
    private void refreshButtons() {
        buttons.forEach((id, button) -> button.setValue(Mode.of(id, !placed.contains(id))));
    }

    @Override
    public void removed() {
        if (!reopening) FarConfig.save();
    }
}
