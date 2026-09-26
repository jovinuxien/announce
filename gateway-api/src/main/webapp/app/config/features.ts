// Phase 2 screens (auction, buy flow, orders, chat) are behind flags that default OFF. Switch them on for a
// browser session with:  localStorage.announceFeatures = '{"trade":true,"chat":true,"mocks":true}'
// `mocks` starts the MSW worker (app/mocks) so the flagged screens run against doc/order-context.md §8/§9
// contracts without the order and messaging services.
export interface Features { trade: boolean; chat: boolean; mocks: boolean }

const DEFAULTS: Features = { trade: false, chat: false, mocks: false };

export function readFeatures(storage: Pick<Storage, 'getItem'> | undefined = safeStorage()): Features {
  try {
    const raw = storage?.getItem('announceFeatures');
    if (!raw) return { ...DEFAULTS };
    const parsed = JSON.parse(raw) as Partial<Features>;
    return { ...DEFAULTS, ...parsed };
  } catch {
    return { ...DEFAULTS };
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
