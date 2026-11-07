package fr.clixmods.livinghorizon.compat;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the links to other mods share ({@link VoxyLink}, {@link DhLink}, {@link LodistanceLink},
 * {@link FarViewLink}, {@link EcstaticLink}, {@link IrisLink}): looking up what older or newer
 * versions may not have, and keeping a word of what each link found, for {@code /livinghorizon
 * lod}. A missing piece the mod can do without is a fallback: the link notes it once, and the
 * line in {@code lod} says it.
 */
public final class Links {
    private Links() {
    }

    /** One link, as {@code /livinghorizon lod} says it. */
    public record Report(String mod, String status) {
    }

    /**
     * Each link: linked or not, the fallbacks it runs on, and how often its optional hooks ran:
     * a hook into another mod that finds nothing to hook says nothing, and never ran then.
     */
    public static List<Report> report() {
        return List.of(new Report("Voxy", VoxyLink.status()), new Report("Distant Horizons", DhLink.status()),
                new Report("Lodistance", LodistanceLink.status()), new Report("Far View", FarViewLink.status()),
                new Report("Ecstatic", EcstaticLink.status()),
                new Report("Iris", IrisLink.status()), new Report("Fog on distant figures", FarFog.status()));
    }

    /** A class some versions do not have: null there. */
    static @Nullable Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** A public method some versions do not have: null there. */
    static @Nullable Method optionalMethod(@Nullable Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) return null;
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** The first of several names a public method of no arguments went by: null when none is there. */
    static @Nullable Method firstMethod(Class<?> owner, String... names) {
        for (String name : names) {
            Method method = optionalMethod(owner, name);
            if (method != null) return method;
        }
        return null;
    }

    /** A public field some versions do not have: null there. */
    static @Nullable Field optionalField(@Nullable Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** A method some versions do not have, as a handle: null there. */
    static @Nullable MethodHandle optional(MethodHandles.Lookup lookup, Class<?> owner, String name, MethodType type)
            throws IllegalAccessException {
        try {
            return lookup.findVirtual(owner, name, type);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** What one link found: linked or not, and the fallbacks it runs on. */
    static final class Status {
        private String state = "not looked at yet";
        private final Set<String> fallbacks = new LinkedHashSet<>();

        synchronized void set(String state) {
            this.state = state;
        }

        /** Something the mod does without on this version, said once however often it is noted. */
        synchronized void fallback(String what) {
            fallbacks.add(what);
        }

        /** {@code "linked"}, {@code "linked, fallbacks: …"}, {@code "not installed"}, or why it is off. */
        synchronized String describe() {
            return fallbacks.isEmpty() ? state : state + ", fallbacks: " + String.join("; ", fallbacks);
        }
    }
}
