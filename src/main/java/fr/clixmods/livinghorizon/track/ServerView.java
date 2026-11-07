package fr.clixmods.livinghorizon.track;

import net.minecraft.client.Options;

/**
 * How far, in chunks, the server sends entities to this client: the render distance, cut to the
 * view distance the server announced. That is what {@link Options#getEffectiveRenderDistance}
 * returns in the game alone, but Bobby makes it return the render distance whole, since it
 * draws its own saved chunks past the server's (issue #112). Those chunks hold no entities, so
 * where the game stops drawing terrain stays {@code getEffectiveRenderDistance}, and where the
 * server stops sending mobs and players is this.
 */
public final class ServerView {
    private ServerView() {
    }

    /** The chunks within which the server sends entities. */
    public static int chunks(Options options) {
        return chunks(options.renderDistance().get(), options.serverRenderDistance);
    }

    /** The same from both settings; {@code server} is 0 until the server has said. */
    static int chunks(int render, int server) {
        return server > 0 ? Math.min(render, server) : render;
    }
}
