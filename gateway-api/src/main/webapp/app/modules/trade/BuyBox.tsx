import { useState } from 'react';
import { Button } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { tradeApi } from 'app/shared/api/tradeApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { AnnounceResponse } from 'app/shared/model/announce';
import { describeError } from 'app/shared/util/problem';

/** Phase 2 (features.trade): "request purchase" on a fixed-price goods announce; the order page takes over. */
export default function BuyBox({ announce }: { announce: AnnounceResponse }) {
  const { t } = useTranslation('trade');
  const navigate = useNavigate();
  const status = useAppSelector(s => s.auth.status);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const buy = async () => {
    if (status !== 'authenticated') {
      navigate('/login', { state: { from: `/announce/${announce.id}` } });
      return;
    }
    setBusy(true);
    try {
      const order = await tradeApi.requestPurchase(announce.id);
      navigate(`/orders/${encodeURIComponent(order.id)}`);
    } catch (e) {
      setError(describeError(e, t('buy.failed')));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button size="sm" variant="success" disabled={busy} onClick={buy}>
        {announce.goods?.saleType === 'GIVEAWAY' ? t('buy.request') : t('buy.buy')}
      </Button>
      <ProblemAlert message={error} onClose={() => setError(null)} />
    </>
  );
}
