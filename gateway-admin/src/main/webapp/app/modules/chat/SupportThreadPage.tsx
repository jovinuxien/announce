import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Badge, Button, Card, Form, InputGroup, Modal, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useSelector } from 'react-redux';
import { Link, useParams } from 'react-router-dom';
import {
  memberOf,
  messageOfFrame,
  staffOf,
  useBlockConversationMutation,
  useCloseConversationMutation,
  useGetConversationQuery,
  useJoinConversationMutation,
  useLazyGetMessagesQuery,
  useMarkReadMutation,
  usePostMessageMutation,
  type MessageView,
  type ServerFrame,
} from 'app/shared/api/supportApi';
import { selectAdmin } from 'app/shared/auth/authSlice';
import { formatDateTime, formatMoney } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import StatusBadge from 'app/shared/ui/StatusBadge';
import MockBanner from 'app/modules/trade/MockBanner';
import { useSocket } from './useSocket';

/** REST catch-up cadence while the socket is not open. */
export const THREAD_POLL_MS = 5_000;

const clientMsgId = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);

/**
 * One support conversation for a staff member: the row and the history from REST, live `message` and `status`
 * frames over `/ws/conversations/{id}` (REST polling when the socket is unavailable), a text composer that posts
 * through the socket with a REST fallback, the read marker, and the staff actions join, close and block. A staff
 * member may read any conversation; replying needs having joined (messaging's not-participant rule).
 */
