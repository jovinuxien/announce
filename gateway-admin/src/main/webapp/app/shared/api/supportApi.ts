import { adminApi } from './adminApi';
import { queryString } from './baseQuery';
import type { PageResponse } from 'app/shared/types';

/**
 * Phase 2 (features.chat). Contract from doc/order-context.md §9 (messaging module: Conversation aggregate with
 * OpenSupportConversation -> WAITING until staff JoinConversation; bulletins are plain announce_bulletin rows pushed
 * to /ws/staff?clientType=) and doc/frontend-edges.md §4 (/api/admin/conversations/**, /api/admin/bulletins/**
 * -> lb://messaging). Served by MSW until the messaging module is routed through the edge.
 */
export type ConversationType = 'LISTING' | 'SUPPORT';
export type ConversationStatus = 'WAITING' | 'OPEN' | 'CLOSED' | 'BLOCKED';
export const CONVERSATION_STATUSES: ConversationStatus[] = ['WAITING', 'OPEN', 'CLOSED', 'BLOCKED'];

export interface ConversationSummary {
  id: string;
  type: ConversationType;
  status: ConversationStatus;
  announceId: string | null;
  openedBy: number;
  openedAt: string;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  lastMessageSeq: number;
  participantIds: number[];
  staffId: number | null;
}
export type BulletinClientType = 'ALL' | 'WEB' | 'MOBILE';
export const BULLETIN_CLIENT_TYPES: BulletinClientType[] = ['ALL', 'WEB', 'MOBILE'];

export interface Bulletin {
  id: number;
  title: string;
  content: string;
  clientType: BulletinClientType;
  postedBy: number;
  postedAt: string;
}
export interface BulletinRequest {
  title: string;
  content: string;
  clientType: BulletinClientType;
}

export const supportApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listConversations: build.query<PageResponse<ConversationSummary>, { status?: ConversationStatus | ''; page?: number; size?: number }>({
      query: (params) => `/api/admin/conversations${queryString(params)}`,
      providesTags: [{ type: 'Conversation', id: 'LIST' }],
    }),
    joinConversation: build.mutation<ConversationSummary, string>({
      query: (id) => ({ url: `/api/admin/conversations/${encodeURIComponent(id)}/join`, method: 'POST' }),
      invalidatesTags: [{ type: 'Conversation', id: 'LIST' }],
    }),
    listBulletins: build.query<Bulletin[], void>({
      query: () => '/api/admin/bulletins',
      providesTags: ['Bulletin'],
    }),
    postBulletin: build.mutation<Bulletin, BulletinRequest>({
      query: (body) => ({ url: '/api/admin/bulletins', method: 'POST', body }),
      invalidatesTags: ['Bulletin'],
    }),
  }),
});

export const { useListConversationsQuery, useJoinConversationMutation, useListBulletinsQuery, usePostBulletinMutation } = supportApi;
