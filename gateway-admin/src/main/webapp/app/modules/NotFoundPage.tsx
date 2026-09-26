import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import EmptyState from 'app/shared/ui/EmptyState';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return <EmptyState icon="bi-signpost-split" title={t('common.notFound.title')} hint={<Link to="/">{t('common.notFound.home')}</Link>} />;
}
