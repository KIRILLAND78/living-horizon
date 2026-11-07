package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.ambient.Herd;
import fr.clixmods.livinghorizon.compat.DrawnGround;
import fr.clixmods.livinghorizon.compat.EcstaticLink;
import fr.clixmods.livinghorizon.compat.EntityCulling;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarViewLink;
import fr.clixmods.livinghorizon.compat.IceAndFire;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.compat.ValkyrienSkies;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorThreshold;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobFlight;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.Puppets;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.track.SeedDraws;
import fr.clixmods.livinghorizon.track.SharedPositions;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.minecraft.client.renderer.MultiBufferSource;
import java.util.ArrayList;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds the distant players to the frame, next to the entities the game draws itself.
 *
 * <p>The game draws the world in two passes: extraction copies what each entity looks
 * like into a render state, submission turns those states into geometry. A puppet's
 * state is extracted by the game's own renderer, exactly like a real player's, and
 * added to the same list - so skins, armour, capes, elytras, mounts and shader packs
 * all come for free.
 *
 * <p>They are drawn where they really are, so that the terrain in front of them hides
 * them: Voxy writes its depth into the game's, and removes the distance fog. The one
 * limit is the far clipping plane, which {@code GameRendererMixin} pushes out to the
 * farthest of them. Past it (or with {@link FarConfig#extendFarPlane} off), a player is
 * drawn closer along the same line of sight and smaller by the same ratio: the same
 * picture, but in front of the terrain between.
 */
public final class GhostRenderer {
    /**
     * How one state is moved and scaled at submission. Positions relative to the camera.
     * Kept and filled again from one frame to the next: read it during the frame it is given.
     */
    public static final class Transform {
        private double anchorX, anchorY, anchorZ, pull, scale;

        public double anchorX() {
            return anchorX;
        }

        public double anchorY() {
            return anchorY;
        }

        public double anchorZ() {
            return anchorZ;
        }

        public double pull() {
            return pull;
        }

        public double scale() {
            return scale;
        }
    }

    private static final Map<EntityRenderState, Transform> TRANSFORMS = new IdentityHashMap<>();
    private static final Set<Entity> EXTRACTED = Collections.newSetFromMap(new IdentityHashMap<>());
    /** The transforms handed out, the first {@link #transforms} of them this frame. */
    private static Transform[] transformPool = new Transform[64];
    private static int transforms;

    /** A transform for this frame, one of last frame's written over. */
    private static Transform nextTransform() {
        if (transforms == transformPool.length) transformPool = Arrays.copyOf(transformPool, transforms * 2);
        Transform transform = transformPool[transforms];
        if (transform == null) transformPool[transforms] = transform = new Transform();
        transforms++;
        return transform;
    }

    private GhostRenderer() {
    }

    /** Blocks. Past this the depth buffer is too coarse to be worth extending to. */
    private static final float MAX_FAR = 65536f;

    /** The farthest distant player drawn last frame, which this frame's far plane covers. */
    private static double farthest;
    private static float neededFar;
    /**
     * Something drawn this far away this frame, somewhere on screen: the next far plane covers it,
     * and past the render distance the far terrain's depth is merged over the whole screen.
     */
    public static void noteDistance(double distance) {
        farthest = Math.max(farthest, distance);
        if (distance > drawnRange) FarDepth.neededAnywhere();
    }

    /**
     * Something drawn this far away this frame, within {@code radius} blocks of {@code (x, y, z)}
     * from the camera: the next far plane covers it, and the far terrain's depth covers that place.
     */
    public static void noteDistance(double distance, double x, double y, double z, double radius) {
        farthest = Math.max(farthest, distance);
        FarDepth.around(x, y, z, radius);
        if (distance > drawnRange) FarDepth.needed();
    }

    // --- What is worth drawing ----------------------------------------------------------

    /** Nanoseconds a frame may spend making the puppets of players who logged off; one is made at least. */
    private static final long MAKE_BUDGET = 1_000_000L;

    /** The view this frame: where the camera looks, how wide, how many pixels a radian is. */
    private static double eyeX, eyeY, eyeZ, lookX, lookY, lookZ, viewAngle, pixelsPerRadian, drawnRange;
    /** Figures drawn this frame, models or pictures, and those left out: for the debug panel. */
    private static int drawn, skipped;

    private static void view(Camera camera, Minecraft minecraft) {
        Vec3 eye = camera.position();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        Vector3fc look = camera.forwardVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        double fov = Math.toRadians(minecraft.options.fov().get());
        int width = Math.max(1, minecraft.getWindow().getWidth()), height = Math.max(1, minecraft.getWindow().getHeight());
        // Half the screen's diagonal, as an angle, with a margin for a quick turn of the head.
        double aspect = width / (double) height;
        viewAngle = Math.atan(Math.tan(fov / 2) * Math.sqrt(1 + aspect * aspect)) + 0.15;
        pixelsPerRadian = DepthFar.pixelsPerRadian(minecraft);
        drawnRange = minecraft.options.getEffectiveRenderDistance() * 16 - 16;
        debug = DebugMarks.active();
        if (debug) DebugMarks.begin(eye, pixelsPerRadian);
    }

    /** The debug view is on this frame: every puppet says what became of it. */
    private static boolean debug;

    /**
     * Why a puppet is not worth extracting at all, or null when it is: outside the view,
     * or - for {@code mayVanish} - smaller than about half a pixel at its true size.
     * Extraction is the costly part of drawing a puppet, and most remembered mobs are
     * behind the camera or specks. Each test can be switched off to measure it.
     */
    static @Nullable Mark skip(Entity entity, boolean mayVanish) {
        double size = Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()));
        return count(why(entity.getX() - eyeX, entity.getY() + size * 0.5 - eyeY, entity.getZ() - eyeZ, size, mayVanish));
    }

    /**
     * Whether something of the mod's own, {@code size} blocks across around a point this far
     * from the camera, is outside the view or smaller than about half a pixel: the tests
     * {@link #skip} makes for a puppet. For the birds, not counted among the puppets.
     */
    public static boolean outOfSight(double dx, double dy, double dz, double size) {
        return why(dx, dy, dz, Math.max(0.5, size), true) != null;
    }

    private static @Nullable Mark why(double dx, double dy, double dz, double size, boolean mayVanish) {
        FarConfig config = FarConfig.get();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < size * 2 + 4) return null;
        if (config.optViewCulling) {
            double angle = Math.acos(Mth.clamp((dx * lookX + dy * lookY + dz * lookZ) / distance, -1, 1));
            if (angle > viewAngle + Math.asin(Math.min(1, size / distance))) return Mark.OUTSIDE;
        }
        if (mayVanish && config.optTinyCulling && config.minApparentPixels <= 0
                && size / distance * pixelsPerRadian < 0.6) {
            return Mark.TINY;
        }
        return null;
    }

    /** Whether something {@code size} blocks tall standing at {@code x, y, z} is in the view this frame, or right by the camera. */
    private static boolean inView(double x, double y, double z, double size) {
        double dx = x - eyeX, dy = y + size * 0.5 - eyeY, dz = z - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < size * 2 + 4) return true;
        double angle = Math.acos(Mth.clamp((dx * lookX + dy * lookY + dz * lookZ) / distance, -1, 1));
        return angle <= viewAngle + Math.asin(Math.min(1, size / distance));
    }

    private static @Nullable Mark count(@Nullable Mark why) {
        if (why != null) skipped++;
        return why;
    }

    /** Not drawn this frame; the debug view is told why. */
    private static boolean skipped(Entity entity, boolean mayVanish) {
        Mark why = skip(entity, mayVanish);
        if (why == null) return false;
        if (debug) DebugMarks.mark(entity, why);
        return true;
    }

    public static void beginFrame() {
        OcclusionQueries.beginFrame();
        FarDepth.beginFigures();
        EXTRACTED.clear();
        TRANSFORMS.clear();
        transforms = 0;
        glowing = false;
        //? if <1.21.9 {
        /*LIGHTS.clear();
        CLASSIC.clear();
        *///?}
        //? if >=1.21.2 && <1.21.9
        /*RENDERERS.clear();*/
        ImpostorRenderer.beginFrame();
        PolygonStats.beginFrame();
        forgetShown();
        neededFar = farthest > 0 ? (float) Math.min(MAX_FAR, farthest * 1.1 + 64) : 0f;
        farthest = 0;
    }

    /**
     * The far plane this frame needs: the projection is built before the entities are
     * extracted, so it covers the players of the frame before, with room to spare. While
     * Ecstatic draws its terrain, its own far plane: it tests its terrain against the game's
     * depth with that plane, and the two depths only agree with the same one.
     */
    public static float farPlane(float vanilla) {
        FarConfig config = FarConfig.get();
        if (!config.anyDistant() || !config.extendFarPlane) return vanilla;
        float ecstatic = EcstaticLink.farPlane();
        if (ecstatic > 0f) return ecstatic;
        return Math.max(vanilla, neededFar);
    }

    /** The game extracted this entity itself this frame. */
    public static void extracted(Entity entity) {
        EXTRACTED.add(entity);
    }

    //? if >=1.21.9 {
    /** The state the game made of an entity it draws itself: its outline, when asked for. */
    public static void extracted(Entity entity, EntityRenderState state) {
        if (!(entity instanceof LivingEntity) || entity instanceof LocalPlayer) return;
        int outline = DebugMarks.outline(FarConfig.get(), Mark.GAME);
        if (outline == 0) return;
        state.outlineColor = outline;
        glowing = true;
    }
    //?}

    /** Whether a figure added this frame wants the glowing outline. */
    private static boolean glowing;

    public static boolean glowing() {
        return glowing;
    }

    /**
     * The state of an entity as its renderer sees it this frame; the impostors are baked from
     * it too. Null when its renderer cannot give one: that entity is not drawn far away.
     */
    public static @Nullable EntityRenderState extract(EntityRenderDispatcher dispatcher, Entity entity, float partialTick) {
        return extract(dispatcher, entity, partialTick,
                light(Minecraft.getInstance().level, entity.getX(), entity.getY(), entity.getZ()));
    }

    /**
     * The state of a figure, extracted with {@code light} (packed): the renderer reads the light
     * as it extracts, and some keep that copy whatever the state says later
     * ({@code EntityRendererLightMixin}).
     */
    public static @Nullable EntityRenderState extract(EntityRenderDispatcher dispatcher, Entity entity, float partialTick,
                                                      int light) {
        if (!drawable(dispatcher, entity)) return null;
        Puppets.skin(entity);
        Puppets.cape(entity);
        int before = extractLight;
        extractLight = light;
        try {
            //? if >=1.21.9 {
            return dispatcher.extractEntity(entity, partialTick);
            //?} elif >=1.21.2 {
            /*return state(renderer(dispatcher, entity), entity, partialTick);
            *///?} else {
            /*return EntityRenderState.of(entity, partialTick);
            *///?}
        } catch (RuntimeException | LinkageError error) {
            // Another mod's renderer, called for an entity in no world: never worth a crash.
            leaveOut(entity.getType(), error);
            return null;
        } finally {
            extractLight = before;
        }
    }

    /** The light of the figure being extracted, packed; -1 outside {@link #extract}. On the render thread. */
    private static int extractLight = -1;

    /** The sky light the figure being extracted is lit with, -1 when none is extracted. */
    public static int extractedSky() {
        return extractLight < 0 ? -1 : extractLight >> 20 & 15;
    }

    /** The block light the figure being extracted is lit with, -1 when none is extracted. */
    public static int extractedBlock() {
        return extractLight < 0 ? -1 : extractLight >> 4 & 15;
    }

    /** Entity types whose renderer gave no state, or failed: never drawn far away again. */
    private static final Set<EntityType<?>> LEFT_OUT = Collections.newSetFromMap(new IdentityHashMap<>());
    //? if >=1.21.2 && <1.21.9
    /*private static final Set<EntityType<?>> PROBED = Collections.newSetFromMap(new IdentityHashMap<>());*/

    /**
     * Whether this entity's renderer can give the mod a state of its own. Before 1.21.9,
     * GeckoLib 4's gives none: it only keeps the one it reuses, for the entity it was last
     * handed, and draws that entity whatever state it is given. Never an entity Valkyrien
     * Skies carries on a ship: its renderer places it there itself.
     */
    public static boolean drawable(EntityRenderDispatcher dispatcher, Entity entity) {
        EntityType<?> type = entity.getType();
        if (LEFT_OUT.contains(type) || ValkyrienSkies.onShip(entity)) return false;
        //? if >=1.21.2 && <1.21.9 {
        /*if (PROBED.add(type) && dispatcher.getRenderer(entity).createRenderState() == null) {
            leaveOut(type, null);
            return false;
        }
        *///?}
        return true;
    }

    private static void leaveOut(EntityType<?> type, @Nullable Throwable error) {
        if (!LEFT_OUT.add(type)) return;
        String id = EntityType.getKey(type).toString();
        if (error == null) {
            LivingHorizonClient.LOGGER.warn("{} is not drawn far away: its renderer gives no render state", id);
        } else {
            LivingHorizonClient.LOGGER.warn("{} is not drawn far away: its renderer failed", id, error);
        }
    }

    //? if >=1.21.2 && <1.21.9 {
    /*@SuppressWarnings("unchecked")
    private static <E extends Entity> net.minecraft.client.renderer.entity.EntityRenderer<? super E, EntityRenderState> renderer(
            EntityRenderDispatcher dispatcher, E entity) {
        return (net.minecraft.client.renderer.entity.EntityRenderer<? super E, EntityRenderState>) dispatcher.getRenderer(entity);
    }

    /^* A state of its own: the renderer's createRenderState(entity, tick) hands back one it reuses. ^/
    private static <E extends Entity, S extends EntityRenderState> EntityRenderState state(
            net.minecraft.client.renderer.entity.EntityRenderer<? super E, S> renderer, E entity, float partialTick) {
        S state = renderer.createRenderState();
        renderer.extractRenderState(entity, state, partialTick);
        RENDERERS.put(state, renderer);
        return state;
    }

    /^*
     * Which renderer made each state, the one that draws it: the game can only draw an
     * entity, not a state, before 1.21.9.
     ^/
    private static final Map<EntityRenderState, net.minecraft.client.renderer.entity.EntityRenderer<?, ?>> RENDERERS =
            new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState> void drawState(S state, double x, double y, double z, PoseStack pose,
                                                              MultiBufferSource buffers, int light) {
        var renderer = (net.minecraft.client.renderer.entity.EntityRenderer<?, S>) RENDERERS.get(state);
        if (renderer == null) return;
        Vec3 offset = renderer.getRenderOffset(state);
        pose.pushPose();
        pose.translate(x + offset.x, y + offset.y, z + offset.z);
        renderer.render(state, pose, buffers, light);
        pose.popPose();
    }
    *///?} elif <1.21.2 {
    /*/^*
     * Draws a figure through its entity's renderer, the only way before 1.21.2, with what
     * the mod changed in its state given to the entity for that time.
     ^/
    @SuppressWarnings("unchecked")
    public static void drawState(EntityRenderState state, double x, double y, double z, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        Entity entity = state.entity;
        var renderer = (net.minecraft.client.renderer.entity.EntityRenderer<Entity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        float tick = state.partialTick;
        Vec3 offset = renderer.getRenderOffset(entity, tick);
        pose.pushPose();
        pose.translate(x + offset.x, y + offset.y, z + offset.z);
        state.apply();
        try {
            renderer.render(entity, Mth.lerp(tick, entity.yRotO, entity.getYRot()), tick, pose, buffers, light);
        } finally {
            state.restore();
            pose.popPose();
        }
    }
    *///?}

    //? if <1.21.9 {
    /*/^* The light each figure is drawn in, which the states do not carry before 1.21.9. ^/
    private static final Map<EntityRenderState, Integer> LIGHTS = new IdentityHashMap<>();
    /^* This frame's figures, drawn by {@link #draw} rather than by the game. ^/
    private static final List<EntityRenderState> CLASSIC = new ArrayList<>();

    /^* The figures of this frame, gathered once the game has chosen the entities it draws. ^/
    public static void extract(Camera camera, float partialTick) {
        extract(camera, partialTick, CLASSIC);
    }

    /^*
     * Draws this frame's figures into the world's buffers, right after the game's own
     * entities: moved and scaled as {@link #transform} says, in the light worked out for them.
     ^/
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        for (EntityRenderState state : CLASSIC) {
            double x = state.x - camera.x, y = state.y - camera.y, z = state.z - camera.z;
            int light = LIGHTS.getOrDefault(state, LightTexture.pack(0, 15));
            Transform t = TRANSFORMS.get(state);
            PolygonStats.enter(state);
            try {
                if (t == null) {
                    drawState(state, x, y, z, pose, buffers, light);
                    continue;
                }
                pose.pushPose();
                pose.translate(
                        (float) (t.anchorX() * t.pull() + (x - t.anchorX()) * t.scale()),
                        (float) (t.anchorY() * t.pull() + (y - t.anchorY()) * t.scale()),
                        (float) (t.anchorZ() * t.pull() + (z - t.anchorZ()) * t.scale()));
                pose.scale((float) t.scale(), (float) t.scale(), (float) t.scale());
                drawState(state, 0.0, 0.0, 0.0, pose, buffers, light);
                pose.popPose();
            } finally {
                PolygonStats.leave();
            }
        }
    }
    *///?}

    public static @Nullable Transform transform(EntityRenderState state) {
        return TRANSFORMS.isEmpty() ? null : TRANSFORMS.get(state);
    }

    public static void extract(Camera camera, float partialTick, List<EntityRenderState> frame) {
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 eye = camera.position();
        view(camera, minecraft);
        prewarm(config);
        long started = System.nanoTime();
        drawn = 0;
        skipped = 0;

        for (FarPlayer player : config.enabled ? FarPlayerTracker.get().players() : List.<FarPlayer>of()) {
            AbstractClientPlayer live = player.live();
            if (live != null) {
                // Sent by the server, yet not drawn: outside the sections the client
                // draws, when its render distance is shorter than the server's.
                if (EXTRACTED.contains(live) || live.isInvisible()) continue;
                Entity mount = live.getRootVehicle();
                EntityCulling.drawn(live);
                if (debug) DebugMarks.mark(live, Mark.PLAYER);
                if (config.impostorPlayers && (mount == live ? impostor(minecraft, eye, live, partialTick, config)
                        : !EXTRACTED.contains(mount) && impostorRiding(minecraft, eye, live, mount, partialTick, config))) {
                    continue;
                }
                EntityRenderState body = extract(dispatcher, live, partialTick);
                if (mount == live) turned(live, body, partialTick);
                EntityRenderState mountState = mount != live && !EXTRACTED.contains(mount)
                        ? extract(dispatcher, mount, partialTick) : null;
                add(frame, minecraft, eye, body, mountState, false, Mark.PLAYER, config, live.getUUID());
            } else if (player.showsPuppet(config)) {
                Entity puppet = player.puppet();
                if (tooFar(puppet, eye, config.playerMaxDistance)) continue;
                if (skipped(puppet, false)) continue;
                if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
                Entity mount = config.showVehicles ? player.mountPuppet() : null;
                if (config.impostorPlayers && (mount == null ? impostor(minecraft, eye, puppet, partialTick, config)
                        : impostorRiding(minecraft, eye, puppet, mount, partialTick, config))) {
                    continue;
                }
                EntityRenderState body = extract(dispatcher, puppet, partialTick);
                if (mount == null) turned(puppet, body, partialTick);
                EntityRenderState mountState = mount == null ? null : extract(dispatcher, mount, partialTick);
                if (mountState != null) Posture.seat(body);
                add(frame, minecraft, eye, body, mountState, true, Mark.PLAYER, config, player.id());
            }
        }

        if (config.enabled) rest(frame, minecraft, dispatcher, eye, partialTick, config);
        mobs(frame, minecraft, dispatcher, eye, partialTick, config);
        unseen(frame, minecraft, dispatcher, eye, partialTick, config);
        if (config.easterEggs) eggs(frame, minecraft, dispatcher, eye, partialTick, config);
        if (config.skyBirds) {
            // Parrots and bats; the silhouettes are drawn by AmbientRenderer.
            for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                if (flyer.puppet == null) continue;
                if (skip(flyer.puppet, true) != null) continue;
                add(frame, minecraft, eye, extract(dispatcher, flyer.puppet, partialTick), null, true, Mark.FAKE, config, null,
                        (flyer.kind == Ambience.Kind.PARROT ? flyer.span : 1.0) * flyer.scale);
            }
        }
        Stats.extract(System.nanoTime() - started, drawn, skipped);
        Profile.record(Profile.Section.EXTRACT, System.nanoTime() - started);
        Profile.frame();
        if (debug) DebugMarks.end();
    }

    /** The figures of the easter eggs that are the game's own: the cow taken, the watcher, those from 9 on. */
    private static void eggs(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        Ambience ambience = FarPlayerTracker.get().ambience();
        LivingEntity cow = ambience.ufo().cow();
        if (cow != null) add(frame, minecraft, eye, extract(dispatcher, cow, partialTick), null, true, Mark.FAKE, config, null);
        LivingEntity watcher = ambience.herobrine().figure();
        if (watcher != null) add(frame, minecraft, eye, extract(dispatcher, watcher, partialTick), null, true, Mark.FAKE, config, null);
        for (fr.clixmods.livinghorizon.ambient.Egg egg : ambience.more()) {
            for (Entity figure : egg.figures()) {
                add(frame, minecraft, eye, extract(dispatcher, figure, partialTick), null, true, Mark.FAKE, config, null);
            }
        }
    }

    /**
     * Pictured from several heights, not only level with it: a phantom or a ghast, Ice and Fire's
     * dragons, and a boat or a vehicle, which an aircraft is, flying overhead or seen from a cliff.
     */
    private static boolean fromHeights(Entity entity) {
        return MobFlight.pictured(entity) || !(entity instanceof LivingEntity) || IceAndFire.flies(entity);
    }

    /** Past the distance set in the options; 0 is no limit. */
    private static boolean tooFar(Entity entity, Vec3 eye, int limit) {
        return limit > 0 && entity.distanceToSqr(eye) > (double) limit * limit;
    }

    /**
     * A mob, as the list of mobs files it: alive, mannequins excepted. Boats, vehicles and
     * mannequins are the other entities, with a distance and a switch for pictures of their own.
     */
    public static boolean creature(Entity entity) {
        return MobKinds.creature(entity.getType());
    }

    /** How far a remembered or sent figure of the mobs' list is drawn: the mobs' distance, or the other entities'. */
    private static int maxDistance(Entity entity, FarConfig config) {
        return creature(entity) ? config.mobMaxDistance : config.entityMaxDistance;
    }

    /** Mobs met on the way, where they were, playing their animation. */
    private static void mobs(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs && !config.distantEntities) return;
        MobMemory memory = FarPlayerTracker.get().mobs();
        List<MobMemory.Remembered> shown = memory.shown();
        for (MobMemory.Remembered mob : shown) {
            Entity puppet = mob.puppet();
            if (puppet == null) {
                if (debug && mob.waiting()) markRemembered(mob, Mark.WAITING);
                continue;
            }
            // Where the spyglass looks, as far as it looks.
            if (!config.shows(creature(puppet)) || tooFar(puppet, eye, maxDistance(puppet, config))
                    && !memory.foresight().inScope(puppet.getX(), puppet.getY() + puppet.getBbHeight() / 2, puppet.getZ())) continue;
            // A copy walking up to its real mob stands for it, however close.
            if (!memory.handingOver(mob) && tooClose(puppet, eye, memory.nearRange(mob))) {
                if (debug) DebugMarks.mark(puppet, Mark.NEAR);
                continue;
            }
            if (overVoid(minecraft, eye, mob.id(), puppet, config)) continue;
            if (skipped(puppet, true)) continue;
            if (occluded(mob.id(), puppet, config)) continue;
            Mark copy = SeedDraws.foreseen(mob.id()) ? Mark.FORESEEN : Mark.FAKE;
            if (debug) DebugMarks.mark(puppet, copy);
            mob.drawn();
            // Held by easter egg 15, its head turned apart from its body: a model, which shows it.
            if (!Herd.holds(puppet) && impostor(minecraft, eye, puppet, partialTick, config)) continue;
            EntityRenderState body = extract(dispatcher, puppet, partialTick);
            turned(puppet, body, partialTick);
            // A puppet is in no world, so never in water: fish would be drawn flopping on
            // their side, as on land. Water mobs were in water when last seen.
            if (body instanceof LivingEntityRenderState living && livesInWater(puppet)) living.isInWater = true;
            add(frame, minecraft, eye, body, null, true, copy, config, mob.id());
        }
        DrawnGround.ask();
        if (debug && DebugMarks.drawing() && minecraft.level != null) {
            // The ones remembered but past the most shown at once.
            Set<MobMemory.Remembered> listed = Collections.newSetFromMap(new IdentityHashMap<>());
            listed.addAll(shown);
            for (MobMemory.Remembered mob : memory.remembered(minecraft.level.dimension().identifier().toString())) {
                if (!listed.contains(mob)) markRemembered(mob, Mark.SPARE);
            }
        }
    }

    /**
     * Behind terrain, so not drawn this frame ({@link FarConfig#hideOccludedMobs}). A hidden
     * mob still counts for the far plane: past it, the depth there is the sky's, and the mob
     * could never be seen again to be drawn again.
     */
    private static boolean occluded(Object key, Entity entity, FarConfig config) {
        if (!config.hideOccludedMobs) return false;
        double dx = entity.getX() - eyeX, dy = entity.getY() - eyeY, dz = entity.getZ() - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Drawn bigger than it is with a minimum size: the bigger box, as drawn, is what must be hidden.
        double boost = Apparent.boost(Apparent.extent(entity.getBbHeight(), entity.getBbWidth()), distance,
                pixelsPerRadian, config.minApparentPixels);
        if (!Occlusion.hidden(key, entity, dx, dy, dz, boost)) {
            return false;
        }
        // Its box is where the far terrain's depth is needed (Occlusion.hidden): only the far plane here.
        farthest = Math.max(farthest, distance);
        skipped++;
        if (debug) DebugMarks.mark(entity, Mark.HIDDEN);
        return true;
    }

    /**
     * No terrain drawn under it, neither the game's nor the far terrain, so not drawn this frame
     * ({@link FarConfig#onlyOverTerrain}): it would stand in the void.
     */
    private static boolean overVoid(Minecraft minecraft, Vec3 eye, Object key, Entity entity, FarConfig config) {
        if (!config.onlyOverTerrain || minecraft.level == null
                || DrawnGround.shown(minecraft.level, eye, key, entity.getX(), entity.getZ())) {
            return false;
        }
        if (debug) DebugMarks.mark(entity, Mark.VOID);
        return true;
    }

    /**
     * Within where the real mob would be sent, a copy is not where the mob is, and could be
     * walked up to: it is not drawn. See {@link MobMemory#nearRange}.
     */
    private static boolean tooClose(Entity puppet, Vec3 eye, double range) {
        double dx = puppet.getX() - eye.x, dz = puppet.getZ() - eye.z;
        return dx * dx + dz * dz < range * range;
    }

    private static void markRemembered(MobMemory.Remembered mob, Mark mark) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(mob.type())).orElse(null);
        if (type == null) return;
        DebugMarks.mark(mob.x(), mob.y(), mob.z(), type.getWidth(), type.getHeight(), type, mark);
    }

    /**
     * Mobs the server sends, of the kinds shown far away, that the game leaves out of the
     * frame: past its entity distance (the "Entity Distance" slider), or in a part of the
     * world it does not draw. Their copy only takes over once the server stops sending them;
     * until then they are drawn here, so that a mob never blinks out between the two.
     */
    private static void unseen(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                               Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs && !config.distantEntities) return;
        // Counted for the panel always; boxed and labelled only when the game's mobs are asked for.
        boolean game = debug && DebugMarks.gameMobs();
        Map<EntityType<?>, Boolean> wanted = WANTED;
        wanted.clear();
        MobMemory memory = FarPlayerTracker.get().mobs();
        for (Entity entity : memory.live()) {
            if (EXTRACTED.contains(entity)) {
                if (game) DebugMarks.mark(entity, Mark.GAME);
                else if (debug) DebugMarks.tally(Mark.GAME);
                continue;
            }
            if (entity.isRemoved() || entity.isInvisible()) continue;
            // A mount with a player on it is drawn with that player; a villager in a minecart,
            // a cow in a boat, each on its own.
            if ((entity.isPassenger() || entity.isVehicle()) && carriesPlayer(entity.getRootVehicle())) continue;
            // Its copy is still walking up to it, and drawn in its place.
            if (memory.hides(entity)) continue;
            EntityType<?> type = entity.getType();
            if (!config.shows(MobKinds.creature(type))) continue;
            // Null once known: hidden whatever its name (FarConfig.hiddenMobs).
            Boolean shown = wanted.get(type);
            if (shown == null && !wanted.containsKey(type)) {
                String id = EntityType.getKey(type).toString();
                shown = config.hidesMob(id) ? null : config.mobTypes.contains(id);
                wanted.put(type, shown);
            }
            if (shown == null || !shown && !(config.rememberNamedMobs && entity.hasCustomName())) continue;
            // Close by, the game leaves a mob out only when the terrain hides it.
            if (entity.distanceToSqr(eye) < 24 * 24) continue;
            if (tooFar(entity, eye, maxDistance(entity, config))) continue;
            // Far View draws it as a box of its own in place of the game's model: once is enough.
            if (FarViewLink.drawsMob(entity, eye)) continue;
            if (overVoid(minecraft, eye, entity.getUUID(), entity, config)) continue;
            if (skipped(entity, true)) continue;
            if (occluded(entity.getUUID(), entity, config)) continue;
            EntityCulling.drawn(entity);
            if (debug) DebugMarks.mark(entity, Mark.LIVE);
            if (impostor(minecraft, eye, entity, partialTick, config)) continue;
            EntityRenderState body = extract(dispatcher, entity, partialTick);
            turned(entity, body, partialTick);
            add(frame, minecraft, eye, body, null, false, Mark.LIVE, config, entity.getUUID());
        }
        DrawnGround.ask();
    }

    /** Whether each kind of mob met this frame is one shown far away. */
    private static final Map<EntityType<?>, Boolean> WANTED = new IdentityHashMap<>();

    public static boolean livesInWater(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
    }

    /**
     * Players who logged off: standing, walking or on guard as the game would draw any
     * player, or seated on the ground or asleep in a bed.
     */
    private static void rest(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        if (!config.offlinePlayers) return;
        ClientLevel level = minecraft.level;
        if (level == null) return;
        RestingPlayers resting = FarPlayerTracker.get().resting();
        String dimension = SharedPositions.dimensionId(level.dimension());
        // Made a few a frame, each dressed and given its mount: joining a server where many
        // logged off would make them all in one frame. The nearest in view first.
        long makeUntil = System.nanoTime() + MAKE_BUDGET;
        boolean made = false;
        // The nearest only, as many as the settings say.
        for (RestingPlayers.Spot spot : resting.shown()) {
            if (!spot.dimension().equals(dimension)) continue;
            RestingPuppet puppet = resting.made(spot);
            if (puppet == null) {
                if (!inView(spot.x(), spot.y(), spot.z(), 2) || (made && System.nanoTime() > makeUntil)) continue;
                puppet = resting.puppet(level, spot);
                made = true;
            }
            if (!drawable(dispatcher, puppet)) continue;
            if (tooFar(puppet, eye, config.playerMaxDistance)) continue;
            if (skipped(puppet, true)) continue;
            RestingPuppet.Look look = puppet.look();
            // On what they rode when they logged off: the mount drawn under them, they seated on it.
            Entity mount = look == RestingPuppet.Look.RIDE ? puppet.mount() : null;
            // Behind terrain, as mobs are. Not on a mount: the rider's box leaves out a tall
            // mount under it (a happy ghast), which may show while the rider is hidden.
            if (mount == null && occluded(spot.name(), puppet, config)) continue;
            if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
            // Far away a picture, as for players online, in whatever posture they are: drawn
            // from the puppet and its look, so nothing is extracted for it.
            if (config.impostorPlayers && restingImpostor(minecraft, eye, puppet, mount, look, partialTick, config)) continue;
            EntityRenderState body = extract(dispatcher, puppet, partialTick);
            if (body == null) continue;
            // In a bed, laid along it.
            if (look != RestingPuppet.Look.BED) turned(puppet, body, partialTick);
            EntityRenderState mountState = mount == null ? null : extract(dispatcher, mount, partialTick);
            if (mountState != null) Posture.seat(body);
            if (look != RestingPuppet.Look.STAND && look != RestingPuppet.Look.RIDE
                    && body instanceof LivingEntityRenderState living) {
                living.yRot = 0;
                living.xRot = 0;
                living.walkAnimationSpeed = 0;
            }
            if (look == RestingPuppet.Look.BED) {
                Posture.lie(body, true);
            } else if (look == RestingPuppet.Look.SIT && Posture.seat(body)) {
                // The riding pose, without a mount: legs out in front, on the ground.
                body.y -= Posture.SEAT_DROP;
            }
            // Their name, marked offline, where the game would show it: close by, as for anyone.
            Component name = config.offlineNames ? body.nameTag : null;
            //? if >=1.21.2 {
            if (name != null && look == RestingPuppet.Look.BED && body.nameTagAttachment != null) {
                // Lying down: just above the body, as the game puts a sleeper's name (the tag adds half a block).
                body.nameTagAttachment = new Vec3(0, 0.3, 0);
            }
            //?}
            add(frame, minecraft, eye, body, mountState, true, Mark.PLAYER, config, spot.name());
            body.nameTag = name;
            // Fishing: its bobber on the water, the line drawn from the rod by the game.
            Entity hook = puppet.hook();
            if (hook != null) add(frame, minecraft, eye, extract(dispatcher, hook, partialTick), null, true, Mark.PLAYER, config, null);
        }
        // Then those out of view, with what is left: there when the camera turns to them.
        for (RestingPlayers.Spot spot : resting.shown()) {
            if (made && System.nanoTime() > makeUntil) break;
            if (!spot.dimension().equals(dimension) || resting.made(spot) != null) continue;
            resting.puppet(level, spot);
            made = true;
        }
    }

    private static void add(List<EntityRenderState> frame, Minecraft minecraft, Vec3 eye, @Nullable EntityRenderState body,
                            @Nullable EntityRenderState mount, boolean puppet, Mark kind, FarConfig config,
                            @Nullable Object who) {
        add(frame, minecraft, eye, body, mount, puppet, kind, config, who, 1.0);
    }

    /**
     * @param kind what it is, for its outline: {@link Mark#PLAYER}, {@link Mark#FAKE} or {@link Mark#LIVE}
     * @param size drawn this many times bigger, around the feet
     */
    private static void add(List<EntityRenderState> frame, Minecraft minecraft, Vec3 eye, @Nullable EntityRenderState body,
                            @Nullable EntityRenderState mount, boolean puppet, Mark kind, FarConfig config,
                            @Nullable Object who, double size) {
        if (body == null) return;
        double ax = body.x - eye.x, ay = body.y - eye.y, az = body.z - eye.z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        if (distance < 1e-3) return;
        if (distance > drawnRange) FarDepth.needed();

        // Drawn where they really are, so that terrain in front hides them through the depth
        // buffer - Voxy's included, see FarDepth - unless that is past the far plane; then
        // closer and smaller in proportion, which looks the same but shows through whatever
        // stands between.
        farthest = Math.max(farthest, distance);
        drawn++;
        double pull = Apparent.pull(distance, DepthFar.of(minecraft));
        double extent = Apparent.extent(body.boundingBoxHeight, body.boundingBoxWidth) * size;
        double boost = Apparent.boost(extent, distance, pixelsPerRadian, config.minApparentPixels);

        Transform transform = nextTransform();
        transform.anchorX = ax;
        transform.anchorY = ay;
        transform.anchorZ = az;
        transform.pull = pull;
        transform.scale = pull * boost * size;
        // Where it shows on screen: as big as drawn, around its middle, a mount included. Nearer
        // than the render distance too, for Distant Horizons' fade, which reaches inside it.
        double shown = extent * boost, radius = shown * (mount == null ? 0.75 : 1.5);
        if (distance > drawnRange) FarDepth.figure(ax, ay + shown * 0.5, az, radius);
        else FarDepth.around(ax, ay + shown * 0.5, az, radius);
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (mount != null) place(frame, mount, transform, puppet, kind, config, self, level);
        place(frame, body, transform, puppet, kind, config, self, level);
    }

    /** One state of a figure, its body or its mount, into the frame, moved as {@code transform} says. */
    private static void place(List<EntityRenderState> frame, EntityRenderState state, Transform transform, boolean puppet,
                              Mark kind, FarConfig config, @Nullable LocalPlayer self, @Nullable ClientLevel level) {
        TRANSFORMS.put(state, transform);
        // A boat hides the water inside its hull with a patch that the water then fails to
        // draw over. Drawn bigger or nearer than it is, that patch hides the water around
        // it; under a shader pack (Photon), drawn with our figures, it turns the game's
        // water clear far around. Taken for under water, a boat has none.
        if (state instanceof BoatRenderState boat) boat.isUnderWater = true;
        state.nameTag = null;
        //? if >=1.21.9 {
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        if (puppet) state.lightCoords = light(level, state);
        else if (!lit(level, AT.set(state.x, state.y, state.z))) state.lightCoords = OPEN_SKY;
        int outline = self == null ? 0 : DebugMarks.outline(config, kind);
        if (outline != 0) {
            state.outlineColor = outline;
            glowing = true;
        }
        //?} else {
        /*// Before 1.21.9 the light is handed over when the state is drawn, and outlines
        // need the entity: neither is in the state.
        LIGHTS.put(state, light(level, state));
        *///?}
        frame.add(state);
    }

    private static final int OPEN_SKY = LightTexture.pack(0, 15);

    /**
     * The light a puppet stands in. Where the client has no light, open sky, which the
     * lightmap still darkens at night. Where it has, the brightest of its feet, the block
     * above and the one above that: a puppet stands where its mob was last seen, which may
     * be a little inside a block, or in a pocket the light engine has not reached yet - read
     * there alone, it would be drawn black.
     */
    private static int light(@Nullable ClientLevel level, EntityRenderState state) {
        return light(level, state.x, state.y, state.z);
    }

    /**
     * The light read at each block this half second, by the block's position: hundreds of
     * figures ask every frame, six light lookups each, for a value that changes slowly. Where
     * the client has no light yet, nothing is kept: it comes a few frames later.
     */
    private static final Long2IntOpenHashMap LIGHT = new Long2IntOpenHashMap();
    private static final long LIGHT_KEPT_NANOS = 500_000_000L;
    private static @Nullable ClientLevel lightLevel;
    private static long lightSince;

    static {
        LIGHT.defaultReturnValue(-1);
    }

    private static int light(@Nullable ClientLevel level, double x, double y, double z) {
        long now = System.nanoTime();
        if (level != lightLevel || now - lightSince > LIGHT_KEPT_NANOS) {
            LIGHT.clear();
            lightLevel = level;
            lightSince = now;
        }
        BlockPos.MutableBlockPos at = AT.set(x, y + 0.1, z);
        long key = at.asLong();
        int kept = LIGHT.get(key);
        if (kept >= 0) return kept;
        if (!lit(level, at)) return OPEN_SKY;
        int block = 0, sky = 0;
        for (int up = 0; up < 3; up++) {
            if (up > 0) at.move(Direction.UP);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
        }
        int packed = LightTexture.pack(block, sky);
        LIGHT.put(key, packed);
        return packed;
    }

    /** The block being looked at for light: one at a time, on the render thread. */
    private static final BlockPos.MutableBlockPos AT = new BlockPos.MutableBlockPos();

    /**
     * Whether the client has the light of this spot. A chunk that arrives gets its light only
     * a few frames later, through a queue the game empties a little each frame - longer when
     * flying, as chunks come in fast. Until then every block of it reads black.
     */
    private static boolean lit(@Nullable ClientLevel level, BlockPos at) {
        if (level == null || !level.hasChunkAt(at)) return false;
        //? if >=1.21.3 {
        return level.getLightEngine().lightOnInColumn(
                SectionPos.getZeroNode(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ())));
        //?} else {
        /*return level.getLightEngine().lightOnInSection(SectionPos.of(at));
        *///?}
    }

    // --- Impostors ------------------------------------------------------------------------

    private static long lastPrewarm;

    /**
     * Once a second, asks for the pictures of every figure on hand to be baked, near or far,
     * so that they are ready when the figure walks past the impostor distance: every skin,
     * every kind of mob, in the resource packs as they are.
     */
    private static void prewarm(FarConfig config) {
        if (!ImpostorAtlas.usable()) return;
        long now = System.currentTimeMillis();
        if (now - lastPrewarm < 1000) return;
        lastPrewarm = now;
        for (FarPlayer player : config.impostorPlayers ? FarPlayerTracker.get().players() : List.<FarPlayer>of()) {
            RemotePlayer puppet = player.puppet();
            // Baked once they land: gliding, the picture would show them lying in the air.
            if (puppet != null && !puppet.isFallFlying()) ImpostorAtlas.request(ImpostorKey.of(puppet), puppet);
        }
        if (!config.distantMobs && !config.distantEntities) return;
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            Entity puppet = mob.puppet();
            if (puppet == null || !config.shows(creature(puppet))
                    || !(creature(puppet) ? config.impostors : config.impostorEntities)) continue;
            ImpostorKey key = ImpostorKey.of(puppet);
            if (!fromHeights(puppet)) {
                ImpostorAtlas.request(key, puppet);
                continue;
            }
            // A phantom, a ghast, a dragon, an aircraft: seen from every height, as it flies about.
            for (int height = 0; height < ImpostorViews.HEIGHTS.length; height++) {
                ImpostorAtlas.request(ImpostorKey.seenFrom(key, height), puppet);
            }
        }
    }

    /**
     * What a figure showed last as an impostor, so that neither its sheet nor its picture
     * flickers. Written over in place each frame the figure is a picture.
     */
    private static final class Shown {
        ImpostorKey key;
        int view;
        long frame;
        boolean ridden;
    }

    private static final Map<Entity, Shown> SHOWN = new IdentityHashMap<>();
    private static long frame;

    /**
     * A model nearing its picture, turned this frame part of the way ({@code share}) to how the
     * picture will show it ({@code yaw}, picture {@code view}): no jump as the picture takes over.
     */
    private static final class Turn {
        int view;
        float yaw;
        float share;
        long frame;
    }

    private static final Map<Entity, Turn> TURNS = new IdentityHashMap<>();

    /**
     * Draws a flat picture of a figure in place of its model, when it is past the impostor
     * distance or smaller on screen than the impostor size ({@link ImpostorThreshold}), and its
     * pictures are baked (they are asked for otherwise, and the model is drawn meanwhile).
     * Nothing is extracted: that is what is saved.
     *
     * <p>Once a figure is an impostor it stays one until it is a little inside both the
     * distance and the size, it keeps its picture until it has clearly turned to the next, and
     * when its look changes (a sheep sheared, a player's armour) it keeps its old picture until the
     * new one is baked: none of it blinks between a model and a picture.
     */
    private static boolean impostor(Minecraft minecraft, Vec3 eye, Entity entity, float partialTick, FarConfig config) {
        return impostor(minecraft, eye, entity, partialTick, config, 0, 0, 0, Float.NaN);
    }

    /**
     * @param dx     blocks the picture is moved from the figure's feet, with {@code dy} and {@code dz}:
     *               a seated figure lowered to the ground, one in a bed laid along it
     * @param facing the way the figure's body is taken to face, NaN for its own: along its bed
     */
    private static boolean impostor(Minecraft minecraft, Vec3 eye, Entity entity, float partialTick, FarConfig config,
                                    double dx, double dy, double dz, float facing) {
        // A glider is tilted along its flight, which no picture baked standing shows.
        if (!ImpostorAtlas.usable() || entity.isPassenger() || entity.isVehicle()
                || entity instanceof LivingEntity glider && glider.isFallFlying()
                || !drawable(minecraft.getEntityRenderDispatcher(), entity)) {
            return false;
        }
        // Players are asked for under their own switch, before; mobs and the rest under theirs.
        if (!(entity instanceof Player) && !(creature(entity) ? config.impostors : config.impostorEntities)) return false;
        ImpostorKey key = ImpostorKey.of(entity);
        // A mob's body turns on its own; a boat or a vehicle is the way it faces.
        float yaw = !Float.isNaN(facing) ? facing : entity instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        boolean aloft = fromHeights(entity);
        if (aloft) {
            // A phantom, a ghast, a dragon, an aircraft, often seen from below: the picture baked from the nearest
            // height, round its middle, turned to the camera up and down as a bird's is.
            dy += ImpostorAtlas.middle(entity);
            key = seenFrom(key, entity, eye, partialTick, dx, dy, dz);
        }
        return picture(minecraft, eye, entity, entity, key, null, 0, partialTick, config,
                dx, dy, dz, yaw, Math.max(entity.getBbHeight(), entity.getBbWidth()), aloft);
    }

    /** The key of the pictures of {@code at} baked from the height nearest the camera's, round the point given. */
    private static ImpostorKey seenFrom(ImpostorKey key, Entity at, Vec3 eye, float partialTick,
                                        double dx, double dy, double dz) {
        double ax = Mth.lerp(partialTick, at.xOld, at.getX()) + dx - eye.x;
        double ay = Mth.lerp(partialTick, at.yOld, at.getY()) + dy - eye.y;
        double az = Mth.lerp(partialTick, at.zOld, at.getZ()) + dz - eye.z;
        double elevation = Math.toDegrees(Math.atan2(-ay, Math.sqrt(ax * ax + az * az)));
        return ImpostorKey.seenFrom(key, ImpostorViews.height(elevation));
    }

    /**
     * A rider and its mount or vehicle as one picture, baked together: a horse, a camel, a
     * happy ghast, a boat, a minecart, the rider seated where it sits, turned as it is
     * turned on it. False when either cannot be pictured (a renderer that gives nothing, a
     * vehicle carrying someone else too), and both are drawn as models.
     */
    private static boolean impostorRiding(Minecraft minecraft, Vec3 eye, Entity rider, Entity mount, float partialTick,
                                          FarConfig config) {
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        if (!ImpostorAtlas.usable() || !(rider instanceof LivingEntity person) || mount.getPassengers().size() > 1
                || !drawable(dispatcher, mount) || !drawable(dispatcher, rider)) {
            return false;
        }
        // A mob's body turns on its own, a vehicle is the way it faces.
        float facing = mount instanceof LivingEntity horse ? horse.yBodyRot : mount.getYRot();
        float facingO = mount instanceof LivingEntity horse ? horse.yBodyRotO : mount.yRotO;
        Vec3 seat = rider.position().subtract(mount.position()).yRot((float) Math.toRadians(facing));
        float turned = Mth.wrapDegrees(person.yBodyRot - facing);
        ImpostorKey key = ImpostorKey.riding(rider, mount, seat, turned);
        float yaw = Mth.rotLerp(partialTick, facingO, facing);
        double extent = Math.max(Math.max(mount.getBbHeight(), seat.y + rider.getBbHeight()), mount.getBbWidth());
        // On an aircraft, a boat or a happy ghast, the rider is pictured from the heights its mount is.
        boolean aloft = fromHeights(mount);
        double dy = 0;
        if (aloft) {
            dy = ImpostorAtlas.middle(mount, new ImpostorAtlas.Rider(rider, seat, turned));
            key = seenFrom(key, mount, eye, partialTick, 0, dy, 0);
        }
        return picture(minecraft, eye, rider, mount, key, seat, turned, partialTick, config, 0, dy, 0, yaw, extent, aloft);
    }

    /**
     * The picture of {@code key} where {@code at} stands, moved by ({@code dx}, {@code dy},
     * {@code dz}), its view chosen for a body facing {@code yaw}; asked for while it is not
     * baked. {@code shown} is the figure the picture stands for, which keeps its last picture
     * until the new one is baked. A rider ({@code shown}) is pictured on its mount ({@code at}),
     * sitting at {@code seat} on it, turned {@code turned} degrees from it. {@code facing}: a
     * picture baked from a height, round the figure's middle, turned to the camera up and down too.
     */
    private static boolean picture(Minecraft minecraft, Vec3 eye, Entity shown, Entity at, ImpostorKey key,
                                   @Nullable Vec3 seat, float turned, float partialTick, FarConfig config,
                                   double dx, double dy, double dz, float yaw, double size, boolean facing) {
        double x = Mth.lerp(partialTick, at.xOld, at.getX()) + dx;
        double y = Mth.lerp(partialTick, at.yOld, at.getY()) + dy;
        double z = Mth.lerp(partialTick, at.zOld, at.getZ()) + dz;
        double ax = x - eye.x, ay = y - eye.y, az = z - eye.z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        Shown last = SHOWN.get(shown);
        double apparent = distance < 1e-3 ? Double.MAX_VALUE : size / distance * pixelsPerRadian;
        if (!ImpostorThreshold.pictured(distance, apparent, last != null, config.impostorDistance, config.impostorPixels)) {
            if (last != null) SHOWN.remove(shown);
            if (seat == null && config.impostorTurn) turn(shown, key, last, yaw, ax, az, distance, apparent, size, config);
            return false;
        }
        // A rider is pictured with its mount, at the mount's place.
        boolean ridden = at != shown;
        ImpostorAtlas.Sheet sheet = ImpostorAtlas.sheet(key);
        if (sheet == null) {
            if (seat == null) ImpostorAtlas.request(key, at);
            else ImpostorAtlas.request(key, at, new ImpostorAtlas.Rider(shown, seat, turned));
            // The last picture stands in while this one is baked, or refused for want of room,
            // only if it shows the same: never a player on foot for one in a boat, nor the reverse.
            sheet = last == null || last.ridden != ridden ? null : ImpostorAtlas.sheet(last.key);
            if (sheet == null) return false;
            key = last.key;
        }
        // As large as its model would be drawn: a figure turning into its picture keeps its size.
        double boost = Apparent.boost(Apparent.extent(size), distance, pixelsPerRadian, config.minApparentPixels);
        // Far larger on screen than its picture holds (a huge mob just past the distance): its
        // model, rather than a picture blown up into blocks.
        if (ImpostorThreshold.enlarged(sheet.worldSize() * boost / distance * pixelsPerRadian, sheet.tile(), last != null)) {
            if (last != null) SHOWN.remove(shown);
            return false;
        }
        if (distance > drawnRange) FarDepth.needed();
        farthest = Math.max(farthest, distance);
        drawn++;
        double pull = Apparent.pull(distance, DepthFar.of(minecraft));
        if (facing) {
            // Turned to the camera round its middle, a picture shows what is nearer than that middle
            // (an aircraft's nose, the wheels under it) farther along the line of sight: seen from
            // above, under the ground it stands on, which hid it. Moved nearer by half its size,
            // and made as much smaller, it looks the same and stands in front of that ground.
            pull *= Math.max(0.1, 1 - sheet.worldSize() * boost * 0.5 / distance);
        }
        // Its first picture is the one its model had turned to.
        Turn turning = TURNS.remove(shown);
        int previous = last != null ? last.view : turning != null && frame - turning.frame <= 1 ? turning.view : -1;
        int view = ImpostorViews.view(yaw, -ax, -az, previous);
        if (last == null) SHOWN.put(shown, last = new Shown());
        last.key = key;
        last.view = view;
        last.frame = frame;
        last.ridden = ridden;
        int light = light(minecraft.level, x, y, z);
        int outline = minecraft.player == null ? 0 : DebugMarks.impostorOutline(config);
        if (outline != 0 && ImpostorRenderer.OUTLINES) glowing = true;
        ImpostorRenderer.add(ax * pull, ay * pull, az * pull, sheet.worldSize() * pull * boost, sheet, view, light,
                outline, facing);
        double extent = sheet.worldSize() * boost;
        // Round its feet, or round its middle for a picture seen from a height.
        double centre = facing ? ay : ay + extent * 0.5;
        if (distance > drawnRange) FarDepth.figure(ax, centre, az, extent * 0.75);
        else FarDepth.around(ax, centre, az, extent * 0.75);
        return true;
    }

    /**
     * Notes how far a model nearing its picture turns to it this frame ({@link #turned}), and
     * to which picture: the one it had turned to, or showed until now, unless it has clearly
     * turned to another. Only a mob or a player whose picture is baked and would be shown.
     */
    private static void turn(Entity shown, ImpostorKey key, @Nullable Shown last, float yaw, double ax, double az,
                             double distance, double apparent, double size, FarConfig config) {
        double share = ImpostorThreshold.approach(distance, apparent, config.impostorDistance, config.impostorPixels);
        ImpostorAtlas.Sheet sheet = share <= 0 || !(shown instanceof LivingEntity) ? null : ImpostorAtlas.sheet(key);
        // A figure too large for its picture stays a model: never turned in steps.
        if (sheet == null || ImpostorThreshold.enlarged(sheet.worldSize()
                * Apparent.boost(Apparent.extent(size), distance, pixelsPerRadian, config.minApparentPixels)
                / distance * pixelsPerRadian, sheet.tile(), false)) {
            TURNS.remove(shown);
            return;
        }
        Turn turn = TURNS.get(shown);
        int previous = last != null ? last.view : turn != null && frame - turn.frame <= 1 ? turn.view : -1;
        if (turn == null) TURNS.put(shown, turn = new Turn());
        turn.view = ImpostorViews.view(yaw, -ax, -az, previous);
        turn.yaw = ImpostorViews.yaw(turn.view, -ax, -az);
        turn.share = (float) share;
        turn.frame = frame;
    }

    /**
     * Turns a model nearing its picture the share of the way {@link #turn} noted: its body to the
     * way the picture shows it, its head straight ahead and level, as it is pictured.
     */
    private static void turned(Entity entity, @Nullable EntityRenderState state, float partialTick) {
        Turn turn = TURNS.get(entity);
        if (turn == null || turn.frame != frame || !(state instanceof LivingEntityRenderState living)
                || !(entity instanceof LivingEntity mob)) {
            return;
        }
        // Before 1.21.2 an angle the state leaves as NaN is the entity's.
        float body = Float.isNaN(living.bodyRot) ? Mth.rotLerp(partialTick, mob.yBodyRotO, mob.yBodyRot) : living.bodyRot;
        float head = Float.isNaN(living.yRot)
                ? Mth.wrapDegrees(Mth.rotLerp(partialTick, mob.yHeadRotO, mob.yHeadRot) - body) : living.yRot;
        float pitch = Float.isNaN(living.xRot) ? Mth.lerp(partialTick, mob.xRotO, mob.getXRot()) : living.xRot;
        float rest = 1 - turn.share;
        living.bodyRot = Mth.rotLerp(turn.share, body, turn.yaw);
        living.yRot = head * rest;
        living.xRot = pitch * rest;
    }

    /**
     * A player who logged off as a picture, in its posture: standing, seated on the ground,
     * in a bed (laid along the bed, as the game lays a sleeper), or on its mount.
     */
    private static boolean restingImpostor(Minecraft minecraft, Vec3 eye, RestingPuppet puppet, @Nullable Entity mount,
                                           RestingPuppet.Look look, float partialTick, FarConfig config) {
        return switch (look) {
            case STAND -> impostor(minecraft, eye, puppet, partialTick, config);
            case SIT -> impostor(minecraft, eye, puppet, partialTick, config, 0, -Posture.SEAT_DROP, 0, Float.NaN);
            case BED -> {
                Direction bed = puppet.getBedOrientation();
                if (bed == null) {
                    // Lying pictures are baked raised (Posture.BAKED_LIFT): lowered back here.
                    yield impostor(minecraft, eye, puppet, partialTick, config, 0, Posture.LIE_LIFT - Posture.BAKED_LIFT, 0,
                            Float.NaN);
                }
                // As the game lays one: its head on the pillow, its body along the bed.
                double back = puppet.getEyeHeight() - 0.1;
                yield impostor(minecraft, eye, puppet, partialTick, config, -bed.getStepX() * back,
                        -Posture.BAKED_LIFT, -bed.getStepZ() * back, sleeping(bed));
            }
            case RIDE -> mount != null && impostorRiding(minecraft, eye, puppet, mount, partialTick, config);
        };
    }

    /** The body rotation the game lays a sleeper at in a bed facing {@code bed}. */
    private static float sleeping(Direction bed) {
        return switch (bed) {
            case SOUTH -> 90f;
            case WEST -> 0f;
            case NORTH -> 270f;
            default -> 180f;
        };
    }

    /** Whether a figure was drawn as a picture in the last frames: for the game tests and the debug tools. */
    public static boolean pictured(Entity entity) {
        Shown shown = SHOWN.get(entity);
        return shown != null && frame - shown.frame <= 3;
    }

    /** Forgets the figures not drawn as impostors for a while: puppets come and go. */
    private static void forgetShown() {
        frame++;
        if (frame % 600 == 0) {
            SHOWN.values().removeIf(shown -> frame - shown.frame > 600);
            TURNS.values().removeIf(turn -> frame - turn.frame > 600);
        }
    }

    /** Whether an entity the server sends should be drawn whatever its distance. */
    public static boolean alwaysRender(Entity entity) {
        FarConfig config = FarConfig.get();
        return config.enabled && config.renderTrackedVehiclesFar && carriesOtherPlayer(entity, 0);
    }

    /**
     * Whether the game leaves another player it would draw itself to the mod, to be drawn as a
     * picture: a figure the game draws is never one, and the game draws players ten times as far
     * as other entities, far past the impostor distance and down to a few pixels. Their mount too,
     * when it carries that player alone. Decided on the last frame's view, by the same thresholds
     * as {@link #picture}; the mod then draws a picture, or the model while that is baked.
     */
    public static boolean pictureInstead(Entity entity, double camX, double camY, double camZ) {
        FarConfig config = FarConfig.get();
        if (!config.enabled || !config.impostorPlayers || pixelsPerRadian <= 0 || !ImpostorAtlas.usable()) return false;
        AbstractClientPlayer rider = null;
        if (entity instanceof AbstractClientPlayer player) {
            rider = player;
        } else if (!entity.isPassenger() && entity.getPassengers().size() == 1
                && entity.getFirstPassenger() instanceof AbstractClientPlayer carried) {
            rider = carried;
        }
        if (rider == null || rider instanceof LocalPlayer || rider.isInvisible() || rider.isSpectator()
                || rider.isFallFlying()) {
            return false;
        }
        FarPlayer far = FarPlayerTracker.get().player(rider.getUUID());
        if (far == null || far.live() != rider) return false;
        Entity mount = rider.getVehicle();
        double size = Math.max(rider.getBbHeight(), rider.getBbWidth());
        Entity at = rider;
        if (mount != null) {
            // Pictured with its mount, and only on one carrying it alone, on nothing else.
            if (mount.isPassenger() || mount.getPassengers().size() > 1) return false;
            size = Math.max(Math.max(mount.getBbHeight(), rider.getY() - mount.getY() + rider.getBbHeight()),
                    mount.getBbWidth());
            at = mount;
        }
        double dx = at.getX() - camX, dy = at.getY() - camY, dz = at.getZ() - camZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double apparent = distance < 1e-3 ? Double.MAX_VALUE : size / distance * pixelsPerRadian;
        return ImpostorThreshold.pictured(distance, apparent, SHOWN.containsKey(rider), config.impostorDistance,
                config.impostorPixels);
    }

    /** A player, or something carrying one, however deep. */
    private static boolean carriesPlayer(Entity root) {
        if (root instanceof AbstractClientPlayer) return true;
        for (Entity passenger : root.getIndirectPassengers()) {
            if (passenger instanceof AbstractClientPlayer) return true;
        }
        return false;
    }

    private static boolean carriesOtherPlayer(Entity entity, int depth) {
        if (depth > 4) return false;
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof AbstractClientPlayer && !(passenger instanceof LocalPlayer)) return true;
            if (carriesOtherPlayer(passenger, depth + 1)) return true;
        }
        return false;
    }
}
