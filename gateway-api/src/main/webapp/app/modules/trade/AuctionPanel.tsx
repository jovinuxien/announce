import { FormEvent, useEffect, useState } from 'react';
import { Button, Card, Form, InputGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useAppSelector } from 'app/config/store';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { AnnounceResponse } from 'app/shared/model/announce';
import { AuctionView } from 'app/shared/model/trade';
import { countdown } from 'app/shared/util/dates';
import { amountToCents, centsToAmount, formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';

/** Phase 2 (features.trade): current price, bid count, countdown, bid form and snag-it on an AUCTION goods announce. */
export default function AuctionPanel({ announce, mine }: { announce: AnnounceResponse; mine: boolean }) {
  const { t, i18n } = useTranslation('trade');
  const status = useAppSelector(s => s.auth.status);
  const [auction, setAuction] = useState<AuctionView | null>(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    tradeApi.auction(announce.id).then(setAuction).catch(e => setError(describeError(e, t('auction.unavailable'))));
    const timer = window.setInterval(() => setNow(new Date()), 30000);
    return () => window.clearInterval(timer);
  }, [announce.id, t]);

  if (!auction) return <ProblemAlert message={error} />;
  const lang = i18n.language;
  const closed = auction.status !== 'OPEN' || new Date(auction.endsAt).getTime() <= now.getTime();

  const bid = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      setAuction(await tradeApi.bid(announce.id, amountToCents(Number(amount))));
      setAmount('');
    } catch (err) {
      setError(describeError(err, t('auction.bidFailed')));
    }
  };
  const snag = async () => {
    setError(null);
    try {
      setAuction(await tradeApi.snag(announce.id));
    } catch (err) {
      setError(describeError(err, t('auction.bidFailed')));
    }
  };

  return (
    <Card className="mt-3 border-primary">
      <Card.Body>
        <h2 className="h6">{t('auction.title')}</h2>
        <div className="an-price fs-4">{formatMoney(centsToAmount(auction.currentBidCents ?? auction.startPriceCents), auction.currency, lang)}</div>
        <div className="small text-muted">
          {t('auction.bids', { count: auction.bidCount })} · {closed ? t('auction.closed') : t('auction.endsIn', { time: countdown(auction.endsAt, now) })}
        </div>
        {auction.myHighestBidCents != null && <div className="small">{t('auction.myBid', { amount: formatMoney(centsToAmount(auction.myHighestBidCents), auction.currency, lang) })}</div>}
        <ProblemAlert message={error} onClose={() => setError(null)} />
        {!closed && !mine && status === 'authenticated' && (
          <Form onSubmit={bid} className="mt-2">
            <InputGroup>
              <Form.Control type="number" step="1" min={centsToAmount(auction.minimumNextBidCents)} value={amount} onChange={e => setAmount(e.target.value)} placeholder={String(centsToAmount(auction.minimumNextBidCents))} required aria-label={t('auction.bidAmount')} />
              <Button type="submit">{t('auction.bid')}</Button>
            </InputGroup>
            <Form.Text>{t('auction.minimum', { amount: formatMoney(centsToAmount(auction.minimumNextBidCents), auction.currency, lang) })}</Form.Text>
            {auction.snagPriceCents != null && (
              <div className="mt-2">
                <Button variant="success" size="sm" onClick={snag}>
                  {t('auction.snag', { amount: formatMoney(centsToAmount(auction.snagPriceCents), auction.currency, lang) })}
                </Button>
              </div>
            )}
          </Form>
        )}
        {!closed && !mine && status !== 'authenticated' && <div className="small mt-2">{t('auction.loginToBid')}</div>}
      </Card.Body>
    </Card>
  );
}
