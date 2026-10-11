import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/**
 * Admin reads of the auction context (order module, `AuctionController`), through the admin edge
 * (`/api/admin/auctions/**`, `/api/admin/bids/**` -> lb://order). The shapes are the read-model rows the backend
 * returns as they are: `announce_auction` and `announce_bid` (db-reactive), amounts as decimals, timestamps as ISO
 * local date-times. See doc/frontend-edges.md §12.
 */
export type AuctionStatus = 'OPEN' | 'CLOSED_WON' | 'CLOSED_UNSOLD' | 'CLOSED_SNAGGED' | 'CANCELLED';
export const AUCTION_STATUSES: AuctionStatus[] = ['OPEN', 'CLOSED_WON', 'CLOSED_UNSOLD', 'CLOSED_SNAGGED', 'CANCELLED'];

/** `announce_auction` row. */
export interface AuctionRow {
  id: string;
  announceId: string;
  sellerId: number;
  title: string | null;
  mainPicture: string | null;
  currency: string;
  initialPrice: number;
  reservePrice: number | null;
  snagItPrice: number | null;
  bidIncrement: number;
  currentPrice: number;
  bidCount: number;
  startTime: string;
  endTime: string;
  autoExtendSec: number | null;
  status: AuctionStatus;
  winningBidId: string | null;
  winnerId: number | null;
  closedTime: string | null;
  lastEventId?: string | null;
  addTime?: string | null;
  updateTime?: string | null;
}
export type BidStatus = 'ACTIVE' | 'OUTBID' | 'WON' | 'RETRACTED' | 'CANCELLED';
/** `announce_bid` row. */
export interface BidRow {
  id: string;
  auctionId: string;
  announceId: string;
  bidderId: number;
  amount: number;
  maxAmount: number | null;
  bidTime: string;
  status: BidStatus;
  cancelReason: string | null;
  cancelledBy: number | null;
}
/** `GET /api/admin/auctions/{announceId}`: the row plus every bid, cancelled and retracted included. */
export interface AuctionView {
  auction: AuctionRow;
  bids: BidRow[];
}
export interface AuctionFilter {
  status?: AuctionStatus | '';
  seller?: number | '';
  q?: string;
  page?: number;
  size?: number;
}
/** `AuctionController.ReasonRequest`. */
export interface ReasonRequest {
  reason: string;
}

export const auctionsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listAuctions: build.query<PageResponse<AuctionRow>, AuctionFilter>({
      query: (params) => `/api/admin/auctions${queryString(params)}`,
      providesTags: [{ type: 'Auction', id: 'LIST' }],
    }),
    getAuction: build.query<AuctionView, string>({
      query: (announceId) => `/api/admin/auctions/${encodeURIComponent(announceId)}`,
      providesTags: (_r, _e, id) => [{ type: 'Auction', id }],
    }),
    /** Answers the domain record (amounts in cents); the screens refetch the row through the tags instead. */
    cancelBid: build.mutation<unknown, { bidId: string; announceId: string; body: ReasonRequest }>({
      query: ({ bidId, body }) => ({ url: `/api/admin/bids/${encodeURIComponent(bidId)}/cancel`, method: 'POST', body }),
      invalidatesTags: (_r, _e, { announceId }) => [{ type: 'Auction', id: announceId }, { type: 'Auction', id: 'LIST' }],
    }),
  }),
});

export const { useListAuctionsQuery, useGetAuctionQuery, useCancelBidMutation } = auctionsApi;
