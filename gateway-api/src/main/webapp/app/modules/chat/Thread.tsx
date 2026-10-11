import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Card, Form, InputGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { chatApi } from 'app/shared/api/chatApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { ConversationView, isMine, isWritable, MessageView, messageOfFrame, otherMember, ServerFrame, staffOf, StatusChange } from 'app/shared/model/chat';
import { formatDateTime } from 'app/shared/util/dates';
import { amountToCents, formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';
import { useConversationSocket } from './useConversationSocket';

export const THREAD_POLL_MS = 5000;
/** A live frame arrives before the read model has caught up; the row is refetched this long after it. */
export const ROW_SETTLE_MS = 1000;
/** Row loads that may miss quietly before the alert shows. */
export const ROW_RETRIES = 5;
const clientMsgId = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);

/**
 * One conversation of the signed-in member (doc/frontend-edges.md §15): the row and the history over REST, then
 * live frames over /ws/conversations/{id}. `message` frames are mapped from MessageLive and appended (de-duplicated
 * on the message id, then the client message id), `status` frames refetch the row, `ack` frames catch the history
 * up, `error` frames show in the alert. While the socket is not open the screen polls. Text (and an OFFER by the
 * buyer of a listing) goes as a frame with a REST fallback; the seller accepts or declines the open offer; the
 * read marker is raised as messages arrive. A member may write while WAITING (support, before staff joins) or OPEN.
 */
