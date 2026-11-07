package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.compat.AmbientBirds;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mobs of other mods this mod knows, by type id, and what it does for them far away.
 *
 * <p>Any mob of another mod is shown far away like the game's own: it is remembered where it
 * was met and walks its loop there. A mob listed here gets more: it flies instead
 * ({@link MobFlight}), and its copy is made ready to be drawn the way that mod draws it
 * (its wings out rather than perched). Supporting another mod's mob is one entry in
 * {@link #KNOWN}; what reaches into that mod lives in {@code compat/}, by reflection, so that
 * nothing here needs it installed.
 */
public final class ModdedMobs {
    /** What this mod knows of one mob of another mod. */
    record Known(@Nullable MobFlight flight, @Nullable Consumer<Entity> prepare) {
    }

    /** Every mob of another mod known here, by type id. */
    private static final Map<String, Known> KNOWN = new HashMap<>();

    static {
        // Ambient Birds (Fabric, 1.21.2 to 1.21.10): birds this client alone sees, which perch
        // round the player and fly off in flocks. Far away they wheel in the sky, wings out.
        for (String bird : List.of("tit", "crow", "jay", "bullfinch", "sparrow", "lark", "seagull", "owl", "woodpecker")) {
            KNOWN.put("ambientbirds:" + bird, new Known(MobFlight.WHEEL, AmbientBirds::flying));
        }
    }

    private ModdedMobs() {
    }

    /** How a mob of another mod flies far away: null for one that walks, or one not known here. */
    static @Nullable MobFlight flight(String type) {
        Known known = KNOWN.get(type);
        return known == null ? null : known.flight();
    }

    /** Makes a copy of a mob of another mod ready to be drawn far away: nothing for one not known here. */
    static void prepare(String type, Entity copy) {
        Known known = KNOWN.get(type);
        if (known != null && known.prepare() != null) known.prepare().accept(copy);
    }
}
