import { useEffect, useState, type FormEvent } from 'react';
import { Breadcrumb, Button, ButtonGroup, Card, Col, Form, Modal, Row, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { REGION_TYPES, useCreateRegionMutation, useDeleteRegionMutation, useGetRegionQuery, useListRegionsQuery, useUpdateRegionMutation, type RegionRequest, type RegionResponse, type RegionType } from 'app/shared/api/regionsApi';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import ConfirmDialog from 'app/shared/ui/ConfirmDialog';
import { usePaging } from 'app/shared/ui/usePaging';

/** Problem `code`s of EdgeRegionController. */
export const REGION_ERROR_CODES: Record<string, string> = {
  'region-has-children': 'regions.errors.hasChildren',
};

interface Crumb {
  id: number;
  name: string;
}
interface FormValues {
  pid: string;
  name: string;
  type: RegionType;
  code: string;
}

export default function RegionsPage() {
  const { t } = useTranslation();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const q = params.get('q') ?? '';
  const pidParam = params.get('pid');
  const searching = q !== '';
  const pid = searching ? undefined : Number(pidParam ?? 0) || 0;
  const [draftQ, setDraftQ] = useState(q);
  const [trail, setTrail] = useState<Crumb[]>([]);
  const { data, error, isLoading } = useListRegionsQuery({ pid: searching ? '' : pid, q: q || undefined, page, size });
  const { data: parent } = useGetRegionQuery(pid ?? 0, { skip: !pid });
  const [editing, setEditing] = useState<RegionResponse | 'new' | null>(null);
  const [deleting, setDeleting] = useState<RegionResponse | null>(null);
  const [values, setValues] = useState<FormValues>({ pid: '0', name: '', type: 1, code: '' });
  const [nameError, setNameError] = useState<string | null>(null);
  const [create, createState] = useCreateRegionMutation();
  const [update, updateState] = useUpdateRegionMutation();
  const [remove, removeState] = useDeleteRegionMutation();

  // Rebuild the trail from the parent when the URL was loaded directly.
  useEffect(() => {
    if (parent && !trail.some((c) => c.id === parent.id)) setTrail([{ id: parent.id, name: parent.name }]);
    if (!pid) setTrail([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [parent?.id, pid]);

  const drill = (r: RegionResponse) => {
    setTrail((tr) => [...tr, { id: r.id, name: r.name }]);
    setDraftQ('');
    const next = new URLSearchParams();
    next.set('pid', String(r.id));
    setParam('q', '');
    setParam('pid', String(r.id));
  };
  const goTo = (index: number) => {
    const target = index < 0 ? null : trail[index];
    setTrail((tr) => tr.slice(0, index + 1));
    setParam('pid', target ? String(target.id) : null);
  };
  const childType = (): RegionType => (parent ? (Math.min(3, parent.type + 1) as RegionType) : 1);
  const open = (r: RegionResponse | 'new') => {
    setEditing(r);
    setNameError(null);
    setValues(r === 'new' ? { pid: String(pid ?? 0), name: '', type: childType(), code: '' } : { pid: String(r.pid), name: r.name, type: r.type, code: String(r.code ?? '') });
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!values.name.trim()) {
      setNameError('regions.errors.nameRequired');
      return;
    }
    const body: RegionRequest = { pid: Number(values.pid) || 0, name: values.name.trim(), type: values.type, code: values.code.trim() === '' ? null : Number(values.code) };
    try {
      if (editing === 'new') await create(body).unwrap();
      else if (editing) await update({ id: editing.id, body }).unwrap();
      setEditing(null);
    } catch {
      /* shown */
    }
  };

  return (
    <>
      <PageHeader
        title={t('regions.title')}
        icon="bi-geo-alt"
        actions={
          <Button onClick={() => open('new')}>
            <i className="bi bi-plus-lg me-1" aria-hidden />
            {t('regions.create')}
          </Button>
        }
      />
      <Card className="shadow-sm mb-3">
        <Card.Body>
          <Row className="g-2 align-items-center">
            <Col md>
              <Breadcrumb className="mb-0" listProps={{ className: 'mb-0' }}>
                <Breadcrumb.Item active={!pid && !searching} onClick={() => goTo(-1)} linkAs="button" linkProps={{ className: 'btn btn-link p-0' }}>
                  {t('regions.root')}
                </Breadcrumb.Item>
                {trail.map((c, i) => (
                  <Breadcrumb.Item key={c.id} active={i === trail.length - 1 && !searching} onClick={() => goTo(i)} linkAs="button" linkProps={{ className: 'btn btn-link p-0' }}>
                    {c.name}
                  </Breadcrumb.Item>
                ))}
                {searching && <Breadcrumb.Item active>{t('regions.searchResults', { q })}</Breadcrumb.Item>}
              </Breadcrumb>
            </Col>
            <Col md="auto">
              <Form
                className="d-flex gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  setParam('q', draftQ.trim());
                }}
              >
                <Form.Control size="sm" value={draftQ} onChange={(e) => setDraftQ(e.target.value)} placeholder={t('regions.searchPlaceholder')} aria-label={t('common.actions.search')} />
                <Button type="submit" size="sm">
                  {t('common.actions.search')}
                </Button>
                {searching && (
                  <Button type="button" size="sm" variant="outline-secondary" onClick={() => { setDraftQ(''); setParam('q', ''); }}>
                    {t('common.actions.reset')}
                  </Button>
                )}
              </Form>
            </Col>
          </Row>
        </Card.Body>
      </Card>
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && data.items.length === 0 && <EmptyState hint={t('regions.emptyHint')} />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>#</th>
                <th>{t('regions.fields.name')}</th>
                <th>{t('regions.fields.type')}</th>
                <th>{t('regions.fields.code')}</th>
                <th>{t('regions.fields.pid')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((r) => (
                <tr key={r.id}>
                  <td>{r.id}</td>
                  <td>
                    {r.type < 3 ? (
                      <Button variant="link" className="p-0 fw-semibold text-decoration-none" onClick={() => drill(r)}>
                        {r.name}
                        <i className="bi bi-chevron-right ms-1 small" aria-hidden />
                      </Button>
                    ) : (
                      <span className="fw-semibold">{r.name}</span>
                    )}
                  </td>
                  <td>{t(`regions.types.${r.type}`)}</td>
                  <td>{r.code}</td>
                  <td>{r.pid}</td>
                  <td className="text-end">
                    <ButtonGroup size="sm">
                      <Button variant="outline-secondary" onClick={() => open(r)} title={t('common.actions.edit')}>
                        <i className="bi bi-pencil" aria-hidden />
                      </Button>
                      <Button variant="outline-danger" onClick={() => setDeleting(r)} title={t('common.actions.delete')}>
                        <i className="bi bi-trash" aria-hidden />
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
            <Modal.Title>{editing === 'new' ? t('regions.create') : t('regions.edit')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            {(createState.error || updateState.error) && <ProblemAlert error={createState.error || updateState.error} codes={REGION_ERROR_CODES} />}
            <Form.Group className="mb-3" controlId="region-name">
              <Form.Label>{t('regions.fields.name')}</Form.Label>
              <Form.Control autoFocus value={values.name} onChange={(e) => setValues({ ...values, name: e.target.value })} isInvalid={!!nameError} />
              {nameError && <Form.Control.Feedback type="invalid">{t(nameError)}</Form.Control.Feedback>}
            </Form.Group>
            <Row>
              <Col>
                <Form.Group className="mb-3" controlId="region-type">
                  <Form.Label>{t('regions.fields.type')}</Form.Label>
                  <Form.Select value={values.type} onChange={(e) => setValues({ ...values, type: Number(e.target.value) as RegionType })}>
                    {REGION_TYPES.map((ty) => (
                      <option key={ty} value={ty}>
                        {t(`regions.types.${ty}`)}
                      </option>
                    ))}
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col>
                <Form.Group className="mb-3" controlId="region-pid">
                  <Form.Label>{t('regions.fields.pid')}</Form.Label>
                  <Form.Control inputMode="numeric" value={values.pid} onChange={(e) => setValues({ ...values, pid: e.target.value.replace(/\D/g, '') })} />
                </Form.Group>
              </Col>
              <Col>
                <Form.Group className="mb-3" controlId="region-code">
                  <Form.Label>{t('regions.fields.code')}</Form.Label>
                  <Form.Control inputMode="numeric" value={values.code} onChange={(e) => setValues({ ...values, code: e.target.value.replace(/\D/g, '') })} />
                </Form.Group>
              </Col>
            </Row>
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
        show={deleting !== null}
        title={t('regions.delete')}
        body={deleting ? t('regions.deleteConfirm', { name: deleting.name }) : ''}
        confirmLabel={t('common.actions.delete')}
        busy={removeState.isLoading}
        error={removeState.error}
        errorCodes={REGION_ERROR_CODES}
        onCancel={() => {
          setDeleting(null);
          removeState.reset();
        }}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await remove(deleting.id).unwrap();
            setDeleting(null);
          } catch {
            /* shown */
          }
        }}
      />
    </>
  );
}
