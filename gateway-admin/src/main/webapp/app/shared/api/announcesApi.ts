import { adminApi } from './adminApi';
import { commandId, queryString } from './baseQuery';
import type { AnnounceStatus, Condition, ContractType, Kind, PageResponse, SaleType } from 'app/shared/types';

/** goods.interfaces.rest.announce.AnnounceDtos */
export interface LocationDto {
  regionId: number | null;
  city: string | null;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
}
export interface AttributeDto {
  name: string;
  value: string;
}
export interface SpecificationDto {
  name: string;
  value: string;
  pictureUrl: string | null;
}
export interface VariantDto {
  specifications: string[];
  price: number | null;
  stock: number | null;
  pictureUrl: string | null;
}
export interface GoodsDto {
  price: number | null;
  condition: Condition | null;
  saleType: SaleType | null;
  brandId: number | null;
  unit: string | null;
  variants: VariantDto[];
  attributes: AttributeDto[];
  specifications: SpecificationDto[];
}
export interface JobDto {
  employer: string;
  contractType: ContractType;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string | null;
  applicationDeadline: string | null;
  howToApply: string | null;
  saleType: SaleType | null;
}
export interface EventDto {
  startsAt: string;
  endsAt: string;
  venue: string | null;
  organizer: string | null;
  ticketed: boolean;
  ticketPrice: number | null;
  capacity: number | null;
  saleType: SaleType | null;
}
export interface AnnounceResponse {
  id: string;
  kind: Kind;
  sellerId: number;
  status: AnnounceStatus;
  title: string;
  brief: string | null;
  description: string | null;
  keywords: string[];
  gallery: string[];
  categoryId: number;
  location: LocationDto | null;
  goods: GoodsDto | null;
  job: JobDto | null;
  event: EventDto | null;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  expiresAt: string | null;
}
export interface AnnounceSummaryResponse {
  id: string;
  kind: Kind;
  sellerId: number;
  title: string;
  brief: string | null;
  pictureUrl: string | null;
  status: AnnounceStatus;
  categoryId: number;
  location: LocationDto | null;
  createdAt: string;
  publishedAt: string | null;
  expiresAt: string | null;
  price: number | null;
  saleType: SaleType | null;
  condition: Condition | null;
  employer: string | null;
  contractType: ContractType | null;
  applicationDeadline: string | null;
  startsAt: string | null;
  endsAt: string | null;
  ticketed: boolean | null;
  ticketPrice: number | null;
  moderatedBy: number | null;
  moderationReason: string | null;
}

/** goods.interfaces.rest.admin.AnnounceAdminController.list query parameters */
export interface AnnounceListParams {
  kind?: Kind | '';
  status?: AnnounceStatus | '';
  seller?: number | '';
  region?: number | '';
  category?: number[];
  saleType?: SaleType | '';
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/** AnnounceAdminController.ModerationRequest */
export interface ModerationRequest {
  reason: string;
}

export const announcesApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listAnnounces: build.query<PageResponse<AnnounceSummaryResponse>, AnnounceListParams>({
      query: (params) => `/api/admin/announces${queryString(params)}`,
      providesTags: (result) =>
        result
          ? [{ type: 'Announce' as const, id: 'LIST' }, ...result.items.map((a) => ({ type: 'Announce' as const, id: a.id }))]
          : [{ type: 'Announce' as const, id: 'LIST' }],
    }),
    getAnnounce: build.query<AnnounceResponse, string>({
      query: (id) => `/api/admin/announces/${encodeURIComponent(id)}`,
      providesTags: (_r, _e, id) => [{ type: 'Announce', id }],
    }),
    withdrawAnnounce: build.mutation<AnnounceResponse, { id: string; body: ModerationRequest }>({
      query: ({ id, body }) => ({
        url: `/api/admin/announces/${encodeURIComponent(id)}/withdraw`,
        method: 'POST',
        body,
        headers: { 'X-Command-Id': commandId() },
      }),
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Announce', id }, { type: 'Announce', id: 'LIST' }, 'Dashboard'],
    }),
    expireAnnounce: build.mutation<AnnounceResponse, { id: string; body: ModerationRequest }>({
      query: ({ id, body }) => ({
        url: `/api/admin/announces/${encodeURIComponent(id)}/expire`,
        method: 'POST',
        body,
        headers: { 'X-Command-Id': commandId() },
      }),
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Announce', id }, { type: 'Announce', id: 'LIST' }, 'Dashboard'],
    }),
  }),
});

export const { useListAnnouncesQuery, useGetAnnounceQuery, useWithdrawAnnounceMutation, useExpireAnnounceMutation } = announcesApi;
