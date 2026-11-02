package fr.clixmods.livinghorizon;

import com.google.gson.annotations.SerializedName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.track.Vehicles;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/livinghorizon.json}. Read once at start, written back so that a player
 * always finds every setting in the file, including the ones added by a newer version.
 */
public final class FarConfig {
    /** Distant players are drawn at all. The toggle key flips this. */
    public boolean enabled = true;

    /** Layout of this file; older files are brought up to date on load. */
    public int version = 0;

    /**
     * The smallest size, in screen pixels, a distant player is drawn at. 0, the default,
     * draws everyone at their true size, where they are: a player 1.8 blocks tall is
     * three pixels high at 500 blocks on a 1080p screen. Above 0 they stop shrinking
     * past some distance and look huge next to the terrain around them.
     */
    public double minApparentPixels = 0.0;

    /**
     * Pushes the game's far clipping plane (four times the render distance) out to the
     * farthest distant player, so that they are drawn where they really are and hidden
     * by the terrain in front of them - the far terrain's included. Off, players past the plane are drawn closer and smaller instead, and then
     * show through the terrain between.
     */
    public boolean extendFarPlane = true;

    /**
     * The far terrain's depth (Voxy's or Distant Horizons') in the depth buffer while the
     * entities are drawn, so that it hides distant mobs pixel by pixel. Needed with Distant
     * Horizons, and with shader packs that keep Voxy out of the depth buffer (Photon does).
     * Named {@code voxyOcclusion} before Distant Horizons.
     */
    @SerializedName(value = "depthOcclusion", alternate = "voxyOcclusion")
    public boolean depthOcclusion = true;

    private static final int CURRENT = 13;

    /** Skinned figures, which Distant Friends uses as its fake players: shown, standing still. */
    private static final String MANNEQUIN = "minecraft:mannequin";

    /**
     * The mobs a server shares and mannequins: what a new file holds before the game has
     * registered its mobs, which {@link #adoptNewMobs} then adds.
     */
    private static List<String> defaultMobTypes() {
        List<String> types = new ArrayList<>(MobKinds.TYPES);
        types.add(MANNEQUIN);
        return types;
    }

    private static List<String> defaultStillMobTypes() {
        return new ArrayList<>(List.of("minecraft:happy_ghast", MANNEQUIN));
    }


    /**
     * Players who logged off stay where they were last, until they come back. Off, they are
     * not followed at all: not drawn, not remembered, and the file of who rests where is
     * left as it is.
     */
    public boolean offlinePlayers = true;

    /** The value of {@link #maxOfflinePlayers} that means no limit; any number below is one. */
    public static final int UNLIMITED_OFFLINE = Integer.MAX_VALUE;
    /** The last number of {@link #maxOfflinePlayers}' slider, before no limit; a higher one can be typed. */
    public static final int MAX_OFFLINE = 1000;

    /**
     * How many players who logged off are drawn at once: the nearest ones. The others stay
     * remembered, and show again when they are among the nearest. {@link #UNLIMITED_OFFLINE}: all.
     */
    public int maxOfflinePlayers = 256;

    /**
     * Players who logged off live their life where they left, as long as the blocks there are
     * loaded: they stroll, look around, sit down, stand in front of chests and workstations,
     * walk to a bed they can reach at night, raise a shield when hit or when monsters come.
     * Off, they sit where they logged off. Either way they keep to the ground.
     */
    public boolean offlineLife = true;

    /**
     * Blocks: how far from where they logged off players who logged off walk around and look
     * for a bed or something to look at, when {@link #offlineLife} is on. 50 is about a village.
     */
    public int offlineRoam = 50;

    /**
     * Their name above a player who logged off, marked offline, as close as the game shows
     * names; and when they were last seen, under the crosshair, when aimed at from right next to them.
     */
    public boolean offlineNames = true;

    /**
     * Mobs met on the way stay where they were once out of range, living there on their
     * own (no AI: a stroll around, a look around, grazing).
     */
    public boolean distantMobs = true;

