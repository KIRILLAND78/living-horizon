package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.Profile;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What each section costs, as lines of text: for the debug panel, the F3 screen and {@code /livinghorizon}. */
public final class ProfileLines {
    /** Sections that run several times in a frame: how many times is worth knowing. */
    private static final Set<Profile.Section> COUNTED = EnumSet.of(Profile.Section.BAKE,
            Profile.Section.BAKE_READ_BACK, Profile.Section.BAKE_STORE);
    private static final long IDLE_SECONDS = 5;
    private static final double SLOW_NANOS = 2_000_000, VERY_SLOW_NANOS = 8_000_000;

    /** A line and how bad its worst time is: 0 fine, 1 slow, 2 very slow. */
    public record Line(String content, int severity) {
    }

    private ProfileLines() {
    }

    /** The lines of every section that ran once, in the order of the sections. Reading them asks for the GPU's time. */
    public static List<Line> lines() {
        ProfileHook.watch();
        long now = System.nanoTime();
        List<Line> lines = new ArrayList<>();
        for (Profile.Section section : Profile.Section.values()) {
            Profile.Stat cpu = section.cpu();
            if (!cpu.seen()) continue;
            long worst = cpu.worst(now);
            StringBuilder text = new StringBuilder(I18n.get("livinghorizon.debug.hud.section", I18n.get(section.langKey()),
                    Profile.millis(cpu.last()), Profile.millis(cpu.average()), Profile.millis(worst)));
            Profile.Stat gpu = section.gpuTime();
            if (section.gpu() && gpu.seen() && gpu.idleSeconds(now) < IDLE_SECONDS) {
                text.append(I18n.get("livinghorizon.debug.hud.sectionGpu", Profile.millis(gpu.last()),
                        Profile.millis(gpu.worst(now))));
            }
            if (COUNTED.contains(section)) {
                text.append(I18n.get("livinghorizon.debug.hud.sectionCount", String.valueOf(cpu.perFrame())));
            }
            // The worst of five seconds is empty when nothing ran in them: say so, rather than a last time beside a worst of zero.
            long idle = cpu.idleSeconds(now);
            if (idle >= IDLE_SECONDS) {
                text.append(I18n.get("livinghorizon.debug.hud.sectionIdle", String.valueOf(idle)));
            }
            if (cpu.bytes() >= 0) {
                text.append(I18n.get("livinghorizon.debug.hud.sectionBytes",
                        String.format(Locale.ROOT, "%.1f", cpu.bytes() / 1024.0)));
            }
            lines.add(new Line(text.toString(), worst >= VERY_SLOW_NANOS ? 2 : worst >= SLOW_NANOS ? 1 : 0));
        }
        double allocated = Allocation.kilobytesPerTick();
        if (allocated >= 0) {
            lines.add(new Line(I18n.get("livinghorizon.debug.hud.alloc", String.format(Locale.ROOT, "%.1f", allocated)), 0));
        }
        return lines;
    }
}
