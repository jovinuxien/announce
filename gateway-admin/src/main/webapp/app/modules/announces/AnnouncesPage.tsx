import { useMemo, useState, type FormEvent } from 'react';
import { Badge, Button, Card, Col, Form, Row, Table } from 'react-bootstrap';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ANNOUNCE_STATUSES, KINDS } from 'app/shared/types';
import { useExpireAnnounceMutation, useListAnnouncesQuery, useWithdrawAnnounceMutation, type AnnounceSummaryResponse } from 'app/shared/api/announcesApi';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import Pagination from 'app/shared/ui/Pagination';
import KindBadge from 'app/shared/ui/KindBadge';
import StatusBadge from 'app/shared/ui/StatusBadge';
import AnnounceFacts from './AnnounceFacts';
import ModerationDialog, { type ModerationAction } from './ModerationDialog';
import { SORTS, activeFilterCount, defaultFilters, filtersFromSearch, filtersToParams, filtersToSearch, type AnnounceFilters } from './announceFilters';

export default function AnnouncesPage() {
  const { t, i18n } = useTranslation();
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const filters = useMemo(() => filtersFromSearch(search), [search]);
  const [draft, setDraft] = useState<AnnounceFilters>(filters);
  const { data, error, isLoading, isFetching } = useListAnnouncesQuery(filtersToParams(filters));
  const [pending, setPending] = useState<{ action: ModerationAction; announce: AnnounceSummaryResponse } | null>(null);
  const [withdraw, withdrawState] = useWithdrawAnnounceMutation();
  const [expire, expireState] = useExpireAnnounceMutation();

  const apply = (next: AnnounceFilters) => navigate(`/announces${filtersToSearch(next)}`);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    apply({ ...draft, page: 1 });
  };
  const reset = () => {
    setDraft(defaultFilters);
    apply(defaultFilters);
  };
  const set = <K extends keyof AnnounceFilters>(k: K, v: AnnounceFilters[K]) => setDraft((d) => ({ ...d, [k]: v }));

  const moderate = async (reason: string) => {
    if (!pending) return;
    const arg = { id: pending.announce.id, body: { reason } };
    try {
      if (pending.action === 'withdraw') await withdraw(arg).unwrap();
      else await expire(arg).unwrap();
      setPending(null);
    } catch {
      /* dialog shows the problem */
    }
  };

  const canWithdraw = (a: AnnounceSummaryResponse) => a.status === 'PUBLISHED' || a.status === 'DRAFT';
  const canExpire = (a: AnnounceSummaryResponse) => a.status === 'PUBLISHED';

  return (
    <>
      <PageHeader title={t('announces.title')} icon="bi-megaphone" subtitle={data ? t('announces.count', { total: data.total }) : undefined} />
      <Card className="shadow-sm mb-3">
        <Card.Body>
          <Form onSubmit={submit}>
            <Row className="g-2 align-items-end">
              <Col md={2}>
                <Form.Label htmlFor="f-kind" className="small mb-1">
                  {t('announces.filters.kind')}
                </Form.Label>
                <Form.Select id="f-kind" size="sm" value={draft.kind} onChange={(e) => set('kind', e.target.value as AnnounceFilters['kind'])}>
                  <option value="">{t('common.all')}</option>
                  {KINDS.map((k) => (
                    <option key={k} value={k}>
                      {t(`common.kind.${k}`)}
                    </option>
                  ))}
                </Form.Select>
              </Col>
              <Col md={2}>
                <Form.Label htmlFor="f-status" className="small mb-1">
                  {t('announces.filters.status')}
                </Form.Label>
                <Form.Select id="f-status" size="sm" value={draft.status} onChange={(e) => set('status', e.target.value as AnnounceFilters['status'])}>
                  <option value="">{t('common.all')}</option>
                  {ANNOUNCE_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {t(`common.status.${s}`)}
                    </option>
                  ))}
                </Form.Select>
              </Col>
              <Col md={1}>
                <Form.Label htmlFor="f-seller" className="small mb-1">
                  {t('announces.filters.seller')}
                </Form.Label>
                <Form.Control id="f-seller" size="sm" inputMode="numeric" value={draft.seller} onChange={(e) => set('seller', e.target.value.replace(/\D/g, ''))} />
              </Col>
              <Col md={1}>
                <Form.Label htmlFor="f-region" className="small mb-1">
                  {t('announces.filters.region')}
                </Form.Label>
                <Form.Control id="f-region" size="sm" inputMode="numeric" value={draft.region} onChange={(e) => set('region', e.target.value.replace(/\D/g, ''))} />
              </Col>
              <Col md={3}>
                <Form.Label htmlFor="f-q" className="small mb-1">
                  {t('announces.filters.text')}
                </Form.Label>
                <Form.Control id="f-q" size="sm" value={draft.q} onChange={(e) => set('q', e.target.value)} placeholder={t('announces.filters.textPlaceholder')} />
              </Col>
              <Col md={2}>
                <Form.Label htmlFor="f-sort" className="small mb-1">
                  {t('announces.filters.sort')}
                </Form.Label>
                <Form.Select id="f-sort" size="sm" value={draft.sort} onChange={(e) => set('sort', e.target.value as AnnounceFilters['sort'])}>
                  {SORTS.map((s) => (
                    <option key={s} value={s}>
                      {t(`announces.sort.${s}`)}
                    </option>
                  ))}
                </Form.Select>
              </Col>
              <Col md="auto" className="d-flex gap-2">
                <Button type="submit" size="sm" disabled={isFetching}>
                  <i className="bi bi-funnel me-1" aria-hidden />
                  {t('common.actions.apply')}
                </Button>
                <Button type="button" size="sm" variant="outline-secondary" onClick={reset} disabled={activeFilterCount(filters) === 0 && filters.sort === 'newest'}>
                  {t('common.actions.reset')}
                </Button>
              </Col>
            </Row>
          </Form>
        </Card.Body>
      </Card>

      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && data.items.length === 0 && <EmptyState hint={t('announces.emptyHint')} />}
      {data && data.items.length > 0 && (
        <Card className="shadow-sm">
          <Table hover responsive className="mb-0">
            <thead>
              <tr>
                <th>{t('announces.columns.announce')}</th>
                <th>{t('announces.columns.kind')}</th>
                <th>{t('announces.columns.status')}</th>
                <th>{t('announces.columns.seller')}</th>
                <th>{t('announces.columns.facts')}</th>
                <th>{t('announces.columns.dates')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((a) => (
                <tr key={a.id}>
                  <td style={{ maxWidth: 320 }}>
                    <div className="d-flex gap-2 align-items-center">
                      {a.pictureUrl ? (
                        <img src={a.pictureUrl} alt="" width={40} height={40} className="rounded object-fit-cover flex-shrink-0" />
                      ) : (
                        <div className="rounded bg-light border flex-shrink-0" style={{ width: 40, height: 40 }} />
                      )}
                      <div className="text-truncate">
                        <Link to={`/announces/${encodeURIComponent(a.id)}`} className="fw-semibold text-decoration-none">
                          {a.title}
                        </Link>
                        <div className="small text-muted text-truncate">
                          {a.location?.city}
                          {a.location?.postalCode && ` ${a.location.postalCode}`}
                          {a.categoryId != null && ` · ${t('announces.fields.category')} ${a.categoryId}`}
                        </div>
                        {a.moderationReason && (
                          <div className="small text-warning-emphasis">
                            <i className="bi bi-shield-exclamation me-1" aria-hidden />
                            {t('announces.moderatedBy', { by: a.moderatedBy ?? '?' })}: {a.moderationReason}
                          </div>
                        )}
                      </div>
                    </div>
                  </td>
                  <td>
                    <KindBadge kind={a.kind} />
                  </td>
                  <td>
                    <StatusBadge status={a.status} />
                  </td>
                  <td>
                    <Link to={`/users/${a.sellerId}`}>#{a.sellerId}</Link>
                  </td>
                  <td>
                    <AnnounceFacts a={a} />
                  </td>
                  <td className="small text-muted">
                    <div>
                      {t('announces.fields.created')}: {formatDateTime(a.createdAt, i18n.language)}
                    </div>
                    {a.publishedAt && (
                      <div>
                        {t('announces.fields.published')}: {formatDateTime(a.publishedAt, i18n.language)}
                      </div>
                    )}
                    {a.expiresAt && (
                      <div>
                        {t('announces.fields.expires')}: {formatDateTime(a.expiresAt, i18n.language)}
                      </div>
                    )}
                  </td>
                  <td className="text-end text-nowrap">
                    <Button size="sm" variant="outline-warning" className="me-1" disabled={!canWithdraw(a)} onClick={() => setPending({ action: 'withdraw', announce: a })}>
                      {t('announces.actions.withdraw')}
                    </Button>
                    <Button size="sm" variant="outline-dark" disabled={!canExpire(a)} onClick={() => setPending({ action: 'expire', announce: a })}>
                      {t('announces.actions.expire')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      {data && (
        <Pagination
          page={data.page}
          size={data.size}
          total={data.total}
          onPage={(p) => apply({ ...filters, page: p })}
          onSize={(s) => apply({ ...filters, size: s, page: 1 })}
        />
      )}
      {activeFilterCount(filters) > 0 && (
        <div className="mt-2">
          <Badge bg="light" text="dark">
            {t('announces.activeFilters', { count: activeFilterCount(filters) })}
          </Badge>
        </div>
      )}

      <ModerationDialog
        show={pending !== null}
        action={pending?.action ?? 'withdraw'}
        announceTitle={pending?.announce.title}
        busy={withdrawState.isLoading || expireState.isLoading}
        error={pending?.action === 'withdraw' ? withdrawState.error : expireState.error}
        onSubmit={moderate}
        onCancel={() => setPending(null)}
      />
    </>
  );
}
