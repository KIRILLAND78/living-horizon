package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.ChangelogScreen;
import fr.clixmods.livinghorizon.ui.DebugScreen;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.LinkScreen;
import fr.clixmods.livinghorizon.ui.ProjectLink;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The icons beside the title of the settings: one per {@link ProjectLink} (Discord, the stores,
 * the website), each opening the game's question about its own link, where cancelling comes back
 * to the settings; then the changelog's, which opens the changelog screen, and Done comes back.
 * The version sits under the title of both screens.
 */
public final class LinksScenario implements Scenario {
    /** Presses the button with the real mouse: the settings keep theirs in a list, which the helper does not search. */
    private static void click(ClientGameTestContext context, String key, int index) {
        int[] at = context.computeOnClient(minecraft -> {
            double scale = minecraft.getWindow().getGuiScale();
            java.util.List<AbstractWidget> found = new java.util.ArrayList<>();
            find(minecraft.screen, key, found);
            if (found.size() <= index) throw new AssertionError("no button " + key + " #" + index);
            AbstractWidget button = found.get(index);
            return new int[]{(int) ((button.getX() + button.getWidth() / 2) * scale),
                    (int) ((button.getY() + button.getHeight() / 2) * scale)};
        });
        context.getInput().setCursorPos(at[0], at[1]);
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
    }

    /** Every button {@code key} names, in the order of the screen. */
    private static void find(ContainerEventHandler parent, String key, java.util.List<AbstractWidget> found) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget && matches(widget, key)) found.add(widget);
            if (child instanceof ContainerEventHandler container) find(container, key, found);
        }
    }

    /** The icon buttons have no text: "" names them, the links first, the changelog's last; any other key is a button's translation key. */
    private static boolean matches(AbstractWidget widget, String key) {
        if (key.isEmpty()) return widget instanceof net.minecraft.client.gui.components.Button && widget.getMessage().getString().isEmpty();
        return widget.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    @Override
    public String name() {
        return "links";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.setScreen(() -> new FarConfigScreen(null));
            context.waitTicks(5);
            Scene.screenshot(context, this, "settings");

            ProjectLink[] links = ProjectLink.values();
            for (int i = 0; i < links.length; i++) {
                ProjectLink expected = links[i];
                click(context, "", i);
                context.waitTicks(5);
                ProjectLink opened = context.computeOnClient(minecraft ->
                        minecraft.screen instanceof LinkScreen screen ? screen.link() : null);
                if (opened != expected) {
                    throw new AssertionError("logo " + i + " opened " + opened + ", not " + expected);
                }
                Scene.screenshot(context, this, "link-" + expected.key);

                click(context, "gui.cancel", 0);
                context.waitTicks(5);
                if (!context.computeOnClient(minecraft -> minecraft.screen instanceof FarConfigScreen)) {
                    throw new AssertionError("cancelling the " + expected.key + " question did not come back to the settings");
                }
            }

            click(context, "", links.length);
            context.waitTicks(5);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof ChangelogScreen)) {
                throw new AssertionError("the changelog button did not open the changelog");
            }
            Scene.screenshot(context, this, "changelog");

            click(context, "gui.done", 0);
            context.waitTicks(5);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof FarConfigScreen)) {
                throw new AssertionError("Done on the changelog did not come back to the settings");
            }

            context.setScreen(() -> new DebugScreen(null));
            context.waitTicks(5);
            Scene.screenshot(context, this, "debug");
            context.setScreen(() -> null);
        }
    }
}