    /** The value of {@link #maxDistantMobs} and {@link #maxDistantEntities} that means no limit; any number below is one. */
    public static final int UNLIMITED_MOBS = Integer.MAX_VALUE;
    /** The last number of their sliders, before no limit; a higher one can be typed. */
    public static final int MAX_MOBS = 4000;

    /** Blocks: the last distance of the sliders before no limit, which the settings keep as 0; a farther one can be typed. */
    public static final int MAX_DISTANCE = 4000;

    /** How many remembered mobs are drawn at once: the nearest ones. {@link #UNLIMITED_MOBS}: all. */
    public int maxDistantMobs = 512;

    /**
     * Boats, vehicles and mannequins met on the way stay where they were, apart from the mobs:
     * the other tab of the list of mobs ({@link fr.clixmods.livinghorizon.track.MobKinds#creature}).
     */
    public boolean distantEntities = true;

    /**
     * How many remembered boats, vehicles and mannequins are drawn at once, the nearest ones,
     * apart from {@link #maxDistantMobs}: a crowd of mobs never pushes an aircraft out.
     * {@link #UNLIMITED_MOBS}: all.
     */
    public int maxDistantEntities = 256;

    /** Whether remembered figures of this kind are shown: the mobs, or the other entities. */
    public boolean shows(boolean creature) {
        return creature ? distantMobs : distantEntities;
    }

    /** How many of this kind are drawn at once, the nearest ones: every one past {@link #UNLIMITED_MOBS}. */
    public int most(boolean creature, int there) {
        int most = creature ? maxDistantMobs : maxDistantEntities;
        return most >= UNLIMITED_MOBS ? there : Math.max(0, Math.min(most, there));
    }

    /** Blocks: past this, distant mobs are not drawn. 0 is no limit. */
    public int mobMaxDistance = 512;

    /**
     * Blocks: past this, distant boats, vehicles and mannequins are not drawn, apart from the
     * mobs' limit: far by default, since there are few of them and an aircraft is seen from
     * afar. 0 is no limit, which the screens ask about first.
     */
    public int entityMaxDistance = MAX_DISTANCE;

    /**
     * Distant mobs are drawn only where terrain is drawn under them: a chunk the game draws, or
     * the far terrain of Voxy or Distant Horizons. Off, a mob remembered where neither has drawn
     * anything yet stands in the void.
     */
    public boolean onlyOverTerrain = true;

    /** Blocks: past this, distant players are not drawn. 0 is no limit, which the screens ask about first. */
    public int playerMaxDistance = MAX_DISTANCE;

    /**
     * The mobs shown far away, by type id ({@code "minecraft:horse"}). This client
     * remembers these when it sees them leave; a server with the mod shares its own list
     * ({@link MobKinds#TYPES}), and only those of its mobs in this list are shown.
     * Every kind is shown from the start, monsters too ({@link #adoptNewMobs}): one that
     * despawned is forgotten when the player comes back and it is not there.
     * Chosen in game under "Choose mobs...".
     */
    public List<String> mobTypes = defaultMobTypes();

    /**
     * Mobs of {@link #mobTypes} shown standing still, where they were, instead of strolling
     * around: a happy ghast drifting around on its own looks wrong.
     */
    public List<String> stillMobTypes = defaultStillMobTypes();

    /** A mob with a name tag is remembered and shown whatever its type. */
    public boolean rememberNamedMobs = true;

    /**
     * The animals a server with the mod foresees in the land nobody has explored yet, from the
     * world's seed ({@code track/SeedMobs}), are shown with the remembered mobs.
     */
    public boolean seedMobs = true;

    /**
     * Chunks around the player read by a scan ("Scan the chunks around", or
     * {@code /livinghorizon scan}): in a single player world, the mobs of these chunks
     * are read from the world itself instead of met on the way.
     */
    public int scanRadius = 32;

    /**
     * Chunks around the player <i>Guess Nearby Mobs</i> foresees the first animals of, in land
     * nobody explored ({@code track/Foresight}): up to 1024.
     */
    public int foreseeRadius = 64;

    /**
     * Birds and bats: flocks, birds of prey, gulls, pigeons, robins, tits, bats at night.
     * Only a picture, on this client.
     */
    public boolean skyBirds = true;

