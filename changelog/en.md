# Living Horizon — Changelog

## Unreleased

### Client

#### New
- **Offline players looking back** — look at a player who logged off and they look back at you; crouch while they face you and they crouch too.
- **Offline players fishing** — players who logged off may stop at the water's edge to fish for a while, one at a time on a stretch of shore.
- **Pushing offline players** — walk into a player who logged off and you push them out of your way; they never push you.

#### Improved
- **Offline players at night** — players who logged off no longer lie down on the ground at night: they sleep in a bed they can reach, or stay on their feet.

#### Fixed
- **Offline players frozen far away** — players who logged off no longer stand still once you walk away: where there is no room for their walk, they take a step or two, turn round and sit down now and then.

## 0.10.0 — 2026-10-20

### Server

#### New
- **Mod on a server** — put the jar in the server's `mods` folder, on Fabric, Quilt, NeoForge or Forge (plus the Fabric API on Fabric and Quilt). Nothing else to set up. It replaces the data pack.
- **Player positions** — the server sends where every player is, five times a second, to the players who have the mod. It also sends their skin, armour, held items and mount.
- **Mobs** — the server sends the mobs within 2,048 blocks of each player, even ones they never met: animals, villagers, golems, allays and boats, with their saddle, harness, armour or chest.
- **Players who logged off** — the server keeps where each player logged off, how they looked, their mount and when they left, and sends it to everyone, even players who join later.
- **Players from before the mod** — the first time it runs, the server reads the players the world already had (the `playerdata` folder, their names from `usercache.json`) and shows them as logged off, where they left, in what they wore.
- **World file** — the server saves all of this in `livinghorizon-positions.dat`, in the world folder. A mob is forgotten when its chunk loads and it is no longer there.
- **LAN worlds** — a world opened to LAN shares everything through the host's game. Nothing to install.
- **Players without the mod** — they can still join. The server sends them nothing.
- **Same mod on both sides** — keep the mod up to date on the server and on the players' games: a game that cannot read the server's messages ignores them.
- **Positions are shared** — the server shares every player's position with all the players who have the mod. Tell your players.
- **No more data pack** — the mod no longer reads it: remove it from the world's `datapacks` folder. Nothing goes through the scoreboard any more, so coordinates are no longer readable in scores or in the sidebar.

#### Fixed
- **Players vanishing when a world closes** — when a server or a LAN world closes, the players in it now stay where they were as offline players.

### Client

#### New
- **Every player at any distance** — on a server with the mod, you see all the other players, with their skin, armour, held items and mount.
- **Mobs you never met** — on a server with the mod, distant mobs show at once, even ones you never walked near, with their saddle, harness, armour or chest.
- **Players who logged off, even before you joined** — you see them where they left, with how they looked and what they rode.
- **Your own world** — in single player, players who logged off in your world show as soon as you open it, even those from before the mod.
- **Everything as you join** — distant mobs and players who logged off show as soon as you join, not a few seconds later.
- **Servers without the mod** — you can still join. No distant player is shown, only the mobs you met, and the log says so once.

#### Improved
- **Distant players' positions** — they come from what the server shares only. The locator bar guess is gone, and so is the *Lost Player Time* setting.

#### Fixed
- **Players on a happy ghast** — a far player riding a happy ghast no longer has a second ghast beside them, and one already riding when you join no longer floats in the air.
- **Happy ghast flying back after its rider gets off** — the happy ghast a far player gets off no longer reappears where it was first ridden and flies back: it stays where it was left.
- **Old skins far away** — a distant player who changes skin no longer keeps the old one far away.
- **Players turned sideways in boats** — a player who logs off in a boat no longer sits turned to the side: they face the way the boat does.
- **Riders sitting inside their horse** — a distant player riding a horse no longer sits inside it or on its back end, as a full model or as a flat picture.
- **Horse shown twice after its rider logs off** — a player who logs off on a horse no longer leaves a second copy of the horse beside the one they rest on.
- **Greyed-out Website and Issues buttons** — the Website and Issues buttons on the mod's page in Mod Menu are no longer greyed out (Fabric).

## 0.9.0 — 2026-10-19

### Fixed
- **Offline players standing in the water** — players who logged off while swimming are no longer left standing in the water: they wait on the nearest shore, or row a boat when the shore is more than 10 blocks away.
- **Water vanishing around boats** — the water no longer turns clear around offline players rowing a boat under a shader pack such as Photon, nor disappears in a large square around far boats.
- **Rowers losing their boat far away** — far away, offline players rowing a boat are no longer shown standing without it while their picture is made.

## 0.8.0 — 2026-10-18

