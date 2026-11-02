---
name: changelog-writing
description: Write, edit or rewrite the changelog of Living Horizon (changelog/en.md and changelog/fr.md) as professional release notes for Minecraft players - a benefit-first title, then what the player now sees or can do, why it matters and any condition, with no implementation details and no over-promising. Use for every changelog entry added in a pull request, for any rewording of existing entries, and when the user asks to clean up, rewrite or proofread the changelog.
---

# Writing the changelog

The changelog is read by players: in the game (the changelog button reads the files the jar
carries), on Modrinth, CurseForge and Discord. It must read like the release notes of a
polished, professional mod or game: confident, natural, concise, and understandable at
once by someone who has never seen the code. It presents what the update brings, not what
the code does.

## Steps

1. Read `changelog/en.md` and `changelog/fr.md` as they are now, and the diff the entry is for.
2. Check every fact against the code or the diff: what the change does, where it applies
   (Minecraft version, loader, shaders, Voxy or Distant Horizons), any default value, and
   whether it needs the mod on the server. Setting and screen names come from
   `src/main/resources/assets/livinghorizon/lang/en_us.json` and `fr_fr.json`, exactly as
   the game shows them.
3. Write the English and the French entry together (*Voice*, *Entry pattern*, *Rules*). Both
   files keep the same parts, kinds and number of bullets, in the same order.
4. Read each entry back as a player would and check it against *Before you finish*.
5. Run `node .github/scripts/changelog-check.js origin/develop` (headings, a released version
   gains no entry, both files match).

## Structure

Keep the structure the CI checks (CLAUDE.md, *Changelog*). Never add an empty section.

```
## Unreleased                         ## Prochaine version
### Server                            ### Serveur
#### New / Improved / Fixed           #### Nouveautés / Améliorations / Corrections
### Client                            ### Client
#### New / Improved / Fixed           #### Nouveautés / Améliorations / Corrections
```

- **Server**: what a server owner gets, installs or controls, written from their side
  ("Server owners can now choose…", "This option is off by default.").
- **Client**: what a player sees, feels or can do in game, single player included.
- A change felt on both sides gets a bullet in each, each from its own side.

Versions released before the Server/Client split keep their flat `### New` headings. New
entries only go under *Unreleased*; a dated section is only reworded when the user asks for
a rewrite of released versions, and then never gains a bullet, never changes order and
keeps every real feature.

## Voice

- **Benefit first.** Lead with what the player gets, in a positive form. Even a fix is
  announced by its result: "Better integration with Distant Horizons' fog", not "Mobs too
  sharp in fog".
- **"Now" carries the change**: "can now", "are now shown", "now stay". The old behaviour,
  when it helps, comes after "instead of…" / "au lieu de…".
- **Summarise, do not enumerate.** "certain distance settings" rather than the list of
  shapes each one draws; "shortcuts to Modrinth, CurseForge, Nexus Mods and the website"
  rather than where each logo sits.
- **Say why it matters** in a short second sentence when it is not obvious: "This makes it
  easier to see how far the chosen value reaches." / "Cela permet de…", "afin de…".
- **Precise, natural adverbs** that a professional note uses: correctly, directly,
  naturally, needlessly, at once (correctement, directement, naturellement, inutilement,
  aussitôt). Use them where they are true, not as filler.
- **"Living Horizon" as the subject** for what the mod itself does: "Living Horizon now
  estimates…". "The game" for what the player's game does.
- **Drop side details** a player does not act on: compatibility with older versions of the
  mod, which button is next to which logo, internal names, exact shapes, how it is stored.
  Keep a detail only if the player needs it to understand, find or use the change.
- Calm and factual: no exclamation marks, no hype ("amazing", "huge"), no jokes (except the
  one easter egg wink allowed by CLAUDE.md).

## Entry pattern

```
- **Benefit-first title** — What the player now sees or can do. Why it matters, or what it
  changes for them. Condition or limit, if any. (Compatibility, if any.)
```

One sentence is fine when it says everything; three is the most. Each sentence has one idea.

**Title.** A short noun phrase (2 to 7 words) naming the result:

