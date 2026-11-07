#!/usr/bin/env node
/*
 * Dates the changelog for a release: the entries waiting in changelog/unreleased/ (and
 * any bullet written straight under "Unreleased") become the section
 * "## <version> — <date>" of changelog/{en,fr}.md, under an "Unreleased" section left
 * empty, and the entry files are deleted.
 *
 *   node .github/scripts/changelog-release.js <version> [YYYY-MM-DD]
 *
 * Run by release.yml on a stable release, right before the version commit, so the
 * dated changelog is part of the tagged commit. A release with no entry is legitimate
 * and leaves the files as they are, and a file that already carries the version is never
 * touched twice.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const fragments = require('./changelog-fragments');

const version = process.argv[2];
const date = process.argv[3] || new Date().toISOString().slice(0, 10);
if (!/^\d+\.\d+\.\d+$/.test(version || '')) {
  console.error('usage: changelog-release.js <version> [YYYY-MM-DD]');
  process.exit(2);
}

const { fragments: waiting, errors } = fragments.read();
if (errors.length) {
  for (const e of errors) console.log('::error::' + e);
  process.exit(1);
}

let dated = false;
for (const lang of ['en', 'fr']) {
  const file = path.join(fragments.ROOT, 'changelog', lang + '.md');
  if (!fs.existsSync(file)) continue;
  const text = fs.readFileSync(file, 'utf8');
  const at = fragments.unreleased(text);
  if (new RegExp('^## ' + version.replace(/\./g, '\\.') + ' ', 'm').test(text)) {
    console.log(lang + ': ' + version + ' is already there.');
    continue;
  }
  if (!at) {
    console.log(lang + ': no Unreleased section, nothing to date.');
    continue;
  }
  const entries = fragments.entries(text, lang, waiting);
  if (!entries.length) {
    console.log(lang + ': nothing waits for this release, nothing to date.');
    continue;
  }
  const section = '## ' + version + ' — ' + date + '\n\n' + fragments.render(entries, lang) + '\n';
  fs.writeFileSync(file, text.slice(0, at.start) + '\n\n' + section + text.slice(at.end).replace(/^\s*/, ''));
  console.log(lang + ': ' + entries.length + ' entries -> ' + version + ' — ' + date);
  dated = true;
}

if (dated) {
  for (const f of waiting) fs.unlinkSync(path.join(fragments.DIR, f.file));
  console.log(waiting.length + ' entry files deleted.');
}
