package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * The OpenGL calls of the mod newer than 4.1, each with a way round it on a context without
 * them. macOS never goes past OpenGL 4.1, and LWJGL answers a call to a function the context
 * lacks by aborting the JVM ({@code jni_FatalError}), with no exception to catch: whatever
 * the mod calls past 4.1 goes through here, and here only after asking the context.
 *
 * <ul>
 *   <li>Direct state access (4.5): objects made and changed by name. Without it, bound to a
 *       target, changed, and the binding found there put back.</li>
 *   <li>{@code glCopyImageSubData} (4.3): a depth copy. Without it, a blit between two
 *       framebuffers of ours.</li>
 *   <li>{@code glTexStorage2D} (4.2) and the depth-stencil texture mode (4.3): without them,
 *       {@code glTexImage2D}, and the mode's default, which is the one wanted.</li>
 * </ul>
 *
 * <p>The ways round cost a few more calls and waits for the driver, on those contexts only.
 * The render thread only.
 */
public final class GlCalls {
    private static boolean known, dsa, copyImage, storage, stencilMode;
    /** The framebuffers a copy blits between, without {@code glCopyImageSubData}. */
    private static int copyRead, copyDraw;

    private GlCalls() {
    }

    private static void know() {
        if (known) return;
        known = true;
        GLCapabilities caps = GL.getCapabilities();
        dsa = caps.OpenGL45 || caps.GL_ARB_direct_state_access;
        copyImage = caps.OpenGL43 || caps.GL_ARB_copy_image;
        storage = caps.OpenGL42 || caps.GL_ARB_texture_storage;
        stencilMode = caps.OpenGL43 || caps.GL_ARB_stencil_texturing;
        if (!dsa || !copyImage) {
            String without = !dsa && !copyImage ? "direct state access and image copies" : !dsa ? "direct state access" : "image copies";
            LivingHorizonClient.LOGGER.info("OpenGL {} has no {}: the far terrain's depth goes without them",
                    GL11C.glGetString(GL11C.GL_VERSION), without);
        }
    }

    /** Whether the context has direct state access (OpenGL 4.5), for the game tests. */
    static boolean directStateAccess() {
        know();
        return dsa;
    }

    // --- Framebuffers ---------------------------------------------------------------------

    static int createFramebuffer() {
        know();
        return dsa ? GL45C.glCreateFramebuffers() : GL30C.glGenFramebuffers();
    }

