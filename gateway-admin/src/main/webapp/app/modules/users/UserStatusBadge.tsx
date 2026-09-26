import { Badge } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import type { UserStatus } from 'app/shared/api/usersApi';

const variants: Record<number, string> = { 0: 'success', 1: 'warning', 2: 'dark' };

export default function UserStatusBadge({ status }: { status: UserStatus | number | null | undefined }) {
  const { t } = useTranslation();
  if (status === null || status === undefined) return null;
  return (
    <Badge bg={variants[status] ?? 'secondary'} text={status === 1 ? 'dark' : undefined}>
      {t(`members.statuses.${status}`, { defaultValue: String(status) })}
    </Badge>
  );
}
