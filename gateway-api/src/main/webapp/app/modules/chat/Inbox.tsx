import { FormEvent, useCallback, useEffect, useState } from 'react';
import { Badge, Button, Card, Form, ListGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { chatApi } from 'app/shared/api/chatApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { ConversationView, otherMember } from 'app/shared/model/chat';
import { formatDateTime } from 'app/shared/util/dates';
import { describeError } from 'app/shared/util/problem';

export const INBOX_POLL_MS = 30000;
const clientMsgId = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);

/**
 * The member's conversations (GET /api/conversations, newest activity first, polled) and the way to reach
 * support: a subject and a first message open a SUPPORT conversation (WAITING until staff joins) and post the
 * message, then the thread opens. doc/frontend-edges.md §15.
 */
export default function Inbox() {
  const { t, i18n } = useTranslation('chat');
  const navigate = useNavigate();
  const me = useAppSelector(s => s.auth.user?.id);
  const [items, setItems] = useState<ConversationView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [opening, setOpening] = useState(false);
  const [openError, setOpenError] = useState<string | null>(null);

  const load = useCallback(
    () =>
      chatApi
        .inbox()
        .then(p => setItems(p.items))
        .catch(e => setError(describeError(e, t('loadFailed')))),
    [t]
  );

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), INBOX_POLL_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  const contactSupport = async (e: FormEvent) => {
    e.preventDefault();
    const content = message.trim();
    if (!subject.trim() || !content) return;
    setOpening(true);
    setOpenError(null);
    try {
      const c = await chatApi.open({
        type: 'SUPPORT',
        subject: subject.trim(),
      });
      await chatApi.post(c.id, {
        clientMsgId: clientMsgId(),
        contentType: 'TEXT',
        content,
      });
      navigate(`/messages/${encodeURIComponent(c.id)}`);
    } catch (err) {
      setOpenError(describeError(err, t('openFailed')));
    } finally {
      setOpening(false);
    }
  };

  const title = (c: ConversationView) => (c.type === 'SUPPORT' ? c.subject || t('inbox.support') : t('inbox.listing', { id: c.announceId }));

  return (
    <div>
      <h1 className="h4">{t('inbox.title')}</h1>
      <ProblemAlert message={error} />
      {items && items.length === 0 && <p className="text-muted">{t('inbox.empty')}</p>}
      {items && items.length > 0 && (
        <ListGroup className="mb-4" data-testid="inbox-list">
          {items.map(c => {
            const other = otherMember(c, me);
            return (
              <ListGroup.Item key={c.id} as={Link} to={`/messages/${encodeURIComponent(c.id)}`} action className="d-flex justify-content-between align-items-center">
                <span>
                  <span className="fw-semibold">{title(c)}</span>
                  <span className="text-muted small ms-2">{t(`status.${c.status}`)}</span>
                  {other != null && <span className="text-muted small ms-2">{t('inbox.with', { id: other })}</span>}
                  {c.openOffer && (
                    <Badge bg="warning" text="dark" className="ms-2">
                      {t('inbox.openOffer')}
                    </Badge>
                  )}
                </span>
                <span className="small text-muted text-nowrap">
                  {!!c.unread && (
                    <Badge bg="primary" pill className="me-2" data-testid="unread">
                      {c.unread}
                    </Badge>
                  )}
                  {formatDateTime(c.lastMessageAt ?? c.updatedAt, i18n.language)}
                </span>
              </ListGroup.Item>
            );
          })}
        </ListGroup>
      )}
      <Card>
        <Card.Body>
          <h2 className="h6">{t('inbox.contactSupport')}</h2>
          <p className="small text-muted">{t('inbox.contactSupportHint')}</p>
          <ProblemAlert message={openError} onClose={() => setOpenError(null)} />
          <Form onSubmit={contactSupport}>
            <Form.Group className="mb-2" controlId="support-subject">
              <Form.Label>{t('inbox.subject')}</Form.Label>
              <Form.Control value={subject} onChange={e => setSubject(e.target.value)} maxLength={255} required />
            </Form.Group>
            <Form.Group className="mb-2" controlId="support-message">
              <Form.Label>{t('inbox.firstMessage')}</Form.Label>
              <Form.Control as="textarea" rows={3} value={message} onChange={e => setMessage(e.target.value)} required />
            </Form.Group>
            <Button type="submit" size="sm" disabled={opening || !subject.trim() || !message.trim()}>
              {t('inbox.start')}
            </Button>
          </Form>
        </Card.Body>
      </Card>
    </div>
  );
}
