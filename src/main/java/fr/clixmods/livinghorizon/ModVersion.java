package fr.clixmods.livinghorizon;

import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The version of the mod, the same on every loader: the build writes it into
 * {@code assets/livinghorizon/version.txt}, which the loaders' own metadata cannot give
 * everywhere (Forge and NeoForge offer no common way to read it at this level).
 */
public final class ModVersion {
    private static String version;

    private ModVersion() {}

    /** "0.10.0", or "?" when the file is missing (a development run before the resources are built). */
    public static synchronized String get() {
        if (version == null) {
            String read = "";
            try (InputStream in = ModVersion.class.getResourceAsStream("/assets/" + LivingHorizonClient.MOD_ID + "/version.txt")) {
                if (in != null) read = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException ignored) {
                // Shown as unknown.
            }
            version = read.isEmpty() || read.startsWith("$") ? "?" : read;
        }
        return version;
    }

    /** "v0.10.0", shown under the title of the mod's screens, which already name the mod. */
    public static Component label() {
        return Component.literal("v" + get());
    }
}
