package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Which figures share a sheet, and which keep it. Zombies with different names look the
 * same and share one sheet, those with a helmet another, however worn by the sun it is.
 * Then a crowd of zombies, each in a helmet of its own colour, asked for again and again:
 * each is baked once, and the zombies' pages grow to hold them all. Last, a pig met again
 * in another world finds its sheet already baked, and after a reload with the same packs,
 * reads it back from disk: the impostor screen then says so, with the date and the world it
 * was baked in.
 */
public final class ImpostorSharingScenario implements Scenario {
    /** More looks than the largest page of a kind holds: 32. */
    private static final int CROWD = 300;

    @Override
    public String name() {
        return "sharing";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
        });
        ImpostorKey pig;
        ImpostorAtlas.Origin origin;
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "execute at @p run summon pig ~-12 ~ ~20 {NoAI:1b,PersistenceRequired:1b}");
            for (int i = 0; i < 8; i++) {
                String helmet = i % 2 == 0 ? "" : "," + helmet("iron_helmet", "");
                Scene.command(world, "execute at @p run summon zombie ~" + (i * 2 - 7) + " ~ ~20"
                        + " {NoAI:1b,PersistenceRequired:1b,CustomName:'\"Zombie " + i + "\"'" + helmet + "}");
            }
            context.waitTicks(20);
            int keys = context.computeOnClient(minecraft -> {
                Set<ImpostorKey> seen = new HashSet<>();
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() == EntityType.ZOMBIE) seen.add(ImpostorKey.of(entity));
                }
                Scene.log("sharing zombie keys=" + seen.size());
                return seen.size();
            });
            if (keys != 2) throw new AssertionError("Eight zombies, half in a helmet, should share 2 sheets, not " + keys);

            for (int i = 0; i < CROWD; i++) {
                int colour = 0x100000 + i * 0x0F0F;
                Scene.command(world, "execute at @p run summon zombie ~" + (i % 30 * 2 - 30) + " ~ ~" + (30 + i / 30 * 2)
                        + " {NoAI:1b,PersistenceRequired:1b," + helmet("leather_helmet", dyed(colour)) + "}");
            }
            context.waitTicks(20);
            // Asked for once a second, as the figures on hand are, until all are baked.
            for (int round = 0; round < 150; round++) {
                context.runOnClient(ImpostorSharingScenario::requestAll);
                context.waitTicks(20);
                boolean done = context.computeOnClient(minecraft ->
                        ImpostorAtlas.failed() || ImpostorAtlas.waiting() == 0 && ImpostorAtlas.sheets() >= CROWD + 3);
                if (done) break;
            }
            Set<ImpostorKey> before = context.computeOnClient(minecraft -> baked());
            for (int round = 0; round < 5; round++) {
                context.runOnClient(ImpostorSharingScenario::requestAll);
                context.waitTicks(20);
            }
            Set<ImpostorKey> after = context.computeOnClient(minecraft -> baked());
            boolean failed = context.computeOnClient(minecraft -> ImpostorAtlas.failed());
            Scene.log("sharing crowd sheets before=" + before.size() + " after=" + after.size()
                    + " kept=" + after.containsAll(before) + " failed=" + failed);
            if (failed) throw new AssertionError("The impostor bake failed, see the log");
            // New ones may come (a zombie burning in the sun dies: a new look), none may go.
            if (!after.containsAll(before)) throw new AssertionError("The crowd's sheets were thrown away");
            if (after.size() < CROWD + 3) throw new AssertionError("Not every look was baked: " + after.size());
            Scene.screenshotAtlas(context, this);
            pig = context.computeOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() == EntityType.PIG) return ImpostorKey.of(entity);
                }
                throw new AssertionError("No pig");
            });
            ImpostorAtlas.Origin baked = context.computeOnClient(minecraft -> origin(pig));
            Scene.log("sharing pig origin=" + baked);
            if (baked == null || baked.source() != ImpostorAtlas.Source.SAVED || baked.bakedAt() <= 0 || baked.world().isEmpty()) {
                throw new AssertionError("The pig's sheet should be baked in this game and saved, with its date and world: " + baked);
            }
            origin = baked;
            // Gone before the world closes: a crowd left behind upset the next worlds' tests.
            Scene.command(world, "kill @e[type=zombie]");
            context.waitTicks(40);
        }
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "execute at @p run summon pig ~-12 ~ ~20 {NoAI:1b,PersistenceRequired:1b}");
            context.waitTicks(20);
            boolean kept = context.computeOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() != EntityType.PIG) continue;
                    ImpostorKey again = ImpostorKey.of(entity);
                    Scene.log("sharing pig again=" + again.equals(pig) + " baked=" + (ImpostorAtlas.sheet(again) != null));
                    return again.equals(pig) && ImpostorAtlas.sheet(again) != null;
                }
                return false;
            });
            if (!kept) throw new AssertionError("A pig met again in another world was not found baked");

            // As a resource reload with the same packs does: the sheets come back from disk.
            int loads = context.computeOnClient(minecraft -> {
                ImpostorAtlas.invalidate();
                return ImpostorAtlas.loads();
            });
            context.runOnClient(ImpostorSharingScenario::requestAll);
            context.waitTicks(40);
            boolean read = context.computeOnClient(minecraft -> {
                Scene.log("sharing from disk: loads=" + (ImpostorAtlas.loads() - loads)
                        + " pig=" + (ImpostorAtlas.sheet(pig) != null));
                return ImpostorAtlas.loads() > loads && ImpostorAtlas.sheet(pig) != null;
            });
            if (!read) throw new AssertionError("The pig's sheet was not read back from disk");
            ImpostorAtlas.Origin back = context.computeOnClient(minecraft -> origin(pig));
            Scene.log("sharing pig origin from disk=" + back);
            if (back == null || back.source() != ImpostorAtlas.Source.DISK || back.bakedAt() != origin.bakedAt()
                    || !back.world().equals(origin.world())) {
                throw new AssertionError("The pig's sheet should come from disk, baked " + origin + ", not " + back);
            }
            context.setScreen(() -> new ImpostorScreen(null));
            Scene.screenshot(context, this, "screen");
            context.setScreen(() -> null);
        }
    }

    private static void requestAll(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != minecraft.player) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
        }
    }

    private static ImpostorAtlas.@Nullable Origin origin(ImpostorKey key) {
        for (ImpostorAtlas.Baked baked : ImpostorAtlas.baked()) {
            if (baked.key().equals(key)) return baked.origin();
        }
        return null;
    }

    private static Set<ImpostorKey> baked() {
        Set<ImpostorKey> keys = new HashSet<>();
        for (ImpostorAtlas.Baked baked : ImpostorAtlas.baked()) keys.add(baked.key());
        return keys;
    }

    /** A helmet in the summon command's data, as this version writes it. */
    private static String helmet(String item, String components) {
        //? if >=1.21.5 {
        return "equipment:{head:{id:\"minecraft:" + item + "\",count:1" + components + "}}";
        //?} else
        /*return "ArmorItems:[{},{},{},{id:\"minecraft:" + item + "\",count:1" + components + "}]";*/
    }

    private static String dyed(int colour) {
        //? if >=1.21.5 {
        return ",components:{\"minecraft:dyed_color\":" + colour + "}";
        //?} else
        /*return ",components:{\"minecraft:dyed_color\":{rgb:" + colour + "}}";*/
    }
}
