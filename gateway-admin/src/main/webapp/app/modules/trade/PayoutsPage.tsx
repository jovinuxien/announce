import { useMemo } from 'react';
import { Card, Col, Row, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PAYOUT_STATUSES, useGetPayoutsQuery, type PayoutStatus } from 'app/shared/api/payoutsApi';
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

export default function PayoutsPage() {
  const { t, i18n } = useTranslation();
  const lng = i18n.language;
  const { params, page, size, setPage, setSize, setFilters } = usePaging();
  const filters = useMemo(() => tradeFiltersFromSearch(params, PAYOUT_STATUSES), [params]);
  const { status, seller } = tradeFiltersToParams(filters);
  const { data, error, isLoading } = useGetPayoutsQuery({ status, seller, page, size });
  const apply = (next: TradeFilters<PayoutStatus>) => setFilters({ status: next.status, seller: next.seller });
  const totals = data?.totals;
  const tile = (label: string, amount: number, orders: number, variant: string) => (
    <Col>
      <Card className={`stat-tile shadow-sm border-${variant}`}>
        <Card.Body>
          <div className="stat-value">{formatMoney(amount, totals?.currency, lng)}</div>
          <div className="text-muted small">
            {label} · {t('trade.payouts.orders', { count: orders })}
          </div>
        </Card.Body>
      </Card>
    </Col>
  );
  return (
    <>
      <PageHeader title={t('trade.payouts.title')} icon="bi-cash-stack" />
      <MockBanner />
      <TradeFilterBar filters={filters} statuses={PAYOUT_STATUSES} statusPrefix="trade.payoutStatus" fields={{ seller: true }} onApply={apply} />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {totals && (
        <>
          <Row xs={1} md={3} lg={5} className="g-3 mb-3">
            {tile(t('trade.payoutStatus.PENDING_RELEASE'), totals.pendingAmount, totals.pendingOrders, 'warning')}
            {tile(t('trade.payoutStatus.BLOCKED_DISPUTE'), totals.blockedAmount, totals.blockedOrders, 'danger')}
            {tile(t('trade.payoutStatus.RELEASING'), totals.releasingAmount, totals.releasingOrders, 'info')}
            {tile(t('trade.payoutStatus.SENT'), totals.sentAmount, totals.sentOrders, 'success')}
            {tile(t('trade.payoutStatus.REFUNDED'), totals.refundedAmount, totals.refundedOrders, 'dark')}
          </Row>
          <div className="small text-muted mb-3">
            {t('trade.payouts.feesCollected', { amount: formatMoney(totals.feesCollected, totals.currency, lng) })} ·{' '}
            {t('trade.payouts.feesPending', { amount: formatMoney(totals.feesPending, totals.currency, lng) })}
          </div>
        </>
      )}
      {data && data.page.items.length === 0 && <EmptyState />}
      {data && data.page.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>{t('trade.orders.order')}</th>
                <th>{t('trade.orders.seller')}</th>
                <th>{t('trade.payouts.account')}</th>
                <th className="text-end">{t('trade.orders.payout')}</th>
                <th className="text-end">{t('trade.orders.fee')}</th>
                <th>{t('common.statusLabel')}</th>
                <th>{t('trade.payouts.due')}</th>
              </tr>
            </thead>
            <tbody>
              {data.page.items.map((p) => (
                <tr key={p.orderId}>
                  <td>
                    <Link to={`/orders/${encodeURIComponent(p.orderId)}`} className="fw-semibold text-decoration-none">
                      {p.title ?? p.orderId}
                    </Link>
                    <div>
                      <code className="small">{p.orderId}</code>
                    </div>
                  </td>
                  <td>
                    <Link to={`/users/${p.sellerId}`}>#{p.sellerId}</Link>
                  </td>
                  <td>
                    {p.sellerAccountStatus ? <StatusBadge status={p.sellerAccountStatus} prefix="trade.accountStatus" /> : <span className="text-muted">—</span>}
                    {!p.payoutsEnabled && <div className="small text-danger">{t('trade.payouts.payoutsDisabled')}</div>}
                  </td>
                  <td className="text-end">
                    {formatMoney(p.payoutAmount, p.currency, lng)}
                    <div className="small text-muted">{t('trade.auctions.from', { price: formatMoney(p.agreedPrice, p.currency, lng) })}</div>
                  </td>
                  <td className="text-end text-muted">{formatMoney(p.feeAmount, p.currency, lng)}</td>
                  <td>
                    <StatusBadge status={p.status} prefix="trade.payoutStatus" />
                    <div className="small text-muted">{t(`trade.orderStatus.${p.orderStatus}`, { defaultValue: p.orderStatus })}</div>
                    {p.disputeReason && <div className="small text-danger">{p.disputeReason}</div>}
                  </td>
                  <td className="small text-muted">{formatDateTime(p.releaseDueAt ?? p.settledAt ?? p.releasedAt, lng) || '—'}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      {data && <Pagination page={data.page.page} size={data.page.size} total={data.page.total} onPage={setPage} onSize={setSize} />}
    </>
  );
}
