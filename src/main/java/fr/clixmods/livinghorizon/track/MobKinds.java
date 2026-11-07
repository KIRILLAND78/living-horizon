package fr.clixmods.livinghorizon.track;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The mobs a server with the mod shares ({@link PositionPublisher}), numbered: kind 1 is the
 * first of {@link #TYPES}. The numbers travel in {@link PositionsMessage}, so a kind is only
 * ever added at the end, never moved.
 */
public final class MobKinds {
    public static final List<String> TYPES = List.of(
            "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama", "minecraft:camel",
            "minecraft:cow", "minecraft:mooshroom", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
            "minecraft:rabbit", "minecraft:goat", "minecraft:cat", "minecraft:wolf", "minecraft:fox",
            "minecraft:parrot", "minecraft:panda", "minecraft:polar_bear", "minecraft:turtle", "minecraft:axolotl",
            "minecraft:frog", "minecraft:armadillo", "minecraft:sniffer", "minecraft:bee", "minecraft:ocelot",
            "minecraft:happy_ghast", "minecraft:strider", "minecraft:villager", "minecraft:iron_golem",
            "minecraft:snow_golem", "minecraft:allay",
            // Boats are not mobs, but a parked boat vanishes from far away just the same.
            "minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat", "minecraft:jungle_boat",
            "minecraft:acacia_boat", "minecraft:cherry_boat", "minecraft:dark_oak_boat", "minecraft:pale_oak_boat",
            "minecraft:mangrove_boat", "minecraft:bamboo_raft",
            "minecraft:oak_chest_boat", "minecraft:spruce_chest_boat", "minecraft:birch_chest_boat",
            "minecraft:jungle_chest_boat", "minecraft:acacia_chest_boat", "minecraft:cherry_chest_boat",
            "minecraft:dark_oak_chest_boat", "minecraft:pale_oak_chest_boat", "minecraft:mangrove_chest_boat",
            "minecraft:bamboo_chest_raft");

    /**
     * A mob, as the list of mobs files it: a living kind, mannequins excepted. Boats, vehicles and
     * mannequins are the other entities, with a switch, a limit and a distance of their own.
     */
    public static boolean creature(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type) && !EntityType.getKey(type).toString().equals("minecraft:mannequin");
    }

    /** Every boat and raft, chest or not; before 1.21.2, the one boat of every wood. */
    public static boolean isBoat(String type) {
        return type.endsWith("_boat") || type.endsWith("_raft") || type.equals("minecraft:boat");
    }

    private MobKinds() {
    }

    public static @Nullable String type(int kind) {
        return kind >= 1 && kind <= TYPES.size() ? TYPES.get(kind - 1) : null;
    }

    /** The number a type is shared by, 0 for one a server does not share. */
    public static int kind(String type) {
        return TYPES.indexOf(type) + 1;
    }

    /**
     * The saved field the server reads the variant from, written back the same way so
     * the game itself turns the number into a coat, a fleece, a colour.
     */
    static @Nullable String variantField(String type) {
        return switch (type) {
            case "minecraft:horse", "minecraft:llama", "minecraft:axolotl", "minecraft:parrot" -> "Variant";
            case "minecraft:sheep" -> "Color";
            case "minecraft:rabbit" -> "RabbitType";
            default -> null;
        };
    }

    /** The saved data of a mob known only from the server: its variant, and its age. */
    static String nbt(String type, int variant, boolean baby) {
        StringBuilder nbt = new StringBuilder("{");
        String field = variantField(type);
        if (field != null) {
            nbt.append(field).append(':').append(variant);
            if (field.equals("Color")) nbt.append('b');
        }
        if (baby) {
            if (nbt.length() > 1) nbt.append(',');
            nbt.append("Age:-24000");
        }
        return nbt.append('}').toString();
    }
}
