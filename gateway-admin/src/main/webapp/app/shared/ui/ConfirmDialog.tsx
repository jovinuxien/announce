import type { ReactNode } from 'react';
import { Button, Modal, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import ProblemAlert from './ProblemAlert';

interface Props {
  show: boolean;
  title: string;
  body: ReactNode;
  confirmLabel?: string;
  variant?: string;
  busy?: boolean;
  error?: unknown;
  errorCodes?: Record<string, string>;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({ show, title, body, confirmLabel, variant = 'danger', busy, error, errorCodes, onConfirm, onCancel }: Props) {
  const { t } = useTranslation();
  return (
    <Modal show={show} onHide={onCancel} centered>
      <Modal.Header closeButton>
        <Modal.Title>{title}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error ? <ProblemAlert error={error} codes={errorCodes} /> : null}
        {body}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="outline-secondary" onClick={onCancel} disabled={busy}>
          {t('common.actions.cancel')}
        </Button>
        <Button variant={variant} onClick={onConfirm} disabled={busy}>
          {busy && <Spinner size="sm" animation="border" className="me-2" />}
          {confirmLabel ?? t('common.actions.confirm')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
