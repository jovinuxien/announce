import { http } from 'app/config/http';
import { ChatPage, ConversationResponse, ConversationView, MessageView, OpenConversationRequest, PostMessageRequest, PostResult } from 'app/shared/model/chat';

const BASE = '/api/conversations';
const path = (id: string, rest = '') => `${BASE}/${encodeURIComponent(id)}${rest}`;

/**
 * Messaging's member routes through the customer edge (doc/frontend-edges.md §3, §15). The caller comes from the
 * token; the edge turns it into X-User-Id / X-User-Type=MEMBER. The commands answer the aggregate
 * (ConversationResponse); the screens refetch the row afterwards.
 */
export const chatApi = {
  inbox: (page = 1, size = 50) => http.get<ChatPage<ConversationView>>(`${BASE}?page=${page}&size=${size}`).then(r => r.data),
  open: (body: OpenConversationRequest) => http.post<ConversationResponse>(BASE, body).then(r => r.data),
  view: (id: string) => http.get<ConversationView>(path(id)).then(r => r.data),
  messages: (id: string, after = 0, limit = 50) => http.get<MessageView[]>(path(id, `/messages?after=${after}&limit=${limit}`)).then(r => r.data),
  post: (id: string, body: PostMessageRequest) => http.post<PostResult>(path(id, '/messages'), body).then(r => r.data),
  acceptOffer: (id: string) => http.post<ConversationResponse>(path(id, '/offers/accept')).then(r => r.data),
  declineOffer: (id: string) => http.post<ConversationResponse>(path(id, '/offers/decline')).then(r => r.data),
  markRead: (id: string, seq: number) => http.post<void>(path(id, '/read'), { seq }).then(() => undefined),
  close: (id: string) => http.post<ConversationResponse>(path(id, '/close')).then(r => r.data),
  /** The WebSocket URL of a conversation on this edge; the token rides as a query parameter because browsers cannot set headers. */
  socketUrl: (id: string, token: string) => {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${proto}://${window.location.host}/ws/conversations/${encodeURIComponent(id)}?access_token=${encodeURIComponent(token)}`;
  },
};
