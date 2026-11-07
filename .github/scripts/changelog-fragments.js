/*
 * The changelog entries waiting for the next release: one file per entry in
 * changelog/unreleased/, both languages in it, so that two pull requests never edit the
 * same lines of changelog/{en,fr}.md. The release puts them under the version it dates
 * and deletes them (changelog-release.js); the build puts them under "Unreleased" in the
 * jar's copy (gradle/target.gradle.kts does the same in Kotlin: keep the two alike).
 *
 * A file, named after its change (changelog/unreleased/mac-crash.md):
 *
 *   part: client
 *   kind: fixed
 *
 *   en: **Crash on Mac fixed** — The game no longer crashes...
 *
 *   fr: **Plantage sur Mac corrigé** — Le jeu ne plante plus...
 *
 * part is server or client, kind new, improved or fixed. A line that names no field
 * carries the one above on. Entries are listed in the order of their file names.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const DIR = path.join(ROOT, 'changelog', 'unreleased');
const PARTS = ['server', 'client'];
const KINDS = ['new', 'improved', 'fixed'];
const FIELDS = ['part', 'kind', 'en', 'fr'];
const AREAS = { en: ['Server', 'Client'], fr: ['Serveur', 'Client'] };
const HEADINGS = { en: ['New', 'Improved', 'Fixed'], fr: ['Nouveautés', 'Améliorations', 'Corrections'] };
const UNRELEASED = /^## (Unreleased|Prochaine version)[ \t]*$/m;

/** The fragment files, sorted by name: README.md explains the format and is none. */
function files() {
  if (!fs.existsSync(DIR)) return [];
  return fs.readdirSync(DIR).filter(f => f.endsWith('.md') && f !== 'README.md').sort();
}

/** { fragments: [{ file, part, kind, en, fr }], errors: [string] } */
function read() {
  const fragments = [];
  const errors = [];
  for (const file of files()) {
    const name = 'changelog/unreleased/' + file;
    const fields = {};
    let current = null;
    for (const raw of fs.readFileSync(path.join(DIR, file), 'utf8').split(/\r?\n/)) {
      const line = raw.trim();
      if (!line) continue;
      const field = /^([a-z]+):\s*(.*)$/.exec(line);
      if (field && FIELDS.includes(field[1])) {
        current = field[1];
        if (current in fields) errors.push(name + ': "' + current + ':" is given twice. One file holds one entry.');
        fields[current] = field[2];
      } else if (current === 'en' || current === 'fr') {
        fields[current] += (fields[current] ? ' ' : '') + line;
      } else {
        errors.push(name + ': "' + line + '" is not one of ' + FIELDS.map(f => f + ':').join(', ') + '.');
      }
    }
    if (!PARTS.includes(fields.part)) errors.push(name + ': "part:" must be ' + PARTS.join(' or ') + '.');
    if (!KINDS.includes(fields.kind)) errors.push(name + ': "kind:" must be ' + KINDS.join(', ') + '.');
    for (const lang of ['en', 'fr']) {
      const text = fields[lang] || '';
      if (!text) errors.push(name + ': "' + lang + ':" is missing: every entry is written in English and in French.');
      else if (!/^\*\*[^*]+\*\* — \S/.test(text)) errors.push(name + ': "' + lang + ':" must read "**Title** — What the player now sees.", without a leading "- ".');
    }
    fragments.push({ file, part: fields.part, kind: fields.kind, en: fields.en || '', fr: fields.fr || '' });
  }
  return { fragments, errors };
}

/** Where the body of the Unreleased section is: { start, end } offsets, or null. */
function unreleased(text) {
  const open = UNRELEASED.exec(text);
  if (!open) return null;
  const start = open.index + open[0].length;
  const next = text.slice(start).search(/^## /m);
  return { start, end: next < 0 ? text.length : start + next };
}

/** The bullets written straight under Unreleased, as entries: [{ part, kind, text }]. */
function written(body, lang) {
  const entries = [];
  let part = null, kind = null;
  for (const raw of body.split(/\r?\n/)) {
    const line = raw.trim();
    const h3 = /^### (.+)$/.exec(line), h4 = /^#### (.+)$/.exec(line);
    if (h3) part = PARTS[AREAS[lang].indexOf(h3[1].trim())] || null;
    else if (h4) kind = KINDS[HEADINGS[lang].indexOf(h4[1].trim())] || null;
    else if (line.startsWith('- ')) entries.push({ part: part || 'client', kind: kind || 'new', text: line.slice(2).trim() });
    else if (line && entries.length) entries[entries.length - 1].text += ' ' + line;
  }
  return entries;
}

/** One version's body, Server then Client, New, Improved, Fixed; '' with no entry. */
function render(entries, lang) {
  const out = [];
  PARTS.forEach((part, p) => {
    const ofPart = entries.filter(e => e.part === part);
    if (!ofPart.length) return;
    out.push('### ' + AREAS[lang][p], '');
    KINDS.forEach((kind, k) => {
      const ofKind = ofPart.filter(e => e.kind === kind);
      if (!ofKind.length) return;
      out.push('#### ' + HEADINGS[lang][k], ...ofKind.map(e => '- ' + e.text), '');
    });
  });
  return out.join('\n');
}

/** What goes under Unreleased of a language: what is written there, then the fragments. */
function entries(text, lang, fragments) {
  const at = unreleased(text);
  const before = at ? written(text.slice(at.start, at.end), lang) : [];
  return before.concat(fragments.map(f => ({ part: f.part, kind: f.kind, text: f[lang] })));
}

module.exports = { DIR, ROOT, AREAS, HEADINGS, files, read, unreleased, written, render, entries };
