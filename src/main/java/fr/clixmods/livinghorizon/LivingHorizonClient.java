package fr.clixmods.livinghorizon;

import com.mojang.blaze3d.platform.InputConstants;
import fr.clixmods.livinghorizon.command.LivingHorizonCommand;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.BenchmarkWorld;
import fr.clixmods.livinghorizon.debug.Allocation;
import fr.clixmods.livinghorizon.debug.DebugHud;
import fr.clixmods.livinghorizon.debug.ProfileHook;
import fr.clixmods.livinghorizon.platform.ClientNetwork;
import fr.clixmods.livinghorizon.platform.Events;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.render.Occlusion;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.MobList;
import fr.clixmods.livinghorizon.ui.OfflineHud;
import fr.clixmods.livinghorizon.ui.OptionsButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if fabric || quilt {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//?} elif neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
*///?} else {
/*import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.util.function.Function;
*///?}

/**
 * Client entry point. A dedicated server never loads it: there, {@code MinecraftServerMixin}
 * shares the positions on its own.
 *
 * <p>Fabric and Quilt find it through {@code fabric.mod.json} and {@code quilt.mod.json}
 * and call {@code onInitializeClient}; NeoForge finds it by its annotation and calls the
 * constructor, on the client only; Forge's annotation has no side, so {@link LivingHorizonCommon}
 * carries it and builds this one on a client only. Every loader ends in the same {@code start}.
 * The common entry point ({@link LivingHorizonCommon}) has run before, on every loader.
 */
//? if fabric || quilt {
public final class LivingHorizonClient implements ClientModInitializer {
//?} elif neoforge {
/*@Mod(value = LivingHorizonClient.MOD_ID, dist = Dist.CLIENT)
public final class LivingHorizonClient {
*///?} else {
/*public final class LivingHorizonClient {
*///?}
    public static final String MOD_ID = "livinghorizon";
    public static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    private static KeyMapping toggle;
    private static KeyMapping settings;

    //? if fabric || quilt {
    @Override
    public void onInitializeClient() {
        start();
    }
    //?} elif neoforge {
    /*public LivingHorizonClient(IEventBus modBus, ModContainer container) {
        Events.init(modBus);
        // The "Configure" button on the mod list, where Fabric has Mod Menu's.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new FarConfigScreen(parent));
        start();
    }
    *///?} elif <1.21.6 {
    /*// Forge before 56 (1.21.6) builds the mod with no argument, and hands it its event bus
    // through the loading context.
    public LivingHorizonClient() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        Events.init(context.getModEventBus());
        // The "Configure" button on the mod list, where Fabric has Mod Menu's. Before Forge 49
        // (1.20.4) the loading context of any mod took it, and the factory was handed the game too.
        //? if >=1.20.4 {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((Function<Screen, Screen>) parent -> new FarConfigScreen(parent)));
        //?} else {
        /^net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new FarConfigScreen(parent)));
        ^///?}
        start();
    }
    *///?} else {
    /*public LivingHorizonClient(FMLJavaModLoadingContext context) {
        Events.init(context.getModBusGroup());
        // The "Configure" button on the mod list, where Fabric has Mod Menu's.
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((Function<Screen, Screen>) parent -> new FarConfigScreen(parent)));
        start();
    }
    *///?}

    private static void start() {
        FarConfig.load();
        ProfileHook.register();
        // What a server with the mod shares, handled on the game's thread.
        Network.receiver(data -> Minecraft.getInstance().execute(() -> FarPlayerTracker.get().receive(data)));
        ClientNetwork.register();
        // Key categories are registered objects from 1.21.9; before, a translation key.
        //? if >=1.21.9 {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        //?} else {
        /*String category = "key.category.livinghorizon.main";
        *///?}
        toggle = Events.register(new KeyMapping(
                "key.livinghorizon.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        settings = Events.register(new KeyMapping(
                "key.livinghorizon.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

        Events.tick(LivingHorizonClient::tick);
        Events.hud(LivingHorizonClient::drawHud);
        OptionsButton.register();
        // Every mod has registered its mobs by now: kinds met for the first time are shown far away.
        Events.started(() -> FarConfig.adoptNewMobs(MobList.all()));
        // Voxy's world cannot close while the readers still hold its sections.
        Events.disconnect(LodWorld::suspend);
        Events.stopping(LodWorld::suspend);
        Events.entityLoad((entity, level) -> FarPlayerTracker.get().mobs().onLoad(entity));
        Events.entityUnload((entity, level) -> FarPlayerTracker.get().mobs().onUnload(entity, level));
        //? if fabric || quilt {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                LivingHorizonCommand.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>tree(
                        source -> new LivingHorizonCommand.Source() {
                            @Override
                            public Minecraft client() {
                                return source.getClient();
                            }

                            @Override
                            public net.minecraft.world.entity.player.Player player() {
                                return source.getPlayer();
                            }

                            @Override
                            public void feedback(Component message) {
                                source.sendFeedback(message);
                            }
                        })));
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
                event -> event.getDispatcher().register(LivingHorizonCommand.forGame()));
        *///?} elif forge {
        /*RegisterClientCommandsEvent.BUS.addListener(event -> event.getDispatcher().register(LivingHorizonCommand.forGame()));
        *///?}
        LOGGER.info("Living Horizon loaded");
    }

    /**
     * The debug panel (and, before 1.21.9, the depth view it draws) and who a player who logged
     * off is, under the crosshair: over the world, under the screens. From {@code GuiMixin}, or
     * Forge's own overlay event where the game's {@code Gui.render} never runs ({@link Events#hud}).
     */
    public static void drawHud(GuiGraphics graphics) {
        OfflineHud.draw(graphics);
        DebugHud.draw(graphics);
    }

    private static void tick(Minecraft minecraft) {
        while (toggle.consumeClick()) {
            FarConfig config = FarConfig.get();
            config.enabled = !config.enabled;
            FarConfig.save();
            if (minecraft.player != null) {
                Component message = Component.translatable(
                        config.enabled ? "livinghorizon.toggle.on" : "livinghorizon.toggle.off");
                //? if >=26.1 {
                /*minecraft.player.sendOverlayMessage(message);
                *///?} else {
                minecraft.player.displayClientMessage(message, true);
                //?}
            }
        }
        while (settings.consumeClick()) {
            minecraft.setScreen(new FarConfigScreen(minecraft.screen));
        }
        BenchmarkWorld.tick(minecraft);
        long started = System.nanoTime();
        long allocated = Allocation.begin();
        FarPlayerTracker.get().tick(minecraft);
        if (minecraft.level != null) {
            LodWorld.tick();
            long occluding = Profile.begin(Profile.Section.TICK_OCCLUSION);
            try {
                Occlusion.tick(minecraft.gameRenderer.getMainCamera().position());
            } finally {
                Profile.end(Profile.Section.TICK_OCCLUSION, occluding);
            }
        }
        Allocation.end(allocated);
        Stats.tick(System.nanoTime() - started);
    }
}
