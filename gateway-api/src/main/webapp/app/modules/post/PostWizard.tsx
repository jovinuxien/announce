import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Form, ProgressBar, Row, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { announceApi } from 'app/shared/api/announceApi';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { AnnounceRequest, AnnounceResponse, Kind, KINDS } from 'app/shared/model/announce';
import { describeError } from 'app/shared/util/problem';
import { useCategories } from 'app/modules/home/useCategories';
import { EventForm, GoodsForm, JobForm } from './KindForms';
import { emptyRequest, Errors, toRequest, validateAnnounce } from './validation';

const STEPS = ['kind', 'category', 'details', 'location', 'preview'] as const;
type Step = (typeof STEPS)[number];

function fromResponse(a: AnnounceResponse): AnnounceRequest {
  return {
    kind: a.kind,
    title: a.title,
    brief: a.brief ?? '',
    description: a.description ?? '',
    keywords: a.keywords ?? [],
    gallery: a.gallery ?? [],
    categoryId: a.categoryId ?? null,
    location: a.location ?? { city: '', postalCode: '' },
    goods: a.goods ?? undefined,
    job: a.job ?? undefined,
    event: a.event ?? undefined,
  };
}

/** Kind -> category (tree of that kind) -> kind-specific details -> location & gallery -> preview, save draft, publish. */
export default function PostWizard() {
  const { t } = useTranslation('post');
  const navigate = useNavigate();
  const { kind: kindParam, id } = useParams();
  const initialKind = (kindParam?.toUpperCase() as Kind) || null;
  const [draft, setDraft] = useState<AnnounceRequest>(() => emptyRequest(initialKind && KINDS.includes(initialKind) ? initialKind : 'GOODS'));
  const [step, setStep] = useState<Step>(id ? 'details' : initialKind ? 'category' : 'kind');
  const [existing, setExisting] = useState<AnnounceResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(!!id);
  const [touched, setTouched] = useState(false);
  const { tree } = useCategories(draft.kind);

  useEffect(() => {
    if (!id) return;
    announceApi
      .get(id)
      .then(a => {
        setExisting(a);
        setDraft(fromResponse(a));
      })
      .catch(e => setError(describeError(e, t('loadFailed'))))
      .finally(() => setLoading(false));
  }, [id, t]);

  const errors: Errors = useMemo(() => validateAnnounce(toRequest(draft)), [draft]);
  const err = (field: string) => (touched && errors[field] ? t(errors[field]) : undefined);
  const stepIndex = STEPS.indexOf(step);

  const setKind = (kind: Kind) => {
    setDraft(emptyRequest(kind));
    setStep('category');
  };

  const next = () => {
    if (step === 'details' || step === 'category') setTouched(true);
    if (step === 'category' && draft.categoryId == null) return;
    if (step === 'details' && Object.keys(errors).some(k => k !== 'categoryId' && !k.startsWith('location'))) return;
    setStep(STEPS[Math.min(STEPS.length - 1, stepIndex + 1)]);
  };
  const back = () => setStep(STEPS[Math.max(0, stepIndex - 1)]);

  const save = async (publish: boolean) => {
    setTouched(true);
    if (Object.keys(errors).length) {
      setStep('details');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const body = toRequest(draft);
      const saved = existing ? await announceApi.update(existing.id, body) : await announceApi.create(body);
      if (publish) await announceApi.publish(saved.id);
      navigate(publish ? `/announce/${encodeURIComponent(saved.id)}` : '/my/announces');
    } catch (e) {
      setError(describeError(e, t('saveFailed')));
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <div className="text-center py-5"><Spinner animation="border" /></div>;

  return (
    <Card className="mx-auto" style={{ maxWidth: 860 }}>
      <Card.Body>
        <h1 className="h4">{existing ? t('editTitle') : t('title')}</h1>
        <ProgressBar now={((stepIndex + 1) / STEPS.length) * 100} label={t(`step.${step}`)} className="mb-3" />
        <ProblemAlert message={error} onClose={() => setError(null)} />

        {step === 'kind' && (
          <Row className="g-3">
            {KINDS.map(k => (
              <Col md={4} key={k}>
                <Card role="button" onClick={() => setKind(k)} className="h-100 text-center">
                  <Card.Body>
                    <div className="fs-1">{k === 'GOODS' ? '📦' : k === 'JOB' ? '💼' : '🎟️'}</div>
                    <Card.Title as="h6">{t(`common:kind.${k}`)}</Card.Title>
                    <Card.Text className="small text-muted">{t(`kindHint.${k}`)}</Card.Text>
                  </Card.Body>
                </Card>
              </Col>
            ))}
          </Row>
        )}

        {step === 'category' && (
          <Form.Group>
            <Form.Label>{t('category.label', { kind: t(`common:kind.${draft.kind}`) })}</Form.Label>
            <Form.Select value={draft.categoryId ?? ''} onChange={e => setDraft({ ...draft, categoryId: e.target.value ? Number(e.target.value) : null })} isInvalid={!!err('categoryId')}>
              <option value="">{t('category.choose')}</option>
              {tree.map(n => (
                <optgroup key={n.category.id} label={n.category.name}>
                  <option value={n.category.id}>{n.category.name}</option>
                  {n.children.map(c => (
                    <option key={c.id} value={c.id}>
                      {'  '}{c.name}
                    </option>
                  ))}
                </optgroup>
              ))}
            </Form.Select>
            <Form.Control.Feedback type="invalid">{err('categoryId')}</Form.Control.Feedback>
            {tree.length === 0 && <Form.Text>{t('category.none')}</Form.Text>}
          </Form.Group>
        )}

        {step === 'details' && (
          <>
            <Form.Group className="mb-3" controlId="post-title">
              <Form.Label>{t('fields.title')}</Form.Label>
              <Form.Control value={draft.title} maxLength={255} onChange={e => setDraft({ ...draft, title: e.target.value })} isInvalid={!!err('title')} />
              <Form.Control.Feedback type="invalid">{err('title')}</Form.Control.Feedback>
            </Form.Group>
            <Form.Group className="mb-3" controlId="post-brief">
              <Form.Label>{t('fields.brief')}</Form.Label>
              <Form.Control value={draft.brief ?? ''} maxLength={255} onChange={e => setDraft({ ...draft, brief: e.target.value })} />
            </Form.Group>
            <Form.Group className="mb-3" controlId="post-description">
              <Form.Label>{t('fields.description')}</Form.Label>
              <Form.Control as="textarea" rows={5} value={draft.description ?? ''} onChange={e => setDraft({ ...draft, description: e.target.value })} />
            </Form.Group>
            {draft.kind === 'GOODS' && <GoodsForm draft={draft} onChange={setDraft} err={err} />}
            {draft.kind === 'JOB' && <JobForm draft={draft} onChange={setDraft} err={err} />}
            {draft.kind === 'EVENT' && <EventForm draft={draft} onChange={setDraft} err={err} />}
          </>
        )}

        {step === 'location' && (
          <>
            <Row className="g-3 mb-3">
              <Col md={4}>
                <Form.Group controlId="post-postal">
                  <Form.Label>{t('fields.postalCode')}</Form.Label>
                  <Form.Control value={draft.location?.postalCode ?? ''} onChange={e => setDraft({ ...draft, location: { ...draft.location, postalCode: e.target.value } })} />
                </Form.Group>
              </Col>
              <Col md={8}>
                <Form.Group controlId="post-city">
                  <Form.Label>{t('fields.city')}</Form.Label>
                  <Form.Control value={draft.location?.city ?? ''} onChange={e => setDraft({ ...draft, location: { ...draft.location, city: e.target.value } })} />
                </Form.Group>
              </Col>
            </Row>
            <Form.Group className="mb-3" controlId="post-gallery">
              <Form.Label>{t('fields.gallery')}</Form.Label>
              <Form.Control as="textarea" rows={3} value={(draft.gallery ?? []).join('\n')} onChange={e => setDraft({ ...draft, gallery: e.target.value.split('\n').map(s => s.trim()) })} placeholder="https://…" />
              <Form.Text>{t('fields.galleryHint')}</Form.Text>
            </Form.Group>
            <Form.Group className="mb-3" controlId="post-keywords">
              <Form.Label>{t('fields.keywords')}</Form.Label>
              <Form.Control value={(draft.keywords ?? []).join(', ')} onChange={e => setDraft({ ...draft, keywords: e.target.value.split(',').map(s => s.trim()) })} />
            </Form.Group>
          </>
        )}

        {step === 'preview' && (
          <div>
            <h2 className="h5">{draft.title}</h2>
            <p className="text-muted small">
              {t(`common:kind.${draft.kind}`)} · {t('preview.category', { id: draft.categoryId })} · {[draft.location?.postalCode, draft.location?.city].filter(Boolean).join(' ')}
            </p>
            <p style={{ whiteSpace: 'pre-wrap' }}>{draft.description || draft.brief}</p>
            <pre className="bg-light p-2 small rounded" style={{ maxHeight: 240, overflow: 'auto' }}>{JSON.stringify(toRequest(draft), null, 2)}</pre>
            {Object.keys(errors).length > 0 && (
              <ul className="text-danger small">
                {Object.entries(errors).map(([f, k]) => (
                  <li key={f}>{t(k)}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="d-flex justify-content-between mt-4">
          <div>
            {stepIndex > 0 && !(existing && step === 'details') && (
              <Button variant="outline-secondary" onClick={back}>{t('nav.back')}</Button>
            )}
          </div>
          <div className="d-flex gap-2">
            <Button as={Link as any} to="/my/announces" variant="link">{t('nav.cancel')}</Button>
            {step !== 'preview' && step !== 'kind' && (
              <Button onClick={next}>{t('nav.next')}</Button>
            )}
            {step === 'preview' && (
              <>
                <Button variant="outline-primary" disabled={busy} onClick={() => save(false)}>{t('nav.saveDraft')}</Button>
                <Button disabled={busy || Object.keys(errors).length > 0} onClick={() => save(true)}>{t('nav.publish')}</Button>
              </>
            )}
          </div>
        </div>
      </Card.Body>
    </Card>
  );
}
