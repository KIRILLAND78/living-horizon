package fr.clixmods.livinghorizon.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//? if fabric || quilt {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
//?} elif neoforge {
/*import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
*///?} else {
/*import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
*///?}
//? if neoforge && >=1.21.5 {
/*import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
*///?} elif neoforge {
/*import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
*///?}
//? if forge && >=1.21.6 {
/*import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
*///?} elif forge {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}

/**
 * The game's events the mod listens to, under one name on every loader. Fabric and Quilt
 * read the Fabric API's; NeoForge and Forge read their own. What the mod does with each
 * one is the same everywhere, so it is written once, in the callers.
 */
public final class Events {
    private Events() {
    }

    //? if neoforge {
    /*private static IEventBus modBus;

    /^* NeoForge hands the mod its own event bus at construction; key mappings are registered on it. ^/
    public static void init(IEventBus bus) {
        modBus = bus;
    }
    *///?} elif forge && >=1.21.6 {
    /*private static BusGroup modBus;

    /^* Forge hands the mod its own bus group at construction; its lifecycle events are on it. ^/
    public static void init(BusGroup group) {
        modBus = group;
    }
    *///?} elif forge {
    /*private static IEventBus modBus;

    /^* Before Forge 56 the mod has one event bus of its own; its lifecycle events are on it. ^/
    public static void init(IEventBus bus) {
        modBus = bus;
    }
    *///?}

    /** What a screen's widgets can be asked and told, whichever way the loader hands them over. */
    public interface Widgets {
        /** The screen's widgets, in the order the game keeps them. */
        List<AbstractWidget> list();

        void add(AbstractWidget widget);

        void remove(AbstractWidget widget);
    }

    /** Registers a key, which the player finds in the Controls screen. */
    public static KeyMapping register(KeyMapping key) {
        //? if fabric || quilt {
        //? if >=26.1 {
        /*return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(key);
        *///?} else {
        return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(key);
        //?}
        //?} elif neoforge {
        /*modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(key));
        return key;
        *///?} elif forge && >=1.21.10 {
        /*RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(key));
        return key;
        *///?} elif forge && >=1.21.6 {
        /*// Before Forge 60 a mod bus event is reached through the mod's own bus group.
        RegisterKeyMappingsEvent.getBus(modBus).addListener(event -> event.register(key));
        return key;
        *///?} elif forge {
        /*modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        return key;
        *///?}
    }

    /** Every client tick, after the game has done its own. */
    /**
     * The game's overlay drawn, over the world and under the screens, where it has no
     * {@code Gui.render} of its own: Forge before 1.20.6 draws it with {@code ForgeGui}, whose
     * {@code render} replaces the game's without calling it, so {@code GuiMixin} never runs there.
     * Elsewhere {@code GuiMixin} draws it, and this does nothing.
     */
    public static void hud(Consumer<GuiGraphics> listener) {
        //? if forge && <1.20.6 {
        /*MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGuiEvent.Post event) ->
                listener.accept(event.getGuiGraphics()));
        *///?}
    }

