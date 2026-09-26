import { useCallback, useEffect, useState } from 'react';
import { Badge, Button, Card, Col, Form, ListGroup, Row, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { OrderView, ShipmentView } from 'app/shared/model/trade';
import { formatDateTime } from 'app/shared/util/dates';
import { centsToAmount, formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';
import { STATUS_VARIANT } from './Orders';

/** Phase 2: one order with its timeline; the buyer pays, confirms receipt or disputes, the seller accepts and ships. */
export default function OrderDetail() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation('trade');
  const user = useAppSelector(s => s.auth.user);
  const [order, setOrder] = useState<OrderView | null>(null);
  const [shipment, setShipment] = useState<ShipmentView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const lang = i18n.language;

  const load = useCallback(() => {
    tradeApi.order(id).then(setOrder).catch(e => setError(describeError(e, t('order.notFound'))));
    tradeApi.shipment(id).then(setShipment).catch(() => setShipment(null));
  }, [id, t]);
  useEffect(() => load(), [load]);

  if (error && !order) return <ProblemAlert message={error} />;
  if (!order) return <div className="text-center py-5"><Spinner animation="border" /></div>;

  const buyer = user?.id === order.buyerId;
  const seller = user?.id === order.sellerId;

  const run = async (fn: () => Promise<unknown>, fail: string) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      load();
    } catch (e) {
      setError(describeError(e, fail));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Row className="g-4">
      <Col lg={7}>
        <Card>
          <Card.Body>
            <div className="d-flex justify-content-between align-items-start">
              <div>
                <h1 className="h4">{order.announceTitle}</h1>
                <div className="text-muted small"><Link to={`/announce/${encodeURIComponent(order.announceId)}`}>{t('order.viewAnnounce')}</Link></div>
              </div>
              <Badge bg={STATUS_VARIANT[order.status] ?? 'secondary'}>{t(`orderStatus.${order.status}`)}</Badge>
            </div>
            <div className="an-price fs-4 mt-2">{formatMoney(centsToAmount(order.amountCents), order.currency, lang)}</div>
            <div className="small text-muted">{t('order.fee', { amount: formatMoney(centsToAmount(order.feeCents), order.currency, lang) })}</div>
            {order.payBy && order.status === 'AGREED' && <div className="small">{t('order.payBy', { date: formatDateTime(order.payBy, lang) })}</div>}
            {order.shipBy && order.status === 'PAID' && <div className="small">{t('order.shipBy', { date: formatDateTime(order.shipBy, lang) })}</div>}
            <ProblemAlert message={error} onClose={() => setError(null)} />
            <div className="d-flex flex-wrap gap-2 mt-3">
              {seller && order.status === 'REQUESTED' && (
                <>
                  <Button disabled={busy} onClick={() => run(() => tradeApi.accept(order.id), t('order.actionFailed'))}>{t('order.accept')}</Button>
                  <Button variant="outline-danger" disabled={busy} onClick={() => run(() => tradeApi.decline(order.id), t('order.actionFailed'))}>{t('order.decline')}</Button>
                </>
              )}
              {buyer && order.status === 'AGREED' && (
                <Button variant="success" disabled={busy} onClick={() => run(() => tradeApi.fakePay(order.id), t('order.actionFailed'))} title={t('order.payHint')}>
                  {t('order.pay')}
                </Button>
              )}
              {seller && order.status === 'PAID' && !shipment && (
                <Button disabled={busy} onClick={() => run(() => tradeApi.requestShipment(order.id), t('order.actionFailed'))}>{t('order.ship')}</Button>
              )}
              {buyer && (order.status === 'SHIPPED' || order.status === 'DELIVERED') && (
                <Button variant="success" disabled={busy} onClick={() => run(() => tradeApi.confirmReceipt(order.id), t('order.actionFailed'))}>{t('order.confirmReceipt')}</Button>
              )}
              {(buyer || seller) && ['REQUESTED', 'AGREED'].includes(order.status) && (
                <Button variant="outline-secondary" disabled={busy} onClick={() => run(() => tradeApi.cancel(order.id), t('order.actionFailed'))}>{t('order.cancel')}</Button>
              )}
            </div>
            {buyer && ['PAID', 'SHIPPED', 'DELIVERED'].includes(order.status) && (
              <Form className="mt-3" onSubmit={e => { e.preventDefault(); void run(() => tradeApi.dispute(order.id, { reason }), t('order.actionFailed')); }}>
                <Form.Group controlId="dispute-reason">
                  <Form.Label>{t('order.disputeReason')}</Form.Label>
                  <Form.Control value={reason} onChange={e => setReason(e.target.value)} required minLength={10} />
                </Form.Group>
                <Button type="submit" variant="outline-danger" size="sm" className="mt-2" disabled={busy}>{t('order.dispute')}</Button>
              </Form>
            )}
          </Card.Body>
        </Card>
      </Col>
      <Col lg={5}>
        <Card className="mb-3">
          <Card.Header>{t('order.shipment')}</Card.Header>
          <Card.Body className="small">
            {shipment ? (
              <>
                <div>{shipment.carrier} {shipment.trackingNumber && <code>{shipment.trackingNumber}</code>}</div>
                <div><Badge bg="info">{t(`shipmentStatus.${shipment.status}`)}</Badge> {formatDateTime(shipment.updatedAt, lang)}</div>
              </>
            ) : (
              <span className="text-muted">{t('order.noShipment')}</span>
            )}
          </Card.Body>
        </Card>
        <Card>
          <Card.Header>{t('order.timeline')}</Card.Header>
          <ListGroup variant="flush">
            {order.timeline.map((e, i) => (
              <ListGroup.Item key={i} className="small d-flex justify-content-between">
                <span><Badge bg={STATUS_VARIANT[e.status] ?? 'secondary'} className="me-2">{t(`orderStatus.${e.status}`)}</Badge>{e.note}</span>
                <span className="text-muted">{formatDateTime(e.at, lang)}</span>
              </ListGroup.Item>
            ))}
          </ListGroup>
        </Card>
      </Col>
    </Row>
  );
}
