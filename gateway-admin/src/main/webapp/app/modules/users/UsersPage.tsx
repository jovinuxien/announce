import { useState, type FormEvent } from 'react';
import { Button, Card, Col, Form, Row, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { USER_STATUSES, useListUsersQuery, type UserStatus } from 'app/shared/api/usersApi';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import { usePaging } from 'app/shared/ui/usePaging';
import UserStatusBadge from './UserStatusBadge';

export default function UsersPage() {
  const { t, i18n } = useTranslation();
  const { params, page, size, setPage, setSize, setParam } = usePaging();
  const q = params.get('q') ?? '';
  const status = params.get('status') ?? '';
  const [draft, setDraft] = useState(q);
  const { data, error, isLoading } = useListUsersQuery({ q: q || undefined, status: status === '' ? '' : (Number(status) as UserStatus), page, size });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setParam('q', draft.trim());
  };

  return (
    <>
      <PageHeader title={t('members.title')} icon="bi-people" subtitle={data ? t('members.count', { total: data.total }) : undefined} />
      <Card className="shadow-sm mb-3">
        <Card.Body>
          <Form onSubmit={submit}>
            <Row className="g-2 align-items-end">
              <Col md={5}>
                <Form.Label htmlFor="u-q" className="small mb-1">
                  {t('members.search')}
                </Form.Label>
                <Form.Control id="u-q" size="sm" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder={t('members.searchPlaceholder')} />
              </Col>
              <Col md={3}>
                <Form.Label htmlFor="u-status" className="small mb-1">
                  {t('members.status')}
                </Form.Label>
                <Form.Select id="u-status" size="sm" value={status} onChange={(e) => setParam('status', e.target.value)}>
                  <option value="">{t('common.all')}</option>
                  {USER_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {t(`members.statuses.${s}`)}
                    </option>
                  ))}
                </Form.Select>
              </Col>
              <Col md="auto">
                <Button type="submit" size="sm">
                  <i className="bi bi-search me-1" aria-hidden />
                  {t('common.actions.search')}
                </Button>
              </Col>
            </Row>
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
                <th>{t('members.fields.username')}</th>
                <th>{t('members.fields.nickname')}</th>
                <th>{t('members.fields.mobile')}</th>
                <th>{t('members.status')}</th>
                <th>{t('members.fields.lastLogin')}</th>
                <th>{t('members.fields.registered')}</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>
                    <Link to={`/users/${u.id}`} className="fw-semibold text-decoration-none">
                      {u.avatar && <img src={u.avatar} alt="" width={24} height={24} className="rounded-circle me-2" />}
                      {u.username}
                    </Link>
                  </td>
                  <td>{u.nickname}</td>
                  <td>{u.mobile}</td>
                  <td>
                    <UserStatusBadge status={u.status} />
                  </td>
                  <td className="small text-muted">{formatDateTime(u.lastLoginTime, i18n.language)}</td>
                  <td className="small text-muted">{formatDateTime(u.addTime, i18n.language)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} onSize={setSize} />}
    </>
  );
}
