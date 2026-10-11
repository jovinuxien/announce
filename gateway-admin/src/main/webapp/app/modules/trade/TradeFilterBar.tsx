import { useEffect, useState, type FormEvent } from 'react';
import { Button, Card, Col, Form, Row } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { activeTradeFilterCount, type TradeFilters } from './tradeFilters';

interface Props<S extends string> {
  filters: TradeFilters<S>;
  statuses: readonly S[];
  /** Translation prefix of the status names, e.g. `trade.orderStatus`. */
  statusPrefix: string;
  /** Which optional inputs to show besides the status. */
  fields: { seller?: boolean; buyer?: boolean; q?: boolean };
  /** Applies the draft; the caller writes it to the URL (and resets the page). */
  onApply: (next: TradeFilters<S>) => void;
}

/** One filter bar for the trade lists: a draft edited locally, applied on submit so a half-typed id never fires a request. */
export default function TradeFilterBar<S extends string>({ filters, statuses, statusPrefix, fields, onApply }: Props<S>) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(filters);
  useEffect(() => setDraft(filters), [filters]);
  const set = <K extends keyof TradeFilters<S>>(k: K, v: TradeFilters<S>[K]) => setDraft((d) => ({ ...d, [k]: v }));
  const submit = (e: FormEvent) => {
    e.preventDefault();
    onApply({ ...draft, q: draft.q.trim() });
  };
  const reset = () => onApply({ status: '', seller: '', buyer: '', q: '' });
  return (
    <Card className="shadow-sm mb-3">
      <Card.Body className="py-2">
        <Form onSubmit={submit}>
          <Row className="g-2 align-items-end">
            <Col sm={6} md={3} lg={2}>
              <Form.Label htmlFor="tf-status" className="small mb-1">
                {t('trade.filters.status')}
              </Form.Label>
              <Form.Select id="tf-status" size="sm" value={draft.status} onChange={(e) => set('status', e.target.value as S | '')}>
                <option value="">{t('common.all')}</option>
                {statuses.map((s) => (
                  <option key={s} value={s}>
                    {t(`${statusPrefix}.${s}`)}
                  </option>
                ))}
              </Form.Select>
            </Col>
            {fields.seller && (
              <Col sm={6} md={3} lg={2}>
                <Form.Label htmlFor="tf-seller" className="small mb-1">
                  {t('trade.filters.seller')}
                </Form.Label>
                <Form.Control id="tf-seller" size="sm" inputMode="numeric" value={draft.seller} onChange={(e) => set('seller', e.target.value.replace(/\D/g, ''))} />
              </Col>
            )}
            {fields.buyer && (
              <Col sm={6} md={3} lg={2}>
                <Form.Label htmlFor="tf-buyer" className="small mb-1">
                  {t('trade.filters.buyer')}
                </Form.Label>
                <Form.Control id="tf-buyer" size="sm" inputMode="numeric" value={draft.buyer} onChange={(e) => set('buyer', e.target.value.replace(/\D/g, ''))} />
              </Col>
            )}
            {fields.q && (
              <Col sm={12} md={6} lg={4}>
                <Form.Label htmlFor="tf-q" className="small mb-1">
                  {t('trade.filters.text')}
                </Form.Label>
                <Form.Control id="tf-q" size="sm" value={draft.q} onChange={(e) => set('q', e.target.value)} placeholder={t('trade.filters.textPlaceholder')} />
              </Col>
            )}
            <Col xs="auto" className="d-flex gap-2">
              <Button type="submit" size="sm" variant="primary">
                {t('common.actions.apply')}
              </Button>
              <Button type="button" size="sm" variant="outline-secondary" onClick={reset} disabled={activeTradeFilterCount(filters) === 0}>
                {t('common.actions.reset')}
              </Button>
            </Col>
          </Row>
        </Form>
      </Card.Body>
    </Card>
  );
}
