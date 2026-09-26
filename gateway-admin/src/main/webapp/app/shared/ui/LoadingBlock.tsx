import { Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';

export default function LoadingBlock() {
  const { t } = useTranslation();
  return (
    <div className="d-flex align-items-center gap-2 text-muted py-4" role="status">
      <Spinner animation="border" size="sm" />
      <span>{t('common.loading')}</span>
    </div>
  );
}