    /** Attaches level 0 of a 2D texture, or nothing when {@code texture} is 0. */
    static void framebufferTexture(int framebuffer, int attachment, int texture) {
        know();
        if (dsa) {
            GL45C.glNamedFramebufferTexture(framebuffer, attachment, texture, 0);
            return;
        }
        int previous = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, attachment, GL11C.GL_TEXTURE_2D, texture, 0);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previous);
    }

    /** A framebuffer that neither draws nor reads colour. */
    static void noColour(int framebuffer) {
        know();
        if (dsa) {
            GL45C.glNamedFramebufferDrawBuffer(framebuffer, GL11C.GL_NONE);
            GL45C.glNamedFramebufferReadBuffer(framebuffer, GL11C.GL_NONE);
            return;
        }
        int draw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
        GL11C.glDrawBuffer(GL11C.GL_NONE);
        GL11C.glReadBuffer(GL11C.GL_NONE);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
    }

    static int framebufferStatus(int framebuffer) {
        know();
        if (dsa) return GL45C.glCheckNamedFramebufferStatus(framebuffer, GL30C.GL_DRAW_FRAMEBUFFER);
        int previous = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previous);
        return status;
    }

    /** One parameter of what is attached to a framebuffer that is not the default one. */
    static int framebufferAttachment(int framebuffer, int attachment, int parameter) {
        know();
        if (dsa) return GL45C.glGetNamedFramebufferAttachmentParameteri(framebuffer, attachment, parameter);
        int previous = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
        int value = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, attachment, parameter);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previous);
        return value;
    }

    // --- Textures -------------------------------------------------------------------------

    /**
     * Binds a 2D texture to a unit. Without direct state access this leaves that unit active:
     * only inside a {@code FarDepth.GlState} that saved the unit, which puts both back.
     */
    static void bindTextureUnit(int unit, int texture) {
        know();
        if (dsa) {
            GL45C.glBindTextureUnit(unit, texture);
            return;
        }
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
    }

    /** Level 0 of a 2D texture: its width, height and internal format, into {@code out}. */
    static void textureLevel(int texture, int[] out) {
        know();
        if (dsa) {
            out[0] = GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_WIDTH);
            out[1] = GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_HEIGHT);
            out[2] = GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
            return;
        }
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        out[0] = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
        out[1] = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
        out[2] = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
    }

    /**
     * A 2D depth texture of one level, read with {@code texelFetch} (nearest, no comparison),
     * which a copy of a depth texture of that format may be made into.
     */
    static int depthTexture(int format, int width, int height) {
        know();
        // Before 1.21.5 the game's depth has an unsized format, which storage refuses and
        // leaves the copy without a size: it is made the way the game makes its own, so
        // that copying between the two stays allowed.
        boolean unsized = format == GL11C.GL_DEPTH_COMPONENT || format == GL30C.GL_DEPTH_STENCIL;
        if (dsa && !unsized) {
            int texture = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
            GL45C.glTextureStorage2D(texture, 1, format, width, height);
            GL45C.glTextureParameteri(texture, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL45C.glTextureParameteri(texture, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL45C.glTextureParameteri(texture, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
            GL45C.glTextureParameteri(texture, GL43C.GL_DEPTH_STENCIL_TEXTURE_MODE, GL11C.GL_DEPTH_COMPONENT);
            return texture;
        }
        int texture = GL11C.glGenTextures();
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        if (storage && !unsized) {
            GL42C.glTexStorage2D(GL11C.GL_TEXTURE_2D, 1, format, width, height);
        } else {
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, format, width, height, 0, pixelFormat(format), pixelType(format),
                    (ByteBuffer) null);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
        }
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        // Its default is the depth already: only set where it can be.
        if (stencilMode) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL43C.GL_DEPTH_STENCIL_TEXTURE_MODE, GL11C.GL_DEPTH_COMPONENT);
        }
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        return texture;
    }

    /**
     * Level 0 of a 2D depth texture as floats, bottom row first, into {@code out}. The pack
     * state it is read with is the caller's to set.
     */
    public static void readDepth(int texture, float[] out) {
        know();
        if (dsa) {
            GL45C.glGetTextureImage(texture, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, out);
            return;
        }
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, out);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
    }

    private static boolean hasStencil(int format) {
        return format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8 || format == GL30C.GL_DEPTH_STENCIL;
    }

    private static int pixelFormat(int format) {
        return hasStencil(format) ? GL30C.GL_DEPTH_STENCIL : GL11C.GL_DEPTH_COMPONENT;
    }

    private static int pixelType(int format) {
        return switch (format) {
            case GL30C.GL_DEPTH24_STENCIL8, GL30C.GL_DEPTH_STENCIL -> GL30C.GL_UNSIGNED_INT_24_8;
            case GL30C.GL_DEPTH32F_STENCIL8 -> GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
            case GL14C.GL_DEPTH_COMPONENT16, GL14C.GL_DEPTH_COMPONENT24, GL14C.GL_DEPTH_COMPONENT32 -> GL11C.GL_UNSIGNED_INT;
            default -> GL11C.GL_FLOAT;
        };
    }

    /**
     * Copies {@code width} by {@code height} of one depth texture into another of the same
     * format, from their bottom left corner, stencil included.
     */
    static void copyDepth(int from, int to, int width, int height, int format) {
        know();
        if (copyImage) {
            GL43C.glCopyImageSubData(from, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, to, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
            return;
        }
        boolean stencil = hasStencil(format);
        int attachment = stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT;
        int draw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean made = copyRead == 0;
        if (made) {
            copyRead = GL30C.glGenFramebuffers();
            copyDraw = GL30C.glGenFramebuffers();
        }
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, copyRead);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, copyDraw);
        if (made) {
            GL11C.glReadBuffer(GL11C.GL_NONE);
            GL11C.glDrawBuffer(GL11C.GL_NONE);
        }
        // Attached every time: the textures are made again, often under the same names (see FarDepth.attachDepth).
        if (!stencil) {
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL11C.GL_TEXTURE_2D, 0, 0);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL11C.GL_TEXTURE_2D, 0, 0);
        }
        GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, attachment, GL11C.GL_TEXTURE_2D, from, 0);
        GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, attachment, GL11C.GL_TEXTURE_2D, to, 0);
        // A blit is cut by the scissor test, and by nothing else that is set.
        if (scissor) GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11C.GL_DEPTH_BUFFER_BIT | (stencil ? GL11C.GL_STENCIL_BUFFER_BIT : 0), GL11C.GL_NEAREST);
        if (scissor) GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
    }

    // --- Vertex arrays and buffers ----------------------------------------------------------
    // The copy targets' bindings are read by their own names: the _BINDING names are OpenGL 4.2's, for the same values.

    static int createVertexArray() {
        know();
        // A name made by glGen becomes an array the first time it is bound, which is how it is used.
        return dsa ? GL45C.glCreateVertexArrays() : GL30C.glGenVertexArrays();
    }

    static int createBuffer() {
        know();
        return dsa ? GL45C.glCreateBuffers() : GL15C.glGenBuffers();
    }

    static void bufferData(int buffer, FloatBuffer data, int usage) {
        know();
        if (dsa) {
            GL45C.glNamedBufferData(buffer, data, usage);
            return;
        }
        int previous = GL11C.glGetInteger(GL31C.GL_COPY_WRITE_BUFFER);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, buffer);
        GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, data, usage);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, previous);
    }

    static void bufferData(int buffer, IntBuffer data, int usage) {
        know();
        if (dsa) {
            GL45C.glNamedBufferData(buffer, data, usage);
            return;
        }
        int previous = GL11C.glGetInteger(GL31C.GL_COPY_WRITE_BUFFER);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, buffer);
        GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, data, usage);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, previous);
    }

    static void bufferData(int buffer, long size, int usage) {
        know();
        if (dsa) {
            GL45C.glNamedBufferData(buffer, size, usage);
            return;
        }
        int previous = GL11C.glGetInteger(GL31C.GL_COPY_WRITE_BUFFER);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, buffer);
        GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, size, usage);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, previous);
    }

    static void copyBuffer(int from, int to, long size) {
        know();
        if (dsa) {
            GL45C.glCopyNamedBufferSubData(from, to, 0, 0, size);
            return;
        }
        int read = GL11C.glGetInteger(GL31C.GL_COPY_READ_BUFFER);
        int write = GL11C.glGetInteger(GL31C.GL_COPY_WRITE_BUFFER);
        GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, from);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, to);
        GL31C.glCopyBufferSubData(GL31C.GL_COPY_READ_BUFFER, GL31C.GL_COPY_WRITE_BUFFER, 0, 0, size);
        GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, read);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, write);
    }

    /** Reads the start of a buffer, as many ints as {@code into} has room for. */
    static void readBuffer(int buffer, IntBuffer into) {
        know();
        if (dsa) {
            GL45C.glGetNamedBufferSubData(buffer, 0, into);
            return;
        }
        int previous = GL11C.glGetInteger(GL31C.GL_COPY_READ_BUFFER);
        GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, buffer);
        GL15C.glGetBufferSubData(GL31C.GL_COPY_READ_BUFFER, 0, into);
        GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, previous);
    }
}
