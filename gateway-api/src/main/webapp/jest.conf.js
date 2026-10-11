module.exports = {
  testEnvironment: 'jsdom',
  // msw/node and undici export a 'node' condition that jsdom's default ('browser') would skip.
  testEnvironmentOptions: { customExportConditions: [''] },
  roots: ['<rootDir>/app'],
  testMatch: ['**/*.spec.ts', '**/*.spec.tsx'],
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: 'tsconfig.test.json', diagnostics: false }],
  },
  moduleNameMapper: {
    '\\.(css|scss)$': 'identity-obj-proxy',
    '^app/(.*)$': '<rootDir>/app/$1',
  },
  setupFiles: ['<rootDir>/jest.polyfills.js'],
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  cacheDirectory: '<rootDir>/../../../target/jest-cache',
};
