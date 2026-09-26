import { useState } from 'react';
import { Button, Form } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { OcsFacet } from 'app/shared/model/search';
import { FACET_ORDER, isRangeField, Range, SearchState } from './searchState';

interface Props {
  facets: OcsFacet[];
  state: SearchState;
  onToggle: (field: string, value: string) => void;
  onRange: (field: string, range: Range) => void;
  onClear: () => void;
  hasRefinements: boolean;
  categoryNames?: Record<string, string>;
}

const HIDDEN = new Set(['kind', 'status', 'seller_id', 'postal_code']);

function order(facets: OcsFacet[]): OcsFacet[] {
  const rank = (f: OcsFacet) => {
    const i = FACET_ORDER.indexOf(f.fieldName);
    return i === -1 ? 1000 + (f.meta?.order ?? 0) : i;
  };
  return [...facets].filter(f => !HIDDEN.has(f.fieldName) && f.entries.length > 0).sort((a, b) => rank(a) - rank(b));
}

function RangeFacet({ facet, value, onRange }: { facet: OcsFacet; value?: Range; onRange: (r: Range) => void }) {
  const { t } = useTranslation('search');
  const [min, setMin] = useState(value?.min?.toString() ?? '');
  const [max, setMax] = useState(value?.max?.toString() ?? '');
  const lower = Math.min(...facet.entries.map(e => e.lowerBound ?? Infinity));
  const upper = Math.max(...facet.entries.map(e => e.upperBound ?? -Infinity));
  return (
    <Form
      className="d-flex gap-1 align-items-center"
      onSubmit={e => {
        e.preventDefault();
        onRange({ min: min === '' ? undefined : Number(min), max: max === '' ? undefined : Number(max) });
      }}
    >
      <Form.Control size="sm" type="number" placeholder={Number.isFinite(lower) ? String(Math.floor(lower)) : t('facets.min')} value={min} onChange={e => setMin(e.target.value)} aria-label={t('facets.min')} />
      <span>–</span>
      <Form.Control size="sm" type="number" placeholder={Number.isFinite(upper) ? String(Math.ceil(upper)) : t('facets.max')} value={max} onChange={e => setMax(e.target.value)} aria-label={t('facets.max')} />
      <Button size="sm" variant="outline-secondary" type="submit">
        {t('facets.apply')}
      </Button>
    </Form>
  );
}

export function facetLabel(t: (k: string, o?: Record<string, unknown>) => string, field: string, fallback?: string): string {
  const key = `facets.field.${field}`;
  const translated = t(key, { defaultValue: '' });
  return translated || fallback || field;
}

export function entryLabel(t: (k: string, o?: Record<string, unknown>) => string, field: string, key: string, categoryNames?: Record<string, string>): string {
  if (field === 'category_ids') return categoryNames?.[key] ?? key;
  if (['sale_type', 'condition', 'contract_type', 'source', 'ticketed'].includes(field)) {
    const translated = t(`facets.value.${field}.${key}`, { defaultValue: '' });
    if (translated) return translated;
  }
  return key;
}

export default function Facets({ facets, state, onToggle, onRange, onClear, hasRefinements, categoryNames }: Props) {
  const { t } = useTranslation('search');
  return (
    <div className="an-facets">
      <div className="d-flex justify-content-between align-items-center mb-2">
        <strong>{t('facets.title')}</strong>
        {hasRefinements && (
          <Button size="sm" variant="link" className="p-0" onClick={onClear}>
            {t('facets.clear')}
          </Button>
        )}
      </div>
      {order(facets).map(f => (
        <div key={f.fieldName} className="mb-3">
          <div className="fw-semibold small text-uppercase text-muted mb-1">{facetLabel(t, f.fieldName, f.meta?.label)}</div>
          {isRangeField(f.fieldName) || f.type === 'interval' || f.type === 'range' ? (
            <RangeFacet facet={f} value={state.ranges[f.fieldName]} onRange={r => onRange(f.fieldName, r)} />
          ) : (
            f.entries.slice(0, 12).map(e => (
              <Form.Check
                key={e.key}
                type="checkbox"
                id={`facet-${f.fieldName}-${e.key}`}
                label={`${entryLabel(t, f.fieldName, e.key, categoryNames)} (${e.docCount})`}
                checked={e.selected || (state.filters[f.fieldName] ?? []).some(v => v.toLowerCase() === e.key.toLowerCase())}
                onChange={() => onToggle(f.fieldName, e.key)}
              />
            ))
          )}
        </div>
      ))}
    </div>
  );
}
