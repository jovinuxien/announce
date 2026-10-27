import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Card, Form, InputGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useAppSelector } from 'app/config/store';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { AnnounceResponse } from 'app/shared/model/announce';
import { AuctionView, isAuctionOpen, minimumNextBid, myStandingBid } from 'app/shared/model/trade';
import { countdown, formatDateTime } from 'app/shared/util/dates';
import { amountToCents, formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';
import { ROW_SETTLE_MS } from 'app/shared/util/settle';

export const AUCTION_POLL_MS = 15000;

/**
 * The auction of an AUCTION goods announce (`GET /api/auctions/{announceId}`: the `announce_auction` row and its
 * bids): current price, increment and minimum next bid, bid count, countdown to the end time, the member's own
 * standing bid, the outcome once closed. Bids and buy-now go in cents; the panel refetches the row afterwards and
 * every 15 s while open. Three time states: before {@code startTime} the window is announced and the form is disabled,
 * between start and end bids are taken, after the end (or once closed) the outcome shows. A 404 means the order module
 * has no auction row for the announce yet (the seller has no enabled payout account): the schedule the announce
 * carries is shown instead so a visitor still sees when bidding is planned.
 */
export default function AuctionPanel({ announce, mine }: { announce: AnnounceResponse; mine: boolean }) {
  const { t, i18n } = useTranslation('trade');
  const status = useAppSelector(s => s.auth.status);
  const me = useAppSelector(s => s.auth.user?.id);
  const [view, setView] = useState<AuctionView | null>(null);
  const [missing, setMissing] = useState(false);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(new Date());
  const lang = i18n.language;

  // `GET /api/auctions/{id}` needs a member token at the edge, so a visitor sees the invitation to log in instead
  const signedIn = status === 'authenticated';
  const load = useCallback(
    () =>
      tradeApi
        .auction(announce.id)
        .then(v => {
          setView(v);
          setMissing(false);
        })
        .catch(e => {
          if ((e as { status?: number }).status === 404) setMissing(true);
          else setError(describeError(e, t('auction.unavailable')));
        }),
    [announce.id, t]
  );

  const settle = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (signedIn) void load();
    return () => window.clearTimeout(settle.current);
  }, [load, signedIn]);

  const open = view ? isAuctionOpen(view.auction, now) : false;
  useEffect(() => {
    if (!open) return;
    const tick = window.setInterval(() => setNow(new Date()), 30000);
    const poll = window.setInterval(() => void load(), AUCTION_POLL_MS);
    return () => {
      window.clearInterval(tick);
      window.clearInterval(poll);
    };
  }, [open, load]);

  if (!signedIn) {
    return (
      <Card className="mt-3 border-primary" data-testid="auction-panel">
        <Card.Body>
          <h2 className="h6">{t('auction.title')}</h2>
          <div className="small">{t('auction.loginToBid')}</div>
        </Card.Body>
      </Card>
    );
  }
  if (missing) {
    const g = announce.goods;
    return (
      <Card className="mt-3 border-primary" data-testid="auction-panel">
        <Card.Body>
          <h2 className="h6">{t('auction.title')}</h2>
          <div className="small text-muted">{t('auction.notOpenYet')}</div>
          {g?.auctionStartsAt && g?.auctionEndsAt && (
            <div className="small" data-testid="auction-planned">
              {t('auction.planned', { start: formatDateTime(g.auctionStartsAt, lang), end: formatDateTime(g.auctionEndsAt, lang) })}
            </div>
          )}
          <div className="small text-muted">{t('auction.notOpenYetWhy')}</div>
        </Card.Body>
      </Card>
    );
  }
  if (!view) return <ProblemAlert message={error} />;
  const { auction, bids } = view;
  const minimum = minimumNextBid(auction);
  const own = myStandingBid(bids, me);
  const won = auction.status === 'CLOSED_WON' || auction.status === 'CLOSED_SNAGGED';
  // the third time state: OPEN but the window has not started yet
  const notStarted = auction.status === 'OPEN' && new Date(auction.startTime).getTime() > now.getTime();
  const timeline = notStarted
    ? t('auction.startsAt', { date: formatDateTime(auction.startTime, lang) })
    : open
      ? `${t('auction.startedAt', { date: formatDateTime(auction.startTime, lang) })} · ${t('auction.endsIn', { time: countdown(auction.endTime, now) })}`
      : t('auction.endedAt', { date: formatDateTime(auction.closedTime ?? auction.endTime, lang) });

  const run = async (fn: () => Promise<unknown>, fail: string) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      await load();
      window.clearTimeout(settle.current);
      settle.current = window.setTimeout(() => void load(), ROW_SETTLE_MS); // the row follows the aggregate a moment later
    } catch (err) {
      setError(describeError(err, fail));
    } finally {
      setBusy(false);
    }
  };
  const bid = (e: FormEvent) => {
    e.preventDefault();
    const cents = amountToCents(Number(amount));
    if (!cents) return;
    void run(() => tradeApi.bid(announce.id, { amountCents: cents }).then(() => setAmount('')), t('auction.bidFailed'));
  };

  return (
    <Card className="mt-3 border-primary" data-testid="auction-panel">
      <Card.Body>
        <div className="d-flex justify-content-between align-items-start">
          <h2 className="h6">{t('auction.title')}</h2>
          <Badge bg={open ? 'primary' : notStarted ? 'info' : 'secondary'} data-testid="auction-status">
            {notStarted ? t('auctionStatus.NOT_STARTED') : t(`auctionStatus.${auction.status}`)}
          </Badge>
        </div>
        <div className="an-price fs-4" data-testid="auction-price">
          {formatMoney(auction.currentPrice, auction.currency, lang)}
        </div>
        <div className="small text-muted" data-testid="auction-timeline">
          {t('auction.bids', { count: auction.bidCount })} · {timeline}
        </div>
        {notStarted && !mine && (
          <Form className="mt-2" data-testid="bid-form-disabled">
            <InputGroup>
              <Form.Control type="number" disabled placeholder={String(minimum)} aria-label={t('auction.bidAmount')} />
              <Button type="button" disabled>
                {t('auction.bid')}
              </Button>
            </InputGroup>
            <Form.Text>{t('auction.bidsOpenAt', { date: formatDateTime(auction.startTime, lang) })}</Form.Text>
          </Form>
        )}
        {own && (
          <div className="small" data-testid="my-bid">
            {t('auction.myBid', { amount: formatMoney(own.amount, auction.currency, lang) })} <span className="text-muted">({t(`bidStatus.${own.status}`)})</span>
          </div>
        )}
        {won && auction.winnerId != null && <div className="small">{auction.winnerId === me ? t('auction.youWon') : t('auction.wonBy', { id: auction.winnerId })}</div>}
        <ProblemAlert message={error} onClose={() => setError(null)} />
        {open && !mine && status === 'authenticated' && (
          <Form onSubmit={bid} className="mt-2">
            <InputGroup>
              <Form.Control
                type="number"
                step="any"
                min={minimum}
                value={amount}
                onChange={e => setAmount(e.target.value)}
                placeholder={String(minimum)}
                required
                aria-label={t('auction.bidAmount')}
              />
              <Button type="submit" disabled={busy || !amount}>
                {t('auction.bid')}
              </Button>
            </InputGroup>
            <Form.Text>
              {t('auction.minimum', { amount: formatMoney(minimum, auction.currency, lang), increment: formatMoney(auction.bidIncrement, auction.currency, lang) })}
            </Form.Text>
            {auction.snagItPrice != null && (
              <div className="mt-2">
                <Button variant="success" size="sm" disabled={busy} onClick={() => void run(() => tradeApi.snag(announce.id), t('auction.bidFailed'))}>
                  {t('auction.snag', { amount: formatMoney(auction.snagItPrice, auction.currency, lang) })}
                </Button>
              </div>
            )}
            {own && own.status === 'ACTIVE' && (
              <div className="mt-2">
                <Button variant="link" size="sm" className="p-0" disabled={busy} onClick={() => void run(() => tradeApi.retractBid(announce.id, own.id), t('auction.bidFailed'))}>
                  {t('auction.retract')}
                </Button>
              </div>
            )}
          </Form>
        )}
        {open && mine && <div className="small mt-2 text-muted">{t('auction.yours')}</div>}
      </Card.Body>
    </Card>
  );
}
