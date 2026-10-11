import { useMemo, useState } from 'react';
import { Button, Card, Col, Form, Modal, Row, Spinner, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AUCTION_STATUSES, useCancelBidMutation, useGetAuctionQuery, useListAuctionsQuery, type AuctionRow, type AuctionStatus, type BidRow } from 'app/shared/api/auctionsApi';
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

function BidsPanel({ auction, onClose }: { auction: AuctionRow; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const { data, error, isLoading } = useGetAuctionQuery(auction.announceId);
  const [cancelling, setCancelling] = useState<BidRow | null>(null);
  const [reason, setReason] = useState('');
  const [cancel, cancelState] = useCancelBidMutation();
  const currency = data?.auction.currency ?? auction.currency;
  return (
    <Card className="shadow-sm h-100">
      <Card.Header className="d-flex justify-content-between align-items-center">
        <span>{t('trade.auctions.bidsOf', { title: auction.title ?? auction.announceId })}</span>
        <Button size="sm" variant="outline-secondary" onClick={onClose}>
          {t('common.actions.close')}
        </Button>
      </Card.Header>
      <Card.Body className="p-0">
        {error ? <ProblemAlert error={error} className="m-3" /> : null}
        {isLoading && <LoadingBlock />}
        {data && (
          <div className="small text-muted px-3 py-2 border-bottom">
            {t('trade.auctions.increment', { amount: formatMoney(data.auction.bidIncrement, currency, i18n.language) })}
            {data.auction.reservePrice != null && <> · {t('trade.auctions.reserve', { amount: formatMoney(data.auction.reservePrice, currency, i18n.language) })}</>}
            {data.auction.snagItPrice != null && <> · {t('trade.auctions.snagIt', { amount: formatMoney(data.auction.snagItPrice, currency, i18n.language) })}</>}
            {data.auction.autoExtendSec ? <> · {t('trade.auctions.autoExtend', { seconds: data.auction.autoExtendSec })}</> : null}
            {data.auction.closedTime && <> · {t('trade.auctions.closedAt', { at: formatDateTime(data.auction.closedTime, i18n.language) })}</>}
          </div>
        )}
        {data && data.bids.length === 0 && <EmptyState />}
        {data && data.bids.length > 0 && (
          <Table size="sm" hover className="mb-0">
            <thead>
              <tr>
                <th>{t('trade.auctions.bidder')}</th>
                <th className="text-end">{t('trade.auctions.amount')}</th>
                <th>{t('trade.auctions.bidTime')}</th>
                <th>{t('common.statusLabel')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.bids.map((b) => (
                <tr key={b.id}>
                  <td>
                    #{b.bidderId}
                    {data.auction.winnerId === b.bidderId && data.auction.winningBidId === b.id && (
                      <i className="bi bi-trophy ms-1 text-warning" title={t('trade.bidStatus.WON')} aria-hidden />
                    )}
                  </td>
                  <td className="text-end">
                    {formatMoney(b.amount, currency, i18n.language)}
                    {b.maxAmount != null && <div className="small text-muted">{t('trade.auctions.upTo', { amount: formatMoney(b.maxAmount, currency, i18n.language) })}</div>}
                  </td>
                  <td className="small text-muted">{formatDateTime(b.bidTime, i18n.language)}</td>
                  <td>
                    <StatusBadge status={b.status} prefix="trade.bidStatus" />
                    {b.cancelReason && (
                      <div className="small text-muted">
                        {b.cancelReason}
                        {b.cancelledBy != null && <> ({t('trade.auctions.cancelledBy', { by: b.cancelledBy })})</>}
                      </div>
                    )}
                  </td>
                  <td className="text-end">
                    <Button
                      size="sm"
                      variant="outline-danger"
                      disabled={data.auction.status !== 'OPEN' || b.status === 'CANCELLED' || b.status === 'RETRACTED'}
                      onClick={() => {
                        setCancelling(b);
                        setReason('');
                      }}
                    >
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
  const { params, page, size, setPage, setSize, setFilters } = usePaging();
  const filters = useMemo(() => tradeFiltersFromSearch(params, AUCTION_STATUSES), [params]);
  const { data, error, isLoading } = useListAuctionsQuery({ ...tradeFiltersToParams(filters), page, size });
  const [selected, setSelected] = useState<AuctionRow | null>(null);
  const apply = (next: TradeFilters<AuctionStatus>) => setFilters({ status: next.status, seller: next.seller, q: next.q });
  return (
    <>
      <PageHeader title={t('trade.auctions.title')} icon="bi-hammer" />
      <MockBanner />
      <TradeFilterBar filters={filters} statuses={AUCTION_STATUSES} statusPrefix="trade.auctionStatus" fields={{ seller: true, q: true }} onApply={apply} />
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
                    <tr key={a.id} className={selected?.id === a.id ? 'table-active' : ''} style={{ cursor: 'pointer' }} onClick={() => setSelected(a)}>
                      <td>
                        <div className="fw-semibold">{a.title ?? a.announceId}</div>
                        <Link to={`/announces/${encodeURIComponent(a.announceId)}`} className="small text-decoration-none" onClick={(e) => e.stopPropagation()}>
                          <code className="small">{a.announceId}</code>
                        </Link>
                      </td>
                      <td>
                        <Link to={`/users/${a.sellerId}`} onClick={(e) => e.stopPropagation()}>#{a.sellerId}</Link>
                      </td>
                      <td className="text-end">
                        {formatMoney(a.currentPrice, a.currency, i18n.language)}
                        <div className="small text-muted">{t('trade.auctions.from', { price: formatMoney(a.initialPrice, a.currency, i18n.language) })}</div>
                      </td>
                      <td className="text-end">{a.bidCount}</td>
                      <td className="small text-muted">{formatDateTime(a.endTime, i18n.language)}</td>
                      <td>
                        <StatusBadge status={a.status} prefix="trade.auctionStatus" />
                        {a.winnerId != null && <div className="small text-muted">{t('trade.auctions.winner', { id: a.winnerId })}</div>}
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
