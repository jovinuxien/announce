import { Card, Form, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ORDER_STATUSES, useListOrdersQuery, type OrderStatus } from 'app/shared/api/ordersApi';
import { formatCents, formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import StatusBadge from 'app/shared/ui/StatusBadge';
import { usePaging } from 'app/shared/ui/usePaging';
import MockBanner from './MockBanner';

export default function OrdersPage() {
  const { t, i18n } = useTranslation();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const status = (params.get('status') ?? '') as OrderStatus | '';
  const { data, error, isLoading } = useListOrdersQuery({ status, page, size });
  return (
    <>
      <PageHeader
        title={t('trade.orders.title')}
        icon="bi-receipt"
        actions={
          <Form.Select size="sm" value={status} onChange={(e) => setParam('status', e.target.value)} aria-label={t('common.statusLabel')}>
            <option value="">{t('common.all')}</option>
            {ORDER_STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`trade.orderStatus.${s}`)}
              </option>
            ))}
          </Form.Select>
        }
      />
      <MockBanner />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && data.items.length === 0 && <EmptyState />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>{t('trade.orders.order')}</th>
                <th>{t('trade.orders.door')}</th>
                <th>{t('trade.orders.parties')}</th>
                <th className="text-end">{t('trade.orders.price')}</th>
                <th className="text-end">{t('trade.orders.fee')}</th>
                <th>{t('common.statusLabel')}</th>
                <th>{t('trade.orders.agreedAt')}</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((o) => (
                <tr key={o.id}>
                  <td>
                    <Link to={`/orders/${encodeURIComponent(o.id)}`} className="fw-semibold text-decoration-none">
                      {o.announceTitle ?? o.id}
                    </Link>
                    <div>
                      <code className="small">{o.id}</code>
                    </div>
                  </td>
                  <td>{t(`trade.door.${o.door}`)}</td>
                  <td className="small">
                    {t('trade.orders.buyer')} #{o.buyerId} · {t('trade.orders.seller')} #{o.sellerId}
                  </td>
                  <td className="text-end">{formatCents(o.agreedPrice, o.currency, i18n.language)}</td>
                  <td className="text-end text-muted">{formatCents(o.feeAmount, o.currency, i18n.language)}</td>
                  <td>
                    <StatusBadge status={o.status} prefix="trade.orderStatus" />
                    {o.disputeReason && <div className="small text-danger">{o.disputeReason}</div>}
                  </td>
                  <td className="small text-muted">{formatDateTime(o.agreedAt, i18n.language)}</td>
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