    public static void tick(Consumer<Minecraft> listener) {
        //? if fabric || quilt {
        ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.accept(Minecraft.getInstance()));
        *///?} elif forge && >=1.20.4 {
        /*TickEvent.ClientTickEvent.Post.BUS.addListener(event -> listener.accept(Minecraft.getInstance()));
        *///?} elif forge {
        /*// Before Forge 49 (1.20.4) one event stands for both ends of the tick.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) listener.accept(Minecraft.getInstance());
        });
        *///?}
    }

    /** Once, when the game has started and every mod has registered what it adds. */
    public static void started(Runnable listener) {
        //? if fabric || quilt {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> listener.run());
        //?} elif neoforge && >=1.21.5 {
        /*NeoForge.EVENT_BUS.addListener(ClientStartedEvent.class, event -> listener.run());
        *///?} elif neoforge {
        /*// NeoForge has client lifecycle events from 21.5; before, the mod bus says when loading is done.
        modBus.addListener(FMLLoadCompleteEvent.class, event -> listener.run());
        *///?} elif forge && >=1.21.6 {
        /*FMLLoadCompleteEvent.getBus(modBus).addListener(event -> listener.run());
        *///?} elif forge {
        /*modBus.addListener((FMLLoadCompleteEvent event) -> listener.run());
        *///?}
    }

    /** When the game is closing. */
    public static void stopping(Runnable listener) {
        //? if fabric || quilt {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> listener.run());
        //?} elif neoforge && >=1.21.5 {
        /*NeoForge.EVENT_BUS.addListener(ClientStoppingEvent.class, event -> listener.run());
        *///?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, event -> listener.run());
        *///?} elif forge {
        /*GameShuttingDownEvent.BUS.addListener(event -> listener.run());
        *///?}
    }

    /** When the player leaves a world or a server. */
    public static void disconnect(Runnable listener) {
        //? if fabric || quilt {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.run());
        *///?} elif forge {
        /*ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> listener.run());
        *///?}
    }

    /** An entity joins the client's world. */
    public static void entityLoad(BiConsumer<Entity, ClientLevel> listener) {
        //? if fabric || quilt {
        ClientEntityEvents.ENTITY_LOAD.register(listener::accept);
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel() instanceof ClientLevel level) listener.accept(event.getEntity(), level);
        });
        *///?} elif forge && >=1.21.6 {
        /*// The event can be cancelled; a listener that never cancels it says so by its priority.
        EntityJoinLevelEvent.BUS.addListener(Priority.NORMAL, event -> {
            if (event.getLevel() instanceof ClientLevel level) listener.accept(event.getEntity(), level);
        });
        *///?} elif forge {
        /*MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ClientLevel level) listener.accept(event.getEntity(), level);
        });
        *///?}
    }

    /** An entity leaves the client's world. */
    public static void entityUnload(BiConsumer<Entity, ClientLevel> listener) {
        //? if fabric || quilt {
        ClientEntityEvents.ENTITY_UNLOAD.register(listener::accept);
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
            if (event.getLevel() instanceof ClientLevel level) listener.accept(event.getEntity(), level);
        });
        *///?} elif forge {
        /*EntityLeaveLevelEvent.BUS.addListener(event -> {
            if (event.getLevel() instanceof ClientLevel level) listener.accept(event.getEntity(), level);
        });
        *///?}
    }

    /** A screen has been laid out, or laid out again. */
    public static void screenInit(BiConsumer<Screen, Widgets> listener) {
        //? if fabric || quilt {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            //? if >=26.1 {
            /*List<AbstractWidget> buttons = Screens.getWidgets(screen);
            *///?} else {
            List<AbstractWidget> buttons = Screens.getButtons(screen);
            //?}
            listener.accept(screen, new Widgets() {
                @Override
                public List<AbstractWidget> list() {
                    return buttons;
                }

                @Override
                public void add(AbstractWidget widget) {
                    buttons.add(widget);
                }

                @Override
                public void remove(AbstractWidget widget) {
                    buttons.remove(widget);
                }
            });
        });
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> listener.accept(event.getScreen(), new Widgets() {
            @Override
            public List<AbstractWidget> list() {
                return event.getListenersList().stream()
                        .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
            }

            @Override
            public void add(AbstractWidget widget) {
                event.addListener(widget);
            }

            @Override
            public void remove(AbstractWidget widget) {
                event.removeListener(widget);
            }
        }));
        *///?} elif forge {
        /*ScreenEvent.Init.Post.BUS.addListener(event -> listener.accept(event.getScreen(), new Widgets() {
            @Override
            public List<AbstractWidget> list() {
                return event.getListenersList().stream()
                        .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
            }

            @Override
            public void add(AbstractWidget widget) {
                event.addListener(widget);
            }

            @Override
            public void remove(AbstractWidget widget) {
                event.removeListener(widget);
            }
        }));
        *///?}
    }
}
