import { useEffect, useState, type FormEvent } from 'react';
import { Button, Form, Modal, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import ProblemAlert from 'app/shared/ui/ProblemAlert';

export type ModerationAction = 'withdraw' | 'expire';

interface Props {
  show: boolean;
  action: ModerationAction;
  announceTitle?: string;
  busy?: boolean;
  error?: unknown;
  onSubmit: (reason: string) => void;
  onCancel: () => void;
}

export const MIN_REASON_LENGTH = 3;

export function validateReason(reason: string): string | null {
  const trimmed = reason.trim();
  if (!trimmed) return 'announces.moderation.reasonRequired';
  if (trimmed.length < MIN_REASON_LENGTH) return 'announces.moderation.reasonTooShort';
  if (trimmed.length > 500) return 'announces.moderation.reasonTooLong';
  return null;
}

/** Withdraw / Expire: the reason is mandatory (AnnounceAdminController.ModerationRequest) and recorded on the event. */
export default function ModerationDialog({ show, action, announceTitle, busy, error, onSubmit, onCancel }: Props) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [validation, setValidation] = useState<string | null>(null);
  useEffect(() => {
    if (show) {
      setReason('');
      setValidation(null);
    }
  }, [show, action]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const err = validateReason(reason);
    setValidation(err);
    if (!err) onSubmit(reason.trim());
  };

  return (
    <Modal show={show} onHide={onCancel} centered>
      <Form onSubmit={submit} noValidate data-testid="moderation-form">
        <Modal.Header closeButton>
          <Modal.Title>{t(`announces.moderation.${action}.title`)}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error ? <ProblemAlert error={error} /> : null}
          <p>{t(`announces.moderation.${action}.hint`, { title: announceTitle ?? '' })}</p>
          <Form.Group controlId="moderation-reason">
            <Form.Label>{t('announces.moderation.reason')}</Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              autoFocus
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              isInvalid={!!validation}
              placeholder={t('announces.moderation.reasonPlaceholder')}
            />
            {validation && <Form.Control.Feedback type="invalid">{t(validation)}</Form.Control.Feedback>}
            <Form.Text muted>{t('announces.moderation.reasonRecorded')}</Form.Text>
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={onCancel} disabled={busy}>
            {t('common.actions.cancel')}
          </Button>
          <Button type="submit" variant={action === 'withdraw' ? 'warning' : 'dark'} disabled={busy}>
            {busy && <Spinner size="sm" animation="border" className="me-2" />}
            {t(`announces.moderation.${action}.confirm`)}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}