    /** Percent: how many birds, all kinds together. 100 is the usual; 0 none; 500 five times as many. */
    public int birdDensity = 100;

    /** Blocks: how far away birds can be. */
    public int birdMaxDistance = 800;

    /** Blocks above you (or the sea, whichever is higher) below which flying flocks never go. */
    public int birdMinHeight = 30;

    /** How much bigger than life the birds are drawn. */
    public int birdSize = 1;

    /**
     * How birds are drawn: {@code "3d"} as small box models, like the game's mobs, with
     * beating wings and legs; {@code "2d"} as flat pixel sprites. Bats and parrots are the
     * game's own either way.
     */
    public String birdStyle = "3d";

    /**
     * Kinds of birds switched off, by id: {@code flocks}, {@code geese}, {@code raptors},
     * {@code gulls}, {@code pigeons}, {@code robins}, {@code tits}, {@code bats}, {@code parrots}.
     */
    public List<String> hiddenBirds = new ArrayList<>();

    /** The easter eggs: secret, never described anywhere a player reads. Only pictures, on this screen. */
    public boolean easterEggs = true;
    /** Before version 10, {@link #easterEggs} for the one there was; read once, then dropped. */
    private @Nullable Boolean ufo;

    /** Draws what a player was riding when last seen: horse, boat, happy ghast... */
    public boolean showVehicles = true;

    /** Lets vehicles carrying another player be drawn at any distance the server still sends them. */
    public boolean renderTrackedVehiclesFar = true;

    /**
     * Distant mobs the terrain hides entirely - the far terrain's included - are not drawn at
     * all, instead of drawn and covered pixel by pixel. Cheaper with many mobs. Asked of the
     * GPU against the depth of the picture ({@link #optOcclusionQueries}), so a mob with one
     * pixel in view is still drawn, and one appears a frame or two after the view clears.
     */
    public boolean hideOccludedMobs = true;

    /**
     * Distant mobs past {@link #impostorDistance}, or smaller on screen than
     * {@link #impostorPixels}, are drawn as a flat picture of
     * themselves, turned to the camera, instead of their whole model. The pictures are
     * baked from the game's own renderers when a world is joined and again whenever the
     * resource packs change, so skins, mobs and packs are all followed. "Simplified Mobs";
     * {@link #impostorEntities} and {@link #impostorPlayers} do the same for the others.
     */
    public boolean impostors = true;

    /** Boats, vehicles and mannequins as flat pictures too, as mobs are ("Simplified Entities"). */
    public boolean impostorEntities = true;

    /** Whether anything is drawn as a flat picture: mobs, the other entities or players. */
    public boolean anyImpostors() {
        return impostors || impostorEntities || impostorPlayers;
    }

    /** Blocks: past this distance an impostor stands in for the model. 0 goes by the size alone. */
    public int impostorDistance = 128;

    /**
     * Pixels: a figure shorter than this on screen is an impostor too, even closer than
     * {@link #impostorDistance} (its model back only a little above). 0 goes by the distance alone;
     * with both 0, nothing is simplified.
     */
    public int impostorPixels = 10;

    /** Top of {@link #impostorDistance}'s range, which starts at 0 (off). */
    public static final int MAX_IMPOSTOR_DISTANCE = 2000;

    /** Range of {@link #impostorPixels}. */
    public static final int MAX_IMPOSTOR_PIXELS = 64;

    /** Distant players become impostors too; off, they keep their whole model at any distance. */
    public boolean impostorPlayers = true;

    /**
     * Megabytes of video memory the mobs' impostor pictures may take, apart from the players':
     * taken only as looks are met, the picture used longest ago making room when full.
     */
    public int impostorMobMemory = 1024;

    /**
     * Megabytes of video memory the players' impostor pictures may take (riders with what they
     * ride included), apart from the mobs'. Thrown away a minute after their last use.
     */
    public int impostorPlayerMemory = 512;

    /** Ranges of the two above, in megabytes. */
    public static final int MIN_MOB_MEMORY = 64, MAX_MOB_MEMORY = 4096, MIN_PLAYER_MEMORY = 32, MAX_PLAYER_MEMORY = 2048;

