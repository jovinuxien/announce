import { AxiosRequestConfig, AxiosResponse } from 'axios';
import { createHttp, ApiError } from './http';
import { tokenStore } from './tokenStore';

/** A fake network: records the requests and answers by path, so the refresh interceptor is tested end to end. */
function fakeAdapter(script: (config: AxiosRequestConfig, calls: AxiosRequestConfig[]) => { status: number; data?: unknown }) {
  const calls: AxiosRequestConfig[] = [];
  const adapter = async (config: AxiosRequestConfig): Promise<AxiosResponse> => {
    calls.push(config);
    const r = script(config, calls);
    const response = { status: r.status, statusText: String(r.status), data: r.data, headers: {}, config: config as never };
    if (r.status >= 400) {
      const err = new Error(`status ${r.status}`) as Error & { response?: AxiosResponse; config?: AxiosRequestConfig; isAxiosError?: boolean };
      err.response = response as AxiosResponse;
      err.config = config;
      err.isAxiosError = true;
      throw err;
    }
    return response as AxiosResponse;
  };
  return { adapter, calls };
}

describe('http client with silent refresh', () => {
  beforeEach(() => tokenStore.set('old-token'));

  it('adds the bearer token', async () => {
    const net = fakeAdapter(() => ({ status: 200, data: { ok: true } }));
    const http = createHttp({ adapter: net.adapter });
    await http.get('/api/announces');
    expect(net.calls[0].headers?.Authorization).toBe('Bearer old-token');
  });

  it('on 401 refreshes once through the cookie and retries with the new token', async () => {
    const net = fakeAdapter((config, calls) => {
      if (config.url === '/auth/refresh') return { status: 200, data: { accessToken: 'new-token' } };
      const auth = config.headers?.Authorization;
      return auth === 'Bearer new-token' ? { status: 200, data: { retried: true } } : { status: 401, data: { status: 401 } };
    });
    const http = createHttp({ adapter: net.adapter });
    const res = await http.get('/api/orders');
    expect(res.data).toEqual({ retried: true });
    expect(net.calls.map(c => c.url)).toEqual(['/api/orders', '/auth/refresh', '/api/orders']);
    expect(tokenStore.get()).toBe('new-token');
  });

  it('parallel 401s share one refresh', async () => {
    const net = fakeAdapter(config => {
      if (config.url === '/auth/refresh') return { status: 200, data: { accessToken: 'new-token' } };
      return config.headers?.Authorization === 'Bearer new-token' ? { status: 200, data: 1 } : { status: 401 };
    });
    const http = createHttp({ adapter: net.adapter });
    await Promise.all([http.get('/a'), http.get('/b'), http.get('/c')]);
    expect(net.calls.filter(c => c.url === '/auth/refresh')).toHaveLength(1);
  });

  it('when the refresh fails the session expires and the error is a problem detail', async () => {
    const expired = jest.fn();
    const net = fakeAdapter(config => {
      if (config.url === '/auth/refresh') return { status: 401, data: { status: 401, detail: 'refresh token revoked' } };
      return { status: 401, data: { status: 401, title: 'Unauthorized', detail: 'a valid bearer token is required' } };
    });
    const http = createHttp({ adapter: net.adapter, onSessionExpired: expired });
    await expect(http.get('/api/orders')).rejects.toBeInstanceOf(ApiError);
    await expect(http.get('/api/orders')).rejects.toMatchObject({ status: 401, message: 'a valid bearer token is required' });
    expect(expired).toHaveBeenCalled();
    expect(tokenStore.get()).toBeNull();
  });

  it('a 401 from /auth itself is not retried', async () => {
    const net = fakeAdapter(() => ({ status: 401, data: { status: 401, detail: 'bad credentials' } }));
    const http = createHttp({ adapter: net.adapter });
    await expect(http.post('/auth/login', {})).rejects.toMatchObject({ status: 401, message: 'bad credentials' });
    expect(net.calls).toHaveLength(1);
  });
});
