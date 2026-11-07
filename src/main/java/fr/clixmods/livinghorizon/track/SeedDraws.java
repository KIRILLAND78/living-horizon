package fr.clixmods.livinghorizon.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
//? if >=26.3 {
/*import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
*///?}
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game's draws for a chunk's first animals ({@code NaturalSpawner.spawnMobsForChunkGeneration}),
 * made for a chunk the game has not generated yet: the server's generator in a world played here
 * or by the server itself ({@link SeedMobs}), or the vanilla generator built from a seed a server
 * shares ({@link #vanilla}).
 *
 * <p>The game places a chunk's first animals with a random of its own, seeded by the world's seed
 * and the chunk's position alone. The same draws are made here, on the biome the generator gives
 * and on the bare terrain of its noise (no grass, trees or snow yet): a column topped with water is
 * no place for an animal, any other is, and a turtle only low enough. Where the game turns a spot
 * down for what the bare terrain lacks, its draws and these part ways for the rest of the chunk.
 * The look (colour, coat, age) the game draws with a random nothing can foresee: it is drawn here
 * from the mob's own id, with the game's odds.
 *
 * <p>Each foreseen mob has an id of its own, the same every time (a name-based one, version 3,
 * that no mob the game makes has: {@link #foreseen}).
 *
 * <p>Thread-safe: a {@link Source} is read from any thread, and each thread makes its own
 * {@link BiomeManager} ({@link Source#biomes()}). Nothing here reaches a class of the client's.
 */
public final class SeedDraws {
    private SeedDraws() {
    }

    /** What making a dimension's first animals needs: its generator, seed, height and sea. */
    public record Source(String dimension, ChunkGenerator generator, RandomState random, long seed,
                         LevelHeightAccessor height, DimensionType type, int seaLevel, @Nullable Tags tags) {
        /** The server's own, or null where the game puts no first animals (see {@link SeedMobs}). */
        public static @Nullable Source of(ServerLevel level) {
            if (level.dimensionType().hasCeiling() || !spawnsMobs(level)) return null;
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (!(generator instanceof NoiseBasedChunkGenerator noise) || noise.generatorSettings().value().disableMobGeneration()) {
                return null;
            }
            return new Source(SharedPositions.dimensionId(level.dimension()), generator, level.getChunkSource().randomState(),
                    level.getSeed(), level, level.dimensionType(), level.getSeaLevel(), null);
        }

        /**
         * The biomes of the generator, through the same zoom as the game's, kept to the height of the
         * world as a chunk keeps them. One per thread: 26.3's resolver is not shared.
         */
        public BiomeManager biomes() {
            int lowest = QuartPos.fromBlock(height.getMinY());
            int highest = lowest + QuartPos.fromBlock(height.getHeight()) - 1;
            //? if >=26.3 {
            /*var resolver = generator.getBiomeSource().createUncachedResolver(random);
            return new BiomeManager((qx, qy, qz) -> resolver.getNoiseBiome(qx, Mth.clamp(qy, lowest, highest), qz),
                    BiomeManager.obfuscateSeed(seed));
            *///?} else {
            var biomes = generator.getBiomeSource();
            var sampler = random.sampler();
            return new BiomeManager((qx, qy, qz) -> biomes.getNoiseBiome(qx, Mth.clamp(qy, lowest, highest), qz, sampler),
                    BiomeManager.obfuscateSeed(seed));
            //?}
        }

        /** Whether a biome is in a tag: the generator's own say, or those of {@link #tags} when it has none bound. */
        boolean is(Holder<Biome> biome, TagKey<Biome> tag) {
            return tags == null ? biome.is(tag) : tags.has(biome, tag);
        }
    }

    /**
     * Which biomes a tag holds, read from other registries than the generator's: the game's own,
     * built again for a client ({@link #vanilla}), come without tags, which data packs add. A
     * client reads them from its level, where the server sent them; none known, no biome is in one.
     */
    public static final class Tags {
        private final HolderLookup.@Nullable Provider registries;
        private final Map<TagKey<Biome>, Set<ResourceKey<Biome>>> held = new ConcurrentHashMap<>();

        Tags(HolderLookup.@Nullable Provider registries) {
            this.registries = registries;
        }

        boolean has(Holder<Biome> biome, TagKey<Biome> tag) {
            Set<ResourceKey<Biome>> keys = held.computeIfAbsent(tag, this::read);
            return biome.unwrapKey().map(keys::contains).orElse(false);
        }

        private Set<ResourceKey<Biome>> read(TagKey<Biome> tag) {
            if (registries == null) return Set.of();
            Set<ResourceKey<Biome>> keys = new HashSet<>();
            registries.lookupOrThrow(Registries.BIOME).get(tag)
                    .ifPresent(set -> set.forEach(holder -> holder.unwrapKey().ifPresent(keys::add)));
            return keys;
        }
    }

    /**
     * What a server says of a dimension whose generator is the game's own, for a client to build it
     * again from the seed: the noise settings ({@code minecraft:overworld}, {@code large_biomes},
     * {@code amplified}) and the biome preset. Null for any other generator: a mod's or a data
     * pack's, which the client cannot build.
     */
    public static String @Nullable [] vanillaKeys(ServerLevel level) {
        if (Source.of(level) == null) return null;
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise)) return null;
        if (!(noise.getBiomeSource() instanceof MultiNoiseBiomeSource biomes)
                || !biomes.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) return null;
        // A data pack that adds biomes to the preset keeps its name: its biomes tell it apart.
        for (Holder<Biome> biome : biomes.possibleBiomes()) {
            if (biome.unwrapKey().map(key -> !key.identifier().getNamespace().equals("minecraft")).orElse(true)) return null;
        }
        for (ResourceKey<NoiseGeneratorSettings> key : List.of(NoiseGeneratorSettings.OVERWORLD,
                NoiseGeneratorSettings.LARGE_BIOMES, NoiseGeneratorSettings.AMPLIFIED)) {
            if (noise.stable(key)) {
                return new String[]{key.identifier().toString(), MultiNoiseBiomeSourceParameterLists.OVERWORLD.identifier().toString()};
            }
        }
        return null;
    }

    /**
     * The game's own generator for a dimension, built from the seed a server shares and the keys
     * of {@link #vanillaKeys}; null when those keys are not the game's. Slow the first time: the
     * game's registries are built once ({@code VanillaRegistries}). Biome tags are read from
     * {@code tags}, the client level's registries.
     */
    public static @Nullable Source vanilla(String dimension, long seed, String settingsKey, String biomesKey,
                                           LevelHeightAccessor height, DimensionType type,
                                           HolderLookup.@Nullable Provider tags) {
        HolderLookup.Provider lookup = VanillaLookup.get();
        var settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).get(key(Registries.NOISE_SETTINGS, settingsKey));
        var preset = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .get(key(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, biomesKey));
        if (settings.isEmpty() || preset.isEmpty()) return null;
        Holder<MultiNoiseBiomeSourceParameterList> list = preset.get();
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(list), settings.get());
        //? if >=26.3 {
        /*RandomState random = RandomState.create(lookup.lookupOrThrow(Registries.NOISE), seed, settings.get().value());
        *///?} else
        RandomState random = RandomState.create(settings.get().value(), lookup.lookupOrThrow(Registries.NOISE), seed);
        return new Source(dimension, generator, random, seed, height, type, settings.get().value().seaLevel(),
                new Tags(tags));
    }

    /** The game's own registries, as built for {@link #vanilla}. */
    static HolderLookup.Provider vanillaLookup() {
        return VanillaLookup.get();
    }

    /** The game's registries, built once, on the first thread that asks. */
    private static final class VanillaLookup {
        private static volatile HolderLookup.@Nullable Provider built;

        static synchronized HolderLookup.Provider get() {
            if (built == null) {
                //? if >=26.3 {
                /*built = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
                *///?} else
                built = net.minecraft.data.registries.VanillaRegistries.createLookup();
            }
            return built;
        }
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, String id) {
        Identifier location = Identifier.tryParse(id);
        return ResourceKey.create(registry, location != null ? location : Identifier.fromNamespaceAndPath("minecraft", "none"));
    }

    /** Whether a mob is one foreseen here, not one the game made: by its id alone. */
    public static boolean foreseen(UUID id) {
        return id.version() == 3;
    }

    /**
     * The first animals of one chunk: every one is drawn, each taking its share of the random as in
     * the game, and those a server shares ({@link MobKinds}) kept. Throws what the generator throws.
     */
    public static List<PositionsMessage.Mob> spawns(Source source, BiomeManager biomes, int cx, int cz) {
        int minX = cx << 4, minZ = cz << 4;
        // The biome the game reads for them: at the chunk's corner, at the top of the world.
        Holder<Biome> biome = biomes.getBiome(new BlockPos(minX, topY(source.height()), minZ));
        // From 26.3 the animals and their odds are environment attributes: the dimension's, then the biome's.
        //? if >=26.3 {
        /*MobSpawnSettings settings = attribute(source, biome, EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        Holder<Biome> noiseBiome = biomes.getNoiseBiomeAtPosition(minX + 0.5, topY(source.height()) + 0.5, minZ + 0.5);
        float probability = attribute(source, noiseBiome, EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY);
        var creatures = settings.getMobsToSpawn(MobCategory.CREATURE);
        *///?} else {
        MobSpawnSettings settings = biome.value().getMobSettings();
        float probability = settings.getCreatureProbability();
        var creatures = settings.getMobs(MobCategory.CREATURE);
        //?}
        if (creatures.isEmpty()) return List.of();

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setDecorationSeed(source.seed(), minX, minZ);
        Map<Long, Integer> tops = new HashMap<>();
        List<PositionsMessage.Mob> mobs = new ArrayList<>();
        int index = 0;
        while (random.nextFloat() < probability) {
            var picked = creatures.getRandom(random);
            if (picked.isEmpty()) continue;
            MobSpawnSettings.SpawnerData data = picked.get();
            EntityType<?> type = type(data);
            int count = count(data, random);
            int x = minX + random.nextInt(16), z = minZ + random.nextInt(16);
            int startX = x, startZ = z;
            String id = EntityType.getKey(type).toString();
            int kind = MobKinds.kind(id);
            Random group = new Random(mix(source.seed(), cx, cz, index * 31L + 7));
            Look look = new Look(id, source, biome, group);
            for (int member = 0; member < count; member++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < 4; attempt++) {
                    int y = type.canSummon() ? top(source, tops, x, z) : Integer.MIN_VALUE;
                    if (y != Integer.MIN_VALUE) {
                        // Turned down by its own rules: the game tries the same spot again, with no draw.
                        if (!allowed(id, y, source)) continue;
                        float width = type.getWidth();
                        double px = Mth.clamp(x, minX + width, minX + 16.0 - width);
                        double pz = Mth.clamp(z, minZ + width, minZ + 16.0 - width);
                        random.nextFloat(); // Which way it faces.
                        placed = true;
                        if (kind > 0) {
                            UUID uuid = id(source.seed(), source.dimension(), cx, cz, index);
                            Random own = new Random(uuid.getLeastSignificantBits());
                            mobs.add(new PositionsMessage.Mob(uuid, px, y, pz, kind, source.dimension(),
                                    look.baby(own), look.variant(own), null));
                        }
                        index++;
                    }
                    x += random.nextInt(5) - random.nextInt(5);
                    z += random.nextInt(5) - random.nextInt(5);
                    while (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
                        x = startX + random.nextInt(5) - random.nextInt(5);
                        z = startZ + random.nextInt(5) - random.nextInt(5);
                    }
                }
            }
            index++;
        }
        return mobs.isEmpty() ? List.of() : List.copyOf(mobs);
    }

    //? if >=26.3 {
    /*/^* An attribute where nothing but the dimension and the biome sets it, as in a chunk being generated. ^/
    private static <T> T attribute(Source source, Holder<Biome> biome, EnvironmentAttribute<T> attribute) {
        T value = attribute.defaultValue();
        value = source.type().attributes().applyModifier(attribute, value);
        return biome.value().getAttributes().applyModifier(attribute, value);
    }
    *///?}

    /**
     * The height an animal stands at on a column of bare terrain, or {@code MIN_VALUE} where it
     * cannot: water or lava on top, or nothing at all.
     */
    private static int top(Source source, Map<Long, Integer> tops, int x, int z) {
        return tops.computeIfAbsent(chunk(x, z), key -> {
            LevelHeightAccessor height = source.height();
            NoiseColumn column = source.generator().getBaseColumn(x, z, height, source.random());
            for (int y = height.getMinY() + height.getHeight() - 1; y >= height.getMinY(); y--) {
                BlockState state = column.getBlock(y);
                if (state.isAir()) continue;
                return state.getFluidState().isEmpty() ? y + 1 : Integer.MIN_VALUE;
            }
            return Integer.MIN_VALUE;
        });
    }

    /**
     * The game's own rules for a kind, where the bare terrain can tell them: a turtle only on a
     * beach low enough. The others want grass, sand or snow, which the bare terrain does not have.
     */
    private static boolean allowed(String type, int y, Source source) {
        return !type.equals("minecraft:turtle") || y < source.seaLevel() + 4;
    }

    /**
     * How a group looks: what its members share (a herd of horses one coat, a family of rabbits
     * one fur), then each one's own, with the game's odds.
     */
    private static final class Look {
        final String type;
        final Source source;
        final Holder<Biome> biome;
        final int shared;
        final float babies;
        int members;

        Look(String type, Source source, Holder<Biome> biome, Random group) {
            this.type = type;
            this.source = source;
            this.biome = biome;
            this.shared = switch (type) {
                case "minecraft:horse" -> group.nextInt(7);
                case "minecraft:llama" -> group.nextInt(4);
                case "minecraft:rabbit" -> rabbit(source, biome, group.nextInt(100));
                default -> 0;
            };
            // The first of a group is never a baby; the game's odds for each one after it.
            this.babies = switch (type) {
                case "minecraft:rabbit", "minecraft:polar_bear", "minecraft:ocelot" -> 1.0F;
                case "minecraft:donkey", "minecraft:mule", "minecraft:panda" -> 0.2F;
                case "minecraft:fox", "minecraft:parrot" -> 0.0F;
                default -> 0.05F;
            };
        }

        boolean baby(Random own) {
            return members++ > 0 && own.nextFloat() <= babies;
        }

        int variant(Random own) {
            return switch (type) {
                case "minecraft:horse" -> shared | own.nextInt(5) << 8;
                case "minecraft:llama", "minecraft:rabbit" -> shared;
                case "minecraft:parrot" -> own.nextInt(5);
                case "minecraft:sheep" -> sheep(source, biome, own);
                default -> 0;
            };
        }

        /** {@code Rabbit.getRandomRabbitVariant}: brown 0, white 1, black 2, splotched 3, gold 4, salt 5. */
        private static int rabbit(Source source, Holder<Biome> biome, int roll) {
            if (source.is(biome, BiomeTags.SPAWNS_WHITE_RABBITS)) return roll < 80 ? 1 : 3;
            if (source.is(biome, BiomeTags.SPAWNS_GOLD_RABBITS)) return 4;
            return roll < 50 ? 0 : roll < 90 ? 5 : 2;
        }

        /** {@code SheepColorSpawnRules}, as wool colour ids: most white, brown where warm, black where cold. */
        private static int sheep(Source source, Holder<Biome> biome, Random own) {
            int common = 0;
            int[] rare = {15, 7, 8, 12};
            //? if >=1.21.5 {
            if (source.is(biome, BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS)) {
                common = 12;
                rare = new int[]{7, 8, 0, 15};
            } else if (source.is(biome, BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS)) {
                common = 15;
                rare = new int[]{8, 7, 0, 12};
            }
            //?}
            int roll = own.nextInt(100);
            if (roll < 5) return rare[0];
            if (roll < 10) return rare[1];
            if (roll < 15) return rare[2];
            if (roll < 18) return rare[3];
            return own.nextInt(500) == 0 ? 6 : common;
        }
    }

    /**
     * The id of a foreseen mob: always the same for the same mob of the same world, version 3. The
     * chunk's own, its last 16 bits its place among the chunk's draws ({@link #index}), so that a
     * file can keep that place alone ({@code Foresight}).
     */
    static UUID id(long seed, String dimension, int cx, int cz, int index) {
        ByteBuffer bytes = ByteBuffer.allocate(16 + dimension.length() * 3);
        bytes.putLong(seed).putInt(cx).putInt(cz).put(dimension.getBytes(StandardCharsets.UTF_8));
        UUID chunk = UUID.nameUUIDFromBytes(java.util.Arrays.copyOf(bytes.array(), bytes.position()));
        return new UUID(chunk.getMostSignificantBits(), chunk.getLeastSignificantBits() & ~0xFFFFL | index & 0xFFFF);
    }

    /** A foreseen mob's place among its chunk's draws, from its id. */
    static int index(UUID id) {
        return (int) (id.getLeastSignificantBits() & 0xFFFF);
    }

    private static long mix(long seed, int cx, int cz, long salt) {
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    // --- What changed between versions ---------------------------------------------------

    /** The game rule that keeps every mob from spawning, a chunk's first animals included. */
    private static boolean spawnsMobs(ServerLevel level) {
        // Before 1.21.11 the game puts them there whatever the rule says.
        //? if >=1.21.11 {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS);
        //?} else
        /*return true;*/
    }

    /** Where the game reads the biome of a chunk's first animals: the top block of the world. */
    private static int topY(LevelHeightAccessor height) {
        return height.getMinY() + height.getHeight() - 1;
    }

    // SpawnerData became a record in 1.21.5.
    private static EntityType<?> type(MobSpawnSettings.SpawnerData data) {
        //? if >=1.21.5 {
        return data.type();
        //?} else
        /*return data.type;*/
    }

    /** How many in a group: one draw, between the two counts; a value provider from 26.3, which draws the same. */
    private static int count(MobSpawnSettings.SpawnerData data, WorldgenRandom random) {
        //? if >=26.3 {
        /*return data.count().sample(random);
        *///?} elif >=1.21.5 {
        return data.minCount() + random.nextInt(1 + data.maxCount() - data.minCount());
        //?} else
        /*return data.minCount + random.nextInt(1 + data.maxCount - data.minCount);*/
    }

    /** A chunk's two coordinates in one number, as the game packs them. */
    public static long chunk(int x, int z) {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    public static int chunkX(long chunk) {
        return (int) chunk;
    }

    public static int chunkZ(long chunk) {
        return (int) (chunk >>> 32);
    }
}
