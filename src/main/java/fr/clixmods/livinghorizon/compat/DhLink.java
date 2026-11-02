package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What {@link DhDepth} (its depth) and {@link DhSource} (its world) share of Distant Horizons'
 * API: {@code DhApi$Delayed}, where its parts appear once it has started, and {@code DhApiResult},
 * what most of its calls answer. Each of the two links the rest of what it needs itself, and
 * notes here what it does without on this version ({@link #fallback}), for one line in
 * {@code /livinghorizon lod}.
 */
final class DhLink {
    static final String API = "com.seibel.distanthorizons.api.";
    private static final Links.Status STATUS = new Links.Status();
    /** How often {@code DhClientApiMixin} ran: never, with Distant Horizons drawing, means its fade was renamed. */
    private static final AtomicLong FADE_HOOK = new AtomicLong();
    private static boolean ready, linked;

    private static Class<?> delayed, result;
    private static MethodHandle success, payload, message;

    private DhLink() {
    }

    /** Whether Distant Horizons is there, in a version whose API this mod knows. */
    static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!Platform.isModLoaded("distanthorizons")) {
            STATUS.set("not installed");
            return false;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            delayed = Class.forName(API + "DhApi$Delayed");
            result = Class.forName(API + "objects.DhApiResult");
            success = lookup.findGetter(result, "success", boolean.class).asType(MethodType.methodType(boolean.class, Object.class));
            payload = lookup.findGetter(result, "payload", Object.class).asType(MethodType.methodType(Object.class, Object.class));
            message = lookup.findGetter(result, "message", String.class).asType(MethodType.methodType(String.class, Object.class));
            linked = true;
            STATUS.set("linked");
        } catch (ReflectiveOperationException | LinkageError e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Distant Horizons found, but not the version this mod knows", e);
        }
        return linked;
    }

    /** {@code DhApiResult}. */
    static Class<?> resultType() {
        return result;
    }

    /**
     * A static field of {@code DhApi$Delayed} as a handle that gives it as an {@code Object}:
     * Distant Horizons fills them in once it has started, so it is read at every use.
     */
    static MethodHandle delayed(String field, Class<?> type) throws ReflectiveOperationException {
        return MethodHandles.publicLookup().findStaticGetter(delayed, field, type).asType(MethodType.methodType(Object.class));
    }

    static boolean success(Object answer) throws Throwable {
        return (boolean) success.invokeExact(answer);
    }

    static Object payload(Object answer) throws Throwable {
        return (Object) payload.invokeExact(answer);
    }

    static String message(Object answer) throws Throwable {
        return (String) message.invokeExact(answer);
    }

    /** Something {@link DhDepth} or {@link DhSource} does without on this version. */
    static void fallback(String what) {
        STATUS.fallback(what);
    }

    /** Called by {@code DhClientApiMixin}: its fade hook still finds its methods. */
    static void fadeHooked() {
        FADE_HOOK.incrementAndGet();
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        linked();
        if (!linked) return STATUS.describe();
        return STATUS.describe() + "; fade hook called " + FADE_HOOK.get() + " times";
    }
}
