import { useState, type FormEvent } from 'react';
import { Badge, Button, ButtonGroup, Card, Form, Modal, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useCreateAdminMutation, useDeleteAdminMutation, useListAdminsQuery, useUpdateAdminMutation, type AdminRequest, type AdminResponse } from 'app/shared/api/adminsApi';
import { useGetAllRolesQuery } from 'app/shared/api/rolesApi';
import { useAuth } from 'app/shared/auth/useAuth';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import ConfirmDialog from 'app/shared/ui/ConfirmDialog';
import { usePaging } from 'app/shared/ui/usePaging';

/** Problem `code`s of EdgeAdminAccountController (EdgeErrors.Conflict). */
export const ADMIN_ERROR_CODES: Record<string, string> = {
  'username-taken': 'admins.errors.usernameTaken',
  'self-delete': 'admins.errors.selfDelete',
};

interface FormValues {
  username: string;
  password: string;
  avatar: string;
  roleIds: number[];
}
const empty: FormValues = { username: '', password: '', avatar: '', roleIds: [] };

export default function AdminsPage() {
  const { t, i18n } = useTranslation();
  const { admin: me } = useAuth();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const q = params.get('q') ?? '';
  const [draftQ, setDraftQ] = useState(q);
  const { data, error, isLoading } = useListAdminsQuery({ q: q || undefined, page, size });
  const { data: roles } = useGetAllRolesQuery();
  const [editing, setEditing] = useState<AdminResponse | 'new' | null>(null);
  const [deleting, setDeleting] = useState<AdminResponse | null>(null);
  const [values, setValues] = useState<FormValues>(empty);
  const [errors, setErrors] = useState<Partial<Record<keyof FormValues, string>>>({});
  const [create, createState] = useCreateAdminMutation();
  const [update, updateState] = useUpdateAdminMutation();
  const [remove, removeState] = useDeleteAdminMutation();

  const roleName = (id: number) => roles?.find((r) => r.id === id)?.name ?? `#${id}`;
  const open = (a: AdminResponse | 'new') => {
    setEditing(a);
    setErrors({});
    setValues(a === 'new' ? empty : { username: a.username, password: '', avatar: a.avatar ?? '', roleIds: a.roleIds });
  };
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!values.username.trim()) errs.username = 'admins.errors.usernameRequired';
    if (editing === 'new' && values.password.length < 6) errs.password = 'admins.errors.passwordTooShort';
    if (editing !== 'new' && values.password && values.password.length < 6) errs.password = 'admins.errors.passwordTooShort';
    if (values.roleIds.length === 0) errs.roleIds = 'admins.errors.roleRequired';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const body: AdminRequest = { username: values.username.trim(), avatar: values.avatar.trim() || null, roleIds: values.roleIds, password: values.password || null };
    try {
      if (editing === 'new') await create(body).unwrap();
      else if (editing) await update({ id: editing.id, body }).unwrap();
      setEditing(null);
    } catch {
      /* shown */
    }
  };
  const toggleRole = (id: number) => setValues((v) => ({ ...v, roleIds: v.roleIds.includes(id) ? v.roleIds.filter((r) => r !== id) : [...v.roleIds, id] }));

  return (
    <>
      <PageHeader
        title={t('admins.title')}
        icon="bi-person-badge"
        actions={
          <Button onClick={() => open('new')}>
            <i className="bi bi-plus-lg me-1" aria-hidden />
            {t('admins.create')}
          </Button>
        }
      />
      <Card className="shadow-sm mb-3">
        <Card.Body>
          <Form
            className="d-flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              setParam('q', draftQ.trim());
            }}
          >
            <Form.Control size="sm" value={draftQ} onChange={(e) => setDraftQ(e.target.value)} placeholder={t('admins.searchPlaceholder')} aria-label={t('common.actions.search')} style={{ maxWidth: 360 }} />
            <Button type="submit" size="sm">
              {t('common.actions.search')}
            </Button>
          </Form>
        </Card.Body>
      </Card>
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && data.items.length === 0 && <EmptyState />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>#</th>
                <th>{t('admins.fields.username')}</th>
                <th>{t('admins.fields.roles')}</th>
                <th>{t('admins.fields.lastLogin')}</th>
                <th>{t('admins.fields.created')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((a) => (
                <tr key={a.id}>
                  <td>{a.id}</td>
                  <td className="fw-semibold">
                    {a.avatar && <img src={a.avatar} alt="" width={24} height={24} className="rounded-circle me-2" />}
                    {a.username}
                    {me?.id === a.id && (
                      <Badge bg="info" text="dark" className="ms-2">
                        {t('admins.you')}
                      </Badge>
                    )}
                  </td>
                  <td>
                    {a.roleIds.map((r) => (
                      <Badge key={r} bg="secondary" className="me-1">
                        {roleName(r)}
                      </Badge>
                    ))}
                  </td>
                  <td className="small text-muted">
                    {formatDateTime(a.lastLoginTime, i18n.language)} {a.lastLoginIp}
                  </td>
                  <td className="small text-muted">{formatDateTime(a.addTime, i18n.language)}</td>
                  <td className="text-end">
                    <ButtonGroup size="sm">
                      <Button variant="outline-secondary" onClick={() => open(a)} title={t('common.actions.edit')}>
                        <i className="bi bi-pencil" aria-hidden />
                      </Button>
                      <Button variant="outline-danger" onClick={() => setDeleting(a)} title={t('common.actions.delete')} disabled={me?.id === a.id}>
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
            <Modal.Title>{editing === 'new' ? t('admins.create') : t('admins.edit')}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            {(createState.error || updateState.error) && <ProblemAlert error={createState.error || updateState.error} codes={ADMIN_ERROR_CODES} />}
            <Form.Group className="mb-3" controlId="admin-username">
              <Form.Label>{t('admins.fields.username')}</Form.Label>
              <Form.Control autoFocus autoComplete="off" value={values.username} onChange={(e) => setValues({ ...values, username: e.target.value })} isInvalid={!!errors.username} />
              {errors.username && <Form.Control.Feedback type="invalid">{t(errors.username)}</Form.Control.Feedback>}
            </Form.Group>
            <Form.Group className="mb-3" controlId="admin-password">
              <Form.Label>{editing === 'new' ? t('admins.fields.password') : t('admins.fields.newPassword')}</Form.Label>
              <Form.Control type="password" autoComplete="new-password" value={values.password} onChange={(e) => setValues({ ...values, password: e.target.value })} isInvalid={!!errors.password} />
              {editing !== 'new' && <Form.Text muted>{t('admins.passwordKeep')}</Form.Text>}
              {errors.password && <Form.Control.Feedback type="invalid">{t(errors.password)}</Form.Control.Feedback>}
            </Form.Group>
            <Form.Group className="mb-3" controlId="admin-avatar">
              <Form.Label>{t('admins.fields.avatar')}</Form.Label>
              <Form.Control value={values.avatar} onChange={(e) => setValues({ ...values, avatar: e.target.value })} />
            </Form.Group>
            <Form.Group className="mb-1">
              <Form.Label>{t('admins.fields.roles')}</Form.Label>
              {(roles ?? []).map((r) => (
                <Form.Check key={r.id} id={`role-${r.id}`} type="checkbox" label={r.name} checked={values.roleIds.includes(r.id)} onChange={() => toggleRole(r.id)} />
              ))}
              {roles && roles.length === 0 && <div className="text-muted small">{t('admins.noRoles')}</div>}
              {errors.roleIds && <div className="text-danger small">{t(errors.roleIds)}</div>}
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
        show={deleting !== null}
        title={t('admins.delete')}
        body={deleting ? t('admins.deleteConfirm', { name: deleting.username }) : ''}
        confirmLabel={t('common.actions.delete')}
        busy={removeState.isLoading}
        error={removeState.error}
        errorCodes={ADMIN_ERROR_CODES}
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
