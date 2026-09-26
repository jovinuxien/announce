import { useEffect, useState, type FormEvent } from 'react';
import { Button, Form, Modal, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import type { Kind } from 'app/shared/types';
import { KINDS } from 'app/shared/types';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import { emptyCategoryForm, validateCategoryForm, type CategoryFormContext, type CategoryFormErrors, type CategoryFormValues } from './categoryValidation';

interface Props {
  show: boolean;
  title: string;
  context: CategoryFormContext;
  initial?: Partial<CategoryFormValues>;
  kind: Kind;
  busy?: boolean;
  error?: unknown;
  onSubmit: (values: CategoryFormValues) => void;
  onCancel: () => void;
}

export default function CategoryFormDialog({ show, title, context, initial, kind, busy, error, onSubmit, onCancel }: Props) {
  const { t } = useTranslation();
  const [values, setValues] = useState<CategoryFormValues>({ ...emptyCategoryForm(kind), ...initial });
  const [errors, setErrors] = useState<CategoryFormErrors>({});
  useEffect(() => {
    if (show) {
      setValues({ ...emptyCategoryForm(kind), ...initial });
      setErrors({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show]);

  const set = (k: keyof CategoryFormValues, v: string) => setValues((s) => ({ ...s, [k]: v }));
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs = validateCategoryForm(values, context);
    setErrors(errs);
    if (Object.keys(errs).length === 0) onSubmit(values);
  };
  const kindFixed = context.mode !== 'root';
  const editOnly = context.mode === 'edit';

  return (
    <Modal show={show} onHide={onCancel} centered>
      <Form onSubmit={submit} noValidate>
        <Modal.Header closeButton>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error ? <ProblemAlert error={error} /> : null}
          <Form.Group className="mb-3" controlId="cat-kind">
            <Form.Label>{t('catalog.fields.kind')}</Form.Label>
            <Form.Select value={values.kind} disabled={kindFixed} onChange={(e) => set('kind', e.target.value)} isInvalid={!!errors.kind}>
              {KINDS.map((k) => (
                <option key={k} value={k}>
                  {t(`common.kind.${k}`)}
                </option>
              ))}
            </Form.Select>
            {kindFixed && <Form.Text muted>{t('catalog.kindInherited')}</Form.Text>}
            {errors.kind && <Form.Control.Feedback type="invalid">{t(errors.kind)}</Form.Control.Feedback>}
          </Form.Group>
          <Form.Group className="mb-3" controlId="cat-name">
            <Form.Label>{t('catalog.fields.name')}</Form.Label>
            <Form.Control autoFocus value={values.name} onChange={(e) => set('name', e.target.value)} isInvalid={!!errors.name} />
            {errors.name && <Form.Control.Feedback type="invalid">{t(errors.name)}</Form.Control.Feedback>}
          </Form.Group>
          <Form.Group className="mb-3" controlId="cat-description">
            <Form.Label>{t('catalog.fields.description')}</Form.Label>
            <Form.Control as="textarea" rows={2} value={values.description} onChange={(e) => set('description', e.target.value)} isInvalid={!!errors.description} />
            {errors.description && <Form.Control.Feedback type="invalid">{t(errors.description)}</Form.Control.Feedback>}
          </Form.Group>
          {!editOnly && (
            <>
              <Form.Group className="mb-3" controlId="cat-icon">
                <Form.Label>{t('catalog.fields.iconUrl')}</Form.Label>
                <Form.Control value={values.iconUrl} onChange={(e) => set('iconUrl', e.target.value)} />
              </Form.Group>
              <Form.Group className="mb-3" controlId="cat-picture">
                <Form.Label>{t('catalog.fields.pictureUrl')}</Form.Label>
                <Form.Control value={values.pictureUrl} onChange={(e) => set('pictureUrl', e.target.value)} />
              </Form.Group>
              <Form.Group className="mb-3" controlId="cat-keywords">
                <Form.Label>{t('catalog.fields.keywords')}</Form.Label>
                <Form.Control value={values.keywords} onChange={(e) => set('keywords', e.target.value)} />
              </Form.Group>
              <Form.Group className="mb-3" controlId="cat-sort">
                <Form.Label>{t('catalog.fields.sortOrder')}</Form.Label>
                <Form.Control inputMode="numeric" value={values.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} isInvalid={!!errors.sortOrder} />
                {errors.sortOrder && <Form.Control.Feedback type="invalid">{t(errors.sortOrder)}</Form.Control.Feedback>}
              </Form.Group>
            </>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" onClick={onCancel} disabled={busy}>
            {t('common.actions.cancel')}
          </Button>
          <Button type="submit" disabled={busy}>
            {busy && <Spinner size="sm" animation="border" className="me-2" />}
            {t('common.actions.save')}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}
