/**
 * The small filter set the admin lists of the order module accept (doc/frontend-edges.md §12): a status, a seller
 * id, a buyer id (orders only) and a title search (`q`, auctions and orders). Kept in the URL query next to page and
 * size so links and reloads work; parsed defensively because the query is user input.
 */
export interface TradeFilters<S extends string> {
  status: S | '';
  seller: string;
  buyer: string;
  q: string;
}

export function tradeFiltersFromSearch<S extends string>(sp: URLSearchParams, statuses: readonly S[]): TradeFilters<S> {
  const status = sp.get('status') ?? '';
  const id = (key: string) => (/^\d+$/.test(sp.get(key) ?? '') ? (sp.get(key) as string) : '');
  return {
    status: statuses.includes(status as S) ? (status as S) : '',
    seller: id('seller'),
    buyer: id('buyer'),
    q: (sp.get('q') ?? '').trim(),
  };
}

/** The request parameters of the list endpoints: numbers for the ids, nothing for an empty value. */
export function tradeFiltersToParams<S extends string>(f: TradeFilters<S>): { status?: S | ''; seller?: number | ''; buyer?: number | ''; q?: string } {
  return {
    status: f.status || undefined,
    seller: f.seller ? Number(f.seller) : undefined,
    buyer: f.buyer ? Number(f.buyer) : undefined,
    q: f.q || undefined,
  };
}

export function activeTradeFilterCount<S extends string>(f: TradeFilters<S>): number {
  return (['status', 'seller', 'buyer', 'q'] as const).filter((k) => f[k] !== '').length;
}
