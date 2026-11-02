---
name: game-test
description: Run the real Minecraft client with the mod, headless, drive it with a scripted scenario (world, mobs, settings, screens) and take screenshots to look at. Use whenever a change affects what the game draws or shows (rendering, impostors, distant mobs or players, birds, screens, settings) and must be checked in the real game, when a bug report comes with a screenshot to reproduce, or when the user asks to test, try or see something in game.
---

# Testing in the real game

The mod is checked in the real client, not only by unit tests: Fabric's client game
tests start Minecraft with the mod, a scenario written in Java plays it (makes a world,
summons mobs, changes the settings, opens screens) and takes screenshots, which you
then look at. No graphics card: the game renders on the CPU (Mesa llvmpipe, OpenGL 4.5)
into a virtual display (Xvfb).

## Run it

```
.claude/skills/game-test/run.sh                  # every scenario
.claude/skills/game-test/run.sh impostors        # one, or several: a,b
.claude/skills/game-test/run.sh all --sodium     # with Sodium loaded too
.claude/skills/game-test/run.sh impostors --shaders <pack folder>   # Sodium, Iris and a shader pack
.claude/skills/game-test/run.sh vehicles --with immersive-aircraft   # another mod, by its Modrinth project
```

It takes 1 to 3 minutes per scenario. The script:

- writes the session's proxy into `~/.gradle/gradle.properties` (the Gradle daemon does
  not always pick it up from `JAVA_TOOL_OPTIONS`), and retries when Maven Central
  answers 429;
- runs `xvfb-run ./gradlew runClientGameTest -Plh.scenario=<names>`;
- prints the scenario's `LHTEST` log lines, any assertion or crash, and the screenshots.

Give it `LH_NODE=1.21.11` even for the active version: without it Gradle configures every
node and downloads the libraries of all of them. When Maven Central keeps answering 429
all the same, point Gradle at its Google mirror for the session with an init script in
`~/.gradle/init.d/` that replaces `repo.maven.apache.org` by
`https://maven-central.storage-download.googleapis.com/maven2/` in every repository list
(settings' `pluginManagement` and `dependencyResolutionManagement`, and each project's).
26.3 opens its window through EGL: `apt-get install libegl1 libegl-mesa0` when the log
says "Could not load EGL library".

The full log is `build/game-test.log`. Screenshots are in
`build/run/clientGameTest/screenshots/`, numbered in order and named
`<scenario>-<name>.png`. That folder is emptied before every run; it is never the `run/`
folder `runClient` plays in.

## Look at the screenshots

Read them (the Read tool shows images). The window is 854x480, GUI scale 1: distant
figures are a few pixels, so crop and enlarge them, and put two side by side, with
Python and PIL:

```python
from PIL import Image
a = Image.open("…/0001_impostors-models-zoom.png").convert("RGB").crop((0, 95, 854, 170))
b = Image.open("…/0002_impostors-impostors-zoom.png").convert("RGB").crop((0, 95, 854, 170))
out = Image.new("RGB", (a.width, a.height * 2 + 4), (255, 0, 0))
out.paste(a, (0, 0)); out.paste(b, (0, a.height + 4))
out.resize((out.width * 2, out.height * 2), Image.NEAREST).save("/tmp/…/compare.png")
```

Write such working images to the scratchpad, not the repository. Mean colours over a box
(`getpixel`) compare brightness between two shots. Send the user the images that show
the result (before and after, model and impostor) with `SendUserFile`.

## Measure what the mod costs

`LH_NODE=1.21.11 run.sh perf` builds a scene far from the player (about 500 remembered
mobs, 40 test players who logged off, birds), turns the camera once round per measure and
prints two parts in words:

- **What the mod costs:** the frame time without the mod and with its defaults, the mod's
  own code per frame (average and worst), and the memory it allocates per frame.
- **What each setting does:** every `opt*` switch and *Simplified Entities*, changed alone
  and compared with the defaults measured just before and after it, so a slow drift of the
  game cancels out. `+` is slower once changed. The verdict reads the frame time when it
  moved more than the noise, else the mod's own code, else says "no difference".

The noise is how much the defaults, measured again before each setting, differ from one
another. Run it on the same machine before and after a change and compare the two
reports. llvmpipe draws on the CPU: the frame times say nothing of a graphics card, and
the settings marked `*` (Voxy's and Distant Horizons' terrain) cannot change anything
here. It takes about 8 minutes.

## Write a scenario

The scenarios are in `src/gametest/java/fr/clixmods/livinghorizon/gametest/`:

- `LivingHorizonGameTests`: the entry point, the list `SCENARIOS`. A new scenario is a
  class implementing `Scenario` (a lower-case `name()` and `run(context)`), added to that
  list. Keep the ones there working: they are the regression checks.
