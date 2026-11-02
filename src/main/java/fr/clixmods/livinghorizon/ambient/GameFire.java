package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A campfire of the game's own where no block is, for the easter eggs that light one (9, 19):
 * the campfire block, drawn by the game as a block display is, among the far figures, and its
 * smoke, the game's particles made as a campfire makes them. Lit, or out and cold.
 */
final class GameFire {
    /** Lit, it glows whatever light is round it; out, it takes the light where it stands. */
    private static final String LIT = "{brightness:{block:15,sky:15}}";
    private static final String OUT = "{}";

    private final ClientLevel level;
    private final Display.BlockDisplay block;
    private final BlockPos pos;
    private boolean lit = true;

    private GameFire(ClientLevel level, Display.BlockDisplay block, BlockPos pos) {
        this.level = level;
        this.block = block;
        this.pos = pos;
    }

    /** A lit campfire on the block whose top middle is {@code (x, y, z)}; null if the game would not make one. */
    static @Nullable GameFire at(ClientLevel level, double x, double y, double z) {
        Display.BlockDisplay block = Puppets.create(EntityType.BLOCK_DISPLAY, level);
        if (block == null) return null;
        BlockPos pos = BlockPos.containing(x, y + 0.1, z);
        // A block display draws its block from its corner.
        block.setPos(pos.getX(), pos.getY(), pos.getZ());
        GameFire fire = new GameFire(level, block, pos);
        return fire.show(true) ? fire : null;
    }

    /** Lit, or out: then the game's puff of smoke of a campfire put out, once. */
    void lit(boolean on) {
        if (on == lit) return;
        lit = on;
        show(on);
        if (!on) {
            for (int i = 0; i < 20; i++) CampfireBlock.makeParticles(level, pos, false, true);
        }
    }

    boolean lit() {
        return lit;
    }

    /** Each tick: its smoke, while lit, at the pace a campfire of the game sends it. */
    void tick(RandomSource random) {
        if (!lit || random.nextFloat() >= 0.11f) return;
        for (int i = random.nextInt(2) + 2; i > 0; i--) CampfireBlock.makeParticles(level, pos, false, false);
    }

    /** The entity the game draws. */
    List<Entity> figures() {
        return List.of(block);
    }

    /**
     * The block display lit or out: its light from saved data, its campfire set outright; its
     * tick takes them in, as it does what a server sends.
     */
    private boolean show(boolean on) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully(on ? LIT : OUT);
            EntityNbt.load(block, level, tag);
            block.setBlockState(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, on));
            block.setPos(pos.getX(), pos.getY(), pos.getZ());
            block.tick();
            return true;
        } catch (Exception e) {
            LivingHorizonClient.LOGGER.debug("No campfire for an easter egg", e);
            return false;
        }
    }
}
