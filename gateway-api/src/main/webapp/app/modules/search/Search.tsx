import { useEffect, useMemo } from 'react';
import { Col, Form, Row, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import AnnounceCard, { fromHit } from 'app/shared/components/AnnounceCard';
import KindTabs from 'app/shared/components/KindTabs';
import Pagination from 'app/shared/components/Pagination';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { useCategoryNames } from 'app/modules/home/useCategories';
import Facets from './Facets';
import { runSearch } from './searchSlice';
import { clearRefinements, hasRefinements, parseSearchParams, setKind, setRange, toggleFilter, toSearchParams, totalPages, toOcsParams } from './searchState';

/** The URL is the single source of truth: every refinement navigates, the effect re-runs the search. */
export default function Search() {
  const { t, i18n } = useTranslation('search');
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { result, loading, error, key } = useAppSelector(s => s.search);
  const state = useMemo(() => parseSearchParams(location.search), [location.search]);
  const categoryNames = useCategoryNames(state.kind);
  const wanted = toOcsParams(state).toString();

  useEffect(() => {
    void dispatch(runSearch(state));
  }, [dispatch, wanted]); // eslint-disable-line react-hooks/exhaustive-deps

  const go = (next: typeof state) => navigate(`/search?${toSearchParams(next)}`);
  const current = key === wanted ? result : null;
  const pages = current ? totalPages(current.matchCount, state.size) : 1;

  return (
    <div>
      <div className="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
        <KindTabs value={state.kind} onChange={k => go(setKind(state, k))} />
        {current && current.sortOptions.length > 0 && (
          <Form.Select
            size="sm"
            style={{ width: 'auto' }}
            aria-label={t('sort')}
            value={state.sort ?? ''}
            onChange={e => go({ ...state, sort: e.target.value || null, page: 1 })}
          >
            <option value="">{t('sortRelevance')}</option>
            {current.sortOptions.map(o => {
              const value = o.sortOrder === 'DESC' ? `-${o.field}` : o.field;
              return (
                <option key={value} value={value}>
                  {t(`sortOption.${o.field}`, { defaultValue: o.label })}
                </option>
              );
            })}
          </Form.Select>
        )}
      </div>
      <Row>
        <Col md={3} className="mb-3">
          {current && (
            <Facets
              facets={current.facets}
              state={state}
              onToggle={(f, v) => go(toggleFilter(state, f, v))}
              onRange={(f, r) => go(setRange(state, f, r))}
              onClear={() => go(clearRefinements(state))}
              hasRefinements={hasRefinements(state)}
              categoryNames={categoryNames}
            />
          )}
        </Col>
        <Col md={9}>
          <ProblemAlert message={error} />
          <div className="text-muted small mb-2">
            {state.q ? t('resultsFor', { count: current?.matchCount ?? 0, q: state.q }) : t('results', { count: current?.matchCount ?? 0 })}
            {loading && <Spinner animation="border" size="sm" className="ms-2" />}
          </div>
          {current && current.hits.length === 0 && !loading && <p className="text-muted">{t('empty')}</p>}
          <Row xs={1} sm={2} lg={3} xl={4} className="g-3">
            {(current?.hits ?? []).map(h => (
              <Col key={h.id}>
                <AnnounceCard item={fromHit(h, i18n.language)} />
              </Col>
            ))}
          </Row>
          <Pagination page={state.page} pages={pages} onChange={p => go({ ...state, page: p })} />
        </Col>
      </Row>
    </div>
  );
}
