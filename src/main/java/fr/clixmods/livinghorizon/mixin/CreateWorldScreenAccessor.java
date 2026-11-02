package fr.clixmods.livinghorizon.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The world creation screen's "Create", pressed by {@code BenchmarkWorld} once it has set the world up. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Invoker("onCreate")
    void livinghorizon$create();
}
