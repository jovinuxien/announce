import { useState, type FormEvent } from 'react';
import { Alert, Button, Card, Col, Form, Row, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { LISTING_SOURCES, type ListingSource } from 'app/shared/types';
import { useGetListingStatsQuery, useImportListingsMutation, useReindexListingsMutation, type ListingRow } from 'app/shared/api/listingsApi';
import { formatDateTime, formatMoney, hostOf } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import StatusBadge from 'app/shared/ui/StatusBadge';
import EmptyState from 'app/shared/ui/EmptyState';

function ListingTable({ rows, showClicks }: { rows: ListingRow[]; showClicks?: boolean }) {
  const { t, i18n } = useTranslation();
  if (rows.length === 0) return <EmptyState />;
  return (
    <Table size="sm" hover responsive className="mb-0">
      <thead>
        <tr>
          <th>{t('listings.columns.title')}</th>
          <th>{t('listings.columns.source')}</th>
          <th className="text-end">{t('listings.columns.price')}</th>
          <th>{t('listings.columns.status')}</th>
          {showClicks && <th className="text-end">{t('listings.columns.clicks')}</th>}
          <th>{t('listings.columns.lastSeen')}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.id}>
            <td style={{ maxWidth: 360 }} className="text-truncate">
              <a href={r.url} target="_blank" rel="noreferrer noopener" title={r.url}>
                {r.title}
                <i className="bi bi-box-arrow-up-right ms-1 small" aria-hidden />
              </a>
              <div className="small text-muted">{hostOf(r.url)}</div>
            </td>
            <td>{r.source}</td>
            <td className="text-end">{formatMoney(r.price, r.currency, i18n.language)}</td>
            <td>
              <StatusBadge status={r.status} />
            </td>
            {showClicks && <td className="text-end">{r.clickCount}</td>}
            <td className="small text-muted">{formatDateTime(r.lastSeenAt, i18n.language)}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

export default function ListingsPage() {
  const { t } = useTranslation();
  const { data, error, isLoading, refetch, isFetching } = useGetListingStatsQuery({ limit: 10 });
  const [path, setPath] = useState('');
  const [source, setSource] = useState<ListingSource | ''>('');
  const [importFile, importState] = useImportListingsMutation();
  const [reindex, reindexState] = useReindexListingsMutation();

  const submitImport = async (e: FormEvent) => {
    e.preventDefault();
    if (!path.trim()) return;
    try {
      await importFile({ path: path.trim(), source: source || null }).unwrap();
    } catch {
      /* shown */
    }
  };

  const sources = data ? Array.from(new Set(data.counts.map((c) => c.source))).sort() : [];
  const statuses = data ? Array.from(new Set(data.counts.map((c) => c.status))).sort() : [];

  return (
    <>
      <PageHeader
        title={t('listings.title')}
        icon="bi-globe"
        subtitle={t('listings.subtitle')}
        actions={
          <>
            <Button variant="outline-secondary" size="sm" onClick={() => refetch()} disabled={isFetching}>
              <i className="bi bi-arrow-clockwise me-1" aria-hidden />
              {t('common.actions.refresh')}
            </Button>
            <Button variant="outline-primary" size="sm" onClick={() => reindex()} disabled={reindexState.isLoading}>
              {reindexState.isLoading ? <Spinner size="sm" animation="border" className="me-1" /> : <i className="bi bi-arrow-repeat me-1" aria-hidden />}
              {t('listings.reindex.button')}
            </Button>
          </>
        }
      />
      {error ? <ProblemAlert error={error} /> : null}
      {reindexState.error ? <ProblemAlert error={reindexState.error} /> : null}
      {reindexState.data && (
        <Alert variant="success" dismissible onClose={() => reindexState.reset()}>
          {t('listings.reindex.done', { indexed: reindexState.data.indexed })}
        </Alert>
      )}
      {isLoading && <LoadingBlock />}
      <Row className="g-3">
        <Col lg={5}>
          <Card className="shadow-sm mb-3">
            <Card.Header>{t('listings.counts.title')}</Card.Header>
            <Card.Body>
              {data && (
                <>
                  <div className="mb-2">
                    <span className="stat-value fs-3">{data.clicks}</span> <span className="text-muted small">{t('listings.counts.clicks')}</span>
                  </div>
                  {sources.length === 0 ? (
                    <div className="text-muted small">{t('listings.counts.empty')}</div>
                  ) : (
                    <Table size="sm" className="mb-0">
                      <thead>
                        <tr>
                          <th>{t('listings.columns.source')}</th>
                          {statuses.map((s) => (
                            <th key={s} className="text-end">
                              <StatusBadge status={s} />
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {sources.map((src) => (
                          <tr key={src}>
                            <td className="fw-semibold">{src}</td>
                            {statuses.map((s) => (
                              <td key={s} className="text-end">
                                {data.counts.find((c) => c.source === src && c.status === s)?.total ?? 0}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  )}
                </>
              )}
            </Card.Body>
          </Card>
          <Card className="shadow-sm">
            <Card.Header>{t('listings.import.title')}</Card.Header>
            <Card.Body>
              <p className="small text-muted">{t('listings.import.hint')}</p>
              {importState.error ? <ProblemAlert error={importState.error} onClose={() => importState.reset()} /> : null}
              {importState.data && (
                <Alert variant="success" dismissible onClose={() => importState.reset()}>
                  <div className="fw-semibold">{t('listings.import.done')}</div>
                  <ul className="mb-0 small">
                    <li>{t('listings.import.report.seen', { n: importState.data.seen })}</li>
                    <li>{t('listings.import.report.inserted', { n: importState.data.inserted })}</li>
                    <li>{t('listings.import.report.updated', { n: importState.data.updated })}</li>
                    <li>{t('listings.import.report.unchanged', { n: importState.data.unchanged })}</li>
                    <li>{t('listings.import.report.rejected', { n: importState.data.rejected })}</li>
                  </ul>
                </Alert>
              )}
              <Form onSubmit={submitImport}>
                <Form.Group className="mb-2" controlId="import-path">
                  <Form.Label>{t('listings.import.path')}</Form.Label>
                  <Form.Control value={path} onChange={(e) => setPath(e.target.value)} placeholder="/home/bimeni/announce_crawled_data/dba_202609261200.jsonl" />
                </Form.Group>
                <Form.Group className="mb-3" controlId="import-source">
                  <Form.Label>{t('listings.import.source')}</Form.Label>
                  <Form.Select value={source} onChange={(e) => setSource(e.target.value as ListingSource | '')}>
                    <option value="">{t('listings.import.sourceAuto')}</option>
                    {LISTING_SOURCES.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </Form.Select>
                </Form.Group>
                <Button type="submit" disabled={importState.isLoading || !path.trim()}>
                  {importState.isLoading && <Spinner size="sm" animation="border" className="me-2" />}
                  {t('listings.import.button')}
                </Button>
              </Form>
            </Card.Body>
          </Card>
        </Col>
        <Col lg={7}>
          <Card className="shadow-sm mb-3">
            <Card.Header>{t('listings.topClicked')}</Card.Header>
            <Card.Body className="p-0">{data && <ListingTable rows={data.topClicked} showClicks />}</Card.Body>
          </Card>
          <Card className="shadow-sm">
            <Card.Header>{t('listings.recentGone')}</Card.Header>
            <Card.Body className="p-0">{data && <ListingTable rows={data.recentGone} />}</Card.Body>
          </Card>
        </Col>
      </Row>
    </>
  );
}
