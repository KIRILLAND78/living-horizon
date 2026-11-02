package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.resources.Identifier;

/**
 * The places where the mod lives outside the game: its Discord server, its pages on the three
 * stores and its website. Each is a logo beside the title of the mod's screen, its tooltip
 * {@code livinghorizon.options.<name>.tooltip}, and opens through {@link LinkScreen}. Only Discord
 * is also a button on Sodium's page.
 */
public enum ProjectLink {
    DISCORD("discord", "https://discord.gg/THSV9dexNj"),
    MODRINTH("modrinth", "https://modrinth.com/mod/living-horizon"),
    CURSEFORGE("curseforge", "https://www.curseforge.com/minecraft/mc-mods/living-horizon"),
    NEXUSMODS("nexusmods", "https://www.nexusmods.com/minecraft/mods/1376"),
    WEBSITE("website", "https://mc-skin-creator.github.io/living-horizon-website/#download");

    /** The last part of its lang keys and the name of its 16 by 16 logo. */
    public final String key;
    public final String url;
    final Identifier icon;

    ProjectLink(String key, String url) {
        this.key = key;
        this.url = url;
        this.icon = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/gui/" + key + ".png");
    }
}
