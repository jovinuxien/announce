import { Badge, Card } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { formatMoney } from 'app/shared/util/money';
import { formatDate, formatEpoch } from 'app/shared/util/dates';
import { AnnounceSummaryResponse } from 'app/shared/model/announce';
import { SearchHit } from 'app/shared/model/search';

export interface CardModel {
  id: string;
  source: string;
  kind: string;
  title: string;
  brief?: string;
  price?: number;
  currency?: string;
  saleType?: string;
  imageUrl?: string;
  city?: string;
  employer?: string;
  contractType?: string;
  when?: string; // preformatted date line (event start, job deadline)
  externalUrl?: string;
  status?: string;
}

export function fromSummary(s: AnnounceSummaryResponse, lang: string): CardModel {
  return {
    id: s.id,
    source: 'local',
    kind: s.kind,
    title: s.title,
    brief: s.brief ?? undefined,
    price: s.kind === 'GOODS' ? s.price ?? undefined : s.kind === 'EVENT' && s.ticketed ? s.ticketPrice ?? undefined : undefined,
    saleType: s.saleType ?? undefined,
    imageUrl: s.pictureUrl ?? undefined,
    city: s.location?.city ?? undefined,
    employer: s.employer ?? undefined,
    contractType: s.contractType ?? undefined,
    when: s.kind === 'EVENT' ? formatDate(s.startsAt, lang) : s.kind === 'JOB' ? formatDate(s.applicationDeadline, lang) : undefined,
    status: s.status,
  };
}

export function fromHit(h: SearchHit, lang: string): CardModel {
  return {
    id: h.id,
    source: h.source,
    kind: h.kind,
    title: h.title,
    brief: h.description,
    price: h.price,
    currency: h.currency,
    saleType: h.saleType,
    imageUrl: h.imageUrl,
    city: h.city,
    employer: h.employer,
    contractType: h.contractType,
    when: h.kind === 'EVENT' ? formatEpoch(h.startsEpoch, lang) : h.endsEpoch ? formatEpoch(h.endsEpoch, lang) : undefined,
    externalUrl: h.externalUrl,
  };
}

function domainOf(url?: string): string {
  if (!url) return '';
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return '';
  }
}

/** One result card. A crawled listing (source != local) links out through /out/{id} in a new tab. */
export default function AnnounceCard({ item }: { item: CardModel }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const external = item.source !== 'local';
  const body = (
    <>
      {item.imageUrl ? (
        <Card.Img variant="top" src={item.imageUrl} alt="" className="an-card-img" loading="lazy" />
      ) : (
        <div className="an-card-placeholder">
          <i className="bi bi-image" />
        </div>
      )}
      <Card.Body className="p-2">
        <div className="small text-muted d-flex justify-content-between">
          <span>{t(`kind.${item.kind}`)}</span>
          {item.city && <span>{item.city}</span>}
        </div>
        <Card.Title as="h6" className="mb-1 text-truncate" title={item.title}>
          {item.title}
        </Card.Title>
        {item.kind === 'GOODS' && (
          <div className="an-price">
            {item.saleType === 'GIVEAWAY'
              ? t('saleType.GIVEAWAY')
              : item.saleType === 'AUCTION'
                ? `${t('saleType.AUCTION')} ${item.price != null ? formatMoney(item.price, item.currency, lang) : ''}`
                : formatMoney(item.price, item.currency, lang)}
          </div>
        )}
        {item.kind === 'JOB' && (
          <div className="small">
            {item.employer && <span>{item.employer} · </span>}
            {item.contractType && <span>{t(`contractType.${item.contractType}`)}</span>}
            {item.when && <div className="text-muted">{t('card.deadline', { date: item.when })}</div>}
          </div>
        )}
        {item.kind === 'EVENT' && (
          <div className="small">
            {item.when && <div>{item.when}</div>}
            {item.price != null && <div className="text-muted">{formatMoney(item.price, item.currency, lang)}</div>}
          </div>
        )}
        {item.status && item.status !== 'PUBLISHED' && (
          <Badge bg="secondary" className="mt-1">
            {t(`status.${item.status}`)}
          </Badge>
        )}
      </Card.Body>
    </>
  );
  return (
    <Card className="h-100 position-relative shadow-sm">
      {external && (
        <Badge bg="warning" text="dark" className="an-source-badge" title={item.externalUrl}>
          {domainOf(item.externalUrl) || item.source}
        </Badge>
      )}
      {external ? (
        <a href={`/out/${encodeURIComponent(item.id)}`} target="_blank" rel="noopener noreferrer nofollow" className="text-reset text-decoration-none">
          {body}
        </a>
      ) : (
        <Link to={`/announce/${encodeURIComponent(item.id)}`} className="text-reset text-decoration-none">
          {body}
        </Link>
      )}
    </Card>
  );
}
