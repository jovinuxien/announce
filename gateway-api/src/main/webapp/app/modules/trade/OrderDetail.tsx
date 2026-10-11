import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Card, Col, Form, ListGroup, Row, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from 'app/config/http';
import { useAppSelector } from 'app/config/store';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { canConfirmReceipt, canDispute, isAwaitingPayment, isBeforePayment, OrderRow, PaymentRow, ShipmentRequest, ShipmentRow } from 'app/shared/model/trade';
import { formatDateTime } from 'app/shared/util/dates';
import { formatMoney } from 'app/shared/util/money';
import { orderTimeline } from 'app/shared/util/orderTimeline';
import { ROW_RETRIES, ROW_SETTLE_MS } from 'app/shared/util/settle';
import { describeError } from 'app/shared/util/problem';
import { STATUS_VARIANT } from './Orders';

export const ORDER_POLL_MS = 5000;
const problemCode = (e: unknown): string | undefined => (e instanceof ApiError ? (e.problem as { code?: string } | undefined)?.code : undefined);

/**
 * One order of the signed-in member (doc/frontend-edges.md §16): the `announce_order` row plus the payment and
 * shipment rows (404 = none yet), a timeline derived from their timestamps, and the actions the order module's
 * rules allow for the role: the seller accepts or declines a PROPOSED order; either party cancels before payment;
 * the buyer pays through the provider (nothing to click until a real provider is wired); the seller books the
 * shipment on PAID; the buyer confirms receipt on SHIPPED or DELIVERED and may open a dispute while paid. The
 * screen polls every 5 s while the order is still moving, since payment and carrier facts arrive by webhook.
 */
