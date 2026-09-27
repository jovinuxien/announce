#!/usr/bin/env node
/**
 * i18n consistency check for @announce/admin-web.
 *  1. Every key in one language's file exists in every other language (per file under app/i18n/<lng>/).
 *  2. No value is an empty string.
 *  3. Every literal `t('ns.key')` / `t("ns.key")` / `i18nKey="ns.key"` in app/**\/*.ts(x) exists in `en`.
 *     Keys are `<file>.<path>` because config/i18n.ts mounts each JSON file under its file name.
 * Exit code 1 with a report on any failure.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(fileURLToPath(import.meta.url), '..', '..');
const i18nDir = join(root, 'app', 'i18n');
const appDir = join(root, 'app');
const REFERENCE = 'en';

function flatten(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v !== null && typeof v === 'object') flatten(v, key, out);
    else out[key] = v;
  }
  return out;
}

function walk(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    const st = statSync(p);
    if (st.isDirectory()) {
      if (entry === 'node_modules') continue;
      walk(p, acc);
    } else if (/\.(ts|tsx)$/.test(entry) && !/\.test\.tsx?$/.test(entry)) acc.push(p);
  }
  return acc;
}

const languages = readdirSync(i18nDir).filter((d) => statSync(join(i18nDir, d)).isDirectory()).sort();
if (!languages.includes(REFERENCE)) {
  console.error(`No reference language "${REFERENCE}" under ${i18nDir}`);
  process.exit(1);
}

const problems = [];
const resources = {}; // lng -> { file -> flat }
for (const lng of languages) {
  resources[lng] = {};
  for (const file of readdirSync(join(i18nDir, lng)).filter((f) => f.endsWith('.json')).sort()) {
    const ns = file.replace(/\.json$/, '');
    let json;
    try {
      json = JSON.parse(readFileSync(join(i18nDir, lng, file), 'utf8'));
    } catch (e) {
      problems.push(`${lng}/${file}: invalid JSON (${e.message})`);
      continue;
    }
    const flat = flatten(json);
    resources[lng][ns] = flat;
    for (const [k, v] of Object.entries(flat)) {
      if (typeof v !== 'string') problems.push(`${lng}/${file}: "${k}" is not a string`);
      else if (v.trim() === '') problems.push(`${lng}/${file}: "${k}" is empty`);
    }
  }
}

// 1. same files and same keys across languages
const allFiles = new Set(languages.flatMap((l) => Object.keys(resources[l])));
for (const ns of [...allFiles].sort()) {
  for (const lng of languages) {
    if (!resources[lng][ns]) {
      problems.push(`${lng}/${ns}.json is missing`);
    }
  }
  const union = new Set(languages.flatMap((l) => Object.keys(resources[l][ns] ?? {})));
  for (const key of [...union].sort()) {
    for (const lng of languages) {
      if (resources[lng][ns] && !(key in resources[lng][ns])) {
        const present = languages.filter((l) => resources[l][ns] && key in resources[l][ns]);
        problems.push(`${lng}/${ns}.json: missing "${key}" (present in ${present.join(', ')})`);
      }
    }
  }
}

// 3. literal keys used in code exist in the reference language
const enKeys = new Set();
for (const [ns, flat] of Object.entries(resources[REFERENCE])) {
  for (const k of Object.keys(flat)) enKeys.add(`${ns}.${k}`);
}
const keyPattern = /(?:\bt|i18nKey)\s*(?:\(\s*|=)\s*(['"])([^'"`\n]+?)\1/g;
let used = 0;
for (const file of walk(appDir)) {
  const src = readFileSync(file, 'utf8');
  let m;
  while ((m = keyPattern.exec(src)) !== null) {
    const key = m[2];
    // skip things that are clearly not translation keys (paths, urls, plain words without a namespace)
    if (!key.includes('.') || key.startsWith('/') || key.startsWith('http')) continue;
    used++;
    if (!enKeys.has(key)) {
      const line = src.slice(0, m.index).split('\n').length;
      problems.push(`${relative(root, file)}:${line}: key "${key}" is not defined in ${REFERENCE}`);
    }
  }
}

const total = Object.values(resources[REFERENCE]).reduce((n, f) => n + Object.keys(f).length, 0);
console.log(`i18n check: languages [${languages.join(', ')}], ${allFiles.size} files, ${total} keys in ${REFERENCE}, ${used} literal keys in code`);
if (problems.length) {
  console.error(`\n${problems.length} problem(s):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log('OK');
