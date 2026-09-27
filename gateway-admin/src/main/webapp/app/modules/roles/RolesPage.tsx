import { useEffect, useState, type FormEvent } from 'react';
import { Badge, Button, ButtonGroup, Card, Col, Form, Modal, Row, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import {
  useCreateRoleMutation, useDeleteRoleMutation, useGetPermissionsQuery, useGetRolePermissionsQuery, useListRolesQuery, useSetRolePermissionsMutation,
  useUpdateRoleMutation, type RoleRequest, type RoleResponse,
} from 'app/shared/api/rolesApi';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import ConfirmDialog from 'app/shared/ui/ConfirmDialog';
import { usePaging } from 'app/shared/ui/usePaging';

/** Problem `code`s of EdgeRoleController. */
export const ROLE_ERROR_CODES: Record<string, string> = {
  'role-in-use': 'admins.roles.errors.inUse',
  'name-taken': 'admins.roles.errors.nameTaken',
};

function PermissionsEditor({ role }: { role: RoleResponse }) {
  const { t } = useTranslation();
  const { data: catalogue, error: catError, isLoading: catLoading } = useGetPermissionsQuery();
  const { data: current, error: curError, isLoading: curLoading } = useGetRolePermissionsQuery(role.id);
  const [save, saveState] = useSetRolePermissionsMutation();
  const [selected, setSelected] = useState<string[]>([]);
  useEffect(() => {
    if (current) setSelected(current);
  }, [current, role.id]);
  const toggle = (code: string) => setSelected((s) => (s.includes(code) ? s.filter((c) => c !== code) : [...s, code]));
  const dirty = current ? [...selected].sort().join() !== [...current].sort().join() : false;
  return (
    <Card className="shadow-sm h-100">
      <Card.Header className="d-flex align-items-center justify-content-between">
        <span>
          {t('admins.roles.permissionsOf', { name: role.name })}
          {selected.includes('*') && (
            <Badge bg="warning" text="dark" className="ms-2">
              {t('common.roles.ROLE_SUPER')}
            </Badge>
          )}
        </span>
        <Button size="sm" disabled={!dirty || saveState.isLoading} onClick={() => save({ id: role.id, body: { permissions: selected } })}>
          {saveState.isLoading && <Spinner size="sm" animation="border" className="me-1" />}
          {t('common.actions.save')}
        </Button>
      </Card.Header>
      <Card.Body>
        {(catError || curError || saveState.error) && <ProblemAlert error={catError || curError || saveState.error} />}
        {(catLoading || curLoading) && <LoadingBlock />}
        {saveState.isSuccess && !dirty && <div className="text-success small mb-2">{t('common.saved')}</div>}
        {catalogue?.map((p) => (
          <Form.Check key={p.code} id={`perm-${p.code}`} type="checkbox" className="mb-2" checked={selected.includes(p.code)} onChange={() => toggle(p.code)}
            label={
              <>
                <code>{p.code}</code>
                <div className="small text-muted">{p.description}</div>
              </>
            }
          />
        ))}
      </Card.Body>
    </Card>
  );
}

interface FormValues {
  name: string;
  description: string;
  enabled: boolean;
}
const empty: FormValues = { name: '', description: '', enabled: true };

export default function RolesPage() {
  const { t } = useTranslation();
  const { page, size, setPage, setSize } = usePaging();
  const { data, error, isLoading } = useListRolesQuery({ page, size });
  const [selected, setSelected] = useState<RoleResponse | null>(null);
  const [editing, setEditing] = useState<RoleResponse | 'new' | null>(null);
  const [deleting, setDeleting] = useState<RoleResponse | null>(null);
  const [values, setValues] = useState<FormValues>(empty);
  const [nameError, setNameError] = useState<string | null>(null);
  const [create, createState] = useCreateRoleMutation();
  const [update, updateState] = useUpdateRoleMutation();
  const [remove, removeState] = useDeleteRoleMutation();

  const open = (r: RoleResponse | 'new') => {
    setEditing(r);
    setNameError(null);
    setValues(r === 'new' ? empty : { name: r.name, description: r.description ?? '', enabled: r.enabled });
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!values.name.trim()) {
      setNameError('admins.roles.errors.nameRequired');
      return;
    }
    const body: RoleRequest = { name: values.name.trim(), description: values.description.trim() || null, enabled: values.enabled };
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
        title={t('admins.roles.title')}
        icon="bi-shield-lock"
        actions={
          <Button onClick={() => open('new')}>
            <i className="bi bi-plus-lg me-1" aria-hidden />
            {t('admins.roles.create')}
          </Button>
        }
      />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      <Row className="g-3">
        <Col lg={selected ? 6 : 12}>
          {data && data.items.length === 0 && <EmptyState />}
          {data && data.items.length > 0 && (
            <Card className="shadow-sm">
              <Table hover responsive className="mb-0">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>{t('admins.roles.fields.name')}</th>
                    <th>{t('admins.roles.fields.description')}</th>
                    <th>{t('admins.roles.fields.enabled')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((r) => (
                    <tr key={r.id} className={selected?.id === r.id ? 'table-active' : ''} style={{ cursor: 'pointer' }} onClick={() => setSelected(r)}>
                      <td>{r.id}</td>
                      <td className="fw-semibold">{r.name}</td>
                      <td className="text-muted">{r.description}</td>
                      <td>{r.enabled ? <Badge bg="success">{t('common.yes')}</Badge> : <Badge bg="secondary">{t('common.no')}</Badge>}</td>
                      <td className="text-end" onClick={(e) => e.stopPropagation()}>
                        <ButtonGroup size="sm">
                          <Button variant="outline-primary" onClick={() => setSelected(r)} title={t('admins.roles.permissions')}>
                            <i className="bi bi-key" aria-hidden />
                          </Button>
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
        </Col>
        {selected && (
          <Col lg={6}>
            <PermissionsEditor role={selected} />
          </Col>
        )}
      </Row>

      <Modal show={editing !== null} onHide={() => setEditing(null)} centered>
        <Form onSubmit={submit} noValidate>
          <Modal.Header closeButton>
            <Modal.Title>{editing === 'new' ? t('admins.roles.create') : t('admins.roles.edit')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            {(createState.error || updateState.error) && <ProblemAlert error={createState.error || updateState.error} codes={ROLE_ERROR_CODES} />}
            <Form.Group className="mb-3" controlId="role-name">
              <Form.Label>{t('admins.roles.fields.name')}</Form.Label>
              <Form.Control autoFocus value={values.name} onChange={(e) => setValues({ ...values, name: e.target.value })} isInvalid={!!nameError} />
              {nameError && <Form.Control.Feedback type="invalid">{t(nameError)}</Form.Control.Feedback>}
            </Form.Group>
            <Form.Group className="mb-3" controlId="role-description">
              <Form.Label>{t('admins.roles.fields.description')}</Form.Label>
              <Form.Control as="textarea" rows={2} value={values.description} onChange={(e) => setValues({ ...values, description: e.target.value })} />
            </Form.Group>
            <Form.Check type="switch" id="role-enabled" label={t('admins.roles.fields.enabled')} checked={values.enabled} onChange={(e) => setValues({ ...values, enabled: e.target.checked })} />
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
        title={t('admins.roles.delete')}
        body={deleting ? t('admins.roles.deleteConfirm', { name: deleting.name }) : ''}
        confirmLabel={t('common.actions.delete')}
        busy={removeState.isLoading}
        error={removeState.error}
        errorCodes={ROLE_ERROR_CODES}
        onCancel={() => {
          setDeleting(null);
          removeState.reset();
        }}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await remove(deleting.id).unwrap();
            if (selected?.id === deleting.id) setSelected(null);
            setDeleting(null);
          } catch {
            /* shown */
          }
        }}
      />
    </>
  );
}
