import { Card, Col, Form, Row, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PAYOUT_STATUSES, useGetPayoutsQuery, type PayoutStatus } from 'app/shared/api/payoutsApi';
import { formatCents, formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import StatusBadge from 'app/shared/ui/StatusBadge';
import { usePaging } from 'app/shared/ui/usePaging';
import MockBanner from './MockBanner';

export default function PayoutsPage() {
  const { t, i18n } = useTranslation();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const status = (params.get('status') ?? '') as PayoutStatus | '';
  const { data, error, isLoading } = useGetPayoutsQuery({ status, page, size });
  const totals = data?.totals;
  const tile = (label: string, value: string, variant: string) => (
    <Col>
      <Card className={`stat-tile shadow-sm border-${variant}`}>
        <Card.Body>
          <div className="stat-value">{value}</div>
          <div className="text-muted small">{label}</div>
        </Card.Body>
      </Card>
    </Col>
  );
  return (
    <>
      <PageHeader
        title={t('trade.payouts.title')}
        icon="bi-cash-stack"
        actions={
          <Form.Select size="sm" value={status} onChange={(e) => setParam('status', e.target.value)} aria-label={t('common.statusLabel')}>
            <option value="">{t('common.all')}</option>
            {PAYOUT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`trade.payoutStatus.${s}`)}
              </option>
            ))}
          </Form.Select>
        }
      />
      <MockBanner />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {totals && (
        <Row xs={1} md={4} className="g-3 mb-3">
          {tile(t('trade.payouts.pending'), formatCents(totals.pendingAmount, totals.currency, i18n.language), 'warning')}
          {tile(t('trade.payouts.sent'), formatCents(totals.sentAmount, totals.currency, i18n.language), 'success')}
          {tile(t('trade.payouts.blocked'), formatCents(totals.blockedAmount, totals.currency, i18n.language), 'danger')}
          {tile(t('trade.payouts.fees'), formatCents(totals.feesCollected, totals.currency, i18n.language), 'primary')}
        </Row>
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
                <th>{t('trade.payouts.ref')}</th>
              </tr>
            </thead>
            <tbody>
              {data.page.items.map((p) => (
                <tr key={p.paymentId}>
                  <td>
                    <Link to={`/orders/${encodeURIComponent(p.orderId)}`}>
                      <code>{p.orderId}</code>
                    </Link>
                  </td>
                  <td>#{p.sellerId}</td>
                  <td>
                    <StatusBadge status={p.sellerAccountStatus} prefix="trade.accountStatus" />
                  </td>
                  <td className="text-end">{formatCents(p.payoutAmount, p.currency, i18n.language)}</td>
                  <td className="text-end text-muted">{formatCents(p.feeAmount, p.currency, i18n.language)}</td>
                  <td>
                    <StatusBadge status={p.status} prefix="trade.payoutStatus" />
                  </td>
                  <td className="small text-muted">{formatDateTime(p.releaseDueAt ?? p.releasedAt, i18n.language)}</td>
                  <td>
                    <code className="small">{p.payoutRef ?? '—'}</code>
                  </td>
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
