---
name: announce-test-build
description: Write the Discord message that asks testers to try the latest build of develop (the newest GitHub pre-release), listing its changes and linking the release. Use when the user asks for a Discord message, announcement or call for testing about the latest develop version, a dev build or a pre-release.
---

# Announce a build of develop to test

Every push to `develop` publishes a GitHub pre-release `vX.Y.Z-dev.N`. This skill turns
the newest one into a Discord message (in English) that asks the community to test it. Nothing is
posted: the message is handed back to the user to paste.

## Steps

1. **Find the build.** `mcp__github__list_releases` on `mc-skin-creator/living-horizon`
   (`perPage: 3`, `fields: ["tag_name","html_url","published_at","prerelease"]`). Take the
   first release with `prerelease: true`. Its `html_url` is the link to put in the message,
   as is: the release page, never the compare view or a pull request.
2. **Check it matches `develop`.** Compare `git rev-parse origin/develop` (after a
   `git fetch origin develop`) with the release's commit if the user asks about a specific
   state; if `develop` moved after the release, say so in one line to the user.
3. **Take the official changelog.** Run `git fetch origin develop`, check out nothing, then
   print the next version's section as it will be released, from `develop`'s files:
   `git worktree add /tmp/lh-develop origin/develop` (or `git stash`-free equivalent), then
   `node .github/scripts/changelog-notes.js unreleased en` from there. Use its bullets
   **verbatim** (title and sentence): never reword, shorten, merge or drop one.
4. **Write the messages in English, always.** There are two parts: the announcement, then
   the changelog, which the user posts in a thread under the announcement.

   **Announcement** (one message, the link up front so nobody scrolls for it):

   ```
   Hello @Testers a new version is available for testing: <tag>

   🔗 Download: <html_url>

   Some of these changes may hurt your performance. If you notice a drop, please report it. Thank you all!

   Changelog and discussion are in the <X.Y.Z> thread below, please keep your feedback there 👇
   ```

   `<X.Y.Z>` is the tag without its `v` and `-dev.N` suffix (`0.17.0`): the thread is the
   one dedicated to that version, where the changelog goes and where people discuss the build.

   **Thread** (the changelog, one or more messages), starting with:

   ```
   # Server
   **Improved**
   - …
   # Client
   **New**
   - …
   ```

   - `### Server` / `### Client` become `# Server` / `# Client`, `#### New` and so on become
     `**New**`; the bullets are copied as they are;
   - **Discord limits a message to 2000 characters**: the official text is longer, so split
     the thread into several messages, only between two bullets (never inside one), repeating
     the `# Client` and `**Fixed**` heading at the top of a continuation. Count each message;
   - `@Testers` is written as is, for the user to turn into the role mention when pasting;
   - no other role ping unless the user asks for it.
5. **Easter eggs stay secret.** Never mention one; at most "easter egg" and a number. The
   changelog has none, so there is nothing to copy.
6. **Hand it back** as one fenced block per message: the announcement first, then the
   thread messages in order, each with its character count.
