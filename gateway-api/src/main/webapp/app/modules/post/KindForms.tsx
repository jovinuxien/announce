import { Button, Col, Form, Row } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { AnnounceRequest, CONDITIONS, CONTRACT_TYPES } from 'app/shared/model/announce';
import { fromInputDateTime, toInputDateTime } from 'app/shared/util/dates';

interface FormProps {
  draft: AnnounceRequest;
  onChange: (d: AnnounceRequest) => void;
  err: (field: string) => string | undefined;
}

const num = (v: string): number | null => (v === '' ? null : Number(v));

export function GoodsForm({ draft, onChange, err }: FormProps) {
  const { t } = useTranslation('post');
  const g = draft.goods ?? {};
  const set = (patch: Partial<typeof g>) => onChange({ ...draft, goods: { ...g, ...patch } });
  const attributes = g.attributes ?? [];
  return (
    <>
      <Row className="g-3 mb-3">
        <Col md={4}>
          <Form.Group controlId="goods-saleType">
            <Form.Label>{t('goods.saleType')}</Form.Label>
            <Form.Select value={g.saleType ?? 'FIXED_PRICE'} onChange={e => set({ saleType: e.target.value as typeof g.saleType })}>
              {(['FIXED_PRICE', 'AUCTION', 'GIVEAWAY'] as const).map(s => (
                <option key={s} value={s}>{t(`common:saleType.${s}`)}</option>
              ))}
            </Form.Select>
          </Form.Group>
        </Col>
        <Col md={4}>
          <Form.Group controlId="goods-price">
            <Form.Label>{g.saleType === 'AUCTION' ? t('goods.startPrice') : t('goods.price')}</Form.Label>
            <Form.Control type="number" min={0} step="0.01" disabled={g.saleType === 'GIVEAWAY'} value={g.price ?? ''} onChange={e => set({ price: num(e.target.value) })} isInvalid={!!err('goods.price')} />
            <Form.Control.Feedback type="invalid">{err('goods.price')}</Form.Control.Feedback>
          </Form.Group>
        </Col>
        <Col md={4}>
          <Form.Group controlId="goods-condition">
            <Form.Label>{t('goods.condition')}</Form.Label>
            <Form.Select value={g.condition ?? ''} onChange={e => set({ condition: (e.target.value || null) as typeof g.condition })} isInvalid={!!err('goods.condition')}>
              <option value="">{t('goods.chooseCondition')}</option>
              {CONDITIONS.map(c => (
                <option key={c} value={c}>{t(`common:condition.${c}`)}</option>
              ))}
            </Form.Select>
            <Form.Control.Feedback type="invalid">{err('goods.condition')}</Form.Control.Feedback>
          </Form.Group>
        </Col>
      </Row>
      {g.saleType === 'AUCTION' && (
        <Row className="g-3 mb-3" data-testid="auction-schedule">
          <Col md={4}>
            <Form.Group controlId="goods-auctionStartsAt">
              <Form.Label>{t('goods.auctionStartsAt')}</Form.Label>
              <Form.Control type="datetime-local" value={toInputDateTime(g.auctionStartsAt)} onChange={e => set({ auctionStartsAt: fromInputDateTime(e.target.value) })} isInvalid={!!err('goods.auctionStartsAt')} />
              <Form.Control.Feedback type="invalid">{err('goods.auctionStartsAt')}</Form.Control.Feedback>
            </Form.Group>
          </Col>
          <Col md={4}>
            <Form.Group controlId="goods-auctionEndsAt">
              <Form.Label>{t('goods.auctionEndsAt')}</Form.Label>
              <Form.Control type="datetime-local" value={toInputDateTime(g.auctionEndsAt)} onChange={e => set({ auctionEndsAt: fromInputDateTime(e.target.value) })} isInvalid={!!err('goods.auctionEndsAt')} />
              <Form.Control.Feedback type="invalid">{err('goods.auctionEndsAt')}</Form.Control.Feedback>
            </Form.Group>
          </Col>
          <Col md={4} className="d-flex align-items-end">
            <Form.Text>{t('goods.auctionScheduleHelp')}</Form.Text>
          </Col>
        </Row>
      )}
      <Form.Label>{t('goods.attributes')}</Form.Label>
      {attributes.map((a, i) => (
        <Row className="g-2 mb-2" key={i}>
          <Col>
            <Form.Control placeholder={t('goods.attrName')} value={a.name} onChange={e => set({ attributes: attributes.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })} />
          </Col>
          <Col>
            <Form.Control placeholder={t('goods.attrValue')} value={a.value} onChange={e => set({ attributes: attributes.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)) })} />
          </Col>
          <Col xs="auto">
            <Button variant="outline-danger" onClick={() => set({ attributes: attributes.filter((_, j) => j !== i) })}>×</Button>
          </Col>
        </Row>
      ))}
      <Button size="sm" variant="outline-secondary" onClick={() => set({ attributes: [...attributes, { name: '', value: '' }] })}>
        {t('goods.addAttribute')}
      </Button>
    </>
  );
}

