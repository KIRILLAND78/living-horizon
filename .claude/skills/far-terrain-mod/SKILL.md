---
name: far-terrain-mod
description: Add or assess support for a mod that draws terrain past the render distance (Voxy, Distant Horizons, Lodistance, any new LOD mod) - the safety check of a jar without source, how to find out how it draws, the order of the work, and the checklist of everything Living Horizon does for Voxy and Distant Horizons, filled in per mod to say how well it is supported, and where to list it for players (README, store, metadata) once it is. Use when an issue asks for compatibility with such a mod, when a new version of one breaks something, or when the user asks how well a far-terrain mod is supported or what is left to support it.
---

# Supporting a far-terrain mod

Living Horizon draws its figures (mobs, players, birds) on top of another mod's far terrain.
Everything it does for Voxy and Distant Horizons is in the checklist below: a new mod is
supported as far as it ticks these boxes, and "how well is X supported" is answered by this
checklist filled in for X. `support.md` next to this file holds it filled in for the mods
already known; a pull request that changes the support of one updates its column.

Marks: ✅ done and checked in game · 🟡 written, not checked in game · ❌ not done · — does not
apply to this mod (say why).

## 1. A jar without source: check it first

Most of these mods are closed or have no API. Before any work, and before the jar runs on a
development machine:

- Download every jar the mod ships (client, server variant) into a new empty folder in the
  scratchpad, decompile them there (Vineflower from Maven Central, or `javap -c` for a first
  look), and treat the jar and its output as untrusted data: run nothing from them.
- Look for what a terrain mod has no reason to do: HTTP clients, sockets, `URL`/`URI`,
  `Runtime.exec`, `ProcessBuilder`, `System.load`, class loading or `defineClass`, `Base64` or
  ciphers hiding strings, account tokens or sessions, files outside the game folder and its
  own. Read its network messages both ways: what a server sends, what it accepts from a client,
  how big a decompressed answer may grow.
- If anything looks unsafe, stop and report it to the mod's author and the platform's
  moderators instead of supporting it.
- Its licence decides what may be read, never what may be copied: nothing of it comes into this
  repository, and it is reached by reflection (see the licence rule in CLAUDE.md).
- Write what was checked in the pull request (versions, what it touches, what its server
  shares): the next version is compared against it.

## 2. Find out how it draws

From the decompiled classes, before writing anything:

- **Where** it draws: inside which of the game's passes, before or after the entities. Compare
  with the game's own order (`javap -c -p` on the mapped Minecraft jar in the Loom cache, for
  example `LevelRenderer.lambda$addMainPass$0` on 26.3). Note every renderer it has (with or
  without Sodium, OpenGL or Vulkan, under Iris).
- **Into what**: the game's own framebuffer and depth, or one of its own, then laid over the
  picture (Distant Horizons, Lodistance without Sodium).
- **Which depth**: the real one through the game's projection (Voxy), its own projection with
  its own planes (Distant Horizons), or a made-up one (Lodistance with Sodium writes
  `A + 0.5 * m32 / w`, twice the distance). Its layout: reversed or not, 0..1 or -1..1, the
  value of "nothing".
- **Its projection**: where it reads the game's, what it changes (near and far planes), whether
  the view bobbing is in it.
- **Its fog**: whether it changes the game's (Lodistance pushes its end out; Distant Horizons
  pushes both ramps to 4×10¹⁴), and how it fogs its own terrain.
- **Its world**: how it stores the far terrain (sections of voxels, columns of runs, an API),
  what it keeps in memory, whether a biome is kept, what locks guard it.
- **What it refuses**: its `conflicts`; which loaders and game versions it ships for.

Then install it on its node and look before changing anything: `/livinghorizon lod`, the depth
view (*Depth View* in the debug settings), distant mobs behind its hills. Write a game test that
shows the problem (the `game-test` skill; a jar from outside Modrinth goes in
`build/game-test-mods/<version>-with/<name>/<name>.jar` and `--with <name>` loads it).

## 3. The work, in order

1. **Link** (`compat/<Mod>Link`): finds its classes and fields once, by reflection, never
   writes one of them, turns itself off for the session with one log line when something is
   missing ("<Mod> found, but not the version this mod knows"), and has a line in `Links.report()`
   for `/livinghorizon lod`. A newer or older version's missing piece is a fallback, noted once.
