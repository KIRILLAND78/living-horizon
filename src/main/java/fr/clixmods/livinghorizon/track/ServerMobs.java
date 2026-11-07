package fr.clixmods.livinghorizon.track;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Where the remembered mobs really are, in a single player world.
 *
 * <p>The server stops sending a mob well before it stops moving it: between the distance it
 * sends that kind at (160 blocks for an animal, less with the "Entity Distance" slider) and
 * the simulation distance (a square of chunks, farther still on its diagonals), the mob
 * grazes, wanders and walks to its bed while its copy stands where it was last seen. In a
 * single player world the server runs in this game: it is asked a few times a second where
 * those mobs are, and the copies follow them ({@link MobMotion}). On another server nothing
 * can be asked; a server with the mod shares where they are ({@link PositionPublisher}).
 */
final class ServerMobs {
    /** Ticks between two questions: the copies walk at the server's pace, this many ticks behind. */
    static final int EVERY = 5;

    /** One mob asked about: where the copy has it, and whether the server was moving it at the last answer. */
    record Asked(UUID id, double x, double y, double z, boolean simulated) {
    }

    /**
     * Where the server has a mob, which way it faces, whether it moves it now (its chunk ticks),
     * and whether it sits, for a pet: null for any other mob.
     */
    record Where(double x, double y, double z, float body, float head, boolean simulated, @Nullable Boolean sitting) {
    }

    /** The mobs found, and those that died, despawned or left the dimension where the server was moving them. */
    record Answer(Map<UUID, Where> found, List<UUID> gone) {
    }

    private @Nullable CompletableFuture<Answer> asking;

    /** No question on the way. */
    boolean idle() {
        return asking == null;
    }

    /** The answer to the last question, once, when it came. */
    @Nullable Answer answer() {
        if (asking == null || !asking.isDone()) return null;
        CompletableFuture<Answer> done = asking;
        asking = null;
        // Failed on the server's side: asked again next time.
        return done.isCompletedExceptionally() ? null : done.getNow(null);
    }

    /** A question left unanswered belongs to the world left. */
    void reset() {
        asking = null;
    }

    void ask(IntegratedServer server, ResourceKey<Level> dimension, List<Asked> mobs) {
        // The world belongs to the server's thread: everything it holds is read there.
        asking = server.submit(() -> {
            Map<UUID, Where> found = new HashMap<>();
            List<UUID> gone = new ArrayList<>();
            ServerLevel level = server.getLevel(dimension);
            if (level == null) return new Answer(found, gone);
            for (Asked mob : mobs) {
                Entity entity = level.getEntity(mob.id());
                if (entity == null || entity.isRemoved() || (entity instanceof LivingEntity living && living.isDeadOrDying())) {
                    // Moved a moment ago, its chunk still ticks and it is nowhere: it died or left.
                    // Merely unloaded, as when the player went far at once, its chunk no longer ticks.
                    if (mob.simulated() && level.isPositionEntityTicking(BlockPos.containing(mob.x(), mob.y(), mob.z()))) {
                        gone.add(mob.id());
                    }
                    continue;
                }
                float body = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
                float head = entity instanceof LivingEntity living ? living.yHeadRot : body;
                Boolean sitting = entity instanceof TamableAnimal pet ? pet.isInSittingPose() : null;
                found.put(mob.id(), new Where(entity.getX(), entity.getY(), entity.getZ(), body, head,
                        level.isPositionEntityTicking(entity.blockPosition()), sitting));
            }
            return new Answer(found, gone);
        });
    }
}
