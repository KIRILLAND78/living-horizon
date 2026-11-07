package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ecstatic's world ({@link EcstaticLink}): the height and biome it samples from the world's
 * seed on a grid, one file per level of detail (a sample every 2 blocks at the first, every 32
 * at the fifth, and every 16 for the placeholder it shows first), in regions of 512 blocks.
 * Only the surface: no blocks, no caves, and no structure or other mod's terrain the seed does
 * not say. Its classes are reached by reflection; any change on its side turns this source off
 * for the session instead of crashing.
 *
 * <p>Ecstatic closes its files as its server stops and frees their memory without forgetting
 * it: a read after that would read freed memory. Its reads and its closing hold the file's
 * lock, so a read here holds it too, and reads nothing from a file whose channel is closed.
 */
final class EcstaticSource implements LodSource {
    /** Ecstatic draws its water at this height wherever its ground is lower. */
    static final int WATER_TOP = 63;
    /** Its levels of detail, the finest first, then the placeholder (level 0). */
    private static final int FINEST = 1, COARSEST = 5;

    private boolean ready;
    private volatile boolean broken;
    private volatile @Nullable String failure;
    /** After a failed read, nothing is read until then. */
    private volatile long pausedUntil;

    private MethodHandle coordinator, fileForLevel, bootstrapFile, readColumn, region, spacing, channel, height, biome;
    private int regionBlocks;

    private final AtomicInteger reads = new AtomicInteger(), found = new AtomicInteger();

    /** The coordinator a read uses, and the biomes its samples name by number (the server's). */
    private record Opened(Object coordinator, Registry<Biome> biomes) {
    }

    @Override
    public String name() {
        return "Ecstatic";
    }

    @Override
    public boolean loaded() {
        return Platform.isModLoaded("ecstatic");
    }

    @Override
    public @Nullable String failure() {
        return failure;
    }

    @Override
    public String details() {
        return reads.get() + " columns read, " + found.get() + " sampled";
    }

    @Override
    public synchronized boolean available() {
        if (broken || System.nanoTime() < pausedUntil) return false;
        if (!ready) {
            ready = true;
            if (!loaded()) {
                broken = true;
                return false;
            }
            try {
                if (!EcstaticLink.linked()) throw new IllegalStateException("its classes are not the ones this mod knows");
                link();
            } catch (Throwable e) {
                broken = true;
                failure = "Ecstatic found, but not the version this mod knows: " + e;
                LivingHorizonClient.LOGGER.warn("Ecstatic found, but not the version this mod knows: its world cannot be read", e);
                return false;
            }
        }
        return true;
    }

    private void link() throws Throwable {
        String p = EcstaticLink.PACKAGE;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> mod = Class.forName(p + "Ecstatic");
        Class<?> coordinatorType = Class.forName(p + "lod.RegionLodCoordinator");
        Class<?> regionType = Class.forName(p + "lod.RegionCoord");
        Class<?> file = Class.forName(p + "storage.LodRegionFile");
        Class<?> column = Class.forName(p + "storage.HeightmapColumn");
        MethodType toObject = MethodType.methodType(Object.class, Object.class);
        coordinator = lookup.findStatic(mod, "activeCoordinator", MethodType.methodType(coordinatorType))
                .asType(MethodType.methodType(Object.class));
        fileForLevel = lookup.findVirtual(coordinatorType, "fileForLevel", MethodType.methodType(file, int.class))
                .asType(MethodType.methodType(Object.class, Object.class, int.class));
        bootstrapFile = lookup.findVirtual(coordinatorType, "bootstrapFile", MethodType.methodType(file)).asType(toObject);
        readColumn = lookup.findVirtual(file, "readColumn", MethodType.methodType(column, regionType, int.class, int.class))
                .asType(MethodType.methodType(Object.class, Object.class, Object.class, int.class, int.class));
        region = lookup.findConstructor(regionType, MethodType.methodType(void.class, int.class, int.class))
                .asType(MethodType.methodType(Object.class, int.class, int.class));
        regionBlocks = (int) lookup.findStaticGetter(regionType, "SIZE_BLOCKS", int.class).invoke();
        spacing = lookup.findGetter(file, "sampleSpacingBlocks", int.class).asType(MethodType.methodType(int.class, Object.class));
        // Private: without it, a read could not tell a file Ecstatic has closed, so no read at all.
        channel = MethodHandles.privateLookupIn(file, MethodHandles.lookup()).findGetter(file, "channel", FileChannel.class)
                .asType(MethodType.methodType(FileChannel.class, Object.class));
        height = lookup.findVirtual(column, "height", MethodType.methodType(int.class)).asType(MethodType.methodType(int.class, Object.class));
        biome = lookup.findVirtual(column, "biomeRawId", MethodType.methodType(int.class)).asType(MethodType.methodType(int.class, Object.class));
        if (regionBlocks <= 0) throw new IllegalStateException("regions of " + regionBlocks + " blocks");
    }

