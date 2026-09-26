import { useState } from 'react';
import { Button, Card, Col, Form, ListGroup, Modal, Row, Spinner, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useGetOrderQuery, useResolveDisputeMutation, type DisputeResolution } from 'app/shared/api/ordersApi';
import { formatCents, formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import StatusBadge from 'app/shared/ui/StatusBadge';
import MockBanner from './MockBanner';

export default function OrderDetailPage() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation();
  const lng = i18n.language;
  const { data: o, error, isLoading } = useGetOrderQuery(id, { skip: !id });
  const [resolution, setResolution] = useState<DisputeResolution | null>(null);
  const [reason, setReason] = useState('');
  const [resolve, resolveState] = useResolveDisputeMutation();

  const submit = async () => {
    if (!o || !resolution || !reason.trim()) return;
    try {
      await resolve({ id: o.id, body: { resolution, reason: reason.trim() } }).unwrap();
      setResolution(null);
      setReason('');
    } catch {
      /* shown */
    }
  };

  return (
    <>
      <PageHeader
        title={o?.announceTitle ?? t('trade.orders.detail')}
        icon="bi-receipt"
        subtitle={
          o && (
            <span className="d-inline-flex gap-2 align-items-center">
              <StatusBadge status={o.status} prefix="trade.orderStatus" />
              <code>{o.id}</code>
            </span>
          )
        }
        actions={
          <>
            <Link to="/orders" className="btn btn-outline-secondary btn-sm">
              <i className="bi bi-arrow-left me-1" aria-hidden />
              {t('common.actions.back')}
            </Link>
            {o?.status === 'DISPUTED' && (
              <>
                <Button size="sm" variant="success" onClick={() => setResolution('RELEASE')}>
                  {t('trade.orders.release')}
                </Button>
                <Button size="sm" variant="danger" onClick={() => setResolution('REFUND')}>
                  {t('trade.orders.refund')}
                </Button>
              </>
            )}
          </>
        }
      />
      <MockBanner />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {o && (
        <Row className="g-3">
          <Col lg={6}>
            <Card className="shadow-sm mb-3">
              <Card.Header>{t('trade.orders.summary')}</Card.Header>
              <Card.Body>
                <Table size="sm" className="mb-0">
                  <tbody>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.door')}</th><td>{t(`trade.door.${o.door}`)}</td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.announce')}</th><td><Link to={`/announces/${encodeURIComponent(o.announceId)}`}>{o.announceId}</Link></td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.buyer')}</th><td><Link to={`/users/${o.buyerId}`}>#{o.buyerId}</Link></td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.seller')}</th><td><Link to={`/users/${o.sellerId}`}>#{o.sellerId}</Link></td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.price')}</th><td>{formatCents(o.agreedPrice, o.currency, lng)}</td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.fee')}</th><td>{formatCents(o.feeAmount, o.currency, lng)}</td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.payout')}</th><td>{formatCents(o.payoutAmount, o.currency, lng)}</td></tr>
                    <tr><th className="text-muted fw-normal">{t('trade.orders.label')}</th><td>{o.labelPrice != null ? formatCents(o.labelPrice, o.currency, lng) : '—'}</td></tr>
                    {o.disputeReason && <tr><th className="text-muted fw-normal">{t('trade.orders.disputeReason')}</th><td className="text-danger">{o.disputeReason}</td></tr>}
                  </tbody>
                </Table>
              </Card.Body>
            </Card>
            <Row className="g-3">
              <Col md={6}>
                <Card className="shadow-sm h-100">
                  <Card.Header>{t('trade.orders.payment')}</Card.Header>
                  <Card.Body className="small">
                    {o.payment ? (
                      <>
                        <div><StatusBadge status={o.payment.status} prefix="trade.paymentStatus" /></div>
                        <div className="mt-2">{o.payment.provider} · <code>{o.payment.intentRef}</code></div>
                        <div>{t('trade.orders.confirmedAt')}: {formatDateTime(o.payment.confirmedAt, lng) || '—'}</div>
                        <div>{t('trade.orders.releasedAt')}: {formatDateTime(o.payment.releasedAt, lng) || '—'}</div>
                        <div>{t('trade.orders.refundedAt')}: {formatDateTime(o.payment.refundedAt, lng) || '—'}</div>
                      </>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </Card.Body>
                </Card>
              </Col>
              <Col md={6}>
                <Card className="shadow-sm h-100">
                  <Card.Header>{t('trade.orders.shipment')}</Card.Header>
                  <Card.Body className="small">
                    {o.shipment ? (
                      <>
                        <div><StatusBadge status={o.shipment.status} prefix="trade.shipmentStatus" /></div>
                        <div className="mt-2">{o.shipment.carrier} {o.shipment.service} · <code>{o.shipment.trackingNumber}</code></div>
                        <div>{t('trade.orders.lastEvent')}: {o.shipment.lastCarrierEvent ?? '—'} {formatDateTime(o.shipment.lastEventAt, lng)}</div>
                        <div>{t('trade.orders.deliveredAt')}: {formatDateTime(o.shipment.deliveredAt, lng) || '—'}</div>
                        {o.shipment.labelUrl && <a href={o.shipment.labelUrl} target="_blank" rel="noreferrer noopener">{t('trade.orders.labelLink')}</a>}
                      </>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </Card.Body>
                </Card>
              </Col>
            </Row>
          </Col>
          <Col lg={6}>
            <Card className="shadow-sm">
              <Card.Header>{t('trade.orders.timeline')}</Card.Header>
              <ListGroup variant="flush">
                {o.timeline.map((e, i) => (
                  <ListGroup.Item key={`${e.at}-${i}`} className="d-flex gap-3">
                    <span className="text-muted small text-nowrap" style={{ width: 150 }}>{formatDateTime(e.at, lng)}</span>
                    <span>
                      <span className="fw-semibold">{e.type}</span>
                      {e.detail && <div className="small text-muted">{e.detail}</div>}
                    </span>
                  </ListGroup.Item>
                ))}
                {o.timeline.length === 0 && <ListGroup.Item className="text-muted">—</ListGroup.Item>}
              </ListGroup>
            </Card>
          </Col>
        </Row>
      )}
      <Modal show={resolution !== null} onHide={() => setResolution(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title>{resolution === 'RELEASE' ? t('trade.orders.release') : t('trade.orders.refund')}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {resolveState.error ? <ProblemAlert error={resolveState.error} /> : null}
          <p className="small text-muted">{t('trade.orders.resolveHint')}</p>
          <Form.Group controlId="resolve-reason">
            <Form.Label>{t('trade.reason')}</Form.Label>
            <Form.Control as="textarea" rows={3} autoFocus value={reason} onChange={(e) => setReason(e.target.value)} />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={() => setResolution(null)}>
            {t('common.actions.cancel')}
          </Button>
          <Button variant={resolution === 'RELEASE' ? 'success' : 'danger'} disabled={!reason.trim() || resolveState.isLoading} onClick={submit}>
            {resolveState.isLoading && <Spinner size="sm" animation="border" className="me-2" />}
            {t('common.actions.confirm')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