    /**
     * Mob types already offered once, the game's and other mods'. A new one is shown far
     * away from the start, walking, then left as the player sets it. Named
     * {@code knownModdedMobs} when only other mods' mobs were offered, monsters excepted.
     */
    @SerializedName(value = "knownMobTypes", alternate = "knownModdedMobs")
    public List<String> knownMobTypes = defaultMobTypes();

    /**
     * Vehicles met so far that are neither mobs nor boats ({@link Vehicles}): an aircraft, a car,
     * a minecart. Only seeing one tells that its kind is a vehicle, so the list of mobs offers
     * these kinds once met. What the game is, not a choice: a reset keeps it.
     */
    public List<String> knownVehicles = new ArrayList<>();

    /**
     * Those of {@link #knownVehicles} that only seat their riders (a minecart, a moving
     * contraption): listed, but never shown by default, which a reset keeps too.
     */
    public List<String> seatedVehicles = new ArrayList<>();

    // --- Optimisations: each one can be switched off, to see what it saves -----------------

    /** Copies outside the view are not prepared at all. */
    public boolean optViewCulling = true;
    /** Copies smaller than about half a pixel are not prepared (only when no minimum size is set). */
    public boolean optTinyCulling = true;
    /** The far terrain's depth is merged only on frames that draw something past the render distance. */
    @SerializedName(value = "optLazyDepth", alternate = "optLazyVoxyDepth")
    public boolean optLazyDepth = true;
    /** The far world (Voxy, Distant Horizons, Lodistance or Far View) is read on several threads at once rather than one. */
    @SerializedName(value = "optParallelRead", alternate = "optParallelVoxy")
    public boolean optParallelRead = true;
    /** Ground already read in the far world is kept for two minutes. */
    public boolean optColumnCache = true;
    /**
     * How {@link #hideOccludedMobs} decides: occlusion queries against the depth of the picture
     * (see {@code OcclusionQueries}), what the player sees, a frame or two late. Off, to
     * compare: lines of sight in the far terrain's world.
     */
    @SerializedName(value = "optOcclusionQueries", alternate = "optHiZ")
    public boolean optOcclusionQueries = true;
    /**
     * Distant Horizons' fade of the game's picture into its terrain, without a shader pack,
     * leaves the entities alone (see {@code FarDepth.beforeFade}). Off, to compare: it paints
     * its terrain over the distant mobs.
     */
    public boolean optDhFade = true;
    /**
     * Under a shader pack that shades past the render distance from Voxy's own depth (Photon),
     * our far figures are written into it, on their own pixels only, so that the terrain behind
     * them does not cast its shadow across them (see {@code FarDepth.intoVoxy}). Off: nothing of
     * Voxy's is ever written, and those shadows show on distant mobs.
     */
    public boolean optVoxyShadows = true;
    /**
     * Without a shader pack, the world's entities get Voxy's fog, which stops growing past a
     * limit, in place of the game's (see {@code FarFog}). Off: the game's fog, under which far
     * mobs turn paler than Voxy's terrain; enchantment glints and lightning then fade as usual.
     */
    public boolean optVoxyFog = true;

    /**
     * Without a shader pack, the world's entities get Distant Horizons' far fog, which takes the
     * game's away (see {@code DhFog}). Off: no fog on them, sharp against its fogged terrain.
     */
    public boolean optDhFog = true;
    /** Saved mobs are read off the game's thread and built within 2 ms per tick. */
    public boolean optBackgroundBuild = true;
    /** A mob only a few pixels high on screen stops walking about: it stands where it is. */
    public boolean optStillTiny = true;
    /**
     * Pixels: a distant mob shorter than this on screen stops walking about and stands (it
     * resumes a little above, so it never flickers). 0 always animates. Needs {@link #optStillTiny}.
     */
    public int animationMinPixels = 10;
    /** Blocks: past this, every distant mob stops walking about and stands, however big it looks. 0 is no limit, which the screens ask about first. */
    public int animationMaxDistance = MAX_DISTANCE;
    /** A copy that was not drawn for a moment (out of view, hidden, too small) stays where it is: no animation, no path read. */
    public boolean optFreezeHidden = false;

