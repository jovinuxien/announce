import { FEATURES_KEY } from './constants';

/**
 * Feature flags.
 *  - `trade` (auctions, orders, payouts): ON by default since 2026-10-10. The screens read the order module's admin
 *    endpoints through the edge (doc/frontend-edges.md §12); when the module is down they show the problem alert.
 *  - `chat` (support queue, bulletins): off; still served by the MSW fixtures until that wiring is done.
 *  - `mocks`: starts the MSW worker so every phase-2 screen (trade included) is fed by app/mocks/handlers.ts. The
 *    worker never starts on its own any more, otherwise it would hijack the real trade endpoints in development.
 *
 * Override without a rebuild:
 *  - localStorage key `announce.features` = {"trade":true,"chat":true,"mocks":true}
 *  - query string `?features=trade,chat,mocks` at load (persisted to localStorage; `?features=` clears them, which
 *    also turns the defaults off: `?features=trade` is the way back to the defaults).
 */
export interface Features {
  trade: boolean;
  chat: boolean;
  mocks: boolean;
}

export const DEFAULT_FEATURES: Features = { trade: true, chat: false, mocks: false };

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

/** Only the explicit `mocks` flag starts the MSW worker; see shouldStartMocksFor for the rule on its own. */
export function shouldStartMocks(): boolean {
  return shouldStartMocksFor(features);
}

export function shouldStartMocksFor(f: Features): boolean {
  return f.mocks;
}
