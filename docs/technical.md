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
- [Fog on distant figures](#fog-on-distant-figures)
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
| [Distant mobs](#distant-mobs) | Mobs out of range, playing an animation in a loop | This client's memory, or the server's, and the world's seed for land never explored |
| [Birds](#birds) | Flocks, perched birds, ducks, bats, parrots | Made up on the client from the terrain |
| [Impostors](#impostors) | Flat pictures in place of far models | Baked by the game's own renderers |
| [Far terrain depth](#far-terrain-depth) | Nothing of its own: hides our figures behind the far terrain | Voxy's or Distant Horizons' depth |

The figures are **puppets**: entities the mod makes and draws with the game's own renderers,
never added to a level (`track/Puppets`).

### Code map

| Package | What is in it |
|---|---|
| root | `LivingHorizonCommon` (entry point on both sides, opens the network channel), `LivingHorizonClient` (client entry point), `FarConfig` (the settings file), `QualityPreset`, `Stats` |
| `track/` | What is followed and remembered: online players (`FarPlayer`, `FarPlayerTracker`), players who logged off (`RestingPlayers`, `RestingPuppet`, `RestingLoop`, `RestingMount`, `Walkway`, `Footing`), mobs (`MobMemory`, `MobPaths`, `MobAnimations`, `MobMotion`, `MobGait`, `MobScan`, `ServerMobs`, `SeedDraws`, `SeedMobs`, `Foresight`), what the server shares (`PositionPublisher`, `PositionsMessage`, `SharedPositions`, `WorldPlayers`) |
| `render/` | Adding the puppets to the frame (`GhostRenderer`, `Sink`), the far plane (`DepthFar`) and the depth arithmetic (`DepthMath`), `Occlusion`, the distance settings drawn in the world (`DistancePreview`); `impostor/` for the impostors; `state/` for the render state stand-ins used before 1.21.2 |
| `compat/` | Voxy, Distant Horizons, Lodistance and Far View: reading their world (`LodWorld`, `VoxySource`, `DhSource`, `LodistanceSource`, `FarViewSource`, `DrawnGround`) and their depth (`FarDepth`, `FarDepthGpu`, `DhDepth`, `OcclusionQueries`), through one link per mod (`VoxyLink`, `DhLink`, `LodistanceLink`, `FarViewLink`, `IrisLink`); Entity Culling, told which entities the mod draws (`EntityCulling`); Lycanites Mobs, whose queued models are drawn in time (`LycaniteMobs`); Valkyrien Skies, whose ships' entities are left to it (`ValkyrienSkies`) |
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
  them. Voxy writes its terrain into the game's depth buffer and removes the render distance
  fog (its own fog on the figures: see [Fog on distant figures](#fog-on-distant-figures));
  for Distant Horizons and shader packs, see [Far terrain depth](#far-terrain-depth).
- **The far clipping plane** (four times the render distance) is pushed out past the farthest
  figure drawn the frame before, players, mobs and birds alike (`extendFarPlane`): a tenth
  farther and 64 blocks more, up to 65536 blocks. A figure still near or past the plane (95%
  of it) is drawn closer along the same line of sight and smaller by the same ratio: the same
  picture, but in front of the terrain between.
- **Mounts.** A player is drawn on the mob they ride (`FarPlayer.mountPuppet`), and the
  remembered mobs leave that mob out (`MobMemory.carried`), so it is never drawn twice.
- **Elytras.** A player the server says is gliding flies: `FarPlayer.glide` sets the copy's
  gliding flag, counts its `fallFlyTicks` (the body lies along the flight after ten) and, from
  1.21.2, ticks its `ElytraAnimationState` (the wings spread), all of which the game does in
  the player's tick, which a copy never runs; its pitch follows the server's, its legs hang
  still. A glider is never an impostor, and never baked into one: the pictures are baked
  standing. A copy made as a player leaves mid-flight keeps how long they glided and their pitch.

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

A server without the mod sends nothing either. Ten seconds after joining a server (never a
world played alone) with nothing received, the client says so once, in its log and, while
*Show Players* is on, in the chat (`FarPlayerTracker.warn`); the `nomodserver` game test
checks it.

### What is sent, and when

- **Five times a second**, to every player online: each player's position, yaw, dimension
  and the id of what they ride, whatever it is, so that a far player is drawn on their own
  mount, and a client that remembers that mount or vehicle (shared by the server, or met) does
  not draw it a second time on its own. A server of an older version names only the kinds of
  `track/MobKinds.TYPES`: the client then keeps the mount it knew while the server's copy is of
  its kind (`FarPlayer.mountFrom`). And whether they glide with an elytra, with their pitch then.
- **Every five seconds**, to every player online:
  - how the 64 players nearest them look, online or logged off (skin, armour and what they
    hold, read every second), and what they ride, as the server saves it;
  - every player who logged off, and the host's test players: where they were last and when
    they left, with how they looked and the mount they took away for those among the 64. The
    server sends them all; each client chooses how many it draws (`maxOfflinePlayers`);
  - the remembered mobs within 2048 blocks of them, with what they wear (saddle, harness,
    armour, carpet, chest; see [Distant mobs](#distant-mobs)), and the animals foreseen in the
    chunks not generated yet within `seedMobRange` of them (see
    [Animals of unexplored land](#animals-of-unexplored-land));
  - the world's seed, when the owner shares it (`shareSeed`), and which dimensions a client can
    build the generator of; otherwise an empty one, which tells the client the server shares
    none. A kind of message older versions leave unread.
- **At once, to a player who joins**: all of it, without waiting for the next five seconds.

The messages (`track/PositionsMessage`) are written with plain Java streams, the same on
every loader and version. The client keeps what it receives in `track/SharedPositions`.

Their layout, from version 3:

- two numbers first: the version written (`VERSION`) and the oldest version that reads it
  (`READABLE_FROM`). A version that only adds fields at the end of a record, or after the
  records, keeps `READABLE_FROM` and older clients skip what they do not know; one that
  changes what is there raises it. A client that cannot read the server's messages says so in
  its log, with both versions, ten seconds after joining, and in the chat which side needs the
  update;
- the kind of message, then the table of the dimensions it names, by id
  (`minecraft:overworld`, `aether:the_aether`): a record names its dimension by its place in
  the table. Version 2 numbered them 0 to 2 and gave 3 to every other one, so players in two
  dimensions of mods were drawn in each other's; it is still read, those in 3 left out;
- the records, each after its length (a varint). The players who logged off come after which
  part of their list the message is and how many parts there are. Version 4 marks a gliding
  player with the last bit of their flags, and writes their pitch at the end of the record:
  version 3 leaves both unread, and reads the rest of the record as before.

A message stays under 1 MiB, past which the game refuses it (`MAX_BYTES`). The players who
logged off go in as many messages as it takes, none left out; the client takes the list once
its last part has come, and drops a list whose parts come out of order. Players online that do
not fit are sent by their position alone, then left out, the first in the list kept, so the
nearest come first. Mobs that do not fit leave out the farthest, and the circle they were
chosen in shrinks so that the client does not take those left out for gone.
`PositionsMessageTest` holds the exact bytes of three messages: a change to the layout fails
it, and is a new version.

### Where it runs

- **A world played alone, or opened to LAN**: the server runs inside the host's game, which
  reads its messages back like any other client. Players who logged off show as soon as the
  world opens.
- **A dedicated server**: the same jar in the server's `mods` folder (with the Fabric API on
  Fabric and Quilt).

`mixin/MinecraftServerMixin` drives it from the start of the server's tick, so that what it
sends goes out when that tick flushes the connections.

### What the owner chooses

`ServerConfig` reads `config/livinghorizon-server.json` (the host's config folder in a world
played alone or opened to LAN). It is written with every setting the first time the server
shares, filled in with those a newer version adds, and read again at the next full share
(five seconds at most) after its modification time changes; everyone is then sent everything
again. A file that does not read at start is kept as `.bad` and the defaults are written; one
that does not read later, while being edited, keeps the settings as they were and the file
alone. An unknown key is said in the console and dropped.

`track/ShareRules` decides, for each player sent the lists, who is left out, with no type of
the game's but a team's name:

- `seedMobs` (on) and `seedMobRange` (512 blocks, 0 to 2048) share the animals foreseen in the
  chunks not generated yet, with the mobs: off, none are worked out at all. `shareSeed` (off)
  sends the world's seed to the players with the mod, for their game to foresee them anywhere
  itself, where the dimension's generator is the game's own: whoever has the seed knows where
  everything in the world is.
- `sharePlayers`, `shareOfflinePlayers`, `shareMobs` switch each list off. The players
  message is still sent, empty but for the player themselves, so the client knows the
  server has the mod. Mobs switched off, or a player in a hidden dimension, get an empty
  list in a circle that covers nothing (radius -1): the client keeps what it remembers.
- `hiddenPlayers` (names, any case) and `hiddenDimensions` (ids) leave records out of both
  lists of players, whoever looks.
- `playerRange` (blocks across the ground, 0 for any) and `teamsOnly` (the scoreboard's
  teams, the same team on both sides) depend on who looks: with either on, the five times a
  second message is made for each player instead of once for all (`sameForAll`).
- `hideSpectators` and `hideInvisible` are applied by the publisher itself, as it reads the
  players online: such a player's record is not updated and not sent, online or logged off.
  They log off from where they were last seen, with the mount they had then. A player seen
  only hidden has no record; `WorldPlayers` also leaves out those saved in spectator mode.

These only change what the server sends. What a client already received is kept in its own
memory of players who logged off.

### The server's file

The server keeps the players and the mobs in `livinghorizon-positions.dat`, at the root of
its world folder, so that a player who joins later sees them too.

- It is saved every minute and when the server stops, into a temporary file moved over it.
- It starts with a version of its own (`PositionsMessage.FILE_VERSION`, 3, and
  `FILE_READABLE_FROM`), apart from the messages' `VERSION`: a new message layout must not make
  servers drop their file. Its layout is the messages', without the kind: the table of
  dimensions, the players, the mobs. A file of version 2 is read, those it put in a dimension
  of a mod left out, then read again from `playerdata`, which names it. A file that does not
  read (another version, damaged) is kept as `livinghorizon-positions.dat.bad`, with a line in
  the console, and a new one is started; one from a newer version that still reads is copied to
  `livinghorizon-positions.dat.v<version>` first.
- Players still online when the server stops are kept as they were, as if they logged off
  then.
- A mob is forgotten when its chunk is loaded and it is not there any more, and the oldest
  go past 4000 mobs. Every player who logged off is kept, however many.
- Each time the server starts, its first share also adds the players the world saved that
  the file does not have yet (`track/WorldPlayers`: every one in `playerdata`, however many,
  with their position and gear, named from `usercache.json`, last seen when their file was
  written, no skin), so players from before the mod show as logged off at once. A player the
  cache does not name is left out, and the game keeps only the 1000 profiles used last in it
  (`CachedUserNameToIdResolver`), so that is the bound on a server without a mod that keeps more.
- Test players placed by the host with `/livinghorizon debug offline test` are shared too,
  marked as tests: the other clients show them while the host keeps them, and nobody saves
  them.

## Players who logged off

A player who logs off stays where they were last, until they come back. It is all a picture
on this client: nothing is sent to the server, they block nobody, and nothing can hurt them.

### Where they come from

- Remembered per server, across sessions, in `config/livinghorizon/resting/<server>.json`
  (`track/RestingPlayers`), written off the game's thread every five seconds when something changed, not at each
  player the server reports. Each spot names its dimension by id (layout 2); a file of layout
  1 numbered them, and its spots in 3, a dimension of a mod, are left out.
- On a server with the mod, even players who logged off before you joined are there: the
  server keeps everyone's last position, skin and gear. Without the server's word, their skin
  is fetched from Mojang by name (from 1.21.9; before, the default skin), one at a time,
  1.5 seconds apart at least, each name once.
- When the world or the server closes, or you leave it, everyone you saw stays where they
  were last, as they looked, on what they rode: as far as this client knows, they logged off
  then.
- `offlinePlayers` off turns all of it off: nobody is shown or remembered.
- `maxOfflinePlayers` of them are drawn at once (256; up to 1000 on the slider, then no limit): the nearest the
  camera in its dimension, chosen again every tick (`RestingPlayers.shown`). The others stay
  remembered and lose their puppet until they are among the nearest again.
- Each puppet is dressed and given its mount from saved data, so they are made a few a frame
  (a millisecond, one at least; `GhostRenderer.rest`), the nearest in view first, then those
  out of view: joining a server where many logged off spreads them over a few frames.

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
  out on the client from the blocks it has, off the game's thread: the chunk sections around
  their spot (`roam` + 4 blocks across, 32 below and 48 above) are copied a few at a time (96
  sections per tick for all of them and their mounts together, `track/BlockCopy`), then
  searched on threads of their own (`track/GroundWork`), beds and chests looked for in the
  same go. The area is read again every 30 seconds, when they find themselves off it, or when
  a step of their way can no longer be stood on. Each way is searched towards where they go,
  in the copy the area was read from. Dodging and walking back after a blast are small
  searches made on the spot.
- **Monsters** within 10 blocks and in plain sight put them on guard: an iron sword drawn,
  the shield up, eyes on the nearest, a step away when it comes closer than 3.5 blocks. They
  never fight. Safe indoors, behind walls, shut doors or windows, they ignore them.
- **A creeper** blowing up within 7 blocks, in sight, throws them, shield up, and they walk
  back.
- **Hit by you**, they raise their shield.
- **Looked at** by you (the nearest under your crosshair, within 6 blocks, nothing in
  between), they look back for as long as you look and 1.5 seconds more: standing, they turn
  to you, and facing you they crouch while you crouch; walking, seated, fishing or on a
  mount, they turn their head only and never crouch.
- **In the rain**, six players in ten (by their name, always the same ones) walk to a spot
  they can reach with a roof or leaves within 20 blocks above, one of the four nearest, as soon
  as the rain reaches them, and stay there, standing or seated, while it rains; at night, after
  looking for a bed. The others carry on.
- **In the evening** (from 11000 ticks into the day until dark), at each choice they go, six
  times in ten, to stand in front of a campfire, a soul campfire, a furnace, a blast furnace or
  a smoker they can reach, one of the three nearest, on a spot beside it nobody else stands on
  or walks to, for 15 to 30 seconds.
- **A chat**: by day one of them may walk up to another within 24 blocks who is idle, seated
  or looking at something, to a spot 2 blocks from them; the other waits, turned to it, then
  both talk face to face for 20 to 40 seconds, each nodding while it speaks, in turn. Never a
  third, and never another pair within 16 blocks. Night, a monster, a hit or a push of more
  than a few blocks ends it.
- **At the water's edge**, where open water runs 3 blocks out from a spot they can reach, they
  may fish for a minute or so: a rod in hand, the bobber on the water with the game's line,
  a fish's wake now and then, a bite that pulls the bobber under, the line reeled in and cast
  again. Nothing is caught. Never two within 16 blocks of each other at once, those walking
  to the water included; at night they leave it for a bed.
- **Walked into** by you or what you ride, they are shoved out of your way (a quarter of a
  block per tick at most, never into a wall), getting up if they sat; never out of a bed. A
  walk that runs into you for two seconds in a row is given up.
  They never push you: nothing of them exists on the server.
- **In lava or fire** they show the swirls of fire resistance; **under water**, those of
  water breathing.

Where the blocks are not loaded, they only stand or sit where they are, standing at night. With
`offlineLife` off, they sit on their spot.

### Far from the camera

Past 64 blocks they have no life of their own (`track/RestingLoop`): nothing is decided,
but once a second they are still kept out of water (below). They play one of the animations
distant mobs play (`track/MobAnimations`) in a loop around where they stand, on the ground
read there (`track/MobPaths`, read again every 30 seconds while some of it is missing),
sitting in some of its pauses by day. At night they stand in those pauses, or stay in their
bed until the morning if they are in one. All the loops together, riders' mounts included,
start at most two such reads a tick (`RestingLoop.READS_PER_TICK`), each copying the blocks
around on the game's thread first.

- Where none of the mobs' animations fits (indoors, on a roof, a balcony), they play a
  cramped one (`MobAnimations.CRAMPED`: a step or two and back). Where not even that fits,
  or while the ground is not read, they turn on their spot every 8 seconds, as mobs do, and
  sit in one stance out of three: never a statue.
- The loop starts wherever their life left them (beside the bed in the morning): they never
  walk back to their spot. Their spot stays the middle of where they may go: ground farther
  than `offlineRoam` from it is left out when the animation is chosen (`MobPaths.within`).
  Mounts do the same.
- Only a figure left standing in water when its rest place is found (`forgetShore`), or one
  that logged off flying and is put on the ground under it, is moved there.
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

Someone who logged off in the air (flying with an elytra, jumping, falling) rests on the
ground under their spot, or on the water under it as above.

Where they rest, when it is not their spot (`RestingPlayers.Rest`: the ground under it, the
shore, or the boat and the water's surface), is kept with the spot and saved in the file: the
figure is made again whenever they leave the nearest drawn and come back, or the world is
opened again, and starts there at once, near or far, its blocks loaded or not. Until the
blocks around the spot have been loaded once, the far world (Voxy or Distant Horizons) is
asked what is under it: a spot well above its ground rests on it, one on or over water in a
boat. That guess is not kept; the blocks decide again once they are there.

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
| Aircraft left flying | Round and round at its height, near or far: from its spot along the way it faced, at the pace it went (0.2 to 1.2 blocks a tick), round a point 16 to 48 blocks beside it | The same |
| Aircraft left still in the air | Hovers on its spot | The same |

Another mod's mount goes by what it is: a living one walks like a horse, or flies like the
happy ghast without gravity; a water creature stays put. A vehicle its rider steers, left off
the ground, is an aircraft (`RestingMount.kindOf`, `HOVER` or `CIRCLE`): one going at least
0.15 blocks a tick circles, as a plane cannot stop in the air, one slower hovers. How fast it
went is in its save; where the client never saw it move by itself (a vehicle the server moves,
a copy the mod moves), `RestingMount.keepMotion` writes there the fastest its rider went over
the last two seconds (`FarPlayer.Pace`): the last positions of a player logging off come to a
stop before they leave. Off the ground or not is the vehicle's own word while the server sends
it, else the server's last copy of it; a copy older than the flight (made while it stood) says
it stands, and it is found in the air once the blocks under it are known. Anything else
neither alive nor a boat (a minecart, a vehicle on the ground) stays where it was. In the saddle they look
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
  `config/livinghorizon/mobs/<server>.json`. Every kind is in it by default, monsters too: one
  that despawned is forgotten like any other (below).
- **Land never explored**: a server with the mod also shares the animals the game will put
  in the chunks it has not generated yet, from the world's seed (see below).
- **Look.** A mob met up close keeps its exact look (the game's own save of it); one known
  only from the server is built from kind, variant and age, read off the game's thread
  (with `optBackgroundBuild`), since the server's first message can bring thousands. Its
  look is put into text only when the server says something new of it.
- **Saved data.** Kept as the game's tag, as it was saved or read, and never changed after:
  every copy is loaded from it, and a change (a pet sitting down) makes a new tag. It becomes
  text only when the file is written, every minute and when the world closes, on the files'
  thread (`track/SaveThread`): the game's thread only copies what the file keeps of each mob.
  What never shows (`MobMemory.UNSEEN`: a villager's memories, gossip and trades, which a
  scanned mob carries) is left out of the file, never out of the tag.
- **Opening.** The file is read on the files' thread too, and its mobs taken in on the first
  tick after (`MobMemory.adoptRead`): a mob met, shared or forgotten in between is newer than
  the file, which never brings it back. A save waits for the read first.
  A mob that leaves joins the list drawn at once, all those that left in one go added together.
- **Forgetting.** Coming back within range of a remembered mob that is not there forgets it.
- **Every kind of mob**, the game's and other mods', is shown from the start, walking, then left
  as the player sets it (`knownMobTypes`, offered once the game has registered them). Each client
  remembers other mods' mobs itself: the server only shares vanilla kinds.
- **Hidden kinds.** `hiddenMobs` beats every other choice, name tags included: a mob of a kind
  there (or of a mod named `modid:*`) is neither remembered nor drawn (`FarConfig.showsMob`).
- **Vehicles** that are neither mobs nor the game's boats (an aircraft, a car, a minecart) are
  told by what their class does, never by a list of ids (`track/Vehicles`): one whose class
  answers who steers it (`getControllingPassenger`, as a boat's does: Immersive Aircraft's
  planes, another mod's boat) is shown from the start; one that only places its riders
  (`positionRider`: a minecart, Superb Warfare's vehicles, Create's seats and moving
  contraptions, whose copies could cost a lot) is listed but waits to be chosen
  (`seatedVehicles`, kept by *Defaults* and a reset). Both methods are found on `Entity` by
  their shape, since their names differ between loaders. The class is the one the kind's
  renderer is declared for (`MobList.vehicle`, read from the game's map of renderers with
  Guava's `TypeToken`), so a kind is under the *Entities* tab of *Mobs & Entities...* from the
  start; a renderer declared for any entity tells nothing, and then the first one met does
  (`knownVehicles`; the *Mobs* tab holds the living kinds, mannequins excepted). A copy
  stands where it was left, as a boat does: no animation, and only this client remembers it.
  So a kind that is not alive, a boat included, is only *Shown* or *Hidden* there, never
  *Roaming* or *Still*.
- **Minecarts rolling on.** The server sends a minecart 128 blocks away at most (8 chunks, two
  less than an animal), but moves it in every chunk within its simulation distance of a
  player. One still moving when the server stops sending it rolls on along its rails
  (`track/MinecartCoast`: the server's rail physics, `OldMinecartBehavior.moveAlongTrack`,
  with its slopes, curves, powered rails and friction), while it stays within
  `getServerSimulationDistance()` of this player and on blocks this client has; off its rails
  it stops. In single player `ServerMobs` puts it where the server has it every few ticks, and
  its run ends once the server no longer moves it.
- **Mobs riding something** (a villager in a minecart, a cow in a boat) are drawn with their
  ride while the server sends them; only a stack with a player in it is left to that player's
  copy. A copy remembered riding keeps the id of the bottom of its stack (`Remembered.vehicle`,
  set by the scan too) and rides that one's copy (`MobMemory.ride`), seated and placed by the
  vehicle's own `positionRider`, wherever it goes; with no copy of it, it stays where it sat.
  The game makes a vehicle's passengers get off before it says the vehicle left, so
  `MobMemory.riders` keeps who rode what as of the last tick.
- **A save that fails.** On the client a chest boat or minecart has the game's own hold, 27
  slots, sized again only when it is loaded from a save, which the client never does: another
  mod's larger one that writes its slots one by one (Shippy Ships' galleon, 504) threw as the
  client saved it, and was never remembered. `EntityNbt.save` first makes the client's hold
  again, empty, at its size; the client never knows what is in it anyway. A kind whose save
  or copy still fails is not drawn far away, and the log says why, once per kind.
- **Other mods' mobs known by name** (`track/ModdedMobs`) get more: how they fly far away
  (`MobFlight`), and what their copy needs to be drawn the way their mod draws them. Support
  for another mod's mob is one entry there; what reaches into that mod lives in `compat/`, by
  reflection, so that nothing needs it installed. Known now: the nine birds of
  [Ambient Birds](https://modrinth.com/mod/ambient-birds) (`compat/AmbientBirds`), which wheel
  in the sky, their copy told it flies past as Ambient Birds tells its flocks (wings out, and
  past its fade-in, or it would be drawn perched and invisible).
- **Mobs this client made itself** (Ambient Birds' birds, which no server knows) can go where
  this client holds no chunk, which the server's never do. The game stops moving an entity
  there, and it would hang still for as long as the level lasts. Every half second the live
  mobs are looked at (`MobMemory.strand`): one outside the held chunks is remembered as if the
  server had stopped sending it, and its copy flies or walks on. Once its chunk is back and
  the game moves it again, the copy hands over to it, as to a mob sent again.
- **Mannequins** (from 1.21.9) are shown too, standing still: Distant Friends uses them as its fake
  players. Mobs in `stillMobTypes` (happy ghast, mannequin) stand still instead of walking
  their loop. A copy wears its mannequin's skin, from a player's name or a texture of a
  resource pack: the game looks it up when the copy is loaded, and `Puppets.skin` hands it
  over, since the game only does it in a tick a copy never runs. Its cape is hung from where it
  stands (`Puppets.cape`), where the tick would move it over time, or it would reach out
  towards 0, 0, 0.
- **Sitting pets.** A cat, wolf or parrot sitting when it was last seen sits in its copy and stays
  on its spot. The game saves a pet's order to sit from a field only the server has, so a save
  made on the client always stands it up: `MobMemory.onUnload` writes the pose the client sees
  (`isInSittingPose`) into the save's `Sitting` instead. In single player the server's pose is
  taken with each answer (below), and a copy walking up to its real pet sits or stands as it does.

### Animals of unexplored land

A chunk gets its first animals as the game generates it, from a random seeded by the world's
seed and the chunk's position alone (`NoiseBasedChunkGenerator.spawnOriginalMobs`). The same
draws are made for the chunks not generated yet (`track/SeedDraws`), so land nobody has explored
has its herds from afar, about where the game will put them.

- **How.** The biome is the generator's, read at the chunk's corner at the top of the world
  through the game's own zoom, as the game reads it; on 26.3 the animals and their odds are
  environment attributes, the dimension's then the biome's. The terrain is the generator's bare
  noise (`getBaseColumn`): no grass, trees or snow yet. A column topped with water or lava is no
  place for an animal, any other is, and a turtle only lower than the sea plus 4 blocks, as the
  game's rule says; the draws then go on as the game's do, so a herd stands where the game puts
  it wherever the bare terrain is right. Where the game turns a spot down for what the bare
  terrain lacks (no grass under it, a tree in the way), its draws and these part ways for the
  rest of the chunk: that herd is missing or elsewhere. The `seedmobs` game test generates
  a square of such chunks and counts how many stand within 2 blocks of the game's (about half).
- **Look.** The game draws a coat, colour or age with a random nothing can foresee: they are
  drawn from the mob's id, with the game's odds (a herd of horses one coat, a family of rabbits
  one fur, the first of a group never a baby; sheep by climate from 1.21.5).
- **Ids.** Each has a name-based id (version 3, from the seed, the dimension, the chunk and its
  place in it): the same every time, whoever works it out, and no mob the game makes has one
  (`SeedDraws.foreseen`).
- **Where.** Only a dimension of the usual terrain (`NoiseBasedChunkGenerator`, mobs not
  switched off by its settings, no ceiling) with mobs spawning (`doMobSpawning`, from 1.21.11
  where the game reads it there too).

Three ways in, all of them the same draws:

1. **The server, around its players** (`track/SeedMobs`): within `seedMobRange` (512 blocks) of
   each player with the mod, on a thread of its own, a pass every five seconds, up to 2048
   chunks a pass, the nearest first, shared with the remembered mobs. Any server with the mod,
   the one in a world played alone included.
2. **This client, by itself** (`track/Foresight`): within *Mob Render Distance* plus 64 blocks
   (4096 blocks without a limit), so that the far terrain of Voxy or Distant Horizons has its
   animals as it is drawn: 1024 chunks at a time, as soon as the last are done (every five ticks
   at most), those within 75° of where the player looks first, then the rest, each the nearest
   first (`Foresight.around`), so that a wider *Mob Render Distance* fills in where they look
   within seconds; and through a spyglass, along where it looks, up to 256 chunks, where
   they are then drawn whatever *Mob Render Distance* says and chosen before any other. The
   spyglass asks every four ticks for 256 chunks, the line of sight first and then wider, half a
   degree at a time (`Foresight.coneChunks`, only the chunks in view), on threads of its own that
   never wait behind a scan (`SeedMobs` `URGENT`, `URGENT_DRAWS`); what it finds is made into
   remembered mobs at once, and again every five ticks while it moves.
3. **By hand**: *Guess Nearby Mobs* (a button next to *Scan Nearby Mobs*, or
   `/livinghorizon foresee [radius]`), every chunk within `foreseeRadius` (64 chunks, up to
   1024), in parts of 65536 chunks, saying in the chat how far it got.

Who works out what this client asks for:

- **A world played here or hosted on LAN**: the server in this game (`SeedMobs.foresee`), one
  request at a time on a thread of its own, the draws on half the cores (the game and its server
  keep the others).
- **Another server with the mod whose owner shares the seed** (`shareSeed`, off by default): the
  server sends it (a `SEED` message, every five seconds) with, for each dimension whose generator
  is the game's own, its noise settings and biome preset (`SeedDraws.vanillaKeys`: the overworld,
  large biomes or amplified, with no biome of a mod or a data pack). This client builds that
  generator again from the game's own registries (`SeedDraws.vanilla`, `VanillaRegistries`, a
  second or two the first time) and draws on a thread of its own. It knows only the chunks it has
  held itself; a chunk generated elsewhere may show its foreseen animals beside its real ones.
  The `seedmobs` game test checks that the generator built again draws exactly the server's.
- **Any other server**: none but what the server shares around its players.

What this client foresees is kept per chunk, four numbers a mob (`Foresight.index`, 32 bytes): a
guess of 1024 chunks around finds about a million animals, some 40 MB. Only the nearest become
remembered mobs, every second (those where the spyglass looks first), as many as `maxDistantMobs`
lets be drawn, since no other could be; the others wait in the index.

The index is saved per world and dimension, apart from the mobs met
(`config/livinghorizon/foreseen/<server>/<world>-<dimension>.bin`, `Foresight.write`): on
leaving the world or the dimension, every five minutes, and once a guess is done. Gzipped, with
the chunks asked for (a bit each) and, per mob, its place among its chunk's draws (its id is
drawn again from the seed: `SeedDraws.id` keeps that place in its last 16 bits), where it stands
in the chunk to a 256th of a block, its height, kind, age and variant: about five bytes a mob,
some 5 MB for a guess of 1024 chunks around (a mob in the memory file takes a kilobyte). Read
again on coming back; in a world played here, the server first leaves out the chunks generated
since (`SeedMobs.unexplored`). *Forget All* deletes this server's files.

- **Generated.** A chunk is left out once it has its animals. On the server: loaded, or saved in
  its region file past the step that puts them there. The region's header says which chunks are
  saved (read again when the file changes); the game's storage thread is then asked, in the
  background, the status each was saved at (`chunkScanner`, that one field). A chunk the game
  only began, around one it generated (up to `features` or `light`), still gets its animals
  later: those stay foreseen. Its own mobs are there once generated, remembered by the server
  like any other, and the client forgets those foreseen as it forgets a mob the server stopped
  sharing. The client never draws one in a chunk it holds, and drops those chunks from its index.
- **On the client.** *Show Guessed Mobs* (`seedMobs`, on, beside *Show Mobs*) hides them and stops foreseeing. The screens call them guessed mobs: a guess of where the world would put them, not a sighting.
  They are never saved in the memory file (the index has its own), those the server shares are taken only within
  *Mob Render Distance* plus 64 blocks, and they do not count in its 2000 mobs. The debug boxes
  and outlines show them in pink (*Foreseen Mobs*).

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
the remembered mobs are (`track/ServerMobs`), the guessed ones left out: the server never has
them. Its answer is applied to the mobs it found, and to those it moved the time before.

- The ones it still moves (past the distance it sends them at, but inside the simulation
  distance) are followed by their copies, at its pace and facing the way they face, with no
  animation of their own.
- A pet sits or stands as it does there, which the client is never told once it is far.
- One that dies there is forgotten, and what the server shared of it before is set aside until
  it says something new: read again, it brought the copy back for a few seconds.

Without this, the copy would stand where the mob was last seen while the real one wandered
off, then run or jump to it once it was sent again.

### Ground and animation

Once its copy is drawn, the blocks within 8 blocks of a mob (a square of 17 by 17 columns,
`track/MobPaths`) are read, a few mobs per tick, from loaded chunks or from the far world
(Voxy, Distant Horizons, Lodistance, Far View or Ecstatic): the loaded sections are copied on the game's thread
(`track/BlockCopy`) and the ground and animation worked out on `track/GroundWork`'s threads,
so the answer comes a tick or so later. They are read again when the mob moves, and later and later while
columns are missing; a ground read whole is saved with the mob and used at once next time.
Mobs that stand still and mobs the server moves read nothing, phantoms and ghasts fly (see
below), and other flyers and swimmers get a flat disc at their own height.

- **The ground**: in each column, the floor nearest the mob's height with room above it.
  Grass, flowers, crops and snow layers count as air; a path dug with a shovel counts as
  ground; water, leaves, fences and walls are not ground. Voxy gives every block of the
  column, Distant Horizons only its top one, Lodistance up to three runs of blocks, Far View its
  top one and, under trees, the ground and where the leaves start, Ecstatic only the height of
  its ground.
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
  rabbit's legs kicking. A chicken flaps while it steps up or down a block. The ender dragon
  beats its wings at the pace its own tick gives them (slower the faster it flies, quicker as
  it climbs) and records its heights and headings each tick, which its renderer reads to turn
  its body and bend its neck and tail; its yaw points behind it, so `MobMotion` turns what it
  reads from it and writes into it by 180 degrees (`MobGait.backwards`). Impostors are baked
  with all of it at rest; the dragon is never one (`GhostRenderer.unpictured`).
- **Flying** (`track/MobFlight`): a phantom circles 5 blocks above where it was last seen, 7 to
  13 blocks out, either way round, now wider, now tighter, rising and dipping; a ghast drifts
  in slow loops within 10 blocks of its spot, 3 up or down; a bird of another mod
  (`ModdedMobs`) flies figures of eight 6 blocks above its spot, 6 to 11 blocks out, at a bird's
  pace (0.22 blocks a tick, Ambient Birds' own), 2 up or down; the ender dragon soars in rounds
  30 to 45 blocks out, either way round, at 0.45 blocks a tick, climbing and diving 5 blocks on
  either side. Like an animation, where it is
  comes from its UUID and the clock alone, and no ground is read. It faces where it flies and
  tilts as it climbs or dives, and flies back to its spot as the player comes near.
- **No teleporting**: when the server reports a drawn mob somewhere else, its copy walks to
  the new spot in a straight line (the ground there is read again meanwhile), then plays the
  animation chosen there.

### Handing over to the real mob

- **The near zone.** A copy is never drawn where the real mob would be sent
  (`MobMemory.nearRange`): the smaller of how far the server sends that kind (the *Entity
  Distance* slider in a single player world) and the server's view distance, less 16 blocks,
  never under 24. There the copy would stand where the real mob is not, close enough to walk
  up to. The simulation distance does not shrink that zone. With the debug boxes on, it shows
  as "too close for a copy".
- **The server's view distance, not the render distance** (`track/ServerView`). The game's
  effective render distance is both in the game alone. Bobby makes it the client's whole
  setting, since it draws the chunks it saved past the server's; those hold no entities. What
  is about terrain (far depth, the ground under a mob, birds) reads the render distance; what
  is about entities the server sends (this zone, a far player lost inside it) reads
  `ServerView`, the render distance cut to the view distance the server announced.
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

- `maxDistantMobs` of them are drawn at once (512, up to 4000 on the slider, more when typed, then no limit at the slider's right end): the nearest remembered
  ones. Past `mobMaxDistance` (512 blocks) none are drawn. Both that distance and the near zone
  are applied after the choice, so copies there still take places and fewer may show.
- With `onlyOverTerrain` (*Only Over Terrain*, in *Debug Options...*, on), a mob is drawn only where terrain is drawn
  under it (`compat/DrawnGround`): a chunk the game holds within its render distance, measured
  as the game picks its sections, or else a chunk the far world has terrain in, within Voxy's or
  Distant Horizons' own render distance (`LodWorld.drawnDistance`). The far world is asked a
  chunk at a time, on four threads of their own (`LodWorld.ground`), 256 chunks at most waiting,
  each answered as it comes. Distant Horizons is asked through its database (`DhSource.holds`):
  it keeps its terrain by sections of 4 by 4 chunks, with how far each column was generated, and
  one small query a section says which of its chunks hold terrain; a column read through its API
  loads or builds the section's terrain, a quarter of a second each. A version whose classes
  differ reads columns instead, as Voxy does. Any read of the far world that finds blocks (a
  mob's ground, most often) notes its chunk as terrain too (`DrawnGround.noted`). A chunk found
  empty is asked again after 1, 2, 4... up to 10 seconds, as the far world fills in after a world
  is opened, and all of them at once when one turns out to hold terrain; one with terrain stays
  so until the world changes. Never a blink: only a "no terrain" answer hides a mob; while its
  chunk has no answer yet (or the far world's reads are paused), it stays as it was the frame before
  (`DrawnGround.Decisions`, by the mob's id, shared by the real mob and its copy). The game's
  chunks within three of the edge of its render distance are asked ahead, so the answer is there
  before a mob or the edge crosses them. The debug marks call a hidden one *over the void*.
- A mob smaller on screen than `animationMinPixels` (10 pixels; 0 always animates; its height
  or width, whichever is larger, and never under `minApparentPixels`) stops walking about
  (`optStillTiny`): its animation's clock stops (`MobMotion.held`), so it stands where the
  animation had it and goes on from there, with no jump, once played again. What still moves it
  (the server, walking back to its spot) moves its legs too: a mob sliding with its legs at rest
  looked like a glitch. Its `tickCount` goes on every tick, still or not: the game plays idle
  movements at `tickCount` plus the frame's part of a tick, and a stopped `tickCount` made them
  run one tick's worth and snap back twenty times a second, a trembling mob. It resumes at that
  size plus a quarter and a pixel, so it never flickers. Sizes use the field of view the frame
  is really projected with (`DepthFar.pixelsPerRadian`), so a spyglass's zoom counts.
- `animationMaxDistance` (blocks; 0 is no limit) stills every mob past it, however big
  (`MobMemory.isStill`).
- A still copy standing with nothing left to walk or turn (`MobMotion.settled`) is looked at once
  in sixteen ticks, each on a tick of its own (by its id): with Mob Render Distance at 4000 blocks
  and the land's animals guessed, tens of thousands far off would otherwise be gone through every
  tick. In between it is not touched at all: the ticks it rested are counted, and its clocks
  (tick count, legs, the animation's) are made up all at once (`MobMemory.catchUp`,
  `MobMotion.rest`) before it is drawn as a model or looked at again, so its idle movements do
  not snap back. A simplified image needs neither: it is baked with no age and its legs at rest. One that moves (the server's, one called back to its spot) is moved every tick:
  looked at once in sixteen, it went in jerks.
- Copies are built (`MobMemory.build`) within 2 ms a tick, those in front of the player first
  (70° either side), each the nearest first. A short saved text (a foreseen mob, one built from
  what the server said) is read at once: queued on the game's background executor, it waited
  behind the world being generated, a few copies a second.
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

In game: three switches, each on by default and on its own: *Simplified Mobs* (`impostors`, mobs
and birds), *Simplified Entities* (`impostorEntities`: boats, vehicles and mannequins, the
*Entities* tab of the list of mobs, `GhostRenderer.creature`) and *Simplified Players*
(`impostorPlayers`). Past `impostorDistance` blocks, a distant figure is not extracted and
submitted as a model: a flat picture of it, always turned to the camera, is drawn instead
(`render/impostor/ImpostorRenderer`). A figure 200 blocks away is a handful of pixels tall, so the
picture loses nothing, and the game no longer builds a render state and a model for it every
frame. A boat or a vehicle is pictured from every height, as a phantom or a ghast is
(`ImpostorViews.HEIGHTS`): an aircraft flies overhead, and a parked one is seen from a cliff.

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
- **Light.** Colour only, without a shader pack that reads normal maps: the game's entity
  shaders have no use for one. The pictures are lit by the game's world light as a model is (turned over, the way the picture
  is drawn), and the quad's normal points up, which that light leaves at full brightness.
  Only the light where the figure stands (sky, torches, night) is applied on top. Under a
  shader pack, Iris works out a quad's normal from its corners, so `ImpostorLean` leans the
  quad back, without changing how it looks on screen, to face it where the light should come
  from. A figure is baked at full light: some renderers keep the light they read as they
  extract (Immersive Aircraft's, from 26.1), so `GhostRenderer.extract` hands the light over
  then too, through `EntityRendererLightMixin`, and does the same for the models it draws far
  away, where the client may have no light yet.
- **Normal maps** (`impostorShading`, from 1.21.6). Under a pack that samples normal maps
  (Iris's `shouldBindPBR`: Photon with *Normal Mapping*, labPBR packs), the pictures are baked
  lit evenly (two opposite lights far stronger than the cap, `ImpostorAtlas.EVEN_0`) and each
  page gets a normal map beside it: the bake's depth is read back with the colours, the slope
  between neighbouring pixels gives each pixel's normal (`ImpostorPixels.normals`, no slope read
  across a step of more than 3 pixels), turned onto the quad `ImpostorLean` leans back by 45
  degrees, red along the picture's right, green along its down. Iris is handed them by a loader
  registered for the page textures' class (`compat/IrisNormals`, its loader registry, not its
  API), which answers with a texture that only borrows the normal map, since Iris closes what
  it is handed when it reloads. A sheet then has twice the levels, saved under a fingerprint
  of its own, and takes twice the memory in the budgets below. A pack that reads no normal map
  keeps the pictures the game shades: lit evenly, they came out brighter than their models.
  From 26.3 the game's copy of a depth texture reads a colour buffer that is not there (an
  OpenGL error, the screen black under a pack), so the depth is read straight from OpenGL there
  (`OverrideBake.readDepth`); under another renderer the normal maps are flat.
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

Each range has a backend of its own (`BakeBackend`: `OverrideBake` from 1.21.6, `DeviceBake` on
1.21.5, `GlBake` before), in a file wrapped whole in a Stonecutter guard: it makes the page
textures, draws and reads back the views, and writes a sheet into its page. Everything else
(the queue, the rows, staging a figure, what is done with the pictures) is shared in
`ImpostorAtlas`.

### Same look, same picture

`ImpostorKey` decides which picture a figure gets. Figures with the same key share one sheet.

- **In the key**: the type, the player's skin, the synched data that changes the look
  (variant, colour, profession, baby, pose; not health, air, score or being on fire), what
  it wears, however worn, and its box to about 6%: some mods' mobs take their size from a
  value that keeps changing (an Ice and Fire dragon's age), which the key leaves out.
- **Left out**: what never shows (positions, times, owners, sound variants, names but the four
  the game draws differently: Dinnerbone, Grumm, jeb_ and Toast) and,
  learnt per kind of mob, what keeps changing on one (a dolphin drying, a zombie raising its
  arms).
- **The same in every game**: the key is hashed so that the same figure has the same key in
  every world and every game (variants by name, records by parts).
- Two looks of the same kind whose pictures come out the same (their full-size pixels hash
  the same) share one sheet too.
- **Worked out again every five to nine seconds** per figure, each at a moment of its own
  (`ImpostorKey.look`): worked out for the whole crowd in one frame, it made the frame rate drop
  every few seconds. The figures not asked about for a while are forgotten, never all of them
  at once, which had thousands worked out again every second.
- **Asked for ahead**: once a second, the pictures of the figures on hand are asked for, near or
  far, so that they are baked before a figure walks past `impostorDistance`; past 1024 copies,
  a share of them each second (`GhostRenderer.prewarm`).

### No blinking

- A figure becomes an impostor past `impostorDistance`, or shorter on screen than `impostorPixels`
  (its height or width, the larger, in the window's pixels), and a model again only 5% inside both
  (`ImpostorThreshold`). Birds too. Either may be 0, off: the other alone decides.
- A figure the game draws itself is never a picture, and the game draws other players ten times
  as far as other entities, far past both thresholds. `GhostRenderer.pictureInstead` takes them
  out of the game's frame (`EntityRenderDispatcherMixin`, with the mount carrying one alone) once
  they pass a threshold, and the mod draws them: a picture, or the model while it is baked.
- It keeps its model while its picture would be shown more than twice the 64 pixels it holds
  (a dragon twenty blocks long just past the distance), and a picture kept stays one until 5%
  past that.
- It keeps a picture until it has turned 8 degrees past the edge between it and the next one.
- A mob or player model nearing its picture turns to it: from 80% of either threshold to the
  point a picture would come back (95%), its body is eased to the yaw the picture will show it at
  (`ImpostorViews.yaw`, the same 8-degree stickiness), head straight and level
  (`ImpostorThreshold.approach`, `GhostRenderer.turn` / `turned`). Only once its sheet is baked
  and not too large to be shown; never on a mount or in a bed. The picture then starts on the
  view the model had turned to. *Smooth Simplification* in the debug screen (`impostorTurn`)
  turns it off.
- When its look changes, a player or a mob keeps its old picture until the new one is baked;
  a 3D bird is drawn as a model meanwhile.

### Video memory

- **Layout.** Each kind of figure has pages of its own, one sheet per row (512 pixels wide):
  4 rows, then 8, 16, then 32, every tile 64 pixels. A sheet takes 170 KB of video memory
  (512 by 64 pixels and three smaller levels).
- **Large figures.** A figure larger than its box (a horse's muzzle) is drawn smaller in its
  tile rather than cut off: at a hundred blocks a figure is a dozen pixels tall. Its picture
  covers up to five times its box (an Ice and Fire dragon's tail needs nearly four); one still
  cut off there keeps its model far away, and the log says so once per kind.
- **Copies' size.** A copy loaded from a mob's saved data works its box out again
  (`EntityNbt.load`): mods that size their mobs in the tick (Ice and Fire's dragons, by age)
  left a copy, which never ticks, with a newborn's box.
- **Mobs** keep their sheets, up to `impostorMobMemory` (1 GB) in all, taken only as looks are
  met (pages are taken 4 rows at a time and more: 50 looks of one kind take about 10 MB, 50
  kinds of one look each about 35 MB). Past that, an unused sheet of the same kind gives
  way, or the figure stays a model for 30 seconds.
- **Players** (riders with what they ride included) have `impostorPlayerMemory` (512 MB) of
  their own, some three thousand sheets, thrown away after a minute unused. They never take
  the mobs' room, nor the mobs theirs.
- A sheet drawn or asked for in the last 10 seconds is never replaced.

`SheetAllocator` holds the pages, their rows and the budgets. It asks the game nothing (the
clock and the page textures are handed in), so `SheetAllocatorTest` checks it on its own.

### On disk

`ImpostorDisk` keeps the mobs' sheets in `config/livinghorizon/impostors/<fingerprint>/`, so
that a mob met in an earlier game is read back rather than baked.

- The fingerprint is the game version, the resource packs chosen and their files, the jars in
  `mods/` and how a sheet is baked (`BAKE`). It is worked out on the disk's thread, at the start
  and after each reload; the mobs wait the moment it takes, so they are read back rather than
  baked. The folders of the last three fingerprints are kept: a resource pack switched off and on
  again finds its sheets. Its text is kept beside the sheets (`fingerprint.txt`), and the log says
  which lines changed when an older folder is thrown away ("the saved mob pictures of ... are
  thrown away").
- Each file holds when and in which world or server its sheet was baked, and how far down its
  tiles the figure's origin stands (file `FORMAT` 4; a file of 3 still reads, its origin where
  its kind puts it, and one of 2, its date unknown). The impostor screen shows it under each name, with
  where the pictures come from (`ImpostorAtlas.Origin`): *Shared*, read back from disk; *New*,
  baked in this game and saved; *Not saved* (players, riders, birds).
- A sheet read back is checked against the size of a sheet (`ImpostorDisk.decode`) before
  anything is allocated: a damaged file is baked again, it never asks for more memory.
- 4096 sheets are kept per fingerprint, some 35 KB each once compressed (about 140 MB), counted
  when the folder is opened, at the start and after a reload: a game may write more until then. The
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
- **Phantoms, ghasts and other mods' birds** (happy ghasts included, `MobFlight.pictured`) are
  mostly seen from below: like a bird, one sheet per height they are seen from (well below,
  below, level, above: `ImpostorViews.HEIGHTS`, part of the key through `ImpostorKey.seenFrom`), baked
  turned round their middle (`ImpostorAtlas.MIDDLE`), facing the camera entirely when drawn.
  All four are asked for together; kept on disk like any mob's. Boats and vehicles are
  pictured the same way, and so are Ice and Fire's flying mobs (dragons, amphitheres,
  hippogryphs: its `IFlyingMount`, looked up by name in `compat/IceAndFire`). A picture turned up to the camera round the middle shows what is
  nearer than that middle (an aircraft's nose, the wheels under it) farther along the line of
  sight, under the ground the figure stands on when seen from above: `GhostRenderer.picture`
  moves it nearer by half its size and makes it as much smaller, the same on screen.
  The middle is the box's, which is not always the figure's: Shippy Ships' galleon has a box
  9 blocks high and masts 40 blocks up, so half of its pictures stayed empty below the hull and
  its masts were cut off even five times larger. One cut off is first moved down or up its tiles
  until it stands in their middle (`ImpostorPixels.centred`, between `FEET` from the top and as
  far from the bottom), then grown if it still needs room; how far down its tiles its middle
  stands is kept with the sheet, on disk too.
- **Players who logged off** are impostors in every posture: standing, seated on the ground,
  or in a bed. A posture other than standing is part of the key
  (`ImpostorKey.posture`), and the picture is baked in it:
  - seated: in the riding pose, drawn lowered to the ground as the model is;
  - in a bed: along its body, baked raised (`Posture.BAKED_LIFT`) so that none of it
    falls below the tile, and laid along the bed.

  `render/Posture` seats or lays a figure down the same way for the model and for the picture.
- **Riders** (players online or not, on a horse, a camel, a happy ghast, in a boat, a raft, a
  minecart...) are baked in one picture with what carries them (`ImpostorKey.riding`,
  `ImpostorAtlas.Rider`): the rider seated where it sits, turned as it is turned. A vehicle has
  no body to turn in its state, so the entity itself is turned for each view and put back
  after the bake. Filed as a player's sheet, never kept on disk. On what is pictured from
  every height (a boat, a vehicle, a happy ghast), the rider is too, both turned round the
  middle of the two together (`ImpostorAtlas.middle(mount, rider)`).
- **Mobs drawn by another mod's own renderer** whose state is not a living one (Ambient Birds'
  birds) read their body from the mob itself: the mob is turned for each view the same way,
  or all eight pictures would show the side it faced.
- **Never impostors**: a vehicle carrying more than one passenger (drawn as models, all of
  them), a mob carrying another that is not a player, the 2D birds, parrots and bats, and the
  types `GhostRenderer.drawable` leaves out (GeckoLib 4). Nor drawn far away at all: what
  Valkyrien Skies carries on a ship (`compat/ValkyrienSkies`), which sits in its shipyard and which
  its renderer places on the ship itself.
- **Lycanites Mobs** (Forge 1.20.1) queues most of its models and draws them at a later stage of
  the frame: `compat/LycaniteMobs` draws that queue after each view is baked, and after the far
  figures, before the far terrain's depth is taken back.

### Preview

*Picture Preview...* in the settings, or `/livinghorizon impostors`: every figure baked,
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
| Lodistance with Sodium | Lodistance, at twice its terrain's distance: `FarDepth` moves it to its real depth, around the entities only |
| Lodistance without Sodium | `FarDepth`, from Lodistance's own depth texture, the same way as Distant Horizons' |
| Far View | Far View itself, with the game's projection: nothing to do (not with its *Seamless* option, see [Far View](#far-view)) |
| Ecstatic | Nobody: it draws after the entities, tested against their depth ([Ecstatic](#ecstatic)) |

### Links to Voxy, Distant Horizons, Lodistance, Far View, Ecstatic and Iris

None of the six has an API for what the mod needs, or not all of it: they are reached by
reflection, each through one class, `VoxyLink`, `DhLink` (what `DhDepth` and `DhSource` share),
`LodistanceLink`, `FarViewLink`, `EcstaticLink` and `IrisLink`. A rename on their side is
followed there and nowhere else. What an older or newer version lacks and the mod can do
without is a fallback (`Links.optional…`), noted by the link; what it cannot do without turns
that part off for the session. `/livinghorizon lod` has one line per link: linked, not
installed or off and why, the fallbacks in use, and how often the mixins into Voxy
(`renderOpaque`) and Distant Horizons (its fades) ran. Those two may find nothing to hook
(`require = 0`): a count that stays at 0 while the mod draws is a renamed method.

### One frame

`FarDepth`'s steps run in this order, each frame: `noteView` (the matrices; everything kept for
the frame, `FarDepth.Frame`, is forgotten here), `noteTarget` (Voxy draws its terrain), `arm`
(the entities are submitted), `before` and `after` around the solid step, from 26.1
`beforeTranslucent` and `afterTranslucent` around the translucent one, `beforeFade` and
`afterFade` around each of Distant Horizons' fades, then `drawDebugView` with the HUD. A step
that finds the one before it missing (a hook lost to a rename, another mod changing the order)
says so once in the log and in `/livinghorizon lod` ("steps out of order"); the `depthview`
game test fails on it.

The shaders' depth arithmetic is written once, `shaders/include/far_depth.glsl`: the OpenGL
programs splice it in (`GlPrograms`), 26.3's take it with `#include`, and `DepthMath` mirrors it
in Java for `DepthMathTest`. The `depthview` game test compiles every program, those only Voxy,
Distant Horizons or Lodistance would use included.

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

### Lodistance

Lodistance (Fabric, 26.3 only) has no API and no source code: `LodistanceLink` reads the fields
of its own classes, never writes one, and turns itself off for the session when one is missing.
It refuses Voxy and Distant Horizons (`conflicts`), so it is always alone. Its jar was
decompiled to find out how it draws (0.8.7: no network but its own server channel, no files
outside the game folder); nothing of it is copied here, its licence reserves all rights.

It draws its terrain one of two ways:

- **With Sodium** (`PearlLodRenderer`, its default), inside Sodium's opaque terrain pass, into
  the game's own depth. Its vertex shader replaces the depth of each point by
  `A + 0.5 * m32 / w` (A 0 when the depth is reversed, 1 otherwise), half the depth the game's
  projection gives that distance, so that the game's terrain always wins over it. A distant mob
  behind one of its hills is hidden only past twice the hill's distance, and shows through it
  otherwise. (Its formula leaves out the projection's far plane: past half of it, its terrain
  comes in front of the game's depth for the same distance instead.) `FarDepth.mergeLodistance`
  moves those pixels to their real depth around the entities, either way, past the far plane to
  the far plane, and `restore` puts Lodistance's back. A pixel is taken for its terrain only where,
  read as the game's own, it would lie farther across than any of the game's chunks
  (Lodistance leaves out the chunks within the render distance less one, and the game draws
  none past it): the game's terrain is never touched.
- **Without Sodium** (`LodRenderer`), into a framebuffer of its own, at `prepareTranslucents`
  inside the world's pass before the solid step, then laid over the picture where the game drew
  nothing. None of it reaches the game's depth: it is merged like Distant Horizons', through the
  inverse of the projection it was drawn with.

The occlusion queries read the merged depth, so *Skip Hidden Mobs* works behind its terrain too.
Its fog: it pushes the game's render distance fog out to where its own terrain's fog runs
(from `fogStart` times its distance to its distance), but its terrain has none of the game's
atmospheric fog (1024 blocks in a clear overworld), which still reached the entities: a mob a
thousand blocks away was a flat patch of fog colour on clear hills. `FarFog.lodistance` gives
the entities its ramp alone, so within the game's render distance they are a little clearer
than the game's terrain behind them; where the atmospheric fog ends before the render distance
(the Nether, thick weather) the game's stays.

`LodistanceSource` reads the ground of its regions held in memory (up to three runs of blocks
per column, the block at the top of each, water over them; no biomes) without its locks, and
`LodWorld.drawnDistance` its distance setting. Not done yet: Vulkan (the GPU device path, where
the merge could not be taken back and would change how its terrain looks against clouds and
particles) and shader packs, where it is left alone (`mergeLodistance` says why).

### Far View

Far View (Forge and NeoForge, 1.20.1 to 1.21.4, no API, no source published) keeps a height
grid of the terrain the client has received, in regions of 128 by 128 columns (`FVWorld`), and
in single player also reads the world's region files and generates chunks around the player
on the integrated server. It meshes that grid and draws it with NeoForge's
`AFTER_SOLID_BLOCKS` stage into the game's own picture and depth, before the entities, with the
game's projection pushed out to its distance (`getProjectionMatrix`). Our figures are hidden
behind it like any mob behind a block, and the occlusion queries see it; its haze is the game's
terrain fog, which the entities share. Under a shader pack it draws in Iris's terrain phase.

- **Its world** (`compat/FarViewSource`): the top block of each column and, under trees, the
  ground and where the leaves start. Its map of regions is a plain one, filled on the game's
  thread: once a second the game's thread copies the regions' arrays into a map of ours
  (`tick`), which the reader threads read. It draws nothing in a dimension with a ceiling,
  nor once switched off (Ctrl+H): its world reads as empty there.
- **Its *Seamless* option** (off by default, never under a shader pack) draws its terrain with
  a projection of its own squeezed into the back of the depth range (`glDepthRange`). A
  figure's depth cannot be compared with it there: a mob shows through its hills. *Skip Hidden
  Mobs* then asks the lines of sight through its grid instead of the depth
  (`FarViewLink.squeezesDepth`); a mob half behind a hill is still drawn whole.
- **Its far mobs** (on by default): past 128 blocks and up to the render distance it cancels
  the game's model of a live mob (`EntityRenderDispatcher.shouldRender`) and draws a box of its
  own. The game leaving it out, `GhostRenderer.unseen` would draw it too: it asks
  `FVFarMobs.suppressModel` first (`FarViewLink.drawsMob`) and leaves those to Far View. With
  its boxes off, the game draws them and we draw the rest past the render distance. In single
  player it also has the built-in server send mobs out to the render distance.

### Ecstatic

Ecstatic (NeoForge 1.21.1) draws the overworld past the render distance from the world's seed,
in single player only. It draws its terrain into the game's framebuffer after the entities
(`RenderLevelStageEvent`, `AFTER_PARTICLES`), tested against the game's depth and written into
it: our figures are already there, so its hills hide them like any block, and nothing is
merged. But it projects its terrain with a far plane of its own (at least 16,800 blocks; the
game's near plane), and depths agree only between projections with the same planes. With the
game's nearer, a hill a hundred blocks behind a mob 300 blocks away has a smaller depth than the
mob and is painted over it (`DepthMathTest`). So while Ecstatic draws, the game's far plane is
Ecstatic's (`EcstaticLink.farPlane`, read from its renderer, in `GhostRenderer.farPlane`). The
depth view's first three pictures are taken around the entities, before Ecstatic draws, and
never show it; its fourth, *Whole World*, is copied once the world is drawn
(`FarDepth.worldDrawn`, at the end of `renderLevel`): its hills there sit at their real
distances, shaded like the game's terrain, only when the two far planes agree.

Its fog: it moves the game's fog out to a hundred times the far distance (the entities'
included) and fogs its terrain itself, without a shader pack, with the game's `linear_fog` in
its own shaders: every way round, from the render distance to an end past its outermost ring
(its fog reaches 45% there, times its *fog falloff*), in the game's fog colour. Distant mobs had
no fog on its fogged hills. `EcstaticLink.fog` gives the world's entities that same ramp
(`FarFog.chooseSmooth`, set around the entities through `GameFog`), which the entities' shaders
apply the same way. Under a shader pack the pack's fog stays. Ecstatic advises against shader
packs for now and its Iris support is not done: not checked here.

Nothing of it runs in the game tests (NeoForge 1.21.1, which has no client game tests): it is
checked by starting the `1.21.1-neoforge` client. Its source was read at 1.4.1 (October 2026): no
network at all, no process, no class loading; files only under `config/ecstatic` and the
world's save, and a PNG the player picks in a file dialog; `Unsafe` only to unmap its files.

`compat/EcstaticSource` reads its grid of heights and biomes (one file per level of detail, a
sample every 2 to 32 blocks): the ground for the mobs and *Only Over Terrain*. Ecstatic frees a
file's memory as its server stops, without forgetting it; a read holds the file's lock, as its
own reads and its closing do, and reads nothing from a closed file.

### Only around the figures

All of this only matters where far figures are, often a narrow band near the horizon. So
`FarDepth.region` works out one rectangle of the picture per frame, and every copy
(`GlCalls.copyDepth` of that rectangle) and every pass (the merge, Voxy's blit and
Lodistance's included, the restore, the fade's mask) is held to it with the scissor
(`optDepthRegion`, *Local Far Depth*):

- it holds every far figure (`FarDepth.figure`), the nearer ones and the birds
  (`FarDepth.around`), and each box the occlusion queries test, eight pixels wider;
- outside it the game's depth is never written, so nothing has to be put back there, and the
  copies hold an older frame that nothing reads (`intoVoxy`'s rectangles are held to it too);
- it is the whole texture when the figures are not all known before the entities are drawn
  (before 1.21.9 the birds and impostors are drawn after `FarDepth.before`;
  `FarDepth.figuresKnown` says when they are), when something far is drawn with no place
  given (`FarDepth.neededAnywhere`), when a sphere reaches behind the camera, past three
  quarters of the picture, and with the depth view on.

Voxy's depth blit (0.2.16 to 0.2.20) leaves the scissor alone; one that turned it off would be
caught after the blit and the whole texture used from then on. The `depthregion` scenario
checks the rectangle and that the occlusion queries answer the same with it as without.

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

### Asking OpenGL as little as possible

With the driver on a thread of its own (NVIDIA's *Threaded optimization*, Mesa's glthread),
every `glGet*`, `glIsEnabled` or `glGetQueryObject` stops the game until the driver has caught
up. The depth steps and the occlusion test keep those few:

- **Uniform locations** are looked up once, when each program is made (`GlPrograms.uniforms`).
- **Texture sizes and formats, framebuffer attachments** come from `compat/GlCache`. It asks
  again once the main render target changes size, and once a second whatever happens. The
  copies `FarDepth` makes are noted as they are made (`GlCache.made`, `deleted`), never asked
  about.
- **`FarDepth.GlState`** reads only what a step changes: the texture units it binds, and only
  for the steps that move them the scissor box (`intoVoxy`, Voxy's blit), the read framebuffer
  (Voxy's blit) and blending and the colour mask of every draw buffer (Voxy's blit, the
  restore into Voxy's framebuffer, the depth view). Our own framebuffers have no colour: the
  steps drawing there (the occlusion test, Distant Horizons' merge, the fade mask, `intoVoxy`)
  leave blending and the colour masks alone. Where a bracket runs two steps (the merge and the
  occlusion test, the restore and `intoVoxy`), one save covers both. One object, reused.
- **Voxy's framebuffer** (`noteTarget`) is asked for only under a pack that keeps Voxy's depth
  apart, the one case the merge reads it.

### OpenGL 4.1 and older

macOS never goes past OpenGL 4.1, and LWJGL answers a call to a function the context lacks
by aborting the JVM (`jni_FatalError`, no exception, no crash report). Every call past 4.1
goes through `compat/GlCalls`, which asks the context once and goes another way where the
function is missing:

- **Direct state access** (4.5): objects are bound, changed, and the binding found there put
  back. `bindTextureUnit` leaves its unit active, inside a `FarDepth.GlState` that saved it.
  The impostors' depth read back on 26.3 (`OverrideBake.readDepth`) is `glGetTexImage` there.
- **`glCopyImageSubData`** (4.3): the depth copies are blits between two framebuffers of its own,
  the scissor test off for them.
- **`glTexStorage2D`** (4.2) and the depth-stencil texture mode (4.3): `glTexImage2D`, and the
  mode's default, which is the depth already.

The one-draw occlusion path needs OpenGL 4.3 anyway (`OcclusionQueries.wordsUsable`); below it
the per-box queries run, as before. `OpenGlVersionTest` fails on a call past 4.1 anywhere else.
The game test checks it with Mesa held at 4.1: `MESA_GL_VERSION_OVERRIDE=4.1` and
`MESA_EXTENSION_OVERRIDE="-GL_ARB_direct_state_access -GL_ARB_copy_image"` (the log then says
"has no direct state access and image copies"); a call that slips through aborts the game there
as on a Mac.

## Fog on distant figures

From 1.21.6 the game fogs the world twice: a render distance fog at the edge of the loaded
chunks, and an environmental fog, linear from its start to its end (0 to 1024 blocks in a
clear overworld, nearer in rain). Voxy removes the first while it draws, and fogs its own
terrain with the second, which it stops growing past a limit: the render distance, at least
320 blocks, times the square root of 3 (1.5 render distances before Voxy 0.2.6). The game's
fog on entities has no such limit: past it, a far mob was paler than the land under it, down
to a flat patch of fog colour near the fog's end.

The game's shaders mix an entity's colour with the fog colour by the fog times that colour's
alpha. `compat/FarFog` ends the fog at Voxy's limit and multiplies the alpha by the fog Voxy
reaches there: the same fog up to the limit, Voxy's from there on.

- `FogRendererMixin` writes that fog into the game's fog buffer, after the game's own (256
  bytes in, room made for it), whenever the game writes its own.
- It is bound while the world's entities are drawn, solid and translucent steps, and the
  game's is bound again before anything else draws: the batch left in the buffers is drawn
  first.
- The terrain, the sky and the clouds keep the game's fog. Only the enchantment glint and
  lightning, which read the fog without its alpha, fade a little sooner.
- Without Voxy, with its fog off, with a shader pack (which fogs everything its own way), or
  with the game's render distance fog on, the game's fog stays. *Voxy Fog Fix* (`optVoxyFog`,
  debug screen, on by default) turns it off, glint and lightning included.

The `farfog` scenario checks it, with a limit standing in for Voxy's.

**Distant Horizons** takes the game's fog away altogether while it draws (both ramps pushed out
to 4×10¹⁴ blocks, `MixinFogRenderer`) and fogs its own terrain in a pass of its own: the
entities had no fog at all, a far mob sharp against fogged hills. Its far fog's thickness at a
distance `d` (`shaders/fog/*/frag.fsh`, its settings through its API) is, with `x = d / R` (its
render distance `R` in blocks) and `u = (x - start) / (end - start)`, `min + (max - min)` times
`u` kept to 0..1 (linear), `1 - e^(-u·density)` (exponential) or `1 - e^(-(u·density)²)`
(exponential squared, its default), across the ground or in every direction (height fog mixed
`SPHERICAL`). `compat/DhFog` gives the entities the same through the game's two ramps, whose
larger one times the colour's alpha is the fog: the alpha is `max`, one ramp a floor at `min`
(running from 10⁷ blocks behind to as far ahead, flat over any distance seen), the other the
slope, on the distance Distant Horizons uses. The slope is exact for a linear fog and a least
squares fit over two render distances for the others (off by 0.12 at most for the exponential
squared, 0.2 for the exponential, which thickens fastest at its start). Fitted again when its
settings change, read once a second. Left out: its height fog, and its *sky colour* fog colour
(the world's stays). *DH Fog Fix* (`optDhFog`, debug screen, on) turns it off. `DhFogTest`
checks the fit against Distant Horizons' formula, and the `dhfog` scenario the mobs in the real
game with Distant Horizons (26.3, `--with distanthorizons`), its fog made thick from 0 to 205
blocks. `/livinghorizon lod` says whose fog the figures get, its ramps in blocks, and how many
frames it was bound (`FarFog.status`, the *Fog on distant figures* line).

Before 1.21.6 the game's fog is not a buffer but one value (`RenderSystem`'s fog start, end,
shape and colour; `FogParameters` from 1.21.2), read by every draw until it is set again, and
a single ramp, smooth (`smoothstep`) from its start to its end. Distant Horizons pushes it out
to 4×10¹⁴ blocks there too, in `FogRenderer.setupFog` for the terrain. `FarFog.begin` and `end`,
around the world's entities in `LevelRendererMixin`, set Distant Horizons' fog through
`compat/GameFog` and put the game's back: one smooth ramp, in every direction or across the
ground, its colour's alpha times `max`, fitted by least squares over two render distances
(`DhFog.fitSmooth`; off by 0.044 at most for its default, 0.04 for a linear fog, 0.19 for the
exponential). There is no floor: a `min` above 0 is only followed on average. Voxy is not
there before 1.21.6, so nothing else changes. The block entities, drawn after, keep the game's.

**Ecstatic** (1.21.1) moves the game's fog out to a hundred times the far distance while it
draws and fogs its terrain with the game's own smooth ramp, in its own shaders, every way round:
from the render distance to `R + (E - R) / 0.45 × falloff`, `E` the edge of its outermost ring
and `falloff` its *fog falloff* setting (0.95 by default), in the game's fog colour. The same
`FarFog.begin` sets exactly that ramp around the entities (`EcstaticLink.fog`), as long as the
game's start is past fifty render distances: Ecstatic's own fog is on. Water, lava, blindness,
a foggy biome or a boss keep the game's.

## Skipping hidden mobs

*Skip Hidden Mobs* (`hideOccludedMobs`) does not draw a distant mob the terrain hides
entirely, which is cheaper with many mobs. `compat/OcclusionQueries` asks the GPU, against
the depth of the picture, where Voxy's or Distant Horizons' terrain already is (merged by
`FarDepth`):

1. Just before the entities are drawn, the box around each distant mob is drawn against that
   depth, writing nothing, with the matrices of the frame (`LevelRendererMixin`, `renderLevel`;
   `render` from 26.2). From OpenGL 4.3 every box of the frame goes in one instanced draw: the
   fragment shader runs after the depth test (`early_fragment_tests`) and writes a word for its
   box into a shader storage buffer, which is copied to a buffer of that batch behind a fence
   (`drawWords`): a dozen GL calls a frame, however many mobs. Without it, each box is drawn
   inside an occlusion query of its own (`GL_ANY_SAMPLES_PASSED_CONSERVATIVE`, or
   `GL_ANY_SAMPLES_PASSED` without `GL_ARB_ES3_compatibility`), five calls per box
   (`drawQueries`); a failure of the one draw falls back to it for the session.
2. The answers are read once the GPU has them (the fence passed, or the last query of the
   batch done), up to 4 frames of them waiting, never waited for.
3. A mob whose last answer is "no pixel passed" is not drawn. It is asked about again on the
   next frame that has room for a batch, so it comes back as soon as one pixel of it would
   show.

On 26.3's GPU device path (see [Distant Horizons](#distant-horizons)), the boxes are tested
in `compat/FarDepthGpu` and the answers read back from there, a capped number per frame.

Why the answer can be trusted:

- The GPU projects the box exactly as the mob is drawn, so the answer is the picture's. With a
  *Minimum Size*, the box is as large as the mob is drawn: model, picture and box take their
  size from the same arithmetic (`render/Apparent`), never under a player's height.
- Depth clamping keeps a box past the far plane at the far plane: the sky never hides a mob,
  terrain in front does.
- A mob never asked about, or whose answer is more than six frames old, is drawn.

The `occlusion` scenario checks the answers: a row of remembered cows 200 blocks away, half
of them hidden behind a wall, none once it is gone.

With `optOcclusionQueries` off, or once the queries have failed, the mobs are tested in the
far terrain's world instead (`render/Occlusion`). The two answers are never mixed. With
Vulkan, once Distant Horizons' depth is merged, no mob is skipped: the depth test hides them
pixel by pixel. Which way the frame goes (`DepthFar.openGl`, `FarDepth.gpuPath`) is worked out
once, as the level starts to render (`FarDepth.noteView`): with Vulkan, asking OpenGL throws, and
the GPU path asks Distant Horizons and Iris through reflection, too much for every mob.

## Debug tools

The switches are in *Debug Options...* in the settings, the F3 entries in the game's own
debug options. `/livinghorizon debug` turns the panel and the boxes on or off together.

| Tool | Switch | What it shows |
|---|---|---|
| Panel | `debugHud` | Top left: cost per tick and frame, what became of every distant mob in the last frame, far terrain reads and depth, how far this server sends mobs, which optimisations are on |
| Cost per section | `debugHud` | A second column of the panel, and `/livinghorizon`: for each part of the mod the last time, a smoothed average and the worst of the last 5 seconds, which an average alone hides (a save of 100 ms once a minute). A time is that of one call: choosing runs once a second (guessed mobs included, nested in it), so its time is spread over twenty ticks, while the other parts of the tick run every tick and those of the frame every frame. Sections: the client tick (following the server's mobs, choosing, building copies, reading the ground, motion, the copies resting between two looks included, resting players, birds, hidden mobs), the frame (extraction, each step of the far terrain's depth, the occlusion queries, baking and storing the flat pictures, submitting birds and flat pictures), the saves with their size in bytes, and the server's publication. Over 2 ms shows yellow, over 8 ms red. With OpenGL the GPU's time is shown too for the depth steps, the occlusion boxes and the baking (`GL_TIME_ELAPSED` queries read a few frames later, never waited for), and so is what the game's thread allocates per tick; both are measured only while the panel or the command was asked for in the last 10 seconds. Each section is named `livinghorizon:<section>` in the game's profiler (F3 pie chart, spark). Code: `Profile`, `debug/ProfileHook`, `debug/GpuTimer` |
| Panel in F3 | `living_horizon` in the game's debug options (F3 + F6, from 1.21.9) | The panel's lines in the F3 screen. `/livinghorizon debug` turns it on too |
| Polygons in F3 | `fake_entities` in the game's debug options (F3 + F6, from 1.21.9) | The polygons of the mod's own entities (distant players and mobs, never the game's), split into full models and impostors (one quad each), and a line of impostor sheets baked and waiting |
| Boxes (from 1.21.11) | `debugBoxes` | A box around every distant mob and player, seen through terrain, coloured by what became of it (below). A dot in the middle keeps it visible when the box is smaller than its lines |
| Game's mobs | `debugGameMobs` | Boxes around the mobs the game draws itself too |
| Boxes per kind | `box*`, in *Boxes...* | The boxes and labels of one kind on their own switch: real ones drawn by the mod, copies, foreseen animals, players, those not drawn (the game's are `debugGameMobs`) |
| Labels (from 1.21.11) | `debugLabels` | State, kind, distance and size on screen above each box, at a constant size |
| Outlines | `outline*`, in *Outlines...* | An outline around each kind of figure, and a cross where a mob is not drawn (below) |
| Statistics | *Statistics...* (`ui/StatsScreen`) | What is kept, counted when opened and on *Refresh*: remembered mobs (met, shared by the server, guessed), chosen, built and waiting; the guessed mobs kept apart, the chunks looked at and the memory they take; the size on disk of each folder of `config/livinghorizon`, this server's and all |
| Depth view | `debugDepthView` | Bottom right: the game's depth alone, with the far terrain merged in, after the entities, or once the whole world is drawn (a far terrain drawn after the entities, Ecstatic's, shows only there; copied only while chosen). Near is white, far black, sky blue |
| Far terrain | `/livinghorizon lod` (or `voxy`) | Which far terrain mod is read (Voxy, Distant Horizons, Far View or Ecstatic), whether reading it failed, and the state of its depth |
| Optimisations | `opt*`, `depthOcclusion`, `hideOccludedMobs` | Every optimisation has its own switch, to compare the cost (see [Optimizations](#optimizations)) |

### Box colours

| Colour | What became of the mob |
|---|---|
| Green | Copy drawn |
| Pink | Copy of an animal foreseen from the seed, never seen |
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
| `outlineForeseen` | Foreseen Mobs | Pink, around the copies of animals foreseen from the seed |
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
| `... test glide <count> [radius] [height]` | The same, `height` blocks above the ground (30 by default, up to 256), wearing an elytra: as if they logged off gliding. They come to rest on the ground, or the water, under them |
| `... test clear` | Removes them |
| `... test list` | Shows everyone resting on this server, where and since when |

Test players are never written to the file: they are gone at the next start. In a world the
host shares, the other players see them too (see
[The server's file](#the-servers-file)).

### Benchmark

`/livinghorizon benchmark [seconds]` (`debug/Benchmark`) measures what the mod costs where the
player stands, on their own machine. It is not a player's command: it and its sub-commands are
there only in a game started with `-Dlh.benchmark=true` (`Benchmark.ON`, a JVM argument in the
launcher), and without it they are not even suggested. The player stands still; the settings go through phases:
a warm-up of 20 seconds that is not measured, the mod off, the player's settings, each preset,
the mod off again, distant players, mobs and birds each on their own, then mobs with
*Simplified Entities*, *Skip Hidden Mobs* and *Hide Behind Far Terrain* off in turn, and the mod
off a third time. Each phase settles for 4 seconds, then is measured for `seconds` (8 by
default, 3 to 60): about three minutes in all. `/livinghorizon benchmark stop` ends it early,
and it stops by itself if the world closes or the game pauses.

While it runs, the debug views and the easter eggs are off, and the frame rate cap, vsync and
the game's slowdown when idle (from 1.21.2) are lifted; `MinecraftFrameMixin` times every
frame from the top of `Minecraft.runTick`. Everything is put back at the end, and nothing is
saved. The report goes to `livinghorizon/benchmarks/benchmark-<date>.txt` in the game folder
and to the log. Per phase: frames per second, the 1% low, the 99th percentile and worst frame
times, and the cost in milliseconds against the mod off (the mean of its three phases, whose
spread is given as the drift: a smaller cost is noise); then the mod's own counters, the
game, GPU, mods and settings. A phase where the player moved or a screen was open is marked.

`/livinghorizon benchmark world` (`debug/BenchmarkWorld`) makes a world to run it in, the same
every time it is made. It is a new single player world, "Living Horizon benchmark", added to
the world list: the world the player is in is saved and closed first (as *Save and Quit*),
never changed. The command says so and only goes ahead on `/livinghorizon benchmark world
confirm`. Nothing in it is random:

- a flat world of a fixed seed, without structures, creative, filled in through the world
  creation screen (`CreateWorldScreenAccessor` presses *Create*);
- time stopped at noon, clear weather, and no mob, patrol, trader or phantom spawning, no
  random ticks, no fire (the game rules under their old names and their 1.21.11 ones);
- a flat world spawns animals as its chunks are generated, so every chunk within 48 of the
  start is generated first with `/forceload`, 16 by 16, and emptied;
- then every mob is summoned with data: its kind, place and facing chosen, a fixed id, no AI,
  the tag `lh_bench`. Given data, `/summon` does not pick a random variant.

The player stands at 0, 0 facing south, where each part of the mod has its zone: mobs the
game draws (20 to 56 blocks), mobs past its entity distance (72 to 136), part of them behind
a wall, about 500 remembered copies (192 to 480), part of them behind a hill, a herd past the
default mob distance (576 to 672), and a pond, a wheat field, a tree and a tower for the
birds. The far herds are never sent to the client: a scan of 48 chunks remembers them once
the world is built.

Players who logged off cannot be built into the world: test players are never saved. While the
benchmark runs there, it places a crowd of 48 of them itself (`BenchmarkWorld.placeCrowd`), on a
grid from 48 to 240 blocks clear of the mobs' rows, near enough to be drawn as models, far
enough to be pictures, some behind the wall and the hill. They wear the default skin of their
names (`LhBench1` and on), so nothing is fetched, and are taken away as it ends; test players
placed by hand stay. With *Offline Player Activity* on they still move about as they like.

Once built, the world gets a marker file, `livinghorizon-benchmark`, in its folder. In a world
with that file, the benchmark puts the player back at 0, 0 facing south and fixes the render
distance (12), the entity distance (100%) and the field of view (70) for the run, then puts
them back; the report says it ran there. Two reports from that world, before and after an
update, measure the same scene. Birds still choose where they fly by chance.

## Commands

`/livinghorizon` is a client command: it never reaches the server.

| Command | What it does |
|---|---|
| `/livinghorizon` | Whether positions are shared, how many mobs are remembered and drawn, the cost per tick and per frame, and every distant player followed, with where their position comes from and how far they are |
| `/livinghorizon config` | Opens the settings |
| `/livinghorizon impostors` | Opens the impostor preview (*Picture Preview...*) |
| `/livinghorizon scan [radius]` | Reads the mobs of the chunks around, in single player (see [Scanning nearby mobs](#scanning-nearby-mobs)) |
| `/livinghorizon foresee [radius]` | Foresees the animals of the land nobody explored within that many chunks, up to 1024 (see [Animals of unexplored land](#animals-of-unexplored-land)) |
| `/livinghorizon bird <kind>` | Calls a group of birds of that kind (see [Calling birds](#calling-birds)) |
| `/livinghorizon lod` (or `voxy`) | Which far terrain mod is read and the state of its depth |
| `/livinghorizon debug` | Turns the debug panel and the boxes on or off together |
| `/livinghorizon debug offline test ...` | Test players who logged off (see [Test players](#test-players-who-logged-off)) |
| `/livinghorizon benchmark [seconds]` | Measures what the mod costs, part by part, and writes a report (see [Benchmark](#benchmark)); only with `-Dlh.benchmark=true` |
| `/livinghorizon benchmark world` | Makes the benchmark's world, the same every time, after saying so; `... confirm` goes ahead |

Two key bindings, unbound by default: *Toggle Distant Players* (flips `enabled`) and *Open
Living Horizon Settings*.

## Settings reference

Every setting is in `config/livinghorizon.json` (`FarConfig`). The file is read at start and
written back with every setting in it, including the ones a newer version adds. Its
`version` field is the file's layout, not a setting: an older file is brought up to date on
load, one step per version (`FarConfig.migrate`, each step tested in `FarConfigTest`).
`src/test/oldconfig` holds the file each released version wrote on its first start, its defaults
as Gson saved them: `OldConfigTest` reads every one, brings it up to date, and checks that a
player's choices survive. A release that changes the file's layout adds its own.

### The client's files

The settings, the remembered mobs and the players who logged off are written through
`JsonStore`:

- into a temporary file beside the real one, then moved over it: a game that stops while
  saving leaves the old file whole;
- the remembered mobs and the players who logged off on a thread of their own, one file after
  another (`track/SaveThread`), which closing a world waits for, 20 seconds at most;
- a file that does not read (a typo made by hand, a file cut short) is moved aside as
  `<name>.bad`, said in the log, and the mod starts from the defaults or an empty list;
- the lists keep the version of their layout, `{"version": 1, "mobs": [...]}` and
  `{"version": 1, "players": [...]}`; a bare list, as files were before, reads as version 0.
  A file of a newer version is read as well as this one can, after a copy is kept as
  `<name>.v<version>`.

In game, the settings are in the mod's screen: the *Living Horizon...* button of the Options
screen, Mod Menu (Fabric, Quilt) or the mods list (NeoForge, Forge), the settings key, or
`/livinghorizon config`. From Sodium 0.8 the main screen's settings are on Sodium's video
settings pages too, with the same names, and its sub-screens open from there.

The mod's screens sit in a column on the left and leave the world in view. While a distance
setting of the main screen is hovered or dragged, and for a moment and a half after it
changes from the keyboard, `render/DistancePreview` draws what it measures in that world
(`SideOptionsScreen.distance` marks the setting, with its shape):

- blocks from the player (*Simplification Distance*, the render distances, *Roaming Radius*,
  *Bird Distance*): a sphere of that radius round the camera, from its bottom up to a fence
  of `16 + distance / 50` blocks above the camera, with a bright rail along the top. Where
  the terrain meets it is that distance; the fence looks lower the farther it is. 0 (no
  limit, or off) draws nothing;
- *Scan Radius*: the same fence along the edge of the chunks a scan reads
  (`dx² + dz² <= radius²`, as `MobScan`);
- *Min. Flight Height*: a ceiling at that height above the player or the sea, as wide as
  *Bird Distance*, with the chunk lines on it.

It is submitted with the entities, like the birds, so that terrain in front of it hides it,
the far terrain's included while its depth is merged. From 1.21.11, without a shader pack, it
is drawn as the game's debug quads: no fog, no depth written. Before, those write depth (water
and clouds behind would be lost), and under a shader pack Iris gives them no program of the
pack (its `IrisPipelines` leaves them out), so Photon's passes paint over them: a glowing
entity's render type draws it there, which Iris gives one, fogged as entities are (without a
pack, past the game's fog it only shows with Voxy or Distant Horizons). The screen asks for it every frame
and it fades out once it stops asking; it is gone as soon as no screen is open. Sodium's page
does not show it.

The *Preset* button (*Low*, *Normal*, *High*, *Ultra*) sets only the settings that scale the
cost: `impostorDistance`, `maxDistantMobs`, `mobMaxDistance`, `birdDensity` and
`birdMaxDistance`. *Normal* is the defaults. Changing one of them by hand shows *Custom*.
No preset goes without a limit: *High* is 1500 mobs to 1024 blocks, *Ultra* 3000 mobs to
2048 blocks (`NumberSliderTest` checks every preset and the defaults). No default goes without
one either; settings version 13 moves the old High and Ultra and the old unlimited default
distances (players, animation, entities) to the new values, and keeps a no limit set by hand.

The sliders of whole numbers on the mod's screens are `ui/NumberSlider`, which the game's own
option lists build through its `OptionInstance.ValueSet` (made public by the access widener
before 26.2, where the game made it so). Only a slider with no limit at its end (the counts and
the render distances, `NumberSlider.typable`) takes a typed value; one within bounds does not,
its ends being what the mod can do. A right click turns it into a text field, which
`SideOptionsScreen` hands the keys until Enter (kept when it reads as a value), Escape or a
click. It takes digits only, and shows the slider's last number on no limit. A number below the
range is brought up to it; past the last number it is kept as typed (shown at that last place,
`NumberSlider.beyond`). No limit is a value of its own (`NumberSlider.UNLIMITED`, `FarConfig.UNLIMITED_MOBS` and
`UNLIMITED_OFFLINE` for the counts, 0 for the distances), reached only at the slider's right
end, never typed. Sodium's rows show a typed number past their end as their last place's value
(`SodiumOptions.typedPast`) and leave it as it is when saved there. The arrow keys move it one value, ten with Shift, where the
game moves it a pixel. `NumberSlider.asksBeforeUnlimited` names every
setting whose right end is no limit (the three counts and the four distances), none of them
safe without one: dragged there, the slider shows it and keeps it on release once the player
says yes; stepped there, at once. The release is heard by the screen too (`SideOptionsScreen`,
`NumberSlider.afterRelease`): before 1.21 the game hands it only to the widget under the cursor,
and a slider dragged past its end is released beside it. Sodium's page cannot ask as its slider moves:
`SodiumOptions.asking` holds the value at *Apply* and asks then, and no reads the page back.

### Main screen

#### General

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `easterEggs` | Easter Eggs | `true` | | A few surprises, now and then |

#### Performance

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `impostors` | Simplified Mobs | `true` | | Far mobs and birds drawn as a flat picture of themselves (see [Impostors](#impostors)) |
| `impostorEntities` | Simplified Entities | `true` | | Far boats, vehicles and mannequins drawn as pictures too, from every height; set from `impostors` when it came (layout 12) |
| `impostorPlayers` | Simplified Players | `true` | | Distant players become impostors too; off, they keep their whole model at any distance |
| `impostorShading` | Shader Lighting | `true` | | Under a shader pack that reads normal maps, the pictures are baked evenly lit with a normal map each, so the pack lights them like the models; twice the video memory. From 1.21.6 |
| `impostorDistance` | Simplification Distance | `128` blocks | 0 to 2000 | Past this distance an impostor stands in for the model (`0`: the size alone) |
| `impostorPixels` | Simplification Size | `10` pixels | 0 to 64 | A figure shorter than this on screen is an impostor too, even closer than `impostorDistance` (`0`: the distance alone) |
| `impostorMobMemory` | Mob Picture Memory | `1024` MB | 64 to 4096 | Video memory the mobs' pictures may take, apart from the players' |
| `impostorPlayerMemory` | Player Picture Memory | `512` MB | 32 to 2048 | Video memory the players' pictures may take (riders with what they ride), apart from the mobs' |

#### Distant Players

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `enabled` | Show Players | `true` | | Distant players are drawn at all. The *Toggle Distant Players* key flips it |
| `showVehicles` | Mounts & Vehicles | `true` | | Draw the mount they were last seen on |
| `playerMaxDistance` | Player Render Distance | `4000` blocks | 1 to 4000, or no limit | Past this, distant players are not drawn (`0`: no limit, the right end of the slider) |
| `offlinePlayers` | Offline Players | `true` | | Players who logged off are shown and remembered; off, neither |
| `maxOfflinePlayers` | Max Offline | `256` | 0 to 1000, more when typed, or no limit | How many players who logged off are drawn at once, nearest first (`0`: none; no limit is the slider's right end, which 1000 in an older settings file becomes) |
| `offlineLife` | Offline Player Activity | `true` | | They live their life around their spot; off, they sit on it |
| `offlineRoam` | Roaming Radius | `50` blocks | 8 to 128 | How far from their spot they walk around and look for a bed or a chest (50 is about a village) |
| `offlineNames` | Offline Player Names | `true` | | Their name above them, marked offline, and when they were last seen, under the crosshair |

*Clear Offline Players* forgets every player who logged off on this server. On a server with
the mod, they come back at their last position.

#### Distant Mobs & Entities

In three parts, on both screens: *General*, *Mobs* (the living kinds, mannequins excepted) and
*Entities* (boats, vehicles, mannequins).

| Setting | In game | Default | Range | What it does |
|---|---|---|---|---|
| `mobTypes` | General: Mobs & Entities... | Every mob and boat of the game and of other mods, and the vehicles their rider steers | | Which mobs and entities are shown, and remembered by this client. A change shows at once, the game paused or not (`MobMemory.kindsChanged`) |
| `hiddenMobs` | Mobs & Entities..., *Hidden* | Empty | | Kinds never shown, over `mobTypes` and name tags: a type id, or `modid:*` for every kind of a mod (its switches then locked). A kind shown until now and switched to *Hidden* is written here, so a modpack can ship the list; *Defaults* keeps the `modid:*` entries. Ids the game does not have (a mod taken out) are kept, harmless; entries are tidied on load (lower case, a bare name read as `minecraft:`, empty ones and repeats dropped). Version 14 of the file lists there the kinds it knew and did not show that were shown by default: the game's, and other mods' that are not monsters (sorted once the game has registered them, `unsortedMobs`); other mods' monsters, hidden by default before 0.13, and kinds the game no longer has cannot be told apart from a choice and are shown |
| `stillMobTypes` | Mobs & Entities..., *Still* | Happy ghast, mannequin | | Mobs shown standing still instead of walking their loop; a boat or a vehicle is only *Shown* or *Hidden* |
| `scanRadius` | General: Scan Radius | `32` chunks | 4 to 1024 | Chunks around you read by a scan, in single player (32 chunks = 512 blocks) |
| `foreseeRadius` | General: Guess Radius | `64` chunks | 4 to 1024 | Chunks around you *Guess Nearby Mobs* foresees the animals of the land nobody explored in |
| `distantMobs` | Mobs: Show Mobs | `true` | | Remembered mobs stay visible far away |
| `maxDistantMobs` | Mobs: Max Mobs | `512` | 0 to 4000, more when typed, or no limit | How many are drawn at once, nearest first, the animals foreseen from the seed included (`0`: none; no limit is the slider's right end, which 1000 in an older settings file becomes). No limit here and in `mobMaxDistance` together can crash the game |
| `mobMaxDistance` | Mobs: Mob Render Distance | `512` blocks | 1 to 4000, or no limit | Past this, distant mobs are not drawn (`0`: no limit, the right end of the slider) |
| `rememberNamedMobs` | Mobs: Name-Tagged Mobs | `true` | | A mob with a name tag is shown whatever its type |
| `seedMobs` | Mobs: Show Guessed Mobs | `true` | | The animals a server with the mod foresees in land nobody has explored, from the seed, are shown |
| `animationMinPixels` | Mobs: Min. Animated Size | `10` pixels | 0 to 40 | A distant mob shorter than this on screen stops walking about and stands (`0`: always walks) |
| `animationMaxDistance` | Mobs: Animation Distance | `4000` blocks | 1 to 4000, or no limit | Past this, every distant mob stops walking about and stands (`0`: no limit, the right end of the slider) |
| `distantEntities` | Entities: Show Entities | `true` | | Remembered boats, vehicles and mannequins stay visible far away |
| `maxDistantEntities` | Entities: Max Entities | `256` | 0 to 4000, more when typed, or no limit | How many of them are drawn at once, nearest first, apart from the mobs: a crowd of mobs never pushes an aircraft out (no limit: the slider's right end) |
| `entityMaxDistance` | Entities: Entity Render Distance | `4000` blocks | 1 to 4000, or no limit | Past this, distant boats, vehicles and mannequins are not drawn (`0`: no limit, the right end of the slider; few, and an aircraft is seen from afar) |
| `knownMobTypes` | (not shown) | Those met so far | | Mob types already offered once, the game's and other mods': a new one is shown from the start, then left as you set it |
| `knownVehicles` | (not shown) | Empty | | Vehicles that are neither mobs nor boats, as found from their renderer or met, listed in *Mobs & Entities...* |
| `seatedVehicles` | (not shown) | Empty | | Those of them that only seat their riders (a minecart): listed, never shown by default |

*Forget All* forgets every mob, boat and vehicle remembered on this server; those a server with
the mod shares come back by themselves. *Scan Nearby Mobs* runs a scan.

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
| `box*` | Boxes... | `true` | Which other kinds get their box and label: `boxLiveMobs`, `boxCopies`, `boxForeseen`, `boxPlayers`, `boxLeftOut` |
| `debugDepthView` | Depth View | `0` | `0` off; `1` the game's depth; `2` with the far terrain merged in; `3` after the entities; `4` the whole world |
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
| `optDepthRegion` | Local Far Depth | The far terrain's depth is merged, and put back, only in the screen rectangle round the distant figures (see [Far terrain depth](#far-terrain-depth)) |
| `optParallelRead` | Parallel Reads | The far world is read on several threads at once rather than one |
| `optColumnCache` | Remember Far Ground | Ground already read in the far world is kept for two minutes |
| `hideOccludedMobs` | Skip Hidden Mobs | Distant mobs the terrain hides entirely are not drawn (see [Skipping hidden mobs](#skipping-hidden-mobs)) |
| `optOcclusionQueries` | Hide Mobs by Depth | How `hideOccludedMobs` decides: occlusion queries on the GPU; off, lines of sight in the far terrain's world |
| `optDhFade` | DH Fade | Distant Horizons' fade leaves the entities alone, without a shader pack; off, it paints its terrain over distant mobs |
| `optVoxyShadows` | Voxy Shadow Fix | Our far figures are written into Voxy's depth, on their own pixels only, so that Photon's terrain shadows do not fall across them |
| `optVoxyFog` | Voxy Fog Fix | Without a shader pack, the world's entities get Voxy's fog, which stops growing past a limit (see [Fog on distant figures](#fog-on-distant-figures)); off, the game's fog |
| `optDhFog` | DH Fog Fix | Without a shader pack, the world's entities get Distant Horizons' far fog, which takes the game's away (see [Fog on distant figures](#fog-on-distant-figures)); off, no fog on them |
| `optBackgroundBuild` | Background Build | Saved mobs are read off the game's thread and built within 2 ms per tick |
| `optStillTiny` | Still Tiny Mobs | Mobs under `animationMinPixels` stop walking about and stand |
| `optFreezeHidden` | Freeze Undrawn Mobs | A copy not drawn for half a second is not animated, only moved where the server says |

Older names are still read from the file:

| Old name | Now |
|---|---|
| `voxyOcclusion` | `depthOcclusion` |
| `optLazyVoxyDepth` | `optLazyDepth` |
| `optParallelVoxy` | `optParallelRead` |
| `optHiZ` | `optOcclusionQueries` |
