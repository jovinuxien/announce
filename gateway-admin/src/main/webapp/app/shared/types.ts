/** Shared shapes mirrored by hand from the Java DTOs (see the controllers named in each api module). */

export type Kind = 'GOODS' | 'JOB' | 'EVENT';
export const KINDS: Kind[] = ['GOODS', 'JOB', 'EVENT'];

export type AnnounceStatus = 'DRAFT' | 'PUBLISHED' | 'SOLD' | 'WITHDRAWN' | 'EXPIRED';
export const ANNOUNCE_STATUSES: AnnounceStatus[] = ['DRAFT', 'PUBLISHED', 'SOLD', 'WITHDRAWN', 'EXPIRED'];

export type SaleType = 'FIXED_PRICE' | 'AUCTION' | 'GIVEAWAY';
export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'FOR_PARTS';
export type ContractType = 'FULL_TIME' | 'PART_TIME' | 'TEMPORARY' | 'INTERNSHIP' | 'FREELANCE';

export type ListingSource = 'DBA' | 'TRADERA';
export const LISTING_SOURCES: ListingSource[] = ['DBA', 'TRADERA'];

/** AnnounceDtos.PageResponse and web.admin.PageResponse share this shape. */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

/** RFC 7807 body as written by the edge (ProblemResponses) and the backends (with an optional `code`). */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  instance?: string;
}

/** The error shape RTK Query's fetchBaseQuery hands back; `data` is the problem body when the server sent one. */
export interface ApiError {
  status: number | string;
  data?: unknown;
  error?: string;
}

export function problemOf(error: unknown): Problem | undefined {
  if (!error || typeof error !== 'object') return undefined;
  const e = error as ApiError;
  if (e.data && typeof e.data === 'object') {
    const p = e.data as Problem;
    if (p.detail || p.title || p.status) return p;
  }
  if (typeof e.status === 'number') return { status: e.status, title: e.error || String(e.status) };
  if (typeof e.error === 'string') return { title: e.error };
  return undefined;
}
