import type { ReactNode } from 'react';
import { Alert } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useAuth } from './useAuth';

interface Props {
  role: string;
  children: ReactNode;
  /** Render nothing instead of the forbidden notice (for menu entries). */
  silent?: boolean;
}

/** Guards super-only screens: a non-holder sees a forbidden notice (or nothing when `silent`). */
export default function RequireRole({ role, children, silent }: Props) {
  const { hasRole } = useAuth();
  const { t } = useTranslation();
  if (!hasRole(role)) {
    if (silent) return null;
    return (
      <Alert variant="warning" data-testid="forbidden">
        <Alert.Heading>{t('common.forbidden.title')}</Alert.Heading>
        <p className="mb-0">{t('common.forbidden.detail')}</p>
      </Alert>
    );
  }
  return <>{children}</>;
}
