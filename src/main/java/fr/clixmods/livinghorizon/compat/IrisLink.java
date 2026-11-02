package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the mod asks Iris, in one place: whether a shader pack is on, whether its shadow
 * pass is drawing, the planes it draws Distant Horizons with, and the render scale of the pack.
 * Reached by reflection, so that the mod loads without it. Oculus, Iris's port to Forge, keeps
 * Iris's classes under another mod id: the classes are looked for, never the mod id, or a pack
 * on Forge reads as no pack.
 *
 * <p>Only {@code isShaderPackInUse} is needed; anything else missing is a fallback, said in
 * {@code /livinghorizon lod}.
 */
final class IrisLink {
    private static final Links.Status STATUS = new Links.Status();
    private static boolean linked;
    private static @Nullable Object api;
    private static @Nullable Method packInUse, shadowPass;
    /** {@code DHCompat}: the planes Iris draws Distant Horizons' terrain with. */
    private static @Nullable Method dhNear, dhFar;
    /** The current pack's options, for its render scale. */
    private static @Nullable Method currentPack, packOptions, optionValues, optionSet, booleanOption, stringOption;
    private static @Nullable Method booleanOptions, stringOptions;
    /** The pack the scale was read from, and the scale: Iris builds a new pack when an option changes. */
    private static @Nullable Object scaledPack;
    private static float scale = 1f;

    private IrisLink() {
    }

    private static synchronized void link() {
        if (linked) return;
        linked = true;
        Class<?> type = Links.optionalClass("net.irisshaders.iris.api.v0.IrisApi");
        if (type == null) {
            STATUS.set("not installed");
            return;
        }
        try {
            Object instance = type.getMethod("getInstance").invoke(null);
            packInUse = type.getMethod("isShaderPackInUse");
            api = instance;
            STATUS.set("linked");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            packInUse = null;
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Iris found, but not the version this mod knows: shader packs are read as off", e);
            return;
        }
        shadowPass = Links.optionalMethod(type, "isRenderingShadowPass");
        if (shadowPass == null) STATUS.fallback("no shadow pass flag");
        Class<?> compat = Links.optionalClass("net.irisshaders.iris.compat.dh.DHCompat");
        dhNear = Links.optionalMethod(compat, "getNearPlane");
        dhFar = Links.optionalMethod(compat, "getFarPlane");
        if (dhNear == null || dhFar == null) {
            dhNear = dhFar = null;
            STATUS.fallback("no Distant Horizons planes: its depth read as without a pack");
        }
        Class<?> values = Links.optionalClass("net.irisshaders.iris.shaderpack.option.values.OptionValues");
        Class<?> set = Links.optionalClass("net.irisshaders.iris.shaderpack.option.OptionSet");
        currentPack = Links.optionalMethod(Links.optionalClass("net.irisshaders.iris.Iris"), "getCurrentPack");
        packOptions = Links.optionalMethod(Links.optionalClass("net.irisshaders.iris.shaderpack.ShaderPack"), "getShaderPackOptions");
        optionValues = Links.optionalMethod(Links.optionalClass("net.irisshaders.iris.shaderpack.option.ShaderPackOptions"), "getOptionValues");
        optionSet = Links.optionalMethod(values, "getOptionSet");
        booleanOption = Links.optionalMethod(values, "getBooleanValueOrDefault", String.class);
        stringOption = Links.optionalMethod(values, "getStringValueOrDefault", String.class);
        booleanOptions = Links.optionalMethod(set, "getBooleanOptions");
        stringOptions = Links.optionalMethod(set, "getStringOptions");
        if (currentPack == null || packOptions == null || optionValues == null || optionSet == null || booleanOption == null
                || stringOption == null || booleanOptions == null || stringOptions == null) {
            currentPack = null;
            STATUS.fallback("no pack options: render scale read as 1");
        }
    }

    /** Whether Iris (or Oculus) is there, in a version this mod knows. */
    static boolean installed() {
        link();
        return packInUse != null;
    }

    /** Whether Iris draws with a shader pack. */
    static boolean shaderPackOn() {
        link();
        if (packInUse == null) return false;
        try {
            return (boolean) packInUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether Iris is drawing its shadow pass. From 26.2 that pass draws its entities through
     * the same steps as the world's, after the world's are submitted.
     */
    static boolean shadowPass() {
        link();
        if (shadowPass == null) return false;
        try {
            return (boolean) shadowPass.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * {@code {near, far}} of the projection Iris draws Distant Horizons' terrain with while a
     * shader pack is on; null without Iris, without a pack, or when its planes are not known yet.
     */
    static float @Nullable [] dhPlanes() {
        if (!shaderPackOn() || dhNear == null || dhFar == null) return null;
        try {
            float near = (float) dhNear.invoke(null), far = (float) dhFar.invoke(null);
            return near > 0.0f && far > near ? new float[]{near, far} : null;
        } catch (Throwable e) {
            dhNear = dhFar = null;
            STATUS.fallback("Distant Horizons planes failed: its depth read as without a pack");
            LivingHorizonClient.LOGGER.warn("Iris did not answer as expected: Distant Horizons' depth is read as without a shader pack", e);
            return null;
        }
    }

    /**
     * The fraction of the screen a shader pack draws the world to, from the bottom left corner:
     * Photon's TAAU shrinks it in its vertex shaders and stretches it afterwards. Voxy is told
     * through the pack's {@code voxy.json}; Distant Horizons is not, so the pack's own option is
     * read, once per pack. 1 without a pack, or with a pack that draws to all of the screen.
     */
    static float packScale() {
        if (!shaderPackOn() || currentPack == null) return 1f;
        try {
            Object pack = ((Optional<?>) currentPack.invoke(null)).orElse(null);
            if (pack == null) return 1f;
            if (pack == scaledPack) return scale;
            scaledPack = pack;
            scale = 1f;
            Object values = optionValues.invoke(packOptions.invoke(pack));
            Object set = optionSet.invoke(values);
            // Asked for an option it does not have, Iris logs a warning and answers true.
            if (!((Map<?, ?>) booleanOptions.invoke(set)).containsKey("TAAU")
                    || !((Map<?, ?>) stringOptions.invoke(set)).containsKey("TAAU_RENDER_SCALE")
                    || !(boolean) booleanOption.invoke(values, "TAAU")) return scale;
            float read = Float.parseFloat((String) stringOption.invoke(values, "TAAU_RENDER_SCALE"));
            if (read >= 0.25f && read <= 1f) scale = read;
            return scale;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1f;
        }
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        link();
        return STATUS.describe();
    }
}
