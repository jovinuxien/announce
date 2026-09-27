import { Button, Card, Form, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { CONVERSATION_STATUSES, useJoinConversationMutation, useListConversationsQuery, type ConversationStatus } from 'app/shared/api/supportApi';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import StatusBadge from 'app/shared/ui/StatusBadge';
import { usePaging } from 'app/shared/ui/usePaging';
import MockBanner from 'app/modules/trade/MockBanner';

export default function SupportQueuePage() {
  const { t, i18n } = useTranslation();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const status = (params.get('status') ?? 'WAITING') as ConversationStatus | '';
  const { data, error, isLoading } = useListConversationsQuery({ status, page, size });
  const [join, joinState] = useJoinConversationMutation();
  return (
    <>
      <PageHeader
        title={t('chat.support.title')}
        icon="bi-chat-dots"
        subtitle={t('chat.support.subtitle')}
        actions={
          <Form.Select size="sm" value={status} onChange={(e) => setParam('status', e.target.value)} aria-label={t('common.statusLabel')}>
            <option value="">{t('common.all')}</option>
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
      {data && data.items.length === 0 && <EmptyState icon="bi-chat-square" hint={t('chat.support.emptyHint')} />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>{t('chat.support.conversation')}</th>
                <th>{t('chat.support.openedBy')}</th>
                <th>{t('chat.support.openedAt')}</th>
                <th>{t('chat.support.lastMessage')}</th>
                <th>{t('common.statusLabel')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((c) => (
                <tr key={c.id}>
                  <td>
                    <code className="small">{c.id}</code>
                    <div className="small text-muted">{t(`chat.conversationType.${c.type}`)}{c.announceId && ` · ${c.announceId}`}</div>
                  </td>
                  <td>#{c.openedBy}</td>
                  <td className="small text-muted">{formatDateTime(c.openedAt, i18n.language)}</td>
                  <td className="small" style={{ maxWidth: 320 }}>
                    <div className="text-truncate">{c.lastMessagePreview}</div>
                    <div className="text-muted">{formatDateTime(c.lastMessageAt, i18n.language)}</div>
                  </td>
                  <td>
                    <StatusBadge status={c.status} prefix="chat.conversationStatus" />
                    {c.staffId && <div className="small text-muted">{t('chat.support.staff')} #{c.staffId}</div>}
                  </td>
                  <td className="text-end">
                    <Button size="sm" disabled={c.status !== 'WAITING' || joinState.isLoading} onClick={() => join(c.id)}>
                      {joinState.isLoading && joinState.originalArgs === c.id && <Spinner size="sm" animation="border" className="me-1" />}
                      {t('chat.support.join')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} onSize={setSize} />}
    </>
  );
}
