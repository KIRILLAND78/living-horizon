#!/usr/bin/env node
/*
 * Moves the bullets a branch wrote under "Unreleased" in changelog/<lang>.md into
 * entry files of changelog/unreleased/, the way entries are written now, and empties
 * that section. For a branch started before the entry files: run it on the branch, commit,
 * then merge or rebase onto the base (taking the base's changelog/<lang>.md where they
 * conflict). A language whose file has no new bullet is left empty in the entry files, for
 * the check to name: write it in by hand.
 *
 *   node .github/scripts/changelog-split.js [base-ref]   (origin/develop by default)
 *
 * A bullet the base already has, in a released version, under Unreleased or as an entry
 * file, is the base's and is only dropped (a bullet reworded since is not recognised: read
 * the files it writes). The languages' bullets are paired by their place under each
 * heading, which the check keeps the same in every file.
 */
'use strict';

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const fragments = require('./changelog-fragments');

const base = process.argv[2] || 'origin/develop';
const git = args => execFileSync('git', args, { cwd: fragments.ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
const show = file => { try { return git(['show', base + ':' + file]); } catch (e) { return ''; } };

// What the base already holds, in each language: every bullet of its files, released or
// not, and its entry files.
const LANGS = fragments.LANGS;
const known = {};
for (const lang of LANGS) known[lang] = new Set();
for (const lang of LANGS) {
  for (const e of fragments.written(show('changelog/' + lang + '.md'), lang)) known[lang].add(e.text);
}
let listed = '';
try { listed = git(['ls-tree', '--name-only', base, 'changelog/unreleased/']); } catch (e) { /* none yet */ }
for (const file of listed.split('\n').filter(f => f.endsWith('.md') && !f.endsWith('README.md'))) {
  for (const line of show(file).split(/\r?\n/)) {
    const m = /^([a-z_]+):\s*(.*)$/.exec(line.trim());
    if (m && known[m[1]]) known[m[1]].add(m[2].trim());
  }
}

const texts = {}, ats = {}, mine = {};
for (const lang of LANGS) {
  const file = path.join(fragments.ROOT, 'changelog', lang + '.md');
  texts[lang] = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  ats[lang] = fragments.unreleased(texts[lang], lang);
  const written = ats[lang] ? fragments.written(texts[lang].slice(ats[lang].start, ats[lang].end), lang) : [];
  mine[lang] = written.filter(e => !known[lang].has(e.text));
}

const key = e => e.part + '/' + e.kind;
const groups = new Set(LANGS.flatMap(lang => mine[lang].map(key)));
const pairs = [];
for (const g of groups) {
  const of = {};
  for (const lang of LANGS) of[lang] = mine[lang].filter(e => key(e) === g);
  for (const lang of LANGS) {
    if (of[lang].length && of[lang].length !== of.en.length) {
      console.error('Under ' + g + ', en.md has ' + of.en.length + ' new bullets and ' + lang + '.md ' + of[lang].length + ': pair them by hand.');
      process.exit(1);
    }
  }
  of.en.forEach((e, i) => {
    const pair = { part: e.part, kind: e.kind };
    for (const lang of LANGS) pair[lang] = of[lang].length ? of[lang][i].text : '';
    pairs.push(pair);
  });
}

fs.mkdirSync(fragments.DIR, { recursive: true });
for (const p of pairs) {
  const title = (/^\*\*([^*]+)\*\*/.exec(p.en) || [, 'entry'])[1];
  const full = title.toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  const slug = (full.length > 48 ? full.slice(0, 48).replace(/-[^-]*$/, '') : full) || 'entry';
  let name = slug, n = 2;
  while (fs.existsSync(path.join(fragments.DIR, name + '.md'))) name = slug + '-' + n++;
  fs.writeFileSync(path.join(fragments.DIR, name + '.md'),
    'part: ' + p.part + '\nkind: ' + p.kind + '\n' + LANGS.map(lang => '\n' + lang + ': ' + p[lang] + '\n').join(''));
  console.log('changelog/unreleased/' + name + '.md');
}

for (const lang of LANGS) {
  const at = ats[lang];
  if (!at) continue;
  const rest = texts[lang].slice(at.end);
  fs.writeFileSync(path.join(fragments.ROOT, 'changelog', lang + '.md'),
    texts[lang].slice(0, at.start) + '\n' + (rest ? '\n' + rest : ''));
}
console.log(pairs.length + ' entries moved; every changelog/<lang>.md has an empty Unreleased section.');
