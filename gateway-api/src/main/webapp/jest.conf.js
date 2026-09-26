module.exports = {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/app'],
  testMatch: ['**/*.spec.ts', '**/*.spec.tsx'],
  transform: { '^.+\\.tsx?$': ['ts-jest', { tsconfig: 'tsconfig.test.json', diagnostics: false }] },
  moduleNameMapper: {
    '\\.(css|scss)$': 'identity-obj-proxy',
    '^app/(.*)$': '<rootDir>/app/$1',
  },
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  cacheDirectory: '<rootDir>/../../../target/jest-cache',
};