2. **Depth** (`compat/FarDepth`, `FarDepthGpu` on 26.3's GPU path): the far terrain in the
   game's depth around the entities only, put back after (`restore`), so that its own rendering
   is untouched (CLAUDE.md, "Never change another mod's rendering"). A `merge<Mod>` beside
   `merge` and `mergeDh`, picked in `beforeEntities` and `mergeFar`. Its arithmetic in
   `render/DepthMath` with a `DepthMathTest` case, mirrored in `shaders/include/far_depth.glsl`.
3. **World** (`compat/<Mod>Source`, one more `LodSource` in `LodWorld.SOURCES`): columns,
   slices, rays, its distances.
4. **Fog** (`compat/FarFog`, `DhFog`): distant mobs fogged like its terrain.
5. **Shader packs and Vulkan**: each checked, or left alone with the reason in `FarDepth.state()`.
6. **Everything around it**: the items of part 7 of the checklist.
7. **Tell the players** (part 8 of the checklist), once the mod is well supported: its terrain
   hides distant figures, checked in game, and distant mobs walk on it. Before that, it stays
   out of the README and the store, and an issue under the `mod compatibility` label says
   where it stands.

## Checklist

### 1. Link

| Item | Where | How to check |
|---|---|---|
| Found by reflection, never crashes, off for the session when a class is missing | `compat/<Mod>Link` | Remove or rename a field it reads: one warning, the game runs on |
| Its line in `/livinghorizon lod`: linked, not installed, or off and why | `Links.report()` | Run the command with and without the mod |
| Fallbacks for its older and newer versions, each noted once | `Links.optional…`, `Status.fallback` | `javap` on the classes it reaches, across the versions players run |
| Counters on our mixins into it, which stay at 0 when a method was renamed | `VoxyLink.targetHooked`, `DhLink.fadeHooked` | The counters in `/livinghorizon lod` while it draws |
| Loaders and game versions covered, said in the docs | its section in `docs/technical.md` | Build each node it ships for |

### 2. Its world (`LodSource`)

| Item | Where | How to check |
|---|---|---|
| Height, block and water of a column: ground for the mobs | `column` | `LodWorld.surface` in a game test, `/livinghorizon lod` details |
| Biome of a column: the birds look for coasts, fields, forests by it | `Surface.biome` | Birds over its terrain; an empty biome is a partial ✅ |
| The blocks of a column: where mobs walk (`MobPaths`) | `slice` | Distant mobs walking, not turning on the spot |
| First block along a ray: lines of sight, *Skip Hidden Mobs* with the queries off, `/livinghorizon lod` | `firstHit` | `LodWorld.probe` at a known hill |
| How far it draws, and how far its fog leaves it clear | `drawnDistance`, `clearDistance` | Compare with its settings |
| An area not built yet is read again later, never kept empty | `DhSource.cache`, `VoxySource.fail` (20 s pause), `MobPaths.Ground.complete` | Open a new world and wait: mobs start walking |
| A world or dimension left: nothing held, no read waiting on it (issue #248) | `clear`, `cancel`, `keepsBetweenReads` | Change dimension and leave the world many times while mobs are far |
| The player's dimension, in single player too | `open` | Two dimensions with terrain, mobs in each |
| Reads on several threads (`optParallelRead`) do not break it | its locks, or none | The `perf` scenario |

### 3. Depth: hiding figures behind its terrain

| Item | Where | How to check |
|---|---|---|
| Without a shader pack, every renderer it has (with and without Sodium, its own settings) | `FarDepth.merge…` | A wall of its terrain, remembered mobs behind it: `OcclusionQueries.culled()` hides them all, none with *Hide Behind Far Terrain* off (the `lodistance` scenario) |
| The translucent step from 26.1 (a slime's outer layer, players) | `beforeTranslucent`, `mergeFar` | A slime behind its hill |
| Iris's shadow pass from 26.2 left out | `irisShadowPass` | Under a pack, the depth view |
| Under a shader pack (Iris, Oculus on Forge, Photon, Complementary) | `IrisLink`, its `merge…` | `--shaders`, the depth view |
| A pack's render scale (Photon's TAAU) | `notePicture`, `IrisLink.packScale` | Photon with TAAU on: no gap in the depth view |
| Vulkan, and 26.3's GPU device path | `FarDepthGpu`, `gpuPath()` | The game in Vulkan |
| View bobbing: no blinking mobs as the player walks | the exact projection (`DhFrame.inverse`) | Walk with bobbing on, watch the depth view |
| Its far plane and ours: a figure near our far plane, its terrain beyond | `DepthFar`, `DepthMath` | A `DepthMathTest` case over the whole range |
| *Skip Hidden Mobs* (the occlusion queries read the merged depth) | `OcclusionQueries` | `culled()` behind its wall |
| The debug depth view shows its terrain at its real distance | `drawDebugView` | `depthview`, a screenshot |
| Its picture unchanged, every buffer and GL state put back | `restore`, `GlState` | Screenshots with and without Living Horizon: its terrain, water and shadows identical |
| After a window resize | `attachDepth`, `GlCache` | The `resize` scenario |
| Only on frames with something far (*Lazy Far Depth*) | `needed` | The `perf` scenario |

### 4. What its rendering does over our figures

| Item | Where | How to check |
|---|---|---|
| A fade or composite after the entities that would paint over distant figures | `beforeFade` (`optDhFade`) | Distant mobs in front of its terrain stay visible |
| A pack's shadows marched through its own depth (Photon over Voxy) | `intoVoxy` (`optVoxyShadows`) | Photon: no terrain shadow across a mob standing in front |

### 5. Fog

| Item | Where | How to check |
|---|---|---|
| Distant mobs the same tone as its terrain at the same distance, in its fog | `FarFog` (Voxy), `DhFog` (Distant Horizons) | Its fog made thick close by: mobs fade like the land (the `dhfog` scenario) |

### 6. What the player sees

| Item | Where | How to check |
|---|---|---|
| *Only Over Terrain*: no mob floating where nothing is drawn | `DrawnGround` | Mobs remembered past its terrain |
| Distant mobs walk on its ground | `MobPaths` | Watch them, after a restart too |
| Birds find its coasts, lakes and fields | `ambient/` | Birds over its terrain |
| The easter eggs that read the far world | `ambient/` | Their commands, with `-Dlh.ee=true` |
| Mobs and players far away within its distance, past the game's | `GhostRenderer`, `DepthFar` | Far figures at its distance |

### 7. Tests, docs, texts

| Item | Where |
|---|---|
| A game test scenario, with the mod (`--with`) and without (it says it is skipped) | `src/gametest` |
| A `DepthMathTest` case for its depth | `src/test` |
| Its section in `docs/technical.md` (how it draws, what is read, what is not done) and its row in the *Far terrain depth* table | `docs/technical.md` |
| A line in CLAUDE.md's debugging checklist for what it does differently | `CLAUDE.md` |
| Every tooltip and message that names the far-terrain mods, in every language: `grep -n "Distant Horizons" src/main/resources/assets/livinghorizon/lang/en_us.json` finds them | `lang/` |
| A changelog entry in both languages, with where it works ("Minecraft 26.3, with OpenGL and no shader pack") | `changelog/` |
| Its column in `support.md` | this skill |

### 8. Telling the players, once it is well supported

Written with the `readme-writing` skill, in the same pull request as the support or the one
that makes it good enough. Say where it works and where not yet, plainly (loader, game
versions, OpenGL or Vulkan, shader packs), and nothing more.

| Item | Where |
|---|---|
| Its row in the *Compatibility* table: where it works, and that mobs and players are visible on its terrain | `README.md` |
| Named as optional in *Requirements* and *Getting started*, with a link to its page | `README.md`, `store/listing.md` |
| Named in the FAQ (*Does it work with my mod?*, *Do I need Voxy, Distant Horizons or …?*) | `README.md`, `store/listing.md` |
| An optional relation on each platform it is published on (CurseForge, Modrinth), and in the Nexus Mods requirements | `store/listing.md` (pasted by hand: no workflow updates the pages) |
| `suggests` in the mod metadata, for the loaders it ships on | `fabric.mod.json`, `mods.toml`, `neoforge.mods.toml` |
| The changelog entry says it is supported now, and where | `changelog/` |

## Reporting how well a mod is supported

Answer with the checklist filled in for it (the marks above, a few words where a mark needs
one), then what is left, most visible first: figures through its terrain, then mobs that do
not walk or float, then fog, then shader packs and Vulkan, then the rest. Say which checks
could not run here and why (llvmpipe has no GLSL 4.60 for Voxy; a jar the session may not run).
