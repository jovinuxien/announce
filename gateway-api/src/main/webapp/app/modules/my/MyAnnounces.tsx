import { useCallback, useEffect, useState } from 'react';
import { Button, ButtonGroup, Nav, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import { announceApi } from 'app/shared/api/announceApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { AnnounceStatus, AnnounceSummaryResponse, STATUSES } from 'app/shared/model/announce';
import { formatDate } from 'app/shared/util/dates';
import { formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';

/** Drafts, published, sold, withdrawn and expired announces of the signed-in member, with the lifecycle actions. */
export default function MyAnnounces() {
  const { t, i18n } = useTranslation('my');
  const user = useAppSelector(s => s.auth.user);
  const [status, setStatus] = useState<AnnounceStatus>('PUBLISHED');
  const [items, setItems] = useState<AnnounceSummaryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!user) return;
    announceApi
      .list({ seller: user.id, status, size: 50, sort: 'newest' })
      .then(p => setItems(p.items))
      .catch(e => setError(describeError(e, t('loadFailed'))));
  }, [user, status, t]);

  useEffect(() => load(), [load]);

  const act = async (id: string, action: 'publish' | 'withdraw' | 'sold') => {
    setBusy(id);
    setError(null);
    try {
      if (action === 'publish') await announceApi.publish(id);
      else if (action === 'withdraw') await announceApi.withdraw(id);
      else await announceApi.markSold(id);
      load();
    } catch (e) {
      setError(describeError(e, t('actionFailed')));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h4 mb-0">{t('title')}</h1>
        <Button as={Link as any} to="/post" size="sm">
          {t('postNew')}
        </Button>
      </div>
      <Nav variant="tabs" activeKey={status} onSelect={k => k && setStatus(k as AnnounceStatus)} className="mb-3">
        {STATUSES.map(s => (
          <Nav.Item key={s}>
            <Nav.Link eventKey={s}>{t(`common:status.${s}`)}</Nav.Link>
          </Nav.Item>
        ))}
      </Nav>
      <ProblemAlert message={error} onClose={() => setError(null)} />
      {items.length === 0 ? (
        <p className="text-muted">{t('empty')}</p>
      ) : (
        <Table hover responsive size="sm" className="bg-white">
          <thead>
            <tr>
              <th>{t('col.title')}</th>
              <th>{t('col.kind')}</th>
              <th>{t('col.price')}</th>
              <th>{t('col.created')}</th>
              <th>{t('col.expires')}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {items.map(a => (
              <tr key={a.id}>
                <td>
                  <Link to={`/announce/${encodeURIComponent(a.id)}`}>{a.title}</Link>
                </td>
                <td>{t(`common:kind.${a.kind}`)}</td>
                <td>{a.kind === 'GOODS' ? (a.saleType === 'GIVEAWAY' ? t('common:saleType.GIVEAWAY') : formatMoney(a.price, undefined, i18n.language)) : ''}</td>
                <td>{formatDate(a.createdAt, i18n.language)}</td>
                <td>{formatDate(a.expiresAt, i18n.language)}</td>
                <td className="text-end">
                  <ButtonGroup size="sm">
                    {(a.status === 'DRAFT' || a.status === 'PUBLISHED') && (
                      <Button variant="outline-secondary" as={Link as any} to={`/post/edit/${encodeURIComponent(a.id)}`}>
                        {t('action.edit')}
                      </Button>
                    )}
                    {(a.status === 'DRAFT' || a.status === 'WITHDRAWN' || a.status === 'EXPIRED') && (
                      <Button variant="outline-primary" disabled={busy === a.id} onClick={() => act(a.id, 'publish')}>
                        {t('action.publish')}
                      </Button>
                    )}
                    {a.status === 'PUBLISHED' && (
                      <Button variant="outline-warning" disabled={busy === a.id} onClick={() => act(a.id, 'withdraw')}>
                        {t('action.withdraw')}
                      </Button>
                    )}
                    {a.status === 'PUBLISHED' && a.kind === 'GOODS' && (
                      <Button variant="outline-success" disabled={busy === a.id} onClick={() => act(a.id, 'sold')}>
                        {t('action.sold')}
                      </Button>
                    )}
                  </ButtonGroup>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}