export default function SupportThreadPage() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation();
  const admin = useSelector(selectAdmin);
  const { data: conversation, error, isLoading, refetch } = useGetConversationQuery(id, { skip: !id });
  const [loadMessages] = useLazyGetMessagesQuery();
  const [postMessage] = usePostMessageMutation();
  const [markRead] = useMarkReadMutation();
  const [join, joinState] = useJoinConversationMutation();
  const [close, closeState] = useCloseConversationMutation();
  const [block, blockState] = useBlockConversationMutation();
  const [messages, setMessages] = useState<MessageView[]>([]);
  const [text, setText] = useState('');
  const [frameError, setFrameError] = useState<string | null>(null);
  const [blocking, setBlocking] = useState(false);
  const [reason, setReason] = useState('');
  const bottom = useRef<HTMLDivElement>(null);
  const lastSeq = useRef(0);

  const append = useCallback((incoming: MessageView[]) => {
    if (incoming.length === 0) return;
    setMessages((prev) => {
      const next = [...prev];
      incoming.forEach((m) => {
        if (!next.some((x) => x.id === m.id || (m.clientMsgId && x.clientMsgId === m.clientMsgId))) next.push(m);
      });
      next.sort((a, b) => a.seq - b.seq);
      lastSeq.current = next.length ? next[next.length - 1].seq : 0;
      return next;
    });
  }, []);

  const catchUp = useCallback(() => {
    if (!id) return;
    loadMessages({ id, after: lastSeq.current })
      .unwrap()
      .then(append)
      .catch(() => undefined);
  }, [id, loadMessages, append]);

  const onFrame = useCallback(
    (f: ServerFrame) => {
      if (f.type === 'message') append([messageOfFrame(f)]);
      else if (f.type === 'status') refetch();
      else if (f.type === 'ack') catchUp();
      else if (f.type === 'error') setFrameError(f.detail);
    },
    [append, refetch, catchUp],
  );
  const { state: socketState, send } = useSocket(id ? `/ws/conversations/${encodeURIComponent(id)}` : undefined, {}, onFrame);

  useEffect(() => {
    setMessages([]);
    lastSeq.current = 0;
    catchUp();
  }, [id, catchUp]);

  useEffect(() => {
    if (socketState === 'open') return;
    const timer = window.setInterval(() => {
      catchUp();
      refetch();
    }, THREAD_POLL_MS);
    return () => window.clearInterval(timer);
  }, [socketState, catchUp, refetch]);

  const joined = !!conversation && !!admin && staffOf(conversation).some((p) => p.id === admin.id);
  const writable = conversation?.status === 'OPEN' || conversation?.status === 'WAITING';
  const canReply = joined && conversation?.status === 'OPEN';

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ block: 'end' });
    const last = messages[messages.length - 1];
    if (last && joined) {
      if (!send({ type: 'read', seq: last.seq })) markRead({ id, seq: last.seq });
    }
  }, [messages.length, joined]); // eslint-disable-line react-hooks/exhaustive-deps

  const post = async (e: FormEvent) => {
    e.preventDefault();
    const content = text.trim();
    if (!content || !canReply) return;
    const msgId = clientMsgId();
    setText('');
    if (!send({ type: 'post', clientMsgId: msgId, contentType: 'TEXT', content })) {
      try {
        await postMessage({ id, body: { clientMsgId: msgId, contentType: 'TEXT', content } }).unwrap();
        catchUp();
      } catch {
        setText(content);
      }
    }
  };

  // the staff actions answer the aggregate state; the row is refetched through the tag (a `status` frame arrives
  // too when the socket is open) and the history is caught up in case the polling fallback is active
  const onJoin = async () => {
    try {
      await join(id).unwrap();
      catchUp();
    } catch {
      /* shown */
    }
  };
  const onClose = async () => {
    try {
      await close(id).unwrap();
      catchUp();
    } catch {
      /* shown */
    }
  };
  const onBlock = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await block({ id, body: { reason: reason.trim() } }).unwrap();
      setBlocking(false);
      setReason('');
      catchUp();
    } catch {
      /* shown */
    }
  };

  const member = conversation ? memberOf(conversation) : null;
  const staff = conversation ? staffOf(conversation) : [];
  const actionError = joinState.error ?? closeState.error ?? blockState.error;

  return (
    <>
      <PageHeader
        title={conversation?.subject || t('chat.thread.title')}
        icon="bi-chat-left-text"
        subtitle={
          conversation && (
            <>
              {t(`chat.conversationType.${conversation.type}`)} · <code>{conversation.id}</code>
            </>
          )
        }
        actions={
          <div className="d-flex gap-2 align-items-center">
            <Badge bg="light" text="dark" data-testid="socket-state">
              {t(`chat.socket.${socketState}`)}
            </Badge>
            <Link to="/support" className="btn btn-sm btn-outline-secondary">
              {t('chat.thread.back')}
            </Link>
          </div>
        }
      />
      <MockBanner />
      {error ? <ProblemAlert error={error} /> : null}
      {actionError ? <ProblemAlert error={actionError} /> : null}
      {frameError && (
        <ProblemAlert error={{ status: 'FRAME', data: { title: t('chat.thread.frameError'), detail: frameError } }} />
      )}
      {isLoading && <LoadingBlock />}
      {conversation && (
        <Card className="shadow-sm">
          <Card.Header className="d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div className="small">
              <StatusBadge status={conversation.status} prefix="chat.conversationStatus" />{' '}
              {member != null && (
                <>
                  {t('chat.thread.member')} <Link to={`/users/${member}`}>#{member}</Link>
                </>
              )}
              {staff.length > 0 && (
                <>
                  {' · '}
                  <span>
                    {t('chat.support.staff')} {staff.map((p) => `#${p.id}`).join(', ')}
                  </span>
                </>
              )}
              {' · '}
              <span>
                {t('chat.thread.opened')} {formatDateTime(conversation.createdAt, i18n.language)}
              </span>
              {conversation.closedAt && (
                <>
                  {' · '}
                  <span>
                    {t('chat.thread.closedAt')} {formatDateTime(conversation.closedAt, i18n.language)}
                  </span>
                </>
              )}
              {conversation.blockedReason && (
                <>
                  {' · '}
                  <span>
                    {t('chat.thread.blockedReason')}: {conversation.blockedReason}
                  </span>
                </>
              )}
            </div>
            <div className="d-flex gap-2">
              {writable && !joined && (
                <Button size="sm" disabled={joinState.isLoading} onClick={onJoin}>
                  {joinState.isLoading && <Spinner size="sm" animation="border" className="me-1" />}
                  {t('chat.support.join')}
                </Button>
              )}
              {writable && (
                <>
                  <Button size="sm" variant="outline-secondary" disabled={closeState.isLoading} onClick={onClose}>
                    {t('chat.thread.close')}
                  </Button>
                  <Button size="sm" variant="outline-danger" onClick={() => setBlocking(true)}>
                    {t('chat.thread.block')}
                  </Button>
                </>
              )}
            </div>
          </Card.Header>
          <Card.Body data-testid="thread-messages" className="d-flex flex-column gap-2" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
            {messages.length === 0 && <div className="text-muted small">{t('chat.thread.noMessages')}</div>}
            {messages.map((m) => {
              const system = m.contentType === 'SYSTEM' || m.senderType === 'SYSTEM';
              const mine = m.senderType === 'ADMIN' && m.senderId === admin?.id;
              const staffMessage = m.senderType === 'ADMIN';
              const align = system ? 'align-self-center text-center' : staffMessage ? 'align-self-end' : 'align-self-start';
              const tone = system ? 'text-muted fst-italic' : mine ? 'bg-primary text-white' : staffMessage ? 'bg-primary-subtle' : 'bg-light';
              const who = system ? t('chat.thread.system') : mine ? t('chat.thread.you') : staffMessage ? `${t('chat.support.staff')} #${m.senderId}` : `${t('chat.thread.member')} #${m.senderId}`;
              return (
                <div key={m.id} className={`${align} rounded px-3 py-2 ${tone}`} style={{ maxWidth: '75%' }} data-sender={m.senderType}>
                  {!system && <div className="small fw-semibold">{who}</div>}
                  {m.contentType === 'OFFER' ? (
                    <strong>{t('chat.thread.offer', { amount: formatMoney(m.offerAmount, m.offerCurrency, i18n.language) })}</strong>
                  ) : m.contentType === 'IMAGE' && m.attachmentUrl ? (
                    <a href={m.attachmentUrl} target="_blank" rel="noreferrer">
                      {t('chat.thread.image')}
                    </a>
                  ) : (
                    <span style={{ whiteSpace: 'pre-wrap' }}>{m.content}</span>
                  )}
                  <div className={`small ${mine ? 'text-white-50' : 'text-muted'}`}>{formatDateTime(m.sentAt, i18n.language)}</div>
                </div>
              );
            })}
            <div ref={bottom} />
          </Card.Body>
          <Card.Footer>
            {canReply ? (
              <Form onSubmit={post}>
                <InputGroup>
                  <Form.Control value={text} onChange={(e) => setText(e.target.value)} placeholder={t('chat.thread.placeholder')} aria-label={t('chat.thread.reply')} maxLength={2000} />
                  <Button type="submit" disabled={!text.trim()}>
                    {t('chat.thread.send')}
                  </Button>
                </InputGroup>
              </Form>
            ) : (
              <div className="text-muted small">
                {writable ? t('chat.thread.joinFirst') : t('chat.thread.notWritable', { status: t(`chat.conversationStatus.${conversation.status}`) })}
              </div>
            )}
          </Card.Footer>
        </Card>
      )}

      <Modal show={blocking} onHide={() => setBlocking(false)}>
        <Form onSubmit={onBlock}>
          <Modal.Header closeButton>
            <Modal.Title>{t('chat.thread.blockTitle')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p className="small text-muted">{t('chat.thread.blockHint')}</p>
            <Form.Group controlId="block-reason">
              <Form.Label>{t('chat.thread.blockReason')}</Form.Label>
              <Form.Control as="textarea" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={() => setBlocking(false)}>
              {t('common.actions.cancel')}
            </Button>
            <Button type="submit" variant="danger" disabled={blockState.isLoading}>
              {blockState.isLoading && <Spinner size="sm" animation="border" className="me-1" />}
              {t('chat.thread.block')}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </>
  );
}
