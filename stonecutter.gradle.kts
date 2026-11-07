plugins {
    id("dev.kikugie.stonecutter")
}

// The version `src/` is currently preprocessed for. Kept on 1.21.11, the version the
// mod is being tested against in-game.
stonecutter active "1.21.11"

stonecutter parameters {
    // The loader is the second axis of the tree, next to the game version: a node
    // named "<version>-neoforge" builds for NeoForge, "<version>-quilt" for Quilt,
    // "<version>-forge" for Forge, every other one for Fabric. Sources branch on it with
    // `//? if fabric {`, `//? if quilt {`, `//? if neoforge {` and `//? if forge {`.
    val nodeName = node.metadata.project
    val loader = when {
        nodeName.endsWith("-neoforge") -> "neoforge"
        nodeName.endsWith("-quilt") -> "quilt"
        nodeName.endsWith("-forge") -> "forge"
        else -> "fabric"
    }
    constants.match(loader, "fabric", "quilt", "neoforge", "forge")

    node.project.findProperty("deps.fabric_api")?.let { dependencies["fapi"] = it as String }

    // Sodium's config API, from Sodium 0.8, on the targets whose table names a version of it:
    // `//? if sodium {` keeps the class that adds the mod's pages to Sodium's screen.
    constants["sodium"] = node.project.findProperty("deps.sodium") != null

    // Mojang renamed ResourceLocation to Identifier in 1.21.11 and changed nothing else
    // about it. A pure rename used in many files is a replacement, not a conditional in
    // each of them: the sources say Identifier, and older targets read ResourceLocation.
    replacements.regex(current.parsed >= "1.21.11") {
        replace("\\bResourceLocation\\b", "Identifier", "\\bIdentifier\\b", "ResourceLocation")
    }

    // Forge before 56 (1.21.6) has one event bus for the game's events, where each event
    // has its own from there: a listener names the event in its parameter instead.
    replacements.regex(loader == "forge" && current.parsed < "1.21.6") {
        replace("\\b([A-Z][\\w.]*)\\.BUS\\.addListener\\(event ->", "net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(($1 event) ->",
                "\\bnet\\.minecraftforge\\.common\\.MinecraftForge\\.EVENT_BUS\\.addListener\\(\\(([A-Z][\\w.]*) event\\) ->", "$1.BUS.addListener(event ->")
    }

    // Names 1.20.3 gave things that were already there.
    replacements.regex(current.parsed < "1.20.3") {
        replace("\\bBlocks\\.SHORT_GRASS\\b", "Blocks.GRASS", "\\bBlocks\\.GRASS\\b", "Blocks.SHORT_GRASS")
        replace("\\bnet\\.minecraft\\.commands\\.functions\\.CommandFunction\\b", "net.minecraft.commands.CommandFunction",
                "\\bnet\\.minecraft\\.commands\\.CommandFunction\\b", "net.minecraft.commands.functions.CommandFunction")
    }

    // What 1.20.5 changed, which the sources use. Java 21 came with it (Math.clamp), and an
    // entity data accessor became a record.
    replacements.regex(current.parsed < "1.20.5") {
        replace("\\bMath\\.clamp\\(", "net.minecraft.util.Mth.clamp(", "\\bnet\\.minecraft\\.util\\.Mth\\.clamp\\(", "Math.clamp(")
        replace("\\b(DATA_[A-Z_]+|accessor)\\.id\\(\\)", "$1.getId()", "\\b(DATA_[A-Z_]+|accessor)\\.getId\\(\\)", "$1.id()")
        replace("\\bLivingEntity\\.DATA_EFFECT_PARTICLES\\b", "LivingEntity.DATA_EFFECT_COLOR_ID",
                "\\bLivingEntity\\.DATA_EFFECT_COLOR_ID\\b", "LivingEntity.DATA_EFFECT_PARTICLES")
    }

    // What 1.21 changed. The game's frame timer became a DeltaTracker, an Identifier was made
    // with its constructor, the options screens had no package of their own, and a vertex
    // was written with other names and ended by endVertex().
    replacements.regex(current.parsed < "1.21") {
        replace("\\.get(?:DeltaTracker|Timer)\\(\\)\\.getGameTimeDeltaPartialTick\\(true\\)", ".getFrameTime()",
                "\\.getFrameTime\\(\\)", ".getDeltaTracker().getGameTimeDeltaPartialTick(true)")
        // One pass: what a replacement writes is not read again, so this one writes the old name.
        replace("\\bIdentifier\\.fromNamespaceAndPath\\(", "new ResourceLocation(",
                "\\bnew ResourceLocation\\(", "Identifier.fromNamespaceAndPath(")
        // Before 1.20.5 a vertex took the pose's matrices, not the pose.
        if (current.parsed < "1.20.5") {
            replace("\\.addVertex\\(at, ", ".vertex(at.pose(), ", "\\.vertex\\(at\\.pose\\(\\), ", ".addVertex(at, ")
        } else {
            replace("\\.addVertex\\(", ".vertex(", "\\.vertex\\(", ".addVertex(")
        }
        replace("\\.setColor\\(", ".color(", "\\.color\\(", ".setColor(")
        replace("\\.setUv\\(", ".uv(", "\\.uv\\(", ".setUv(")
        replace("\\.setOverlay\\(", ".overlayCoords(", "\\.overlayCoords\\(", ".setOverlay(")
        replace("\\.setLight\\(", ".uv2(", "\\.uv2\\(", ".setLight(")
        if (current.parsed < "1.20.5") {
            replace("\\.setNormal\\(at, ([^;]*)\\);", ".normal(at.normal(), $1).endVertex();",
                    "\\.normal\\(at\\.normal\\(\\), ([^;]*)\\)\\.endVertex\\(\\);", ".setNormal(at, $1);")
        } else {
            replace("\\.setNormal\\(([^;]*)\\);", ".normal($1).endVertex();", "\\.normal\\(([^;]*)\\)\\.endVertex\\(\\);", ".setNormal($1);")
        }
        replace("\\bnet\\.minecraft\\.client\\.gui\\.screens\\.options\\.OptionsScreen\\b", "net.minecraft.client.gui.screens.OptionsScreen",
                "\\bnet\\.minecraft\\.client\\.gui\\.screens\\.OptionsScreen\\b", "net.minecraft.client.gui.screens.options.OptionsScreen")
    }

    // Names 1.21.2 gave things that were already there.
    replacements.regex(current.parsed < "1.21.2") {
        replace("\\.getDeltaTracker\\(\\)", ".getTimer()", "\\.getTimer\\(\\)", ".getDeltaTracker()")
        replace("\\.getMinY\\(\\)", ".getMinBuildHeight()", "\\.getMinBuildHeight\\(\\)", ".getMinY()")
        // No render states yet: the mod's own stand-ins, of the same names, in their place.
        replace("\\bnet\\.minecraft\\.client\\.renderer\\.entity\\.state\\.(EntityRenderState|LivingEntityRenderState|HumanoidRenderState|BoatRenderState);",
                "fr.clixmods.livinghorizon.render.state.$1;",
                "\\bfr\\.clixmods\\.livinghorizon\\.render\\.state\\.(EntityRenderState|LivingEntityRenderState|HumanoidRenderState|BoatRenderState);",
                "net.minecraft.client.renderer.entity.state.$1;")
    }

    // Names 1.21.5 gave things that were already there.
    replacements.regex(current.parsed < "1.21.5") {
        replace("\\.isBrightOutside\\(\\)", ".isDay()", "\\.isDay\\(\\)", ".isBrightOutside()")
        replace("\\bTagParser\\.parseCompoundFully\\(", "TagParser.parseTag(", "\\bTagParser\\.parseTag\\(", "TagParser.parseCompoundFully(")
    }

    // The camera answers position() from 1.21.6, where it became a waypoint's point of view;
    // before, getPosition() alone.
    replacements.regex(current.parsed < "1.21.6") {
        replace("\\b(camera|getMainCamera\\(\\))\\.position\\(\\)", "$1.getPosition()",
                "\\b(camera|getMainCamera\\(\\))\\.getPosition\\(\\)", "$1.position()")
    }

    // What 1.21.9 moved. The sources use the new place; the older targets read the old one.
    replacements.regex(current.parsed < "1.21.9") {
        replace("\\bimport net\\.minecraft\\.world\\.entity\\.Avatar;", "import net.minecraft.world.entity.player.Player; // Avatar until 1.21.9",
                "\\bimport net\\.minecraft\\.world\\.entity\\.player\\.Player; // Avatar until 1\\.21\\.9", "import net.minecraft.world.entity.Avatar;")
        replace("\\bAvatar\\.DATA_PLAYER_MODE_CUSTOMISATION\\b", "Player.DATA_PLAYER_MODE_CUSTOMISATION",
                "\\bPlayer\\.DATA_PLAYER_MODE_CUSTOMISATION\\b", "Avatar.DATA_PLAYER_MODE_CUSTOMISATION")
    }

    // Names 1.21.11 changed and nothing else about them. The sources use the new ones, and
    // the older targets read the old: a rename used in a dozen places is a replacement, not
    // a conditional in each of them. The second pair is the one an older target reads; the
    // first never matches there, and does nothing on a target that has the new names.
    replacements.regex(current.parsed >= "1.21.11") {
        replace("\\bIN_F3\\b", "IN_OVERLAY", "\\bIN_OVERLAY\\b", "IN_F3")
        replace("\\.getLookVector\\(\\)", ".forwardVector()", "\\.forwardVector\\(\\)", ".getLookVector()")
        replace("\\.location\\(\\)", ".identifier()", "\\.identifier\\(\\)", ".location()")
        replace("\\bnet\\.minecraft\\.Util\\b", "net.minecraft.util.Util", "\\bnet\\.minecraft\\.util\\.Util\\b", "net.minecraft.Util")
        replace("\\bnet\\.minecraft\\.client\\.renderer\\.RenderType\\b", "net.minecraft.client.renderer.rendertype.RenderType",
                "\\bnet\\.minecraft\\.client\\.renderer\\.rendertype\\.RenderType\\b", "net.minecraft.client.renderer.RenderType")
        replace("\\bLH_NEVER_A\\b", "LH_NEVER_B", "\\bnet\\.minecraft\\.client\\.renderer\\.rendertype\\.RenderTypes\\b", "net.minecraft.client.renderer.RenderType")
        replace("\\bLH_NEVER_A\\b", "LH_NEVER_B", "\\bRenderTypes\\b", "RenderType")
        replace("\\bnet\\.minecraft\\.world\\.entity\\.npc\\.AbstractVillager\\b", "net.minecraft.world.entity.npc.villager.AbstractVillager",
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.villager\\.AbstractVillager\\b", "net.minecraft.world.entity.npc.AbstractVillager")
        replace("\\bnet\\.minecraft\\.world\\.entity\\.vehicle\\.AbstractBoat\\b", "net.minecraft.world.entity.vehicle.boat.AbstractBoat",
                "\\bnet\\.minecraft\\.world\\.entity\\.vehicle\\.boat\\.AbstractBoat\\b", "net.minecraft.world.entity.vehicle.AbstractBoat")
        replace("\\bnet\\.minecraft\\.world\\.entity\\.vehicle\\.AbstractMinecart\\b", "net.minecraft.world.entity.vehicle.minecart.AbstractMinecart",
                "\\bnet\\.minecraft\\.world\\.entity\\.vehicle\\.minecart\\.AbstractMinecart\\b", "net.minecraft.world.entity.vehicle.AbstractMinecart")
        replace("\\bnet\\.minecraft\\.world\\.entity\\.animal\\.Parrot\\b", "net.minecraft.world.entity.animal.parrot.Parrot",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.parrot\\.Parrot\\b", "net.minecraft.world.entity.animal.Parrot")
        replace("\\bnet\\.minecraft\\.world\\.entity\\.animal\\.Chicken\\b", "net.minecraft.world.entity.animal.chicken.Chicken",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.chicken\\.Chicken\\b", "net.minecraft.world.entity.animal.Chicken")
        replace("\\bnet\\.minecraft\\.world\\.entity\\.animal\\.Rabbit\\b", "net.minecraft.world.entity.animal.rabbit.Rabbit",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.rabbit\\.Rabbit\\b", "net.minecraft.world.entity.animal.Rabbit")
    }

    // 26.1 renamed what draws the GUI, and the methods that go with it, and moved the
    // render states into a package of their own. Same arrangement as above: the sources
    // use the names of 1.21.11, and 26.1 and later read the new ones.
    replacements.regex(current.parsed >= "26.1") {
        replace("\\bvoid render\\(GuiGraphics\\b", "void extractRenderState(GuiGraphicsExtractor",
                "\\bvoid extractRenderState\\(GuiGraphicsExtractor\\b", "void render(GuiGraphics")
        replace("\\bmethod = \"renderContent\"", "method = \"extractContent\"", "\\bmethod = \"extractContent\"", "method = \"renderContent\"")
        replace("\\bvoid renderBackground\\(GuiGraphics\\b", "void extractBackground(GuiGraphicsExtractor",
                "\\bvoid extractBackground\\(GuiGraphicsExtractor\\b", "void renderBackground(GuiGraphics")
        replace("\\bsuper\\.render\\(", "super.extractRenderState(", "\\bsuper\\.extractRenderState\\(", "super.render(")
        replace("\\bsuper\\.renderBackground\\(", "super.extractBackground(", "\\bsuper\\.extractBackground\\(", "super.renderBackground(")
        replace("\\bGuiGraphics\\b", "GuiGraphicsExtractor", "\\bGuiGraphicsExtractor\\b", "GuiGraphics")
        replace("\\.drawString\\(", ".text(", "\\.text\\(", ".drawString(")
        replace("\\.drawCenteredString\\(", ".centeredText(", "\\.centeredText\\(", ".drawCenteredString(")
        replace("\\.renderItem\\(", ".item(", "\\.item\\(", ".renderItem(")
        replace("\\bnet\\.minecraft\\.client\\.renderer\\.state\\.CameraRenderState\\b", "net.minecraft.client.renderer.state.level.CameraRenderState",
                "\\bnet\\.minecraft\\.client\\.renderer\\.state\\.level\\.CameraRenderState\\b", "net.minecraft.client.renderer.state.CameraRenderState")
        replace("\\bnet\\.minecraft\\.client\\.renderer\\.state\\.LevelRenderState\\b", "net.minecraft.client.renderer.state.level.LevelRenderState",
                "\\bnet\\.minecraft\\.client\\.renderer\\.state\\.level\\.LevelRenderState\\b", "net.minecraft.client.renderer.state.LevelRenderState")
        replace("net/minecraft/client/renderer/state/CameraRenderState\\b", "net/minecraft/client/renderer/state/level/CameraRenderState",
                "net/minecraft/client/renderer/state/level/CameraRenderState\\b", "net/minecraft/client/renderer/state/CameraRenderState")
        replace("net/minecraft/client/renderer/state/LevelRenderState\\b", "net/minecraft/client/renderer/state/level/LevelRenderState",
                "net/minecraft/client/renderer/state/level/LevelRenderState\\b", "net/minecraft/client/renderer/state/LevelRenderState")
        replace("\\bnet\\.minecraft\\.client\\.renderer\\.LightTexture\\b", "net.minecraft.util.LightCoordsUtil",
                "\\bnet\\.minecraft\\.util\\.LightCoordsUtil\\b", "net.minecraft.client.renderer.LightTexture")
        replace("\\bLightTexture\\b", "LightCoordsUtil", "\\bLightCoordsUtil\\b", "LightTexture")
    }

    // 26.2 moved what the screen, the chat and the overlay are from the game to its GUI, gave
    // the game renderer's getters record-style names, and the entity types their own class.
    // The sources keep the names of 1.21.11; 26.2 and later read the new ones.
    replacements.regex(current.parsed >= "26.2") {
        replace("\\b(minecraft|client)\\.setScreen\\(", "$1.gui.setScreen(", "\\b(minecraft|client)\\.gui\\.setScreen\\(", "$1.setScreen(")
        replace("\\b(minecraft|client)\\.screen\\b(?!\\.v[0-9])", "$1.gui.screen()", "\\b(minecraft|client)\\.gui\\.screen\\(\\)", "$1.screen")
        replace("\\.getOverlay\\(\\)", ".gui.overlay()", "\\.gui\\.overlay\\(\\)", ".getOverlay()")
        replace("\\.gui\\.getChat\\(\\)", ".gui.hud.getChat()", "\\.gui\\.hud\\.getChat\\(\\)", ".gui.getChat()")
        replace("\\.getMainCamera\\(\\)", ".mainCamera()", "\\.mainCamera\\(\\)", ".getMainCamera()")
        replace("\\.getMainRenderTarget\\(\\)", ".gameRenderer.mainRenderTarget()", "\\.gameRenderer\\.mainRenderTarget\\(\\)", ".getMainRenderTarget()")
        replace("\\bminecraft\\.renderBuffers\\(\\)", "minecraft.gameRenderer.renderBuffers()", "\\bminecraft\\.gameRenderer\\.renderBuffers\\(\\)", "minecraft.renderBuffers()")
        replace("\\.getLighting\\(\\)", ".lighting()", "\\.lighting\\(\\)", ".getLighting()")
        replace("\\.getFeatureRenderDispatcher\\(\\)", ".featureRenderDispatcher()", "\\.featureRenderDispatcher\\(\\)", ".getFeatureRenderDispatcher()")
        replace("\\bEntityType\\.([A-Z][A-Z_]+)\\b", "net.minecraft.world.entity.EntityTypes.$1",
                "\\bnet\\.minecraft\\.world\\.entity\\.EntityTypes\\.([A-Z][A-Z_]+)\\b", "EntityType.$1")
    }

    // 26.3 moved the GPU abstraction out of blaze3d into a library of its own, renderpearl:
    // the classes kept their names, in new packages.
    replacements.regex(current.parsed >= "26.3") {
        replace("\\bcom\\.mojang\\.blaze3d\\.buffers\\.(GpuBuffer|GpuBufferSlice)\\b", "com.mojang.renderpearl.api.buffers.$1",
                "\\bcom\\.mojang\\.renderpearl\\.api\\.buffers\\.(GpuBuffer|GpuBufferSlice)\\b", "com.mojang.blaze3d.buffers.$1")
        replace("\\bcom\\.mojang\\.blaze3d\\.systems\\.CommandEncoder\\b", "com.mojang.renderpearl.api.commands.CommandEncoder",
                "\\bcom\\.mojang\\.renderpearl\\.api\\.commands\\.CommandEncoder\\b", "com.mojang.blaze3d.systems.CommandEncoder")
        replace("\\bcom\\.mojang\\.blaze3d\\.systems\\.GpuDevice\\b", "com.mojang.renderpearl.api.device.GpuDevice",
                "\\bcom\\.mojang\\.renderpearl\\.api\\.device\\.GpuDevice\\b", "com.mojang.blaze3d.systems.GpuDevice")
        replace("\\bcom\\.mojang\\.blaze3d\\.textures\\.(\\w+)", "com.mojang.renderpearl.api.textures.$1",
                "\\bcom\\.mojang\\.renderpearl\\.api\\.textures\\.(\\w+)", "com.mojang.blaze3d.textures.$1")
        replace("\\bcom\\.mojang\\.blaze3d\\.GpuFormat\\b", "com.mojang.renderpearl.api.GpuFormat",
                "\\bcom\\.mojang\\.renderpearl\\.api\\.GpuFormat\\b", "com.mojang.blaze3d.GpuFormat")
        replace("\\bcom\\.mojang\\.blaze3d\\.opengl\\.GlTexture\\b", "com.mojang.renderpearl.backend.opengl.GlTexture",
                "\\bcom\\.mojang\\.renderpearl\\.backend\\.opengl\\.GlTexture\\b", "com.mojang.blaze3d.opengl.GlTexture")
        // And a quaternion turns a pose with rotate, the keyboard is no longer GLFW's.
        replace("\\bpose\\.mulPose\\(", "pose.rotate(", "\\bpose\\.rotate\\(", "pose.mulPose(")
        replace("\\bInputConstants\\.Type\\.KEYSYM\\b", "InputConstants.Type.KEYBOARD", "\\bInputConstants\\.Type\\.KEYBOARD\\b", "InputConstants.Type.KEYSYM")
    }
}
