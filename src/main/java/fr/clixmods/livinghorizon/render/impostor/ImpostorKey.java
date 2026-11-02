package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >=1.20.5
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What decides which picture a figure gets: its type, and everything about it that
 * changes how it looks - skin, variant, colour, size, profession, what it wears. Two
 * figures with the same key share one sheet: every pig of one skin, every zombie with
 * the same helmet and sword.
 *
 * <p>What a figure carries and never shows is left out: where its bed is, who owns it,
 * when its anger ends, its name, how worn its armour is. So is what keeps changing on it, a counter or a flag
 * that comes and goes (a dolphin drying, a spider climbing, a zombie raising its arms):
 * it is learnt per kind of mob, from the figures seen changing it again and again.
 * Counted in, it gave the same figure a new sheet every few seconds, and a crowd of them
 * more sheets than the pages hold, baked over and over.
 *
 * @param type       the entity type's id
 * @param appearance a hash of the rest
 */
public record ImpostorKey(String type, long appearance) {
    /** Milliseconds a figure's key is kept before it is worked out again. */
    private static final long KEEP = 5000;

    /** Milliseconds within which a value changing twice on one figure is taken as restless. */
    private static final long RESTLESS_WITHIN = 60_000;

    /** When a value of a figure last changed, and which of its bits. */
    private record Change(long when, long bits) {
    }

    /**
     * A figure's key, the values it was worked out from, and when each of them last changed.
     */
    private record Known(ImpostorKey key, long when, Map<Integer, Object> values, Map<Integer, Change> changes) {
    }

    private static final Map<Entity, Known> KNOWN = new IdentityHashMap<>();

    /**
     * Per kind of mob, the bits of each synched value seen changing on one figure again and
     * again, -1 for the whole value: left out of its key. Kept with the sheets on disk
     * ({@link ImpostorDisk}), so that a key worked out in the next game is the same.
     */
    private static final Map<String, Map<Integer, Long>> RESTLESS = new HashMap<>();
    /** Bumped whenever {@link #RESTLESS} learns something, to know when to save it. */
    private static int learnt;

    /** Identity hashes in a value's text, which change from one game to the next. */
    private static final Pattern IDENTITY = Pattern.compile("@[0-9a-f]{4,}");

    /** Names the game draws differently: upside down, in rainbow colours, as Toast. */
    private static final Set<String> SPECIAL_NAMES = Set.of("Dinnerbone", "Grumm", "jeb_", "Toast");

    /**
     * Synched data that changes all the time and never the picture: health, air, score, the
     * arrows stuck in it... Counted in, a hurt player or a mob holding its breath would ask
     * for a new sheet every few seconds.
     */
    private static final List<EntityDataAccessor<?>> IGNORED = List.of(
            Entity.DATA_AIR_SUPPLY_ID, Entity.DATA_CUSTOM_NAME_VISIBLE, Entity.DATA_SILENT, Entity.DATA_NO_GRAVITY,
            Entity.DATA_TICKS_FROZEN);
    private static final List<EntityDataAccessor<?>> IGNORED_LIVING = List.of(
            LivingEntity.DATA_LIVING_ENTITY_FLAGS, LivingEntity.DATA_HEALTH_ID, LivingEntity.DATA_EFFECT_PARTICLES,
            LivingEntity.DATA_EFFECT_AMBIENCE_ID, LivingEntity.DATA_ARROW_COUNT_ID, LivingEntity.DATA_STINGER_COUNT_ID);
    private static final List<EntityDataAccessor<?>> IGNORED_PLAYER = List.of(
            Player.DATA_PLAYER_ABSORPTION_ID, Player.DATA_SCORE_ID);

    /** The shared flags that change nothing on the picture: on fire, sprinting, glowing. */
    private static final int UNSEEN_FLAGS = 1 | 1 << 3 | 1 << 6;

    /** Postures a figure's picture is baked in: standing, as most are, seated, or lying down. */
    public static final int STANDING = 0, SEATED = 1, LYING = 2;

    /**
     * The posture of the keys of figures not standing: players who logged off, sitting on the
     * ground, lying down or in a bed, and riders baked with their mount ({@link #riding}).
     */
    private static final Map<ImpostorKey, Integer> POSTURES = new HashMap<>();
    /** Mixed into the key of a figure not standing, so that it never meets the same figure standing. */
    private static final long POSTURE_SALT = 0x5EA7EDL;

    /**
     * The key of a figure: its look, worked out at most every few seconds, since figures
     * hardly ever change; and its posture, which changes often, every time.
     */
    public static ImpostorKey of(Entity entity) {
        ImpostorKey key = look(entity);
        int posture = posture(entity);
        if (posture == STANDING) return key;
        return posed(new ImpostorKey(key.type, key.appearance * 31 + POSTURE_SALT * posture), posture);
    }

