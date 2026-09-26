import { Badge } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';

const variants: Record<string, string> = {
  DRAFT: 'secondary', PUBLISHED: 'success', SOLD: 'primary', WITHDRAWN: 'warning', EXPIRED: 'dark',
  ACTIVE: 'success', GONE: 'dark',
  PROPOSED: 'secondary', AGREED: 'info', PAYMENT_PENDING: 'warning', PAID: 'primary', SHIPPED: 'primary', DELIVERED: 'primary',
  RELEASING: 'info', SETTLED: 'success', CANCELLED: 'dark', DISPUTED: 'danger', REFUNDING: 'warning', REFUNDED: 'dark',
  OPEN: 'success', CLOSED_WON: 'primary', CLOSED_UNSOLD: 'secondary', SNAGGED: 'primary',
  WAITING: 'warning', CLOSED: 'secondary', BLOCKED: 'danger',
  PENDING_RELEASE: 'warning', SENT: 'success', FAILED: 'danger', BLOCKED_DISPUTE: 'danger',
  OUTBID: 'secondary', WON: 'success', RETRACTED: 'dark',
  VERIFIED: 'success', PENDING: 'warning', RESTRICTED: 'danger',
};

interface Props {
  status: string | null | undefined;
  /** Translation prefix, e.g. `common.status` -> `common.status.PUBLISHED`. */
  prefix?: string;
}

export default function StatusBadge({ status, prefix = 'common.status' }: Props) {
  const { t } = useTranslation();
  if (!status) return null;
  const variant = variants[status] ?? 'secondary';
  const dark = variant === 'warning' || variant === 'info';
  return (
    <Badge bg={variant} text={dark ? 'dark' : undefined}>
      {t(`${prefix}.${status}`, { defaultValue: status })}
    </Badge>
  );
}
