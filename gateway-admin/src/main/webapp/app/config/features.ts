import { FEATURES_KEY } from './constants';

/**
 * Feature flags. `trade` (auctions, orders, payouts) and `chat` (support queue, bulletins) default to off and are
 * backed by MSW mocks until the order and messaging modules are routed through the edge. `mocks` forces the MSW
 * worker on even in a production bundle.
 *
 * Override without a rebuild:
 *  - localStorage key `announce.features` = {"trade":true,"chat":true}
 *  - query string `?features=trade,chat` at load (persisted to localStorage; `?features=` clears them).
 */
export interface Features {
  trade: boolean;
  chat: boolean;
  mocks: boolean;
}

export const DEFAULT_FEATURES: Features = { trade: false, chat: false, mocks: false };

export function parseFeatureList(list: string | null): Partial<Features> | undefined {
  if (list === null) return undefined;
  const out: Partial<Features> = { trade: false, chat: false, mocks: false };
  list
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .forEach((name) => {
      if (name in out) (out as Record<string, boolean>)[name] = true;
    });
  return out;
}

export function resolveFeatures(search: string, stored: string | null): Features {
  let result: Features = { ...DEFAULT_FEATURES };
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as Partial<Features>;
      result = { ...result, ...pick(parsed) };
    } catch {
      /* ignore a broken value */
    }
  }
  const fromQuery = parseFeatureList(new URLSearchParams(search).get('features'));
  if (fromQuery) result = { ...result, ...fromQuery };
  return result;
}

function pick(p: Partial<Features>): Partial<Features> {
  const out: Partial<Features> = {};
  (['trade', 'chat', 'mocks'] as const).forEach((k) => {
    if (typeof p[k] === 'boolean') out[k] = p[k];
  });
  return out;
}

function load(): Features {
  if (typeof window === 'undefined') return { ...DEFAULT_FEATURES };
  let stored: string | null = null;
  try {
    stored = window.localStorage.getItem(FEATURES_KEY);
  } catch {
    stored = null;
  }
  const resolved = resolveFeatures(window.location.search, stored);
  if (new URLSearchParams(window.location.search).has('features')) {
    try {
      window.localStorage.setItem(FEATURES_KEY, JSON.stringify(resolved));
    } catch {
      /* private mode */
    }
  }
  return resolved;
}

export const features: Features = load();

export function isEnabled(name: keyof Features): boolean {
  return features[name];
}

export function shouldStartMocks(): boolean {
  const flagged = features.trade || features.chat;
  return (flagged && process.env.NODE_ENV !== 'production') || features.mocks;
}