### New
- **Simplified Players setting** — keeps distant players as full models while distant mobs still turn into flat pictures far away.
- **12 new languages** — German, Spanish, Italian, Brazilian Portuguese, Polish, Turkish, Russian, Ukrainian, Simplified and Traditional Chinese, Japanese and Korean.
- **Settings in Sodium's menu** — Living Horizon's settings also appear in Sodium's *Video Settings*, on pages of their own (Sodium 0.8 and newer).
- **Offline players keep their mount** — players who logged off on a horse, a boat or a happy ghast keep riding it around where they left.
- **Video memory settings** — the *Simplified Entities...* screen shows how much video memory the pictures of mobs and players use, and *Mob Picture Memory* and *Player Picture Memory* set a limit for each.

### Improved
- **Clearer settings** — every setting has a clearer name and description, in the style of Minecraft's own options, and distance sliders use the full width.
- **Offline players** — players who logged off walk a small loop around where they left, and turn into flat pictures far away like online players, whether standing, sitting, lying down or in bed.
- **Riders** — far away, a player riding a horse, a boat, a minecart or anything else becomes one flat picture with their mount, which is much lighter than two full models.
- **Large crowds of players** — with more than about a hundred distant players, the extra ones now turn into flat pictures too instead of staying full models.

### Fixed
- **Mobs showing through hills after resizing the window** — distant mobs no longer show through hills, Voxy's and Distant Horizons' included, after the game window changes size.
- **Players and slimes vanishing with Distant Horizons** — distant players, offline players and slimes no longer disappear under Distant Horizons' terrain (Minecraft 26.1 and newer).
- **Distant Horizons' terrain cutting into mobs** — Distant Horizons' terrain no longer cuts into mobs and players standing in front of it (Minecraft 26.1 and newer).
- **Distant mobs blinking while walking** — with view bobbing on, distant mobs no longer blink and Distant Horizons' terrain no longer flickers in front of them at every step (Minecraft 26.1 and newer).

## 0.7.0 — 2026-10-17

### New
- **Offline players come to life** — players who logged off live around where they left: they stroll, sit, look at chests and workstations, go to bed at night, and raise a shield when hit or when monsters are near. They stay on the ground: break the block under them and they fall, bury them and they climb back up.
- **Offline names** — players who logged off show *(offline)* after their name, and looking at one up close tells when they were last seen. Settings can hide their names, or the players themselves.
- **3D birds** — birds are now small 3D models like Minecraft's mobs, with flapping wings and legs when perched, and turn into flat pictures far away. The *Bird Style* setting brings back the old 2D birds.
- **Hmm…** — I thought I saw something in the sky. 😉

### Improved
- **Hopping mobs** — distant slimes, magma cubes and rabbits now hop instead of sliding, slimes squash when they land, and chickens flap their wings when stepping down a block.
- **Ducks** — ducks now fly off together when you come near and land farther along the water, instead of leaving for good.

### Fixed
- **Mobs frozen through a spyglass** — distant mobs seen through a spyglass now count at their zoomed size, so they keep walking when they look big (*Min. Animated Size* setting).
- **Mobs turning on the spot when opening a world** — distant mobs now walk and graze from the start.
- **Perched birds acting strangely** — tits, robins and ducks no longer dart back and forth, float in the air, stand inside leaves or swim on dry land, and ducks no longer appear right next to you only to fly away. Ducks now swim around their pond, and robins and tits hop more and fly off less.
- **Wrong license in the mod list** — Living Horizon no longer shows the wrong license in the mod list (Forge, NeoForge and Quilt).

## 0.6.0 — 2026-10-15

### New
- **Mob pictures saved between sessions** — the flat pictures of far mobs are saved on disk and reused the next time you play; they are only made again after you change resource packs, mods or game version.

### Improved
- **New mod icon** — a new pixel-art icon: Steve in front, with mobs, villagers, players and birds all the way to the mountains behind him.
- **Distant mobs when you come back** — in single player, distant mobs much less often teleport or run to another spot as you come back: they follow the real mobs while the game still moves them, and disappear when those die.

### Fixed
- **Flickering in the Simplified Entities screen** — two mobs with the same name, such as villagers in different outfits, no longer swap places and flicker.
- **Dark lines on far mobs** — far mobs drawn as flat pictures no longer show thin dark lines or black specks along their top or bottom edge, and long mobs such as horses and sniffers are no longer cut off at the muzzle or tail.
- **Crash with GeckoLib mobs** — a world no longer crashes a few seconds after loading when a mob from a GeckoLib mod was seen far away (Minecraft 1.21.2 to 1.21.8). These mobs are not shown far away yet.
- **Game slowing down with simplified entities** — the game no longer slows down when far mobs are drawn as flat pictures: the same mobs are no longer pictured over and over, and mobs that look alike share one picture.