- `Scene`: what they share. `flatWorld(context)` (a new flat world at noon, still, the
  player at 0, 0 facing south, +z; close it with try-with-resources), `command`,
  `summon` and `row` (mobs with `NoAI`, turned every 45 degrees), `configure` (the mod's
  settings), `screenshot`, `screenshotAtlas` (the impostor page), `log` (an `LHTEST` line).
  Every scenario starts from the default settings, with birds and the easter eggs off.
- `scenario/ImpostorsScenario`, `scenario/RememberedMobsScenario`: examples to copy.

The `context` is Fabric's `ClientGameTestContext`: `waitTicks`, `runOnClient` and
`computeOnClient` (anything touching the game: the scenario itself runs on another
thread), `setScreen`, `clickScreenButton(translation key)`, `takeScreenshot`. Throw an
`AssertionError` for a check that must fail the run.

`./gradlew build` compiles the scenarios (`compileGametestJava`) but does not run them.

## What to know about the scene

- **Fog.** Without Voxy, the game's fog hides everything past its render distance. Keep
  the scene within it (render distance 12 chunks is the default here; 16 is slower) or
  the figures are invisible, models and impostors alike.
- **Who draws a mob.** The game draws a mob within its entity distance; past it, while
  the server still sends it, the mod draws the real mob; once the server stops sending
  it, the mod draws its remembered copy. `entityDistanceScaling` at 0.5 makes the game
  stop early (about 35 blocks for a cow), so mobs at 50 blocks are the mod's.
- **Remembered mobs.** The server still sends mobs 150 blocks away: go 200 blocks away
  (`tp`) and wait about 300 ticks. A mob that vanishes within 32 blocks of where the
  player was is taken as killed and forgotten, so the mobs must stand farther than that
  before the `tp`.
- **Occlusion.** With `hideOccludedMobs` on, figures past the terrain are hidden by
  depth; turn it off to look at the figures themselves.
- **Slow.** Chunks draw slowly: `flatWorld` waits 100 ticks. `waitForChunksRender`
  times out at large render distances. The menus' blur is off (`Scene.reset`): on a
  machine with few cores llvmpipe spends them all blurring the loading screen, the server
  runs in step with the client, and "Timeout loading world" ends the run. Raise the
  render distance after `flatWorld`, not before, for the same reason.
- **Options' bounds.** An option set out of its range is ignored: the field of view goes
  from 30 to 110, so 30 is the closest zoom.

## Limits

- No real graphics card: a bug of one driver (NVIDIA, AMD) does not show here.
- **Voxy cannot run** (it needs GLSL 4.60, llvmpipe has 4.50). Say so when a check depended on it.
- **Distant Horizons runs on 26.3** (`LH_NODE=26.3.x ... --with distanthorizons`): it needs
  Fabric Loader 0.19.5, so set `deps.fabric_loader` to it in `stonecutter.properties.toml` for
  the run, and put it back; 3.3.4 needs the same. The game may hang as it closes with it: kill
  `KnotClient` once the scenario says `done`. The `dhfog` scenario checks its fog on the mobs.
- **Lodistance runs on 26.3** (Fabric Loader 0.19.5, as for Distant Horizons): it is on
  CurseForge only, so put its client jar (not `lodistance-server`) at
  `build/game-test-mods/26.3-with/lodistance/lodistance.jar` and pass `--with lodistance`. With
  `--sodium` it draws into the game's depth, without Sodium into its own; the `lodistance`
  scenario checks both. It takes the chunks as they load: something built afterwards reaches it
  through `save-all flush` and its client command `lodistance reimport`.
- **Shader packs** run through Iris on llvmpipe, slowly: `--shaders` takes an unpacked
  pack folder, downloads Iris next to Sodium for the node's game version (`LH_NODE`), and
  `Scene.shaderPack` copies the pack into Iris's folder and turns it on before the first
  scenario. Photon does not load on llvmpipe (too much shared memory): use Complementary,
  its zip from Modrinth unpacked into the scratchpad. The run folder is emptied at start, so a pack copied there by hand is lost.
- Sodium can be added (`--sodium`, downloaded from Modrinth into `build/game-test-mods`). The
  `sodium` scenario shows the mod's pages in Sodium's video settings; without Sodium it does nothing.
- Any other mod can be added by its Modrinth project, `--with <project>` (repeatable): its
  Fabric jar for the node's game version, in a folder of its own, loaded by that run only. Its
  own dependencies are not fetched; the Fabric API is there already. The `vehicles` scenario
  checks Immersive Aircraft's aircraft with `--with immersive-aircraft`, the minecart without.

## Never

- Commit screenshots, logs or anything under `build/`.
- Point the game test's run directory at `run/`: it is deleted before every run.
