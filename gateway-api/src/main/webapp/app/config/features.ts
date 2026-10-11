// Phase 2 screens behind flags. `trade` (auction panel, buy box, orders, payouts) and `chat` (contact seller, inbox,
// thread) default ON since 2026-10-11: the screens run against the order module and messaging through the edge
// (doc/frontend-edges.md §15, §16). `mocks` starts the MSW worker (app/mocks) so the flagged screens run without the
// services. Override for a browser session with:  localStorage.announceFeatures = '{"trade":true,"chat":true,"mocks":true}'
export interface Features {
  trade: boolean;
  chat: boolean;
  mocks: boolean;
}

export const DEFAULT_FEATURES: Features = {
  trade: true,
  chat: true,
  mocks: false,
};

export function readFeatures(storage: Pick<Storage, 'getItem'> | undefined = safeStorage()): Features {
  try {
    const raw = storage?.getItem('announceFeatures');
    if (!raw) return { ...DEFAULT_FEATURES };
    const parsed = JSON.parse(raw) as Partial<Features>;
    return { ...DEFAULT_FEATURES, ...parsed };
  } catch {
    return { ...DEFAULT_FEATURES };
  }
}

function safeStorage(): Storage | undefined {
  try {
    return typeof window !== 'undefined' ? window.localStorage : undefined;
  } catch {
    return undefined;
  }
}

export const features: Features = readFeatures();
