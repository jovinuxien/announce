import { Kind, KINDS } from 'app/shared/model/announce';
import { OcsHit, SearchHit } from 'app/shared/model/search';

/** Numeric facets the searcher filters by range (`field=min,max`); everything else is a term filter. */
export const RANGE_FIELDS = [
  'price', 'salary_min', 'salary_max', 'starts_epoch', 'ends_epoch', 'expire_epoch', 'application_deadline_epoch', 'ticket_price', 'variant_price',
];
/** Facets shown in the rail, in this order; unknown facets (dynamic attributes such as km) follow. */
export const FACET_ORDER = ['category_ids', 'price', 'sale_type', 'condition', 'source', 'city', 'region_id', 'contract_type', 'employer', 'salary_min', 'starts_epoch', 'ticketed', 'ticket_price'];
export const DEFAULT_SIZE = 24;
const RESERVED = new Set(['q', 'kind', 'sort', 'page', 'size']);

export interface Range { min?: number; max?: number }
export interface SearchState {
  q: string;
  kind: Kind | null;
  filters: Record<string, string[]>;
  ranges: Record<string, Range>;
  sort: string | null;
  page: number;
  size: number;
}

export const emptySearch = (): SearchState => ({ q: '', kind: null, filters: {}, ranges: {}, sort: null, page: 1, size: DEFAULT_SIZE });

export function isRangeField(field: string): boolean {
  return RANGE_FIELDS.includes(field);
}

/** URL -> state. `?q=toyota&kind=GOODS&city=Kolding,Vamdrup&price=100..500&sort=-price&page=2` */
export function parseSearchParams(search: string): SearchState {
  const p = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const s = emptySearch();
  s.q = (p.get('q') ?? '').trim();
  const kind = p.get('kind')?.toUpperCase();
  s.kind = kind && (KINDS as string[]).includes(kind) ? (kind as Kind) : null;
  s.sort = p.get('sort') || null;
  s.page = Math.max(1, parseInt(p.get('page') ?? '1', 10) || 1);
  s.size = Math.min(100, Math.max(1, parseInt(p.get('size') ?? String(DEFAULT_SIZE), 10) || DEFAULT_SIZE));
  p.forEach((value, key) => {
    if (RESERVED.has(key) || !value) return;
    if (isRangeField(key)) {
      const [min, max] = value.split('..');
      const r: Range = {};
      if (min !== '' && !Number.isNaN(Number(min))) r.min = Number(min);
      if (max != null && max !== '' && !Number.isNaN(Number(max))) r.max = Number(max);
      if (r.min != null || r.max != null) s.ranges[key] = r;
    } else {
      const values = value.split(',').map(v => v.trim()).filter(Boolean);
      if (values.length) s.filters[key] = Array.from(new Set([...(s.filters[key] ?? []), ...values]));
    }
  });
  return s;
}

/** state -> URL query string (without `?`), stable key order so links compare equal. */
export function toSearchParams(s: SearchState): string {
  const p = new URLSearchParams();
  if (s.q) p.set('q', s.q);
  if (s.kind) p.set('kind', s.kind);
  Object.keys(s.filters)
    .sort()
    .forEach(k => {
      if (s.filters[k].length) p.set(k, s.filters[k].join(','));
    });
  Object.keys(s.ranges)
    .sort()
    .forEach(k => {
      const r = s.ranges[k];
      if (r.min != null || r.max != null) p.set(k, `${r.min ?? ''}..${r.max ?? ''}`);
    });
  if (s.sort) p.set('sort', s.sort);
  if (s.page > 1) p.set('page', String(s.page));
  if (s.size !== DEFAULT_SIZE) p.set('size', String(s.size));
  return p.toString();
}

/** state -> the OCS searcher query the edge forwards (offset/limit paging, `field=a,b`, `field=min,max`, `sort=-field`). */
export function toOcsParams(s: SearchState): URLSearchParams {
  const p = new URLSearchParams();
  if (s.q) p.set('q', s.q);
  p.set('limit', String(s.size));
  p.set('offset', String((s.page - 1) * s.size));
  p.set('withFacets', 'true');
  if (s.kind) p.set('kind', s.kind);
  Object.keys(s.filters)
    .sort()
    .forEach(k => {
      if (s.filters[k].length) p.set(k, s.filters[k].join(','));
    });
  Object.keys(s.ranges)
    .sort()
    .forEach(k => {
      const r = s.ranges[k];
      if (r.min != null || r.max != null) p.set(k, `${r.min ?? 0},${r.max ?? 999999999}`);
    });
  if (s.sort) p.set('sort', s.sort);
  return p;
}

export function toggleFilter(s: SearchState, field: string, value: string): SearchState {
  const current = s.filters[field] ?? [];
  const next = current.includes(value) ? current.filter(v => v !== value) : [...current, value];
  const filters = { ...s.filters };
  if (next.length) filters[field] = next;
  else delete filters[field];
  return { ...s, filters, page: 1 };
}

export function setRange(s: SearchState, field: string, range: Range): SearchState {
  const ranges = { ...s.ranges };
  if (range.min == null && range.max == null) delete ranges[field];
  else ranges[field] = range;
  return { ...s, ranges, page: 1 };
}

export function setKind(s: SearchState, kind: Kind | null): SearchState {
  // kind-specific facets do not carry over between kinds
  return { ...s, kind, filters: {}, ranges: {}, page: 1 };
}

export function clearRefinements(s: SearchState): SearchState {
  return { ...s, filters: {}, ranges: {}, page: 1 };
}

export function hasRefinements(s: SearchState): boolean {
  return Object.keys(s.filters).length > 0 || Object.keys(s.ranges).length > 0;
}

const str = (v: unknown): string | undefined => (v == null ? undefined : String(v));
const num = (v: unknown): number | undefined => {
  if (v == null || v === '') return undefined;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isNaN(n) ? undefined : n;
};

/** One searcher hit -> the card model shared by announces (source local) and crawled listings. */
export function toSearchHit(hit: OcsHit): SearchHit {
  const d = hit.document.data ?? {};
  return {
    id: hit.document.id,
    source: str(d.source) ?? 'local',
    kind: str(d.kind) ?? 'GOODS',
    title: str(d.title) ?? '',
    description: str(d.description),
    price: num(d.price),
    currency: str(d.currency),
    saleType: str(d.sale_type),
    imageUrl: str(d.image_url),
    city: str(d.city),
    postalCode: str(d.postal_code),
    externalUrl: str(d.external_url),
    createdEpoch: num(d.created_epoch),
    endsEpoch: num(d.ends_epoch),
    employer: str(d.employer),
    contractType: str(d.contract_type),
    startsEpoch: num(d.starts_epoch),
    data: d,
  };
}

export function totalPages(matchCount: number, size: number): number {
  return Math.max(1, Math.ceil(matchCount / size));
}
