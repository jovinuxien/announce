/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'jsdom',
  // msw/node and undici export a 'node' condition that jsdom's default ('browser') would skip.
  testEnvironmentOptions: { customExportConditions: [''] },
  roots: ['<rootDir>/app'],
  testMatch: ['**/*.test.ts', '**/*.test.tsx'],
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: 'tsconfig.jest.json' }],
  },
  moduleNameMapper: {
    '^app/(.*)$': '<rootDir>/app/$1',
    '\\.(css|scss)$': 'identity-obj-proxy',
  },
  setupFiles: ['<rootDir>/scripts/jest.polyfills.js'],
  setupFilesAfterEnv: ['<rootDir>/scripts/jest.setup.ts'],
  clearMocks: true,
};
