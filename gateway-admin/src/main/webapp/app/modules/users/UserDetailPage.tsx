import { useState } from 'react';
import { Button, ButtonGroup, Card, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { USER_STATUSES, useGetUserQuery, useSetUserStatusMutation, type UserStatus } from 'app/shared/api/usersApi';
import { formatDate, formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import ConfirmDialog from 'app/shared/ui/ConfirmDialog';
import UserStatusBadge from './UserStatusBadge';

export default function UserDetailPage() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation();
  const userId = Number(id);
  const { data: u, error, isLoading } = useGetUserQuery(userId, { skip: !Number.isInteger(userId) });
  const [target, setTarget] = useState<UserStatus | null>(null);
  const [setStatus, setState] = useSetUserStatusMutation();

  const rows: [string, React.ReactNode][] = u
    ? [
        [t('members.fields.username'), u.username],
        [t('members.fields.nickname'), u.nickname],
        [t('members.fields.mobile'), u.mobile],
        [t('members.fields.gender'), u.gender == null ? '—' : t(`members.genders.${u.gender}`, { defaultValue: String(u.gender) })],
        [t('members.fields.birthday'), formatDate(u.birthday, i18n.language) || '—'],
        [t('members.fields.level'), u.userLevel ?? '—'],
        [t('members.status'), <UserStatusBadge key="s" status={u.status} />],
        [t('members.fields.lastLogin'), `${formatDateTime(u.lastLoginTime, i18n.language)} ${u.lastLoginIp ?? ''}`],
        [t('members.fields.registered'), formatDateTime(u.addTime, i18n.language)],
      ]
    : [];

  return (
    <>
      <PageHeader
        title={u ? `${u.username} (#${u.id})` : t('members.detail')}
        icon="bi-person"
        actions={
          <>
            <Link to="/users" className="btn btn-outline-secondary btn-sm">
              <i className="bi bi-arrow-left me-1" aria-hidden />
              {t('common.actions.back')}
            </Link>
            <Link to={`/announces?seller=${userId}`} className="btn btn-outline-primary btn-sm">
              <i className="bi bi-megaphone me-1" aria-hidden />
              {t('members.announcesOf')}
            </Link>
          </>
        }
      />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {u && (
        <Card className="shadow-sm" style={{ maxWidth: 720 }}>
          <Card.Body>
            <div className="d-flex align-items-center gap-3 mb-3">
              {u.avatar ? <img src={u.avatar} alt="" width={64} height={64} className="rounded-circle" /> : <i className="bi bi-person-circle fs-1 text-secondary" aria-hidden />}
              <div>
                <div className="fw-semibold">{t('members.setStatus')}</div>
                <ButtonGroup size="sm">
                  {USER_STATUSES.map((s) => (
                    <Button key={s} variant={s === u.status ? 'secondary' : 'outline-secondary'} disabled={s === u.status} onClick={() => setTarget(s)}>
                      {t(`members.statuses.${s}`)}
                    </Button>
                  ))}
                </ButtonGroup>
              </div>
            </div>
            <Table size="sm" className="mb-0">
              <tbody>
                {rows.map(([label, value]) => (
                  <tr key={label}>
                    <th className="text-muted fw-normal" style={{ width: '35%' }}>
                      {label}
                    </th>
                    <td>{value ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card.Body>
        </Card>
      )}
      <ConfirmDialog
        show={target !== null}
        title={t('members.setStatus')}
        body={target !== null && u ? t('members.setStatusConfirm', { user: u.username, status: t(`members.statuses.${target}`) }) : ''}
        variant={target === 0 ? 'success' : 'danger'}
        busy={setState.isLoading}
        error={setState.error}
        onCancel={() => setTarget(null)}
        onConfirm={async () => {
          if (target === null) return;
          try {
            await setStatus({ id: userId, body: { status: target } }).unwrap();
            setTarget(null);
          } catch {
            /* shown */
          }
        }}
      />
    </>
  );
}
