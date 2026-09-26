import axios, { AxiosError, AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { tokenStore } from 'app/config/tokenStore';
import { ProblemDetail } from 'app/shared/model/announce';

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.status = status;
    this.problem = problem;
  }
}

export interface HttpOptions {
  /** Called when a refresh fails: the session is over (the auth slice logs out and the shell redirects). */
  onSessionExpired?: () => void;
  adapter?: AxiosRequestConfig['adapter'];
}

interface RetriableConfig extends InternalAxiosRequestConfig { _retried?: boolean }

/**
 * One axios instance: adds the Bearer token, and on a 401 from any non-auth call refreshes once through the
 * httpOnly cookie (single-flight, so parallel 401s share one refresh) and retries the original request.
 */
export function createHttp(options: HttpOptions = {}): AxiosInstance {
  const http = axios.create({ baseURL: '', timeout: 15000, adapter: options.adapter, withCredentials: true });
  let refreshing: Promise<string | null> | null = null;

  const refresh = (): Promise<string | null> => {
    if (!refreshing) {
      refreshing = axios
        .post<{ accessToken: string }>('/auth/refresh', null, { adapter: options.adapter, withCredentials: true })
        .then(res => {
          tokenStore.set(res.data.accessToken);
          return res.data.accessToken;
        })
        .catch(() => {
          tokenStore.set(null);
          options.onSessionExpired?.();
          return null;
        })
        .finally(() => {
          refreshing = null;
        });
    }
    return refreshing;
  };

  http.interceptors.request.use(config => {
    const token = tokenStore.get();
    if (token && !config.headers.Authorization) config.headers.Authorization = `Bearer ${token}`;
    return config;
  });

  http.interceptors.response.use(
    response => response,
    async (error: AxiosError) => {
      const config = error.config as RetriableConfig | undefined;
      const status = error.response?.status;
      const isAuthCall = config?.url?.startsWith('/auth/') ?? false;
      if (status === 401 && config && !config._retried && !isAuthCall) {
        config._retried = true;
        const token = await refresh();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
          return http.request(config);
        }
      }
      return Promise.reject(toApiError(error));
    }
  );
  return http;
}

export function toApiError(error: AxiosError): ApiError {
  const status = error.response?.status ?? 0;
  const body = error.response?.data as ProblemDetail | undefined;
  const message = body?.detail || body?.title || error.message || 'request failed';
  return new ApiError(status, message, body);
}

export const http = createHttp({
  onSessionExpired: () => {
    // lazy import to avoid a cycle with the store
    import('app/config/store').then(({ default: store }) => import('app/shared/auth/authSlice').then(m => store.dispatch(m.sessionExpired())));
  },
});
