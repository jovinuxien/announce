import { useEffect, useState } from 'react';
import { Badge, Button, Card, Col, Row, Spinner, Table } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import { features } from 'app/config/features';
import { useAppSelector } from 'app/config/store';
import { announceApi } from 'app/shared/api/announceApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import RichText, { looksLikeHtml } from 'app/shared/components/RichText';
import { AnnounceResponse } from 'app/shared/model/announce';
import { formatDateTime } from 'app/shared/util/dates';
import { formatMoney } from 'app/shared/util/money';
import { describeError } from 'app/shared/util/problem';
import AuctionPanel from 'app/modules/trade/AuctionPanel';
import BuyBox from 'app/modules/trade/BuyBox';
import ContactSeller from 'app/modules/chat/ContactSeller';

function Gallery({ pictures, title }: { pictures: string[]; title: string }) {
  const [index, setIndex] = useState(0);
  if (!pictures.length) {
    return (
      <div className="an-card-placeholder rounded" style={{ height: 320 }}>
        <i className="bi bi-image" />
      </div>
    );
  }
  return (
    <div>
      <img src={pictures[index]} alt={title} className="an-gallery-main rounded" />
      {pictures.length > 1 && (
        <div className="d-flex gap-2 mt-2 flex-wrap">
          {pictures.map((p, i) => (
            <img key={p + i} src={p} alt="" className={`an-gallery-thumb rounded ${i === index ? 'active' : ''}`} onClick={() => setIndex(i)} />
          ))}
        </div>
      )}
    </div>
  );
}

function GoodsSection({ a, lang }: { a: AnnounceResponse; lang: string }) {
  const { t } = useTranslation('announce');
  const g = a.goods ?? {};
  return (
    <>
      <div className="an-price fs-3">
        {g.saleType === 'GIVEAWAY' ? t('common:saleType.GIVEAWAY') : g.saleType === 'AUCTION' ? t('goods.startingAt', { price: formatMoney(g.price, undefined, lang) }) : formatMoney(g.price, undefined, lang)}
      </div>
      {g.saleType === 'AUCTION' && (g.auctionStartsAt || g.auctionEndsAt) && (
        <div className="small mb-2" data-testid="auction-schedule">
          <i className="bi bi-clock" />{' '}
          {g.auctionStartsAt && g.auctionEndsAt
            ? t('goods.auctionSchedule', { start: formatDateTime(g.auctionStartsAt, lang), end: formatDateTime(g.auctionEndsAt, lang) })
            : g.auctionStartsAt
              ? t('goods.auctionStarts', { date: formatDateTime(g.auctionStartsAt, lang) })
              : t('goods.auctionEnds', { date: formatDateTime(g.auctionEndsAt, lang) })}
        </div>
      )}
      <Table size="sm" borderless className="mb-2">
        <tbody>
          {g.condition && <tr><th>{t('goods.condition')}</th><td>{t(`common:condition.${g.condition}`)}</td></tr>}
          {g.saleType && <tr><th>{t('goods.saleType')}</th><td>{t(`common:saleType.${g.saleType}`)}</td></tr>}
          {g.unit && <tr><th>{t('goods.unit')}</th><td>{g.unit}</td></tr>}
          {(g.attributes ?? []).map(at => (
            <tr key={at.name}><th>{at.name}</th><td>{at.value}</td></tr>
          ))}
        </tbody>
      </Table>
      {(g.variants ?? []).length > 0 && (
        <Table size="sm" bordered>
          <thead><tr><th>{t('goods.variant')}</th><th>{t('goods.price')}</th><th>{t('goods.stock')}</th></tr></thead>
          <tbody>
            {(g.variants ?? []).map((v, i) => (
              <tr key={i}><td>{v.specifications.join(' / ')}</td><td>{formatMoney(v.price, undefined, lang)}</td><td>{v.stock}</td></tr>
            ))}
          </tbody>
        </Table>
      )}
    </>
  );
}

function JobSection({ a, lang }: { a: AnnounceResponse; lang: string }) {
  const { t } = useTranslation('announce');
  const j = a.job;
  if (!j) return null;
  return (
    <Table size="sm" borderless>
      <tbody>
        <tr><th>{t('job.employer')}</th><td>{j.employer}</td></tr>
        <tr><th>{t('job.contract')}</th><td>{t(`common:contractType.${j.contractType}`)}</td></tr>
        {(j.salaryMin != null || j.salaryMax != null) && (
          <tr><th>{t('job.salary')}</th><td>{[j.salaryMin, j.salaryMax].filter(v => v != null).map(v => formatMoney(v, j.currency, lang)).join(' – ')}</td></tr>
        )}
        {j.applicationDeadline && <tr><th>{t('job.deadline')}</th><td>{formatDateTime(j.applicationDeadline, lang)}</td></tr>}
        <tr><th>{t('job.howToApply')}</th><td style={{ whiteSpace: 'pre-wrap' }}>{j.howToApply}</td></tr>
      </tbody>
    </Table>
  );
}

