import { http } from 'app/config/http';
import {
  ChatPage,
  ConversationView,
  MessageView,
  OpenConversationRequest,
  PostMessageRequest,
  PostResult,
} from 'app/shared/model/chat';

const BASE = '/api/conversations';

export const chatApi = {
  inbox: (page = 1, size = 50) => http.get<ChatPage<ConversationView>>(`${BASE}?page=${page}&size=${size}`).then(r => r.data),
  open: (body: OpenConversationRequest) => http.post<ConversationView>(BASE, body).then(r => r.data),
  view: (id: string) => http.get<ConversationView>(`${BASE}/${encodeURIComponent(id)}`).then(r => r.data),
  messages: (id: string, after = 0, limit = 50) =>
    http.get<MessageView[]>(`${BASE}/${encodeURIComponent(id)}/messages?after=${after}&limit=${limit}`).then(r => r.data),
  post: (id: string, body: PostMessageRequest) => http.post<PostResult>(`${BASE}/${encodeURIComponent(id)}/messages`, body).then(r => r.data),
  acceptOffer: (id: string) => http.post<ConversationView>(`${BASE}/${encodeURIComponent(id)}/offers/accept`).then(r => r.data),
  declineOffer: (id: string) => http.post<ConversationView>(`${BASE}/${encodeURIComponent(id)}/offers/decline`).then(r => r.data),
  markRead: (id: string, seq: number) => http.post<void>(`${BASE}/${encodeURIComponent(id)}/read`, { seq }).then(() => undefined),
  close: (id: string) => http.post<ConversationView>(`${BASE}/${encodeURIComponent(id)}/close`).then(r => r.data),
  /** The WebSocket URL of a conversation; the token rides as a query parameter because browsers cannot set headers. */
  socketUrl: (id: string, token: string) => {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${proto}://${window.location.host}/ws/conversations/${encodeURIComponent(id)}?access_token=${encodeURIComponent(token)}`;
  },
};
