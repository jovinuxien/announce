import { http } from 'app/config/http';
import {
  AnnounceListQuery,
  AnnounceRequest,
  AnnounceResponse,
  AnnounceSummaryResponse,
  PageResponse,
} from 'app/shared/model/announce';

const BASE = '/api/announces';

export function toListParams(q: AnnounceListQuery): URLSearchParams {
  const p = new URLSearchParams();
  if (q.kind) p.set('kind', q.kind);
  (q.category ?? []).forEach(c => p.append('category', String(c)));
  if (q.brand != null) p.set('brand', String(q.brand));
  if (q.seller != null) p.set('seller', String(q.seller));
  if (q.region != null) p.set('region', String(q.region));
  if (q.q) p.set('q', q.q);
  if (q.saleType) p.set('saleType', q.saleType);
  if (q.status) p.set('status', q.status);
  if (q.page != null) p.set('page', String(q.page));
  if (q.size != null) p.set('size', String(q.size));
  if (q.sort) p.set('sort', q.sort);
  return p;
}

export const announceApi = {
  list: (q: AnnounceListQuery = {}) =>
    http.get<PageResponse<AnnounceSummaryResponse>>(`${BASE}?${toListParams(q).toString()}`).then(r => r.data),
  get: (id: string) => http.get<AnnounceResponse>(`${BASE}/${encodeURIComponent(id)}`).then(r => r.data),
  create: (body: AnnounceRequest) => http.post<AnnounceResponse>(BASE, body).then(r => r.data),
  update: (id: string, body: AnnounceRequest) => http.put<AnnounceResponse>(`${BASE}/${encodeURIComponent(id)}`, body).then(r => r.data),
  publish: (id: string, validityDays?: number) =>
    http.post<AnnounceResponse>(`${BASE}/${encodeURIComponent(id)}/publish`, validityDays ? { validityDays } : {}).then(r => r.data),
  withdraw: (id: string) => http.post<AnnounceResponse>(`${BASE}/${encodeURIComponent(id)}/withdraw`).then(r => r.data),
  markSold: (id: string) => http.post<AnnounceResponse>(`${BASE}/${encodeURIComponent(id)}/sold`).then(r => r.data),
};