    @Override
    public void clear() {
        // Nothing kept between reads: each read asks Ecstatic for its coordinator again.
    }

    @Override
    public void fail(Throwable e) {
        pausedUntil = System.nanoTime() + 20_000_000_000L;
        failure = "Ecstatic did not answer as expected: " + e;
        LivingHorizonClient.LOGGER.warn("Reading Ecstatic's world paused for 20 seconds: it did not answer as expected", e);
    }

    /** Ecstatic's world while it draws one: single player, the overworld, its files open. */
    @Override
    public @Nullable Object open() throws Throwable {
        Minecraft minecraft = Minecraft.getInstance();
        if (!EcstaticLink.draws(minecraft)) return null;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) return null;
        Object active = (Object) coordinator.invokeExact();
        if (active == null) return null;
        //? if >=1.21.2 {
        Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        //?} else {
        /*Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        *///?}
        return new Opened(active, biomes);
    }

    @Override
    public LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable {
        Opened opened = (Opened) world;
        reads.incrementAndGet();
        Object column = sample(opened.coordinator, x, z);
        if (column == null) return null;
        found.incrementAndGet();
        int top = (int) height.invokeExact(column);
        Biome kind = opened.biomes.byId((int) biome.invokeExact(column));
        return surface(top, kind == null ? "" : String.valueOf(opened.biomes.getKey(kind)));
    }

    /**
     * What Ecstatic draws at a column whose ground ends at {@code top} (exclusive, as it stores
     * it): that ground, or its water over it up to {@link #WATER_TOP}. No block: it knows none.
     */
    static LodWorld.Surface surface(int top, String biome) {
        if (top < WATER_TOP) return new LodWorld.Surface(WATER_TOP - 1, biome, true, null);
        return new LodWorld.Surface(top - 1, biome, false, null);
    }

    /** The finest sample Ecstatic holds of a column, or null. */
    private @Nullable Object sample(Object active, int x, int z) throws Throwable {
        for (int level = FINEST; level <= COARSEST; level++) {
            Object column = sampleIn((Object) fileForLevel.invokeExact(active, level), x, z);
            if (column != null) return column;
        }
        return sampleIn((Object) bootstrapFile.invokeExact(active), x, z);
    }

    /** The sample of one file whose cell holds the column, or null; never from a file Ecstatic has closed. */
    private @Nullable Object sampleIn(@Nullable Object file, int x, int z) throws Throwable {
        if (file == null) return null;
        int step = (int) spacing.invokeExact(file);
        if (step <= 0) return null;
        int[] cell = cell(x, z, regionBlocks, step);
        Object at = (Object) region.invokeExact(cell[0], cell[1]);
        synchronized (file) {
            if (!((FileChannel) channel.invokeExact(file)).isOpen()) return null;
            return (Object) readColumn.invokeExact(file, at, cell[2], cell[3]);
        }
    }

    /**
     * Where a column sits in Ecstatic's grid: its region, and the sample of that region whose
     * cell holds it, {@code {regionX, regionZ, sampleX, sampleZ}}.
     */
    static int[] cell(int x, int z, int regionBlocks, int spacing) {
        return new int[]{Math.floorDiv(x, regionBlocks), Math.floorDiv(z, regionBlocks),
                Math.floorMod(x, regionBlocks) / spacing, Math.floorMod(z, regionBlocks) / spacing};
    }

    /**
     * Walks the ray in longer steps farther away, where Ecstatic's samples are farther apart, and
     * stops where it goes under the ground of a column. A column without a sample is walked through.
     */
    @Override
    public double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                           double from, double to) throws Throwable {
        Object active = ((Opened) world).coordinator;
        long last = Long.MIN_VALUE;
        int ground = Integer.MIN_VALUE;
        for (double t = from; t < to; t += Math.clamp(t / 48.0, 1.0, 8.0)) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            if (key != last) {
                last = key;
                Object column = sample(active, x, z);
                ground = column == null ? Integer.MIN_VALUE : (int) height.invokeExact(column);
            }
            if (y < ground) return t;
        }
        return Double.NaN;
    }

    /** As far as its farthest ring of terrain reaches; 0 when it draws nothing here. */
    @Override
    public double drawnDistance() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!EcstaticLink.draws(minecraft)) return 0;
        return EcstaticLink.drawnDistance(minecraft.options.getEffectiveRenderDistance());
    }

    /** Where its fog, a smoothstep from its start to its end, reaches a fifth: a third of the way. */
    @Override
    public double clearDistance() {
        double drawn = drawnDistance();
        float[] ramp = EcstaticLink.fogRamp(Minecraft.getInstance().options.getEffectiveRenderDistance());
        if (drawn <= 0 || ramp == null) return drawn;
        return Math.min(drawn, ramp[0] + (ramp[1] - ramp[0]) * 0.31);
    }
}
