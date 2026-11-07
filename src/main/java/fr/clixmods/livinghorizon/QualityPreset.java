package fr.clixmods.livinghorizon;

import java.util.Locale;

/**
 * Ready-made settings for how far and how many: {@link #NORMAL} is the defaults, the others
 * trade the cost of drawing more and farther for a fuller horizon. They set only the
 * settings that scale that cost; everything else stays as the player chose it. None of them
 * goes without a limit: that is for a player who chose it, and was asked first.
 */
public enum QualityPreset {
    LOW(64, 128, 256, 50, 400),
    /** Read from the defaults themselves, so that it is always what a fresh install has. */
    NORMAL(new FarConfig()),
    HIGH(256, 1500, 1024, 200, 1500),
    ULTRA(512, 3000, 2048, 400, 3000);

    private final int impostorDistance, maxDistantMobs, mobMaxDistance, birdDensity, birdMaxDistance;

    QualityPreset(int impostorDistance, int maxDistantMobs, int mobMaxDistance, int birdDensity, int birdMaxDistance) {
        this.impostorDistance = impostorDistance;
        this.maxDistantMobs = maxDistantMobs;
        this.mobMaxDistance = mobMaxDistance;
        this.birdDensity = birdDensity;
        this.birdMaxDistance = birdMaxDistance;
    }

    QualityPreset(FarConfig defaults) {
        this(defaults.impostorDistance, defaults.maxDistantMobs, defaults.mobMaxDistance, defaults.birdDensity,
                defaults.birdMaxDistance);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public void apply(FarConfig c) {
        c.impostorDistance = impostorDistance;
        c.maxDistantMobs = maxDistantMobs;
        c.mobMaxDistance = mobMaxDistance;
        c.birdDensity = birdDensity;
        c.birdMaxDistance = birdMaxDistance;
    }

    public boolean matches(FarConfig c) {
        return c.impostorDistance == impostorDistance && c.maxDistantMobs == maxDistantMobs
                && c.mobMaxDistance == mobMaxDistance && c.birdDensity == birdDensity
                && c.birdMaxDistance == birdMaxDistance;
    }

    /** The preset the settings are on, or null when they match none (a custom setup). */
    public static QualityPreset of(FarConfig c) {
        for (QualityPreset preset : values()) if (preset.matches(c)) return preset;
        return null;
    }

    /** The next one up, round to the lowest; a custom setup goes to {@link #NORMAL}. */
    public static QualityPreset next(FarConfig c) {
        QualityPreset current = of(c);
        if (current == null) return NORMAL;
        return values()[(current.ordinal() + 1) % values().length];
    }
}
