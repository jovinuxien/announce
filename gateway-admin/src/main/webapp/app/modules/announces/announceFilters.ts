import { ANNOUNCE_STATUSES, KINDS, type AnnounceStatus, type Kind, type SaleType } from 'app/shared/types';
import type { AnnounceListParams } from 'app/shared/api/announcesApi';
import { DEFAULT_PAGE_SIZE } from 'app/config/constants';

/** Filter state of the announces list; lives in the URL query so links from the dashboard and reloads work. */
export interface AnnounceFilters {
  kind: Kind | '';
  status: AnnounceStatus | '';
  seller: string;
  region: string;
  saleType: SaleType | '';
  q: string;
  sort: 'newest' | 'oldest' | 'price_asc' | 'price_desc';
  page: number;
  size: number;
}

export const SORTS: AnnounceFilters['sort'][] = ['newest', 'oldest', 'price_asc', 'price_desc'];

export const defaultFilters: AnnounceFilters = {
  kind: '', status: '', seller: '', region: '', saleType: '', q: '', sort: 'newest', page: 1, size: DEFAULT_PAGE_SIZE,
};

const SALE_TYPES: SaleType[] = ['FIXED_PRICE', 'AUCTION', 'GIVEAWAY'];

export function filtersFromSearch(search: string | URLSearchParams): AnnounceFilters {
  const sp = typeof search === 'string' ? new URLSearchParams(search) : search;
  const kind = sp.get('kind');
  const status = sp.get('status');
  const saleType = sp.get('saleType');
  const sort = sp.get('sort');
  const page = Number(sp.get('page'));
  const size = Number(sp.get('size'));
  return {
    kind: KINDS.includes(kind as Kind) ? (kind as Kind) : '',
    status: ANNOUNCE_STATUSES.includes(status as AnnounceStatus) ? (status as AnnounceStatus) : '',
    seller: /^\d+$/.test(sp.get('seller') ?? '') ? (sp.get('seller') as string) : '',
    region: /^\d+$/.test(sp.get('region') ?? '') ? (sp.get('region') as string) : '',
    saleType: SALE_TYPES.includes(saleType as SaleType) ? (saleType as SaleType) : '',
    q: (sp.get('q') ?? '').trim(),
    sort: SORTS.includes(sort as AnnounceFilters['sort']) ? (sort as AnnounceFilters['sort']) : 'newest',
    page: Number.isInteger(page) && page > 0 ? page : 1,
    size: Number.isInteger(size) && size > 0 && size <= 200 ? size : DEFAULT_PAGE_SIZE,
  };
}

/** URL query for the SPA route: only what differs from the defaults, so links stay short. */
export function filtersToSearch(f: AnnounceFilters): string {
  const sp = new URLSearchParams();
  (Object.keys(defaultFilters) as (keyof AnnounceFilters)[]).forEach((k) => {
    const v = f[k];
    if (v === defaultFilters[k] || v === '' || v === undefined) return;
    sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

/** The request parameters of AnnounceAdminController.list. */
export function filtersToParams(f: AnnounceFilters): AnnounceListParams {
  return {
    kind: f.kind || undefined,
    status: f.status || undefined,
    seller: f.seller ? Number(f.seller) : undefined,
    region: f.region ? Number(f.region) : undefined,
    saleType: f.saleType || undefined,
    q: f.q || undefined,
    page: f.page,
    size: f.size,
    sort: f.sort,
  };
}

export function activeFilterCount(f: AnnounceFilters): number {
  return (['kind', 'status', 'seller', 'region', 'saleType', 'q'] as const).filter((k) => f[k] !== '').length;
}
