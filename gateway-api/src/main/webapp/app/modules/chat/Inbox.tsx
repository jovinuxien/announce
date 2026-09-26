import { useEffect, useState } from 'react';
import { Badge, ListGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { chatApi } from 'app/shared/api/chatApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { ConversationView } from 'app/shared/model/chat';
import { formatDateTime } from 'app/shared/util/dates';
import { describeError } from 'app/shared/util/problem';

export default function Inbox() {
  const { t, i18n } = useTranslation('chat');
  const [items, setItems] = useState<ConversationView[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    chatApi.inbox().then(p => setItems(p.items)).catch(e => setError(describeError(e, t('loadFailed'))));
  }, [t]);

  return (
    <div>
      <h1 className="h4">{t('inbox.title')}</h1>
      <ProblemAlert message={error} />
      {items.length === 0 ? (
        <p className="text-muted">{t('inbox.empty')}</p>
      ) : (
        <ListGroup>
          {items.map(c => (
            <ListGroup.Item key={c.id} as={Link} to={`/messages/${encodeURIComponent(c.id)}`} action className="d-flex justify-content-between align-items-center">
              <span>
                <span className="fw-semibold">{c.type === 'SUPPORT' ? c.subject || t('inbox.support') : t('inbox.listing', { id: c.announceId })}</span>
                <span className="text-muted small ms-2">{t(`status.${c.status}`)}</span>
                {c.openOffer && <Badge bg="warning" text="dark" className="ms-2">{t('inbox.openOffer')}</Badge>}
              </span>
              <span className="small text-muted">
                {!!c.unread && <Badge bg="primary" pill className="me-2">{c.unread}</Badge>}
                {formatDateTime(c.lastMessageAt ?? c.updatedAt, i18n.language)}
              </span>
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
    </div>
  );
}