export default function Thread() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation('chat');
  const me = useAppSelector(s => s.auth.user?.id);
  const [conversation, setConversation] = useState<ConversationView | null>(null);
  const [messages, setMessages] = useState<MessageView[]>([]);
  const [text, setText] = useState('');
  const [offer, setOffer] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [change, setChange] = useState<StatusChange | null>(null);
  const lastSeq = useRef(0);
  const bottom = useRef<HTMLDivElement>(null);
  const lang = i18n.language;

  const append = useCallback((incoming: MessageView[]) => {
    setMessages(prev => {
      const next = [...prev];
      incoming.forEach(m => {
        const at = next.findIndex(x => x.id === m.id || (!!m.clientMsgId && x.clientMsgId === m.clientMsgId));
        if (at >= 0) next[at] = { ...next[at], ...m };
        else next.push(m);
      });
      next.sort((a, b) => a.seq - b.seq);
      lastSeq.current = next.length ? next[next.length - 1].seq : 0;
      return next;
    });
  }, []);

  // The row is projected a moment after the conversation was opened (or the member joined), so the first misses
  // are retried quietly; the alert shows only when the row keeps failing.
  const rowMisses = useRef(0);
  const loadRow = useCallback(
    () =>
      chatApi
        .view(id)
        .then(c => {
          rowMisses.current = 0;
          setConversation(c);
        })
        .catch(e => {
          rowMisses.current += 1;
          if (rowMisses.current > ROW_RETRIES) setError(describeError(e, t('loadFailed')));
        }),
    [id, t]
  );
  const catchUp = useCallback(
    () =>
      chatApi
        .messages(id, lastSeq.current)
        .then(append)
        .catch(() => undefined),
    [id, append]
  );

  const settleTimer = useRef<number | undefined>(undefined);
  const refetchRowSoon = useCallback(() => {
    window.clearTimeout(settleTimer.current);
    settleTimer.current = window.setTimeout(() => void loadRow(), ROW_SETTLE_MS);
  }, [loadRow]);

  const onFrame = useCallback(
    (f: ServerFrame) => {
      // frames outrun the projection, so the row is updated from the frame first and refetched once it has settled
      if (f.type === 'message') {
        append([messageOfFrame(f)]);
        if (f.contentType === 'OFFER') {
          setConversation(prev =>
            prev ? { ...prev, openOffer: { messageId: f.messageId, amount: (f.offerAmountCents ?? 0) / 100, currency: f.currency ?? 'DKK', at: f.sentAt } } : prev
          );
          refetchRowSoon();
        }
      } else if (f.type === 'status') {
        setChange(f.change);
        if ((f.change === 'OFFER_ACCEPTED' || f.change === 'OFFER_DECLINED') && f.messageId) {
          const status = f.change === 'OFFER_ACCEPTED' ? 'ACCEPTED' : 'DECLINED';
          setMessages(prev => prev.map(m => (m.id === f.messageId ? { ...m, offerStatus: status } : m)));
        }
        setConversation(prev => {
          if (!prev) return prev;
          const next: ConversationView = { ...prev, status: f.status };
          if (f.change === 'JOINED' && f.participantType && f.participantId != null && !prev.participants.some(p => p.type === f.participantType && p.id === f.participantId)) {
            next.participants = [
              ...prev.participants,
              { type: f.participantType, id: f.participantId, role: f.participantType === 'ADMIN' ? 'CSR' : 'MEMBER', lastReadSeq: 0, joinedAt: f.at },
            ];
          }
          if (f.change === 'OFFER_ACCEPTED') {
            next.acceptedOffer = prev.openOffer ?? (f.messageId ? { messageId: f.messageId, amount: (f.amountCents ?? 0) / 100, currency: f.currency ?? 'DKK', at: f.at } : null);
            next.openOffer = null;
          }
          if (f.change === 'OFFER_DECLINED') next.openOffer = null;
          if (f.change === 'CLOSED') next.closedAt = f.at;
          return next;
        });
        refetchRowSoon();
      } else if (f.type === 'ack') void catchUp();
      else if (f.type === 'error') setError(f.detail);
    },
    [append, refetchRowSoon, catchUp]
  );
  const { state: socketState, send } = useConversationSocket(id || undefined, onFrame);

  useEffect(() => {
    setMessages([]);
    lastSeq.current = 0;
    rowMisses.current = 0;
    setChange(null);
    void loadRow();
    void catchUp();
    return () => window.clearTimeout(settleTimer.current);
  }, [id, loadRow, catchUp]);

  // until the row is known, retry it (socket or not)
  useEffect(() => {
    if (conversation || !id) return;
    const timer = window.setInterval(() => void loadRow(), ROW_SETTLE_MS);
    return () => window.clearInterval(timer);
  }, [conversation, id, loadRow]);

  // REST fallback while the socket is not open
  useEffect(() => {
    if (socketState === 'open') return;
    const timer = window.setInterval(() => {
      void loadRow();
      void catchUp();
    }, THREAD_POLL_MS);
    return () => window.clearInterval(timer);
  }, [socketState, loadRow, catchUp]);

  // read marker: once the row is known (so the member is a participant) and the history has moved
  const lastSeenSeq = useRef(0);
  useEffect(() => {
    bottom.current?.scrollIntoView?.({ block: 'end' });
    const seq = lastSeq.current;
    if (!conversation || seq === 0 || seq <= lastSeenSeq.current) return;
    lastSeenSeq.current = seq;
    if (!send({ type: 'read', seq })) chatApi.markRead(id, seq).catch(() => undefined);
  }, [messages.length, conversation, id, send]);

  const post = async (e: FormEvent) => {
    e.preventDefault();
    const content = text.trim();
    if (!content) return;
    const msgId = clientMsgId();
    setText('');
    if (!send({ type: 'post', clientMsgId: msgId, contentType: 'TEXT', content })) {
      try {
        await chatApi.post(id, {
          clientMsgId: msgId,
          contentType: 'TEXT',
          content,
        });
        await catchUp();
      } catch (err) {
        setError(describeError(err, t('postFailed')));
      }
    }
  };

  const makeOffer = async (e: FormEvent) => {
    e.preventDefault();
    const amountCents = amountToCents(Number(offer));
    if (!amountCents) return;
    const msgId = clientMsgId();
    setOffer('');
    const currency = conversation?.openOffer?.currency ?? conversation?.acceptedOffer?.currency ?? 'DKK';
    if (!send({ type: 'offer', clientMsgId: msgId, amountCents, currency })) {
      try {
        await chatApi.post(id, {
          clientMsgId: msgId,
          contentType: 'OFFER',
          amountCents,
          currency,
        });
        await catchUp();
      } catch (err) {
        setError(describeError(err, t('postFailed')));
      }
    }
  };

  const decide = async (accept: boolean) => {
    const offerMessageId = conversation?.openOffer?.messageId;
    try {
      const answer = accept ? await chatApi.acceptOffer(id) : await chatApi.declineOffer(id);
      // the aggregate answered; the history is not refetched, so mark the offer line here (a status frame does the same on the other side)
      if (offerMessageId) setMessages(prev => prev.map(m => (m.id === offerMessageId ? { ...m, offerStatus: accept ? 'ACCEPTED' : 'DECLINED' } : m)));
      setConversation(prev =>
        prev
          ? {
              ...prev,
              status: answer.status,
              openOffer: null,
              acceptedOffer: answer.acceptedOffer
                ? { messageId: answer.acceptedOffer.messageId, amount: answer.acceptedOffer.amountCents / 100, currency: answer.acceptedOffer.currency, at: answer.updatedAt }
                : prev.acceptedOffer,
            }
          : prev
      );
      refetchRowSoon();
      await catchUp();
    } catch (err) {
      setError(describeError(err, t('offerFailed')));
    }
  };

  const buyer = me != null && me === conversation?.buyerId;
  const seller = me != null && me === conversation?.sellerId;
  const writable = isWritable(conversation?.status);
  const other = conversation ? otherMember(conversation, me) : null;
  const staff = conversation ? staffOf(conversation) : [];

  return (
    <Card>
      <Card.Header className="d-flex justify-content-between align-items-center flex-wrap gap-2">
        <span>
          <Link to="/messages" className="me-2 text-decoration-none" aria-label={t('thread.back')}>
            &larr;
          </Link>
          {conversation?.type === 'LISTING' && conversation.announceId ? (
            <Link to={`/announce/${encodeURIComponent(conversation.announceId)}`}>{t('thread.listing', { id: conversation.announceId })}</Link>
          ) : (
            <span>{conversation ? conversation.subject || t('inbox.support') : '…'}</span>
          )}
          {conversation && (
            <Badge bg="secondary" className="ms-2" data-testid="conversation-status">
              {t(`status.${conversation.status}`)}
            </Badge>
          )}
          {other != null && <span className="small text-muted ms-2">{t('thread.with', { id: other })}</span>}
          {staff.map(p => (
            <span key={p.id} className="small text-muted ms-2">
              {t('thread.staff', { id: p.id })}
            </span>
          ))}
        </span>
        <span className="small text-muted" data-testid="socket-state">
          {t(`socket.${socketState}`)}
        </span>
      </Card.Header>
      <Card.Body className="an-chat-thread d-flex flex-column gap-2" data-testid="thread-messages">
        {conversation?.type === 'SUPPORT' && conversation.status === 'WAITING' && <div className="an-bubble system">{t('thread.waiting')}</div>}
        {messages.map(m => {
          const mine = isMine(m, me);
          const cls = m.contentType === 'SYSTEM' || m.senderType === 'SYSTEM' ? 'system' : mine ? 'mine' : 'theirs';
          return (
            <div key={m.id} className={`an-bubble ${cls}`} data-sender={m.senderType} data-mine={mine || undefined}>
              {m.senderType === 'ADMIN' && <div className="small fw-semibold">{t('thread.support')}</div>}
              {m.contentType === 'OFFER' ? (
                <strong>
                  {t('thread.offer', {
                    amount: formatMoney(m.offerAmount, m.offerCurrency, lang),
                  })}
                  {m.offerStatus && m.offerStatus !== 'OPEN' && <span className="ms-1 fw-normal">({t(`offerStatus.${m.offerStatus}`)})</span>}
                </strong>
              ) : m.contentType === 'IMAGE' && m.attachmentUrl ? (
                <img src={m.attachmentUrl} alt="" style={{ maxWidth: '100%' }} />
              ) : (
                <span style={{ whiteSpace: 'pre-wrap' }}>{m.content}</span>
              )}
              <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                {formatDateTime(m.sentAt, lang)}
              </div>
            </div>
          );
        })}
        <div ref={bottom} />
      </Card.Body>
      <Card.Footer>
        <ProblemAlert message={error} onClose={() => setError(null)} />
        {change && (
          <Alert variant="info" className="py-1 my-2 small" dismissible onClose={() => setChange(null)}>
            {t(`change.${change}`)}
          </Alert>
        )}
        {conversation?.status === 'CLOSED' && <p className="text-muted small mb-2">{t('thread.closed')}</p>}
        {conversation?.status === 'BLOCKED' && <p className="text-muted small mb-2">{t('thread.blocked', { reason: conversation.blockedReason ?? '' })}</p>}
        {conversation?.openOffer && seller && writable && (
          <div className="d-flex gap-2 align-items-center mb-2" data-testid="open-offer">
            <span>
              {t('thread.openOffer', {
                amount: formatMoney(conversation.openOffer.amount, conversation.openOffer.currency, lang),
              })}
            </span>
            <Button size="sm" variant="success" onClick={() => decide(true)}>
              {t('thread.accept')}
            </Button>
            <Button size="sm" variant="outline-danger" onClick={() => decide(false)}>
              {t('thread.decline')}
            </Button>
          </div>
        )}
        {conversation?.openOffer && buyer && writable && (
          <p className="small text-muted mb-2">
            {t('thread.yourOffer', {
              amount: formatMoney(conversation.openOffer.amount, conversation.openOffer.currency, lang),
            })}
          </p>
        )}
        <Form onSubmit={post} className="mb-2">
          <InputGroup>
            <Form.Control value={text} onChange={e => setText(e.target.value)} placeholder={t('thread.placeholder')} disabled={!writable} aria-label={t('thread.message')} />
            <Button type="submit" disabled={!writable || !text.trim()}>
              {t('thread.send')}
            </Button>
          </InputGroup>
        </Form>
        {buyer && writable && conversation?.type === 'LISTING' && !conversation.openOffer && (
          <Form onSubmit={makeOffer}>
            <InputGroup size="sm">
              <InputGroup.Text>{t('thread.offerLabel')}</InputGroup.Text>
              <Form.Control type="number" min={1} step="any" value={offer} onChange={e => setOffer(e.target.value)} aria-label={t('thread.offerLabel')} />
              <Button type="submit" variant="outline-primary" disabled={!offer}>
                {t('thread.sendOffer')}
              </Button>
            </InputGroup>
          </Form>
        )}
      </Card.Footer>
    </Card>
  );
}
