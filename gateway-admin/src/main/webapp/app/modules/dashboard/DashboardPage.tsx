import { useState } from 'react';
import { Card, Col, Form, Row, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useGetDashboardQuery, type AnnounceCount, type ListingCount } from 'app/shared/api/dashboardApi';
import { ANNOUNCE_STATUSES, KINDS } from 'app/shared/types';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import KindBadge from 'app/shared/ui/KindBadge';
import StatusBadge from 'app/shared/ui/StatusBadge';
import MiniChart from './MiniChart';

function Tile({ icon, label, value, to }: { icon: string; label: string; value: number | string; to?: string }) {
  const body = (
    <Card className="stat-tile h-100 shadow-sm">
      <Card.Body className="d-flex align-items-center gap-3">
        <i className={`bi ${icon} fs-2 text-primary`} aria-hidden />
        <div>
          <div className="stat-value">{value}</div>
          <div className="text-muted small">{label}</div>
        </div>
      </Card.Body>
    </Card>
  );
  return to ? (
    <Link to={to} className="text-decoration-none text-reset">
      {body}
    </Link>
  ) : (
    body
  );
}

function AnnounceMatrix({ rows }: { rows: AnnounceCount[] }) {
  const { t } = useTranslation();
  const cell = (kind: string, status: string) => rows.find((r) => r.kind === kind && r.status === status)?.total ?? 0;
  const rowTotal = (kind: string) => rows.filter((r) => r.kind === kind).reduce((s, r) => s + r.total, 0);
  return (
    <Table size="sm" className="mb-0" responsive>
      <thead>
        <tr>
          <th>{t('dashboard.kind')}</th>
          {ANNOUNCE_STATUSES.map((s) => (
            <th key={s} className="text-end">
              <StatusBadge status={s} />
            </th>
          ))}
          <th className="text-end">{t('dashboard.total')}</th>
        </tr>
      </thead>
      <tbody>
        {KINDS.map((k) => (
          <tr key={k}>
            <td>
              <Link to={`/announces?kind=${k}`}>
                <KindBadge kind={k} />
              </Link>
            </td>
            {ANNOUNCE_STATUSES.map((s) => (
              <td key={s} className="text-end">
                <Link to={`/announces?kind=${k}&status=${s}`} className="text-reset text-decoration-none">
                  {cell(k, s)}
                </Link>
              </td>
            ))}
            <td className="text-end fw-semibold">{rowTotal(k)}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

function ListingMatrix({ rows }: { rows: ListingCount[] }) {
  const { t } = useTranslation();
  const sources = Array.from(new Set(rows.map((r) => r.source))).sort();
  const statuses = Array.from(new Set(rows.map((r) => r.status))).sort();
  if (!sources.length) return <div className="text-muted small">{t('dashboard.noListings')}</div>;
  return (
    <Table size="sm" className="mb-0" responsive>
      <thead>
        <tr>
          <th>{t('dashboard.source')}</th>
          {statuses.map((s) => (
            <th key={s} className="text-end">
              <StatusBadge status={s} />
            </th>
          ))}
          <th className="text-end">{t('dashboard.total')}</th>
        </tr>
      </thead>
      <tbody>
        {sources.map((src) => (
          <tr key={src}>
            <td className="fw-semibold">{src}</td>
            {statuses.map((s) => (
              <td key={s} className="text-end">
                {rows.find((r) => r.source === src && r.status === s)?.total ?? 0}
              </td>
            ))}
            <td className="text-end fw-semibold">{rows.filter((r) => r.source === src).reduce((a, r) => a + r.total, 0)}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

export default function DashboardPage() {
  const { t, i18n } = useTranslation();
  const [days, setDays] = useState(30);
  const { data, error, isLoading, isFetching } = useGetDashboardQuery({ days });

  return (
    <>
      <PageHeader
        title={t('dashboard.title')}
        icon="bi-speedometer2"
        subtitle={data ? t('dashboard.generatedAt', { at: formatDateTime(data.generatedAt, i18n.language) }) : undefined}
        actions={
          <Form.Select size="sm" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label={t('dashboard.window')} disabled={isFetching}>
            {[7, 14, 30, 90].map((d) => (
              <option key={d} value={d}>
                {t('dashboard.lastDays', { days: d })}
              </option>
            ))}
          </Form.Select>
        }
      />
      {error ? <ProblemAlert error={error} /> : null}
      {isLoading && <LoadingBlock />}
      {data && (
        <>
          <Row xs={1} sm={2} lg={5} className="g-3 mb-3">
            <Col>
              <Tile icon="bi-people" label={t('dashboard.tiles.members')} value={data.members} to="/users" />
            </Col>
            <Col>
              <Tile icon="bi-person-badge" label={t('dashboard.tiles.admins')} value={data.admins} to="/admins" />
            </Col>
            <Col>
              <Tile icon="bi-diagram-3" label={t('dashboard.tiles.categories')} value={data.categories} to="/categories" />
            </Col>
            <Col>
              <Tile icon="bi-award" label={t('dashboard.tiles.brands')} value={data.brands} to="/brands" />
            </Col>
            <Col>
              <Tile icon="bi-cursor" label={t('dashboard.tiles.listingClicks')} value={data.listingClicks} to="/listings" />
            </Col>
          </Row>
          <Row className="g-3 mb-3">
            <Col lg={7}>
              <Card className="shadow-sm h-100">
                <Card.Header>{t('dashboard.announcesByKind')}</Card.Header>
                <Card.Body>
                  <AnnounceMatrix rows={data.announces} />
                </Card.Body>
              </Card>
            </Col>
            <Col lg={5}>
              <Card className="shadow-sm h-100">
                <Card.Header>{t('dashboard.listingsBySource')}</Card.Header>
                <Card.Body>
                  <ListingMatrix rows={data.listings} />
                </Card.Body>
              </Card>
            </Col>
          </Row>
          <Row className="g-3">
            <Col lg={6}>
              <Card className="shadow-sm">
                <Card.Header>{t('dashboard.newMembers', { days })}</Card.Header>
                <Card.Body>
                  <MiniChart series={data.newMembersPerDay} title={t('dashboard.newMembers', { days })} color="#198754" />
                </Card.Body>
              </Card>
            </Col>
            <Col lg={6}>
              <Card className="shadow-sm">
                <Card.Header>{t('dashboard.newAnnounces', { days })}</Card.Header>
                <Card.Body>
                  <MiniChart series={data.newAnnouncesPerDay} title={t('dashboard.newAnnounces', { days })} />
                </Card.Body>
              </Card>
            </Col>
          </Row>
        </>
      )}
    </>
  );
}
