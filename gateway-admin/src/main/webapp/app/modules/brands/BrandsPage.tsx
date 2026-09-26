import { useState, type FormEvent } from 'react';
import { Badge, Button, ButtonGroup, Card, Form, Modal, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useCreateBrandMutation, useHideBrandMutation, useListBrandsQuery, useUpdateBrandMutation, type BrandRequest, type BrandResponse } from 'app/shared/api/brandsApi';
import { formatMoney } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import ConfirmDialog from 'app/shared/ui/ConfirmDialog';
import { usePaging } from 'app/shared/ui/usePaging';

interface FormValues {
  name: string;
  description: string;
  pictureUrl: string;
  sortOrder: string;
}
const empty: FormValues = { name: '', description: '', pictureUrl: '', sortOrder: '' };

export default function BrandsPage() {
  const { t, i18n } = useTranslation();
  const { page, size, setPage, setSize } = usePaging();
  const { data, error, isLoading } = useListBrandsQuery({ page, size });
  const [editing, setEditing] = useState<BrandResponse | 'new' | null>(null);
  const [hiding, setHiding] = useState<BrandResponse | null>(null);
  const [values, setValues] = useState<FormValues>(empty);
  const [nameError, setNameError] = useState<string | null>(null);
  const [create, createState] = useCreateBrandMutation();
  const [update, updateState] = useUpdateBrandMutation();
  const [hide, hideState] = useHideBrandMutation();

  const open = (b: BrandResponse | 'new') => {
    setEditing(b);
    setNameError(null);
    setValues(b === 'new' ? empty : { name: b.name, description: b.description ?? '', pictureUrl: b.pictureUrl ?? '', sortOrder: String(b.sortOrder ?? '') });
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!values.name.trim()) {
      setNameError('catalog.errors.nameRequired');
      return;
    }
    const body: BrandRequest = {
      name: values.name.trim(),
      description: values.description.trim() || null,
      pictureUrl: values.pictureUrl.trim() || null,
      sortOrder: values.sortOrder.trim() === '' ? null : Number(values.sortOrder),
    };
    try {
      if (editing === 'new') await create(body).unwrap();
      else if (editing) await update({ id: editing.id, body }).unwrap();
      setEditing(null);
    } catch {
      /* shown */
    }
  };
  const set = (k: keyof FormValues, v: string) => setValues((s) => ({ ...s, [k]: v }));

  return (
    <>
      <PageHeader
        title={t('catalog.brands.title')}
        icon="bi-award"
        actions={
          <Button onClick={() => open('new')}>
            <i className="bi bi-plus-lg me-1" aria-hidden />
            {t('catalog.brands.create')}
          </Button>
        }
      />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && data.items.length === 0 && <EmptyState />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>#</th>
                <th>{t('catalog.fields.name')}</th>
                <th>{t('catalog.fields.description')}</th>
                <th className="text-end">{t('catalog.fields.sortOrder')}</th>
                <th className="text-end">{t('catalog.brands.floorPrice')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((b) => (
                <tr key={b.id} className={b.hidden ? 'text-muted' : ''}>
                  <td>{b.id}</td>
                  <td>
                    {b.pictureUrl && <img src={b.pictureUrl} alt="" width={24} height={24} className="me-2 rounded" />}
                    {b.name} {b.hidden && <Badge bg="secondary">{t('catalog.hidden')}</Badge>}
                  </td>
                  <td className="text-truncate" style={{ maxWidth: 360 }}>
                    {b.description}
                  </td>
                  <td className="text-end">{b.sortOrder}</td>
                  <td className="text-end">{formatMoney(b.floorPrice, undefined, i18n.language)}</td>
                  <td className="text-end">
                    <ButtonGroup size="sm">
                      <Button variant="outline-secondary" onClick={() => open(b)} title={t('common.actions.edit')}>
                        <i className="bi bi-pencil" aria-hidden />
                      </Button>
                      <Button variant="outline-danger" onClick={() => setHiding(b)} disabled={b.hidden} title={t('catalog.actions.hide')}>
                        <i className="bi bi-eye-slash" aria-hidden />
                      </Button>
                    </ButtonGroup>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} onSize={setSize} />}

      <Modal show={editing !== null} onHide={() => setEditing(null)} centered>
        <Form onSubmit={submit} noValidate>
          <Modal.Header closeButton>
            <Modal.Title>{editing === 'new' ? t('catalog.brands.create') : t('catalog.brands.edit')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            {(createState.error || updateState.error) && <ProblemAlert error={createState.error || updateState.error} />}
            <Form.Group className="mb-3" controlId="brand-name">
              <Form.Label>{t('catalog.fields.name')}</Form.Label>
              <Form.Control autoFocus value={values.name} onChange={(e) => set('name', e.target.value)} isInvalid={!!nameError} />
              {nameError && <Form.Control.Feedback type="invalid">{t(nameError)}</Form.Control.Feedback>}
            </Form.Group>
            <Form.Group className="mb-3" controlId="brand-description">
              <Form.Label>{t('catalog.fields.description')}</Form.Label>
              <Form.Control as="textarea" rows={2} value={values.description} onChange={(e) => set('description', e.target.value)} />
            </Form.Group>
            <Form.Group className="mb-3" controlId="brand-picture">
              <Form.Label>{t('catalog.fields.pictureUrl')}</Form.Label>
              <Form.Control value={values.pictureUrl} onChange={(e) => set('pictureUrl', e.target.value)} />
            </Form.Group>
            <Form.Group className="mb-3" controlId="brand-sort">
              <Form.Label>{t('catalog.fields.sortOrder')}</Form.Label>
              <Form.Control inputMode="numeric" value={values.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setEditing(null)}>
              {t('common.actions.cancel')}
            </Button>
            <Button type="submit" disabled={createState.isLoading || updateState.isLoading}>
              {(createState.isLoading || updateState.isLoading) && <Spinner size="sm" animation="border" className="me-2" />}
              {t('common.actions.save')}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      <ConfirmDialog
        show={hiding !== null}
        title={t('catalog.actions.hide')}
        body={hiding ? t('catalog.brands.hideConfirm', { name: hiding.name }) : ''}
        confirmLabel={t('catalog.actions.hide')}
        busy={hideState.isLoading}
        error={hideState.error}
        onCancel={() => setHiding(null)}
        onConfirm={async () => {
          if (!hiding) return;
          try {
            await hide(hiding.id).unwrap();
            setHiding(null);
          } catch {
            /* shown */
          }
        }}
      />
    </>
  );
}