    // --- Debug -----------------------------------------------------------------------------

    /** A coloured box around every distant mob and player, seen through terrain. */
    public boolean debugBoxes = false;
    /** Its state, kind, distance and size on screen above each box. */
    public boolean debugLabels = false;
    /** Boxes around the mobs the game draws itself, too. */
    public boolean debugGameMobs = false;
    // Which other kinds get their box and label, in the Boxes screen.
    /** Around the real mobs the server sends that the mod draws. */
    public boolean boxLiveMobs = true;
    /** Around the copies of mobs met. */
    public boolean boxCopies = true;
    /** Around the animals foreseen from the seed, in land nobody explored. */
    public boolean boxForeseen = true;
    /** Around the distant players. */
    public boolean boxPlayers = true;
    /** Around the mobs left out of the frame (too small, hidden, over the void, outside the view...). */
    public boolean boxLeftOut = true;
    // Outlines, seen through terrain, in the colours of the debug boxes: what draws each figure.
    /** Around the mobs the game draws itself: white. */
    public boolean outlineGameMobs = false;
    /** Around the real mobs the server sends that the mod draws, past the game's entity distance: cyan. */
    public boolean outlineLiveMobs = false;
    /** Around the 3D copies of mobs the server no longer sends: green. */
    public boolean outlineCopies = false;
    /** Around the copies of animals foreseen from the seed, in land nobody explored: pink. */
    public boolean outlineForeseen = false;
    /** Around the distant players the mod draws: blue. */
    public boolean outlinePlayers = false;
    /** Around impostors, whatever they stand for: magenta. */
    public boolean outlineImpostors = false;
    /** A cross where a distant mob is left out of the frame, in the colour of why: where it is, not what. */
    public boolean outlineLeftOut = false;
    /** Before version 8, one white outline around every distant figure; read once, then dropped. */
    private @Nullable Boolean glowOutline;
    /** Before version 8, the outlines and crosses on the mod's figures together; read once, then dropped. */
    private @Nullable Boolean debugOutlines;
    /** Before version 9, how a player who logged off was drawn: {@code "sleep"}, {@code "sit"} or {@code "hidden"}. */
    private @Nullable String offlinePose;
    /** A panel of what the mod does and costs, in the corner of the screen. */
    public boolean debugHud = false;
    /** 0: off; 1: the game's depth; 2: with the far terrain merged in; 3: after the entities; 4: the whole world drawn. */
    public int debugDepthView = 0;

