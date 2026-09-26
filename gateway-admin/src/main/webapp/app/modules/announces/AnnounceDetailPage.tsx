import { useState } from 'react';
import { Alert, Badge, Button, Card, Col, Row, Table } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useExpireAnnounceMutation, useGetAnnounceQuery, useWithdrawAnnounceMutation, type AnnounceResponse } from 'app/shared/api/announcesApi';
import { formatDateTime, formatMoney } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import KindBadge from 'app/shared/ui/KindBadge';
import StatusBadge from 'app/shared/ui/StatusBadge';
import ModerationDialog, { type ModerationAction } from './ModerationDialog';

function Row2({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <tr>
      <th className="text-muted fw-normal" style={{ width: '35%' }}>
        {label}
      </th>
      <td>{children}</td>
    </tr>
  );
}

function KindBlock({ a }: { a: AnnounceResponse }) {
  const { t, i18n } = useTranslation();
  const lng = i18n.language;
  if (a.goods) {
    const g = a.goods;
    return (
      <Table size="sm" className="mb-0">
        <tbody>
          <Row2 label={t('announces.fields.price')}>{g.saleType === 'GIVEAWAY' ? t('common.saleType.GIVEAWAY') : formatMoney(g.price, undefined, lng)}</Row2>
          <Row2 label={t('announces.fields.saleType')}>{g.saleType && t(`common.saleType.${g.saleType}`)}</Row2>
          <Row2 label={t('announces.fields.condition')}>{g.condition && t(`common.condition.${g.condition}`)}</Row2>
          <Row2 label={t('announces.fields.brand')}>{g.brandId ?? '—'}</Row2>
          <Row2 label={t('announces.fields.unit')}>{g.unit ?? '—'}</Row2>
          <Row2 label={t('announces.fields.attributes')}>
            {g.attributes.length === 0 ? '—' : g.attributes.map((x) => <div key={x.name}>{`${x.name}: ${x.value}`}</div>)}
          </Row2>
          <Row2 label={t('announces.fields.variants')}>{g.variants.length}</Row2>
        </tbody>
      </Table>
    );
  }
  if (a.job) {
    const j = a.job;
    return (
      <Table size="sm" className="mb-0">
        <tbody>
          <Row2 label={t('announces.fields.employer')}>{j.employer}</Row2>
          <Row2 label={t('announces.fields.contractType')}>{t(`common.contractType.${j.contractType}`)}</Row2>
          <Row2 label={t('announces.fields.salary')}>
            {j.salaryMin == null && j.salaryMax == null ? '—' : `${formatMoney(j.salaryMin, j.currency, lng)} – ${formatMoney(j.salaryMax, j.currency, lng)}`}
          </Row2>
          <Row2 label={t('announces.fields.deadline')}>{formatDateTime(j.applicationDeadline, lng) || '—'}</Row2>
          <Row2 label={t('announces.fields.howToApply')}>{j.howToApply ?? '—'}</Row2>
        </tbody>
      </Table>
    );
  }
  if (a.event) {
    const e = a.event;
    return (
      <Table size="sm" className="mb-0">
        <tbody>
          <Row2 label={t('announces.fields.startsAt')}>{formatDateTime(e.startsAt, lng)}</Row2>
          <Row2 label={t('announces.fields.endsAt')}>{formatDateTime(e.endsAt, lng)}</Row2>
          <Row2 label={t('announces.fields.venue')}>{e.venue ?? '—'}</Row2>
          <Row2 label={t('announces.fields.organizer')}>{e.organizer ?? '—'}</Row2>
          <Row2 label={t('announces.fields.ticketed')}>{e.ticketed ? formatMoney(e.ticketPrice, undefined, lng) : t('announces.fields.free')}</Row2>
          <Row2 label={t('announces.fields.capacity')}>{e.capacity ?? '—'}</Row2>
        </tbody>
      </Table>
    );
  }
  return null;
}