| Kind | Pattern | Examples |
|---|---|---|
| New | the feature, plainly | **Distance preview in the world**, **Server settings**, **In-game changelog** |
| Improved | the result + a comparative | **Smoother resource pack changes**, **Clearer settings**, **More natural elytra flight**, **Up to 4,000 mobs shown** |
| Fixed | what now works, in a positive form | **Better integration with Distant Horizons' fog**, **Mounts shown only once**, **Sitting pets stay seated** |
| Fixed, crash or data loss | the problem + "fixed" / « corrigé » | **Startup crash fixed**, **Plantage au démarrage corrigé** |

Never a version or a mod name alone ("**Photon**", "**Minecraft 1.20 to 1.21.4**").

**Description.**

1. First sentence: what is new or different, with "now". For a fix, what now happens
   correctly, then the old symptom after "instead of…", or the symptom with "no longer…".
   Either way a reader can tell it was a bug.
2. Optional second sentence: the benefit, the consequence or the reason ("These values can
   greatly reduce performance or crash some setups.").
3. Optional last sentence: the condition a player must know ("On a server, this needs the
   server to share its seed.", "This option is off by default.", "Right-click a setting to
   type an exact value.": one how-to sentence, only when the feature cannot be found
   otherwise).
4. Compatibility in brackets at the very end, as its own sentence:
   `(Minecraft 1.21.6 and later, without shaders.)` / `(Minecraft 1.21.6 et plus, sans shaders.)`

Starts with a capital, ends with a full stop. `*Italics*` name a setting or a screen,
`` `code` `` a file or a command. Keep every `*` paired: the in-game changelog screen reads them.

## Rules

1. **The player's side, never the code's.** No shader, depth, buffer, pass, query, mask,
   thread, packet, class or setting internals, nothing about the debug screens.
   - Bad: "The server now tells clients who is gliding with an elytra."
   - Better: "**Elytra flight visible from afar** — Players flying with an elytra are now
     correctly shown gliding when they appear in the distance."
2. **Explain the mod's own terms** when the context does not: simplified images (the light
   pictures that replace distant 3D models: *Simplified Mobs*, *Entities*, *Players*),
   offline players (players who logged off, still shown where they left), the scan (reads
   the mobs around you so they appear at once), remembered mobs, estimated mobs.
3. **Never over-promise.** What is estimated is said to be estimated, and its limit given:
   "Living Horizon estimates them from the world's generation. As they are only estimates,
   they may differ or be gone once you actually reach the area." Never "shows the mobs that
   are there".
4. **Most important first**: the feature, then the condition. "The game now asks for
   confirmation before applying an unlimited value", not "On Sodium's page, when the slider
   reaches its end, a window now…".
5. **Not documentation.** One how-to sentence at most; no click-by-click procedure.
6. **Keep the numbers that give the scale**: 1,024 chunks, 4,000 blocks, 256 by default,
   10 pixels, Minecraft 1.21.6 and later. Nothing else numeric.
7. **Compatibility last**, in brackets (see *Entry pattern*). A server requirement is a full
   sentence before it.
8. **Server and privacy.** When the server shares something sensitive (player positions,
   the world seed), say plainly what it allows, who receives it and whether it is on by
   default. Neither minimise nor dramatise: "Servers can now let Living Horizon share the
   world seed with players who have the mod, so their game can show estimated mobs much
   farther away. This option is off by default, as the seed reveals the whole world."