    /** Birds drawn as 3D models rather than flat sprites. */
    public boolean birdModels() {
        return !"2d".equals(birdStyle);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FarConfig instance = new FarConfig();

    /** Whether anything distant is drawn: players, mobs or both, each on its own switch. */
    public boolean anyDistant() {
        return enabled || distantMobs || distantEntities;
    }

    public static FarConfig get() {
        return instance;
    }

    private static Path path() {
        return Platform.configDir().resolve("livinghorizon.json");
    }

    public static void load() {
        Path path = path();
        JsonElement root = JsonStore.read(path);
        if (root != null) {
            try {
                FarConfig read = GSON.fromJson(root, FarConfig.class);
                if (read != null) instance = read;
            } catch (RuntimeException e) {
                // A typo made by hand: the file is kept aside, not written over with the defaults.
                JsonStore.keepBad(path, e);
            }
            if (instance.version > CURRENT) JsonStore.keepNewer(path, instance.version);
        }
        migrate(instance);
        save();
    }

    /**
     * Brings settings read from an older file up to date, one layout at a time, and fills in
     * what the file left out. Does nothing to settings already up to date, the defaults included.
     */
    static void migrate(FarConfig config) {
        if (config.version < 2) {
            // 0.1.0 kept far players at least 14 pixels tall by default, which made
            // them giants next to the terrain they stand on.
            config.minApparentPixels = 0.0;
        }
        if (config.mobTypes == null) config.mobTypes = defaultMobTypes();
        if (config.stillMobTypes == null) config.stillMobTypes = defaultStillMobTypes();
        if (config.version < 5) {
            // Mannequins came with Distant Friends' fake players: shown, and still, unless set otherwise.
            if (!config.mobTypes.contains(MANNEQUIN)) config.mobTypes.add(MANNEQUIN);
            if (!config.stillMobTypes.contains(MANNEQUIN)) config.stillMobTypes.add(MANNEQUIN);
        }
        if (config.version < 4 && config.birdSize == 2) {
            // Twice the size suited lone parrots; silhouettes come in their own sizes.
            config.birdSize = 1;
        }
        if (config.hiddenBirds == null) config.hiddenBirds = new ArrayList<>();
        if (config.birdStyle == null) config.birdStyle = "3d";
        if (config.knownMobTypes == null) config.knownMobTypes = defaultMobTypes();
        if (config.knownVehicles == null) config.knownVehicles = new ArrayList<>();
        if (config.seatedVehicles == null) config.seatedVehicles = new ArrayList<>();
        if (config.version < 3) {
            // Boats came after the list was first written: shown unless taken out by hand.
            for (String type : MobKinds.TYPES) {
                if (MobKinds.isBoat(type) && !config.mobTypes.contains(type)) config.mobTypes.add(type);
            }
        }
        if (config.version < 6) {
            // The far terrain's depth on mobs and skipping the mobs it hides left the main
            // settings for the debug screen, on: whoever had turned them off could not tell.
            config.depthOcclusion = true;
            config.hideOccludedMobs = true;
            config.optOcclusionQueries = true;
        }
        if (config.version < 7) {
            // New defaults: whoever never changed the old ones gets them.
            if (config.maxDistantMobs == 200) config.maxDistantMobs = 512;
            if (config.mobMaxDistance == 0) config.mobMaxDistance = 512;
            if ("sleep".equals(config.offlinePose)) config.offlinePose = "sit";
            if (config.impostorDistance == 200) config.impostorDistance = 128;
            config.impostors = true;
        }
        if (config.version < 8) {
            // One switch per kind of figure now; the old ones turn on what they covered.
            if (Boolean.TRUE.equals(config.glowOutline)) {
                config.outlineLiveMobs = config.outlineCopies = config.outlinePlayers = true;
                config.outlineImpostors = true;
            }
            if (Boolean.TRUE.equals(config.debugOutlines)) {
                config.outlineCopies = config.outlineImpostors = config.outlineLeftOut = true;
            }
            config.glowOutline = null;
            config.debugOutlines = null;
        }
        if (config.version < 9) {
            // Hiding them is a switch of its own now, which also stops remembering them; the
            // poses gave way to a life of their own.
            if ("hidden".equals(config.offlinePose)) config.offlinePlayers = false;
            config.offlinePose = null;
        }
        if (config.version < 10) {
            if (Boolean.FALSE.equals(config.ufo)) config.easterEggs = false;
            config.ufo = null;
        }
        if (config.version < 11) {
            // Every kind is offered now, the game's monsters too: those shown by default
            // before keep the player's choice, the others are shown once the game is started.
            for (String type : defaultMobTypes()) {
                if (!config.knownMobTypes.contains(type)) config.knownMobTypes.add(type);
            }
        }
        if (config.version < 12) {
            // One switch for every figure but players became one for mobs and one for the rest.
            config.impostorEntities = config.impostors;
        }
        if (config.version < 13) {
            // No limit was 1000; the sliders go further now, then no limit, and any number can be typed.
            if (config.maxDistantMobs >= 1000) config.maxDistantMobs = UNLIMITED_MOBS;
            if (config.maxDistantEntities >= 1000) config.maxDistantEntities = UNLIMITED_MOBS;
            if (config.maxOfflinePlayers >= 1000) config.maxOfflinePlayers = UNLIMITED_OFFLINE;
            // No limit is never safe: the presets and defaults that went without one, which the
            // player did not choose by hand, take the limits they have now.
            boolean high = config.impostorDistance == 256 && config.maxDistantMobs == UNLIMITED_MOBS
                    && config.mobMaxDistance == 1024 && config.birdDensity == 200 && config.birdMaxDistance == 1500;
            boolean ultra = config.impostorDistance == 512 && config.maxDistantMobs == UNLIMITED_MOBS
                    && config.mobMaxDistance == 0 && config.birdDensity == 400 && config.birdMaxDistance == 3000;
            if (high) QualityPreset.HIGH.apply(config);
            if (ultra) QualityPreset.ULTRA.apply(config);
            if (config.playerMaxDistance <= 0) config.playerMaxDistance = MAX_DISTANCE;
            if (config.entityMaxDistance <= 0) config.entityMaxDistance = MAX_DISTANCE;
            if (config.animationMaxDistance <= 0) config.animationMaxDistance = MAX_DISTANCE;
        }
        // Written by hand out of range: the nearest end.
        config.impostorMobMemory = Math.clamp(config.impostorMobMemory, MIN_MOB_MEMORY, MAX_MOB_MEMORY);
        config.impostorPlayerMemory = Math.clamp(config.impostorPlayerMemory, MIN_PLAYER_MEMORY, MAX_PLAYER_MEMORY);
        config.impostorPixels = Math.clamp(config.impostorPixels, 0, MAX_IMPOSTOR_PIXELS);
        config.impostorDistance = Math.clamp(config.impostorDistance, 0, MAX_IMPOSTOR_DISTANCE);
        config.version = CURRENT;
    }

    /**
     * Mob types met for the first time, the game's and other mods': shown far away, walking.
     * Called once the game has registered them.
     */
    public static void adoptNewMobs(Iterable<EntityType<?>> types) {
        if (instance.adopt(types)) save();
    }

    private boolean adopt(Iterable<EntityType<?>> types) {
        boolean changed = false;
        for (EntityType<?> type : types) {
            String id = EntityType.getKey(type).toString();
            if (knownMobTypes.contains(id)) continue;
            knownMobTypes.add(id);
            if (!mobTypes.contains(id) && !seatedVehicles.contains(id)) mobTypes.add(id);
            changed = true;
        }
        return changed;
    }

    /** The mob choices back to their defaults: every kind shown, the happy ghast and mannequins still. */
    public void resetMobs(Iterable<EntityType<?>> types) {
        mobTypes = defaultMobTypes();
        stillMobTypes = defaultStillMobTypes();
        knownMobTypes = defaultMobTypes();
        adopt(types);
    }

    /**
     * A kind of vehicle met, its first one seen: listed among the mobs from now on. One its rider
     * steers, as a boat, is shown far away from the start; one that only seats its riders (a
     * minecart, a moving contraption) waits to be chosen. Either is then left as the player sets it.
     */
    public static void adoptVehicle(String id, boolean steered) {
        boolean changed = instance.knowVehicle(id, steered);
        // Offered here, once: as the game starts, a known kind is left as it is.
        if (!instance.knownMobTypes.contains(id)) {
            instance.knownMobTypes.add(id);
            if (steered && !instance.mobTypes.contains(id)) instance.mobTypes.add(id);
            changed = true;
        }
        if (changed) save();
    }

    /**
     * A kind is a vehicle, steered or only seating its riders: listed among the mobs, and offered
     * as such ({@link #adoptNewMobs}). True when it was not known yet. Not saved by itself.
     */
    public boolean knowVehicle(String id, boolean steered) {
        if (knownVehicles.contains(id)) return false;
        knownVehicles.add(id);
        if (!steered) seatedVehicles.add(id);
        return true;
    }

    /** Every setting back to its default, mob and bird choices included, and written to the file. */
    public static void reset(Iterable<EntityType<?>> types) {
        FarConfig old = instance;
        instance = new FarConfig();
        instance.version = CURRENT;
        // Which kinds are vehicles is what the game is, not a setting.
        instance.knownVehicles = old.knownVehicles;
        instance.seatedVehicles = old.seatedVehicles;
        instance.adopt(types);
        save();
    }

    public static void save() {
        Path path = path();
        try {
            JsonStore.write(path, writer -> GSON.toJson(instance, writer));
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
