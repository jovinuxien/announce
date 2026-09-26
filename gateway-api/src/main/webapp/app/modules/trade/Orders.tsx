import { useEffect, useState } from 'react';
import { Badge, Nav, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { OrderView } from 'app/shared/model/trade';
import { formatDateTime } from 'app/shared/util/dates';
import { centsToAmount, formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';

export const STATUS_VARIANT: Record<string, string> = {
  REQUESTED: 'secondary', AGREED: 'info', PAID: 'primary', SHIPPED: 'primary', DELIVERED: 'primary', SETTLED: 'success',
  DISPUTED: 'danger', CANCELLED: 'dark', REFUNDED: 'warning', EXPIRED: 'dark',
};

export default function Orders() {
  const { t, i18n } = useTranslation('trade');
  const [role, setRole] = useState<'buyer' | 'seller'>('buyer');
  const [orders, setOrders] = useState<OrderView[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    tradeApi.orders(role).then(setOrders).catch(e => setError(describeError(e, t('orders.loadFailed'))));
  }, [role, t]);

  return (
    <div>
      <h1 className="h4">{t('orders.title')}</h1>
      <Nav variant="tabs" activeKey={role} onSelect={k => k && setRole(k as 'buyer' | 'seller')} className="mb-3">
        <Nav.Item><Nav.Link eventKey="buyer">{t('orders.asBuyer')}</Nav.Link></Nav.Item>
        <Nav.Item><Nav.Link eventKey="seller">{t('orders.asSeller')}</Nav.Link></Nav.Item>
      </Nav>
      <ProblemAlert message={error} />
      {orders.length === 0 ? (
        <p className="text-muted">{t('orders.empty')}</p>
      ) : (
        <Table hover size="sm" responsive className="bg-white">
          <thead>
            <tr><th>{t('orders.col.announce')}</th><th>{t('orders.col.amount')}</th><th>{t('orders.col.status')}</th><th>{t('orders.col.created')}</th></tr>
          </thead>
          <tbody>
            {orders.map(o => (
              <tr key={o.id}>
                <td><Link to={`/orders/${encodeURIComponent(o.id)}`}>{o.announceTitle}</Link></td>
                <td>{formatMoney(centsToAmount(o.amountCents), o.currency, i18n.language)}</td>
                <td><Badge bg={STATUS_VARIANT[o.status] ?? 'secondary'}>{t(`orderStatus.${o.status}`)}</Badge></td>
                <td>{formatDateTime(o.createdAt, i18n.language)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}