export function JobForm({ draft, onChange, err }: FormProps) {
  const { t } = useTranslation('post');
  const j = draft.job ?? { employer: '', contractType: 'FULL_TIME' as const, howToApply: '' };
  const set = (patch: Partial<typeof j>) => onChange({ ...draft, job: { ...j, ...patch } });
  return (
    <>
      <Row className="g-3 mb-3">
        <Col md={6}>
          <Form.Group controlId="job-employer">
            <Form.Label>{t('job.employer')}</Form.Label>
            <Form.Control value={j.employer} onChange={e => set({ employer: e.target.value })} isInvalid={!!err('job.employer')} />
            <Form.Control.Feedback type="invalid">{err('job.employer')}</Form.Control.Feedback>
          </Form.Group>
        </Col>
        <Col md={6}>
          <Form.Group controlId="job-contract">
            <Form.Label>{t('job.contract')}</Form.Label>
            <Form.Select value={j.contractType} onChange={e => set({ contractType: e.target.value as typeof j.contractType })}>
              {CONTRACT_TYPES.map(c => (
                <option key={c} value={c}>{t(`common:contractType.${c}`)}</option>
              ))}
            </Form.Select>
          </Form.Group>
        </Col>
        <Col md={4}>
          <Form.Group controlId="job-salaryMin">
            <Form.Label>{t('job.salaryMin')}</Form.Label>
            <Form.Control type="number" min={0} value={j.salaryMin ?? ''} onChange={e => set({ salaryMin: num(e.target.value) })} />
          </Form.Group>
        </Col>
        <Col md={4}>
          <Form.Group controlId="job-salaryMax">
            <Form.Label>{t('job.salaryMax')}</Form.Label>
            <Form.Control type="number" min={0} value={j.salaryMax ?? ''} onChange={e => set({ salaryMax: num(e.target.value) })} isInvalid={!!err('job.salaryMax')} />
            <Form.Control.Feedback type="invalid">{err('job.salaryMax')}</Form.Control.Feedback>
          </Form.Group>
        </Col>
        <Col md={4}>
          <Form.Group controlId="job-currency">
            <Form.Label>{t('job.currency')}</Form.Label>
            <Form.Select value={j.currency ?? 'DKK'} onChange={e => set({ currency: e.target.value })}>
              {['DKK', 'SEK', 'EUR'].map(c => <option key={c}>{c}</option>)}
            </Form.Select>
          </Form.Group>
        </Col>
        <Col md={6}>
          <Form.Group controlId="job-deadline">
            <Form.Label>{t('job.deadline')}</Form.Label>
            <Form.Control type="datetime-local" value={toInputDateTime(j.applicationDeadline)} onChange={e => set({ applicationDeadline: fromInputDateTime(e.target.value) })} isInvalid={!!err('job.applicationDeadline')} />
            <Form.Control.Feedback type="invalid">{err('job.applicationDeadline')}</Form.Control.Feedback>
          </Form.Group>
        </Col>
      </Row>
      <Form.Group className="mb-3" controlId="job-howToApply">
        <Form.Label>{t('job.howToApply')}</Form.Label>
        <Form.Control as="textarea" rows={3} value={j.howToApply} onChange={e => set({ howToApply: e.target.value })} isInvalid={!!err('job.howToApply')} />
        <Form.Control.Feedback type="invalid">{err('job.howToApply')}</Form.Control.Feedback>
      </Form.Group>
    </>
  );
}

