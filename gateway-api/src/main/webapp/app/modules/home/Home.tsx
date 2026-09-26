import { useEffect, useState } from 'react';
import { Button, Col, Row } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { searchApi } from 'app/shared/api/searchApi';
import AnnounceCard, { fromHit } from 'app/shared/components/AnnounceCard';
import { Kind, KINDS } from 'app/shared/model/announce';
import { SearchHit } from 'app/shared/model/search';
import { toSearchHit } from 'app/modules/search/searchState';
import { useCategories } from './useCategories';

function KindSection({ kind }: { kind: Kind }) {
  const { t, i18n } = useTranslation();
  const { tree } = useCategories(kind);
  const [latest, setLatest] = useState<SearchHit[]>([]);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    const p = new URLSearchParams({ kind, sort: '-created_epoch', limit: '8', offset: '0' });
    searchApi
      .search(p)
      .then(r => setLatest((r.slices?.[0]?.hits ?? []).map(toSearchHit)))
      .catch(() => setUnavailable(true));
  }, [kind]);

  return (
    <section className="mb-5">
      <div className="d-flex justify-content-between align-items-baseline mb-2">
        <h2 className="h4 mb-0">{t(`kind.${kind}`)}</h2>
        <Link to={`/search?kind=${kind}`}>{t('home.browseAll')}</Link>
      </div>
      <div className="d-flex flex-wrap gap-2 mb-3">
        {tree.map(n => (
          <Button key={n.category.id} as={Link as any} to={`/search?kind=${kind}&category_ids=${n.category.id}`} size="sm" variant="outline-secondary">
            {n.category.name}
          </Button>
        ))}
        <Button as={Link as any} to={`/post/${kind.toLowerCase()}`} size="sm" variant="primary">
          {t(`home.post.${kind}`)}
        </Button>
      </div>
      {unavailable ? (
        <p className="text-muted small">{t('home.searchUnavailable')}</p>
      ) : (
        <Row xs={2} md={4} className="g-3">
          {latest.map(h => (
            <Col key={h.id}>
              <AnnounceCard item={fromHit(h, i18n.language)} />
            </Col>
          ))}
        </Row>
      )}
    </section>
  );
}

export default function Home() {
  const { t } = useTranslation();
  return (
    <div>
      <div className="p-4 mb-4 bg-white rounded-3 border">
        <h1 className="h3">{t('home.title')}</h1>
        <p className="text-muted mb-0">{t('home.tagline')}</p>
      </div>
      {KINDS.map(k => (
        <KindSection key={k} kind={k} />
      ))}
    </div>
  );
}