export default function OrderDetail() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation('trade');
  const me = useAppSelector(s => s.auth.user?.id);
  const [order, setOrder] = useState<OrderRow | null>(null);
  const [payment, setPayment] = useState<PaymentRow | null>(null);
  const [shipment, setShipment] = useState<ShipmentRow | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notPayable, setNotPayable] = useState(false);
  const [reason, setReason] = useState('');
  const [booking, setBooking] = useState<ShipmentRequest>({ service: 'MyPack Collect', parcelSize: 'M', fromAddress: '', toAddress: '' });
  const [busy, setBusy] = useState(false);
  const lang = i18n.language;

  // the row of a just-created order (Buy navigates here at once) is projected a moment later: retry quietly first
  const misses = useRef(0);
  const settle = useRef<number | undefined>(undefined);
  const load = useCallback(async () => {
    try {
      const [o, p, s] = await Promise.all([tradeApi.order(id), tradeApi.payment(id), tradeApi.shipment(id)]);
      misses.current = 0;
      setOrder(o);
      setPayment(p);
      setShipment(s);
    } catch (e) {
      misses.current += 1;
      if (misses.current > ROW_RETRIES) setError(describeError(e, t('order.notFound')));
    }
  }, [id, t]);
  const refetchSoon = useCallback(() => {
    window.clearTimeout(settle.current);
    settle.current = window.setTimeout(() => void load(), ROW_SETTLE_MS);
  }, [load]);
  useEffect(() => {
    misses.current = 0;
    void load();
    return () => window.clearTimeout(settle.current);
  }, [load]);
  useEffect(() => {
    if (order || error) return;
    const timer = window.setInterval(() => void load(), ROW_SETTLE_MS);
    return () => window.clearInterval(timer);
  }, [order, error, load]);

  const moving = !!order && !['SETTLED', 'REFUNDED', 'CANCELLED', 'DECLINED'].includes(order.status);
  useEffect(() => {
    if (!moving) return;
    const timer = window.setInterval(() => void load(), ORDER_POLL_MS);
    return () => window.clearInterval(timer);
  }, [moving, load]);

  if (error && !order) return <ProblemAlert message={error} />;
  if (!order)
    return (
      <div className="text-center py-5">
        <Spinner animation="border" />
      </div>
    );

  const buyer = me != null && me === order.buyerId;
  const seller = me != null && me === order.sellerId;
  const timeline = orderTimeline(order, payment, shipment);

  const run = async (fn: () => Promise<unknown>, fail: string) => {
    setBusy(true);
    setError(null);
    setNotPayable(false);
    try {
      await fn();
      await load();
      refetchSoon(); // the projection may not have written the new state yet
    } catch (e) {
      const message = describeError(e, fail);
      if (problemCode(e) === 'seller-not-payable') setNotPayable(true);
      await load(); // a refusal usually means the row moved on; show where it is now
      setError(message);
    } finally {
      setBusy(false);
    }
  };
  const book = (e: FormEvent) => {
    e.preventDefault();
    void run(() => tradeApi.requestShipment(order.id, booking), t('order.actionFailed'));
  };

  return (
    <Row className="g-4">
      <Col lg={7}>
        <Card>
          <Card.Body>
            <div className="d-flex justify-content-between align-items-start">
              <div>
                <h1 className="h4">{order.title || order.announceId}</h1>
                <div className="text-muted small">
                  <Link to={`/announce/${encodeURIComponent(order.announceId)}`}>{t('order.viewAnnounce')}</Link> · {t(`door.${order.door}`)} ·{' '}
                  {buyer ? t('order.seller', { id: order.sellerId }) : t('order.buyer', { id: order.buyerId })}
                </div>
              </div>
              <Badge bg={STATUS_VARIANT[order.status] ?? 'secondary'} data-testid="order-status">
                {t(`orderStatus.${order.status}`)}
              </Badge>
            </div>
            <div className="an-price fs-4 mt-2">{formatMoney(order.agreedPrice, order.currency, lang)}</div>
            {(order.feeAmount > 0 || order.agreedAt) && ( // the fee is computed when the deal is agreed; a proposed order carries none yet
              <div className="small text-muted">
                {seller
                  ? t('order.payout', { amount: formatMoney(order.payoutAmount, order.currency, lang), fee: formatMoney(order.feeAmount, order.currency, lang) })
                  : t('order.fee', { amount: formatMoney(order.feeAmount, order.currency, lang) })}
              </div>
            )}
            {order.status === 'DISPUTED' && order.disputeReason && <div className="small text-danger mt-1">{t('order.disputeOpen', { reason: order.disputeReason })}</div>}
            <ProblemAlert message={error} onClose={() => setError(null)} />
            {notPayable && (
              <p className="small mb-2">
                <Link to="/orders?role=seller">{t('order.setUpPayouts')}</Link>
              </p>
            )}
            <div className="d-flex flex-wrap gap-2 mt-3" data-testid="order-actions">
              {seller && order.status === 'PROPOSED' && (
                <>
                  <Button disabled={busy} onClick={() => void run(() => tradeApi.accept(order.id), t('order.actionFailed'))}>
                    {t('order.accept')}
                  </Button>
                  <Button variant="outline-danger" disabled={busy} onClick={() => void run(() => tradeApi.decline(order.id), t('order.actionFailed'))}>
                    {t('order.decline')}
                  </Button>
                </>
              )}
              {buyer && order.status === 'PROPOSED' && <span className="small text-muted align-self-center">{t('order.waitingForSeller')}</span>}
              {buyer && isAwaitingPayment(order.status) && (
                <span className="small text-muted align-self-center" data-testid="pay-hint">
                  {t('order.payThroughProvider', { provider: payment?.provider ?? '…' })}
                </span>
              )}
              {seller && isAwaitingPayment(order.status) && <span className="small text-muted align-self-center">{t('order.waitingForPayment')}</span>}
              {buyer && canConfirmReceipt(order.status) && (
                <Button variant="success" disabled={busy} onClick={() => void run(() => tradeApi.confirmReceipt(order.id), t('order.actionFailed'))}>
                  {t('order.confirmReceipt')}
                </Button>
              )}
              {(buyer || seller) && isBeforePayment(order.status) && (
                <Button variant="outline-secondary" disabled={busy} onClick={() => void run(() => tradeApi.cancel(order.id), t('order.actionFailed'))}>
                  {t('order.cancel')}
                </Button>
              )}
            </div>
            {seller && order.status === 'PAID' && (!shipment || shipment.status === 'OPEN') && (
              <Form className="mt-3" onSubmit={book} data-testid="shipment-form">
                <h2 className="h6">{t('order.ship')}</h2>
                <Row className="g-2">
                  <Col sm={6}>
                    <Form.Group controlId="ship-service">
                      <Form.Label className="small mb-0">{t('order.shipService')}</Form.Label>
                      <Form.Control size="sm" value={booking.service} onChange={e => setBooking({ ...booking, service: e.target.value })} required />
                    </Form.Group>
                  </Col>
                  <Col sm={6}>
                    <Form.Group controlId="ship-parcel">
                      <Form.Label className="small mb-0">{t('order.shipParcelSize')}</Form.Label>
                      <Form.Select size="sm" value={booking.parcelSize} onChange={e => setBooking({ ...booking, parcelSize: e.target.value })}>
                        {['S', 'M', 'L', 'XL'].map(s => (
                          <option key={s} value={s}>
                            {s}
                          </option>
                        ))}
                      </Form.Select>
                    </Form.Group>
                  </Col>
                  <Col sm={6}>
                    <Form.Group controlId="ship-from">
                      <Form.Label className="small mb-0">{t('order.shipFrom')}</Form.Label>
                      <Form.Control size="sm" value={booking.fromAddress} onChange={e => setBooking({ ...booking, fromAddress: e.target.value })} required />
                    </Form.Group>
                  </Col>
                  <Col sm={6}>
                    <Form.Group controlId="ship-to">
                      <Form.Label className="small mb-0">{t('order.shipTo')}</Form.Label>
                      <Form.Control size="sm" value={booking.toAddress} onChange={e => setBooking({ ...booking, toAddress: e.target.value })} required />
                    </Form.Group>
                  </Col>
                </Row>
                <Button type="submit" size="sm" className="mt-2" disabled={busy}>
                  {t('order.book')}
                </Button>
              </Form>
            )}
            {buyer && canDispute(order.status) && (
              <Form
                className="mt-3"
                onSubmit={e => {
                  e.preventDefault();
                  void run(() => tradeApi.dispute(order.id, reason), t('order.actionFailed'));
                }}
              >
                <Form.Group controlId="dispute-reason">
                  <Form.Label className="small mb-0">{t('order.disputeReason')}</Form.Label>
                  <Form.Control size="sm" value={reason} onChange={e => setReason(e.target.value)} required minLength={10} />
                </Form.Group>
                <Button type="submit" variant="outline-danger" size="sm" className="mt-2" disabled={busy || reason.trim().length < 10}>
                  {t('order.dispute')}
                </Button>
              </Form>
            )}
          </Card.Body>
        </Card>
      </Col>
      <Col lg={5}>
        <Card className="mb-3" data-testid="payment-card">
          <Card.Header>{t('order.payment')}</Card.Header>
          <Card.Body className="small">
            {payment ? (
              <>
                <div>
                  <Badge bg="info">{t(`paymentStatus.${payment.status}`)}</Badge> {formatMoney(payment.amount, payment.currency, lang)} · {payment.provider}
                </div>
                {payment.intentRef && <div className="text-muted">{t('order.paymentRef', { ref: payment.intentRef })}</div>}
                {payment.failureReason && <div className="text-danger">{payment.failureReason}</div>}
              </>
            ) : (
              <span className="text-muted">{t('order.noPayment')}</span>
            )}
          </Card.Body>
        </Card>
        <Card className="mb-3" data-testid="shipment-card">
          <Card.Header>{t('order.shipment')}</Card.Header>
          <Card.Body className="small">
            {shipment && shipment.status !== 'OPEN' ? (
              <>
                <div>
                  {shipment.carrier} {shipment.service && `· ${shipment.service}`} {shipment.trackingNumber && <code>{shipment.trackingNumber}</code>}
                </div>
                <div>
                  <Badge bg="info">{t(`shipmentStatus.${shipment.status}`)}</Badge> {formatDateTime(shipment.lastEventAt ?? shipment.requestedAt, lang)}
                </div>
                {shipment.lastCarrierEvent && <div className="text-muted">{shipment.lastCarrierEvent}</div>}
                {seller && shipment.labelUrl && (
                  <a href={shipment.labelUrl} target="_blank" rel="noreferrer">
                    {t('order.label')}
                  </a>
                )}
              </>
            ) : (
              <span className="text-muted">{t('order.noShipment')}</span>
            )}
          </Card.Body>
        </Card>
        <Card>
          <Card.Header>{t('order.timeline')}</Card.Header>
          <ListGroup variant="flush" data-testid="timeline">
            {timeline.length === 0 && <ListGroup.Item className="small text-muted">{t('order.noTimeline')}</ListGroup.Item>}
            {timeline.map((e, i) => (
              <ListGroup.Item key={i} className="small d-flex justify-content-between">
                <span>
                  <strong>{t(`timeline.${e.key}`)}</strong> {e.detail && <span className="text-muted">{e.detail}</span>}
                </span>
                <span className="text-muted text-nowrap ms-2">{formatDateTime(e.at, lang)}</span>
              </ListGroup.Item>
            ))}
          </ListGroup>
        </Card>
      </Col>
    </Row>
  );
}
