import { adminApi } from './adminApi';
import { queryString } from './baseQuery';

/**
 * The staff side of the messaging module through the admin edge (doc/frontend-edges.md §4, §11, §14):
 *  - the support queue and the staff actions: `/api/admin/conversations` (one status per call, WAITING by default,
 *    oldest first), `POST {id}/join | close | block`, `/api/admin/bulletins` (GET by audience, POST);
 *  - what a staff member does inside a conversation goes through the member routes, which accept staff:
 *    `GET /api/conversations/{id}`, `GET {id}/messages?after=`, `POST {id}/messages`, `POST {id}/read`;
 *  - the live channels `/ws/conversations/{id}` and `/ws/staff?clientType=` (app/modules/chat/useSocket.ts).
 * Types mirror `ConversationQueries.ConversationView / MessageView`, `Bulletins.BulletinView` and `MessagingDtos`
 * field for field. The write endpoints answer the aggregate state (`ConversationResponse`) or a `PostResult`, not
 * the read-model row, so the screens refetch through the tags instead of using the answer.
 */
export type ConversationType = 'LISTING' | 'SUPPORT';
export type ConversationStatus = 'WAITING' | 'OPEN' | 'CLOSED' | 'BLOCKED';
/** The queue lists one status at a time; WAITING is the queue proper. */
export const CONVERSATION_STATUSES: ConversationStatus[] = ['WAITING', 'OPEN', 'CLOSED', 'BLOCKED'];
export type ParticipantType = 'USER' | 'ADMIN';
export type ParticipantRole = 'BUYER' | 'SELLER' | 'MEMBER' | 'CSR';
export type SenderType = 'USER' | 'ADMIN' | 'SYSTEM';
export type ContentType = 'TEXT' | 'IMAGE' | 'OFFER' | 'SYSTEM';

export interface ParticipantView {
  type: ParticipantType;
  id: number;
  role: ParticipantRole;
  lastReadSeq: number;
  joinedAt: string | null;
}

export interface OfferView {
  messageId: string | null;
  amount: number | string | null;
  currency: string | null;
  at: string | null;
}

/** ConversationQueries.ConversationView: the read-model row with its participants. */
export interface ConversationView {
  id: string;
  type: ConversationType;
  announceId: string | null;
  buyerId: number | null;
  sellerId: number | null;
  subject: string | null;
  status: ConversationStatus;
  lastMessageId: string | null;
  lastMessageSeq: number;
  lastMessageAt: string | null;
  messageCount: number;
  /** Unread count of the viewer; null when the viewer is not a participant (staff looking at the queue). */
  unread: number | null;
  openOffer: OfferView | null;
  acceptedOffer: OfferView | null;
  participants: ParticipantView[];
  createdAt: string;
  updatedAt: string | null;
  closedAt: string | null;
  blockedReason: string | null;
}

/** ConversationQueries.MessageView: one announce_chat_message row. */
export interface MessageView {
  id: string;
  conversationId: string;
  seq: number;
  senderType: SenderType;
  senderId: number | null;
  contentType: ContentType;
  content: string | null;
  attachmentUrl: string | null;
  offerAmount: number | string | null;
  offerCurrency: string | null;
  offerStatus: string | null;
  clientMsgId: string | null;
  sentAt: string;
}

/** MessagingDtos.PageResponse: no total; a full page means there may be more. */
export interface ChatPage<T> {
  items: T[];
  page: number;
  size: number;
}

/** MessagingDtos.PostMessageRequest, the same shape as the `post` frame. */
export interface PostMessageRequest {
  clientMsgId: string;
  contentType: ContentType;
  content?: string;
  attachmentUrl?: string;
}

/** The answer of a post (domain PostResult); `duplicate` when the clientMsgId was seen before. */
export interface PostResult {
  conversationId: string;
  messageId: string;
  seq: number;
  clientMsgId: string | null;
  duplicate: boolean;
  at: string;
}

