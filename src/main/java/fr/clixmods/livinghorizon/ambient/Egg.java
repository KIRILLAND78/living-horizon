package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An easter egg from 9 on, as {@link Ambience} runs it: ticked, drawn, called by its command
 * {@code /livinghorizon ee<number>}. Each starts by chance only on the days the
 * {@link EggCalendar} allows it and while no other is under way ({@code quiet}), and tells the
 * calendar once it has come ({@link EggCalendar#shown}).
 */
public interface Egg {
    /** Its number, for its command, the calendar and the log. */
    int number();

    /** On its way or under way: no other easter egg starts by chance meanwhile. */
    boolean busy();

    /** Where it stands, while it is there; null otherwise. For the game tests. */
    @Nullable Vec3 position();

    /**
     * Looks for its place and comes there: by chance, or because the player asked
     * ({@code called}: wherever it can, at any hour; called again, it starts over). False if
     * the easter eggs are off or it has nowhere to come.
     */
    boolean summon(ClientLevel level, LocalPlayer self, boolean called);

    /**
     * Each tick of the client in a level. {@code enabled}: the easter eggs are on and this is
     * the Overworld, or it must go at once; {@code quiet}: it may start by chance.
     */
    void tick(ClientLevel level, LocalPlayer self, boolean enabled, boolean quiet);

    /** Draws what it draws itself, while the easter eggs are on. */
    void submit(PoseStack pose, Vec3 camera, Sink collector, float partial);

    /**
     * Whether the {@link EggCalendar} paces it: true for most; false for one that keeps to
     * dates of its own, which is then told {@code quiet} whenever no other is under way.
     */
    default boolean paced() {
        return true;
    }

    /** Entities of the game it shows, drawn by their own renderers as far figures are; usually none. */
    default List<? extends Entity> figures() {
        return List.of();
    }
}
