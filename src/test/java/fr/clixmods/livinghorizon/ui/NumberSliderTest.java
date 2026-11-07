package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.QualityPreset;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberSliderTest {
    private static NumberSlider.Range range(int min, int max, boolean unlimited) {
        return new NumberSlider.Range(Component.literal("Max Mobs"), min, max, (caption, v) -> Component.literal("" + v),
                unlimited, true);
    }

    @Test
    void readsTypedNumbersAsTheyAre() {
        NumberSlider.Range range = range(0, 4000, true);
        assertEquals(1234, range.parse("1234"));
        assertEquals(4000, range.parse("4000"));
        // Past the slider's last number, the number typed: never no limit.
        assertEquals(6000, range.parse("6000"));
        assertEquals(999999999, range.parse("999999999"));
        assertEquals(0, range.parse("0"));
        assertTrue(range.beyond(6000));
        assertFalse(range.beyond(4000));
        assertFalse(range.beyond(NumberSlider.UNLIMITED));
    }

    @Test
    void takesDigitsOnly() {
        NumberSlider.Range range = range(0, 4000, true);
        assertNull(range.parse("unlimited"));
        assertNull(range.parse("12a"));
        assertNull(range.parse("-5"));
        assertNull(range.parse("1 234"));
        assertNull(range.parse(""));
        assertNull(range.parse("1234567890"));
    }

    @Test
    void bringsNumbersIntoARangeWithoutNoLimit() {
        NumberSlider.Range range = range(32, 2000, false);
        assertEquals(2000, range.parse("5000"));
        assertEquals(32, range.parse("1"));
        assertFalse(range.beyond(5000));
    }

    @Test
    void slidesBackToTheSameValue() {
        NumberSlider.Range range = range(1, 4000, true);
        for (int v : new int[]{1, 2, 512, 2048, 4000, NumberSlider.UNLIMITED}) assertEquals(v, range.fromSlider(range.toSlider(v)));
        assertEquals(1, range.fromSlider(0));
        assertEquals(NumberSlider.UNLIMITED, range.fromSlider(1));
        // A number past the end sits at the last place, before no limit.
        assertEquals(range.toSlider(4000), range.toSlider(6000));
        NumberSlider.Range bounded = range(32, 2000, false);
        assertEquals(2000, bounded.fromSlider(1));
    }

    @Test
    void asksForEverySettingWithNoLimit() {
        for (String setting : new String[]{"maxDistantMobs", "maxDistantEntities", "maxOfflinePlayers",
                "playerMaxDistance", "mobMaxDistance", "animationMaxDistance", "entityMaxDistance"}) {
            assertTrue(NumberSlider.asksBeforeUnlimited(setting), setting);
        }
        assertFalse(NumberSlider.asksBeforeUnlimited("impostorDistance"));
        assertFalse(NumberSlider.asksBeforeUnlimited("foreseeRadius"));
    }

    @Test
    void noDefaultGoesWithoutALimit() {
        FarConfig defaults = new FarConfig();
        assertTrue(defaults.maxDistantMobs < FarConfig.UNLIMITED_MOBS);
        assertTrue(defaults.maxDistantEntities < FarConfig.UNLIMITED_MOBS);
        assertTrue(defaults.maxOfflinePlayers < FarConfig.UNLIMITED_OFFLINE);
        for (int distance : new int[]{defaults.playerMaxDistance, defaults.mobMaxDistance,
                defaults.animationMaxDistance, defaults.entityMaxDistance}) {
            assertTrue(distance > 0, "a distance with no limit by default");
        }
    }

    @Test
    void noPresetGoesWithoutALimit() {
        for (QualityPreset preset : QualityPreset.values()) {
            FarConfig config = new FarConfig();
            preset.apply(config);
            assertTrue(config.maxDistantMobs < FarConfig.UNLIMITED_MOBS, preset + " has no limit on mobs");
            assertTrue(config.mobMaxDistance > 0, preset + " has no limit on distance");
        }
    }
}
