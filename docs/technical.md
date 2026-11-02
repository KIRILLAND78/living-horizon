# Living Horizon: technical notes

How the mod works, for whoever works on it: what each part does, where it lives in the code,
the debug tools, the commands and every setting. Players want the [README](../README.md).

Code paths are relative to `src/main/java/fr/clixmods/livinghorizon/`. Pitfalls that depend
on the game version, on Voxy's or Distant Horizons' version, or on shader packs are listed in
the *Debugging checklist* of [CLAUDE.md](../CLAUDE.md).

## Contents

- [Overview](#overview)
- [Distant players](#distant-players)
- [Sharing positions from the server](#sharing-positions-from-the-server)
- [Players who logged off](#players-who-logged-off)
- [Distant mobs](#distant-mobs)
- [Birds](#birds)
- [Impostors](#impostors)
- [Far terrain depth](#far-terrain-depth)
- [Skipping hidden mobs](#skipping-hidden-mobs)
- [Debug tools](#debug-tools)
- [Commands](#commands)
- [Settings reference](#settings-reference)

## Overview

Living Horizon draws life past the render distance, on top of the far terrain of Voxy or
Distant Horizons. Everything is drawn on the client. A server with the mod only shares
positions with the clients that have it.

| Part | What is drawn | Where it comes from |
|---|---|---|
| [Distant players](#distant-players) | Online players past the server's view distance | Positions shared by a server with the mod |
| [Players who logged off](#players-who-logged-off) | Players at the spot they left, living around it | This client's memory and the server's file |
| [Distant mobs](#distant-mobs) | Mobs out of range, playing an animation in a loop | This client's memory, or the server's |
| [Birds](#birds) | Flocks, perched birds, ducks, bats, parrots | Made up on the client from the terrain |
| [Impostors](#impostors) | Flat pictures in place of far models | Baked by the game's own renderers |
| [Far terrain depth](#far-terrain-depth) | Nothing of its own: hides our figures behind the far terrain | Voxy's or Distant Horizons' depth |

The figures are **puppets**: entities the mod makes and draws with the game's own renderers,
never added to a level (`track/Puppets`).

### Code map

| Package | What is in it |
|---|---|
| root | `LivingHorizonCommon` (entry point on both sides, opens the network channel), `LivingHorizonClient` (client entry point), `FarConfig` (the settings file), `QualityPreset`, `Stats` |
| `track/` | What is followed and remembered: online players (`FarPlayer`, `FarPlayerTracker`), players who logged off (`RestingPlayers`, `RestingPuppet`, `RestingLoop`, `RestingMount`, `Walkway`, `Footing`), mobs (`MobMemory`, `MobPaths`, `MobAnimations`, `MobMotion`, `MobGait`, `MobScan`, `ServerMobs`), what the server shares (`PositionPublisher`, `PositionsMessage`, `SharedPositions`, `WorldPlayers`) |
| `render/` | Adding the puppets to the frame (`GhostRenderer`, `Sink`), the far plane (`DepthFar`), `Occlusion`; `impostor/` for the impostors; `state/` for the render state stand-ins used before 1.21.2 |
| `compat/` | Voxy and Distant Horizons: reading their world (`LodWorld`, `VoxySource`, `DhSource`) and their depth (`FarDepth`, `FarDepthGpu`, `DhDepth`, `OcclusionQueries`) |
| `ambient/` | The birds (`Ambience`, `BirdModels`, `BirdFigure`, `AmbientRenderer`) and the easter eggs |
| `platform/` | What differs between loaders: events, network, config folder |
| `ui/` | The settings screens (`FarConfigScreen` and its sub-screens, `SodiumOptions`), the impostor screen, the text under the crosshair (`OfflineHud`) |
| `debug/` | The debug panel, boxes and F3 lines |
| `command/` | `/livinghorizon` |
| `mixin/` | The hooks into the game |

Code a dedicated server loads (`LivingHorizonCommon`, `platform/Network`,
`track/PositionPublisher`, `mixin/MinecraftServerMixin` and what they use) never touches a
client class.

## Distant players

| Distance from you | What the client knows | What is drawn |
|---|---|---|
| Inside the server's view distance | The entity itself | The real player (the mod only keeps their mount from being culled) |
| Past it | The position a server with the mod shares | A copy, at that position |
| Past it, nothing shared | Nothing | Nobody |

- **Drawn by the game's own renderer** (`render/GhostRenderer`). The copy's render state is
  extracted like a real player's and added to the same list, so skins, capes, elytras,
  armour, mounts and shader packs all work.
- **Drawn where they really are, at their true size**, so terrain in front of them hides
  them. Voxy writes its terrain into the game's depth buffer and removes the distance fog;
  for Distant Horizons and shader packs, see [Far terrain depth](#far-terrain-depth).
- **The far clipping plane** (four times the render distance) is pushed out past the farthest
  figure drawn the frame before, players, mobs and birds alike (`extendFarPlane`): a tenth
  farther and 64 blocks more, up to 65536 blocks. A figure still near or past the plane (95%
  of it) is drawn closer along the same line of sight and smaller by the same ratio: the same
  picture, but in front of the terrain between.
- **Mounts.** A player is drawn on the mob they ride (`FarPlayer.mountPuppet`), and the
  remembered mobs leave that mob out (`MobMemory.carried`), so it is never drawn twice.

What breaks it:

- No shared positions (no mod on the server or the LAN host): no copy at all.
- Another dimension: the copy is hidden until they are back in yours.
- A player who was **never** close to you is drawn with the skin and gear the server shares,
  standing until the server's copy of their mount arrives (every five seconds).

## Sharing positions from the server

A server with the mod tells the clients that have the mod where everyone is
(`track/PositionPublisher`). It uses the mod's own network channel,
`livinghorizon:positions` (`platform/Network`, on the loader's networking: Fabric's payloads,
a named channel before 1.20.5; NeoForge's; Forge's channels, its `NetworkRegistry` before
1.20.2). Nothing goes through the scoreboard or any other part of the
world, and a player without the mod is sent nothing.

### What is sent, and when

- **Five times a second**, to every player online: each player's position, yaw, dimension
  and which mob they ride (a vehicle not in `track/MobKinds.TYPES` only as "riding"), so that
  a far player is drawn on their own mount.
- **Every five seconds**, to every player online:
  - how the 64 players nearest them look, online or logged off (skin, armour and what they
    hold, read every second), and what they ride, as the server saves it;
  - the players who logged off: where they were last and when they left, with how they
    looked and the mount they took away for those among the 64;
  - the remembered mobs within 2048 blocks of them, with what they wear (saddle, harness,
    armour, carpet, chest; see [Distant mobs](#distant-mobs)).
- **At once, to a player who joins**: all of it, without waiting for the next five seconds.

The messages (`track/PositionsMessage`) are written with plain Java streams, the same on
every loader and version. The client keeps what it receives in `track/SharedPositions`.

### Where it runs

- **A world played alone, or opened to LAN**: the server runs inside the host's game, which
  reads its messages back like any other client. Players who logged off show as soon as the
  world opens.
- **A dedicated server**: the same jar in the server's `mods` folder (with the Fabric API on
  Fabric and Quilt).

`mixin/MinecraftServerMixin` drives it from the start of the server's tick, so that what it
sends goes out when that tick flushes the connections.

### The server's file

The server keeps the players and the mobs in `livinghorizon-positions.dat`, at the root of
its world folder, so that a player who joins later sees them too.

- It is saved every minute and when the server stops.
- Players still online when the server stops are kept as they were, as if they logged off
  then.
- A mob is forgotten when its chunk is loaded and it is not there any more, and the oldest
  go past 4000 mobs.
- Each time the server starts, its first share also adds the players the world saved that
  the file does not have yet (`track/WorldPlayers`: the 1000 most recent in `playerdata`,
  with their position and gear, named from `usercache.json`, last seen when their file was
  written, no skin), so players from before the mod show as logged off at once.
- Test players placed by the host with `/livinghorizon debug offline test` are shared too,
  marked as tests: the other clients show them while the host keeps them, and nobody saves
  them.

## Players who logged off

A player who logs off stays where they were last, until they come back. It is all a picture
on this client: nothing is sent to the server, they block nobody, and nothing can hurt them.

### Where they come from

- Remembered per server, across sessions, in `config/livinghorizon/resting/<server>.json`
  (`track/RestingPlayers`).
- On a server with the mod, even players who logged off before you joined are there: the
  server keeps everyone's last position, skin and gear. Without the server's word, their skin
  is fetched from Mojang by name (from 1.21.9; before, the default skin).
- When the world or the server closes, or you leave it, everyone you saw stays where they
  were last, as they looked, on what they rode: as far as this client knows, they logged off
  then.
- `offlinePlayers` off turns all of it off: nobody is shown or remembered.

### Their life, near the camera

With `offlineLife` on (the default), they live around their spot (`track/RestingPuppet`),
within `offlineRoam` blocks of it (50, about a village; up to 128). This runs only near the
camera and where the chunk they stand in is loaded: it stops past 64 blocks and starts again
under 48.

- **By day** they stand and look around, sit down, stroll, or stand in front of a chest, a
  crafting table or a villager's workstation and look at it.
- **At night** they walk to a free bed they can reach and sleep in it. With none, they stay
  on their feet, looking around, and look for one again every 10 to 20 seconds: they never
  lie on the ground. In the morning they get up. Night is whenever the sky is dark: a thunderstorm
  too, and always in the Nether and the End.
- **Walking** (`track/Walkway`) only where a player could: one block up, two down, no corner
  cut, nothing walked through but doors and fence gates, which they cannot open. It is worked
  out on the client from the blocks it has. The area around is read a little every tick (600
  spots per tick for all of them and their mounts together, 250 at most for one), and again
  every 30 seconds or when they find themselves off it. Each way is searched towards where
  they go. Looking for beds and chests, dodging and walking back after a blast have budgets
  of their own.
- **Monsters** within 10 blocks and in plain sight put them on guard: an iron sword drawn,
  the shield up, eyes on the nearest, a step away when it comes closer than 3.5 blocks. They
  never fight. Safe indoors, behind walls, shut doors or windows, they ignore them.
- **A creeper** blowing up within 7 blocks, in sight, throws them, shield up, and they walk
  back.
- **Hit by you**, they raise their shield.
- **Walked into** by you or what you ride, they are shoved out of your way (a quarter of a
  block per tick at most, never into a wall), getting up if they sat; never out of a bed.
  They never push you: nothing of them exists on the server.
- **In lava or fire** they show the swirls of fire resistance; **under water**, those of
  water breathing.

Where the blocks are not loaded, they only stand or sit where they are, standing at night. With
`offlineLife` off, they sit on their spot.

### Far from the camera

Past 64 blocks they have no life of their own (`track/RestingLoop`): nothing is decided,
but once a second they are still kept out of water (below). They play one of the animations
distant mobs play (`track/MobAnimations`) in a loop around their spot, on the ground read
around it (`track/MobPaths`, read again every 30 seconds while some of it is missing),
sitting in some of its pauses by day. At night they stand in those pauses, or stay in their
bed until the morning if they are in one.

- Where none of the mobs' animations fits (indoors, on a roof, a balcony), they play a
  cramped one (`MobAnimations.CRAMPED`: a step or two and back). Where not even that fits,
  or while the ground is not read, they turn on their spot every 8 seconds, as mobs do, and
  sit in one stance out of three: never a statue.
- From wherever their life left them, they stroll back to the loop, stopping now and then to
  look around, and start it once there.
- When the camera comes near again, their life goes on from where the loop left them.

### Ground and water

Whether they live or sit, they keep to the ground:

- Break the pile they sit on and they fall to the floor below; bury them and they reappear
  on top.
- Sitting with `offlineLife` off, their own spot wins again once it has room and something
  under it, since that is where they will come back.
- With nothing below down to the bottom of the world, they stay where they are.

They are kept out of the water, `offlineLife` on or off:

- Someone who logged off swimming or wading rests on the nearest place to stand within 10
  blocks of their spot (a bank or a beach, from the water's surface up to three blocks above
  it), decided once the blocks around are loaded. Living, their life and their loop go on
  from that shore; sitting, they sit there.
- With no shore that near, they get an oak boat on the surface, which carries them as a
  mount does (below), whether `showVehicles` is on or not. Once its water is gone, they are
  back on foot.
- One thrown into the water while their own spot is dry, with no shore within 10 blocks,
  stays in it.

### Mounts

A player who logs off riding something keeps it when `showVehicles` is on
(`track/RestingMount`):

- The server takes the mount away with them, so this client keeps a copy of it as it last
  saw it (coat, saddle, armour) with their spot, and draws it under them, never as a
  remembered mob of its own.
- A server with the mod keeps its own copy with their last position and shares it, so a
  player who never saw them log off sees them riding too, when they are among that player's
  64 nearest (see [What is sent](#what-is-sent-and-when)).
- Players who shared their mount with another player rest on foot: the server leaves the
  mount to the other one.

With `offlineLife` on, the mount carries them about their spot, within `offlineRoam` blocks,
then rests a while and goes again:

| Mount | Near the camera | Away from the camera |
|---|---|---|
| Horse (any mount that walks) | Only where it has room for itself and its rider (0.9 blocks wide at most), worked out like a walk on foot | A loop on the ground read around the spot |
| Boat | On the water it floats on; stays put on land | A loop on the water |
| Happy ghast | Straight lines through open air, at most 24 blocks from its spot and 4 up or down | A loop in the air, at its height |

Another mod's mount goes by what it is: a living one walks like a horse, or flies like the
happy ghast without gravity; a water creature stays put. Anything neither alive nor a boat (a
minecart) stays where it was. In the saddle they look
around and raise their shield when hit; no bed, no chest, no sword. With `offlineLife` off,
rider and mount wait on the spot.

### Names

- Their name shows above them, followed by *(offline)*, as close as the game shows names (64
  blocks).
- Aiming at one from right next to it (3 blocks) shows under the crosshair when they were
  last seen, in the game's language (`ui/OfflineHud`). A player known only from the server
  shows the time the server saw them leave; *offline* without a date only comes from a file
  an older version saved.
- That time is kept in the file with their spot (`lastSeen`, milliseconds since 1970).
- `offlineNames` turns both off.

## Distant mobs

Animals, villagers, golems, allays and named mobs stay visible once out of range, where they
were, playing an animation in a loop: a stroll of a few blocks, with pauses to graze or look
around, and back. No AI runs anywhere. The animations are written once
(`track/MobAnimations`), the blocks around the mob choose which one it plays, and each mob
starts its loop at its own moment, drawn from its UUID. `track/MobMemory` keeps them.

### Where the mobs come from

- **On a server with the mod**, the server shares the remembered mobs loaded anywhere with
  every player within 2048 blocks of them, every five seconds: position, kind, variant (horse
  coat, sheep colour, llama, rabbit, axolotl, parrot), age and what they wear, by the mob's
  UUID. Whoever was last near a mob leaves its
  position for everyone, and a mob gone from its loaded chunk disappears for everyone. The
  server shares the 31 mobs and the boats of `track/MobKinds.TYPES`, numbered in that order.
- **Otherwise**, each client remembers the mobs it met itself (the types in `mobTypes`), in
  `config/livinghorizon/mobs/<server>.json`.
- **Look.** A mob met up close keeps its exact look (the game's own save of it); one known
  only from the server is built from kind, variant and age.
- **Forgetting.** Coming back within range of a remembered mob that is not there forgets it.
- **Other mods' mobs** are shown from the start (monsters excepted), then left as the player
  sets them (`knownModdedMobs`). Each client remembers them itself: the server only shares
  vanilla kinds.
- **Mannequins** (from 1.21.9) are shown too, standing still: Distant Friends uses them as its fake
  players. Mobs in `stillMobTypes` (happy ghast, mannequin) stand still instead of walking
  their loop.

### Scanning nearby mobs

*Scan Nearby Mobs* (a button in the settings, or `/livinghorizon scan [radius]`) reads the
mobs of every chunk within `scanRadius` chunks from the world itself (`track/MobScan`) and
remembers them at once, with no need to walk there. Single player only.

- Chunks the integrated server has loaded give their mobs as they are. The others are read
  from the save, the way the game reads a chunk's entities, without loading or generating it.
- A remembered mob in the scanned chunks that is not found any more is forgotten.
- On another server there is nothing to read: the server only sends the mobs near you, and a
  server with the mod is what shares the others.

### Following the server in single player

In single player the server runs in the game itself. Four times a second it is asked where
the remembered mobs are (`track/ServerMobs`).

- The ones it still moves (past the distance it sends them at, but inside the simulation
  distance) are followed by their copies, at its pace and facing the way they face, with no
  animation of their own.
- One that dies there is forgotten.

Without this, the copy would stand where the mob was last seen while the real one wandered
off, then run or jump to it once it was sent again.

### Ground and animation

Once its copy is drawn, the blocks within 8 blocks of a mob (a square of 17 by 17 columns,
`track/MobPaths`) are read, a few mobs per tick, from loaded chunks or from the far world
(Voxy or Distant Horizons). They are read again when the mob moves, and later and later while
columns are missing; a ground read whole is saved with the mob and used at once next time.
Mobs that stand still and mobs the server moves read nothing, and flyers and swimmers get a
flat disc at their own height.

- **The ground**: in each column, the floor nearest the mob's height with room above it.
  Grass, flowers, crops and snow layers count as air; a path dug with a shovel counts as
  ground; water, leaves, fences and walls are not ground. Voxy gives every block of the
  column, Distant Horizons only its top one.
- **Choosing an animation**: every animation is laid on that ground, turned eight ways and
  mirrored. It fits when it never leaves the ground and never climbs or drops more than a
  block at a time: never off a cliff, never over a fence. Those at least 0.6 times as wide as
  the widest that fits are kept and the mob's UUID picks one; with none, the mob turns on the
  spot.
- **Playing it**: nothing is computed but a point of the animation (`track/MobMotion`). Head
  glances come at their own pace; animals graze in the pauses the animation marks for it.
- **Gaits** (`track/MobGait`): each kind covers the way as its real one does. Most walk.
  Slimes, magma cubes and rabbits hop: still on the ground between two hops and along an arc
  in the air, a slime stretching as it leaves the ground and squashing as it lands, a
  rabbit's legs kicking. A chicken flaps while it steps up or down a block. Impostors are
  baked with all of it at rest.
- **No teleporting**: when the server reports a drawn mob somewhere else, its copy walks to
  the new spot in a straight line (the ground there is read again meanwhile), then plays the
  animation chosen there.

### Handing over to the real mob

- **The near zone.** A copy is never drawn where the real mob would be sent
  (`MobMemory.nearRange`): the smaller of how far the server sends that kind (the *Entity
  Distance* slider in a single player world) and the render distance, less 16 blocks, never
  under 24. There the copy would stand where the real mob is not, close enough to walk up to.
  The simulation distance does not shrink that zone. With the debug boxes on, it shows as
  "too close for a copy".
- **Coming back.** Coming up to the distance where the server starts sending a mob
  (`MobMotion.home`), its copy is drawn back from its animation to where it was last seen:
  the pull starts 48 blocks before that distance and is whole 8 blocks before it. When the
  server sends the real mob, the copy walks the rest of the way up to it (faster the farther
  it is) while the real one is kept out of the frame. The real one takes over once both stand
  in the same place, or after 6 seconds. This is the one time a copy is drawn in the near
  zone. A copy not drawn, not a living mob, riding or ridden, or more than 48 blocks from the
  real one is forgotten instead, and the real mob shows at once.
- **No gap.** A mob the server still sends but the game does not draw (past the *Entity
  Distance* slider, or in a part of the world it does not draw) is drawn by the mod. The
  moment the server stops sending it, its copy takes its place, in the same frame (on the next
  ticks only when a crowd goes at once).
- **Sending distance.** In single player it is the server's own, scaled by the *Entity
  Distance* slider. On another server, how far it sends each category of mob is learnt from
  what it sends, up to the game's tracking range for the type (vanilla: up to the view
  distance; Paper: 48 blocks for animals). A copy is only judged missing well inside that
  distance, never just because its chunk is loaded.

### How many are drawn and animated

- `maxDistantMobs` of them are drawn at once (512; 1000 is no limit): the nearest remembered
  ones. Past `mobMaxDistance` (512 blocks) none are drawn. Both that distance and the near zone
  are applied after the choice, so copies there still take places and fewer may show.
- A mob smaller on screen than `animationMinPixels` (10 pixels; 0 always animates; its height
  or width, whichever is larger, and never under `minApparentPixels`) plays no
  walking or idle animation (`optStillTiny`): it keeps sliding along its animation and turning
  every tick, but its legs stay still. It resumes at that size plus a quarter and a pixel, so
  it never flickers. Sizes use the field of view the frame is really projected with
  (`DepthFar.pixelsPerRadian`), so a spyglass's zoom counts.
- `animationMaxDistance` (blocks; 0 is no limit) stills every mob past it, however big
  (`MobMemory.isStill`).
- With `optFreezeHidden` (*Debug Options...*, off by default), a copy not drawn for half a
  second (out of view, hidden, too small) is not ticked at all until it is drawn again: no
  animation, no ground read, only moved where the server says it is.

## Birds

Purely a picture, on this client (`ambient/Ambience`): nothing is spawned and no AI runs.

| Kind | `hiddenBirds` id | Where and when |
|---|---|---|
| Flocks | `flocks` | Crossing the sky |
| Geese | `geese` | Flying in a V |
| Birds of prey | `raptors` | Circling |
| Seagulls | `gulls` | Over the coast, landing on the beach now and then |
| Pigeons | `pigeons` | Sitting on tall buildings |
| Robins | `robins` | Hopping in fields |
| Tits | `tits` | In the trees |
| Ducks | `ducks` | Swimming on lakes and rivers |
| Bats | `bats` | At night, some hanging under leaves and roofs |
| Parrots | `parrots` | Now and then |

### Behaviour

- Robins and tits fly off when you come within 7 blocks and land 12 to 22 blocks farther.
  Pigeons circle and land back on their perches; gulls on the beach take to the air again.
- Ducks never let you near: they take off together for farther along the water, or for
  another water known around.
- Birds that leave never vanish at once: they scatter and shrink away. All of them go at
  once only when the world changes or the birds are switched off. Birds live in the
  Overworld only.

### Where they go

Where each kind goes is read off the terrain a few columns at a time: from the chunks loaded
here, or farther from the far world (`compat/LodWorld`, which reads Voxy through
`VoxySource` or Distant Horizons through `DhSource`, its public API). Robins look for crops or
farmland, tits for leaves, ducks for water, pigeons for a man-made block well above its
neighbours, gulls for a beach, shore or ocean biome.

### How they are drawn

- **3D** (`birdStyle` = `3d`, the default): small models (`ambient/BirdModels`) made of boxes
  in pixels, like the game's mobs, coloured box by box over a feather grain
  (`textures/misc/birds_3d.png`). The wings beat and bend at the tip, perched birds stand on
  their legs, ducks float. Past the impostor distance they are pictures of themselves (see
  [Impostors](#impostors)).
- **2D** (`birdStyle` = `2d`): pixel sprites from `textures/misc/birds.png`
  (`ambient/AmbientRenderer`). Flying, seen from above, one plane per wing; perched, seen from
  the side, facing the camera.
- Bats and parrots are the game's own either way.

### Calling birds

`/livinghorizon bird <kind>` calls a group of that kind at once, whatever the pace, the hour
or the weather (`Ambience.summon`).

- Those that fly come within 100 blocks.
- Those that perch go to the place the player looks at, up to 128 blocks away, when it suits
  them (a tree for tits, water for ducks, a field for robins, a tall building for pigeons,
  the coast for gulls). Otherwise they go to the nearest such place around, never close
  enough to take fright at once.
- The chat says when there is no such place, or when that kind is switched off.

## Impostors

In game: *Simplified Entities* (`impostors`, on by default). Past `impostorDistance` blocks, a
distant player or mob is not extracted and submitted as a model: a flat picture of it, always
turned to the camera, is drawn instead (`render/impostor/ImpostorRenderer`). A figure 200
blocks away is a handful of pixels tall, so the picture loses nothing, and the game no longer
builds a render state and a model for it every frame. With `impostorPlayers` off, players
keep their whole model at any distance.

### Baking the pictures

- **By the game's own renderers.** `ImpostorAtlas` draws each figure the way the inventory
  draws a player (an orthographic picture through the entity's own renderer), eight views
  around it, at twice the size of a tile. Skins, armour, other mods' models and the resource
  packs in use are all followed, because nothing is drawn but by the game.
- **Into an atlas page.** The views are read back and written into an atlas page. The game's
  texture-to-texture copy cannot be used: it only copies right at the corner of a texture and
  stretches anything written elsewhere.
- **Mip levels** (`ImpostorPixels`), from 64 down to 8 pixels, are worked out per tile on the
  CPU:
  - colours are averaged by how opaque they are;
  - the see-through pixels round a figure are given its colour, so its outline does not turn
    dark;
  - an alpha ramp crosses the cut-out shader's 0.1 at half coverage, so a small figure is
    neither a ghost nor fatter than its model.

  The page is sampled smoothly when small, by pixels when large.
- **Light.** Colour only, no normal map: the game's entity shaders have no use for one. The
  pictures are lit by the game's world light as a model is (turned over, the way the picture
  is drawn), and the quad's normal points up, which that light leaves at full brightness.
  Only the light where the figure stands (sky, torches, night) is applied on top. Under a
  shader pack, Iris works out a quad's normal from its corners, so `ImpostorLean` leans the
  quad back, without changing how it looks on screen, to face it where the light should come
  from.
- **When.** Every figure on hand (distant players, remembered mobs) is queued about once a
  second, near or far, and baked a little each frame (from the GUI pass from 1.21.6; see
  below), 3 ms of it, checked after each sheet, one sheet at least, so it is ready when the
  figure crosses the distance. Sheets read back from disk are uploaded all at once. A figure
  whose sheet is missing is drawn as a model meanwhile.
- **Resource reload.** A reload throws every sheet away. The mobs' sheets are read back from
  disk when the fingerprint (see [On disk](#on-disk)) is the same, and baked again otherwise;
  the others are always baked again.

### Older game versions

- **From 1.21.6 to 26.2** the views are drawn the way the GUI draws its pictures, through the
  game's output overrides. **On 26.3** they are drawn through a render pass on a target of the
  mod's own.
- **Before 1.21.6** they are drawn at the start of the frame into a target of the mod's own,
  which `MinecraftMixin` and `LevelRendererTargetMixin` hand the game as its main target and
  its Fabulous item target while a figure is drawn:
  - on 1.21.5, one view at a time through the GPU device;
  - before 1.21.5, the eight views side by side in plain OpenGL, read back at once and
    written into a page with its mip levels.
- **Before 1.21.2**, with no render states, the figure is posed through the mod's stand-ins
  (`render/state/`), which set the entity's body, head, stride and age for the time it is
  drawn.

### Same look, same picture

`ImpostorKey` decides which picture a figure gets. Figures with the same key share one sheet.

- **In the key**: the type, the player's skin, the synched data that changes the look
  (variant, colour, profession, baby, pose; not health, air, score or being on fire), and
  what it wears, however worn.
- **Left out**: what never shows (positions, times, owners, sound variants, names but the four
  the game draws differently: Dinnerbone, Grumm, jeb_ and Toast) and,
  learnt per kind of mob, what keeps changing on one (a dolphin drying, a zombie raising its
  arms).
- **The same in every game**: the key is hashed so that the same figure has the same key in
  every world and every game (variants by name, records by parts).
- Two looks of the same kind whose pictures come out the same (their full-size pixels hash
  the same) share one sheet too.

### No blinking

- A figure becomes an impostor past `impostorDistance`, and a model again only 5% inside it.
- It keeps a picture until it has turned 8 degrees past the edge between it and the next one.
- When its look changes, a player or a mob keeps its old picture until the new one is baked;
  a 3D bird is drawn as a model meanwhile.

### Video memory

- **Layout.** Each kind of figure has pages of its own, one sheet per row (512 pixels wide):
  4 rows, then 8, 16, then 32, every tile 64 pixels. A sheet takes 170 KB of video memory
  (512 by 64 pixels and three smaller levels).
- **Large figures.** A figure larger than its box (a horse's muzzle) is drawn smaller in its
  tile rather than cut off: at a hundred blocks a figure is a dozen pixels tall.
- **Mobs** keep their sheets, up to `impostorMobMemory` (1 GB) in all, taken only as looks are
  met (pages are taken 4 rows at a time and more: 50 looks of one kind take about 10 MB, 50
  kinds of one look each about 35 MB). Past that, an unused sheet of the same kind gives
  way, or the figure stays a model for 30 seconds.
- **Players** (riders with what they ride included) have `impostorPlayerMemory` (512 MB) of
  their own, some three thousand sheets, thrown away after a minute unused. They never take
  the mobs' room, nor the mobs theirs.
- A sheet drawn or asked for in the last 10 seconds is never replaced.

### On disk

`ImpostorDisk` keeps the mobs' sheets in `config/livinghorizon/impostors/<fingerprint>/`, so
that a mob met in an earlier game is read back rather than baked.

- The fingerprint is the game version, the resource packs chosen and their files, the jars in
  `mods/` and the sheet format. When one of them changes, the folder is thrown away.
- 4096 sheets are kept, some 35 KB each once compressed (about 140 MB), counted when the
  folder is opened, at the start and after a reload: a game may write more until then. The
  ones met longest ago go first.

### Special figures

- **3D birds** (`ambient/BirdFigure`) are baked from their model as figures of the mod's own
  (`ImpostorFigure`), not entities:
  - one sheet per species, pose (perched, or flying with its wings up, level or down) and
    height it is seen from (well below, below or above when flying; below, level or above
    when perched);
  - all the sheets of a pose are baked together the first time one is needed, on pages of
    their species;
  - the picture faces the camera entirely, not only round, since it was baked as seen from
    that height, and the wing beat goes on from one sheet to the next;
  - they are baked again each game, never kept on disk, and named after the species in the
    impostor screen.
- **Players who logged off** are impostors in every posture: standing, seated on the ground,
  or in a bed. A posture other than standing is part of the key
  (`ImpostorKey.posture`), and the picture is baked in it:
  - seated: in the riding pose, drawn lowered to the ground as the model is;
  - in a bed: along its body, baked raised (`ImpostorAtlas.LYING_LIFT`) so that none of it
    falls below the tile, and laid along the bed.
- **Riders** (players online or not, on a horse, a camel, a happy ghast, in a boat, a raft, a
  minecart...) are baked in one picture with what carries them (`ImpostorKey.riding`,
  `ImpostorAtlas.Rider`): the rider seated where it sits, turned as it is turned. A vehicle has
  no body to turn in its state, so the entity itself is turned for each view and put back
  after the bake. Filed as a player's sheet, never kept on disk.
- **Never impostors**: a vehicle carrying more than one passenger (drawn as models, all of
  them), a mob carrying another that is not a player, the 2D birds, parrots and bats, and the
  types `GhostRenderer.drawable` leaves out (GeckoLib 4).

### Preview

*Simplified Entities...* in the settings, or `/livinghorizon impostors`: every figure baked,
its eight views, and its front view at 32, 16 and 8 screen pixels, as it looks far away.
*Rebuild* throws every picture away, on disk too, and bakes the figures on hand anew, even
after a failed bake.

## Far terrain depth

Our figures must hide behind the far terrain pixel by pixel, like any mob behind any block.
That needs the far terrain's depth in the game's depth buffer while the entities are drawn,
and only then (`depthOcclusion`). `compat/FarDepth` does it, then puts everything back, so
that the far terrain mod and the shader pack see exactly what they would without Living
Horizon (on 26.3 the game's GPU device path works otherwise, see
[Distant Horizons](#distant-horizons)).

| Setup | Who puts the far terrain into the game's depth |
|---|---|
| Voxy, no shader pack | Voxy itself, for good: nothing to do |
| Voxy, under a pack that keeps it out of the game's depth (Photon sets `excludeLodsFromVanillaDepth`) | `FarDepth`, around the entities only |
| Distant Horizons | `FarDepth`, from Distant Horizons' own depth texture (`compat/DhDepth`) |

### Voxy under a shader pack

Left alone, anything drawn past the render distance would show through Voxy's mountains. So,
around the call that draws the entities:

1. Just before, `FarDepth` saves the game's depth and writes Voxy's into it, with Voxy's own
   depth blit.
2. The entities are drawn, tested against Voxy's terrain like against any block.
3. Just after, the saved depth comes back, except where an entity was drawn.

The pack still finds the depth it expects.

- **From 1.21.9 to 26.1** the game's call only fills its buffers, and draws what is left in
  them after it (the last batch, items and their glint): that is drawn before the saved depth
  comes back. Before 1.21.9 the mod brackets the entities itself and ends the batch first.
- **From 26.1** the translucent parts (a slime's outer layer) are drawn in a step of their
  own, just after Iris copies the depth for the pack. Voxy's depth goes back in at the start
  of that step and out at its end.
- **Render scale.** Under a pack's render scale (Photon's TAAU), the pack draws the world in
  the bottom left corner of the depth texture and says so to Voxy (`useViewportDims`). Voxy's
  depth, the depth view and the occlusion queries all go in that corner too.

### Distant Horizons

Distant Horizons draws its terrain into a framebuffer of its own and never writes its depth
into the game's. `compat/DhDepth` reads that depth texture and the projection it was made
with, and `FarDepth` converts it into the game's depth the same way (and undoes it after the
entities).

Without a shader pack, Distant Horizons fades the game's picture into its terrain after the
entities. `FarDepth.beforeFade` keeps that fade off our figures (`optDhFade`); otherwise it
paints its terrain over distant mobs.

**On 26.3**, with Vulkan, or with OpenGL over Distant Horizons without a shader pack (unless
Distant Horizons draws with OpenGL itself, as it does with Iris installed), all of this goes
through the game's own GPU device (`compat/FarDepthGpu`). Nothing can be copied in the middle
of the world's pass there, so the merge is a draw in that pass and is not taken back: Distant
Horizons' depth stays in the game's for the rest of the frame, which its fade treats as sky.
`FarDepthGpu.beforeFade` keeps the fade off our figures on a copy of the depth, put back
after.

### Shadows under Photon

Such a pack also reads Voxy's own depth textures (`vxDepthTexOpaque`, `vxDepthTexTrans`)
where the game's depth ends. Photon's screen-space shadows, used past its shadow map, start
from the point Voxy's depth gives. At a distant mob, that point is the terrain behind it,
whose shadow then fell across the mob.

So right after the entities are drawn, `FarDepth.intoVoxy` writes our figures into both of
Voxy's depth textures, and nothing else:

- only inside the screen rectangles of the figures drawn past the render distance
  (`FarDepth.figure`);
- only where they were just drawn, past the render distance and nearer than Voxy's terrain.

Anywhere else, what Voxy draws is left exactly as it is. *Voxy Shadow Fix* (`optVoxyShadows`)
turns it off.

## Skipping hidden mobs

*Skip Hidden Mobs* (`hideOccludedMobs`) does not draw a distant mob the terrain hides
entirely, which is cheaper with many mobs. `compat/OcclusionQueries` asks the GPU, against
the depth of the picture, where Voxy's or Distant Horizons' terrain already is (merged by
`FarDepth`):

1. Just before the entities are drawn, the box around each distant mob is drawn against that
   depth, writing nothing, inside an occlusion query (`GL_ANY_SAMPLES_PASSED_CONSERVATIVE`, or
   `GL_ANY_SAMPLES_PASSED` without OpenGL 4.3 or `GL_ARB_ES3_compatibility`), with the
   matrices of the frame (`LevelRendererMixin`, `renderLevel`; `render` from 26.2).
2. The answers are read once the GPU has them, up to 4 frames of them waiting, never waited
   for.
3. A mob whose last answer is "no pixel passed" is not drawn. It is asked about again on the
   next frame that has room for a batch, so it comes back as soon as one pixel of it would
   show.

On 26.3's GPU device path (see [Distant Horizons](#distant-horizons)), the boxes are tested
in `compat/FarDepthGpu` and the answers read back from there, a capped number per frame.

Why the answer can be trusted:

- The GPU projects the box exactly as the mob is drawn, so the answer is the picture's.
- Depth clamping keeps a box past the far plane at the far plane: the sky never hides a mob,
  terrain in front does.
- A mob never asked about, or whose answer is more than six frames old, is drawn.

With `optOcclusionQueries` off, or once the queries have failed, the mobs are tested in the
far terrain's world instead (`render/Occlusion`). The two answers are never mixed. With
Vulkan, once Distant Horizons' depth is merged, no mob is skipped: the depth test hides them
pixel by pixel.

## Debug tools

The switches are in *Debug Options...* in the settings, the F3 entries in the game's own
debug options. `/livinghorizon debug` turns the panel and the boxes on or off together.

| Tool | Switch | What it shows |
|---|---|---|
| Panel | `debugHud` | Top left: cost per tick and frame, what became of every distant mob in the last frame, far terrain reads and depth, how far this server sends mobs, which optimisations are on |
| Panel in F3 | `living_horizon` in the game's debug options (F3 + F6, from 1.21.9) | The panel's lines in the F3 screen. `/livinghorizon debug` turns it on too |
| Polygons in F3 | `fake_entities` in the game's debug options (F3 + F6, from 1.21.9) | The polygons of the mod's own entities (distant players and mobs, never the game's), split into full models and impostors (one quad each), and a line of impostor sheets baked and waiting |
| Boxes (from 1.21.11) | `debugBoxes` | A box around every distant mob and player, seen through terrain, coloured by what became of it (below). A dot in the middle keeps it visible when the box is smaller than its lines |
| Game's mobs | `debugGameMobs` | Boxes around the mobs the game draws itself too |
| Labels (from 1.21.11) | `debugLabels` | State, kind, distance and size on screen above each box, at a constant size |
| Outlines | `outline*`, in *Outlines...* | An outline around each kind of figure, and a cross where a mob is not drawn (below) |
| Depth view | `debugDepthView` | Bottom right: the game's depth alone, with the far terrain merged in, or after the entities. Near is white, far black, sky blue |
| Far terrain | `/livinghorizon lod` (or `voxy`) | Which far terrain mod is read (Voxy or Distant Horizons), whether reading it failed, and the state of its depth |
| Optimisations | `opt*`, `depthOcclusion`, `hideOccludedMobs` | Every optimisation has its own switch, to compare the cost (see [Optimizations](#optimizations)) |

### Box colours

| Colour | What became of the mob |
|---|---|
| Green | Copy drawn |
| Cyan | Real mob drawn by the mod |
| Blue | Player |
| White | Real mob drawn by the game (with `debugGameMobs`) |
| Yellow | Too small (under about half a pixel) |
| Orange | Behind blocks |
| Brown | Too close for a copy (the [near zone](#handing-over-to-the-real-mob)) |
| Red | Outside the view |
| Purple | Being built |
| Grey | Past `maxDistantMobs` |

### Outlines

Outlines are seen through terrain, in the colours of the boxes, each on its own switch. They
are made for screenshots that compare optimisations.

| Switch | In game | Outline |
|---|---|---|
| `outlineGameMobs` | Real (game) | White, around the mobs and players the game draws |
| `outlineLiveMobs` | Real (mod) | Cyan, around the real mobs the mod draws |
| `outlineCopies` | 3D Copies | Green, around the 3D copies |
| `outlinePlayers` | Players | Blue, around the distant players |
| `outlineImpostors` | Impostors | Magenta, around the impostors |
| `outlineLeftOut` | Not Drawn | A cross a few pixels wide where a distant mob is not drawn at all, in the colour of why: where it is, not what it is |

Outlines need 1.21.9 (impostor outlines: 1.21.9 to 26.1); crosses need 1.21.11.

### Test players who logged off

To try that feature alone in single player:

| Command | What it does |
|---|---|
| `/livinghorizon debug offline test spawn [name] [minutes]` | Places one where you stand, wearing your skin, logged off that many minutes ago (default: `Sleeper1`, `Sleeper2`..., just now), on a copy of what you ride if you ride something |
| `... test spawn <name> <minutes> <count> [radius]` | Places that many (`name1`, `name2`...), up to 100, each on the ground somewhere within the radius around you (16 blocks by default, up to 256), facing a way of its own, on your mount at your height if it flies. Each wears the skin of a different real player (50 of them, then again from the first), fetched from Mojang (from 1.21.9; before, the default skin of its name) |
| `... test crowd <count> [radius] [minutes]` | The same, with names of their own (`Sleeper1`...) |
| `... test clear` | Removes them |
| `... test list` | Shows everyone resting on this server, where and since when |

Test players are never written to the file: they are gone at the next start. In a world the
host shares, the other players see them too (see
[The server's file](#the-servers-file)).

## Commands

`/livinghorizon` is a client command: it never reaches the server.

| Command | What it does |
|---|---|
| `/livinghorizon` | Whether positions are shared, how many mobs are remembered and drawn, the cost per tick and per frame, and every distant player followed, with where their position comes from and how far they are |
| `/livinghorizon config` | Opens the settings |
| `/livinghorizon impostors` | Opens the impostor preview (*Simplified Entities...*) |
| `/livinghorizon scan [radius]` | Reads the mobs of the chunks around, in single player (see [Scanning nearby mobs](#scanning-nearby-mobs)) |
| `/livinghorizon bird <kind>` | Calls a group of birds of that kind (see [Calling birds](#calling-birds)) |
| `/livinghorizon lod` (or `voxy`) | Which far terrain mod is read and the state of its depth |
| `/livinghorizon debug` | Turns the debug panel and the boxes on or off together |
| `/livinghorizon debug offline test ...` | Test players who logged off (see [Test players](#test-players-who-logged-off)) |

Two key bindings, unbound by default: *Toggle Distant Players* (flips `enabled`) and *Open
Living Horizon Settings*.

## Settings reference

Every setting is in `config/livinghorizon.json` (`FarConfig`). The file is read at start and
written back with every setting in it, including the ones a newer version adds. Its
`version` field is the file's layout, not a setting: an older file is brought up to date on
load.

In game, the settings are in the mod's screen: the *Living Horizon...* button of the Options
screen, Mod Menu (Fabric, Quilt) or the mods list (NeoForge, Forge), the settings key, or
`/livinghorizon config`. From Sodium 0.8 the main screen's settings are on Sodium's video
settings pages too, with the same names, and its sub-screens open from there.

The *Preset* button (*Low*, *Normal*, *High*, *Ultra*) sets only the settings that scale the
cost: `impostorDistance`, `maxDistantMobs`, `mobMaxDistance`, `birdDensity` and
`birdMaxDistance`. *Normal* is the defaults. Changing one of them by hand shows *Custom*.

### Main screen

#### General

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `easterEggs` | Easter Eggs | `true` | | A few surprises, now and then |

#### Performance

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `impostors` | Simplified Entities | `true` | | Far players and mobs drawn as a flat picture of themselves (see [Impostors](#impostors)) |
| `impostorPlayers` | Simplified Players | `true` | | Distant players become impostors too; off, they keep their whole model at any distance |
| `impostorDistance` | Simplification Distance | `128` blocks | 32 to 2000 | Past this distance an impostor stands in for the model |
| `impostorMobMemory` | Mob Picture Memory | `1024` MB | 64 to 4096 | Video memory the mobs' pictures may take, apart from the players' |
| `impostorPlayerMemory` | Player Picture Memory | `512` MB | 32 to 2048 | Video memory the players' pictures may take (riders with what they ride), apart from the mobs' |

#### Distant Players

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `enabled` | Show Players | `true` | | Distant players are drawn at all. The *Toggle Distant Players* key flips it |
| `showVehicles` | Mounts & Vehicles | `true` | | Draw the mount they were last seen on |
| `playerMaxDistance` | Player Render Distance | `0` blocks | 0 to 4000 | Past this, distant players are not drawn (`0`: no limit) |
| `offlinePlayers` | Offline Players | `true` | | Players who logged off are shown and remembered; off, neither |
| `offlineLife` | Offline Player Activity | `true` | | They live their life around their spot; off, they sit on it |
| `offlineRoam` | Roaming Radius | `50` blocks | 8 to 128 | How far from their spot they walk around and look for a bed or a chest (50 is about a village) |
| `offlineNames` | Offline Player Names | `true` | | Their name above them, marked offline, and when they were last seen, under the crosshair |

*Clear Offline Players* forgets every player who logged off on this server. On a server with
the mod, they come back at their last position.

#### Distant Mobs

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `distantMobs` | Show Mobs | `true` | | Remembered mobs stay visible far away |
| `maxDistantMobs` | Max Mobs | `512` | 0 to 1000 | How many are drawn at once, nearest first (`0`: none, `1000`: no limit). No limit here and in `mobMaxDistance` together can crash the game |
| `mobMaxDistance` | Mob Render Distance | `512` blocks | 0 to 4000 | Past this, distant mobs are not drawn (`0`: no limit) |
| `rememberNamedMobs` | Name-Tagged Mobs | `true` | | A mob with a name tag is shown whatever its type |
| `animationMinPixels` | Min. Animated Size | `10` pixels | 0 to 40 | A distant mob shorter than this on screen plays no walking or idle animation (`0`: always animates) |
| `animationMaxDistance` | Animation Distance | `0` blocks | 0 to 4000 | Past this, no distant mob plays an animation (`0`: no limit) |
| `mobTypes` | Mobs & Entities... | The 31 mobs and the boats a server shares, and mannequins | | Which mobs are shown, and remembered by this client |
| `stillMobTypes` | Mobs & Entities..., *Still* | Happy ghast, mannequin | | Mobs shown standing still instead of walking their loop |
| `scanRadius` | Scan Radius | `32` chunks | 4 to 1024 | Chunks around you read by a scan, in single player (32 chunks = 512 blocks) |
| `knownModdedMobs` | (not shown) | Empty | | Mob types of other mods already offered once: a new one is shown from the start (monsters excepted), then left as you set it |

*Forget Mobs* forgets every mob remembered on this server; those a server with the mod shares
come back by themselves. *Scan Nearby Mobs* runs a scan.

#### Birds

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `skyBirds` | Show Birds | `true` | | Birds and bats around you |
| `birdDensity` | Bird Density | `100` percent | 0 to 500 | How many birds, all kinds together (`0`: none, `500`: five times as many) |
| `birdMaxDistance` | Bird Distance | `800` blocks | 100 to 3000 | How far away birds can be |
| `birdMinHeight` | Min. Flight Height | `30` blocks | 0 to 200 | Height above you (or the sea, whichever is higher) below which flocks and birds of prey never fly |
| `birdSize` | Bird Size | `1` times | 1 to 6 | How much bigger than life birds are drawn |
| `birdStyle` | Bird Style | `3d` | `3d`, `2d` | `3d`: small models with beating wings; `2d`: flat pixel sprites |
| `hiddenBirds` | Bird Types... | Empty | | Kinds switched off: `flocks`, `geese`, `raptors`, `gulls`, `pigeons`, `robins`, `tits`, `ducks`, `bats`, `parrots` |

### Debug Options

#### Show on Screen

| Setting | In game | Default | What it does |
|---|---|---|---|
| `debugHud` | Debug Panel | `false` | The debug panel, top left |
| `debugBoxes` | Boxes on Mobs | `false` | A coloured box around every distant mob and player, seen through terrain |
| `debugLabels` | Labels on Mobs | `false` | State, kind, distance and size on screen above each box |
| `debugGameMobs` | Game's Mobs Too | `false` | Boxes around the mobs the game draws itself too |
| `debugDepthView` | Depth View | `0` | `0` off; `1` the game's depth; `2` with the far terrain merged in; `3` after the entities |
| `outline*` | Outlines... | `false` | See [Outlines](#outlines) |

#### Display

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `extendFarPlane` | Extended View Range | `true` | | Push the far clipping plane out past the farthest figure, so terrain can hide it |
| `minApparentPixels` | Minimum Size | `0` pixels | 0 to 32 | Above 0, a distant figure (player, mob, bird) is never drawn smaller than this, and stops shrinking (`0`: off) |
| `renderTrackedVehiclesFar` | Keep Ridden Vehicles | `true` | | Never cull a mount that carries another player |

#### Optimizations

Every one is on by default except `optFreezeHidden`. Switch one off to see what it saves.

| Setting | In game | What it does |
|---|---|---|
| `optViewCulling` | Skip Outside the View | Copies outside the view are not prepared at all |
| `optTinyCulling` | Skip Specks | Copies smaller than about half a pixel are not prepared (only when no minimum size is set) |
| `depthOcclusion` | Hide Behind Far Terrain | The far terrain's depth on distant mobs and players, pixel by pixel (see [Far terrain depth](#far-terrain-depth)) |
| `optLazyDepth` | Lazy Far Depth | The far terrain's depth is merged only on frames that draw something past the render distance |
| `optParallelRead` | Parallel Reads | The far world is read on several threads at once rather than one |
| `optColumnCache` | Remember Far Ground | Ground already read in the far world is kept for two minutes |
| `hideOccludedMobs` | Skip Hidden Mobs | Distant mobs the terrain hides entirely are not drawn (see [Skipping hidden mobs](#skipping-hidden-mobs)) |
| `optOcclusionQueries` | Hide Mobs by Depth | How `hideOccludedMobs` decides: occlusion queries on the GPU; off, lines of sight in the far terrain's world |
| `optDhFade` | DH Fade | Distant Horizons' fade leaves the entities alone, without a shader pack; off, it paints its terrain over distant mobs |
| `optVoxyShadows` | Voxy Shadow Fix | Our far figures are written into Voxy's depth, on their own pixels only, so that Photon's terrain shadows do not fall across them |
| `optBackgroundBuild` | Background Build | Saved mobs are read off the game's thread and built within 2 ms per tick |
| `optStillTiny` | Still Tiny Mobs | Mobs under `animationMinPixels` play no walking or idle animation |
| `optFreezeHidden` | Freeze Undrawn Mobs | A copy not drawn for half a second is not animated, only moved where the server says |

Older names are still read from the file:

| Old name | Now |
|---|---|
| `voxyOcclusion` | `depthOcclusion` |
| `optLazyVoxyDepth` | `optLazyDepth` |
| `optParallelVoxy` | `optParallelRead` |
| `optHiZ` | `optOcclusionQueries` |
