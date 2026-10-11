// The messaging module's shapes as built (doc/frontend-edges.md §14, §15): the read-model views of
// ConversationQueries, the request records of MessagingDtos, the aggregate answer of the commands and the
// frames of /ws/conversations/{id}. Field names are messaging's; nothing is renamed on the way in.
export type ConversationType = 'LISTING' | 'SUPPORT';
export type ConversationStatus = 'WAITING' | 'OPEN' | 'CLOSED' | 'BLOCKED';
export type ParticipantType = 'USER' | 'ADMIN';
export type ParticipantRole = 'BUYER' | 'SELLER' | 'MEMBER' | 'CSR';
export type SenderType = 'USER' | 'ADMIN' | 'SYSTEM';
export type ContentType = 'TEXT' | 'IMAGE' | 'OFFER' | 'SYSTEM';
export type OfferStatus = 'OPEN' | 'ACCEPTED' | 'DECLINED';

/** ConversationQueries.ParticipantView. */
export interface ParticipantView {
  type: ParticipantType;
  id: number;
  role: ParticipantRole;
  lastReadSeq: number;
  joinedAt: string | null;
}
/** ConversationQueries.OfferView: the amount is a decimal, not cents. */
export interface OfferView {
  messageId: string;
  amount: number;
  currency: string;
  at: string | null;
}

/** ConversationQueries.ConversationView, one row of the inbox. `unread` is null when the viewer is not a participant. */
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
  unread: number | null;
  openOffer: OfferView | null;
  acceptedOffer: OfferView | null;
  participants: ParticipantView[];
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
  blockedReason: string | null;
}

/** ConversationQueries.MessageView. */
export interface MessageView {
  id: string;
  conversationId: string;
  seq: number;
  senderType: SenderType;
  senderId: number | null;
  contentType: ContentType;
  content: string | null;
  attachmentUrl: string | null;
  offerAmount: number | null;
  offerCurrency: string | null;
  offerStatus: OfferStatus | null;
  clientMsgId: string | null;
  sentAt: string;
}

/** MessagingDtos.PageResponse: 1-based page, no total (the inbox is paged by scrolling, not by a count). */
export interface ChatPage<T> {
  items: T[];
  page: number;
  size: number;
}

/** MessagingDtos.OpenConversationRequest: LISTING needs the announce, SUPPORT takes a subject. */
export interface OpenConversationRequest {
  type: ConversationType;
  announceId?: string;
  subject?: string;
}
/** MessagingDtos.PostMessageRequest: an OFFER carries amountCents and currency, the others content or attachmentUrl. */
export interface PostMessageRequest {
  clientMsgId: string;
  contentType: ContentType;
  content?: string;
  attachmentUrl?: string;
  amountCents?: number;
  currency?: string;
}
export interface PostResult {
  conversationId: string;
  messageId: string;
  seq: number;
  clientMsgId: string;
  duplicate: boolean;
  at: string;
}

/** The aggregate's offer (cents) inside a ConversationResponse. */
export interface OfferAnswer {
  messageId: string;
  amountCents: number;
  currency: string;
}
/**
 * MessagingDtos.ConversationResponse: what open, accept, decline and close answer. It is the aggregate, not the
 * read-model row (no counters, no unread, offers in cents), so the screens use it for the id and refetch the row.
 */
export interface ConversationResponse {
  id: string;
  type: ConversationType;
  announceId: string | null;
  buyerId: number | null;
  sellerId: number | null;
  subject: string | null;
  status: ConversationStatus;
  lastSeq: number;
  participants: Array<{
    type: ParticipantType;
    id: number;
    role: ParticipantRole;
  }>;
  openOffer: OfferAnswer | null;
  acceptedOffer: OfferAnswer | null;
  createdAt: string;
  updatedAt: string;
}

/** The frames a client sends on /ws/conversations/{id} (ConversationWebSocketHandler.ClientFrame). */
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

/** A `message` frame: MessageLive, with `messageId`, the offer in cents and `currency` (not the MessageView names). */
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
export type StatusChange = 'JOINED' | 'OFFER_ACCEPTED' | 'OFFER_DECLINED' | 'CLOSED' | 'BLOCKED';
/** A `status` frame: ConversationStatusLive. */
export interface StatusFrame {
  type: 'status';
  conversationId: string;
  change: StatusChange;
  status: ConversationStatus;
  messageId: string | null;
  amountCents: number | null;
  currency: string | null;
  participantType: ParticipantType | null;
  participantId: number | null;
  at: string;
}
export type ServerFrame =
  | MessageFrame
  | StatusFrame
  | ({ type: 'ack'; action: string } & Partial<PostResult>)
  | {
      type: 'error';
      action: string;
      clientMsgId?: string;
      status: number;
      code: string;
      detail: string;
    }
  | { type: 'pong' };

/** Turns a live frame into the MessageView the screens render; the offer status is OPEN until a status frame says otherwise. */
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
    offerStatus: f.contentType === 'OFFER' ? 'OPEN' : null,
    clientMsgId: f.clientMsgId,
    sentAt: f.sentAt,
  };
}

/** A member may write while the conversation is WAITING (support, before staff joins) or OPEN. */
export const isWritable = (status: ConversationStatus | undefined): boolean => status === 'OPEN' || status === 'WAITING';
/** The message was sent by the signed-in member. */
export const isMine = (m: Pick<MessageView, 'senderType' | 'senderId'>, me: number | undefined): boolean => m.senderType === 'USER' && me != null && m.senderId === me;
/** The other member of a listing conversation, seen from `me`; null for a support conversation. */
export function otherMember(c: Pick<ConversationView, 'type' | 'buyerId' | 'sellerId'>, me: number | undefined): number | null {
  if (c.type !== 'LISTING') return null;
  return me === c.buyerId ? c.sellerId : c.buyerId;
}
/** The staff members who joined. */
export const staffOf = (c: Pick<ConversationView, 'participants'>): ParticipantView[] => c.participants.filter(p => p.type === 'ADMIN');
