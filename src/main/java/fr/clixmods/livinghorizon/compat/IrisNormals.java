package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Hands Iris the normal maps of the mod's own textures. Iris looks a texture's normal and
 * specular maps up by its class, the first time it is bound under a shader pack: a loader is
 * registered for the impostors' page textures, which answers with the page's normal map.
 * A pack that reads normal maps (Photon's <em>Normal Mapping</em>, labPBR packs) then lights
 * each picture as it would the model's sides.
 *
 * <p>This is Iris's own loader registry, not its public API: reached by reflection, in the
 * packages of Iris and of older Oculus, and nothing happens without either. The loader is an
 * interface of Iris's, made with a {@link Proxy}.
 */
public final class IrisNormals {
    private static final String[] PACKAGES = {"net.irisshaders.iris.pbr.loader", "net.coderbot.iris.texture.pbr.loader"};
    private static final Set<Class<?>> REGISTERED = new HashSet<>();
    private static boolean failed;
    /** How many times Iris asked for a normal map, and how many it was given: for the game tests. */
    private static int asked, given;

    private IrisNormals() {
    }

    /** Whether the shader pack on reads normal maps: only then are the impostors' baked for it. */
    public static boolean packReadsNormalMaps() {
        return IrisLink.readsNormalMaps();
    }

    /** How many normal maps Iris was handed. */
    public static int given() {
        return given;
    }

    /** {@code asked/given}: how often Iris asked for a normal map, and how often it got one. */
    public static String counts() {
        return asked + "/" + given;
    }

    /**
     * Registers {@code normals} as the loader of {@code type}'s normal maps, once per class: it
     * is asked for the normal map of a texture of that class, null for none.
     */
    public static synchronized void register(Class<?> type, Function<Object, @Nullable Object> normals) {
        if (failed || REGISTERED.contains(type)) return;
        for (String pack : PACKAGES) {
            Class<?> registry = Links.optionalClass(pack + ".PBRTextureLoaderRegistry");
            Class<?> loader = Links.optionalClass(pack + ".PBRTextureLoader");
            Class<?> consumer = Links.optionalClass(pack + ".PBRTextureLoader$PBRTextureConsumer");
            if (registry == null || loader == null || consumer == null) continue;
            try {
                Object instance = registry.getField("INSTANCE").get(null);
                Method register = registry.getMethod("register", Class.class, loader);
                Method accept = null;
                for (Method method : consumer.getMethods()) {
                    if (method.getName().equals("acceptNormalTexture")) accept = method;
                }
                if (accept == null) throw new NoSuchMethodException("acceptNormalTexture");
                Method acceptNormal = accept;
                Object proxy = Proxy.newProxyInstance(loader.getClassLoader(), new Class<?>[]{loader}, (self, method, args) -> {
                    switch (method.getName()) {
                        case "load" -> {
                            Object map = normals.apply(args[0]);
                            asked++;
                            if (map != null) {
                                acceptNormal.invoke(args[2], map);
                                given++;
                            }
                            return null;
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(self);
                        }
                        case "equals" -> {
                            return self == args[0];
                        }
                        case "toString" -> {
                            return "Living Horizon normal maps";
                        }
                        default -> {
                            return null;
                        }
                    }
                });
                register.invoke(instance, type, proxy);
                REGISTERED.add(type);
                LivingHorizonClient.LOGGER.info("Impostors: normal maps handed to Iris for {}", type.getName());
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failed = true;
                LivingHorizonClient.LOGGER.warn("Iris found, but not the version this mod knows: impostors get no normal maps", e);
                return;
            }
        }
    }
}
