import { useMemo } from 'react';
import { Card, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ORDER_STATUSES, useListOrdersQuery, type OrderStatus } from 'app/shared/api/ordersApi';
import { formatDateTime, formatMoney } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import StatusBadge from 'app/shared/ui/StatusBadge';
import { usePaging } from 'app/shared/ui/usePaging';
import MockBanner from './MockBanner';
import TradeFilterBar from './TradeFilterBar';
import { tradeFiltersFromSearch, tradeFiltersToParams, type TradeFilters } from './tradeFilters';

export default function OrdersPage() {
  const { t, i18n } = useTranslation();
  const { params, page, size, setPage, setSize, setFilters } = usePaging();
  const filters = useMemo(() => tradeFiltersFromSearch(params, ORDER_STATUSES), [params]);
  const { data, error, isLoading } = useListOrdersQuery({ ...tradeFiltersToParams(filters), page, size });
  const apply = (next: TradeFilters<OrderStatus>) => setFilters({ status: next.status, seller: next.seller, buyer: next.buyer, q: next.q });
  return (
    <>
      <PageHeader title={t('trade.orders.title')} icon="bi-receipt" />
      <MockBanner />
      <TradeFilterBar filters={filters} statuses={ORDER_STATUSES} statusPrefix="trade.orderStatus" fields={{ seller: true, buyer: true, q: true }} onApply={apply} />
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
                      {o.title ?? o.id}
                    </Link>
                    <div>
                      <code className="small">{o.id}</code>
                    </div>
                  </td>
                  <td>{t(`trade.door.${o.door}`)}</td>
                  <td className="small">
                    {t('trade.orders.buyer')} #{o.buyerId} · {t('trade.orders.seller')} #{o.sellerId}
                  </td>
                  <td className="text-end">{formatMoney(o.agreedPrice, o.currency, i18n.language)}</td>
                  <td className="text-end text-muted">{formatMoney(o.feeAmount, o.currency, i18n.language)}</td>
                  <td>
                    <StatusBadge status={o.status} prefix="trade.orderStatus" />
                    {o.disputeReason && <div className="small text-danger">{o.disputeReason}</div>}
                  </td>
                  <td className="small text-muted">{formatDateTime(o.agreedAt ?? o.addTime, i18n.language)}</td>
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
