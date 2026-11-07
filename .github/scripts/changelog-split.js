#!/usr/bin/env node
/*
 * Moves the bullets a branch wrote under "Unreleased" in changelog/{en,fr}.md into
 * entry files of changelog/unreleased/, the way entries are written now, and empties
 * that section. For a branch started before the entry files: run it on the branch, commit,
 * then merge or rebase onto the base (taking the base's changelog/en.md and fr.md where
 * they conflict).
 *
 *   node .github/scripts/changelog-split.js [base-ref]   (origin/develop by default)
 *
 * A bullet the base already has, under Unreleased or as an entry file, is the base's and
 * is only dropped. English and French bullets are paired by their place under each
 * heading, which the old check kept the same in both files.
 */
'use strict';

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const fragments = require('./changelog-fragments');

const base = process.argv[2] || 'origin/develop';
const git = args => execFileSync('git', args, { cwd: fragments.ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
const show = file => { try { return git(['show', base + ':' + file]); } catch (e) { return ''; } };

// What the base already holds, in each language: its Unreleased bullets and its entry files.
const known = { en: new Set(), fr: new Set() };
for (const lang of ['en', 'fr']) {
  const text = show('changelog/' + lang + '.md');
  const at = fragments.unreleased(text);
  if (at) for (const e of fragments.written(text.slice(at.start, at.end), lang)) known[lang].add(e.text);
}
let listed = '';
try { listed = git(['ls-tree', '--name-only', base, 'changelog/unreleased/']); } catch (e) { /* none yet */ }
for (const file of listed.split('\n').filter(f => f.endsWith('.md') && !f.endsWith('README.md'))) {
  for (const line of show(file).split(/\r?\n/)) {
    const m = /^(en|fr):\s*(.*)$/.exec(line.trim());
    if (m) known[m[1]].add(m[2].trim());
  }
}

const texts = {}, ats = {}, mine = {};
for (const lang of ['en', 'fr']) {
  texts[lang] = fs.readFileSync(path.join(fragments.ROOT, 'changelog', lang + '.md'), 'utf8');
  ats[lang] = fragments.unreleased(texts[lang]);
  const written = ats[lang] ? fragments.written(texts[lang].slice(ats[lang].start, ats[lang].end), lang) : [];
  mine[lang] = written.filter(e => !known[lang].has(e.text));
}

const key = e => e.part + '/' + e.kind;
const groups = new Set(mine.en.map(key).concat(mine.fr.map(key)));
const pairs = [];
for (const g of groups) {
  const en = mine.en.filter(e => key(e) === g), fr = mine.fr.filter(e => key(e) === g);
  if (en.length !== fr.length) {
    console.error('Under ' + g + ', English has ' + en.length + ' new bullets and French ' + fr.length + ': pair them by hand.');
    process.exit(1);
  }
  en.forEach((e, i) => pairs.push({ part: e.part, kind: e.kind, en: e.text, fr: fr[i].text }));
}

fs.mkdirSync(fragments.DIR, { recursive: true });
for (const p of pairs) {
  const title = (/^\*\*([^*]+)\*\*/.exec(p.en) || [, 'entry'])[1];
  const slug = title.toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48) || 'entry';
  let name = slug, n = 2;
  while (fs.existsSync(path.join(fragments.DIR, name + '.md'))) name = slug + '-' + n++;
  fs.writeFileSync(path.join(fragments.DIR, name + '.md'),
    'part: ' + p.part + '\nkind: ' + p.kind + '\n\nen: ' + p.en + '\n\nfr: ' + p.fr + '\n');
  console.log('changelog/unreleased/' + name + '.md');
}

for (const lang of ['en', 'fr']) {
  const at = ats[lang];
  if (!at) continue;
  const rest = texts[lang].slice(at.end);
  fs.writeFileSync(path.join(fragments.ROOT, 'changelog', lang + '.md'),
    texts[lang].slice(0, at.start) + '\n' + (rest ? '\n' + rest : ''));
}
console.log(pairs.length + ' entries moved; changelog/en.md and fr.md have an empty Unreleased section.');