export type BulletinAudience = 'STAFF' | 'MEMBERS';
export const BULLETIN_AUDIENCES: BulletinAudience[] = ['STAFF', 'MEMBERS'];
/** The backend takes any string; empty means every client type. The admin SPA connects as `web`. */
export const BULLETIN_CLIENT_TYPES = ['', 'web', 'mobile'] as const;
export type BulletinClientType = (typeof BULLETIN_CLIENT_TYPES)[number];
export const STAFF_CLIENT_TYPE = 'web';

/** Bulletins.BulletinView: one announce_bulletin row. `clientType` null means every client type. */
export interface BulletinView {
  id: number;
  adminId: number;
  audience: BulletinAudience;
  clientType: string | null;
  content: string;
  expiresAt: string | null;
  createdAt: string;
}

/** MessagingDtos.BulletinRequest; `content` is required, at most 1023 characters. */
export interface BulletinRequest {
  audience: BulletinAudience;
  clientType: string | null;
  content: string;
  expiresAt: string | null;
}

export interface BlockRequest {
  reason: string;
}

export interface ConversationListFilter {
  status: ConversationStatus;
  page?: number;
  size?: number;
}

/** Frames the server pushes on `/ws/conversations/{id}` and `/ws/staff` (messaging's LiveFrames). */
/** messaging's MessageLive: the message as pushed, amounts in cents. */
export interface MessageFrame {
  type: 'message';
  conversationId: string;
  messageId: string;
  seq: number;
  senderType: SenderType;
  senderId: number | null;
  contentType: ContentType;
  content: string | null;
  attachmentUrl: string | null;
  offerAmountCents: number | null;
  currency: string | null;
  clientMsgId: string | null;
  sentAt: string;
}

export type ServerFrame =
  | MessageFrame
  | { type: 'status'; conversationId: string; change: 'JOINED' | 'OFFER_ACCEPTED' | 'OFFER_DECLINED' | 'CLOSED' | 'BLOCKED'; status: ConversationStatus; participantType: ParticipantType | null; participantId: number | null; at: string }
  | ({ type: 'bulletin' } & Omit<BulletinView, 'createdAt'> & { at: string })
  | ({ type: 'ack'; action: string } & Partial<PostResult>)
  | { type: 'error'; action: string; clientMsgId?: string; status: number; code: string; detail: string }
  | { type: 'pong' };

/** Frames a client sends on `/ws/conversations/{id}`; staff use `post`, `read`, `close` and `ping`. */
export interface ClientFrame {
  type: 'post' | 'read' | 'close' | 'ping';
  clientMsgId?: string;
  contentType?: ContentType;
  content?: string;
  seq?: number;
}

/** A `message` frame as the MessageView the screens render. */
export function messageOfFrame(f: MessageFrame): MessageView {
  return {
    id: f.messageId,
    conversationId: f.conversationId,
    seq: f.seq,
    senderType: f.senderType,
    senderId: f.senderId,
    contentType: f.contentType,
    content: f.content,
    attachmentUrl: f.attachmentUrl,
    offerAmount: f.offerAmountCents == null ? null : f.offerAmountCents / 100,
    offerCurrency: f.currency,
    offerStatus: null,
    clientMsgId: f.clientMsgId,
    sentAt: f.sentAt,
  };
}

/** A `bulletin` frame as the BulletinView the list shows. */
export function bulletinOfFrame(f: Extract<ServerFrame, { type: 'bulletin' }>): BulletinView {
  return { id: f.id, adminId: f.adminId, audience: f.audience, clientType: f.clientType, content: f.content, expiresAt: f.expiresAt, createdAt: f.at };
}

/** The WebSocket URL of a backend path on this edge; the token rides as a query parameter (WebSocketTokenFilter). */
export function socketUrl(path: string, token: string, params: Record<string, string> = {}): string {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const sp = new URLSearchParams(params);
  sp.set('access_token', token);
  return `${proto}://${window.location.host}${path}?${sp.toString()}`;
}

