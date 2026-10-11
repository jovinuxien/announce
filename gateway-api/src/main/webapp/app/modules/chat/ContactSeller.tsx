import { useState } from 'react';
import { Button } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { chatApi } from 'app/shared/api/chatApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { AnnounceResponse } from 'app/shared/model/announce';
import { describeError } from 'app/shared/util/problem';

/**
 * Opens (or finds: the id is derived from the announce and the buyer, so a second click lands in the same
 * conversation) the listing conversation between the viewer and the seller. Messaging answers 409 buyer-is-seller
 * on the owner's own announce and 404 announce-not-found when announce-management does not know it.
 */
export default function ContactSeller({ announce }: { announce: AnnounceResponse }) {
  const { t } = useTranslation('chat');
  const navigate = useNavigate();
  const status = useAppSelector(s => s.auth.status);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const open = async () => {
    if (status !== 'authenticated') {
      navigate('/login', { state: { from: `/announce/${announce.id}` } });
      return;
    }
    setBusy(true);
    try {
      const c = await chatApi.open({
        type: 'LISTING',
        announceId: announce.id,
      });
      navigate(`/messages/${encodeURIComponent(c.id)}`);
    } catch (e) {
      setError(describeError(e, t('openFailed')));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button size="sm" variant="outline-primary" onClick={open} disabled={busy}>
        <i className="bi bi-chat-dots me-1" />
        {t('contactSeller')}
      </Button>
      <ProblemAlert message={error} onClose={() => setError(null)} />
    </>
  );
}
