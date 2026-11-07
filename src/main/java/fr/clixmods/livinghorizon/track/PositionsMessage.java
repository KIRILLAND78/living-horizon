package fr.clixmods.livinghorizon.track;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a server with the mod tells the clients with the mod: where every player is, and the
 * remembered mobs around the one it is sent to. Written with plain Java streams, no type of
 * the game's, so that it reads the same on every loader and version, and in a unit test.
 *
 * <p>Three kinds of message: {@link #PLAYERS} online, a few times a second; {@link #OFFLINE},
 * the players who logged off, and {@link #MOBS}, every few seconds, and all three at once to a
 * player who just joined. How a player looks ({@link Look}) and what they ride ({@link Mount})
 * come every few seconds only. The server keeps the same lists in the world
 * ({@link PositionPublisher}), written in this format too, under a version of its own
 * ({@link #FILE_VERSION}).
 *
 * <p>The layout, from version 3: the version it was written in and the oldest version that
 * reads it ({@link Format}), the kind of message, the table of the dimensions it names (their
 * ids, {@code minecraft:overworld}), then its records, each one after its length. A later
 * version may add fields at the end of a record, or after the records, and still be read here:
 * what this one does not know is skipped. Version 2, which numbered the dimensions 0 to 3, is
 * still read, from a server not updated yet and from the file. Version 4 adds a player gliding
 * with an elytra: a flag version 3 leaves unread, and their pitch at the end of the record.
 */
public final class PositionsMessage {
    /** The layout written here. */
    public static final int VERSION = 4;
    /** The oldest version that reads what is written here: a reader of it or newer skips what it does not know. */
    static final int READABLE_FROM = 3;
    /** The layout of the server's own file ({@link #saved}), apart from the messages'. */
    static final int FILE_VERSION = 3;
    /** The oldest version that reads the file written here. */
    static final int FILE_READABLE_FROM = 3;
    /** The layout before this one: the three dimensions of the game by number, any other as 3. */
    static final int LEGACY = 2;
    /** What version 2 meant by dimensions 0, 1 and 2; 3 was any other one, which cannot be told apart. */
    public static final List<String> LEGACY_DIMENSIONS =
            List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    /** Past this, a message leaves out what does not fit: 1 MiB is the most the game lets a mod's message carry. */
    public static final int MAX_BYTES = 1_000_000;
    public static final byte PLAYERS = 0;
    public static final byte MOBS = 1;
    public static final byte OFFLINE = 2;
    /**
     * The world's seed, for a client to foresee the first animals of the land nobody explored
     * itself ({@link SeedDraws}); sent only when the server's owner allows it. Versions before it
     * leave a kind they do not know unread.
     */
    public static final byte SEED = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    /** Bytes before the dimensions: the two versions and the kind. */
    private static final int HEAD = 9;
    /** The most bytes a count takes. */
    private static final int COUNT = 5;
    private static volatile boolean warnedCut;

    /**
     * One player: online now, or where they were when they logged off. {@code dimension} is its
     * id ({@link SharedPositions#dimensionId}); {@code vehicle} the published mob they ride, if any;
     * {@code mount} what they ride, as the server saves it, and {@code look} how they look: both
     * sent every few seconds only, so null in most messages. {@code lastSeen} is when they logged
     * off, in milliseconds since 1970 (0 when not known), and {@code test} marks a test player the
     * host placed with the debug command. {@code gliding}: flying with an elytra, {@code pitch}
     * the way they look up or down then (0 otherwise).
     */
    public record Player(String name, double x, double y, double z, float yaw, String dimension, boolean online,
                         boolean riding, @Nullable UUID vehicle, @Nullable Mount mount, @Nullable Look look,
                         long lastSeen, boolean test, boolean gliding, float pitch) {
        public Player(String name, double x, double y, double z, float yaw, String dimension, boolean online,
                      boolean riding, @Nullable UUID vehicle) {
            this(name, x, y, z, yaw, dimension, online, riding, vehicle, null, null, 0, false);
        }

        public Player(String name, double x, double y, double z, float yaw, String dimension, boolean online,
                      boolean riding, @Nullable UUID vehicle, @Nullable Mount mount) {
            this(name, x, y, z, yaw, dimension, online, riding, vehicle, mount, null, 0, false);
        }

        public Player(String name, double x, double y, double z, float yaw, String dimension, boolean online,
                      boolean riding, @Nullable UUID vehicle, @Nullable Mount mount, @Nullable Look look,
                      long lastSeen, boolean test) {
            this(name, x, y, z, yaw, dimension, online, riding, vehicle, mount, look, lastSeen, test, false, 0f);
        }

        /** The same player, without what is sent every few seconds only. */
        public Player plain() {
            return mount == null && look == null ? this
                    : new Player(name, x, y, z, yaw, dimension, online, riding, vehicle, null, null, lastSeen, test,
                            gliding, pitch);
        }

        /** The same player, with how they look and what they ride. */
        public Player full(@Nullable Mount mount, @Nullable Look look) {
            return new Player(name, x, y, z, yaw, dimension, online, riding, vehicle, mount, look, lastSeen, test,
                    gliding, pitch);
        }
    }

    /**
     * How a player looks: their skin, as the {@code textures} property of their profile (value
     * and signature; null when the server has none), and what they wear and hold ({@link Gear}).
     */
    public record Look(@Nullable String textures, @Nullable String signature, List<String> gear) {
    }

    /**
     * What a player rode when they logged off, the server's own copy: its type
     * ({@code minecraft:horse}), the mount as the game saves it, where the rider sits on it (the
     * rider's feet from the mount's, in the mount's frame), the way it faced, and the way the
     * rider faced from it.
     */
    public record Mount(String type, String nbt, double seatX, double seatY, double seatZ, float yaw, float riderYaw) {
        /** Past this, a saved mount is left out: a message's text is at most 64 KiB. */
        public static final int MAX_NBT = 32_000;
    }

    /**
     * One remembered mob: its kind ({@link MobKinds}), its dimension's id, and what its look
     * needs; {@code extra} is what it wears (saddle, harness, armour, carpet, chest), as the game
     * saves it, or null.
     */
    public record Mob(UUID id, double x, double y, double z, int kind, String dimension, boolean baby, int variant,
                      @Nullable String extra) {
        public Mob(UUID id, double x, double y, double z, int kind, String dimension, boolean baby, int variant) {
            this(id, x, y, z, kind, dimension, baby, variant, null);
        }
    }

    /**
     * The mobs of one message, and the circle they were chosen in: a mob remembered inside it
     * and not in the list is gone, one outside it is only out of reach.
     */
    public record Mobs(List<Mob> mobs, String dimension, double x, double z, double radius) {
        public boolean covers(String dimension, double x, double z) {
            return dimension.equals(this.dimension) && Math.hypot(x - this.x, z - this.z) <= radius;
        }
    }

    /**
     * A message read: its kind, and the list it carries (the other one is null). The players who
     * logged off come in {@code part} of a list that may take several messages; null for the others.
     */
    public record Decoded(byte kind, @Nullable List<Player> players, @Nullable Mobs mobs, @Nullable Part part,
                          @Nullable Seed seed) {
        public Decoded(byte kind, @Nullable List<Player> players, @Nullable Mobs mobs) {
            this(kind, players, mobs, null, null);
        }

        public Decoded(byte kind, @Nullable List<Player> players, @Nullable Mobs mobs, @Nullable Part part) {
            this(kind, players, mobs, part, null);
        }
    }

    /**
     * The world's seed and the dimensions whose generator is the game's own, for a client to build
     * it again ({@link SeedDraws#vanilla}); none when the server shares no seed.
     */
    public record Seed(long seed, List<SeedGround> dimensions) {
        public static final Seed NONE = new Seed(0, List.of());
    }

    /** A dimension whose generator is the game's own: its noise settings and its biome preset, by id. */
    public record SeedGround(String dimension, String settings, String biomes) {
    }

    /** The seed message; {@link Seed#NONE} tells a client the server shares none (any more). */
    public static byte[] seed(Seed seed) {
        Encoder encoder = new Encoder(MAX_BYTES, 1 + COUNT + 8);
        Records list = encoder.list();
        for (SeedGround ground : seed.dimensions()) {
            encoder.add(list, out -> {
                writeVarint(out, encoder.dimension(ground.dimension()));
                out.writeUTF(ground.settings());
                out.writeUTF(ground.biomes());
            });
        }
        return write(VERSION, READABLE_FROM, out -> {
            out.writeByte(SEED);
            encoder.writeTable(out);
            writeRecord(out, head -> head.writeLong(seed.seed()));
            list.writeTo(out);
        });
    }

    /** Which message of a list sent in several this one is, from 0, and how many there are. */
    public record Part(int index, int count) {
        /** A list in one message. */
        public static final Part WHOLE = new Part(0, 1);

        public boolean last() {
            return index == count - 1;
        }
    }

    /**
     * The version a message or a file was written in, and the oldest version that reads it
     * ({@link #READABLE_FROM}; version 2 had no such field, and is read by itself only).
     */
    public record Format(int version, int readableFrom) {
        /** Whether a reader of version {@code reader} reads it. */
        public boolean readableBy(int reader) {
            return version == LEGACY || version > LEGACY && readableFrom <= reader;
        }
    }

    private PositionsMessage() {
    }

    /** What a message or file says of its version; null when it is too short to say. */
    public static @Nullable Format format(byte[] data) {
        if (data.length < 4) return null;
        int version = intAt(data, 0);
        if (version <= LEGACY) return new Format(version, version);
        return data.length < 8 ? null : new Format(version, intAt(data, 4));
    }

    // --- Messages ------------------------------------------------------------------------

    /** The players online now, as many as fit in one message, the first ones first. */
    public static byte[] players(List<Player> players) {
        return players(PLAYERS, players, MAX_BYTES);
    }

    /**
     * The players who logged off, where they were last: every one of them, in as many messages as
     * it takes, in order. A client takes the list once the last has come.
     */
    public static List<byte[]> offline(List<Player> players) {
        return offline(players, MAX_BYTES);
    }

    /**
     * The mobs around a player. When they do not all fit, the nearest are sent and the circle
     * shrinks to them, so that a mob left out is not taken for gone.
     */
    public static byte[] mobs(Mobs mobs) {
        return mobs(mobs, MAX_BYTES);
    }

    static byte[] players(byte kind, List<Player> players, int limit) {
        Encoder encoder = new Encoder(limit, 0);
        Records list = encoder.list();
        int cut = 0;
        for (Player player : players) {
            if (encoder.add(list, out -> writePlayer(out, encoder, player))) continue;
            cut++;
            // Too much for one message: by their position alone, or not at all.
            Player plain = player.plain();
            if (plain != player) encoder.add(list, out -> writePlayer(out, encoder, plain));
        }
        if (cut > 0) warnCut(cut, "players");
        return write(VERSION, READABLE_FROM, out -> {
            out.writeByte(kind);
            encoder.writeTable(out);
            list.writeTo(out);
        });
    }

    static List<byte[]> offline(List<Player> players, int limit) {
        // Each message says which part of the list it is, before the players: room kept for it.
        int head = 1 + 2 * COUNT;
        List<Encoder> encoders = new ArrayList<>();
        List<Records> lists = new ArrayList<>();
        Encoder encoder = new Encoder(limit, head);
        Records list = encoder.list();
        int cut = 0;
        for (Player player : players) {
            if (addPlayer(encoder, list, player)) continue;
            if (list.count > 0) {
                // This one is full: the next one starts with them.
                encoders.add(encoder);
                lists.add(list);
                encoder = new Encoder(limit, head);
                list = encoder.list();
                if (addPlayer(encoder, list, player)) continue;
            }
            // Too much alone for a message: by their position alone, or not at all.
            cut++;
            Player plain = player.plain();
            if (plain != player) addPlayer(encoder, list, plain);
        }
        encoders.add(encoder);
        lists.add(list);
        if (cut > 0) warnCut(cut, "players");
        List<byte[]> messages = new ArrayList<>(encoders.size());
        for (int i = 0; i < encoders.size(); i++) {
            Encoder each = encoders.get(i);
            Records part = lists.get(i);
            int index = i, count = encoders.size();
            messages.add(write(VERSION, READABLE_FROM, out -> {
                out.writeByte(OFFLINE);
                each.writeTable(out);
                writeRecord(out, which -> {
                    writeVarint(which, index);
                    writeVarint(which, count);
                });
                part.writeTo(out);
            }));
        }
        return messages;
    }

    private static boolean addPlayer(Encoder encoder, Records list, Player player) {
        return encoder.add(list, out -> writePlayer(out, encoder, player));
    }

    static byte[] mobs(Mobs mobs, int limit) {
        byte[] whole = mobs(mobs, mobs.mobs(), mobs.radius(), limit);
        if (whole != null) return whole;
        List<Mob> nearest = new ArrayList<>(mobs.mobs());
        nearest.sort(Comparator.comparingDouble(mob -> Math.hypot(mob.x() - mobs.x(), mob.z() - mobs.z())));
        byte[] cut = mobs(mobs, nearest, mobs.radius(), limit);
        if (cut == null) throw new IllegalStateException("not even an empty list of mobs fits");
        return cut;
    }

    /** The mobs, in this order, until one does not fit: then null, or the nearest with a smaller circle. */
    private static byte @Nullable [] mobs(Mobs mobs, List<Mob> order, double radius, int limit) {
        // The circle comes before the mobs, but is written last: room kept for it (its length, index, x, z, radius).
        Encoder encoder = new Encoder(limit, 1 + COUNT + 24);
        Records list = encoder.list();
        int index = encoder.dimension(mobs.dimension());
        double reach = radius;
        for (int i = 0; i < order.size(); i++) {
            Mob mob = order.get(i);
            if (encoder.add(list, out -> writeMob(out, encoder, mob))) continue;
            if (order == mobs.mobs()) return null;
            // Sorted by distance: the circle stops short of the first left out.
            double distance = Math.hypot(mob.x() - mobs.x(), mob.z() - mobs.z());
            reach = Math.min(radius, Math.nextDown(distance));
            warnCut(order.size() - i, "mobs");
            break;
        }
        double circle = reach;
        return write(VERSION, READABLE_FROM, out -> {
            out.writeByte(MOBS);
            encoder.writeTable(out);
            writeRecord(out, head -> {
                writeVarint(head, index);
                head.writeDouble(mobs.x());
                head.writeDouble(mobs.z());
                head.writeDouble(circle);
            });
            list.writeTo(out);
        });
    }

    /** Null for a message no version of this one reads, or one that does not read whole. */
    public static @Nullable Decoded read(byte[] data) {
        Format format = format(data);
        if (format == null || !format.readableBy(VERSION)) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (format.version() == LEGACY) {
                in.skipNBytes(4);
                return readLegacy(in);
            }
            in.skipNBytes(8);
            byte kind = in.readByte();
            List<String> dimensions = readTable(in);
            if (kind == PLAYERS) return new Decoded(kind, readPlayers(in, dimensions), null);
            if (kind == OFFLINE) {
                DataInputStream head = readRecord(in);
                Part part = new Part(readVarint(head), readVarint(head));
                if (part.index() < 0 || part.index() >= part.count()) return null;
                return new Decoded(kind, readPlayers(in, dimensions), null, part);
            }
            if (kind == MOBS) {
                DataInputStream head = readRecord(in);
                String dimension = dimensions.get(readVarint(head));
                double x = head.readDouble(), z = head.readDouble(), radius = head.readDouble();
                return new Decoded(kind, null, new Mobs(readMobs(in, dimensions), dimension, x, z, radius));
            }
            if (kind == SEED) {
                long seed = readRecord(in).readLong();
                int count = readCount(in);
                List<SeedGround> grounds = new ArrayList<>(Math.min(count, 64));
                for (int i = 0; i < count; i++) {
                    DataInputStream ground = readRecord(in);
                    grounds.add(new SeedGround(dimensions.get(readVarint(ground)), ground.readUTF(), ground.readUTF()));
                }
                return new Decoded(kind, null, null, null, new Seed(seed, List.copyOf(grounds)));
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // --- The server's file ---------------------------------------------------------------

    /** The server's own file: every player and every mob it knows, in the same layout, whatever its size. */
    static byte[] saved(List<Player> players, List<Mob> mobs) {
        Encoder encoder = new Encoder(Integer.MAX_VALUE, 0);
        Records playerList = encoder.list();
        for (Player player : players) encoder.add(playerList, out -> writePlayer(out, encoder, player));
        Records mobList = encoder.list();
        for (Mob mob : mobs) encoder.add(mobList, out -> writeMob(out, encoder, mob));
        return write(FILE_VERSION, FILE_READABLE_FROM, out -> {
            encoder.writeTable(out);
            playerList.writeTo(out);
            mobList.writeTo(out);
        });
    }

    /**
     * The server's file read back: the players and the mobs, or null when it does not read. A
     * file of version 2 leaves out those it put in a dimension it could not name.
     */
    static @Nullable Saved readSaved(byte[] data) {
        Format format = format(data);
        if (format == null || !format.readableBy(FILE_VERSION)) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (format.version() == LEGACY) {
                in.skipNBytes(4);
                return new Saved(readLegacyPlayers(in), readLegacyMobs(in));
            }
            in.skipNBytes(8);
            List<String> dimensions = readTable(in);
            return new Saved(readPlayers(in, dimensions), readMobs(in, dimensions));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    record Saved(List<Player> players, List<Mob> mobs) {
    }

    // --- Writing -------------------------------------------------------------------------

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(int version, int readableFrom, Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeInt(readableFrom);
            writer.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] bytes(Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        return bytes.toByteArray();
    }

    /** A record: its length, then what it holds. */
    private static void writeRecord(DataOutputStream out, Writer writer) throws IOException {
        byte[] record = bytes(writer);
        writeVarint(out, record.length);
        out.write(record);
    }

    /** A list of records being written, its count written before them. */
    private static final class Records {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int count;

        void writeTo(DataOutputStream out) throws IOException {
            writeVarint(out, count);
            bytes.writeTo(out);
        }
    }

    /**
     * A message or a file being written: its lists of records, under a size, and the table of
     * the dimensions they name, written before them.
     */
    private static final class Encoder {
        private final long limit;
        private final List<String> dimensions = new ArrayList<>();
        private final Map<String, Integer> indices = new HashMap<>();
        /** Bytes of the table's names, the lists, their counts, and what is written last. */
        private long size;

        Encoder(long limit, int reserved) {
            this.limit = limit;
            this.size = HEAD + COUNT + reserved;
        }

        /** The index of a dimension in the table, added to it the first time. */
        int dimension(String id) {
            Integer index = indices.get(id);
            if (index != null) return index;
            indices.put(id, dimensions.size());
            dimensions.add(id);
            size += 2 + utfLength(id);
            return dimensions.size() - 1;
        }

        Records list() {
            size += COUNT;
            return new Records();
        }

        /** Adds a record to a list; false, with nothing added, when it does not fit. */
        boolean add(Records list, Writer fields) {
            int named = dimensions.size();
            long before = size;
            byte[] record;
            try {
                record = bytes(fields);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            long grown = size + varintSize(record.length) + record.length;
            if (grown > limit) {
                // The dimensions it named first go with it.
                while (dimensions.size() > named) indices.remove(dimensions.remove(dimensions.size() - 1));
                size = before;
                return false;
            }
            size = grown;
            try {
                writeVarint(list.bytes, record.length);
                list.bytes.write(record);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            list.count++;
            return true;
        }

        void writeTable(DataOutputStream out) throws IOException {
            writeVarint(out, dimensions.size());
            for (String id : dimensions) out.writeUTF(id);
        }
    }

    private static void writePlayer(DataOutputStream out, Encoder encoder, Player player) throws IOException {
        out.writeUTF(player.name());
        out.writeDouble(player.x());
        out.writeDouble(player.y());
        out.writeDouble(player.z());
        out.writeFloat(player.yaw());
        writeVarint(out, encoder.dimension(player.dimension()));
        Mount mount = player.mount();
        Look look = player.look();
        out.writeByte((player.online() ? 1 : 0) | (player.riding() ? 2 : 0) | (player.vehicle() != null ? 4 : 0)
                | (mount != null ? 8 : 0) | (look != null ? 16 : 0) | (player.test() ? 32 : 0)
                | (player.lastSeen() != 0 ? 64 : 0) | (player.gliding() ? 128 : 0));
        if (player.vehicle() != null) writeId(out, player.vehicle());
        if (player.lastSeen() != 0) out.writeLong(player.lastSeen());
        if (look != null) {
            writeText(out, look.textures());
            writeText(out, look.signature());
            out.writeByte(look.gear().size());
            for (String item : look.gear()) out.writeUTF(item);
        }
        if (mount != null) {
            out.writeUTF(mount.type());
            out.writeUTF(mount.nbt());
            out.writeDouble(mount.seatX());
            out.writeDouble(mount.seatY());
            out.writeDouble(mount.seatZ());
            out.writeFloat(mount.yaw());
            out.writeFloat(mount.riderYaw());
        }
        // From version 4, last: version 3 reads the record without it.
        if (player.gliding()) out.writeFloat(player.pitch());
    }

    private static void writeMob(DataOutputStream out, Encoder encoder, Mob mob) throws IOException {
        writeId(out, mob.id());
        out.writeDouble(mob.x());
        out.writeDouble(mob.y());
        out.writeDouble(mob.z());
        out.writeShort(mob.kind());
        writeVarint(out, encoder.dimension(mob.dimension()));
        out.writeByte((mob.baby() ? 1 : 0) | (mob.extra() != null ? 2 : 0));
        out.writeInt(mob.variant());
        if (mob.extra() != null) out.writeUTF(mob.extra());
    }

    private static void warnCut(int count, String what) {
        if (warnedCut) return;
        warnedCut = true;
        LOGGER.warn("Living Horizon: {} {} left out of a message, which would have been larger than the game allows",
                count, what);
    }

    // --- Reading -------------------------------------------------------------------------

    private static List<String> readTable(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<String> dimensions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) dimensions.add(in.readUTF());
        return dimensions;
    }

    /** A record's fields, from its length: what a later version added after them is left unread. */
    private static DataInputStream readRecord(DataInputStream in) throws IOException {
        return new DataInputStream(new ByteArrayInputStream(in.readNBytes(readCount(in))));
    }

    /** A count or a length, never more than the bytes left: a damaged one allocates nothing. */
    private static int readCount(DataInputStream in) throws IOException {
        int count = readVarint(in);
        if (count < 0 || count > in.available()) throw new EOFException("count past the end: " + count);
        return count;
    }

    private static List<Player> readPlayers(DataInputStream in, List<String> dimensions) throws IOException {
        int count = readCount(in);
        List<Player> players = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) players.add(readPlayer(readRecord(in), dimensions));
        return players;
    }

    private static Player readPlayer(DataInputStream in, List<String> dimensions) throws IOException {
        String name = in.readUTF();
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat();
        String dimension = dimensions.get(readVarint(in));
        int flags = in.readUnsignedByte();
        UUID vehicle = (flags & 4) != 0 ? readId(in) : null;
        long lastSeen = (flags & 64) != 0 ? in.readLong() : 0;
        Look look = (flags & 16) != 0 ? readLook(in) : null;
        Mount mount = (flags & 8) != 0 ? readMount(in) : null;
        boolean gliding = (flags & 128) != 0;
        float pitch = gliding ? in.readFloat() : 0f;
        return new Player(name, x, y, z, yaw, dimension, (flags & 1) != 0, (flags & 2) != 0, vehicle, mount, look,
                lastSeen, (flags & 32) != 0, gliding, pitch);
    }

    private static List<Mob> readMobs(DataInputStream in, List<String> dimensions) throws IOException {
        int count = readCount(in);
        List<Mob> mobs = new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            DataInputStream mob = readRecord(in);
            UUID id = readId(mob);
            double x = mob.readDouble(), y = mob.readDouble(), z = mob.readDouble();
            int kind = mob.readShort();
            String dimension = dimensions.get(readVarint(mob));
            int flags = mob.readUnsignedByte();
            int variant = mob.readInt();
            String extra = (flags & 2) != 0 ? mob.readUTF() : null;
            mobs.add(new Mob(id, x, y, z, kind, dimension, (flags & 1) != 0, variant, extra));
        }
        return mobs;
    }

    private static Look readLook(DataInputStream in) throws IOException {
        String textures = readText(in), signature = readText(in);
        int slots = in.readUnsignedByte();
        List<String> gear = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) gear.add(in.readUTF());
        return new Look(textures, signature, gear);
    }

    private static Mount readMount(DataInputStream in) throws IOException {
        return new Mount(in.readUTF(), in.readUTF(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readFloat(), in.readFloat());
    }

    // --- Version 2 -----------------------------------------------------------------------

    /** A message of version 2, after its version: a player or a mob in a dimension it could not name is left out. */
    private static @Nullable Decoded readLegacy(DataInputStream in) throws IOException {
        byte kind = in.readByte();
        if (kind == PLAYERS) return new Decoded(kind, readLegacyPlayers(in), null);
        if (kind == OFFLINE) return new Decoded(kind, readLegacyPlayers(in), null, Part.WHOLE);
        if (kind == MOBS) {
            String dimension = legacyDimension(in.readByte());
            double x = in.readDouble(), z = in.readDouble(), radius = in.readDouble();
            List<Mob> mobs = readLegacyMobs(in);
            // Around a player in a dimension it could not name: nothing that can be placed.
            if (dimension == null) return new Decoded(kind, null, new Mobs(List.of(), "", x, z, 0));
            return new Decoded(kind, null, new Mobs(mobs, dimension, x, z, radius));
        }
        return null;
    }

    private static List<Player> readLegacyPlayers(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Player> players = new ArrayList<>(Math.min(Math.max(count, 0), 1024));
        for (int i = 0; i < count; i++) {
            String name = in.readUTF();
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            float yaw = in.readFloat();
            String dimension = legacyDimension(in.readByte());
            int flags = in.readByte();
            UUID vehicle = (flags & 4) != 0 ? readId(in) : null;
            long lastSeen = (flags & 64) != 0 ? in.readLong() : 0;
            Look look = (flags & 16) != 0 ? readLook(in) : null;
            Mount mount = (flags & 8) != 0 ? readMount(in) : null;
            if (dimension == null) continue;
            players.add(new Player(name, x, y, z, yaw, dimension, (flags & 1) != 0, (flags & 2) != 0, vehicle, mount,
                    look, lastSeen, (flags & 32) != 0));
        }
        return players;
    }

    private static List<Mob> readLegacyMobs(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Mob> mobs = new ArrayList<>(Math.min(Math.max(count, 0), 4096));
        for (int i = 0; i < count; i++) {
            UUID id = readId(in);
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            int kind = in.readShort();
            int flags = in.readByte();
            int variant = in.readInt();
            String extra = (flags & 0x20) != 0 ? in.readUTF() : null;
            String dimension = legacyDimension(flags & 0x0F);
            if (dimension == null) continue;
            mobs.add(new Mob(id, x, y, z, kind, dimension, (flags & 0x10) != 0, variant, extra));
        }
        return mobs;
    }

    /** What version 2's number for a dimension meant; null for 3, any dimension of a mod. */
    static @Nullable String legacyDimension(int code) {
        return code >= 0 && code < LEGACY_DIMENSIONS.size() ? LEGACY_DIMENSIONS.get(code) : null;
    }

    // --- Pieces --------------------------------------------------------------------------

    /** A text that may be missing; longer than {@code writeUTF} allows, it is left out. */
    private static void writeText(DataOutputStream out, @Nullable String text) throws IOException {
        boolean kept = text != null && text.length() < 16_000;
        out.writeBoolean(kept);
        if (kept) out.writeUTF(text);
    }

    private static @Nullable String readText(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeId(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readId(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    /** A number of 0 or more, seven bits per byte, the lowest first: one byte up to 127. */
    private static void writeVarint(java.io.OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarint(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("number too long");
    }

    private static int varintSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    /** Bytes {@code writeUTF} writes for a text, its length included. */
    private static int utfLength(String text) {
        int length = 2;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            length += c >= 1 && c <= 0x7F ? 1 : c <= 0x7FF ? 2 : 3;
        }
        return length;
    }

    private static int intAt(byte[] data, int at) {
        return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
    }
}
