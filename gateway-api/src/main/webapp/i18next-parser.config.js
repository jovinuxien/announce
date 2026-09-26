// `npm run i18n:check` fails when a key used in the code is missing from any of en, da, sv
// (or when a file holds a key the code no longer uses). Keys are `namespace:key.path`.
module.exports = {
  locales: ['en', 'da', 'sv'],
  input: ['app/**/*.{ts,tsx}', '!app/**/*.spec.{ts,tsx}', '!app/i18n/**', '!app/mocks/**'],
  output: 'app/i18n/locales/$LOCALE/$NAMESPACE.json',
  defaultNamespace: 'common',
  namespaceSeparator: ':',
  keySeparator: '.',
  defaultValue: (locale, namespace, key) => `__MISSING__ ${key}`,
  keepRemoved: true, // keys built at runtime (kind.${k}, status.${s}, validation.*) are not visible to the lexer
  sort: true,
  createOldCatalogs: false,
  lexers: { ts: ['JavascriptLexer'], tsx: ['JsxLexer'] },
};
