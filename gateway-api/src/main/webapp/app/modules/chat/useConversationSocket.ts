import { useEffect, useRef, useState } from 'react';
import { tokenStore } from 'app/config/tokenStore';
import { chatApi } from 'app/shared/api/chatApi';
import { ClientFrame, ServerFrame } from 'app/shared/model/chat';

export type SocketState = 'connecting' | 'open' | 'closed' | 'unavailable';

/**
 * One WebSocket per open thread (/ws/conversations/{id}?access_token=...). Frames the server pushes are handed
 * to `onFrame`; `send` posts a client frame. Reconnects with a small backoff; gives up as `unavailable` after
 * repeated failures (the thread then falls back to REST polling).
 */
export function useConversationSocket(conversationId: string | undefined, onFrame: (f: ServerFrame) => void) {
  const [state, setState] = useState<SocketState>('closed');
  const socket = useRef<WebSocket | null>(null);
  const handler = useRef(onFrame);
  handler.current = onFrame;

  useEffect(() => {
    if (!conversationId || typeof WebSocket === 'undefined') return;
    let attempts = 0;
    let closedByUs = false;
    let timer: number | undefined;

    const connect = () => {
      const token = tokenStore.get();
      if (!token) {
        setState('unavailable');
        return;
      }
      setState('connecting');
      const ws = new WebSocket(chatApi.socketUrl(conversationId, token));
      socket.current = ws;
      ws.onopen = () => {
        attempts = 0;
        setState('open');
      };
      ws.onmessage = ev => {
        try {
          handler.current(JSON.parse(ev.data) as ServerFrame);
        } catch {
          // ignore unparseable frames
        }
      };
      ws.onclose = () => {
        socket.current = null;
        if (closedByUs) return;
        attempts += 1;
        if (attempts > 3) {
          setState('unavailable');
          return;
        }
        setState('closed');
        timer = window.setTimeout(connect, 1000 * attempts);
      };
      ws.onerror = () => ws.close();
    };
    connect();

    return () => {
      closedByUs = true;
      window.clearTimeout(timer);
      socket.current?.close();
      socket.current = null;
    };
  }, [conversationId]);

  const send = (frame: ClientFrame): boolean => {
    const ws = socket.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) return false;
    ws.send(JSON.stringify(frame));
    return true;
  };

  return { state, send };
}
