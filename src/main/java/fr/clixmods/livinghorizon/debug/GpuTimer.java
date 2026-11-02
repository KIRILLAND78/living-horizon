package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.render.DepthFar;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/**
 * The GPU's time in a section, with {@code GL_TIME_ELAPSED} queries read some frames later,
 * as {@code OcclusionQueries} does: never waited for. Only one such query can be open at a
 * time, so a section opened inside another (the occlusion boxes inside the depth step) takes
 * the outer one's place and gives it back after: the outer section's time is the sum of its
 * pieces. Only on OpenGL, and not while the game's own profiler has its query open.
 */
final class GpuTimer {
    /** A GPU that falls this far behind is not waited for: the queries are thrown away. */
    private static final int MAX_PENDING = 128;

    private static final class Open {
        final Profile.Section section;
        int query;
        boolean split;

        Open(Profile.Section section, int query) {
            this.section = section;
            this.query = query;
        }
    }

    /** A query that ended; the last piece of a section's time closes it. {@code query} is 0 for a closing mark only. */
    private record Piece(int query, Profile.Section section, boolean last) {
    }

    private static final ArrayDeque<Open> STACK = new ArrayDeque<>();
    private static final ArrayDeque<Piece> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<Integer> POOL = new ArrayDeque<>();
    private static final Map<Profile.Section, Long> SUM = new EnumMap<>(Profile.Section.class);
    private static boolean broken;

    private GpuTimer() {
    }

    private static boolean usable() {
        if (broken || !ProfileHook.watched() || !DepthFar.openGl()) return false;
        if (!GL.getCapabilities().OpenGL33) {
            broken = true;
            return false;
        }
        return true;
    }

    static void begin(Profile.Section section) {
        try {
            boolean on = usable();
            Open outer = STACK.peek();
            if (outer != null && outer.query != 0) {
                GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
                PENDING.add(new Piece(outer.query, outer.section, false));
                outer.query = 0;
                outer.split = true;
            }
            STACK.push(new Open(section, on ? start() : 0));
        } catch (Throwable e) {
            fail(e);
        }
    }

    static void end(Profile.Section section) {
        try {
            Open open = STACK.peek();
            if (open == null || open.section != section) return;
            STACK.pop();
            if (open.query != 0) GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
            if (open.query != 0 || open.split) PENDING.add(new Piece(open.query, section, true));
            // Back in the section this one was opened in: its time goes on in a new piece.
            Open outer = STACK.peek();
            if (outer != null && outer.split && outer.query == 0 && usable()) outer.query = start();
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Opens a query, unless another is open: the game times its own frame the same way when its profiler runs. */
    private static int start() {
        if (GL15C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL15C.GL_CURRENT_QUERY) != 0) return 0;
        int query = POOL.isEmpty() ? GL15C.glGenQueries() : POOL.pop();
        GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        return query;
    }

    /** Takes in every answer the GPU already has, oldest first, without waiting for any. */
    static void collect() {
        if (PENDING.isEmpty()) return;
        try {
            while (!PENDING.isEmpty()) {
                Piece piece = PENDING.peek();
                if (piece.query != 0
                        && GL15C.glGetQueryObjecti(piece.query, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) {
                    break;
                }
                PENDING.poll();
                if (piece.query != 0) {
                    long nanos = GL33C.glGetQueryObjecti64(piece.query, GL15C.GL_QUERY_RESULT);
                    SUM.merge(piece.section, nanos, Long::sum);
                    POOL.push(piece.query);
                }
                if (piece.last) {
                    Long total = SUM.remove(piece.section);
                    if (total != null) Profile.recordGpu(piece.section, total);
                }
            }
            if (PENDING.size() > MAX_PENDING) {
                for (Piece piece : PENDING) {
                    if (piece.query != 0) GL15C.glDeleteQueries(piece.query);
                }
                PENDING.clear();
                SUM.clear();
            }
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        broken = true;
        STACK.clear();
        PENDING.clear();
        SUM.clear();
        LivingHorizonClient.LOGGER.warn("GPU timing of the mod's sections turned off", e);
    }
}
