import { Alert } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { shouldStartMocks } from 'app/config/features';

/** Reminds the reviewer that a phase-2 screen is fed by MSW, not by a backend. */
export default function MockBanner() {
  const { t } = useTranslation();
  if (!shouldStartMocks()) return null;
  return (
    <Alert variant="info" className="py-2 small">
      <i className="bi bi-info-circle me-1" aria-hidden />
      {t('common.mocked')}
    </Alert>
  );
}
