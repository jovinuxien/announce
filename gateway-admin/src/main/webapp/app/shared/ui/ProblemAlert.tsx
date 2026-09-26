import { Alert } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { problemOf } from 'app/shared/types';

interface Props {
  error: unknown;
  /** Optional map from a problem `code` to a translation key for a friendlier message. */
  codes?: Record<string, string>;
  onClose?: () => void;
  className?: string;
}

/** Shows an RFC 7807 problem: `detail` (and `code` when present) as the backends and the edge send them. */
export default function ProblemAlert({ error, codes, onClose, className }: Props) {
  const { t } = useTranslation();
  if (!error) return null;
  const problem = problemOf(error);
  const mapped = problem?.code && codes && codes[problem.code] ? t(codes[problem.code]) : undefined;
  const detail = mapped || problem?.detail || problem?.title || t('common.error.unknown');
  return (
    <Alert variant="danger" dismissible={!!onClose} onClose={onClose} className={className} role="alert" data-testid="problem-alert">
      <div className="d-flex align-items-start gap-2">
        <i className="bi bi-exclamation-triangle-fill" aria-hidden />
        <div>
          <div>{detail}</div>
          {problem?.code && (
            <small className="text-muted">
              {t('common.error.code')}: <code>{problem.code}</code>
              {problem.status ? ` · HTTP ${problem.status}` : ''}
            </small>
          )}
          {!problem?.code && problem?.status && <small className="text-muted">HTTP {problem.status}</small>}
        </div>
      </div>
    </Alert>
  );
}
