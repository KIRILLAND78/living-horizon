# Entries for the next release

Each change a player notices gets a file of its own here, in English and in French, instead
of a bullet written into `changelog/en.md` and `changelog/fr.md`. Two pull requests then never
touch the same lines, and never conflict over the changelog.

Name the file after the change (`mac-crash.md`, or the branch's name), and write:

```
part: client
kind: fixed

en: **Crash on Mac fixed** — The game no longer crashes about a second after you join a world on a Mac.

fr: **Plantage sur Mac corrigé** — Le jeu ne plante plus environ une seconde après que vous avez rejoint un monde sur Mac.
```

- `part`: `server` or `client` (*Server* / *Client* in the changelog);
- `kind`: `new`, `improved` or `fixed`;
- `en` and `fr`: the bullet, without its leading `- `, as the `changelog-writing` skill says
  to write it. A line that names no field carries the one above on.

One file is one bullet: a change with two entries has two files. The release puts them all
under the version it dates, in the order of their names, Server then Client, and deletes
them; the jar's changelog screen shows them under *Unreleased* until then. See them as they
will read:

```
node .github/scripts/changelog-notes.js unreleased en
```

`node .github/scripts/changelog-check.js origin/develop` checks them, as the CI does.
