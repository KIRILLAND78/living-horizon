# Contributing

Living Horizon is developed in one place: this repository. Contributions are welcome,
and they all land here as pull requests. Forks and separate versions are not allowed
while the project is maintained (see [LICENSE](LICENSE)); keep your work-in-progress
clone private and open a pull request when it is ready.

## How

1. Open an issue first for anything larger than a small fix, so the direction is agreed
   before the work is done.
2. Work on a `feature/*` or `fix/*` branch. Pull requests target `develop`.
3. Commits are in English, as conventional commits (`feat(scope): summary`).
4. Run `./gradlew :1.21.11:build`: it compiles and runs the tests. CI runs it too.
5. Add yourself to `CREDITS.md` in your pull request, with a few words on what you brought,
   and to the other places the `add-contributor` skill lists (an AI agent does it for you if
   you are not credited yet).
6. If the change shows to players, add it to `changelog/en.md` and `changelog/fr.md`, under
   *Unreleased* / *Prochaine version*, in one short sentence a player understands, under
   *Server* or *Client* (*Serveur* / *Client*) and *New*, *Improved* or *Fixed*.

`CLAUDE.md` describes the full conventions of this repository.

## Using AI

AI is part of this project's workflow, and you can use it to contribute, no problem. Just
keep thinking about what it writes: read, understand and test your change before opening
the pull request. Claude works best here: it reads `CLAUDE.md` and the skills in
`.claude/skills/` on its own.

## What you agree to

By opening a pull request you accept section 3 of the [LICENSE](LICENSE):

- you wrote the contribution, or you have the right to submit it;
- you keep your copyright, and you grant the maintainer a perpetual, irrevocable license
  to use, modify, distribute and relicense it;
- the maintainer may accept, change or refuse it;
- if it is accepted, you are credited as a contributor, and that credit stays whatever
  happens later, including a change of license or a fork.

Do not submit code taken from another project unless its license allows it.
