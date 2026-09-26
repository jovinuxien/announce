import { useState } from 'react';
import { Button, Card, Col, Form, Modal, Row, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { AUCTION_STATUSES, useCancelBidMutation, useGetAuctionQuery, useListAuctionsQuery, type AuctionStatus, type AuctionSummary, type Bid } from 'app/shared/api/auctionsApi';
import { formatCents, formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import StatusBadge from 'app/shared/ui/StatusBadge';
import { usePaging } from 'app/shared/ui/usePaging';
import MockBanner from './MockBanner';

function BidsPanel({ auction, onClose }: { auction: AuctionSummary; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const { data, error, isLoading } = useGetAuctionQuery(auction.announceId);
  const [cancelling, setCancelling] = useState<Bid | null>(null);
  const [reason, setReason] = useState('');
  const [cancel, cancelState] = useCancelBidMutation();
  return (
    <Card className="shadow-sm h-100">
      <Card.Header className="d-flex justify-content-between align-items-center">
        <span>{t('trade.auctions.bidsOf', { title: auction.announceTitle ?? auction.announceId })}</span>
        <Button size="sm" variant="outline-secondary" onClick={onClose}>
          {t('common.actions.close')}
        </Button>
      </Card.Header>
      <Card.Body className="p-0">
        {error ? <ProblemAlert error={error} className="m-3" /> : null}
        {isLoading && <LoadingBlock />}
        {data && data.bids.length === 0 && <EmptyState />}
        {data && data.bids.length > 0 && (
          <Table size="sm" hover className="mb-0">
            <thead>
              <tr>
                <th>{t('trade.auctions.bidder')}</th>
                <th className="text-end">{t('trade.auctions.amount')}</th>
                <th>{t('trade.auctions.placedAt')}</th>
                <th>{t('common.statusLabel')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.bids.map((b) => (
                <tr key={b.id}>
                  <td>#{b.bidderId}</td>
                  <td className="text-end">{formatCents(b.amount, data.currency, i18n.language)}</td>
                  <td className="small text-muted">{formatDateTime(b.placedAt, i18n.language)}</td>
                  <td>
                    <StatusBadge status={b.status} prefix="trade.bidStatus" />
                    {b.cancelReason && <div className="small text-muted">{b.cancelReason}</div>}
                  </td>
                  <td className="text-end">
                    <Button size="sm" variant="outline-danger" disabled={b.status === 'CANCELLED' || b.status === 'RETRACTED'} onClick={() => { setCancelling(b); setReason(''); }}>
                      {t('trade.auctions.cancelBid')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card.Body>
      <Modal show={cancelling !== null} onHide={() => setCancelling(null)} centered>
        <Form
          onSubmit={async (e) => {
            e.preventDefault();
            if (!cancelling || !reason.trim()) return;
            try {
              await cancel({ bidId: cancelling.id, announceId: auction.announceId, body: { reason: reason.trim() } }).unwrap();
              setCancelling(null);
            } catch {
              /* shown */
            }
          }}
        >
          <Modal.Header closeButton>
            <Modal.Title>{t('trade.auctions.cancelBid')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            {cancelState.error ? <ProblemAlert error={cancelState.error} /> : null}
            <Form.Group controlId="bid-reason">
              <Form.Label>{t('trade.reason')}</Form.Label>
              <Form.Control as="textarea" rows={3} autoFocus value={reason} onChange={(e) => setReason(e.target.value)} />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setCancelling(null)}>
              {t('common.actions.cancel')}
            </Button>
            <Button type="submit" variant="danger" disabled={!reason.trim() || cancelState.isLoading}>
              {cancelState.isLoading && <Spinner size="sm" animation="border" className="me-2" />}
              {t('trade.auctions.cancelBid')}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </Card>
  );
}

export default function AuctionsPage() {
  const { t, i18n } = useTranslation();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const status = (params.get('status') ?? '') as AuctionStatus | '';
  const { data, error, isLoading } = useListAuctionsQuery({ status, page, size });
  const [selected, setSelected] = useState<AuctionSummary | null>(null);
  return (
    <>
      <PageHeader
        title={t('trade.auctions.title')}
        icon="bi-hammer"
        actions={
          <Form.Select size="sm" value={status} onChange={(e) => setParam('status', e.target.value)} aria-label={t('common.statusLabel')}>
            <option value="">{t('common.all')}</option>
            {AUCTION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`trade.auctionStatus.${s}`)}
              </option>
            ))}
          </Form.Select>
        }
      />
      <MockBanner />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      <Row className="g-3">
        <Col lg={selected ? 7 : 12}>
          {data && data.items.length === 0 && <EmptyState />}
          {data && data.items.length > 0 && (
            <Card className="shadow-sm">
              <Table hover responsive className="mb-0">
                <thead>
                  <tr>
                    <th>{t('trade.auctions.announce')}</th>
                    <th>{t('trade.auctions.seller')}</th>
                    <th className="text-end">{t('trade.auctions.currentPrice')}</th>
                    <th className="text-end">{t('trade.auctions.bids')}</th>
                    <th>{t('trade.auctions.ends')}</th>
                    <th>{t('common.statusLabel')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((a) => (
                    <tr key={a.announceId} className={selected?.announceId === a.announceId ? 'table-active' : ''} style={{ cursor: 'pointer' }} onClick={() => setSelected(a)}>
                      <td>
                        <div className="fw-semibold">{a.announceTitle}</div>
                        <code className="small">{a.announceId}</code>
                      </td>
                      <td>#{a.sellerId}</td>
                      <td className="text-end">
                        {formatCents(a.currentPrice, a.currency, i18n.language)}
                        <div className="small text-muted">{t('trade.auctions.from', { price: formatCents(a.initialPrice, a.currency, i18n.language) })}</div>
                      </td>
                      <td className="text-end">{a.bidCount}</td>
                      <td className="small text-muted">{formatDateTime(a.endTime, i18n.language)}</td>
                      <td>
                        <StatusBadge status={a.status} prefix="trade.auctionStatus" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card>
          )}
          {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} onSize={setSize} />}
        </Col>
        {selected && (
          <Col lg={5}>
            <BidsPanel auction={selected} onClose={() => setSelected(null)} />
          </Col>
        )}
      </Row>
    </>
  );
}
