//? if sodium {
package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Foresight;
import fr.clixmods.livinghorizon.track.MobScan;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The settings as pages of Sodium's video settings screen (Sodium 0.8 and later), next to
 * Sodium's own: the same settings, names and tooltips as {@link FarConfigScreen}, and its
 * sub-screens behind buttons. Found by Sodium through the {@code sodium:config_api_user}
 * entry point (Fabric, Quilt) or mod property (NeoForge); never loaded without it.
 *
 * <p>Sodium holds changes until "Apply", then saves each changed option through its binding
 * and calls {@link #afterSave} once. A preset sets other settings: it is applied there, after
 * the sliders, and Sodium's copy of every value is read again so that the page shows it.
 */
public final class SodiumOptions implements ConfigEntryPoint {
    private static final String KEY = "livinghorizon.options.";

    /** The preset chosen on the page, applied once Sodium has saved everything else. */
    private @Nullable Preset chosenPreset;

    private final StorageEventHandler storage = this::afterSave;

    /** Settings set to no limit on the page, kept once the player says yes: {@link #asking}. */
    private final java.util.Map<String, Runnable> held = new java.util.LinkedHashMap<>();

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        FarConfig defaults = new FarConfig();
        builder.registerOwnModOptions()
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0xF28C28))
                .setNonTintedIcon(id("icon.png"))
                .addPage(general(builder, defaults))
                .addPage(impostors(builder, defaults))
                .addPage(players(builder, defaults))
                .addPage(mobs(builder, defaults))
                .addPage(birds(builder, defaults));
    }

    private OptionPageBuilder general(ConfigBuilder b, FarConfig d) {
        return b.createOptionPage().setName(Component.translatable(KEY + "general"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(b.createEnumOption(id("preset"), Preset.class)
                                .setName(Component.translatable(KEY + "preset.name"))
                                .setTooltip(tooltip("preset"))
                                .setElementNameProvider(p -> Component.translatable(KEY + "preset." + p.id()))
                                .setDefaultValue(Preset.NORMAL)
                                .setStorageHandler(storage)
                                .setBinding(p -> chosenPreset = p, () -> Preset.of(FarConfig.get())))
                        .addOption(bool(b, "easterEggs", d.easterEggs, v -> FarConfig.get().easterEggs = v,
                                () -> FarConfig.get().easterEggs)))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(opens(b, "debug", DebugScreen::new))
                        // Only Discord here: the store pages and the website stay on the mod's own screen.
                        .addOption(button(b, "discord", parent -> LinkScreen.open(parent, ProjectLink.DISCORD)))
                        .addOption(opens(b, "changelog", ChangelogScreen::new))
                        .addOption(button(b, "restoreDefaults", parent -> confirm(parent, "restoreDefaults", () -> {
                            FarConfig.reset(MobList.all());
                            reload();
                        }))));
    }

    private OptionPageBuilder impostors(ConfigBuilder b, FarConfig d) {
        return b.createOptionPage().setName(Component.translatable(KEY + "impostorsSection"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "impostors", d.impostors, v -> FarConfig.get().impostors = v,
                                () -> FarConfig.get().impostors))
                        .addOption(bool(b, "impostorEntities", d.impostorEntities, v -> FarConfig.get().impostorEntities = v,
                                () -> FarConfig.get().impostorEntities))
                        .addOption(bool(b, "impostorPlayers", d.impostorPlayers, v -> FarConfig.get().impostorPlayers = v,
                                () -> FarConfig.get().impostorPlayers))
                        .addOption(blocks(b, "impostorDistance", 0, FarConfig.MAX_IMPOSTOR_DISTANCE, d.impostorDistance,
                                v -> FarConfig.get().impostorDistance = v, () -> FarConfig.get().impostorDistance)
                                .setEnabledProvider(any(PICTURED), ids(PICTURED)))
                        .addOption(integer(b, "impostorPixels", 0, FarConfig.MAX_IMPOSTOR_PIXELS, d.impostorPixels,
                                v -> FarConfig.get().impostorPixels = v, () -> FarConfig.get().impostorPixels,
                                unit("impostorPixels", "pixels", 0))
                                .setEnabledProvider(any(PICTURED), ids(PICTURED)))
                        .addOption(integer(b, "impostorMobMemory", FarConfig.MIN_MOB_MEMORY, FarConfig.MAX_MOB_MEMORY,
                                d.impostorMobMemory, v -> FarConfig.get().impostorMobMemory = v,
                                () -> FarConfig.get().impostorMobMemory,
                                unit("impostorMobMemory", "megabytes", FarConfig.MIN_MOB_MEMORY))
                                .setEnabledProvider(any(NOT_PLAYERS), ids(NOT_PLAYERS)))
                        .addOption(integer(b, "impostorPlayerMemory", FarConfig.MIN_PLAYER_MEMORY, FarConfig.MAX_PLAYER_MEMORY,
                                d.impostorPlayerMemory, v -> FarConfig.get().impostorPlayerMemory = v,
                                () -> FarConfig.get().impostorPlayerMemory,
                                unit("impostorPlayerMemory", "megabytes", FarConfig.MIN_PLAYER_MEMORY))
                                .setEnabledProvider(on("impostorPlayers"), id("impostorPlayers"))))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(opens(b, "impostorPreview", ImpostorScreen::new)));
    }

    private OptionPageBuilder players(ConfigBuilder b, FarConfig d) {
        return b.createOptionPage().setName(Component.translatable(KEY + "players"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "enabled", d.enabled, v -> FarConfig.get().enabled = v,
                                () -> FarConfig.get().enabled))
                        .addOption(bool(b, "showVehicles", d.showVehicles, v -> FarConfig.get().showVehicles = v,
                                () -> FarConfig.get().showVehicles))
                        .addOption(reach(b, "playerMaxDistance", d.playerMaxDistance,
                                v -> FarConfig.get().playerMaxDistance = v, () -> FarConfig.get().playerMaxDistance)))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "offlinePlayers", d.offlinePlayers, v -> FarConfig.get().offlinePlayers = v,
                                () -> FarConfig.get().offlinePlayers))
                        .addOption(count(b, "maxOfflinePlayers", FarConfig.MAX_OFFLINE, FarConfig.UNLIMITED_OFFLINE, d.maxOfflinePlayers,
                                v -> FarConfig.get().maxOfflinePlayers = v, () -> FarConfig.get().maxOfflinePlayers)
                                .setEnabledProvider(on("offlinePlayers"), id("offlinePlayers")))
                        .addOption(bool(b, "offlineLife", d.offlineLife, v -> FarConfig.get().offlineLife = v,
                                () -> FarConfig.get().offlineLife)
                                .setEnabledProvider(on("offlinePlayers"), id("offlinePlayers")))
                        .addOption(blocks(b, "offlineRoam", 8, 128, d.offlineRoam, v -> FarConfig.get().offlineRoam = v,
                                () -> FarConfig.get().offlineRoam)
                                .setEnabledProvider(s -> s.readBooleanOption(id("offlinePlayers"))
                                        && s.readBooleanOption(id("offlineLife")), id("offlinePlayers"), id("offlineLife")))
                        .addOption(bool(b, "offlineNames", d.offlineNames, v -> FarConfig.get().offlineNames = v,
                                () -> FarConfig.get().offlineNames)
                                .setEnabledProvider(on("offlinePlayers"), id("offlinePlayers")))
                        .addOption(button(b, "forgetSleepers", parent -> confirm(parent, "forgetSleepers",
                                () -> FarPlayerTracker.get().resting().forgetAll()))));
    }

    private OptionPageBuilder mobs(ConfigBuilder b, FarConfig d) {
        // In three parts, as on the mod's own screen: what goes for both, the mobs, the other entities.
        return b.createOptionPage().setName(Component.translatable(KEY + "mobs"))
                .addOptionGroup(scan(b, d, b.createOptionGroup().setName(Component.translatable(KEY + "mobs.general"))
                        .addOption(opens(b, "chooseMobs", MobTypesScreen::new))
                        .addOption(button(b, "forgetMobs", parent -> confirm(parent, "forgetMobs",
                                () -> FarPlayerTracker.get().mobs().forgetAll())))))
                .addOptionGroup(b.createOptionGroup().setName(Component.translatable(KEY + "mobs.creatures"))
                        .addOption(bool(b, "distantMobs", d.distantMobs, v -> FarConfig.get().distantMobs = v,
                                () -> FarConfig.get().distantMobs))
                        .addOption(bool(b, "seedMobs", d.seedMobs,
                                v -> FarConfig.get().seedMobs = v, () -> FarConfig.get().seedMobs))
                        .addOption(count(b, "maxDistantMobs", FarConfig.MAX_MOBS, FarConfig.UNLIMITED_MOBS, d.maxDistantMobs,
                                v -> FarConfig.get().maxDistantMobs = v, () -> FarConfig.get().maxDistantMobs))
                        .addOption(bool(b, "rememberNamedMobs", d.rememberNamedMobs,
                                v -> FarConfig.get().rememberNamedMobs = v, () -> FarConfig.get().rememberNamedMobs))
                        .addOption(reach(b, "mobMaxDistance", d.mobMaxDistance,
                                v -> FarConfig.get().mobMaxDistance = v, () -> FarConfig.get().mobMaxDistance))
                        .addOption(integer(b, "animationMinPixels", 0, 40, d.animationMinPixels,
                                v -> FarConfig.get().animationMinPixels = v, () -> FarConfig.get().animationMinPixels,
                                unit("animationMinPixels", "pixels", 0)))
                        .addOption(reach(b, "animationMaxDistance", d.animationMaxDistance,
                                v -> FarConfig.get().animationMaxDistance = v, () -> FarConfig.get().animationMaxDistance)))
                .addOptionGroup(b.createOptionGroup().setName(Component.translatable(KEY + "mobs.entities"))
                        .addOption(bool(b, "distantEntities", d.distantEntities, v -> FarConfig.get().distantEntities = v,
                                () -> FarConfig.get().distantEntities))
                        .addOption(count(b, "maxDistantEntities", FarConfig.MAX_MOBS, FarConfig.UNLIMITED_MOBS, d.maxDistantEntities,
                                v -> FarConfig.get().maxDistantEntities = v, () -> FarConfig.get().maxDistantEntities))
                        .addOption(reach(b, "entityMaxDistance", d.entityMaxDistance,
                                v -> FarConfig.get().entityMaxDistance = v, () -> FarConfig.get().entityMaxDistance)));
    }

    /** "Scan the chunks around": only in a single player world, read again whenever the screen is built. */
    /** The scan's two settings, added to {@code group}. */
    private OptionGroupBuilder scan(ConfigBuilder b, FarConfig d, OptionGroupBuilder group) {
        return group
                .addOption(b.createIntegerOption(id("scanRadius"))
                        .setName(Component.translatable(KEY + "scanRadius"))
                        .setTooltip(tooltip("scanRadius"))
                        .setRange(MobScan.MIN_RADIUS, MobScan.MAX_RADIUS, 1)
                        .setValueFormatter(v -> Component.translatable(KEY + "scanRadius.value", v))
                        .setDefaultValue(d.scanRadius)
                        .setStorageHandler(storage)
                        .setBinding(v -> FarConfig.get().scanRadius = v,
                                () -> Math.clamp(FarConfig.get().scanRadius, MobScan.MIN_RADIUS, MobScan.MAX_RADIUS)))
                .addOption(b.createExternalButtonOption(id("scanMobs"))
                        .setName(Component.translatable(KEY + "scanMobs"))
                        .setTooltip(Component.translatable(KEY + "scanMobs.tooltip"))
                        .setEnabledProvider(s -> MobScan.available(Minecraft.getInstance()), ConfigState.UPDATE_ON_REBUILD)
                        .setScreenConsumer(parent -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            if (!MobScan.available(minecraft)) return;
                            FarConfig.save();
                            minecraft.setScreen(null);
                            MobScan.start(minecraft, FarConfig.get().scanRadius);
                        }))
                .addOption(b.createIntegerOption(id("foreseeRadius"))
                        .setName(Component.translatable(KEY + "foreseeRadius"))
                        .setTooltip(tooltip("foreseeRadius"))
                        .setRange(Foresight.MIN_RADIUS, Foresight.MAX_RADIUS, 1)
                        .setValueFormatter(v -> Component.translatable(KEY + "foreseeRadius.value", v))
                        .setDefaultValue(d.foreseeRadius)
                        .setStorageHandler(storage)
                        .setBinding(v -> FarConfig.get().foreseeRadius = v,
                                () -> Math.clamp(FarConfig.get().foreseeRadius, Foresight.MIN_RADIUS, Foresight.MAX_RADIUS)))
                .addOption(b.createExternalButtonOption(id("foreseeMobs"))
                        .setName(Component.translatable(KEY + "foreseeMobs"))
                        .setTooltip(Component.translatable(KEY + "foreseeMobs.tooltip"))
                        .setEnabledProvider(s -> foreseeable(), ConfigState.UPDATE_ON_REBUILD)
                        .setScreenConsumer(parent -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            if (!foreseeable()) return;
                            FarConfig.save();
                            minecraft.setScreen(null);
                            FarPlayerTracker.get().mobs().foresight().start(minecraft, FarConfig.get().foreseeRadius);
                        }));
    }

    /** Whether <i>Guess Nearby Mobs</i> can run here: a world played here, or a server sharing its seed. */
    private static boolean foreseeable() {
        return FarPlayerTracker.get().mobs().foresight().available(Minecraft.getInstance());
    }

    private OptionPageBuilder birds(ConfigBuilder b, FarConfig d) {
        Function<ConfigState, Boolean> birds = on("skyBirds");
        return b.createOptionPage().setName(Component.translatable(KEY + "birds"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "skyBirds", d.skyBirds, v -> FarConfig.get().skyBirds = v,
                                () -> FarConfig.get().skyBirds))
                        .addOption(integer(b, "birdDensity", 0, 500, d.birdDensity,
                                v -> FarConfig.get().birdDensity = v, () -> FarConfig.get().birdDensity,
                                unit("birdDensity", "percent", 0))
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(blocks(b, "birdMaxDistance", 100, 3000, d.birdMaxDistance,
                                v -> FarConfig.get().birdMaxDistance = v, () -> FarConfig.get().birdMaxDistance)
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(blocks(b, "birdMinHeight", 0, 200, d.birdMinHeight,
                                v -> FarConfig.get().birdMinHeight = v, () -> FarConfig.get().birdMinHeight)
                                .setEnabledProvider(birds, id("skyBirds"))))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(integer(b, "birdSize", 1, 6, d.birdSize,
                                v -> FarConfig.get().birdSize = v, () -> FarConfig.get().birdSize,
                                unit("birdSize", "multiplier", 1))
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(b.createEnumOption(id("birdStyle"), BirdStyle.class)
                                .setName(Component.translatable(KEY + "birdStyle"))
                                .setTooltip(tooltip("birdStyle"))
                                .setElementNameProvider(s -> Component.translatable(KEY + "birdStyle." + s.id))
                                .setDefaultValue(BirdStyle.of(d.birdStyle))
                                .setStorageHandler(storage)
                                .setBinding(s -> FarConfig.get().birdStyle = s.id, () -> BirdStyle.of(FarConfig.get().birdStyle))
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(opens(b, "chooseBirds", BirdTypesScreen::new)));
    }

    /** Once Sodium has saved the changed options: the preset on top of them, then the file. */
    private void afterSave() {
        Preset preset = chosenPreset;
        chosenPreset = null;
        if (preset != null && preset.preset != null) {
            preset.preset.apply(FarConfig.get());
            reload();
        }
        FarConfig.save();
        if (!held.isEmpty()) askUnlimited();
    }

    /**
     * The right end of a slider that asks first ({@link NumberSlider#asksBeforeUnlimited}): Sodium's
     * page cannot ask as the slider moves, so the value is held at "Apply" and set only once the
     * player says yes. No puts the page back to what the settings keep.
     */
    private IntConsumer asking(String name, int top, IntConsumer set, IntSupplier shown) {
        if (!NumberSlider.asksBeforeUnlimited(name)) return set;
        return v -> {
            held.remove(name);
            if (v >= top && shown.getAsInt() < top) held.put(name, () -> set.accept(v));
            else set.accept(v);
        };
    }

    private void askUnlimited() {
        java.util.List<Runnable> actions = new java.util.ArrayList<>(held.values());
        net.minecraft.network.chat.MutableComponent names = Component.empty();
        for (String name : held.keySet()) {
            if (!names.getSiblings().isEmpty()) names.append(", ");
            names.append(Component.translatable(KEY + name));
        }
        held.clear();
        Minecraft minecraft = Minecraft.getInstance();
        Screen back = minecraft.screen;
        minecraft.setScreen(new Confirm(yes -> {
            if (yes) {
                actions.forEach(Runnable::run);
                FarConfig.save();
            }
            reload();
            minecraft.setScreen(back);
        }, Component.translatable(KEY + "unlimited", names), Component.translatable(KEY + "unlimited.confirm")));
    }

    /**
     * Sodium's copy of every value read again from the settings, so that the page shows what a
     * preset or "Restore defaults" just set. Sodium's own class, not its API: left as it is when
     * a version of Sodium no longer has it, until the screen is opened again.
     */
    private static void reload() {
        try {
            Object config = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager")
                    .getField("CONFIG").get(null);
            if (config != null) config.getClass().getMethod("resetAllOptionsFromBindings").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LivingHorizonClient.LOGGER.debug("Could not refresh Sodium's options", e);
        }
    }

    private OptionBuilder bool(ConfigBuilder b, String name, boolean fallback, Consumer<Boolean> set,
                               java.util.function.Supplier<Boolean> get) {
        return b.createBooleanOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setDefaultValue(fallback)
                .setStorageHandler(storage)
                .setBinding(set, get);
    }

    private OptionBuilder integer(ConfigBuilder b, String name, int min, int max, int fallback, IntConsumer set,
                                  IntSupplier get, ControlValueFormatter format) {
        // A value out of range in the file would read as the default: shown at the nearest end instead.
        IntSupplier shown = () -> Math.clamp(get.getAsInt(), min, max);
        return b.createIntegerOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setRange(min, max, 1)
                .setValueFormatter(format)
                .setDefaultValue(Math.clamp(fallback, min, max))
                .setStorageHandler(storage)
                .setBinding(asking(name, max, set, shown)::accept, shown::getAsInt);
    }

    /**
     * A count from 0 to {@code last}, then no limit at the right end, read as its {@code .max} text,
     * as {@link FarConfigScreen#integer}: the settings keep no limit as {@code unlimited}. A typed
     * number past {@code last}, from the mod's screen, is shown at {@code last} and kept unless moved.
     */
    private OptionBuilder count(ConfigBuilder b, String name, int last, int unlimited, int fallback, IntConsumer set,
                                IntSupplier get) {
        int top = last + 1;
        IntSupplier shown = () -> {
            int value = get.getAsInt();
            return value >= unlimited ? top : Math.clamp(value, 0, last);
        };
        return b.createIntegerOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setRange(0, top, 1)
                .setValueFormatter(v -> v >= top ? Component.translatable(KEY + name + ".max")
                        : v == 0 ? Component.translatable(KEY + name + ".zero")
                        : Component.literal(String.valueOf(typedPast(v, last, unlimited, get))))
                .setDefaultValue(fallback >= unlimited ? top : Math.clamp(fallback, 0, last))
                .setStorageHandler(storage)
                .setBinding(asking(name, top, beyond(last, unlimited, v -> set.accept(v >= top ? unlimited : v), get), shown)::accept,
                        shown::getAsInt);
    }

    /**
     * What a row at its last place reads: the number the settings keep when one was typed past it
     * on the mod's screen, which {@link #beyond} leaves as it is.
     */
    private static int typedPast(int v, int last, int unlimited, IntSupplier get) {
        int kept = get.getAsInt();
        return v == last && kept > last && kept < unlimited ? kept : v;
    }

    /**
     * A number typed past the slider's last one on the mod's screen, which Sodium's row shows at
     * that last place: left as it is when the row is saved still there.
     */
    private static IntConsumer beyond(int last, int unlimited, IntConsumer set, IntSupplier get) {
        return v -> {
            int kept = get.getAsInt();
            if (v == last && kept > last && kept < unlimited) return;
            set.accept(v);
        };
    }

    /** A distance in blocks, "512 Blocks", the zero of a range from 0 read as its {@code .zero} text. */
    private OptionBuilder blocks(ConfigBuilder b, String name, int min, int max, int fallback, IntConsumer set,
                                 IntSupplier get) {
        return integer(b, name, min, max, fallback, set, get, unit(name, "blocks", min));
    }

    /** A distance up to {@link FarConfig#MAX_DISTANCE}, then no limit at the right end, as {@link FarConfigScreen#reach}. */
    private OptionBuilder reach(ConfigBuilder b, String name, int fallback, IntConsumer set, IntSupplier get) {
        int top = FarConfig.MAX_DISTANCE + 1;
        IntSupplier shown = () -> {
            int value = get.getAsInt();
            return value <= 0 ? top : Math.clamp(value, 1, FarConfig.MAX_DISTANCE);
        };
        return b.createIntegerOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setRange(1, top, 1)
                .setValueFormatter(v -> v >= top ? Component.translatable(KEY + name + ".zero")
                        : Component.translatable(KEY + "blocks", typedPast(v, FarConfig.MAX_DISTANCE, Integer.MAX_VALUE, get)))
                .setDefaultValue(fallback <= 0 ? top : Math.clamp(fallback, 1, FarConfig.MAX_DISTANCE))
                .setStorageHandler(storage)
                .setBinding(asking(name, top, beyond(FarConfig.MAX_DISTANCE, Integer.MAX_VALUE,
                        v -> set.accept(v >= top ? 0 : v), get), shown)::accept, shown::getAsInt);
    }

    /** A number in a unit, as {@link FarConfigScreen} writes it ("30s", "6px", "100%"); the zero of a range from 0 as its text. */
    private static ControlValueFormatter unit(String name, String unit, int min) {
        return v -> v == 0 && min == 0 ? Component.translatable(KEY + name + ".zero") : Component.translatable(KEY + unit, v);
    }

    /** A button opening one of the mod's screens, which comes back to Sodium's when closed. */
    private static OptionBuilder button(ConfigBuilder b, String name, Consumer<Screen> open) {
        return b.createExternalButtonOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setScreenConsumer(open);
    }

    private static OptionBuilder opens(ConfigBuilder b, String name, Function<Screen, Screen> screen) {
        return button(b, name, parent -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(screen.apply(parent));
        });
    }

    /** Asks before an action that cannot be undone, then back to Sodium's screen. */
    private static void confirm(Screen parent, String name, Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new Confirm(yes -> {
            if (yes) action.run();
            minecraft.setScreen(parent);
        }, Component.translatable(KEY + name), Component.translatable(KEY + name + ".confirm")));
    }

    /** The game's confirmation, which over Sodium's screen answers "no" to Escape on its release. */
    private static final class Confirm extends ConfirmScreen {
        Confirm(it.unimi.dsi.fastutil.booleans.BooleanConsumer callback, Component title, Component message) {
            super(callback, title, message);
        }

    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && true) {
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
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && true) {
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

    private static Function<ConfigState, Boolean> on(String name) {
        return state -> state.readBooleanOption(id(name));
    }

    /** The switches of the flat pictures: what the pictures' distance and size follow, and the mobs' memory. */
    private static final String[] PICTURED = {"impostors", "impostorEntities", "impostorPlayers"};
    private static final String[] NOT_PLAYERS = {"impostors", "impostorEntities"};

    /** On while any of these switches is on. */
    private static Function<ConfigState, Boolean> any(String[] names) {
        return state -> {
            for (String name : names) {
                if (state.readBooleanOption(id(name))) return true;
            }
            return false;
        };
    }

    private static Identifier[] ids(String[] names) {
        Identifier[] ids = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) ids[i] = id(names[i]);
        return ids;
    }

    private static Component tooltip(String name) {
        return Component.translatable(KEY + name + ".tooltip");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, path.toLowerCase(Locale.ROOT));
    }

    /** {@link QualityPreset}, and the settings that match none of them. */
    public enum Preset {
        LOW(QualityPreset.LOW), NORMAL(QualityPreset.NORMAL), HIGH(QualityPreset.HIGH), ULTRA(QualityPreset.ULTRA),
        CUSTOM(null);

        final @Nullable QualityPreset preset;

        Preset(@Nullable QualityPreset preset) {
            this.preset = preset;
        }

        String id() {
            return preset == null ? "custom" : preset.id();
        }

        static Preset of(FarConfig config) {
            QualityPreset current = QualityPreset.of(config);
            for (Preset p : values()) if (p.preset == current) return p;
            return CUSTOM;
        }
    }

    /** {@link FarConfig#birdStyle}, which the file keeps as text. */
    public enum BirdStyle {
        MODELS("3d"), SPRITES("2d");

        final String id;

        BirdStyle(String id) {
            this.id = id;
        }

        static BirdStyle of(String id) {
            return "2d".equals(id) ? SPRITES : MODELS;
        }
    }
}
//?}
