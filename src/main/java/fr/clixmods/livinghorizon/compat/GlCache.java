package fr.clixmods.livinghorizon.compat;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;

import java.util.HashMap;
import java.util.Map;

/**
 * What OpenGL said of a texture (its size and format) or of a framebuffer's depth attachment,
 * kept rather than asked every frame. With the driver on a thread of its own (NVIDIA's threaded
 * optimisation, Mesa's glthread), each question stops the game until the driver has caught up.
 *
 * <p>Everything is asked again once the main render target changes size: the window was resized,
 * and the textures were made again, often under the same names. Also once a second whatever
 * happens, for a texture made again under the same name without the window changing (a shader
 * pack that picks another depth format). The textures this mod makes are noted as they are made
 * and never asked about. The render thread only.
 */
final class GlCache {
    /** How long an answer is kept at most, in nanoseconds. */
    private static final long REFRESH = 1_000_000_000L;

    /** A texture's name to its width, height and internal format. */
    private static final Map<Integer, int[]> TEXTURES = new HashMap<>();
    /** A framebuffer's name and attachment point to the attachment's type and name. */
    private static final Map<Long, int[]> ATTACHMENTS = new HashMap<>();
    private static int screenWidth, screenHeight;
    private static long refreshed;

    private GlCache() {
    }

    private static void check() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        long now = System.nanoTime();
        if (main.width == screenWidth && main.height == screenHeight && now - refreshed < REFRESH) return;
        screenWidth = main.width;
        screenHeight = main.height;
        refreshed = now;
        TEXTURES.clear();
        ATTACHMENTS.clear();
    }

    private static int[] texture(int texture) {
        check();
        int[] known = TEXTURES.get(texture);
        if (known == null) {
            known = new int[]{
                    GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_WIDTH),
                    GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_HEIGHT),
                    GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT)};
            TEXTURES.put(texture, known);
        }
        return known;
    }

    static int width(int texture) {
        return texture(texture)[0];
    }

    static int height(int texture) {
        return texture(texture)[1];
    }

    static int format(int texture) {
        return texture(texture)[2];
    }

    /** A texture this mod has just made, at this size and format: nothing to ask. */
    static void made(int texture, int width, int height, int format) {
        check();
        TEXTURES.put(texture, new int[]{width, height, format});
    }

    /** A texture this mod deleted: its name may come back for another one. */
    static void deleted(int texture) {
        TEXTURES.remove(texture);
    }

    private static int[] attachment(int framebuffer, int attachment) {
        check();
        long key = (long) framebuffer << 32 | (attachment & 0xFFFFFFFFL);
        int[] known = ATTACHMENTS.get(key);
        if (known == null) {
            int type = GL45C.glGetNamedFramebufferAttachmentParameteri(framebuffer, attachment,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int name = type == GL11C.GL_NONE ? 0 : GL45C.glGetNamedFramebufferAttachmentParameteri(framebuffer,
                    attachment, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            known = new int[]{type, name};
            ATTACHMENTS.put(key, known);
        }
        return known;
    }

    /** What is attached there: {@code GL_TEXTURE}, {@code GL_RENDERBUFFER} or {@code GL_NONE}. */
    static int attachmentType(int framebuffer, int attachment) {
        return attachment(framebuffer, attachment)[0];
    }

    /** The name of the texture or renderbuffer attached there, 0 for none. */
    static int attachmentName(int framebuffer, int attachment) {
        return attachment(framebuffer, attachment)[1];
    }
}
