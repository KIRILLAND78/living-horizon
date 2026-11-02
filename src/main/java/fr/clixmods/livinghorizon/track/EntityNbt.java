package fr.clixmods.livinghorizon.track;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
//? if >=1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

import java.util.stream.Stream;

/**
 * An entity as the game saves it, and back. From 1.21.6 the game writes and reads entities
 * through value inputs and outputs that know the registries; before, straight into tags.
 */
public final class EntityNbt {
    private EntityNbt() {
    }

    /** The entity as the game saves it, without its type. */
    public static CompoundTag save(Entity entity, Level level) {
        //? if >=1.21.6 {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
        //?} else {
        /*return entity.saveWithoutId(new CompoundTag());
        *///?}
    }

    /**
     * Gives an entity what a saved tag says of it, and the size that follows. Some mods' mobs
     * work their size out from their saved data in their tick (Ice and Fire's dragons, by
     * age), which a copy never runs: it kept the size of a newborn, and its pictures were cut
     * out of a model ten times larger.
     */
    public static void load(Entity entity, Level level, CompoundTag tag) {
        //? if >=1.21.6 {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        //?} else {
        /*entity.load(tag);
        *///?}
        entity.refreshDimensions();
    }

    /** The entities of a saved chunk, their passengers on them, never added to the world. */
    public static Stream<Entity> chunkEntities(CompoundTag chunk, Level level) {
        //? if >=1.21.6 {
        return EntityType.loadEntitiesRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), chunk)
                .childrenListOrEmpty("Entities"), level, EntitySpawnReason.LOAD);
        //?} elif >=1.21.5 {
        /*return EntityType.loadEntitiesRecursive(chunk.getListOrEmpty("Entities"), level, EntitySpawnReason.LOAD);
        *///?} elif >=1.21.2 {
        /*return EntityType.loadEntitiesRecursive(chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND), level,
                EntitySpawnReason.LOAD);
        *///?} else {
        /*return EntityType.loadEntitiesRecursive(chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND), level);
        *///?}
    }
}
