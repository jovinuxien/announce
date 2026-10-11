import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { setupServer } from 'msw/node';
import { makeStore } from 'app/config/store';
import { signedIn } from 'app/shared/auth/authSlice';
import { handlers } from 'app/mocks/handlers';

/**
 * The trade page tests run against the same MSW handlers and fixtures as `features.mocks` in the browser
 * (app/mocks/handlers.ts, app/mocks/tradeFixtures.ts), served by msw/node over the undici fetch polyfilled in
 * scripts/jest.polyfills.js. The fixtures are in the order module's real shapes (doc/frontend-edges.md §12), so
 * what passes here renders the same against the backend.
 */
export const server = setupServer(...handlers);

export function useTradeServer() {
  beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());
}

export function renderTradePage(ui: ReactElement, path: string, initialEntry: string = path) {
  const store = makeStore();
  store.dispatch(signedIn({ accessToken: 't', tokenType: 'Bearer', expiresIn: 7200, admin: { id: 1, username: 'root', avatar: null, roleIds: [1], roles: ['ROLE_ADMIN'] } }));
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialEntry]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path={path} element={ui} />
          <Route path="*" element={<div data-testid="elsewhere" />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}
