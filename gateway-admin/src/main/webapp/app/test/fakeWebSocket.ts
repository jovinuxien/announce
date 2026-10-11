/**
 * A WebSocket double for the page tests: records the URL and the frames the page sends, opens on the next tick
 * (unless `autoOpen` is off, which leaves the page on its REST fallback), and lets a test push server frames.
 * msw does not intercept WebSockets in this setup, and the live frames are not mocked in the browser either.
 */
export class FakeWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;
  static instances: FakeWebSocket[] = [];
  static autoOpen = true;

  readonly url: string;
  readyState = FakeWebSocket.CONNECTING;
  sent: string[] = [];
  onopen: ((ev: unknown) => void) | null = null;
  onmessage: ((ev: { data: string }) => void) | null = null;
  onclose: ((ev: unknown) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.instances.push(this);
    if (FakeWebSocket.autoOpen) setTimeout(() => this.open(), 0);
  }

  open() {
    if (this.readyState !== FakeWebSocket.CONNECTING) return;
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.({});
  }

  /** A frame from the server. */
  push(frame: object) {
    this.onmessage?.({ data: JSON.stringify(frame) });
  }

  send(data: string) {
    this.sent.push(data);
  }

  /** The frames the page sent, parsed. */
  sentFrames(): Array<Record<string, unknown>> {
    return this.sent.map((s) => JSON.parse(s) as Record<string, unknown>);
  }

  close() {
    if (this.readyState === FakeWebSocket.CLOSED) return;
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.({});
  }

  static last(): FakeWebSocket {
    const ws = FakeWebSocket.instances[FakeWebSocket.instances.length - 1];
    if (!ws) throw new Error('No WebSocket was opened');
    return ws;
  }

  static reset() {
    FakeWebSocket.instances = [];
    FakeWebSocket.autoOpen = true;
  }
}

/** Installs the double as the global WebSocket for a test file; call the returned function to restore. */
export function installFakeWebSocket(): () => void {
  const original = (globalThis as { WebSocket?: unknown }).WebSocket;
  (globalThis as { WebSocket: unknown }).WebSocket = FakeWebSocket;
  return () => {
    (globalThis as { WebSocket?: unknown }).WebSocket = original;
  };
}

export function useFakeWebSocket() {
  let restore: () => void;
  beforeAll(() => {
    restore = installFakeWebSocket();
  });
  afterEach(() => FakeWebSocket.reset());
  afterAll(() => restore());
}
