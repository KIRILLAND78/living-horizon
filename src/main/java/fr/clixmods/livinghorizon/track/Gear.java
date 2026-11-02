package fr.clixmods.livinghorizon.track;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player wears and holds - armour and both hands - as text: one item per slot of
 * {@link #SLOTS}, as the game saves it, empty for an empty slot. Written on a server and read
 * on a client, or kept in a file with a player who logged off. Loaded on a dedicated server too:
 * nothing here may reach a class of the client's.
 */
public final class Gear {
    /** The slots kept, in this order. */
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    /** Past this, an item is left out (a filled shulker box in a hand): a message's text is at most 64 KiB. */
    static final int MAX_ITEM = 8000;

    private Gear() {
    }

    /** What {@code entity} wears and holds now. */
    public static List<String> of(LivingEntity entity, RegistryAccess registries) {
        List<String> gear = new ArrayList<>(SLOTS.size());
        for (EquipmentSlot slot : SLOTS) gear.add(save(entity.getItemBySlot(slot), registries));
        return gear;
    }

    /** Wearing {@code stack} in {@code slot} and nothing else: for test players. */
    public static List<String> only(EquipmentSlot slot, ItemStack stack, RegistryAccess registries) {
        List<String> gear = new ArrayList<>(SLOTS.size());
        for (EquipmentSlot each : SLOTS) gear.add(each == slot ? save(stack, registries) : "");
        return gear;
    }

    /** Whether nothing is worn nor held. */
    public static boolean empty(@Nullable List<String> gear) {
        if (gear == null) return true;
        for (String item : gear) if (!item.isEmpty()) return false;
        return true;
    }

    /** Dresses {@code entity} in {@code gear}; a slot the list leaves out, or cannot read, is emptied. */
    public static void apply(LivingEntity entity, @Nullable List<String> gear, RegistryAccess registries) {
        for (int i = 0; i < SLOTS.size(); i++) {
            String item = gear != null && i < gear.size() ? gear.get(i) : "";
            entity.setItemSlot(SLOTS.get(i), load(item, registries));
        }
    }

    /** One item of {@code gear}, by slot: empty when there is none. */
    public static ItemStack item(@Nullable List<String> gear, EquipmentSlot slot, RegistryAccess registries) {
        int i = SLOTS.indexOf(slot);
        return gear == null || i < 0 || i >= gear.size() ? ItemStack.EMPTY : load(gear.get(i), registries);
    }

    static String save(ItemStack stack, RegistryAccess registries) {
        if (stack.isEmpty()) return "";
        try {
            Tag tag = ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), stack).result().orElse(null);
            String text = tag == null ? "" : tag.toString();
            return text.length() > MAX_ITEM ? "" : text;
        } catch (RuntimeException e) {
            return "";
        }
    }

    static ItemStack load(@Nullable String item, RegistryAccess registries) {
        if (item == null || item.isEmpty()) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseCompoundFully(item);
            return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).result()
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            // An item of a mod this side does not have, or saved by another version.
            return ItemStack.EMPTY;
        }
    }
}
