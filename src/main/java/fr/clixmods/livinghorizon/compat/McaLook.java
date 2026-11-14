package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * How a player looks with Minecraft Comes Alive (MCA): its player model can be a villager's, or
 * a player's dressed and shaped as one, chosen in its editor. MCA keeps it with each player on
 * the server, as a villager's saved data, and hands it to a client only while that player is
 * online, so a player who logged off was drawn in their plain skin. The server shares that
 * data with the players who logged off ({@code PositionsMessage.Look#mca}); the client hands
 * it back to MCA ({@code compat/Mca}).
 *
 * <p>Only what is small is kept ({@link #trim}): MCA's look is a few names, numbers and colours,
 * while its relations, memories, inventory and the prompt of its chat would be resent every few
 * seconds for nothing. Reached by reflection, so that the mod loads without MCA; any failure
 * turns the server's side off. Loaded on a dedicated server: nothing here may reach a class of
 * the client's.
 */
public final class McaLook {
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    /** MCA's mod id, the same on every loader and version. */
    public static final String MOD = "mca";
    /** Its classes moved to {@code net.conczin.mca} after 1.20.1. */
    public static final List<String> PACKAGES = List.of("net.conczin.mca.", "net.mca.");
    /** Past this, a saved value is not part of the look: an inventory, a memory, a long text. */
    private static final int MAX_VALUE = 1024;
    /** Past this, the look is left out: a message's text is at most 16 000 characters. */
    public static final int MAX_LOOK = 12_000;

    private static @Nullable Method getIfPresent;
    private static @Nullable Method isSet;
    private static @Nullable Method data;
    private static boolean looked;

    private McaLook() {
    }

    /** Whether MCA is installed. */
    public static boolean present() {
        return Platform.isModLoaded(MOD);
    }

    /**
     * How the player of this id looks with MCA, as {@link #trim} keeps it; null without MCA, or
     * for a player who never chose a look in it. Server thread only.
     */
    public static @Nullable String of(MinecraftServer server, UUID id) {
        if (!looked) find();
        Method get = getIfPresent;
        if (get == null) return null;
        try {
            ServerLevel overworld = server.overworld();
            if (!(get.invoke(null, overworld, id) instanceof Optional<?> saved) || saved.isEmpty()) return null;
            Object player = saved.get();
            if (isSet != null && !Boolean.TRUE.equals(isSet.invoke(player))) return null;
            return data != null && data.invoke(player) instanceof CompoundTag tag ? trim(tag) : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            LOGGER.warn("Minecraft Comes Alive found, but not the version this mod knows: the look of the players "
                    + "who logged off is not shared", error);
            getIfPresent = null;
            return null;
        }
    }

    /**
     * A villager's saved data, without what is too large to be part of its look (a list or a
     * text longer than {@link #MAX_VALUE}), as text; null when what is left is still longer than
     * {@link #MAX_LOOK}. Compounds are looked into: from 26.2 MCA keeps all of its own under one.
     */
    public static @Nullable String trim(CompoundTag saved) {
        String text = small(saved).toString();
        return text.length() <= MAX_LOOK ? text : null;
    }

    private static CompoundTag small(CompoundTag saved) {
        CompoundTag kept = new CompoundTag();
        for (String key : keys(saved)) {
            Tag value = saved.get(key);
            if (value instanceof CompoundTag compound) {
                kept.put(key, small(compound));
            } else if (value != null && value.toString().length() <= MAX_VALUE) {
                kept.put(key, value.copy());
            }
        }
        return kept;
    }

    private static Iterable<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        return List.copyOf(tag.keySet());
        //?} else
        /*return List.copyOf(tag.getAllKeys());*/
    }

    private static void find() {
        looked = true;
        if (!present()) return;
        for (String root : PACKAGES) {
            try {
                Class<?> saved = Class.forName(root + "server.world.data.PlayerSaveData");
                for (Method method : saved.getMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals("getIfPresent") && parameters.length == 2
                            && parameters[1] == UUID.class && parameters[0].isAssignableFrom(ServerLevel.class)) {
                        getIfPresent = method;
                    }
                }
                isSet = saved.getMethod("isEntityDataSet");
                data = saved.getMethod("getEntityData");
                if (getIfPresent == null) throw new NoSuchMethodException("PlayerSaveData.getIfPresent");
                return;
            } catch (ClassNotFoundException missing) {
                // The other package.
            } catch (ReflectiveOperationException | LinkageError error) {
                getIfPresent = null;
                LOGGER.warn("Minecraft Comes Alive found, but not the version this mod knows: the look of the players "
                        + "who logged off is not shared", error);
                return;
            }
        }
        LOGGER.warn("Minecraft Comes Alive found, but not the version this mod knows: the look of the players "
                + "who logged off is not shared");
    }
}
