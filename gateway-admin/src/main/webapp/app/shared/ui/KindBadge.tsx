import { Badge } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import type { Kind } from 'app/shared/types';

const variants: Record<Kind, string> = { GOODS: 'primary', JOB: 'success', EVENT: 'warning' };
const icons: Record<Kind, string> = { GOODS: 'bi-box-seam', JOB: 'bi-briefcase', EVENT: 'bi-calendar-event' };

export default function KindBadge({ kind }: { kind: Kind | string | null | undefined }) {
  const { t } = useTranslation();
  if (!kind) return null;
  const k = kind as Kind;
  return (
    <Badge bg={variants[k] ?? 'secondary'} text={k === 'EVENT' ? 'dark' : undefined} className="text-uppercase">
      <i className={`bi ${icons[k] ?? 'bi-tag'} me-1`} aria-hidden />
      {t(`common.kind.${k}`, { defaultValue: k })}
    </Badge>
  );
}