## 0.5.0 — 2026-10-13

### New
- **Simplified entities on Minecraft 1.20.1 to 1.21.8** — very far mobs and players can now be drawn as flat pictures on these versions too, which is much lighter for your computer.

### Fixed
- **Crash on startup** — the game no longer crashes when it starts with the mod installed (Forge, Minecraft 1.20.1 to 1.20.4).
- **Crash in the Simplified Entities screen** — opening it no longer crashes the game (Minecraft 1.21.8 and older).
- **Mobs missing after joining a world** — mobs that were already around you when you joined now show on the horizon too, not only the ones you meet later.

## 0.4.2 — 2026-10-12

### Fixed
- **Mobs glitching as they walk away** — a mob that walks out of your normal view no longer vanishes for a moment or turns to face another way.
- **Parts of mobs showing through hills** — a villager's clothes, a held item or a slime's outer layer no longer show through Voxy's or Distant Horizons' hills while the rest of the mob is hidden (Minecraft 1.21.9 and newer, with Distant Horizons, or with Voxy and a shader pack).

## 0.4.1 — 2026-10-11

### Fixed
- **Far mobs too dark with shaders** — far mobs drawn as flat pictures are no longer too dark with Iris shader packs such as Photon or Complementary: the sun now lights them like full models.
- **Terrain shadows on distant mobs** — distant mobs no longer show the shadow of the terrain behind them, and Voxy's water looks as it should (Photon with Voxy).
- **Distant mobs showing through hills** — distant mobs no longer show through Voxy's or Distant Horizons' hills (Minecraft 26.1; Minecraft 26.2 and newer with a shader pack, older Iris versions included; Minecraft 26.3 with Iris installed).
- **Voxy 0.2.18 not recognised** — the mod works with Voxy 0.2.18 again: distant mobs stand on its terrain and no longer show through its hills.
- **Distant mobs hidden by the wrong hills** — distant mobs are no longer hidden by hills that are not in front of them (Distant Horizons with Photon's upscaling, TAAU).

## 0.4.0 — 2026-10-10

### New
- **Automatic first scan** — the first time you enter a single player world, the mobs within 32 chunks are found at once, so the horizon is alive from the start. The scan radius is now set in chunks, like the render distance.
- **Animation settings** — two new sliders stop the animations of distant mobs that are too small on screen or too far away, to save performance.
- **Quality presets** — Low, Normal, High and Ultra choose in one click how many distant mobs and birds you see, and how far. High and Ultra are for powerful computers.

### Fixed
- **Distant mobs turning black** — without shaders, distant mobs no longer turn black for a moment, mostly while flying.

## 0.3.2 — 2026-10-09

### Fixed
- **Photon** — with Voxy and Photon's upscaling (TAAU) on, distant mobs hide behind the right hills again.

## 0.3.1 — 2026-10-08

### Fixed
- **Minecraft 1.20 to 1.21.4** — distant mobs now hide behind Voxy's and Distant Horizons' hills, as on newer versions.
- **Older Distant Horizons** — works with Distant Horizons 2.3 and newer, not only the latest version.

## 0.3.0 — 2026-10-07

### New
- **Vulkan** — with the game's Vulkan renderer, distant mobs hide behind hills too, Distant Horizons included.

### Fixed
- **Distant Horizons without shaders** — distant mobs no longer fade into the terrain.
- **Distant Horizons** — no more smears where the normal render distance ends.

## 0.2.0 — 2026-10-07

### New
- **Distant mobs** — animals, villagers and golems stay alive on the horizon, past your render distance, grazing and walking around.
- **Distant players** — other players keep walking far away, with their skin, armour and mount. Players who log off stay asleep where they left.
- **Birds** — flocks, geese in a V, gulls over the coast, bats at night.
- **Distant Horizons support** — works with Distant Horizons as well as Voxy.
- **Hidden behind hills** — distant mobs behind a hill are not drawn, which keeps the game fast.
- **Impostors** (off by default) — very far mobs and players are drawn as flat pictures, much lighter with many of them.
- **Scan the chunks around** — in single player, mobs from the chunks around you show up on the horizon right away.
- **Settings** — a "Living Horizon..." button in the Options menu, with the world still visible behind it, and a button to restore the defaults.
- **Quilt, NeoForge and Forge** — the mod also runs on Quilt, NeoForge and Forge, on Minecraft 1.20 to 26.3.
- **A logo** in the mod list and the data pack list.