export function EventForm({ draft, onChange, err }: FormProps) {
  const { t } = useTranslation('post');
  const e = draft.event ?? { startsAt: '', endsAt: '' };
  const set = (patch: Partial<typeof e>) => onChange({ ...draft, event: { ...e, ...patch } });
  return (
    <Row className="g-3 mb-3">
      <Col md={6}>
        <Form.Group controlId="event-starts">
          <Form.Label>{t('event.starts')}</Form.Label>
          <Form.Control type="datetime-local" value={toInputDateTime(e.startsAt)} onChange={ev => set({ startsAt: fromInputDateTime(ev.target.value) ?? '' })} isInvalid={!!err('event.startsAt')} />
          <Form.Control.Feedback type="invalid">{err('event.startsAt')}</Form.Control.Feedback>
        </Form.Group>
      </Col>
      <Col md={6}>
        <Form.Group controlId="event-ends">
          <Form.Label>{t('event.ends')}</Form.Label>
          <Form.Control type="datetime-local" value={toInputDateTime(e.endsAt)} onChange={ev => set({ endsAt: fromInputDateTime(ev.target.value) ?? '' })} isInvalid={!!err('event.endsAt')} />
          <Form.Control.Feedback type="invalid">{err('event.endsAt')}</Form.Control.Feedback>
        </Form.Group>
      </Col>
      <Col md={6}>
        <Form.Group controlId="event-venue">
          <Form.Label>{t('event.venue')}</Form.Label>
          <Form.Control value={e.venue ?? ''} onChange={ev => set({ venue: ev.target.value })} />
        </Form.Group>
      </Col>
      <Col md={6}>
        <Form.Group controlId="event-organizer">
          <Form.Label>{t('event.organizer')}</Form.Label>
          <Form.Control value={e.organizer ?? ''} onChange={ev => set({ organizer: ev.target.value })} />
        </Form.Group>
      </Col>
      <Col md={4}>
        <Form.Check className="mt-4" type="switch" id="event-ticketed" label={t('event.ticketed')} checked={!!e.ticketed} onChange={ev => set({ ticketed: ev.target.checked })} />
      </Col>
      <Col md={4}>
        <Form.Group controlId="event-ticketPrice">
          <Form.Label>{t('event.ticketPrice')}</Form.Label>
          <Form.Control type="number" min={0} step="0.01" disabled={!e.ticketed} value={e.ticketPrice ?? ''} onChange={ev => set({ ticketPrice: num(ev.target.value) })} isInvalid={!!err('event.ticketPrice')} />
          <Form.Control.Feedback type="invalid">{err('event.ticketPrice')}</Form.Control.Feedback>
        </Form.Group>
      </Col>
      <Col md={4}>
        <Form.Group controlId="event-capacity">
          <Form.Label>{t('event.capacity')}</Form.Label>
          <Form.Control type="number" min={1} value={e.capacity ?? ''} onChange={ev => set({ capacity: num(ev.target.value) })} isInvalid={!!err('event.capacity')} />
          <Form.Control.Feedback type="invalid">{err('event.capacity')}</Form.Control.Feedback>
        </Form.Group>
      </Col>
    </Row>
  );
}
