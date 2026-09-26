// Hand-written from announce-management's AnnounceDtos, CategoryController and BrandController records.
export type Kind = 'GOODS' | 'JOB' | 'EVENT';
export const KINDS: Kind[] = ['GOODS', 'JOB', 'EVENT'];
export type SaleType = 'FIXED_PRICE' | 'AUCTION' | 'GIVEAWAY';
export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'FOR_PARTS';
export const CONDITIONS: Condition[] = ['NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'FOR_PARTS'];
export type AnnounceStatus = 'DRAFT' | 'PUBLISHED' | 'SOLD' | 'WITHDRAWN' | 'EXPIRED';
export const STATUSES: AnnounceStatus[] = ['DRAFT', 'PUBLISHED', 'SOLD', 'WITHDRAWN', 'EXPIRED'];
export type ContractType = 'FULL_TIME' | 'PART_TIME' | 'TEMPORARY' | 'INTERNSHIP' | 'FREELANCE';
export const CONTRACT_TYPES: ContractType[] = ['FULL_TIME', 'PART_TIME', 'TEMPORARY', 'INTERNSHIP', 'FREELANCE'];

export interface LocationDto {
  regionId?: number | null;
  city?: string | null;
  postalCode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}
export interface AttributeDto { name: string; value: string }
export interface SpecificationDto { name: string; value: string; pictureUrl?: string | null }
export interface VariantDto { specifications: string[]; price: number; stock: number; pictureUrl?: string | null }

export interface GoodsDto {
  price?: number | null;
  condition?: Condition | null;
  saleType?: SaleType | null;
  brandId?: number | null;
  unit?: string | null;
  variants?: VariantDto[] | null;
  attributes?: AttributeDto[] | null;
  specifications?: SpecificationDto[] | null;
}
export interface JobDto {
  employer: string;
  contractType: ContractType;
  salaryMin?: number | null;
  salaryMax?: number | null;
  currency?: string | null;
  applicationDeadline?: string | null; // ISO LocalDateTime, no zone
  howToApply: string;
  saleType?: SaleType | null;
}
export interface EventDto {
  startsAt: string;
  endsAt: string;
  venue?: string | null;
  organizer?: string | null;
  ticketed?: boolean | null;
  ticketPrice?: number | null;
  capacity?: number | null;
  saleType?: SaleType | null;
}

export interface AnnounceRequest {
  kind: Kind;
  title: string;
  brief?: string | null;
  description?: string | null;
  keywords?: string[] | null;
  gallery?: string[] | null;
  categoryId: number | null;
  location?: LocationDto | null;
  goods?: GoodsDto | null;
  job?: JobDto | null;
  event?: EventDto | null;
}

export interface AnnounceResponse {
  id: string;
  kind: Kind;
  sellerId: number;
  status: AnnounceStatus;
  title: string;
  brief?: string | null;
  description?: string | null;
  keywords?: string[] | null;
  gallery?: string[] | null;
  categoryId?: number | null;
  location?: LocationDto | null;
  goods?: GoodsDto | null;
  job?: JobDto | null;
  event?: EventDto | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  publishedAt?: string | null;
  expiresAt?: string | null;
}

export interface AnnounceSummaryResponse {
  id: string;
  kind: Kind;
  sellerId: number;
  title: string;
  brief?: string | null;
  pictureUrl?: string | null;
  status: AnnounceStatus;
  categoryId?: number | null;
  location?: LocationDto | null;
  createdAt?: string | null;
  publishedAt?: string | null;
  expiresAt?: string | null;
  price?: number | null;
  saleType?: SaleType | null;
  condition?: Condition | null;
  employer?: string | null;
  contractType?: ContractType | null;
  applicationDeadline?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  ticketed?: boolean | null;
  ticketPrice?: number | null;
}

export interface PageResponse<T> { items: T[]; page: number; size: number; total: number }

export interface AnnounceListQuery {
  kind?: Kind;
  category?: number[];
  brand?: number;
  seller?: number;
  region?: number;
  q?: string;
  saleType?: SaleType;
  status?: AnnounceStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CategoryResponse {
  id: number;
  kind: Kind;
  name: string;
  parentId?: number | null;
  level: string;
  description?: string | null;
  iconUrl?: string | null;
  pictureUrl?: string | null;
  keywords?: string | null;
  sortOrder: number;
  hidden: boolean;
}
export interface CategoryNodeResponse { category: CategoryResponse; children: CategoryResponse[] }

export interface BrandResponse {
  id: number;
  name: string;
  description?: string | null;
  pictureUrl?: string | null;
  sortOrder: number;
  floorPrice?: number | null;
  hidden: boolean;
}

/** RFC 7807 body every backend and the edge answer with. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: string[];
}