function EventSection({ a, lang }: { a: AnnounceResponse; lang: string }) {
  const { t } = useTranslation('announce');
  const e = a.event;
  if (!e) return null;
  return (
    <Table size="sm" borderless>
      <tbody>
        <tr><th>{t('event.starts')}</th><td>{formatDateTime(e.startsAt, lang)}</td></tr>
        <tr><th>{t('event.ends')}</th><td>{formatDateTime(e.endsAt, lang)}</td></tr>
        {e.venue && <tr><th>{t('event.venue')}</th><td>{e.venue}</td></tr>}
        {e.organizer && <tr><th>{t('event.organizer')}</th><td>{e.organizer}</td></tr>}
        <tr><th>{t('event.tickets')}</th><td>{e.ticketed ? formatMoney(e.ticketPrice, undefined, lang) || t('event.ticketed') : t('event.free')}</td></tr>
        {e.capacity != null && <tr><th>{t('event.capacity')}</th><td>{e.capacity}</td></tr>}
      </tbody>
    </Table>
  );
}

export default function AnnounceDetail() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation('announce');
  const lang = i18n.language;
  const user = useAppSelector(s => s.auth.user);
  const [a, setA] = useState<AnnounceResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setA(null);
    setError(null);
    announceApi.get(id).then(setA).catch(e => setError(describeError(e, t('notFound'))));
  }, [id, t]);

  if (error) return <ProblemAlert message={error} />;
  if (!a) return <div className="text-center py-5"><Spinner animation="border" /></div>;

  const mine = user?.id === a.sellerId;
  const location = [a.location?.postalCode, a.location?.city].filter(Boolean).join(' ');
  const isAuction = a.kind === 'GOODS' && a.goods?.saleType === 'AUCTION';
  const share = () => {
    const url = window.location.href;
    if (navigator.share) void navigator.share({ title: a.title, url });
    else void navigator.clipboard?.writeText(url);
  };

  return (
    <Row className="g-4">
      <Col lg={7}>
        <Gallery pictures={a.gallery ?? []} title={a.title} />
        <Card className="mt-3">
          <Card.Body>
            <h2 className="h5">{t('description')}</h2>
            {looksLikeHtml(a.description) ? (
              <RichText html={a.description as string} />
            ) : (
              <p style={{ whiteSpace: 'pre-wrap' }} className="mb-0">{a.description || a.brief || t('noDescription')}</p>
            )}
            {(a.keywords ?? []).length > 0 && (
              <div className="mt-2">
                {(a.keywords ?? []).map(k => (
                  <Badge bg="light" text="dark" key={k} className="me-1 border">{k}</Badge>
                ))}
              </div>
            )}
          </Card.Body>
        </Card>
      </Col>
      <Col lg={5}>
        <div className="d-flex justify-content-between align-items-start">
          <div>
            <div className="small text-muted">{t(`common:kind.${a.kind}`)}{location && ` · ${location}`}</div>
            <h1 className="h3">{a.title}</h1>
          </div>
          {a.status !== 'PUBLISHED' && <Badge bg="secondary">{t(`common:status.${a.status}`)}</Badge>}
        </div>
        {a.kind === 'GOODS' && <GoodsSection a={a} lang={lang} />}
        {a.kind === 'JOB' && <JobSection a={a} lang={lang} />}
        {a.kind === 'EVENT' && <EventSection a={a} lang={lang} />}
        <Card className="mt-3">
          <Card.Body>
            <h2 className="h6">{t('seller.title')}</h2>
            <div className="small text-muted">{t('seller.member', { id: a.sellerId })}</div>
            <div className="small text-muted">{t('seller.since', { date: formatDateTime(a.publishedAt ?? a.createdAt, lang) })}</div>
            {a.expiresAt && <div className="small text-muted">{t('seller.expires', { date: formatDateTime(a.expiresAt, lang) })}</div>}
            <div className="d-flex gap-2 mt-2 flex-wrap">
              {mine ? (
                <Button as={Link as any} to={`/post/edit/${encodeURIComponent(a.id)}`} size="sm" variant="outline-primary">{t('seller.edit')}</Button>
              ) : (
                <>
                  {features.chat && a.status === 'PUBLISHED' && <ContactSeller announce={a} />}
                  {features.trade && a.kind === 'GOODS' && a.status === 'PUBLISHED' && !isAuction && <BuyBox announce={a} />}
                </>
              )}
              <Button size="sm" variant="outline-secondary" onClick={share}>{t('share')}</Button>
            </div>
          </Card.Body>
        </Card>
        {features.trade && isAuction && a.status === 'PUBLISHED' && <AuctionPanel announce={a} mine={mine} />}
        <Card className="mt-3">
          <Card.Body className="text-center text-muted small">
            <i className="bi bi-geo-alt" /> {location || t('locationUnknown')}
            <div className="mt-2 rounded bg-light" style={{ height: 120 }}>{t('mapPlaceholder')}</div>
          </Card.Body>
        </Card>
      </Col>
    </Row>
  );
}
