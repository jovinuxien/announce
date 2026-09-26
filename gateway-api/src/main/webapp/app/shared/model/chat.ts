// Mirrors the messaging module's MessagingDtos, ConversationQueries views and the WebSocket frames.
export type ConversationType = 'LISTING' | 'SUPPORT';
export type ConversationStatus = 'OPEN' | 'WAITING' | 'CLOSED' | 'BLOCKED';
export type ContentType = 'TEXT' | 'IMAGE' | 'OFFER' | 'SYSTEM';
export interface ParticipantView { type: string; id: number; lastReadSeq?: number; joinedAt?: string }
export interface OfferView { messageId?: string; amountCents?: number; amount?: number; currency: string; status?: string; buyerId?: number }
export interface ConversationView {
  id: string;
  type: ConversationType;
  announceId?: string | null;
  buyerId?: number | null;
  sellerId?: number | null;
  subject?: string | null;
  status: ConversationStatus;
  lastMessageId?: string | null;
  lastMessageSeq: number;
  lastMessageAt?: string | null;
  messageCount: number;
  unread?: number | null;
  openOffer?: OfferView | null;
  acceptedOffer?: OfferView | null;
  participants: ParticipantView[];
  createdAt?: string;
  updatedAt?: string;
  closedAt?: string | null;
  blockedReason?: string | null;
}
export interface MessageView {
  id: string;
  conversationId: string;
  seq: number;
  senderType: string;
  senderId?: number | null;
  contentType: ContentType;
  content?: string | null;
  attachmentUrl?: string | null;
  offerAmount?: number | null;
  offerCurrency?: string | null;
  offerStatus?: string | null;
  clientMsgId?: string | null;
  sentAt: string;
}
export interface OpenConversationRequest { type?: ConversationType; announceId?: string; subject?: string }
export interface PostMessageRequest {
  clientMsgId: string;
  contentType: ContentType;
  content?: string;
  attachmentUrl?: string;
  amountCents?: number;
  currency?: string;
}
export interface PostResult { conversationId: string; messageId: string; seq: number; clientMsgId: string; duplicate: boolean; at: string }
export interface ChatPage<T> { items: T[]; page: number; size: number }

/** Frames on /ws/conversations/{id}: the client sends `type` plus the fields that matter; the server pushes. */
export interface ClientFrame {
  type: 'post' | 'offer' | 'accept-offer' | 'decline-offer' | 'read' | 'close' | 'ping';
  clientMsgId?: string;
  contentType?: ContentType;
  content?: string;
  attachmentUrl?: string;
  amountCents?: number;
  currency?: string;
  seq?: number;
}
export type ServerFrame =
  | ({ type: 'message' } & MessageView)
  | { type: 'status'; conversationId: string; status: ConversationStatus; [k: string]: unknown }
  | ({ type: 'ack'; action: string } & Partial<PostResult>)
  | { type: 'error'; action: string; clientMsgId?: string; status: number; code: string; detail: string }
  | { type: 'pong' };
