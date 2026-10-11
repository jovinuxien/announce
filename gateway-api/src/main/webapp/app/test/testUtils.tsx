import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { setupServer } from 'msw/node';
import '@testing-library/jest-dom';
import 'app/i18n';
import { makeStore } from 'app/config/store';
import { tokenStore } from 'app/config/tokenStore';
import { login, sessionExpired } from 'app/shared/auth/authSlice';
import { handlers } from 'app/mocks/handlers';
import { chatDb, seedChat } from 'app/mocks/chatData';
import { seedTrade, tradeDb } from 'app/mocks/tradeData';

/**
 * The page tests run against the same MSW handlers and fixtures as `features.mocks` in the browser
 * (app/mocks/handlers.ts, app/mocks/chatData.ts, app/mocks/tradeData.ts), served by msw/node (axios goes through jsdom's XMLHttpRequest,
 * which msw intercepts). The fixtures are in the backends' real shapes (doc/frontend-edges.md §15, §16), so what passes
 * here renders the same against the backend. The signed-in member is #1 (chatData.ME).
 */
export const server = setupServer(...handlers);

export function useMockServer() {
  beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
  beforeEach(() => {
    seedChat();
    chatDb.autoReply = false;
    seedTrade();
    tradeDb.autoCarrier = false;
  });
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());
}

export const ME_USER = {
  id: 1,
  username: 'anna',
  nickname: 'Anna',
  mobile: null,
  avatar: null,
};

export function renderPage(ui: ReactElement, path: string, initialEntry: string = path, options: { anonymous?: boolean } = {}) {
  const store = makeStore();
  if (options.anonymous) {
    tokenStore.set(null);
    store.dispatch(sessionExpired());
  } else {
    tokenStore.set('t');
    store.dispatch(login.fulfilled({ accessToken: 't', tokenType: 'Bearer', expiresIn: 7200, user: ME_USER }, 'test', { username: 'anna', password: 'x' }));
  }
  return {
    store,
    ...render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[initialEntry]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={<div data-testid="elsewhere" />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    ),
  };
}
