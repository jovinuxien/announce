import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface Props {
  title?: string;
  hint?: ReactNode;
  icon?: string;
}

export default function EmptyState({ title, hint, icon = 'bi-inbox' }: Props) {
  const { t } = useTranslation();
  return (
    <div className="text-center text-muted py-5" data-testid="empty-state">
      <i className={`bi ${icon} fs-1 d-block mb-2`} aria-hidden />
      <div className="fw-semibold">{title ?? t('common.empty.title')}</div>
      {hint && <div className="small">{hint}</div>}
    </div>
  );
}