    /**
     * The key of a rider and its mount baked together: both their looks, where the rider sits
     * on it and the way it turns, to a sixteenth of a block and fifteen degrees. Filed as a
     * player's, never kept on disk.
     */
    public static ImpostorKey riding(Entity rider, Entity mount, Vec3 seat, float yaw) {
        long hash = look(mount).appearance * 31 + look(rider).appearance;
        hash = hash * 31 + Math.round(seat.x * 16);
        hash = hash * 31 + Math.round(seat.y * 16);
        hash = hash * 31 + Math.round(seat.z * 16);
        hash = hash * 31 + Math.round(Mth.wrapDegrees(yaw) / 15f);
        return new ImpostorKey(EntityType.getKey(EntityType.PLAYER).toString(), hash * 31 + type(mount).hashCode());
    }

    private static ImpostorKey posed(ImpostorKey key, int posture) {
        if (POSTURES.size() > 4096) POSTURES.clear();
        POSTURES.put(key, posture);
        return key;
    }

    /** A player who logged off: sitting on the ground or in a bed; standing otherwise. */
    public static int posture(Entity entity) {
        if (!(entity instanceof RestingPuppet puppet)) return STANDING;
        return switch (puppet.look()) {
            case SIT -> SEATED;
            case BED -> LYING;
            default -> STANDING;
        };
    }

    /** The posture this key's picture is baked in. */
    public static int posture(ImpostorKey key) {
        return POSTURES.getOrDefault(key, STANDING);
    }

    private static ImpostorKey look(Entity entity) {
        long now = System.currentTimeMillis();
        Known known = KNOWN.get(entity);
        if (known != null && now - known.when < KEEP) return known.key;
        if (KNOWN.size() > 4096) KNOWN.clear();
        Map<Integer, Object> values = values(entity);
        Map<Integer, Change> changes = known == null ? new HashMap<>() : known.changes;
        if (known != null) learn(type(entity), known.values, values, changes, now);
        ImpostorKey key = compute(entity, values);
        KNOWN.put(entity, new Known(key, now, values, changes));
        return key;
    }

    public static void forgetAll() {
        KNOWN.clear();
        POSTURES.clear();
    }

    /** What has been learnt to leave out, a copy: by kind of mob, then by synched value. */
    public static Map<String, Map<Integer, Long>> restless() {
        Map<String, Map<Integer, Long>> copy = new HashMap<>();
        RESTLESS.forEach((type, masks) -> copy.put(type, new HashMap<>(masks)));
        return copy;
    }

    /** Takes back what an earlier game learnt, on top of what this one has. */
    public static void restore(Map<String, Map<Integer, Long>> saved) {
        saved.forEach((type, masks) -> masks.forEach((id, mask) ->
                RESTLESS.computeIfAbsent(type, ignoredType -> new HashMap<>()).merge(id, mask, (x, y) -> x | y)));
        KNOWN.clear();
    }

    /** Changes whenever something new is learnt. */
    public static int learnt() {
        return learnt;
    }

    private static String type(Entity entity) {
        return EntityType.getKey(entity.getType()).toString();
    }

    /** The synched values of a figure that may show on its picture, by id. */
    private static Map<Integer, Object> values(Entity entity) {
        Map<Integer, Object> values = new HashMap<>();
        List<SynchedEntityData.DataValue<?>> all = entity.getEntityData().getNonDefaultValues();
        if (all == null) return values;
        for (SynchedEntityData.DataValue<?> value : all) {
            if (ignored(entity, value.id())) continue;
            Object data = seen(entity, value.id(), value.value());
            if (data != null) values.put(value.id(), data);
        }
        return values;
    }

    /**
     * What of a synched value may show on the picture, or null for nothing. Judged by its
     * type, which every version and every mod's mobs share: a position, a time, an owner or
     * a name never changes how a figure is drawn, a carried block does.
     */
    private static @Nullable Object seen(Entity entity, int id, @Nullable Object data) {
        if (data instanceof Optional<?> optional) {
            Object inside = optional.orElse(null);
            if (inside instanceof Component name) return special(name);
            return inside instanceof BlockState ? inside : null;
        }
        if (data instanceof Component name) return special(name);
        // A variant the server sends (a pig's, a wolf's) is a new object in every world: by name,
        // the sheet baked in one world is found again in the next. A sound variant, picked at
        // random, is heard and never seen.
        if (data instanceof Holder<?> holder) {
            Object name = holder.unwrapKey().<Object>map(Object::toString).orElse(holder);
            return name.toString().contains("sound_variant") ? null : name;
        }
        if (data instanceof Long || data instanceof BlockPos || data instanceof UUID) return null;
        // The parrots on a player's shoulders; on a mob, the id of another entity.
        if (data instanceof OptionalInt) return entity instanceof Player ? data : null;
        if (id == Entity.DATA_SHARED_FLAGS_ID.id() && data instanceof Byte flags) return (byte) (flags & ~UNSEEN_FLAGS);
        return data;
    }

    private static @Nullable String special(Component name) {
        String plain = ChatFormatting.stripFormatting(name.getString());
        return plain != null && SPECIAL_NAMES.contains(plain) ? plain : null;
    }

