package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Hands Minecraft Comes Alive (MCA) the look of the players who logged off ({@link McaLook}).
 * MCA draws a player as a villager, or dressed as one, when its client holds a villager for
 * that player's id ({@code MCAClient.playerData}), which it asks the server for once and the
 * server only answers while that player is online: a player who logged off before this game
 * saw them was drawn in their plain skin. The puppet of a player who logged off gets a villager
 * made from the look the server shared, or the one this client kept as they left
 * ({@link #seen}). Reached by reflection, so that the mod loads without MCA; any failure turns
 * this off.
 */
public final class Mca {
    /** The villager MCA makes for a player's look, with the same saved data. */
    private static final String VILLAGER = "mca:male_villager";

    private static @Nullable Map<Object, Object> players;
    private static @Nullable Method playerModel;
    private static boolean looked;
    /** The villagers handed to MCA here, by player id, and the look each was made from. */
    private static final Map<UUID, Handed> HANDED = new HashMap<>();
    /** A hash of each villager's look, for the impostors' keys; dropped with the villager. */
    private static final Map<Object, Integer> HASHES = new WeakHashMap<>();

    private record Handed(Object villager, String look, Level level) {
    }

    private Mca() {
    }

    /**
     * How the player of this id looks with MCA, as this client knows it now (they are online,
     * or were seen this session); null when MCA has nothing for them. Kept with a player who
     * logs off, for a server without this mod, which shares nothing of them.
     */
    public static @Nullable String seen(UUID id) {
        Map<Object, Object> known = players();
        if (known == null) return null;
        Object villager = known.get(id);
        if (!(villager instanceof Entity entity)) return null;
        Handed handed = HANDED.get(id);
        if (handed != null && handed.villager() == villager) return handed.look();
        try {
            CompoundTag saved = EntityNbt.save(entity, entity.level());
            // MCA's editor keeps the model chosen (villager, player, vanilla) beside the look,
            // where the villager's own save leaves it out.
            Method model = playerModel;
            if (model != null && model.invoke(villager) instanceof Enum<?> chosen) {
                lookCompound(saved).putInt("PlayerModel", chosen.ordinal());
            }
            return McaLook.trim(saved);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    /**
     * Makes the player of this id look as {@code look} says to MCA, unless MCA already knows how
     * they look from its server. Called as the puppet of a player who logged off is made.
     */
    public static void dress(Level level, UUID id, @Nullable String look) {
        if (look == null) return;
        Map<Object, Object> known = players();
        if (known == null) return;
        Handed handed = HANDED.get(id);
        Object current = known.get(id);
        if (current != null && (handed == null || handed.villager() != current)) return;
        if (handed != null && handed.look().equals(look) && handed.level() == level) return;
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(VILLAGER)).orElse(null);
            Entity villager = type == null ? null : Puppets.create(type, level);
            if (villager == null) return;
            EntityNbt.load(villager, level, TagParser.parseCompoundFully(look));
            known.put(id, villager);
            HANDED.put(id, new Handed(villager, look, level));
        } catch (Exception | LinkageError error) {
            LivingHorizonClient.LOGGER.debug("Living Horizon: the MCA look of a player who logged off did not read", error);
        }
    }

    /**
     * A number that changes with the MCA look of this player, 0 for none: two players of one
     * skin drawn differently by MCA must not share an impostor.
     */
    public static int lookHash(Entity entity) {
        if (!(entity instanceof Player)) return 0;
        Map<Object, Object> known = players();
        if (known == null) return 0;
        Object villager = known.get(entity.getUUID());
        if (villager == null) return 0;
        Integer hash = HASHES.get(villager);
        if (hash == null) {
            String look = seen(entity.getUUID());
            hash = look == null ? villager.getClass().hashCode() : look.hashCode();
            HASHES.put(villager, hash);
        }
        return hash;
    }

    /** Forgets the villagers made here, as the client leaves a server: MCA clears its own then. */
    public static void forget() {
        HANDED.clear();
    }

    /** The compound MCA keeps its look in: from 26.2 one of its own ({@code MCAData}), before the save itself. */
    private static CompoundTag lookCompound(CompoundTag saved) {
        Tag nested = saved.get("MCAData");
        return nested instanceof CompoundTag compound ? compound : saved;
    }

    private static @Nullable Map<Object, Object> players() {
        if (!looked) find();
        return players;
    }

    private static void find() {
        looked = true;
        if (!McaLook.present()) return;
        for (String root : McaLook.PACKAGES) {
            try {
                Class<?> client = Class.forName(root + "MCAClient");
                if (client.getField("playerData").get(null) instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked") Map<Object, Object> known = (Map<Object, Object>) map;
                    players = known;
                }
                try {
                    playerModel = Class.forName(root + "entity.VillagerLike").getMethod("getPlayerModel");
                } catch (ReflectiveOperationException none) {
                    // An MCA without player models: always a villager.
                }
                if (players != null) return;
            } catch (ClassNotFoundException missing) {
                // The other package.
            } catch (ReflectiveOperationException | LinkageError error) {
                players = null;
                break;
            }
        }
        LivingHorizonClient.LOGGER.warn("Minecraft Comes Alive found, but not the version this mod knows: the players "
                + "who logged off keep their own skin");
    }
}
