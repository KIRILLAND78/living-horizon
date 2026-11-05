#!/usr/bin/env node
/*
 * Checks the changelog of a pull request against its base branch:
 *
 * - entries only go into the Unreleased section: a released version never gains an
 *   entry, and no new dated section appears for a version that was never tagged
 *   (the release dates the Unreleased section by itself);
 * - the section headings are the ones the release scripts read: "### Server" and
 *   "### Client" (Serveur, Client), each with "#### New", "#### Improved", "#### Fixed"
 *   (Nouveautés, Améliorations, Corrections). Released versions written before the
 *   split keep their flat "### New" headings;
 * - each heading appears once per area of a version;
 * - en.md and fr.md have the same sections, headings and number of entries.
 *
 *   node .github/scripts/changelog-check.js <base-ref>
 */
'use strict';

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const base = process.argv[2];
if (!base) {
  console.error('usage: changelog-check.js <base-ref>');
  process.exit(2);
}

const ROOT = path.resolve(__dirname, '..', '..');
const HEADINGS = { en: ['New', 'Improved', 'Fixed'], fr: ['Nouveautés', 'Améliorations', 'Corrections'] };
const AREAS = { en: ['Server', 'Client'], fr: ['Serveur', 'Client'] };
const UNRELEASED = /^(Unreleased|Prochaine version)$/;
const git = args => execFileSync('git', args, { cwd: ROOT, encoding: 'utf8' });

// [{ version: '0.3.0' | null, kinds: [{ area, heading, entries, flat }] }]
// "### Server" / "### Client" open an area, "#### New" and so on a kind inside it; a
// "### New" with no area is the flat layout of the versions released before the split.
function parse(text, lang) {
  const releases = [];
  let area = null;
  for (const line of text.split(/\r?\n/)) {
    const h2 = /^## (.+?)\s*$/.exec(line);
    const h3 = /^### (.+?)\s*$/.exec(line);
    const h4 = /^#### (.+?)\s*$/.exec(line);
    if (h2) {
      const dated = /^(\d+\.\d+\.\d+)\s+—\s+\d{4}-\d{2}-\d{2}$/.exec(h2[1]);
      releases.push({ title: h2[1], version: dated ? dated[1] : null, unreleased: UNRELEASED.test(h2[1]), kinds: [] });
      area = null;
    } else if (h3 && releases.length) {
      if (AREAS[lang].includes(h3[1])) area = h3[1];
      else {
        area = null;
        releases[releases.length - 1].kinds.push({ area: null, heading: h3[1], entries: 0, flat: true });
      }
    } else if (h4 && releases.length) {
      releases[releases.length - 1].kinds.push({ area, heading: h4[1], entries: 0, flat: false });
    } else if (/^- /.test(line) && releases.length) {
      const r = releases[releases.length - 1];
      if (!r.kinds.length) r.kinds.push({ area: null, heading: '(none)', entries: 0, flat: true });
      r.kinds[r.kinds.length - 1].entries++;
    }
  }
  return releases;
}

const entries = r => r.kinds.reduce((n, k) => n + k.entries, 0);
const tags = new Set(git(['tag', '--list', 'v*']).split('\n').map(t => t.trim().slice(1)));
const errors = [];
const files = {};

for (const lang of ['en', 'fr']) {
  const name = 'changelog/' + lang + '.md';
  const now = parse(fs.readFileSync(path.join(ROOT, name), 'utf8'), lang);
  let before = [];
  try { before = parse(git(['show', base + ':' + name]), lang); } catch (e) { /* new file */ }
  files[lang] = now;

  for (const r of now) {
    if (!r.version && !r.unreleased) errors.push(name + ': "## ' + r.title + '" is neither "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '" nor "## X.Y.Z — YYYY-MM-DD".');
    for (const k of r.kinds) {
      if (!HEADINGS[lang].includes(k.heading)) {
        errors.push(name + ': "' + (k.flat ? '### ' : '#### ') + k.heading + '" under "' + r.title + '" must be one of ' + HEADINGS[lang].join(', ') + '.');
      } else if (!k.area && !k.flat) {
        errors.push(name + ': "#### ' + k.heading + '" under "' + r.title + '" has no "### ' + AREAS[lang].join('" or "### ') + '" above it.');
      } else if (k.flat && !r.version) {
        errors.push(name + ': "### ' + k.heading + '" under "' + r.title + '" must sit under "### ' + AREAS[lang].join('" or "### ') + '": only released versions keep the flat layout.');
      }
    }
    const kindsSeen = new Set();
    for (const k of r.kinds) {
      const key = (k.area || '') + '/' + k.heading;
      if (kindsSeen.has(key)) errors.push(name + ': "' + (k.flat ? '### ' : '#### ') + k.heading + '" appears twice' + (k.area ? ' under "### ' + k.area + '"' : '') + ' of "' + r.title + '": merge its entries into the first one.');
      kindsSeen.add(key);
    }
    if (!r.version) continue;
    const old = before.find(o => o.version === r.version);
    if (old && entries(r) > entries(old)) {
      errors.push(name + ': ' + r.version + ' is already released and gained an entry. Add it under "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '" instead.');
    } else if (!old && !tags.has(r.version)) {
      errors.push(name + ': ' + r.version + ' was never released. Add the entries under "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '": the release dates it.');
    }
  }
  if (!now.some(r => r.unreleased)) errors.push(name + ': the "## ' + (lang === 'en' ? 'Unreleased' : 'Prochaine version') + '" section is missing.');
}

const shape = (rs, lang) => rs.map(r => (r.version || 'unreleased') + ': ' + r.kinds.map(k => {
  const kind = HEADINGS.en[HEADINGS[lang].indexOf(k.heading)] || k.heading;
  const area = k.area ? AREAS.en[AREAS[lang].indexOf(k.area)] + ' ' : '';
  return area + kind + ' ' + k.entries;
}).join(', '));
const en = shape(files.en, 'en'), fr = shape(files.fr, 'fr');
for (let i = 0; i < Math.max(en.length, fr.length); i++) {
  if (en[i] !== fr[i]) errors.push('en.md and fr.md differ: "' + (en[i] || 'nothing') + '" in English, "' + (fr[i] || 'nothing') + '" in French.');
}

for (const e of errors) console.log('::error::' + e);
if (errors.length) process.exit(1);
console.log('The changelog is in order.');