9. **One word per concept**, in every entry:

   | Concept | English | French |
   |---|---|---|
   | Minecraft mobs | mobs | créatures |
   | Boats, vehicles, mannequins | entities | entités |
   | Players | players | joueurs |
   | Players who logged off | offline players | joueurs déconnectés |
   | The mod's own birds | birds | oiseaux |
   | Mobs worked out from the seed, never seen (the game's *Show Guessed Mobs*) | estimated mobs | créatures estimées (setting *Créatures devinées*) |
   | The world seed | seed | seed (players' word, not *graine*) |
   | Past the render distance | distant, in the distance, from afar | lointain(e)s, au loin |
   | The game's render distance | render distance | distance d'affichage |
   | Pictures replacing 3D models | simplified images | images simplifiées |
   | Iris packs | shaders, shader pack | shaders, pack de shaders |
   | Versions from X on | Minecraft X and later | Minecraft X et plus |
   | Single player | single player | solo |
   | Players who have the mod | players with the mod | joueurs utilisant le mod |

   French addresses the player as **vous**, never *tu*; English uses "you".
10. **Never change what an entry describes.** Reword and trim freely, but keep the exact
    behaviour. When a sentence can be read two ways, keep the more cautious reading or ask
    the user; never invent a feature or a benefit the change does not bring.

Also: one bullet per problem (two causes of one symptom share a bullet); nothing for CI,
refactors, tooling, documentation or debug-only tools; easter eggs are never described
(CLAUDE.md, *Easter eggs are secret*).

## Before and after

Typical drafts, and what they become:

| Draft | Release note |
|---|---|
| **Links to the mod's pages** — beside the Discord logo, the mod's settings now have the logos of Modrinth, CurseForge, Nexus Mods and the mod's website, to open its pages in one click. | **Quick links to Living Horizon** — The mod's settings now offer shortcuts to Modrinth, CurseForge, Nexus Mods and the official website, alongside Discord. |
| **Distances shown in the world** — hovering or changing a distance now draws it in the world behind: a glowing wall that far away, the edge of the chunks *Scan Radius* reads, or a ceiling at *Min. Flight Height*. | **Distance preview in the world** — When you adjust a distance setting, its limit is now shown directly in the world behind the menu. This makes it easier to see how far the chosen value reaches. |
| **Simplification Distance can be turned off** — *Simplification Distance* now goes down to OFF, so far mobs, entities, players and birds become flat pictures only when they look smaller on screen than the *Simplification Size*. | **Fully optional simplification distance** — *Simplification Distance* can now be turned off. Mobs, entities, players and birds then only become simplified images once they look small enough on screen. |
| **Distant mobs sharp in Distant Horizons' fog** — distant mobs no longer stay sharp against Distant Horizons' fogged terrain. | **Better integration with Distant Horizons' fog** — Distant mobs now correctly take on Distant Horizons' fog and blend into the landscape, instead of staying unnaturally sharp. (Minecraft 1.21.6 and later, without shaders.) |
| **Guessed mobs** — far lands nobody explored yet are no longer empty: the mod guesses from the seed where the world would put them. | **Mobs in unexplored areas** — Regions you have never visited can now show mobs in the distance. Living Horizon estimates them from the world's generation, so they are only estimates: they may differ, or be gone, once you reach the area. |

## Reference

What a finished section looks like (illustration only: never copy an entry that is not in
the diff).

```
## Unreleased

### Server

#### New
- **Seed sharing** — Servers can now let Living Horizon share the world seed with players
  who have the mod, so their game can show estimated mobs much farther away. This option
  is off by default.

### Client

#### New
- **Typed values** — Distances and limits in the settings can now be typed in directly,
  even beyond the slider's maximum. Right-click a setting to enter an exact value.

#### Improved
- **Safer default settings** — Settings that used to be unlimited now have sensible
  limits by default, to avoid large drops in performance. The High and Ultra presets
  were adjusted too.

#### Fixed
- **Startup crash fixed** — The game no longer crashes on startup with the mod installed.
  (Forge, Minecraft 1.20.1 to 1.20.4.)
```

The French register, for the same kind of entries:

```
- **Aperçu des distances dans le monde** — Lorsque vous réglez une distance, sa limite
  s'affiche maintenant directement dans le monde, derrière le menu. Vous voyez ainsi
  d'un coup d'œil jusqu'où porte la valeur choisie.
- **Meilleure intégration avec le brouillard de Distant Horizons** — Les créatures
  lointaines prennent maintenant correctement le brouillard de Distant Horizons et se
  fondent dans le paysage, au lieu de rester anormalement nettes. (Minecraft 1.21.6 et
  plus, sans shaders.)
```

## Before you finish

- Does every title name a result a player can picture, and read well on its own?
- Does every description start with what the player now sees or can do?
- Is every list that a player does not need summarised, and every side detail dropped?
- Does every fix make clear what was wrong ("instead of…", "no longer…")?
- Is every estimate called an estimate, and every sensitive server option said to be on
  or off by default?
- Same terms as the table, *vous* in French, compatibility last?
- Same bullets, in the same order, in both files?

Give the user the result, not a commentary on each change: the files are the deliverable.
Say if an entry was ambiguous and how it was read.
