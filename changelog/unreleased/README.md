# Entries for the next release

Each change a player notices gets a file of its own here, in every language the mod ships,
instead of a bullet written into `changelog/<code>.md`. Two pull requests then never touch the
same lines, and never conflict over the changelog.

Name the file after the change (`mac-crash.md`, or the branch's name), and write:

```
part: client
kind: fixed

en: **Crash on Mac fixed** — The game no longer crashes about a second after you join a world on a Mac.

fr: **Plantage sur Mac corrigé** — Le jeu ne plante plus environ une seconde après que vous avez rejoint un monde sur Mac.

de: **Absturz auf dem Mac behoben** — Das Spiel stürzt nicht mehr etwa eine Sekunde nach dem Betreten einer Welt auf einem Mac ab.

es: ...
```

- `part`: `server` or `client` (*Server* / *Client* in the changelog);
- `kind`: `new`, `improved` or `fixed`;
- then one line per language of `changelog/languages.json`: `en`, `fr`, `de`, `es`, `it`,
  `pt_br`, `pl`, `tr`, `ru`, `uk`, `zh_cn`, `zh_tw`, `ja`, `ko`. Each is the bullet, without
  its leading `- `, as the `changelog-writing` skill says to write it, every one required.
  A line that names no field carries the one above on.

One file is one bullet: a change with two entries has two files. The release puts them all
under the version it dates, in the order of their names, Server then Client, and deletes
them; the jar's changelog screen shows them under *Unreleased* until then. See them as they
will read, in any language:

```
node .github/scripts/changelog-notes.js unreleased en
```

`node .github/scripts/changelog-check.js origin/develop` checks them, as the CI does.

A branch started before these files, with bullets written into `changelog/<code>.md`: run
`node .github/scripts/changelog-split.js` on it, fill in the languages it leaves empty, commit,
then merge `develop` and keep `develop`'s `changelog/<code>.md` where they conflict.
