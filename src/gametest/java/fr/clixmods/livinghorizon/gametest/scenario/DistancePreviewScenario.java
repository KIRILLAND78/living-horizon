package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.DistancePreview;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.Nullable;

/**
 * The distance settings drawn in the world behind the settings screen: hovering one shows
 * what it measures (a sphere round the player, the chunks a scan reads, the birds' lowest
 * height), and hovering anything else takes it away. Two stone walls stand on the right of
 * the view, at 70 and 140 blocks: the first hides the 128-block sphere, which stands in front
 * of the second.
 */
public final class DistancePreviewScenario implements Scenario {
    @Override
    public String name() {
        return "distancepreview";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Facing south, the right of the screen, beside the settings, is to the west.
            Scene.command(world, "forceload add -80 50 -10 150");
            context.waitTicks(40);
            Scene.command(world, "fill -70 -60 70 -40 -48 70 stone");
            Scene.command(world, "fill -50 -60 140 -15 -36 140 stone");
            Scene.configure(context, c -> {
                c.mobMaxDistance = 160;
                c.scanRadius = 6;
                c.birdMaxDistance = 200;
            });
            context.waitTicks(40);

            context.setScreen(() -> new FarConfigScreen(null));
            context.waitTicks(5);
            Scene.screenshot(context, this, "none");
            expect(context, false, "before anything is hovered");

            hover(context, "impostorDistance");
            expect(context, true, "over Simplified Beyond");
            Scene.screenshot(context, this, "impostor-128");

            // Down the list in its order: a row not drawn since the list moved keeps its old place.
            hover(context, "scanRadius");
            expect(context, true, "over the scan radius");
            Scene.screenshot(context, this, "scan-6-chunks");

            hover(context, "mobMaxDistance");
            Scene.screenshot(context, this, "mobs-160");

            hover(context, "birdMinHeight");
            expect(context, true, "over the birds' height");
            Scene.screenshot(context, this, "birds-30-above");

            // Over the world, away from the settings: the preview fades out.
            context.getInput().setCursorPos(window(context, 700), window(context, 240));
            context.waitTicks(10);
            expect(context, false, "with the mouse over the world");

            // From above, looking down: where the sphere meets the ground is the distance.
            Scene.command(world, "tp @a 0 -30 0 0 40");
            context.waitTicks(40);
            hover(context, "offlineRoam");
            expect(context, true, "over the roaming radius");
            Scene.screenshot(context, this, "roam-50-above");

            context.setScreen(() -> null);
            context.waitTicks(10);
            expect(context, false, "once the screen is closed");
        }
    }

    private static void expect(ClientGameTestContext context, boolean shown, String when) {
        boolean showing = context.computeOnClient(minecraft -> DistancePreview.showing());
        Scene.log("distancepreview: " + (showing ? "shown" : "not shown") + " " + when);
        if (showing != shown) throw new AssertionError("The distance preview is " + (showing ? "" : "not ") + "shown " + when);
    }

    /** Scrolls the settings until the slider of this setting is in view, and puts the mouse over it. */
    private static void hover(ClientGameTestContext context, String setting) {
        context.runOnClient(minecraft -> list(minecraft).setScrollAmount(0));
        context.waitTicks(2);
        for (int page = 0; page < 30; page++) {
            int[] where = context.computeOnClient(minecraft -> {
                AbstractSelectionList<?> list = list(minecraft);
                AbstractWidget slider = find(list, I18n.get("livinghorizon.options." + setting));
                if (slider != null && slider.getY() > list.getY() + 4 && slider.getY() + slider.getHeight() < list.getBottom() - 4) {
                    return new int[]{slider.getX() + slider.getWidth() / 2, slider.getY() + slider.getHeight() / 2};
                }
                list.setScrollAmount(list.scrollAmount() + 60);
                return null;
            });
            if (where != null) {
                context.getInput().setCursorPos(window(context, where[0]), window(context, where[1]));
                context.waitTicks(3);
                return;
            }
            context.waitTicks(1);
        }
        throw new AssertionError("No slider for " + setting + " in view");
    }

    private static AbstractSelectionList<?> list(Minecraft minecraft) {
        for (GuiEventListener child : minecraft.screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) return list;
        }
        throw new AssertionError("The settings screen has no list");
    }

    /** The widget whose text starts with the setting's name, in a row drawn this frame. */
    private static @Nullable AbstractWidget find(ContainerEventHandler parent, String caption) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(caption + ":")) return widget;
            if (child instanceof ContainerEventHandler container) {
                AbstractWidget found = find(container, caption);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static double window(ClientGameTestContext context, double gui) {
        return context.computeOnClient(minecraft -> gui * minecraft.getWindow().getGuiScale());
    }
}
