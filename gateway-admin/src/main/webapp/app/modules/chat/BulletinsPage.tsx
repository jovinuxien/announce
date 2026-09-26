import { useState, type FormEvent } from 'react';
import { Button, Card, Col, Form, ListGroup, Row, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { BULLETIN_CLIENT_TYPES, useListBulletinsQuery, usePostBulletinMutation, type BulletinClientType } from 'app/shared/api/supportApi';
import { formatDateTime } from 'app/shared/format';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import MockBanner from 'app/modules/trade/MockBanner';

export default function BulletinsPage() {
  const { t, i18n } = useTranslation();
  const { data, error, isLoading } = useListBulletinsQuery();
  const [post, postState] = usePostBulletinMutation();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [clientType, setClientType] = useState<BulletinClientType>('ALL');
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;
    try {
      await post({ title: title.trim(), content: content.trim(), clientType }).unwrap();
      setTitle('');
      setContent('');
    } catch {
      /* shown */
    }
  };
  return (
    <>
      <PageHeader title={t('chat.bulletins.title')} icon="bi-broadcast" subtitle={t('chat.bulletins.subtitle')} />
      <MockBanner />
      <Row className="g-3">
        <Col lg={5}>
          <Card className="shadow-sm">
            <Card.Header>{t('chat.bulletins.post')}</Card.Header>
            <Card.Body>
              {postState.error ? <ProblemAlert error={postState.error} /> : null}
              {postState.isSuccess && <div className="text-success small mb-2">{t('chat.bulletins.posted')}</div>}
              <Form onSubmit={submit}>
                <Form.Group className="mb-3" controlId="bulletin-title">
                  <Form.Label>{t('chat.bulletins.fields.title')}</Form.Label>
                  <Form.Control value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} />
                </Form.Group>
                <Form.Group className="mb-3" controlId="bulletin-content">
                  <Form.Label>{t('chat.bulletins.fields.content')}</Form.Label>
                  <Form.Control as="textarea" rows={4} value={content} onChange={(e) => setContent(e.target.value)} />
                </Form.Group>
                <Form.Group className="mb-3" controlId="bulletin-client">
                  <Form.Label>{t('chat.bulletins.fields.clientType')}</Form.Label>
                  <Form.Select value={clientType} onChange={(e) => setClientType(e.target.value as BulletinClientType)}>
                    {BULLETIN_CLIENT_TYPES.map((c) => (
                      <option key={c} value={c}>
                        {t(`chat.clientType.${c}`)}
                      </option>
                    ))}
                  </Form.Select>
                </Form.Group>
                <Button type="submit" disabled={postState.isLoading || !title.trim() || !content.trim()}>
                  {postState.isLoading && <Spinner size="sm" animation="border" className="me-2" />}
                  {t('chat.bulletins.send')}
                </Button>
              </Form>
            </Card.Body>
          </Card>
        </Col>
        <Col lg={7}>
          <Card className="shadow-sm">
            <Card.Header>{t('chat.bulletins.recent')}</Card.Header>
            {error ? <ProblemAlert error={error} className="m-3" /> : null}
            {isLoading && <LoadingBlock />}
            {data && data.length === 0 && <EmptyState />}
            {data && data.length > 0 && (
              <ListGroup variant="flush">
                {data.map((b) => (
                  <ListGroup.Item key={b.id}>
                    <div className="d-flex justify-content-between">
                      <span className="fw-semibold">{b.title}</span>
                      <small className="text-muted">
                        {t(`chat.clientType.${b.clientType}`)} · {formatDateTime(b.postedAt, i18n.language)} · #{b.postedBy}
                      </small>
                    </div>
                    <div className="small" style={{ whiteSpace: 'pre-wrap' }}>{b.content}</div>
                  </ListGroup.Item>
                ))}
              </ListGroup>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
