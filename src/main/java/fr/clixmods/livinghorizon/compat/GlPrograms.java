package fr.clixmods.livinghorizon.compat;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The OpenGL programs {@link FarDepth} and {@link OcclusionQueries} draw with: compiled, linked
 * and their samplers set in one place, every error named after the program it came from.
 */
final class GlPrograms {
    /** One triangle over the whole screen, which every full-screen program here draws. */
    private static final String FULL_SCREEN = """
            #version 330 core
            void main() {
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    /** The line a shader takes the shared depth arithmetic with, as {@code FarDepthGpu}'s do on 26.3. */
    static final String DEPTH_MATH = "#include <livinghorizon:far_depth.glsl>";
    private static final String DEPTH_MATH_FILE = "/assets/livinghorizon/shaders/include/far_depth.glsl";
    private static String depthMath;

    private GlPrograms() {
    }

    /**
     * A program drawing {@link #FULL_SCREEN} with {@code fragment}, its {@code samplers} bound
     * to texture units 0, 1, 2… in that order.
     */
    static int fullScreen(String name, String fragment, String... samplers) {
        return program(name, FULL_SCREEN, fragment, samplers);
    }

    /** A program of these two shaders, its {@code samplers} bound to texture units 0, 1, 2… in that order. */
    static int program(String name, String vertex, String fragment, String... samplers) {
        int vs = shader(name, GL20C.GL_VERTEX_SHADER, vertex);
        int fs;
        try {
            fs = shader(name, GL20C.GL_FRAGMENT_SHADER, fragment);
        } catch (RuntimeException e) {
            GL20C.glDeleteShader(vs);
            throw e;
        }
        int linked = GL20C.glCreateProgram();
        GL20C.glAttachShader(linked, vs);
        GL20C.glAttachShader(linked, fs);
        GL20C.glLinkProgram(linked);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(linked, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(linked);
            GL20C.glDeleteProgram(linked);
            throw new IllegalStateException(name + " program: " + log);
        }
        if (samplers.length > 0) {
            int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            GL20C.glUseProgram(linked);
            for (int unit = 0; unit < samplers.length; unit++) {
                GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, samplers[unit]), unit);
            }
            GL20C.glUseProgram(previous);
        }
        return linked;
    }

    /**
     * The locations of a program's uniforms, in the order of {@code names}: looked up once, as the
     * program is made, since each lookup waits for a driver that runs on a thread of its own.
     */
    static int[] uniforms(int program, String... names) {
        int[] locations = new int[names.length];
        for (int i = 0; i < names.length; i++) locations[i] = GL20C.glGetUniformLocation(program, names[i]);
        return locations;
    }

    /** One shader, its {@link #DEPTH_MATH} line replaced by that file. */
    static int shader(String name, int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, withDepthMath(source));
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException(name + (type == GL20C.GL_VERTEX_SHADER ? " vertex shader: " : " fragment shader: ") + log);
        }
        return shader;
    }

    /**
     * {@code source} with the shared depth arithmetic ({@code far_depth.glsl}) where it asks for
     * it: OpenGL has no {@code #include}, the game's own shader loader does it on 26.3.
     */
    static String withDepthMath(String source) {
        if (!source.contains(DEPTH_MATH)) return source;
        if (depthMath == null) {
            try (InputStream in = GlPrograms.class.getResourceAsStream(DEPTH_MATH_FILE)) {
                if (in == null) throw new IllegalStateException("missing " + DEPTH_MATH_FILE);
                depthMath = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return source.replace(DEPTH_MATH, depthMath);
    }
}
