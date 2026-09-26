/**
 * @jest-environment node
 */
import { configureStore } from '@reduxjs/toolkit';
import { authReducer, initialAuthState, signedIn } from 'app/shared/auth/authSlice';
import { baseQueryWithReauth, queryString, resetRefreshState } from './baseQuery';

function jsonResponse(status: number, body: unknown, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': contentType } });
}

function makeStore() {
  return configureStore({ reducer: { auth: authReducer }, preloadedState: { auth: { ...initialAuthState } } });
}

const tokenBody = { accessToken: 'jwt-2', tokenType: 'Bearer', expiresIn: 7200, admin: { id: 1, username: 'root', avatar: null, roleIds: [1], roles: ['ROLE_ADMIN'] } };

describe('baseQueryWithReauth', () => {
  const fetchMock = jest.fn<Promise<Response>, [RequestInfo | URL, RequestInit | undefined]>();
  beforeEach(() => {
    resetRefreshState();
    fetchMock.mockReset();
    global.fetch = fetchMock as unknown as typeof fetch;
  });

  function api(store: ReturnType<typeof makeStore>) {
    return { signal: new AbortController().signal, abort: jest.fn(), dispatch: store.dispatch, getState: store.getState, extra: undefined, endpoint: 'test', type: 'query' as const };
  }

  it('sends the in-memory token as a Bearer header', async () => {
    const store = makeStore();
    store.dispatch(signedIn({ ...tokenBody, accessToken: 'jwt-1' }));
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { ok: true }));
    const result = await baseQueryWithReauth('/api/admin/dashboard', api(store), {});
    expect(result.data).toEqual({ ok: true });
    const req = fetchMock.mock.calls[0][0] as Request;
    expect(req.headers.get('authorization')).toBe('Bearer jwt-1');
  });

  it('on 401 refreshes once and retries with the new token', async () => {
    const store = makeStore();
    store.dispatch(signedIn({ ...tokenBody, accessToken: 'jwt-old' }));
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401 }, 'application/problem+json'))
      .mockResolvedValueOnce(jsonResponse(200, tokenBody))
      .mockResolvedValueOnce(jsonResponse(200, { items: [] }));
    const result = await baseQueryWithReauth('/api/admin/users', api(store), {});
    expect(result.data).toEqual({ items: [] });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const refresh = fetchMock.mock.calls[1][0] as Request;
    expect(refresh.url).toContain('/auth/refresh');
    expect(refresh.method).toBe('POST');
    expect(refresh.credentials).toBe('same-origin');
    const retry = fetchMock.mock.calls[2][0] as Request;
    expect(retry.headers.get('authorization')).toBe('Bearer jwt-2');
    expect(store.getState().auth.accessToken).toBe('jwt-2');
    expect(store.getState().auth.status).toBe('authenticated');
  });

  it('when the refresh fails it logs out and does not retry', async () => {
    const store = makeStore();
    store.dispatch(signedIn({ ...tokenBody, accessToken: 'jwt-old' }));
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401 }, 'application/problem+json'))
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401, detail: 'refresh token revoked' }, 'application/problem+json'));
    const result = await baseQueryWithReauth('/api/admin/users', api(store), {});
    expect(result.error?.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(store.getState().auth.status).toBe('anonymous');
    expect(store.getState().auth.accessToken).toBeNull();
  });

  it('a 401 from /auth/** never triggers a refresh (no loop on login or refresh itself)', async () => {
    const store = makeStore();
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401, detail: 'bad credentials' }, 'application/problem+json'));
    const result = await baseQueryWithReauth({ url: '/auth/login', method: 'POST', body: { username: 'a', password: 'b' } }, api(store), {});
    expect(result.error?.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('concurrent 401s share one refresh', async () => {
    const store = makeStore();
    store.dispatch(signedIn({ ...tokenBody, accessToken: 'jwt-old' }));
    fetchMock.mockImplementation(async (input) => {
      const req = input as Request;
      if (req.url.includes('/auth/refresh')) return jsonResponse(200, tokenBody);
      if (req.headers.get('authorization') === 'Bearer jwt-2') return jsonResponse(200, { ok: req.url });
      return jsonResponse(401, { status: 401 }, 'application/problem+json');
    });
    const [a, b] = await Promise.all([baseQueryWithReauth('/api/admin/roles', api(store), {}), baseQueryWithReauth('/api/admin/regions', api(store), {})]);
    expect(a.data).toBeDefined();
    expect(b.data).toBeDefined();
    const refreshes = fetchMock.mock.calls.filter((c) => (c[0] as Request).url.includes('/auth/refresh'));
    expect(refreshes).toHaveLength(1);
  });
});

describe('queryString', () => {
  it('skips empty values and repeats arrays', () => {
    expect(queryString({ kind: 'GOODS', q: '', status: undefined, page: 2, category: [1, 2] })).toBe('?kind=GOODS&page=2&category=1&category=2');
    expect(queryString({})).toBe('');
  });
});
