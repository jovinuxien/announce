import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/**
 * Phase 2 (features.trade). Contract from doc/order-context.md §4 (`auction` aggregate: AuctionOpened, BidPlaced,
 * AuctionClosedWon/Unsold, AuctionCancelled, BidCancelled by staff), §5 (announce_auction, announce_bid) and
 * §8 (POST /api/admin/bids/{id}/cancel). Served by MSW until the order module is routed through the edge.
 */
export type AuctionStatus = 'OPEN' | 'CLOSED_WON' | 'CLOSED_UNSOLD' | 'SNAGGED' | 'CANCELLED';
export const AUCTION_STATUSES: AuctionStatus[] = ['OPEN', 'CLOSED_WON', 'CLOSED_UNSOLD', 'SNAGGED', 'CANCELLED'];

export interface AuctionSummary {
  announceId: string;
  announceTitle: string | null;
  sellerId: number;
  initialPrice: number;
  reservePrice: number | null;
  snagItPrice: number | null;
  increment: number;
  currentPrice: number;
  currency: string;
  bidCount: number;
  startTime: string;
  endTime: string;
  autoExtend: boolean;
  status: AuctionStatus;
  winnerId: number | null;
  winningBidId: string | null;
  orderId: string | null;
}
export type BidStatus = 'ACTIVE' | 'OUTBID' | 'WON' | 'RETRACTED' | 'CANCELLED';
export interface Bid {
  id: string;
  announceId: string;
  bidderId: number;
  amount: number;
  maxAmount: number | null;
  placedAt: string;
  status: BidStatus;
  cancelReason: string | null;
}
export interface AuctionDetail extends AuctionSummary {
  bids: Bid[];
}
export interface CancelBidRequest {
  reason: string;
}

export const auctionsApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listAuctions: build.query<PageResponse<AuctionSummary>, { status?: AuctionStatus | ''; page?: number; size?: number }>({
      query: (params) => `/api/admin/auctions${queryString(params)}`,
      providesTags: [{ type: 'Auction', id: 'LIST' }],
    }),
    getAuction: build.query<AuctionDetail, string>({
      query: (announceId) => `/api/admin/auctions/${encodeURIComponent(announceId)}`,
      providesTags: (_r, _e, id) => [{ type: 'Auction', id }],
    }),
    cancelBid: build.mutation<Bid, { bidId: string; announceId: string; body: CancelBidRequest }>({
      query: ({ bidId, body }) => ({ url: `/api/admin/bids/${encodeURIComponent(bidId)}/cancel`, method: 'POST', body }),
      invalidatesTags: (_r, _e, { announceId }) => [{ type: 'Auction', id: announceId }, { type: 'Auction', id: 'LIST' }],
    }),
  }),
});

export const { useListAuctionsQuery, useGetAuctionQuery, useCancelBidMutation } = auctionsApi;