    /**
     * Compares a figure's values with those of its last key: a value changing again within
     * {@link #RESTLESS_WITHIN} is restless for its whole kind of mob, the bits that changed
     * both times for a number or flags, all of it otherwise.
     */
    private static void learn(String type, Map<Integer, Object> before, Map<Integer, Object> after,
                              Map<Integer, Change> changes, long now) {
        Set<Integer> ids = new HashSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (int id : ids) {
            Object was = before.get(id), is = after.get(id);
            if (Objects.equals(was, is)) continue;
            Long a = bits(was), b = bits(is);
            long changed = a != null && b != null ? a ^ b : -1L;
            Change last = changes.get(id);
            if (last != null && now - last.when < RESTLESS_WITHIN) {
                Map<Integer, Long> masks = RESTLESS.computeIfAbsent(type, ignoredType -> new HashMap<>());
                long mask = masks.getOrDefault(id, 0L), more = mask | changed | last.bits;
                if (more != mask) {
                    masks.put(id, more);
                    learnt++;
                }
            }
            changes.put(id, new Change(now, changed));
        }
    }

    /** A number or flags as bits, 0 for a value at its default; null for anything else. */
    private static @Nullable Long bits(@Nullable Object data) {
        if (data == null) return 0L;
        if (data instanceof Boolean flag) return flag ? 1L : 0L;
        if (data instanceof Byte number) return number & 0xFFL;
        if (data instanceof Integer number) return number & 0xFFFFFFFFL;
        return null;
    }

    private static ImpostorKey compute(Entity entity, Map<Integer, Object> values) {
        String type = type(entity);
        Map<Integer, Long> restless = RESTLESS.getOrDefault(type, Map.of());
        long hash = 17;
        if (entity instanceof AbstractClientPlayer player) {
            // A skin was its texture alone before 1.20.2.
            //? if >=1.20.2 {
            hash = hash * 31 + player.getSkin().hashCode();
            //?} else
            /*hash = hash * 31 + player.getSkinTextureLocation().hashCode();*/
        }
        // In the order of the ids: the same values always give the same key.
        for (int id : values.keySet().stream().sorted().toList()) {
            Object data = values.get(id);
            long mask = restless.getOrDefault(id, 0L);
            Long bits = bits(data);
            long seen;
            if (bits != null) {
                // A restless bit is left out, and a value with nothing else set counts as unset.
                seen = bits & ~mask;
                if (seen == 0) continue;
            } else {
                if (mask != 0) continue;
                seen = stable(data);
            }
            hash = hash * 31 + id;
            hash = hash * 31 + seen;
        }
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) hash = hash * 31 + item(living.getItemBySlot(slot));
        }
        return new ImpostorKey(type, hash);
    }

    /**
     * A hash of a value that is the same from one game to the next, which a hash code often
     * is not (an enum's, a registry entry's, an object's identity): the sheets saved on disk
     * are found again by it. Records are hashed through their parts, registry entries by
     * name, anything else by its text.
     */
    private static long stable(@Nullable Object value) {
        if (value == null) return 0;
        if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Character) {
            return value.hashCode();
        }
        if (value instanceof Enum<?> constant) return constant.name().hashCode();
        if (value instanceof Optional<?> optional) return optional.isPresent() ? 31 + stable(optional.get()) : 0;
        if (value instanceof OptionalInt optional) return optional.isPresent() ? 31 + optional.getAsInt() : 0;
        if (value instanceof Holder<?> holder) {
            Optional<?> name = holder.unwrapKey();
            return name.isPresent() ? name.get().toString().hashCode() : stable(holder.value());
        }
        if (value instanceof Record record) {
            try {
                long hash = 7;
                for (RecordComponent part : record.getClass().getRecordComponents()) {
                    hash = hash * 31 + stable(part.getAccessor().invoke(record));
                }
                return hash;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Not readable from here: its text then.
            }
        }
        return text(value);
    }

    private static long text(Object value) {
        return IDENTITY.matcher(String.valueOf(value)).replaceAll("").hashCode();
    }

    /**
     * What an item worn or held looks like: the item and its data, but not how worn it is. A
     * helmet in the sun loses durability every few seconds, which would ask for a new sheet
     * each time.
     */
    private static long item(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        long hash = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().hashCode();
        //? if >=1.20.5 {
        if (stack.has(DataComponents.DAMAGE)) {
            stack = stack.copy();
            stack.remove(DataComponents.DAMAGE);
        }
        return stack.getComponentsPatch().isEmpty() ? hash : hash * 31 + text(stack.getComponentsPatch());
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Damage")) {
            tag = tag.copy();
            tag.remove("Damage");
        }
        return tag == null ? hash : hash * 31 + text(tag);
        *///?}
    }

    /** Ids are only unique along one line of classes: each list is checked for its own class. */
    private static boolean ignored(Entity entity, int id) {
        return listed(IGNORED, id)
                || entity instanceof LivingEntity && listed(IGNORED_LIVING, id)
                || entity instanceof Player && listed(IGNORED_PLAYER, id)
                || entity instanceof AbstractVillager && AbstractVillager.DATA_UNHAPPY_COUNTER.id() == id;
    }

    private static boolean listed(List<EntityDataAccessor<?>> accessors, int id) {
        for (EntityDataAccessor<?> accessor : accessors) {
            if (accessor.id() == id) return true;
        }
        return false;
    }
}
