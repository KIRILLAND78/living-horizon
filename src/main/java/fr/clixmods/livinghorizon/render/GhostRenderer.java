package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

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
    /** How one state is moved and scaled at submission. Positions relative to the camera. */
    public record Transform(double anchorX, double anchorY, double anchorZ, double pull, double scale) {
    }

    private static final Map<EntityRenderState, Transform> TRANSFORMS = new IdentityHashMap<>();
    private static final Set<Entity> EXTRACTED = Collections.newSetFromMap(new IdentityHashMap<>());

    private GhostRenderer() {
    }

    /** Blocks. Past this the depth buffer is too coarse to be worth extending to. */
    private static final float MAX_FAR = 65536f;

    /** The farthest distant player drawn last frame, which this frame's far plane covers. */
    private static double farthest;
    private static float neededFar;
    /** Something drawn this far away this frame: the next far plane covers it. */
    public static void noteDistance(double distance) {
        farthest = Math.max(farthest, distance);
        if (distance > drawnRange) FarDepth.needed();
    }

    // --- What is worth drawing ----------------------------------------------------------

    /** The view this frame: where the camera looks, how wide, how many pixels a radian is. */
    private static double eyeX, eyeY, eyeZ, lookX, lookY, lookZ, viewAngle, pixelsPerRadian, drawnRange;
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
        FarConfig config = FarConfig.get();
        double size = Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()));
        double dx = entity.getX() - eyeX, dy = entity.getY() + size * 0.5 - eyeY, dz = entity.getZ() - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < size * 2 + 4) return count(null);
        if (config.optViewCulling) {
            double angle = Math.acos(Mth.clamp((dx * lookX + dy * lookY + dz * lookZ) / distance, -1, 1));
            if (angle > viewAngle + Math.asin(Math.min(1, size / distance))) return count(Mark.OUTSIDE);
        }
        if (mayVanish && config.optTinyCulling && config.minApparentPixels <= 0
                && size / distance * pixelsPerRadian < 0.6) {
            return count(Mark.TINY);
        }
        return count(null);
    }

    private static @Nullable Mark count(@Nullable Mark why) {
        if (why == null) drawn++;
        else skipped++;
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
     * extracted, so it covers the players of the frame before, with room to spare.
     */
    public static float farPlane(float vanilla) {
        FarConfig config = FarConfig.get();
        if (!config.anyDistant() || !config.extendFarPlane) return vanilla;
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
        if (!drawable(dispatcher, entity)) return null;
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
        }
    }

    /** Entity types whose renderer gave no state, or failed: never drawn far away again. */
    private static final Set<EntityType<?>> LEFT_OUT = Collections.newSetFromMap(new IdentityHashMap<>());
    //? if >=1.21.2 && <1.21.9
    /*private static final Set<EntityType<?>> PROBED = Collections.newSetFromMap(new IdentityHashMap<>());*/

    /**
     * Whether this entity's renderer can give the mod a state of its own. Before 1.21.9,
     * GeckoLib 4's gives none: it only keeps the one it reuses, for the entity it was last
     * handed, and draws that entity whatever state it is given.
     */
    public static boolean drawable(EntityRenderDispatcher dispatcher, Entity entity) {
        EntityType<?> type = entity.getType();
        if (LEFT_OUT.contains(type)) return false;
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
                if (debug) DebugMarks.mark(live, Mark.PLAYER);
                if (config.impostorPlayers && (mount == live ? impostor(minecraft, eye, live, partialTick, config)
                        : !EXTRACTED.contains(mount) && impostorRiding(minecraft, eye, live, mount, partialTick, config))) {
                    continue;
                }
                EntityRenderState body = extract(dispatcher, live, partialTick);
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
                EntityRenderState mountState = mount == null ? null : extract(dispatcher, mount, partialTick);
                if (mountState != null && body instanceof HumanoidRenderState humanoid) {
                    humanoid.isPassenger = true;
                }
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
        if (debug) DebugMarks.end();
    }

    /** The figures of the easter eggs that are the game's own: the cow taken, the watcher. */
    private static void eggs(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        Ambience ambience = FarPlayerTracker.get().ambience();
        LivingEntity cow = ambience.ufo().cow();
        if (cow != null) add(frame, minecraft, eye, extract(dispatcher, cow, partialTick), null, true, Mark.FAKE, config, null);
        LivingEntity watcher = ambience.herobrine().figure();
        if (watcher != null) add(frame, minecraft, eye, extract(dispatcher, watcher, partialTick), null, true, Mark.FAKE, config, null);
    }

    /** Past the distance set in the options; 0 is no limit. */
    private static boolean tooFar(Entity entity, Vec3 eye, int limit) {
        return limit > 0 && entity.distanceToSqr(eye) > (double) limit * limit;
    }

    /** Mobs met on the way, where they were, playing their animation. */
    private static void mobs(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs) return;
        MobMemory memory = FarPlayerTracker.get().mobs();
        List<MobMemory.Remembered> shown = memory.shown();
        for (MobMemory.Remembered mob : shown) {
            Entity puppet = mob.puppet();
            if (puppet == null) {
                if (debug && mob.waiting()) markRemembered(mob, Mark.WAITING);
                continue;
            }
            if (tooFar(puppet, eye, config.mobMaxDistance)) continue;
            // A copy walking up to its real mob stands for it, however close.
            if (!memory.handingOver(mob) && tooClose(puppet, eye, memory.nearRange(mob))) {
                if (debug) DebugMarks.mark(puppet, Mark.NEAR);
                continue;
            }
            if (skipped(puppet, true)) continue;
            if (occluded(mob.id(), puppet, config)) continue;
            if (debug) DebugMarks.mark(puppet, Mark.FAKE);
            mob.drawn();
            if (impostor(minecraft, eye, puppet, partialTick, config)) continue;
            EntityRenderState body = extract(dispatcher, puppet, partialTick);
            // A puppet is in no world, so never in water: fish would be drawn flopping on
            // their side, as on land. Water mobs were in water when last seen.
            if (body instanceof LivingEntityRenderState living && livesInWater(puppet)) living.isInWater = true;
            add(frame, minecraft, eye, body, null, true, Mark.FAKE, config, mob.id());
        }
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
        // Drawn bigger than it is with a minimum size: the bigger box is what must be hidden.
        double boost = 1.0;
        if (config.minApparentPixels > 0 && distance > 1e-3) {
            double extent = Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()));
            boost = Math.clamp(config.minApparentPixels / (extent / distance * pixelsPerRadian), 1.0, 400.0);
        }
        if (!Occlusion.hidden(key, entity, dx, dy, dz, boost)) {
            return false;
        }
        noteDistance(distance);
        // Counted as drawn when it passed the view tests: it is not drawn after all.
        drawn--;
        skipped++;
        if (debug) DebugMarks.mark(entity, Mark.HIDDEN);
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
        if (!config.distantMobs) return;
        boolean game = debug && DebugMarks.gameMobs();
        Map<EntityType<?>, Boolean> wanted = new IdentityHashMap<>();
        MobMemory memory = FarPlayerTracker.get().mobs();
        for (Entity entity : memory.live()) {
            if (EXTRACTED.contains(entity)) {
                if (game) DebugMarks.mark(entity, Mark.GAME);
                continue;
            }
            if (entity.isRemoved() || entity.isInvisible() || entity.isPassenger() || entity.isVehicle()) continue;
            // Its copy is still walking up to it, and drawn in its place.
            if (memory.hides(entity)) continue;
            boolean shown = wanted.computeIfAbsent(entity.getType(),
                    type -> config.mobTypes.contains(EntityType.getKey(type).toString()));
            if (!shown && !(config.rememberNamedMobs && entity.hasCustomName())) continue;
            // Close by, the game leaves a mob out only when the terrain hides it.
            if (entity.distanceToSqr(eye) < 24 * 24) continue;
            if (tooFar(entity, eye, config.mobMaxDistance)) continue;
            if (skipped(entity, true)) continue;
            if (occluded(entity.getUUID(), entity, config)) continue;
            if (debug) DebugMarks.mark(entity, Mark.LIVE);
            if (impostor(minecraft, eye, entity, partialTick, config)) continue;
            add(frame, minecraft, eye, extract(dispatcher, entity, partialTick), null, false, Mark.LIVE, config,
                    entity.getUUID());
        }
    }

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
        int dimension = SharedPositions.dimensionCode(level.dimension());
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.dimension() != dimension) continue;
            RestingPuppet puppet = resting.puppet(level, spot);
            if (skipped(puppet, true)) continue;
            if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
            EntityRenderState body = extract(dispatcher, puppet, partialTick);
            if (body == null) continue;
            RestingPuppet.Look look = puppet.look();
            // On what they rode when they logged off: the mount drawn under them, they seated on it.
            Entity mount = look == RestingPuppet.Look.RIDE ? puppet.mount() : null;
            // Far away a picture, as for players online, in whatever posture they are.
            if (config.impostorPlayers && restingImpostor(minecraft, eye, puppet, mount, look, partialTick, config)) continue;
            EntityRenderState mountState = mount == null ? null : extract(dispatcher, mount, partialTick);
            if (mountState != null && body instanceof HumanoidRenderState humanoid) {
                humanoid.isPassenger = true;
            }
            if (look != RestingPuppet.Look.STAND && look != RestingPuppet.Look.RIDE
                    && body instanceof LivingEntityRenderState living) {
                living.yRot = 0;
                living.xRot = 0;
                living.walkAnimationSpeed = 0;
                if (look == RestingPuppet.Look.BED) {
                    // In the bed: its sleeping position, so the game lays it along the bed.
                    living.pose = Pose.SLEEPING;
                }
            }
            if (look == RestingPuppet.Look.SIT && body instanceof HumanoidRenderState humanoid) {
                // The riding pose, without a mount: legs out in front, on the ground.
                humanoid.isPassenger = true;
                body.y -= SEAT_DROP;
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
        double reach = DepthFar.of(minecraft) * 0.95;
        double pull = distance > reach ? reach / distance : 1.0;

        double boost = 1.0;
        if (config.minApparentPixels > 0) {
            double pixelsPerRadian = DepthFar.pixelsPerRadian(minecraft);
            // The longest side: a sleeper is 1.8 blocks long and 0.2 high.
            double extent = Math.max(1.8, Math.max(body.boundingBoxHeight, body.boundingBoxWidth)) * size;
            double apparent = extent / distance * pixelsPerRadian;
            boost = Math.clamp(config.minApparentPixels / apparent, 1.0, 400.0);
        }

        Transform transform = new Transform(ax, ay, az, pull, pull * boost * size);
        if (distance > drawnRange) {
            // Where it shows on screen: as big as drawn, around its middle, a mount included.
            double extent = Math.max(1.8, Math.max(body.boundingBoxHeight, body.boundingBoxWidth)) * size * boost;
            FarDepth.figure(ax, ay + extent * 0.5, az, extent * (mount == null ? 0.75 : 1.5));
        }
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        for (EntityRenderState state : mount == null ? new EntityRenderState[]{body} : new EntityRenderState[]{mount, body}) {
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
            else if (!lit(level, BlockPos.containing(state.x, state.y, state.z))) state.lightCoords = OPEN_SKY;
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

    private static int light(@Nullable ClientLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + 0.1, z);
        if (!lit(level, feet)) return OPEN_SKY;
        int block = 0, sky = 0;
        for (int up = 0; up < 3; up++) {
            BlockPos at = feet.above(up);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
        }
        return LightTexture.pack(block, sky);
    }

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
            if (player.puppet() != null) ImpostorAtlas.request(ImpostorKey.of(player.puppet()), player.puppet());
        }
        if (!config.distantMobs) return;
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            Entity puppet = mob.puppet();
            if (puppet instanceof LivingEntity) ImpostorAtlas.request(ImpostorKey.of(puppet), puppet);
        }
    }

    /** What a figure showed last as an impostor, so that neither its sheet nor its picture flickers. */
    private record Shown(ImpostorKey key, int view, long frame, boolean ridden) {
    }

    private static final Map<Entity, Shown> SHOWN = new IdentityHashMap<>();
    private static long frame;

    /** A figure must come this much inside the impostor distance to get its model back. */
    private static final double IMPOSTOR_RETURN = 0.95;

    /**
     * Draws a flat picture of a figure in place of its model, when it is past the impostor
     * distance and its pictures are baked (they are asked for otherwise, and the model is
     * drawn meanwhile). Nothing is extracted: that is what is saved.
     *
     * <p>Once a figure is an impostor it stays one until it is a little closer than the
     * distance, it keeps its picture until it has clearly turned to the next, and when its
     * look changes (a sheep sheared, a player's armour) it keeps its old picture until the
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
        if (!ImpostorAtlas.usable() || !(entity instanceof LivingEntity living)
                || entity.isPassenger() || entity.isVehicle()
                || !drawable(minecraft.getEntityRenderDispatcher(), entity)) {
            return false;
        }
        ImpostorKey key = ImpostorKey.of(entity);
        float yaw = Float.isNaN(facing) ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : facing;
        return picture(minecraft, eye, entity, entity, key, () -> ImpostorAtlas.request(key, entity), partialTick, config,
                dx, dy, dz, yaw, Math.max(entity.getBbHeight(), entity.getBbWidth()));
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
        return picture(minecraft, eye, rider, mount, key,
                () -> ImpostorAtlas.request(key, mount, new ImpostorAtlas.Rider(rider, seat, turned)), partialTick, config,
                0, 0, 0, yaw, extent);
    }

    /**
     * The picture of {@code key} where {@code at} stands, moved by ({@code dx}, {@code dy},
     * {@code dz}), its view chosen for a body facing {@code yaw}; asked for with
     * {@code request} while it is not baked. {@code shown} is the figure the picture stands
     * for, which keeps its last picture until the new one is baked.
     */
    private static boolean picture(Minecraft minecraft, Vec3 eye, Entity shown, Entity at, ImpostorKey key, Runnable request,
                                   float partialTick, FarConfig config, double dx, double dy, double dz, float yaw,
                                   double size) {
        double x = Mth.lerp(partialTick, at.xOld, at.getX()) + dx;
        double y = Mth.lerp(partialTick, at.yOld, at.getY()) + dy;
        double z = Mth.lerp(partialTick, at.zOld, at.getZ()) + dz;
        double ax = x - eye.x, ay = y - eye.y, az = z - eye.z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        Shown last = SHOWN.get(shown);
        double from = last == null ? config.impostorDistance : config.impostorDistance * IMPOSTOR_RETURN;
        if (distance <= from || distance < 1e-3) {
            if (last != null) SHOWN.remove(shown);
            return false;
        }
        // A rider is pictured with its mount, at the mount's place.
        boolean ridden = at != shown;
        ImpostorAtlas.Sheet sheet = ImpostorAtlas.sheet(key);
        if (sheet == null) {
            request.run();
            // The last picture stands in while this one is baked, or refused for want of room,
            // only if it shows the same: never a player on foot for one in a boat, nor the reverse.
            sheet = last == null || last.ridden != ridden ? null : ImpostorAtlas.sheet(last.key);
            if (sheet == null) return false;
            key = last.key;
        }
        if (distance > drawnRange) FarDepth.needed();
        farthest = Math.max(farthest, distance);
        double reach = DepthFar.of(minecraft) * 0.95;
        double pull = distance > reach ? reach / distance : 1.0;
        double boost = 1.0;
        if (config.minApparentPixels > 0) {
            boost = Math.clamp(config.minApparentPixels / (size / distance * pixelsPerRadian), 1.0, 400.0);
        }
        int view = ImpostorViews.view(yaw, -ax, -az, last == null ? -1 : last.view);
        SHOWN.put(shown, new Shown(key, view, frame, ridden));
        int light = light(minecraft.level, x, y, z);
        int outline = minecraft.player == null ? 0 : DebugMarks.impostorOutline(config);
        if (outline != 0 && ImpostorRenderer.OUTLINES) glowing = true;
        ImpostorRenderer.add(new ImpostorRenderer.Billboard(ax * pull, ay * pull, az * pull,
                sheet.worldSize() * pull * boost, sheet, view, light, outline, false));
        if (distance > drawnRange) {
            double extent = sheet.worldSize() * boost;
            FarDepth.figure(ax, ay + extent * 0.5, az, extent * 0.75);
        }
        return true;
    }

    /** Blocks a figure in the riding pose is lowered by to sit on the ground: the seat's height. */
    private static final double SEAT_DROP = 0.6;
    /** Blocks a sleeper with no bed under it is raised by: the game lays one a little low. */
    private static final double LIE_LIFT = 0.13;

    /**
     * A player who logged off as a picture, in its posture: standing, seated on the ground,
     * in a bed (laid along the bed, as the game lays a sleeper), or on its mount.
     */
    private static boolean restingImpostor(Minecraft minecraft, Vec3 eye, RestingPuppet puppet, @Nullable Entity mount,
                                           RestingPuppet.Look look, float partialTick, FarConfig config) {
        return switch (look) {
            case STAND -> impostor(minecraft, eye, puppet, partialTick, config);
            case SIT -> impostor(minecraft, eye, puppet, partialTick, config, 0, -SEAT_DROP, 0, Float.NaN);
            case BED -> {
                Direction bed = puppet.getBedOrientation();
                if (bed == null) {
                    // Lying pictures are baked raised (ImpostorAtlas.LYING_LIFT): lowered back here.
                    yield impostor(minecraft, eye, puppet, partialTick, config, 0, LIE_LIFT - ImpostorAtlas.LYING_LIFT, 0,
                            Float.NaN);
                }
                // As the game lays one: its head on the pillow, its body along the bed.
                double back = puppet.getEyeHeight() - 0.1;
                yield impostor(minecraft, eye, puppet, partialTick, config, -bed.getStepX() * back,
                        -ImpostorAtlas.LYING_LIFT, -bed.getStepZ() * back, sleeping(bed));
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
        if (frame % 600 == 0) SHOWN.values().removeIf(shown -> frame - shown.frame > 600);
    }

    /** Whether an entity the server sends should be drawn whatever its distance. */
    public static boolean alwaysRender(Entity entity) {
        FarConfig config = FarConfig.get();
        return config.enabled && config.renderTrackedVehiclesFar && carriesOtherPlayer(entity, 0);
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
