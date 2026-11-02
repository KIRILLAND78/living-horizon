package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
//? if >=1.21.9
import net.minecraft.client.entity.ClientAvatarState;
//? if >=1.21.9
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
//? if >=1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
//? if >=1.21.9
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The entities the mod makes to draw what is not loaded (distant players, remembered mobs,
 * birds, the saucer's cow) are never added to a level. Up to 26.1 every entity numbered
 * itself when it was made; from 26.2 only the level numbers it, and drawing one without a
 * number fails. Each gets its own, counting down from far below the numbers the server
 * hands out, so that none is ever taken for a real entity.
 */
public final class Puppets {
    private static int next = -1_000_000;

    private Puppets() {
    }

    /** The entity, numbered if the game no longer does it. Render thread only. */
    public static <T extends Entity> @Nullable T numbered(@Nullable T entity) {
        //? if >=26.2 {
        /*if (entity != null) entity.setId(next--);
        *///?}
        return entity;
    }

    /** A new entity of this type, as if loaded from a save, numbered. Render thread only. */
    public static <T extends Entity> @Nullable T create(EntityType<T> type, Level level) {
        //? if >=1.21.2 {
        return numbered(type.create(level, EntitySpawnReason.LOAD));
        //?} else
        /*return numbered(type.create(level));*/
    }

    /**
     * Puts a puppet's hop at rest (a slime's squash, a rabbit's kick, a chicken's wings) while
     * its impostor is baked, so that the pictures show it standing. Gives back what undoes it.
     */
    public static Runnable atRest(Entity entity) {
        return MobGait.atRest(entity);
    }

    /**
     * A player of this profile, numbered, with every skin layer on. One the server does not
     * know wears the default skin the game picks from its id. Render thread only.
     */
    public static RemotePlayer player(ClientLevel level, GameProfile profile) {
        RemotePlayer player = numbered(new RemotePlayer(level, profile));
        player.getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        return player;
    }

    /**
     * Gives a mannequin the skin its profile was looked up to. The game hands it over in the
     * mannequin's tick, which a puppet never runs: without this, every mannequin drawn far away
     * wears the default skin. Does nothing to anything else, or while the lookup runs.
     */
    public static void skin(Entity entity) {
        //? if >=1.21.9 {
        if (!(entity instanceof ClientMannequin mannequin)) return;
        CompletableFuture<Optional<PlayerSkin>> lookup = mannequin.skinLookup;
        if (lookup == null || !lookup.isDone()) return;
        mannequin.skinLookup = null;
        try {
            lookup.join().ifPresent(mannequin::setSkin);
        } catch (RuntimeException failed) {
            // No skin to be found: the default one stays, as on the game's own mannequin.
        }
        //?}
    }

    /**
     * Hangs a mannequin's cape from where it stands. The game moves the cape after the mannequin
     * in its tick, which a puppet never runs: it stays at 0, 0, 0, and the cape reaches out
     * towards there. Set outright, since the tick only takes it a quarter of the way when it is
     * less than ten blocks off along an axis, and each view of an impostor is baked from a single
     * call. A real mannequin drawn far away loses only its cape's lag, as it stands still. Does
     * nothing to anything else.
     */
    public static void cape(Entity entity) {
        //? if >=1.21.9 {
        if (!(entity instanceof ClientMannequin mannequin)) return;
        ClientAvatarState state = mannequin.avatarState();
        state.xCloak = mannequin.getX();
        state.yCloak = mannequin.getY();
        state.zCloak = mannequin.getZ();
        state.xCloakO = mannequin.xo;
        state.yCloakO = mannequin.yo;
        state.zCloakO = mannequin.zo;
        //?}
    }

    /**
     * Moves the legs as if the entity had walked this much; {@code scale} speeds a baby's
     * up, which the renderer itself did before 1.21.2.
     */
    public static void walk(LivingEntity entity, float speed, float multiplier, float scale) {
        //? if >=1.21.2 {
        entity.walkAnimation.update(speed, multiplier, scale);
        //?} else
        /*entity.walkAnimation.update(speed, multiplier);*/
    }
}
