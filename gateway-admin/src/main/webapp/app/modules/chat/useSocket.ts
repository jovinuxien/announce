import { useEffect, useRef, useState } from 'react';
import { useSelector } from 'react-redux';
import { selectAccessToken } from 'app/shared/auth/authSlice';
import { socketUrl, type ClientFrame, type ServerFrame } from 'app/shared/api/supportApi';

export type SocketState = 'connecting' | 'open' | 'closed' | 'unavailable';

/**
 * One WebSocket on a messaging path through the admin edge: `/ws/conversations/{id}` for a conversation,
 * `/ws/staff` for the bulletin channel. The staff access token rides as `?access_token=` because browsers cannot
 * set headers on a handshake (the edge's WebSocketTokenFilter turns it into the bearer header). Frames the server
 * pushes are handed to `onFrame`; `send` posts a client frame and answers false when the socket is not open, so
 * the caller can fall back to REST. Reconnects with a small backoff and gives up as `unavailable` after repeated
 * failures; the screens then poll. Ported from the customer SPA's useConversationSocket.
 */
export function useSocket(path: string | undefined, params: Record<string, string>, onFrame: (f: ServerFrame) => void) {
  const token = useSelector(selectAccessToken);
  const [state, setState] = useState<SocketState>('closed');
  const socket = useRef<WebSocket | null>(null);
  const handler = useRef(onFrame);
  handler.current = onFrame;
  const paramsKey = JSON.stringify(params);

  useEffect(() => {
    if (!path || !token || typeof WebSocket === 'undefined') {
      setState('unavailable');
      return;
    }
    let attempts = 0;
    let closedByUs = false;
    let timer: number | undefined;

    const connect = () => {
      setState('connecting');
      const ws = new WebSocket(socketUrl(path, token, JSON.parse(paramsKey) as Record<string, string>));
      socket.current = ws;
      ws.onopen = () => {
        attempts = 0;
        setState('open');
      };
      ws.onmessage = (ev) => {
        try {
          handler.current(JSON.parse(String(ev.data)) as ServerFrame);
        } catch {
          /* ignore a frame that is not JSON */
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
  }, [path, token, paramsKey]);

  const send = (frame: ClientFrame): boolean => {
    const ws = socket.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) return false;
    ws.send(JSON.stringify(frame));
    return true;
  };

  return { state, send };
}