const id = (value: string) => encodeURIComponent(value);

export const supportApi = adminApi.injectEndpoints({
  endpoints: (build) => ({
    listConversations: build.query<ChatPage<ConversationView>, ConversationListFilter>({
      query: (params) => `/api/admin/conversations${queryString(params)}`,
      providesTags: (result) => [
        { type: 'Conversation', id: 'LIST' },
        ...(result?.items ?? []).map((c) => ({ type: 'Conversation' as const, id: c.id })),
      ],
    }),
    getConversation: build.query<ConversationView, string>({
      query: (conversationId) => `/api/conversations/${id(conversationId)}`,
      providesTags: (_r, _e, conversationId) => [{ type: 'Conversation', id: conversationId }],
    }),
    getMessages: build.query<MessageView[], { id: string; after?: number; limit?: number }>({
      query: ({ id: conversationId, after, limit }) => `/api/conversations/${id(conversationId)}/messages${queryString({ after, limit })}`,
    }),
    postMessage: build.mutation<PostResult, { id: string; body: PostMessageRequest }>({
      query: ({ id: conversationId, body }) => ({ url: `/api/conversations/${id(conversationId)}/messages`, method: 'POST', body }),
    }),
    markRead: build.mutation<void, { id: string; seq: number }>({
      query: ({ id: conversationId, seq }) => ({ url: `/api/conversations/${id(conversationId)}/read`, method: 'POST', body: { seq } }),
    }),
    joinConversation: build.mutation<unknown, string>({
      query: (conversationId) => ({ url: `/api/admin/conversations/${id(conversationId)}/join`, method: 'POST' }),
      invalidatesTags: (_r, _e, conversationId) => [{ type: 'Conversation', id: conversationId }, { type: 'Conversation', id: 'LIST' }],
    }),
    closeConversation: build.mutation<unknown, string>({
      query: (conversationId) => ({ url: `/api/admin/conversations/${id(conversationId)}/close`, method: 'POST' }),
      invalidatesTags: (_r, _e, conversationId) => [{ type: 'Conversation', id: conversationId }, { type: 'Conversation', id: 'LIST' }],
    }),
    blockConversation: build.mutation<unknown, { id: string; body: BlockRequest }>({
      query: ({ id: conversationId, body }) => ({ url: `/api/admin/conversations/${id(conversationId)}/block`, method: 'POST', body }),
      invalidatesTags: (_r, _e, { id: conversationId }) => [{ type: 'Conversation', id: conversationId }, { type: 'Conversation', id: 'LIST' }],
    }),
    listBulletins: build.query<BulletinView[], { audience: BulletinAudience }>({
      query: (params) => `/api/admin/bulletins${queryString(params)}`,
      providesTags: ['Bulletin'],
    }),
    postBulletin: build.mutation<BulletinView, BulletinRequest>({
      query: (body) => ({ url: '/api/admin/bulletins', method: 'POST', body }),
      invalidatesTags: ['Bulletin'],
    }),
  }),
});

export const {
  useListConversationsQuery,
  useGetConversationQuery,
  useLazyGetMessagesQuery,
  usePostMessageMutation,
  useMarkReadMutation,
  useJoinConversationMutation,
  useCloseConversationMutation,
  useBlockConversationMutation,
  useListBulletinsQuery,
  usePostBulletinMutation,
} = supportApi;

/** Staff participants of a conversation (the CSRs who joined). */
export function staffOf(c: ConversationView): ParticipantView[] {
  return c.participants.filter((p) => p.type === 'ADMIN');
}

/** The member who opened a support conversation, or the buyer of a listing conversation. */
export function memberOf(c: ConversationView): number | null {
  return c.buyerId ?? c.participants.find((p) => p.type === 'USER')?.id ?? null;
}
