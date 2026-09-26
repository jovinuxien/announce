import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Card, Form, InputGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { chatApi } from 'app/shared/api/chatApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { ConversationView, MessageView, ServerFrame } from 'app/shared/model/chat';
import { formatDateTime } from 'app/shared/util/dates';
import { amountToCents, centsToAmount, formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';
import { useConversationSocket } from './useConversationSocket';

const clientMsgId = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);

/**
 * One conversation: history from REST, live frames over the WebSocket (REST fallback with polling when the socket
 * is unavailable), text and OFFER messages, accept or decline of the open offer by the seller, read marker.
 */
export default function Thread() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation('chat');
  const user = useAppSelector(s => s.auth.user);
  const [conversation, setConversation] = useState<ConversationView | null>(null);
  const [messages, setMessages] = useState<MessageView[]>([]);
  const [text, setText] = useState('');
  const [offer, setOffer] = useState('');
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);
  const lang = i18n.language;

  const append = useCallback((m: MessageView) => {
    setMessages(prev => (prev.some(x => x.id === m.id || (m.clientMsgId && x.clientMsgId === m.clientMsgId)) ? prev : [...prev, m].sort((a, b) => a.seq - b.seq)));
  }, []);

  const onFrame = useCallback(
    (f: ServerFrame) => {
      if (f.type === 'message') append(f as MessageView);
      else if (f.type === 'status' || f.type === 'ack') chatApi.view(id).then(setConversation).catch(() => undefined);
      else if (f.type === 'error') setError(f.detail);
    },
    [append, id]
  );
  const { state: socketState, send } = useConversationSocket(id || undefined, onFrame);

  const refresh = useCallback(() => {
    chatApi.view(id).then(setConversation).catch(e => setError(describeError(e, t('loadFailed'))));
    setMessages(prev => {
      const after = prev.length ? prev[prev.length - 1].seq : 0;
      chatApi.messages(id, after).then(ms => ms.forEach(append)).catch(() => undefined);
      return prev;
    });
  }, [id, t, append]);

  useEffect(() => {
    setMessages([]);
    refresh();
  }, [id, refresh]);

  // REST fallback when the socket is not usable
  useEffect(() => {
    if (socketState === 'open') return;
    const timer = window.setInterval(refresh, 5000);
    return () => window.clearInterval(timer);
  }, [socketState, refresh]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' });
    const last = messages[messages.length - 1];
    if (last && conversation) {
      if (!send({ type: 'read', seq: last.seq })) chatApi.markRead(id, last.seq).catch(() => undefined);
    }
  }, [messages.length]); // eslint-disable-line react-hooks/exhaustive-deps

  const post = async (e: FormEvent) => {
    e.preventDefault();
    const content = text.trim();
    if (!content) return;
    const msgId = clientMsgId();
    setText('');
    if (!send({ type: 'post', clientMsgId: msgId, contentType: 'TEXT', content })) {
      try {
        await chatApi.post(id, { clientMsgId: msgId, contentType: 'TEXT', content });
        refresh();
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
    const currency = conversation?.openOffer?.currency ?? 'DKK';
    if (!send({ type: 'offer', clientMsgId: msgId, amountCents, currency })) {
      try {
        await chatApi.post(id, { clientMsgId: msgId, contentType: 'OFFER', amountCents, currency });
        refresh();
      } catch (err) {
        setError(describeError(err, t('postFailed')));
      }
    }
  };

  const decide = async (accept: boolean) => {
    try {
      setConversation(accept ? await chatApi.acceptOffer(id) : await chatApi.declineOffer(id));
      refresh();
    } catch (err) {
      setError(describeError(err, t('offerFailed')));
    }
  };

  const buyer = user?.id === conversation?.buyerId;
  const seller = user?.id === conversation?.sellerId;
  const open = conversation?.status === 'OPEN';

  return (
    <Card>
      <Card.Header className="d-flex justify-content-between align-items-center">
        <span>
          {conversation?.type === 'SUPPORT' ? conversation.subject || t('inbox.support') : t('thread.listing', { id: conversation?.announceId })}
          {conversation && <Badge bg="secondary" className="ms-2">{t(`status.${conversation.status}`)}</Badge>}
        </span>
        <span className="small text-muted">{t(`socket.${socketState}`)}</span>
      </Card.Header>
      <Card.Body className="an-chat-thread d-flex flex-column gap-2">
        {messages.map(m => {
          const mine = m.senderType === 'MEMBER' && m.senderId === user?.id;
          const cls = m.contentType === 'SYSTEM' ? 'system' : mine ? 'mine' : 'theirs';
          return (
            <div key={m.id} className={`an-bubble ${cls}`}>
              {m.contentType === 'OFFER' ? (
                <strong>{t('thread.offer', { amount: formatMoney(m.offerAmount, m.offerCurrency, lang) })} {m.offerStatus && `(${m.offerStatus})`}</strong>
              ) : m.contentType === 'IMAGE' && m.attachmentUrl ? (
                <img src={m.attachmentUrl} alt="" style={{ maxWidth: '100%' }} />
              ) : (
                <span style={{ whiteSpace: 'pre-wrap' }}>{m.content}</span>
              )}
              <div className="text-muted" style={{ fontSize: '0.7rem' }}>{formatDateTime(m.sentAt, lang)}</div>
            </div>
          );
        })}
        <div ref={bottom} />
      </Card.Body>
      <Card.Footer>
        <ProblemAlert message={error} onClose={() => setError(null)} />
        {conversation?.openOffer && seller && open && (
          <div className="d-flex gap-2 align-items-center mb-2">
            <span>{t('thread.openOffer', { amount: formatMoney(conversation.openOffer.amountCents != null ? centsToAmount(conversation.openOffer.amountCents) : conversation.openOffer.amount, conversation.openOffer.currency, lang) })}</span>
            <Button size="sm" variant="success" onClick={() => decide(true)}>{t('thread.accept')}</Button>
            <Button size="sm" variant="outline-danger" onClick={() => decide(false)}>{t('thread.decline')}</Button>
          </div>
        )}
        <Form onSubmit={post} className="mb-2">
          <InputGroup>
            <Form.Control value={text} onChange={e => setText(e.target.value)} placeholder={t('thread.placeholder')} disabled={!open} aria-label={t('thread.message')} />
            <Button type="submit" disabled={!open}>{t('thread.send')}</Button>
          </InputGroup>
        </Form>
        {buyer && open && conversation?.type === 'LISTING' && (
          <Form onSubmit={makeOffer}>
            <InputGroup size="sm">
              <InputGroup.Text>{t('thread.offerLabel')}</InputGroup.Text>
              <Form.Control type="number" min={1} value={offer} onChange={e => setOffer(e.target.value)} aria-label={t('thread.offerLabel')} />
              <Button type="submit" variant="outline-primary" disabled={!offer}>{t('thread.sendOffer')}</Button>
            </InputGroup>
          </Form>
        )}
      </Card.Footer>
    </Card>
  );
}
