package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Whether a distant mob is hidden by what stands in front of it, asked of the GPU itself.
 *
 * <p>Just before the entities are drawn - the terrain in the depth buffer, Voxy's or Distant
 * Horizons' merged in, no entity yet - the box around each distant mob is drawn against that
 * depth, with nothing written: the GPU answers whether a single pixel of it would show. The
 * answers are read a frame or two later, never waited for, and a mob whose last answer is
 * "no pixel" is not drawn.
 *
 * <p>The box is projected by the GPU with the matrices of the frame, exactly as the mob would
 * be, so the answer is the picture's: a mob with one pixel in view is drawn. Depth clamping
 * keeps a box past the far plane at the far plane, where only terrain in front can hide it.
 * A mob never asked about, or whose answer is too old, is drawn.
 *
 * <p>How the GPU answers depends on what it offers. From OpenGL 4.3, every box of the frame
 * goes in one instanced draw: each fragment that passes the depth test writes a word for its
 * box into a buffer, which is copied and read back once a fence says the GPU is done - a
 * dozen calls a frame whatever the number of mobs ({@link #drawWords}). Without it, each box
 * is drawn inside an occlusion query of its own ({@link #drawQueries}), five calls per box.
 *
 * <p><b>Keep the one-draw path.</b> It is the single most valuable optimisation of the mod:
 * measured by the author on his own machine, the frame rate went from 70 to 100 FPS with it
 * (the per-box queries cost five GL calls and a query per distant mob per frame, and as many
 * polls while the answers were pending). Never remove it, never make the per-box queries the
 * default again, and never let a refactor route every frame through {@link #drawQueries}:
 * that path is the fallback for OpenGL older than 4.3 and nothing else. Any change here is
 * checked with the {@code perf} game test and the debug panel's occlusion line ("one draw").
 *
 * <p>Any GL failure turns this off for the session, and the far terrain's world is read instead.
 */
public final class OcclusionQueries {
    /** An answer older than this many frames is not trusted: the mob is drawn. */
    private static final int MAX_AGE = 6;
    /** Batches of queries waiting for the GPU: past this, a frame asks nothing. */
    private static final int IN_FLIGHT_MAX = 4;
    /** Blocks added around a box: the model outgrows its box a little, and answers come late. */
    private static final double MARGIN = 0.25;

    private static boolean broken;
    private static String reason = "no frame yet";

    private static int program, vertexArray, framebuffer, framebufferDepth;
    private static int lowLocation, highLocation, matrixLocation;

    /** Room to hand {@link FarDepth#viewProjection()} to OpenGL: the frame's matrix is kept there only. */
    private static final float[] MATRIX = new float[16];
    /** {@link FarDepth#viewProjection()} holds this frame's: the level has started to render. */
    private static boolean viewNoted;

    /**
     * Queries sent in one frame, the first {@link #size}, in the order of their keys. Kept and
     * filled again: a frame allocates nothing once the arrays are large enough.
     */
    private static final class Batch {
        Object[] keys = new Object[64];
        int[] queries = new int[64];
        int size;
        long frame;

        void fit(int n) {
            if (keys.length >= n) return;
            int length = Math.max(n, keys.length * 2);
            keys = new Object[length];
            queries = new int[length];
        }
    }

    /** The last answer for a mob, and the frame it was asked in: written over in place. */
    private static final class Answer {
        boolean visible;
        long frame;
    }

    /** This frame's boxes, relative to the camera: their keys, and two corners each, six floats. */
    private static Object[] askedKeys = new Object[64];
    private static float[] askedBoxes = new float[6 * 64];
    private static int askedSize;
    private static final ArrayDeque<Batch> IN_FLIGHT = new ArrayDeque<>();
    private static final ArrayDeque<Batch> SPARE = new ArrayDeque<>();
    /** Query names given back by the GPU, to use again. */
    private static int[] pool = new int[64];
    private static int pooled;
    private static final Map<Object, Answer> ANSWERS = new HashMap<>();
    private static long frame;
    private static int culled, asked;

    private OcclusionQueries() {
    }

    // --- The frame ----------------------------------------------------------------------------

    /** The level starts to render: {@link FarDepth#viewProjection()} holds this frame's matrices. */
    static void viewNoted() {
        viewNoted = true;
    }

    /** Before the distant mobs are looked at: what the GPU answered since comes in. */
    public static void beginFrame() {
        frame++;
        clearAsked();
        culled = 0;
        // With Vulkan (26.2 on), there is no OpenGL to ask.
        if (broken || !DepthFar.openGl()) return;
        try {
            collect();
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Not turned off by a GL failure. */
    public static boolean usable() {
        return !broken;
    }

    // --- Through the game's GPU device ---------------------------------------------------------

    /** The game's device answers instead of OpenGL's queries ({@code FarDepthGpu}, 26.3). */
    private static boolean gpuReady;
    private static long takeCursor;

    public static void gpuReady(boolean ready) {
        gpuReady = ready;
    }

    public static boolean gpuReady() {
        return gpuReady && !broken;
    }

    /**
     * This frame's boxes, at most {@code keys.length}, for the game's device: their keys, and
     * their two corners in {@code boxes}, six floats each. The others are asked again next frame.
     */
    public static int takeAsked(Object[] keys, float[] boxes) {
        int size = askedSize;
        asked = size;
        int n = Math.min(keys.length, size);
        // More than fit in one frame: each frame starts where the last one stopped.
        int start = size > n ? (int) (takeCursor % size) : 0;
        takeCursor += n;
        for (int i = 0; i < n; i++) {
            int from = (start + i) % size;
            keys[i] = askedKeys[from];
            System.arraycopy(askedBoxes, 6 * from, boxes, 6 * i, 6);
        }
        clearAsked();
        return n;
    }

    /** The frame being drawn, to date the answers that come back for it. */
    public static long frame() {
        return frame;
    }

    /** An answer from the game's device about a box asked in {@code askedIn}. */
    public static void answer(Object key, boolean visible, long askedIn) {
        Answer old = ANSWERS.get(key);
        if (old == null || old.frame <= askedIn) remember(key, visible, askedIn);
    }

    /** Remembers an answer, in the place of the last one about the same mob. */
    private static void remember(Object key, boolean visible, long askedIn) {
        Answer answer = ANSWERS.get(key);
        if (answer == null) ANSWERS.put(key, answer = new Answer());
        answer.visible = visible;
        answer.frame = askedIn;
    }

    /** Forgets this frame's boxes, and lets go of their keys. */
    private static void clearAsked() {
        Arrays.fill(askedKeys, 0, askedSize, null);
        askedSize = 0;
    }

    /** Another world: what was seen is of no use. */
    public static void clear() {
        ANSWERS.clear();
    }

    // --- The test -----------------------------------------------------------------------------

    /**
     * Whether a mob is hidden, by the last answer about it; asks again this frame. The box
     * goes from its feet, at {@code (x, y, z)} relative to the camera, to {@code height}
     * above, {@code half} to each side.
     */
    public static boolean hidden(Object key, double x, double y, double z, double half, double height) {
        if (broken) return false;
        half += MARGIN;
        if (askedSize == askedKeys.length) {
            askedKeys = Arrays.copyOf(askedKeys, askedSize * 2);
            askedBoxes = Arrays.copyOf(askedBoxes, 6 * askedSize * 2);
        }
        int at = 6 * askedSize;
        askedKeys[askedSize++] = key;
        askedBoxes[at] = (float) (x - half);
        askedBoxes[at + 1] = (float) (y - MARGIN);
        askedBoxes[at + 2] = (float) (z - half);
        askedBoxes[at + 3] = (float) (x + half);
        askedBoxes[at + 4] = (float) (y + height + MARGIN);
        askedBoxes[at + 5] = (float) (z + half);
        Answer answer = ANSWERS.get(key);
        if (answer == null || answer.visible || frame - answer.frame > MAX_AGE) return false;
        culled++;
        return true;
    }

    /** For the debug panel. */
    public static String state() {
        if (broken) return "off: " + reason;
        int inFlight = IN_FLIGHT.size() + WORDS_IN_FLIGHT.size();
        return asked + " asked, " + culled + " hidden, " + inFlight + " frames in flight, "
                + (wordsUsable() ? "one draw" : "one query each")
                + (reason.isEmpty() ? "" : " (" + reason + ")");
    }

    /** How many mobs the last frame hid. */
    public static int culled() {
        return culled;
    }

    // --- On the GPU ---------------------------------------------------------------------------

    /**
     * Draws this frame's boxes against a depth texture holding the terrain and no entity yet,
     * and arranges for the answers to come back. Called just before the entities are drawn.
     */
    static void run(int depthTexture, int width, int height) {
        long started = Profile.begin(Profile.Section.OCCLUSION_RUN);
        try {
            drawBoxes(depthTexture, width, height);
        } finally {
            Profile.end(Profile.Section.OCCLUSION_RUN, started);
        }
    }

    /** {@code width} by {@code height}: the depth texture's size, known to whoever made it. */
    private static void drawBoxes(int depthTexture, int width, int height) {
        asked = askedSize;
        if (broken || askedSize == 0) return;
        if (!viewNoted) {
            reason = "no view this frame";
            return;
        }
        if (IN_FLIGHT.size() + WORDS_IN_FLIGHT.size() >= IN_FLIGHT_MAX) {
            reason = "the GPU is behind";
            return;
        }
        reason = "";
        FarDepth.viewProjection().get(MATRIX);
        try {
            if (width <= 0 || height <= 0) {
                reason = "the depth texture has no size";
                return;
            }
            prepare(depthTexture);
            if (wordsSupport == 0) wordsSupport = GL.getCapabilities().OpenGL43 ? 1 : -1;
            // Where the world is drawn: all of the texture, or its corner under a render scale.
            int pictureWidth = FarDepth.pictureWidth(), pictureHeight = FarDepth.pictureHeight();
            int viewWidth = pictureWidth > 0 ? Math.min(pictureWidth, width) : width;
            int viewHeight = pictureHeight > 0 ? Math.min(pictureHeight, height) : height;
            // Our framebuffer has no colour, and no texture is bound: blending, the colour masks,
            // the texture units and the scissor box are left alone, and not read.
            FarDepth.GlState gl = FarDepth.GlState.save(0, 0);
            boolean clamp = GL11C.glIsEnabled(GL32C.GL_DEPTH_CLAMP);
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                GL11C.glEnable(GL32C.GL_DEPTH_CLAMP);
                GL11C.glDepthFunc(DepthFar.reversed() ? GL11C.GL_GEQUAL : GL11C.GL_LEQUAL);
                GL11C.glDepthMask(false);
                GL11C.glViewport(0, 0, viewWidth, viewHeight);
                if (wordsUsable()) {
                    try {
                        drawWords();
                    } catch (Throwable e) {
                        // The queries still work: one per box, as before OpenGL 4.3.
                        wordsBroken = true;
                        LivingHorizonClient.LOGGER.warn("Occlusion boxes cannot be drawn in one go: one query per box instead", e);
                    }
                }
                if (!wordsUsable()) drawQueries();
            } finally {
                if (!clamp) GL11C.glDisable(GL32C.GL_DEPTH_CLAMP);
                gl.restore();
            }
        } catch (Throwable e) {
            fail(e);
        } finally {
            clearAsked();
            viewNoted = false;
        }
    }

    /** Takes in every batch the GPU has finished, oldest first, without waiting for any. */
    private static void collect() {
        long started = Profile.begin(Profile.Section.OCCLUSION_COLLECT);
        try {
            takeWords();
            takeAnswers();
            if (ANSWERS.size() > 4096) {
                for (Iterator<Answer> it = ANSWERS.values().iterator(); it.hasNext(); ) {
                    if (frame - it.next().frame > MAX_AGE) it.remove();
                }
            }
        } finally {
            Profile.end(Profile.Section.OCCLUSION_COLLECT, started);
        }
    }

    private static void fail(Throwable e) {
        broken = true;
        reason = String.valueOf(e);
        ANSWERS.clear();
        LivingHorizonClient.LOGGER.warn("Occlusion queries turned off: distant mobs are tested in the far terrain's world instead", e);
    }

    /** The program, and a framebuffer of our own around the depth texture, with no colour. */
    private static void prepare(int depthTexture) {
        if (vertexArray == 0) vertexArray = GlCalls.createVertexArray();
        // Attached again every frame: the copy is made again, often under the same name, when
        // the window is resized (see FarDepth.attachDepth).
        boolean made = framebuffer == 0;
        framebuffer = FarDepth.attachDepth(framebuffer, depthTexture);
        if (made) GlCalls.noColour(framebuffer);
        if (framebufferDepth != depthTexture && GlCalls.framebufferStatus(framebuffer) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("the occlusion framebuffer is incomplete");
        }
        framebufferDepth = depthTexture;
    }

    // --- One query per box (OpenGL 3.3) --------------------------------------------------------

    /** Draws each box inside an occlusion query of its own: nothing is written but the query's count. */
    private static void drawQueries() {
        if (program == 0) {
            program = compile();
            lowLocation = GL20C.glGetUniformLocation(program, "low");
            highLocation = GL20C.glGetUniformLocation(program, "high");
            matrixLocation = GL20C.glGetUniformLocation(program, "viewProjection");
        }
        // The conservative test is OpenGL 4.3; the game may run on 3.3 (26.x), where it is not to be used.
        GLCapabilities caps = GL.getCapabilities();
        int target = caps.OpenGL43 || caps.GL_ARB_ES3_compatibility
                ? GL43C.GL_ANY_SAMPLES_PASSED_CONSERVATIVE : GL33C.GL_ANY_SAMPLES_PASSED;
        Batch batch = SPARE.isEmpty() ? new Batch() : SPARE.pop();
        batch.fit(askedSize);
        batch.frame = frame;
        Object[] keys = batch.keys;
        int[] queries = batch.queries;
        GL20C.glUseProgram(program);
        GL20C.glUniformMatrix4fv(matrixLocation, false, MATRIX);
        GL30C.glBindVertexArray(vertexArray);
        for (int i = 0; i < askedSize; i++) {
            int at = 6 * i;
            keys[i] = askedKeys[i];
            queries[i] = pooled > 0 ? pool[--pooled] : GL15C.glGenQueries();
            GL20C.glUniform3f(lowLocation, askedBoxes[at], askedBoxes[at + 1], askedBoxes[at + 2]);
            GL20C.glUniform3f(highLocation, askedBoxes[at + 3], askedBoxes[at + 4], askedBoxes[at + 5]);
            GL15C.glBeginQuery(target, queries[i]);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 36);
            GL15C.glEndQuery(target);
        }
        batch.size = askedSize;
        IN_FLIGHT.add(batch);
    }

    private static void takeAnswers() {
        while (!IN_FLIGHT.isEmpty()) {
            Batch batch = IN_FLIGHT.peek();
            // Queries finish in the order they were sent: the last one done, the batch is done.
            int last = batch.queries[batch.size - 1];
            if (GL15C.glGetQueryObjecti(last, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) return;
            IN_FLIGHT.poll();
            for (int i = 0; i < batch.size; i++) {
                boolean visible = GL15C.glGetQueryObjecti(batch.queries[i], GL15C.GL_QUERY_RESULT) != 0;
                remember(batch.keys[i], visible, batch.frame);
                giveBack(batch.queries[i]);
            }
            Arrays.fill(batch.keys, 0, batch.size, null);
            batch.size = 0;
            SPARE.push(batch);
        }
    }

    private static void giveBack(int query) {
        if (pooled == pool.length) pool = Arrays.copyOf(pool, pooled * 2);
        pool[pooled++] = query;
    }

    /** A box from two corners, as twelve triangles; nothing is written but the query's count. */
    private static int compile() {
        String vertex = """
                #version 330 core
                uniform mat4 viewProjection;
                uniform vec3 low;
                uniform vec3 high;
                const int CORNERS[36] = int[36](
                    0, 2, 6, 0, 6, 4,   1, 5, 7, 1, 7, 3,
                    0, 4, 5, 0, 5, 1,   2, 3, 7, 2, 7, 6,
                    0, 1, 3, 0, 3, 2,   4, 6, 7, 4, 7, 5);
                void main() {
                    int corner = CORNERS[gl_VertexID];
                    vec3 pick = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
                    gl_Position = viewProjection * vec4(mix(low, high, pick), 1.0);
                }
                """;
        String fragment = """
                #version 330 core
                void main() {
                }
                """;
        return GlPrograms.program("Occlusion query", vertex, fragment);
    }

    // --- One draw for every box (OpenGL 4.3) ---------------------------------------------------

    /**
     * The boxes of one frame drawn at once: the words the fragments wrote, copied into
     * {@code buffer} (one unsigned int per box, 0 until a fragment of it passed), and the
     * fence placed after the copy, which says when the words may be read.
     */
    private static final class Words {
        int buffer;
        /** Bytes the buffer holds. */
        long capacity;
        long sync;
        /** The keys of its boxes, the first {@link #size}: kept and filled again. */
        Object[] keys = new Object[64];
        int size;
        long frame;
    }

    /** The draw in one go failed, or OpenGL is older than 4.3: one query per box instead. */
    private static boolean wordsBroken;
    /** Whether the context offers OpenGL 4.3, asked once on the GL thread: 0 not yet, 1 yes, -1 no. */
    private static int wordsSupport;
    private static int wordsProgram, wordsMatrixLocation;
    /** The boxes of this frame (two vec4 each) and the words the fragments write (one uint each). */
    private static int boxBuffer, wordBuffer;
    private static long boxCapacity, wordCapacity;
    private static FloatBuffer corners = BufferUtils.createFloatBuffer(8 * 64);
    private static IntBuffer words = BufferUtils.createIntBuffer(64);
    private static final ArrayDeque<Words> WORDS_IN_FLIGHT = new ArrayDeque<>();
    private static final ArrayDeque<Words> WORDS_FREE = new ArrayDeque<>();
    /** The storage buffer bindings put back after the draw, read again each frame. */
    private static final int[] BOUND = new int[2];
    private static final long[] STARTS = new long[2], SIZES = new long[2];

    /** Shader storage buffers and early fragment tests, both in OpenGL 4.3. */
    private static boolean wordsUsable() {
        return !wordsBroken && wordsSupport == 1;
    }

    /**
     * Every box in one instanced draw: a fragment that passes the depth test writes 1 for its
     * box, the words are copied to a buffer of this batch, and a fence is placed after the copy.
     * Every binding touched is put back: the game, Sodium and the shader packs keep buffers there.
     */
    private static void drawWords() {
        int n = askedSize;
        if (wordsProgram == 0) {
            wordsProgram = compileWords();
            wordsMatrixLocation = GL20C.glGetUniformLocation(wordsProgram, "viewProjection");
            boxBuffer = GlCalls.createBuffer();
            wordBuffer = GlCalls.createBuffer();
        }
        if (corners.capacity() < 8 * n) corners = BufferUtils.createFloatBuffer(8 * Math.max(n, 2 * corners.capacity() / 8));
        if (words.capacity() < n) words = BufferUtils.createIntBuffer(Math.max(n, 2 * words.capacity()));
        corners.clear();
        for (int i = 0; i < n; i++) {
            int at = 6 * i;
            corners.put(askedBoxes[at]).put(askedBoxes[at + 1]).put(askedBoxes[at + 2]).put(0f)
                    .put(askedBoxes[at + 3]).put(askedBoxes[at + 4]).put(askedBoxes[at + 5]).put(0f);
        }
        corners.flip();
        // Streamed: a new store each frame, so the GPU never waits for the last frame's draw.
        GlCalls.bufferData(boxBuffer, corners, GL15C.GL_STREAM_DRAW);
        words.clear();
        for (int i = 0; i < n; i++) words.put(0);
        words.flip();
        GlCalls.bufferData(wordBuffer, words, GL15C.GL_STREAM_COPY);

        Words batch = WORDS_FREE.isEmpty() ? new Words() : WORDS_FREE.pop();
        if (batch.buffer == 0) batch.buffer = GlCalls.createBuffer();
        if (batch.capacity < 4L * n) {
            batch.capacity = 4L * Math.max(n, 64);
            GlCalls.bufferData(batch.buffer, batch.capacity, GL15C.GL_STREAM_READ);
        }

        int storage = GL11C.glGetInteger(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING);
        int[] bound = BOUND;
        long[] starts = STARTS, sizes = SIZES;
        for (int i = 0; i < 2; i++) {
            bound[i] = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, i);
            starts[i] = GL32C.glGetInteger64i(GL43C.GL_SHADER_STORAGE_BUFFER_START, i);
            sizes[i] = GL32C.glGetInteger64i(GL43C.GL_SHADER_STORAGE_BUFFER_SIZE, i);
        }
        try {
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, boxBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, wordBuffer);
            GL20C.glUseProgram(wordsProgram);
            GL20C.glUniformMatrix4fv(wordsMatrixLocation, false, MATRIX);
            GL30C.glBindVertexArray(vertexArray);
            GL31C.glDrawArraysInstanced(GL11C.GL_TRIANGLES, 0, 36, n);
            // The fragments' writes are made visible to the copy that follows.
            GL42C.glMemoryBarrier(GL42C.GL_BUFFER_UPDATE_BARRIER_BIT);
            GlCalls.copyBuffer(wordBuffer, batch.buffer, 4L * n);
        } finally {
            for (int i = 0; i < 2; i++) {
                if (bound[i] != 0 && sizes[i] > 0) {
                    GL30C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, i, bound[i], starts[i], sizes[i]);
                } else {
                    GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, i, bound[i]);
                }
            }
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, storage);
        }
        batch.sync = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (batch.keys.length < n) batch.keys = new Object[Math.max(n, batch.keys.length * 2)];
        System.arraycopy(askedKeys, 0, batch.keys, 0, n);
        batch.size = n;
        batch.frame = frame;
        WORDS_IN_FLIGHT.add(batch);
    }

    /** Takes in the words of every batch whose fence has passed, oldest first, without waiting. */
    private static void takeWords() {
        while (!WORDS_IN_FLIGHT.isEmpty()) {
            Words batch = WORDS_IN_FLIGHT.peek();
            if (GL32C.glGetSynci(batch.sync, GL32C.GL_SYNC_STATUS, null) != GL32C.GL_SIGNALED) return;
            WORDS_IN_FLIGHT.poll();
            GL32C.glDeleteSync(batch.sync);
            batch.sync = 0;
            int n = batch.size;
            if (words.capacity() < n) words = BufferUtils.createIntBuffer(n);
            words.clear();
            words.limit(n);
            GlCalls.readBuffer(batch.buffer, words);
            for (int i = 0; i < n; i++) remember(batch.keys[i], words.get(i) != 0, batch.frame);
            Arrays.fill(batch.keys, 0, n, null);
            batch.size = 0;
            WORDS_FREE.push(batch);
        }
    }

    /**
     * The boxes from a buffer, one instance each; a fragment that passes the depth test - the
     * test runs first, so none runs for a hidden pixel - writes its box's word.
     */
    private static int compileWords() {
        String vertex = """
                #version 430 core
                uniform mat4 viewProjection;
                layout(std430, binding = 0) readonly buffer Boxes { vec4 corners[]; };
                const int CORNERS[36] = int[36](
                    0, 2, 6, 0, 6, 4,   1, 5, 7, 1, 7, 3,
                    0, 4, 5, 0, 5, 1,   2, 3, 7, 2, 7, 6,
                    0, 1, 3, 0, 3, 2,   4, 6, 7, 4, 7, 5);
                flat out int box;
                void main() {
                    box = gl_InstanceID;
                    vec3 low = corners[2 * gl_InstanceID].xyz;
                    vec3 high = corners[2 * gl_InstanceID + 1].xyz;
                    int corner = CORNERS[gl_VertexID];
                    vec3 pick = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
                    gl_Position = viewProjection * vec4(mix(low, high, pick), 1.0);
                }
                """;
        String fragment = """
                #version 430 core
                layout(early_fragment_tests) in;
                layout(std430, binding = 1) buffer Words { uint visible[]; };
                flat in int box;
                void main() {
                    visible[box] = 1u;
                }
                """;
        return GlPrograms.program("Occlusion words", vertex, fragment);
    }
}
