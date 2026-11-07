package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import com.google.common.reflect.TypeToken;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.track.Vehicles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Every kind of mob in the game, every boat, and the other vehicles met so far, for the list
 * of what is shown far away.
 */
public final class MobList {
    private MobList() {
    }

    /**
     * Every living kind has default attributes, which is how they are told apart here:
     * {@code EntityType.getBaseClass()} answers {@code Entity} for all of them. Players and
     * armour stands are living but not mobs; mannequins are kept, for the skinned figures
     * builds and mods (Distant Friends) leave in the world. A vehicle is only known as one once
     * met ({@link FarConfig#knownVehicles}).
     */
    public static List<EntityType<?>> all() {
        FarConfig config = FarConfig.get();
        List<EntityType<?>> mobs = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (DefaultAttributes.hasSupplier(type) && type != EntityType.PLAYER
                    && type != EntityType.ARMOR_STAND) {
                mobs.add(type);
                continue;
            }
            String id = EntityType.getKey(type).toString();
            if (MobKinds.isBoat(id)) {
                mobs.add(type);
                continue;
            }
            Vehicles.Kind kind = vehicle(type);
            if (kind != Vehicles.Kind.NONE) config.knowVehicle(id, kind == Vehicles.Kind.STEERED);
            if (config.knownVehicles.contains(id)) mobs.add(type);
        }
        return mobs;
    }

    /**
     * What a kind is as a vehicle, before one is met: the entity class its renderer is declared
     * for tells, as Immersive Aircraft's {@code BiplaneEntityRenderer} names {@code BiplaneEntity}.
     * The game's minecarts share a renderer declared for every minecart: the rideable one is
     * named. A renderer declared for any entity tells nothing; one of that kind met tells then
     * ({@code MobMemory}). Nothing before the game has made its renderers.
     */
    private static Vehicles.Kind vehicle(EntityType<?> type) {
        //? if >=1.21.11 {
        if (type == EntityType.MINECART) return Vehicles.kindOf(net.minecraft.world.entity.vehicle.minecart.Minecart.class);
        //?} else
        /*if (type == EntityType.MINECART) return Vehicles.kindOf(net.minecraft.world.entity.vehicle.Minecart.class);*/
        Minecraft minecraft = Minecraft.getInstance();
        var dispatcher = minecraft == null ? null : minecraft.getEntityRenderDispatcher();
        Object renderer = dispatcher == null ? null : dispatcher.renderers.get(type);
        return renderer == null ? Vehicles.Kind.NONE : DRAWS.get(renderer.getClass());
    }

    /** What the entities a renderer class is declared for are as vehicles, asked once per class. */
    private static final ClassValue<Vehicles.Kind> DRAWS = new ClassValue<>() {
        @Override
        protected Vehicles.Kind computeValue(Class<?> renderer) {
            try {
                return Vehicles.kindOf(TypeToken.of(renderer)
                        .resolveType(EntityRenderer.class.getTypeParameters()[0]).getRawType());
            } catch (RuntimeException | LinkageError e) {
                return Vehicles.Kind.NONE;
            }
        }
    };

    /**
     * Whether a kind of {@link #all} goes under the mobs' tab: a living kind, mannequins
     * excepted. Boats, vehicles and mannequins go under the entities' tab.
     */
    public static boolean creature(EntityType<?> type) {
        return MobKinds.creature(type);
    }

    /** A living kind of {@link #all}, mannequins included: not a boat nor another vehicle. */
    public static boolean alive(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type);
    }
}
