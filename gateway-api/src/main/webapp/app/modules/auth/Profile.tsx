import { Card, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { features } from 'app/config/features';
import { useAppSelector } from 'app/config/store';
import PayoutsCard from 'app/modules/trade/PayoutsCard';

export default function Profile() {
  const { t } = useTranslation('auth');
  const user = useAppSelector(s => s.auth.user);
  if (!user) return null;
  return (
    <div className="mx-auto" style={{ maxWidth: 560 }}>
      <Card>
        <Card.Body>
          <h1 className="h4 mb-3">{t('profile.title')}</h1>
          <Table size="sm" borderless className="mb-0">
            <tbody>
              <tr>
                <th>{t('profile.id')}</th>
                <td>{user.id}</td>
              </tr>
              <tr>
                <th>{t('register.username')}</th>
                <td>{user.username}</td>
              </tr>
              <tr>
                <th>{t('register.nickname')}</th>
                <td>{user.nickname}</td>
              </tr>
              <tr>
                <th>{t('register.mobile')}</th>
                <td>{user.mobile || '—'}</td>
              </tr>
            </tbody>
          </Table>
          <p className="text-muted small mb-0 mt-3">{t('profile.readOnly')}</p>
        </Card.Body>
      </Card>
      {features.trade && (
        <div className="mt-3">
          <PayoutsCard />
        </div>
      )}
    </div>
  );
}
