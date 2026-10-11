import { useEffect, useState } from 'react';
import { Badge, Col, Nav, Row, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { OrderRow, OrderStatus } from 'app/shared/model/trade';
import { formatDateTime } from 'app/shared/util/dates';
import { formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';
import PayoutsCard from './PayoutsCard';

export const STATUS_VARIANT: Record<OrderStatus, string> = {
  PROPOSED: 'secondary',
  AGREED: 'info',
  PAYMENT_PENDING: 'info',
  PAID: 'primary',
  SHIPPED: 'primary',
  DELIVERED: 'primary',
  RELEASING: 'primary',
  SETTLED: 'success',
  DISPUTED: 'danger',
  REFUNDING: 'warning',
  REFUNDED: 'warning',
  CANCELLED: 'dark',
  DECLINED: 'dark',
};
export const ORDERS_PAGE_SIZE = 50;

/**
 * The member's orders as buyer or as seller (`GET /api/orders?role=&page=&size=`: `announce_order` rows, newest
 * first, a plain array with a 0-based page). The seller tab carries the payout account card, since nothing can be
 * accepted without it. doc/frontend-edges.md §16.
 */
export default function Orders() {
  const { t, i18n } = useTranslation('trade');
  const [params, setParams] = useSearchParams();
  const role = params.get('role') === 'seller' ? 'seller' : 'buyer';
  const me = useAppSelector(s => s.auth.user?.id);
  const [orders, setOrders] = useState<OrderRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setOrders(null);
    tradeApi
      .orders(role, 0, ORDERS_PAGE_SIZE)
      .then(setOrders)
      .catch(e => setError(describeError(e, t('orders.loadFailed'))));
  }, [role, t]);

  return (
    <div>
      <h1 className="h4">{t('orders.title')}</h1>
      <Nav variant="tabs" activeKey={role} onSelect={k => k && setParams(k === 'seller' ? { role: 'seller' } : {})} className="mb-3">
        <Nav.Item>
          <Nav.Link eventKey="buyer">{t('orders.asBuyer')}</Nav.Link>
        </Nav.Item>
        <Nav.Item>
          <Nav.Link eventKey="seller">{t('orders.asSeller')}</Nav.Link>
        </Nav.Item>
      </Nav>
      <Row className="g-3">
        <Col lg={role === 'seller' ? 8 : 12}>
          <ProblemAlert message={error} />
          {orders && orders.length === 0 && <p className="text-muted">{t('orders.empty')}</p>}
          {orders && orders.length > 0 && (
            <Table hover size="sm" responsive className="bg-white" data-testid="orders-table">
              <thead>
                <tr>
                  <th>{t('orders.col.announce')}</th>
                  <th>{t('orders.col.other')}</th>
                  <th>{t('orders.col.amount')}</th>
                  <th>{t('orders.col.status')}</th>
                  <th>{t('orders.col.created')}</th>
                </tr>
              </thead>
              <tbody>
                {orders.map(o => (
                  <tr key={o.id}>
                    <td>
                      <Link to={`/orders/${encodeURIComponent(o.id)}`}>{o.title || o.announceId}</Link>
                      <div className="text-muted small">{t(`door.${o.door}`)}</div>
                    </td>
                    <td className="small">{t('orders.member', { id: me === o.buyerId ? o.sellerId : o.buyerId })}</td>
                    <td>{formatMoney(o.agreedPrice, o.currency, i18n.language)}</td>
                    <td>
                      <Badge bg={STATUS_VARIANT[o.status] ?? 'secondary'}>{t(`orderStatus.${o.status}`)}</Badge>
                    </td>
                    <td className="small">{formatDateTime(o.agreedAt ?? o.addTime, i18n.language)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Col>
        {role === 'seller' && (
          <Col lg={4}>
            <PayoutsCard />
          </Col>
        )}
      </Row>
    </div>
  );
}
