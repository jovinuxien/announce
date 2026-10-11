import { useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Card } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { SellerAccountRow } from 'app/shared/model/trade';
import { describeError } from 'app/shared/util/problem';
import { ROW_SETTLE_MS } from 'app/shared/util/settle';

/**
 * The seller's payout account (payment context, `GET` / `POST /api/sellers/me/account`). The order module refuses
 * AcceptPurchase and never opens an auction until the account exists with payouts enabled (`seller-not-payable`),
 * so this is the first thing a seller has to do. The provider decides the state; the fake one verifies at once.
 */
export default function PayoutsCard() {
  const { t } = useTranslation('trade');
  const [account, setAccount] = useState<SellerAccountRow | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(
    () =>
      tradeApi
        .sellerAccount()
        .then(setAccount)
        .catch(e => setError(describeError(e, t('payouts.loadFailed')))),
    [t]
  );
  const settle = useRef<number | undefined>(undefined);
  useEffect(() => {
    void load();
    return () => window.clearTimeout(settle.current);
  }, [load]);

  const onboard = async () => {
    setBusy(true);
    setError(null);
    try {
      // the command answers the aggregate; the row follows a moment later
      const a = await tradeApi.onboard();
      setAccount({
        id: String(a.userId),
        userId: a.userId,
        provider: a.provider,
        accountRef: a.accountRef,
        status: a.status,
        payoutsEnabled: a.payoutsEnabled,
        onboardingUrl: a.onboardingUrl,
      });
      window.clearTimeout(settle.current);
      settle.current = window.setTimeout(() => void load(), ROW_SETTLE_MS);
    } catch (e) {
      setError(describeError(e, t('payouts.onboardFailed')));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="mb-3" data-testid="payouts-card">
      <Card.Body>
        <h2 className="h6">{t('payouts.title')}</h2>
        <ProblemAlert message={error} onClose={() => setError(null)} />
        {account === undefined && !error && <div className="small text-muted">…</div>}
        {account === null && (
          <>
            <p className="small text-muted mb-2">{t('payouts.none')}</p>
            <Button size="sm" disabled={busy} onClick={onboard}>
              {t('payouts.setUp')}
            </Button>
          </>
        )}
        {account && (
          <div className="small">
            <div>
              <Badge bg={account.payoutsEnabled ? 'success' : 'warning'} text={account.payoutsEnabled ? undefined : 'dark'} data-testid="payouts-state">
                {account.payoutsEnabled ? t('payouts.enabled') : t('payouts.pending')}
              </Badge>{' '}
              <span className="text-muted">{t('payouts.provider', { provider: account.provider, status: account.status })}</span>
            </div>
            {account.accountRef && <div className="text-muted">{t('payouts.accountRef', { ref: account.accountRef })}</div>}
            {!account.payoutsEnabled && account.onboardingUrl && (
              <a href={account.onboardingUrl} target="_blank" rel="noreferrer">
                {t('payouts.continue')}
              </a>
            )}
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