export default function AnnounceDetailPage() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation();
  const { data: a, error, isLoading } = useGetAnnounceQuery(id, { skip: !id });
  const [action, setAction] = useState<ModerationAction | null>(null);
  const [withdraw, withdrawState] = useWithdrawAnnounceMutation();
  const [expire, expireState] = useExpireAnnounceMutation();
  const [result, setResult] = useState<AnnounceResponse | null>(null);

  const moderate = async (reason: string) => {
    if (!a || !action) return;
    try {
      const updated = action === 'withdraw' ? await withdraw({ id: a.id, body: { reason } }).unwrap() : await expire({ id: a.id, body: { reason } }).unwrap();
      setResult(updated);
      setAction(null);
    } catch {
      /* shown */
    }
  };

  return (
    <>
      <PageHeader
        title={a?.title ?? t('announces.detail.title')}
        icon="bi-megaphone"
        subtitle={
          a && (
            <span className="d-inline-flex gap-2 align-items-center">
              <KindBadge kind={a.kind} />
              <StatusBadge status={a.status} />
              <code>{a.id}</code>
            </span>
          )
        }
        actions={
          <>
            <Link to="/announces" className="btn btn-outline-secondary btn-sm">
              <i className="bi bi-arrow-left me-1" aria-hidden />
              {t('common.actions.back')}
            </Link>
            <Button variant="warning" size="sm" disabled={!a || (a.status !== 'PUBLISHED' && a.status !== 'DRAFT')} onClick={() => setAction('withdraw')}>
              {t('announces.actions.withdraw')}
            </Button>
            <Button variant="dark" size="sm" disabled={!a || a.status !== 'PUBLISHED'} onClick={() => setAction('expire')}>
              {t('announces.actions.expire')}
            </Button>
          </>
        }
      />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {result && (
        <Alert variant="success" dismissible onClose={() => setResult(null)}>
          {t('announces.moderation.done')} <StatusBadge status={result.status} />
        </Alert>
      )}
      {a && (
        <Row className="g-3">
          <Col lg={7}>
            <Card className="shadow-sm mb-3">
              <Card.Header>{t('announces.detail.general')}</Card.Header>
              <Card.Body>
                <Table size="sm" className="mb-0">
                  <tbody>
                    <Row2 label={t('announces.columns.seller')}>
                      <Link to={`/users/${a.sellerId}`}>#{a.sellerId}</Link>
                    </Row2>
                    <Row2 label={t('announces.fields.category')}>{a.categoryId}</Row2>
                    <Row2 label={t('announces.fields.brief')}>{a.brief}</Row2>
                    <Row2 label={t('announces.fields.description')}>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{a.description}</div>
                    </Row2>
                    <Row2 label={t('announces.fields.keywords')}>
                      {a.keywords.map((k) => (
                        <Badge key={k} bg="light" text="dark" className="me-1">
                          {k}
                        </Badge>
                      ))}
                    </Row2>
                    <Row2 label={t('announces.fields.location')}>
                      {a.location ? [a.location.postalCode, a.location.city, a.location.regionId != null ? `${t('announces.filters.region')} ${a.location.regionId}` : null].filter(Boolean).join(' · ') : '—'}
                    </Row2>
                    <Row2 label={t('announces.fields.created')}>{formatDateTime(a.createdAt, i18n.language)}</Row2>
                    <Row2 label={t('announces.fields.updated')}>{formatDateTime(a.updatedAt, i18n.language)}</Row2>
                    <Row2 label={t('announces.fields.published')}>{formatDateTime(a.publishedAt, i18n.language) || '—'}</Row2>
                    <Row2 label={t('announces.fields.expires')}>{formatDateTime(a.expiresAt, i18n.language) || '—'}</Row2>
                  </tbody>
                </Table>
              </Card.Body>
            </Card>
            {a.gallery.length > 0 && (
              <Card className="shadow-sm">
                <Card.Header>{t('announces.fields.gallery')}</Card.Header>
                <Card.Body className="d-flex flex-wrap gap-2">
                  {a.gallery.map((url) => (
                    <a key={url} href={url} target="_blank" rel="noreferrer noopener">
                      <img src={url} alt="" width={96} height={96} className="rounded border object-fit-cover" />
                    </a>
                  ))}
                </Card.Body>
              </Card>
            )}
          </Col>
          <Col lg={5}>
            <Card className="shadow-sm">
              <Card.Header>
                {t(`announces.detail.kindBlock.${a.kind}`)}
              </Card.Header>
              <Card.Body>
                <KindBlock a={a} />
              </Card.Body>
            </Card>
          </Col>
        </Row>
      )}
      <ModerationDialog
        show={action !== null}
        action={action ?? 'withdraw'}
        announceTitle={a?.title}
        busy={withdrawState.isLoading || expireState.isLoading}
        error={action === 'withdraw' ? withdrawState.error : expireState.error}
        onSubmit={moderate}
        onCancel={() => setAction(null)}
      />
    </>
  );
}
