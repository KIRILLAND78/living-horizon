# How well each far-terrain mod is supported

The checklist of `SKILL.md`, filled in. A pull request that changes the support of one of these
mods updates its column in the same pull request.

Marks: ✅ done (for Voxy and Distant Horizons: shipped and in players' hands; Voxy cannot run
in the game tests, so its marks rest on players' games) · 🟡 written, not checked in game · ❌ not
done · — does not apply.

Lodistance: as in PR #311. Far View: as in PR #310. Ecstatic: as in PR #309, not merged yet; none
of it checked in game but the depth view's *Whole World* picture (without Ecstatic).

## 1. Link

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| Found by reflection, off for the session when something is missing | ✅ `VoxyLink` | ✅ `DhLink` | ✅ `LodistanceLink` | 🟡 `FarViewLink` | 🟡 `EcstaticLink` |
| Line in `/livinghorizon lod` | ✅ | ✅ | ✅ | 🟡 | 🟡 (says whether its far plane is matched) |
| Fallbacks across its versions | ✅ (0.2.18's rename) | ✅ (2.3 to 3.3.4) | ❌ 0.8.7 only | 🟡 0.1.5 and 0.1.6 (no far mobs, no *Seamless*) to 0.1.7 | ❌ 1.4.1 only; its fog falloff falls back to its default |
| Counters on our mixins into it | ✅ `renderOpaque` | ✅ its fades | — no mixin into it | — no mixin into it | — no mixin into it |
| Loaders and versions | several | several | Fabric 26.3 only (all it ships) | Forge and NeoForge 1.20.1 to 1.21.4 (all it ships) | NeoForge 1.21.1 only (all it ships) |

## 2. Its world

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| Height, block, water of a column | ✅ | ✅ | 🟡 | 🟡 `FarViewSourceTest` | 🟡 height only, water under 63 as it draws it, no block |
| Biome | ✅ | ✅ | ❌ it keeps none | ❌ it keeps none | 🟡 its biome per sample, through the server's registry |
| Blocks of a column (walking) | ✅ | ✅ (top block only) | 🟡 up to three runs | 🟡 top block; ground and leaves under trees | 🟡 top block only (the default `slice`) |
| First block along a ray | ✅ | ✅ | 🟡 | 🟡 `FarViewSourceTest` | 🟡 |
| Drawn and clear distances | ✅ | ✅ | 🟡 | 🟡 its distance and haze settings | 🟡 its outermost ring, its fog ramp |
| Area not built yet read again later | ✅ 20 s pause | ✅ cache emptied | ❌ regions not in memory read as nothing | 🟡 its grid copied every second | 🟡 nothing kept: each read asks its files again |
| World or dimension left without a freeze | ✅ | ✅ (#248) | 🟡 reads take no lock | 🟡 reads take no lock and never call it | 🟡 no wait on it; a closed file is never read (it frees its memory) |
| The player's dimension in single player | ✅ | ✅ | 🟡 its world follows the client's level | 🟡 its grid follows the client's level | — overworld only, single player only (all it draws) |
| Parallel reads | ✅ | ✅ | 🟡 | 🟡 a copy of its grid, read without locks | 🟡 its files' own locks |

## 3. Depth

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| Without a shader pack | ✅ Voxy writes it itself | ✅ merged | ✅ with Sodium (12 of 12 cows hidden) · 🟡 without Sodium (it drew nothing in the game tests) | 🟡 Far View writes it itself with the game's projection · with *Seamless*, lines of sight only | 🟡 nothing merged: it draws after the entities, tested against their depth; its far plane taken by the game's (`DepthMathTest`) |
| Translucent step from 26.1 | ✅ | ✅ | 🟡 same code | — 1.21.4 at most | — 1.21.1 only |
| Iris's shadow pass left out | ✅ | ✅ | — not under a pack yet | — 1.21.4 at most | — 1.21.1 only |
| Under a shader pack | ✅ Photon | ✅ Iris, Oculus | ❌ left alone, says why | 🟡 it draws in Iris's terrain phase, the game's projection | ❌ it advises against packs for now |
| A pack's render scale | ✅ | ✅ | ❌ | ❌ not looked at | ❌ |
| Vulkan, 26.3's GPU path | — OpenGL only | ✅ | ❌ | — 1.21.4 at most | — 1.21.1, OpenGL only |
| View bobbing | ✅ | ✅ `DhFrame.inverse` | ✅ its depth does not depend on it | 🟡 the game's own projection | 🟡 its projection and the game's are bobbed the same way |
| Far planes over the whole range (`DepthMathTest`) | ✅ | ✅ | ✅ moved both ways past half the far plane | — nothing converted · ❌ *Seamless* | 🟡 the game's is its own while it draws |
| *Skip Hidden Mobs* | ✅ | ✅ | ✅ | 🟡 queries; lines of sight with *Seamless* | ❌ its terrain is drawn after the queries; the depth test still hides them |
| Debug depth view | ✅ | ✅ | ✅ | 🟡 the game's depth | 🟡 its fourth picture, *Whole World*, copied once the world is drawn (checked in game without Ecstatic) |
| Its picture unchanged | ✅ | ✅ | ✅ screenshots identical | 🟡 nothing of it written | 🟡 nothing of it written; the game's far plane changed to its own |
| Window resize | ✅ | ✅ | 🟡 same code | — nothing of ours | — nothing of ours bound to its buffers |
| *Lazy Far Depth* | ✅ | ✅ | ✅ same code | — nothing merged | — nothing merged |

## 4. Its rendering over our figures

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| Fade or composite after the entities | — | ✅ `optDhFade` | — draws before the entities | — draws before the entities; its mob boxes are left to it (`drawsMob`) | — its terrain is drawn after them, but depth tested |
| A pack's shadows through its depth | ✅ `optVoxyShadows` | — | — | — | — |

## 5. Fog

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| Distant mobs fogged like its terrain | ✅ `FarFog` | ✅ `DhFog` | 🟡 its ramp alone, without the game's atmospheric fog (`FarFog.lodistance`); within the game's render distance mobs are a little clearer than the game's terrain | 🟡 its haze is the game's terrain fog, which the entities share | 🟡 its ramp, exactly (`EcstaticLink.fog`), without a shader pack |

## 6. What the player sees

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| *Only Over Terrain* | ✅ | ✅ | 🟡 | 🟡 | 🟡 |
| Distant mobs walk | ✅ | ✅ | 🟡 | 🟡 | 🟡 |
| Birds find coasts, lakes, fields | ✅ | ✅ | 🟡 blocks only, no biome | 🟡 blocks only, no biome | 🟡 biomes and water, no blocks |
| Easter eggs that read the far world | ✅ | ✅ | 🟡 | 🟡 | 🟡 |

## 7. Tests, docs, texts

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| Game test scenario | ❌ cannot run on llvmpipe (GLSL 4.60) | ✅ `dhfog` | ✅ `lodistance`, part of it run | ❌ Forge and NeoForge only: no client game tests there | ❌ no client game tests on NeoForge 1.21.1 |
| `DepthMathTest` case | ✅ | ✅ | ✅ | — nothing converted | 🟡 its far plane against the game's |
| `docs/technical.md`, CLAUDE.md checklist | ✅ | ✅ | ✅ | ✅ | 🟡 |
| Tooltips in every language, changelog | ✅ | ✅ | ✅ | ✅ | 🟡 |

## 8. Telling the players

| Item | Voxy | Distant Horizons | Lodistance | Far View | Ecstatic |
|---|---|---|---|---|---|
| README: *Compatibility*, *Requirements*, *Getting started*, FAQ | ✅ | ✅ | ✅ | ❌ waits for a check in game | 🟡 *Compatibility*, *Requirements*, *Getting started*, FAQ, before a check in game, at the author's request |
| Store description and platform relations | ✅ | ✅ | ✅ (to paste by hand) | ❌ | 🟡 (to paste by hand) |
| `suggests` in the metadata | ✅ | ✅ | ✅ Fabric | ❌ | 🟡 NeoForge |

## Left to do

- **Lodistance**: run the `lodistance` scenario again with Sodium (its ground checks, the
  last depth change and the fog have not run in game); shader packs; Vulkan; areas
  not in its memory; its renderer without Sodium, which drew nothing in the game tests (its hook
  misses 26.3's indirect chunk path).
- **Far View**: everything in game, by hand on `1.21.4-neoforge` (the client game tests need the
  Fabric API): mobs on its terrain and behind its hills, with and without a shader pack, its
  mob boxes alone; its *Seamless* option hides a mob only whole, by lines of sight (moving its
  squeezed depth to the real one around the entities, as for Lodistance, would hide it pixel by
  pixel); no biome; then the README and the store.
- **Ecstatic**: start the `1.21.1-neoforge` client with it (no client game tests there): distant
  mobs behind and in front of its hills, its fog on them, the depth view's *Whole World*,
  `/livinghorizon lod`: the README already lists it. Shader packs once its Iris support lands.
- **Voxy**: a game test, once something can run it.
