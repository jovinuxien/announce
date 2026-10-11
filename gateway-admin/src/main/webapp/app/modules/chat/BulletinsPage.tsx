import { useCallback, useState, type FormEvent } from 'react';
import { Badge, Button, Card, Col, Form, ListGroup, Row, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import {
  BULLETIN_AUDIENCES,
  BULLETIN_CLIENT_TYPES,
  STAFF_CLIENT_TYPE,
  bulletinOfFrame,
  useListBulletinsQuery,
  usePostBulletinMutation,
  type BulletinAudience,
  type BulletinView,
  type ServerFrame,
} from 'app/shared/api/supportApi';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import MockBanner from 'app/modules/trade/MockBanner';
import { useSocket } from './useSocket';

export const BULLETIN_MAX_LENGTH = 1023;

/** The first line of a bulletin is its heading; the rest is the body. */
export function splitBulletin(content: string): { heading: string; body: string } {
  const [heading, ...rest] = content.split('\n');
  return { heading: heading.trim(), body: rest.join('\n').trim() };
}

/** `datetime-local` value (local wall clock) -> the LocalDateTime string messaging takes; null when empty. */
export function toLocalDateTime(value: string): string | null {
  if (!value) return null;
  return value.length === 16 ? `${value}:00` : value;
}

const clientTypeKey = (value: string | null) => (value ? value : 'all');

/**
 * Staff bulletins: a row in announce_bulletin pushed live to the connected staff dashboards of the chosen client
 * type (`/ws/staff?clientType=`). The form takes the content (first line = heading), the audience, the client
 * type and an optional expiry; the list shows the current bulletins of an audience and prepends the ones that
 * arrive live while the page is open. See doc/frontend-edges.md §14.
 */
export default function BulletinsPage() {
  const { t, i18n } = useTranslation();
  const [audience, setAudience] = useState<BulletinAudience>('STAFF');
  const { data, error, isLoading } = useListBulletinsQuery({ audience });
  const [post, postState] = usePostBulletinMutation();
  const [content, setContent] = useState('');
  const [formAudience, setFormAudience] = useState<BulletinAudience>('STAFF');
  const [clientType, setClientType] = useState<string>('');
  const [expiresAt, setExpiresAt] = useState('');
  const [live, setLive] = useState<BulletinView[]>([]);

  const onFrame = useCallback((f: ServerFrame) => {
    if (f.type === 'bulletin') setLive((prev) => (prev.some((b) => b.id === f.id) ? prev : [bulletinOfFrame(f), ...prev]));
  }, []);
  const { state: socketState } = useSocket('/ws/staff', { clientType: STAFF_CLIENT_TYPE }, onFrame);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const text = content.trim();
    if (!text || text.length > BULLETIN_MAX_LENGTH) return;
    try {
      await post({ audience: formAudience, clientType: clientType || null, content: text, expiresAt: toLocalDateTime(expiresAt) }).unwrap();
      setContent('');
      setExpiresAt('');
    } catch {
      /* shown */
    }
  };

  const shown: BulletinView[] = [...live.filter((b) => b.audience === audience && !(data ?? []).some((d) => d.id === b.id)), ...(data ?? [])];

  return (
    <>
      <PageHeader
        title={t('chat.bulletins.title')}
        icon="bi-broadcast"
        subtitle={t('chat.bulletins.subtitle')}
        actions={
          <Badge bg={socketState === 'open' ? 'success' : 'light'} text={socketState === 'open' ? undefined : 'dark'} data-testid="socket-state">
            {t(`chat.socket.${socketState}`)}
          </Badge>
        }
      />
      <MockBanner />
      <Row className="g-3">
        <Col lg={5}>
          <Card className="shadow-sm">
            <Card.Header>{t('chat.bulletins.post')}</Card.Header>
            <Card.Body>
              {postState.error ? <ProblemAlert error={postState.error} /> : null}
              {postState.isSuccess && <div className="text-success small mb-2">{t('chat.bulletins.posted')}</div>}
              <Form onSubmit={submit}>
                <Form.Group className="mb-3" controlId="bulletin-content">
                  <Form.Label>{t('chat.bulletins.fields.content')}</Form.Label>
                  <Form.Control as="textarea" rows={5} value={content} onChange={(e) => setContent(e.target.value)} maxLength={BULLETIN_MAX_LENGTH} />
                  <Form.Text muted>{t('chat.bulletins.contentHelp', { max: BULLETIN_MAX_LENGTH, left: BULLETIN_MAX_LENGTH - content.length })}</Form.Text>
                </Form.Group>
                <Row className="g-2">
                  <Col sm={6}>
                    <Form.Group className="mb-3" controlId="bulletin-audience">
                      <Form.Label>{t('chat.bulletins.fields.audience')}</Form.Label>
                      <Form.Select value={formAudience} onChange={(e) => setFormAudience(e.target.value as BulletinAudience)}>
                        {BULLETIN_AUDIENCES.map((a) => (
                          <option key={a} value={a}>
                            {t(`chat.audience.${a}`)}
                          </option>
                        ))}
                      </Form.Select>
                    </Form.Group>
                  </Col>
                  <Col sm={6}>
                    <Form.Group className="mb-3" controlId="bulletin-client">
                      <Form.Label>{t('chat.bulletins.fields.clientType')}</Form.Label>
                      <Form.Select value={clientType} onChange={(e) => setClientType(e.target.value)}>
                        {BULLETIN_CLIENT_TYPES.map((c) => (
                          <option key={c || 'all'} value={c}>
                            {t(`chat.clientType.${clientTypeKey(c)}`)}
                          </option>
                        ))}
                      </Form.Select>
                    </Form.Group>
                  </Col>
                </Row>
                <Form.Group className="mb-3" controlId="bulletin-expires">
                  <Form.Label>{t('chat.bulletins.fields.expiresAt')}</Form.Label>
                  <Form.Control type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
                </Form.Group>
                <Button type="submit" disabled={postState.isLoading || !content.trim()}>
                  {postState.isLoading && <Spinner size="sm" animation="border" className="me-2" />}
                  {t('chat.bulletins.send')}
                </Button>
              </Form>
            </Card.Body>
          </Card>
        </Col>
        <Col lg={7}>
          <Card className="shadow-sm">
            <Card.Header className="d-flex justify-content-between align-items-center">
              <span>{t('chat.bulletins.current')}</span>
              <Form.Select size="sm" style={{ width: 'auto' }} value={audience} onChange={(e) => setAudience(e.target.value as BulletinAudience)} aria-label={t('chat.bulletins.fields.audience')}>
                {BULLETIN_AUDIENCES.map((a) => (
                  <option key={a} value={a}>
                    {t(`chat.audience.${a}`)}
                  </option>
                ))}
              </Form.Select>
            </Card.Header>
            {error ? <ProblemAlert error={error} className="m-3" /> : null}
            {isLoading && <LoadingBlock />}
            {data && shown.length === 0 && <EmptyState />}
            {shown.length > 0 && (
              <ListGroup variant="flush" data-testid="bulletin-list">
                {shown.map((b) => {
                  const { heading, body } = splitBulletin(b.content);
                  return (
                    <ListGroup.Item key={b.id}>
                      <div className="d-flex justify-content-between gap-2">
                        <span className="fw-semibold">{heading}</span>
                        <small className="text-muted text-nowrap">
                          {t(`chat.clientType.${clientTypeKey(b.clientType)}`, { defaultValue: b.clientType ?? '' })} · {formatDateTime(b.createdAt, i18n.language)} · #{b.adminId}
                        </small>
                      </div>
                      {body && (
                        <div className="small" style={{ whiteSpace: 'pre-wrap' }}>
                          {body}
                        </div>
                      )}
                      {b.expiresAt && <div className="small text-muted">{t('chat.bulletins.expires', { at: formatDateTime(b.expiresAt, i18n.language) })}</div>}
                    </ListGroup.Item>
                  );
                })}
              </ListGroup>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
