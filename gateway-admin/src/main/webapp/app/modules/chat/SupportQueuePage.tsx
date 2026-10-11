import { Button, Card, Form, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import {
  CONVERSATION_STATUSES,
  memberOf,
  staffOf,
  useJoinConversationMutation,
  useListConversationsQuery,
  type ConversationStatus,
} from 'app/shared/api/supportApi';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import StatusBadge from 'app/shared/ui/StatusBadge';
import { usePaging } from 'app/shared/ui/usePaging';
import MockBanner from 'app/modules/trade/MockBanner';

/** How often the queue refetches while shown: nothing pushes queue changes to the staff channel. */
export const QUEUE_POLL_MS = 15_000;

function statusOf(value: string | null): ConversationStatus {
  return CONVERSATION_STATUSES.includes(value as ConversationStatus) ? (value as ConversationStatus) : 'WAITING';
}

/**
 * The support queue: messaging lists one status per call, oldest first (WAITING is the queue proper). Join turns a
 * WAITING conversation OPEN with this staff member as a participant and opens it; any other row opens read-only
 * until the staff member joins there. See doc/frontend-edges.md §14.
 */
export default function SupportQueuePage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const status = statusOf(params.get('status'));
  const { data, error, isLoading } = useListConversationsQuery({ status, page, size }, { pollingInterval: QUEUE_POLL_MS });
  const [join, joinState] = useJoinConversationMutation();
  const onJoin = async (id: string) => {
    try {
      await join(id).unwrap();
      navigate(`/support/${encodeURIComponent(id)}`);
    } catch {
      /* shown */
    }
  };
  return (
    <>
      <PageHeader
        title={t('chat.support.title')}
        icon="bi-chat-dots"
        subtitle={t('chat.support.subtitle')}
        actions={
          <Form.Select size="sm" value={status} onChange={(e) => setParam('status', e.target.value)} aria-label={t('common.statusLabel')}>
            {CONVERSATION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`chat.conversationStatus.${s}`)}
              </option>
            ))}
          </Form.Select>
        }
      />
      <MockBanner />
      {error ? <ProblemAlert error={error} /> : null}
      {joinState.error ? <ProblemAlert error={joinState.error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && data.items.length === 0 && <EmptyState icon="bi-chat-square" hint={status === 'WAITING' ? t('chat.support.emptyHint') : t('chat.support.emptyStatus')} />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>{t('chat.support.conversation')}</th>
                <th>{t('chat.support.member')}</th>
                <th>{t('chat.support.openedAt')}</th>
                <th>{t('chat.support.lastMessage')}</th>
                <th>{t('common.statusLabel')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((c) => {
                const member = memberOf(c);
                const staff = staffOf(c);
                return (
                  <tr key={c.id}>
                    <td>
                      <Link to={`/support/${encodeURIComponent(c.id)}`} className="fw-semibold text-decoration-none">
                        {c.subject || t('chat.support.noSubject')}
                      </Link>
                      <div className="small text-muted">
                        {t(`chat.conversationType.${c.type}`)}
                        {c.announceId && (
                          <>
                            {' · '}
                            <Link to={`/announces/${encodeURIComponent(c.announceId)}`}>{c.announceId}</Link>
                          </>
                        )}
                        {' · '}
                        <code>{c.id}</code>
                      </div>
                    </td>
                    <td>{member != null ? <Link to={`/users/${member}`}>#{member}</Link> : '–'}</td>
                    <td className="small text-muted">{formatDateTime(c.createdAt, i18n.language)}</td>
                    <td className="small">
                      <div>{formatDateTime(c.lastMessageAt, i18n.language)}</div>
                      <div className="text-muted">{t('chat.support.messages', { count: c.messageCount })}</div>
                    </td>
                    <td>
                      <StatusBadge status={c.status} prefix="chat.conversationStatus" />
                      {staff.length > 0 && (
                        <div className="small text-muted">
                          {t('chat.support.staff')} {staff.map((p) => `#${p.id}`).join(', ')}
                        </div>
                      )}
                    </td>
                    <td className="text-end text-nowrap">
                      {c.status === 'WAITING' ? (
                        <Button size="sm" disabled={joinState.isLoading} onClick={() => onJoin(c.id)}>
                          {joinState.isLoading && joinState.originalArgs === c.id && <Spinner size="sm" animation="border" className="me-1" />}
                          {t('chat.support.join')}
                        </Button>
                      ) : (
                        <Link to={`/support/${encodeURIComponent(c.id)}`} className="btn btn-sm btn-outline-secondary">
                          {t('chat.support.open')}
                        </Link>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        </Card>
      )}
      {data && <Pagination page={data.page} size={data.size} count={data.items.length} hasMore={data.items.length >= data.size} onPage={setPage} onSize={setSize} />}
    </>
  );
}
