import '@testing-library/jest-dom';
import { initI18n } from '../app/config/i18n';

// Real English resources so tests can assert on visible strings.
beforeAll(async () => {
  await initI18n('en');
});
