import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/**
 * Started from app/index.tsx only when `shouldStartMocks()` is true (a phase-2 flag on in a non-production bundle, or
 * `features.mocks`). Needs /mockServiceWorker.js at the site root: `npx msw init public` regenerates it
 * (public/ is copied to the output by webpack). Unmatched requests pass through to the edge untouched.
 */
export const worker = setupWorker(...handlers);

export async function startMocks() {
  try {
    await worker.start({ onUnhandledRequest: 'bypass', serviceWorker: { url: '/mockServiceWorker.js' } });
    // eslint-disable-next-line no-console
    console.info('[admin-web] MSW mocks active for the phase-2 admin endpoints');
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('[admin-web] MSW could not start; phase-2 screens will hit the real edge', e);
  }
}
