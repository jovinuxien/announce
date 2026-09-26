import { useState } from 'react';
import { Badge, Button, ButtonGroup, Card, Form, Modal, Nav, Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { KINDS, type Kind } from 'app/shared/types';
import {
  useCreateChildCategoryMutation, useCreateRootCategoryMutation, useGetCategoryTreeQuery, useHideCategoryMutation,
  useMoveCategoryMutation, useShowCategoryMutation, useUpdateCategoryMutation, type CategoryNodeResponse, type CategoryResponse,
} from 'app/shared/api/categoriesApi';
import PageHeader from 'app/shared/ui/PageHeader';
import ProblemAlert from 'app/shared/ui/ProblemAlert';
import LoadingBlock from 'app/shared/ui/LoadingBlock';
import EmptyState from 'app/shared/ui/EmptyState';
import KindBadge from 'app/shared/ui/KindBadge';
import CategoryFormDialog from './CategoryFormDialog';
import { toCategoryRequest, type CategoryFormContext, type CategoryFormValues } from './categoryValidation';

type Dialog =
  | { type: 'root' }
  | { type: 'child'; parent: CategoryResponse }
  | { type: 'edit'; category: CategoryResponse }
  | { type: 'move'; category: CategoryResponse }
  | null;

export default function CategoriesPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const kind = (KINDS.includes(params.get('kind') as Kind) ? params.get('kind') : 'GOODS') as Kind;
  const { data, error, isLoading } = useGetCategoryTreeQuery({ kind });
  const [dialog, setDialog] = useState<Dialog>(null);
  const [showHidden, setShowHidden] = useState(true);

  const [createRoot, rootState] = useCreateRootCategoryMutation();
  const [createChild, childState] = useCreateChildCategoryMutation();
  const [update, updateState] = useUpdateCategoryMutation();
  const [move, moveState] = useMoveCategoryMutation();
  const [hide, hideState] = useHideCategoryMutation();
  const [show, showState] = useShowCategoryMutation();
  const busy = rootState.isLoading || childState.isLoading || updateState.isLoading || moveState.isLoading;
  const dialogError = dialog?.type === 'root' ? rootState.error : dialog?.type === 'child' ? childState.error : dialog?.type === 'edit' ? updateState.error : moveState.error;
  const actionError = hideState.error || showState.error;

  const submit = async (values: CategoryFormValues) => {
    const body = toCategoryRequest(values);
    try {
      if (dialog?.type === 'root') await createRoot(body).unwrap();
      else if (dialog?.type === 'child') await createChild({ parentId: dialog.parent.id, body }).unwrap();
      else if (dialog?.type === 'edit') await update({ id: dialog.category.id, body: { name: body.name, description: body.description } }).unwrap();
      setDialog(null);
    } catch {
      /* dialog shows the problem */
    }
  };

  const context: CategoryFormContext =
    dialog?.type === 'child' ? { mode: 'child', parentKind: dialog.parent.kind } : dialog?.type === 'edit' ? { mode: 'edit', parentKind: dialog.category.kind } : { mode: 'root' };

  const roots: CategoryNodeResponse[] = (data ?? []).filter((n) => showHidden || !n.category.hidden);

  return (
    <>
      <PageHeader
        title={t('catalog.categories.title')}
        icon="bi-diagram-3"
        actions={
          <>
            <Form.Check type="switch" id="show-hidden" label={t('catalog.showHidden')} checked={showHidden} onChange={(e) => setShowHidden(e.target.checked)} className="me-3 mt-1" />
            <Button onClick={() => setDialog({ type: 'root' })}>
              <i className="bi bi-plus-lg me-1" aria-hidden />
              {t('catalog.categories.createRoot')}
            </Button>
          </>
        }
      />
      <Nav variant="tabs" activeKey={kind} onSelect={(k) => k && setParams({ kind: k })} className="mb-3">
        {KINDS.map((k) => (
          <Nav.Item key={k}>
            <Nav.Link eventKey={k}>
              <KindBadge kind={k} />
            </Nav.Link>
          </Nav.Item>
        ))}
      </Nav>
      {error ? <ProblemAlert error={error} /> : null}
      {actionError ? <ProblemAlert error={actionError} /> : null}
      {isLoading && <LoadingBlock />}
      {data && roots.length === 0 && <EmptyState hint={t('catalog.categories.emptyHint')} />}
      {roots.map((node) => (
        <Card key={node.category.id} className="mb-2 shadow-sm">
          <Card.Header className="d-flex align-items-center gap-2">
            <i className="bi bi-folder2-open text-secondary" aria-hidden />
            <span className={`fw-semibold ${node.category.hidden ? 'text-decoration-line-through text-muted' : ''}`}>{node.category.name}</span>
            {node.category.hidden && <Badge bg="secondary">{t('catalog.hidden')}</Badge>}
            <small className="text-muted">#{node.category.id}</small>
            {node.category.description && <small className="text-muted text-truncate">— {node.category.description}</small>}
            <ButtonGroup size="sm" className="ms-auto">
              <Button variant="outline-primary" onClick={() => setDialog({ type: 'child', parent: node.category })} title={t('catalog.categories.addChild')}>
                <i className="bi bi-plus" aria-hidden /> {t('catalog.categories.addChild')}
              </Button>
              <Button variant="outline-secondary" onClick={() => setDialog({ type: 'edit', category: node.category })} title={t('common.actions.edit')}>
                <i className="bi bi-pencil" aria-hidden />
              </Button>
              {node.category.hidden ? (
                <Button variant="outline-success" onClick={() => show(node.category.id)} title={t('catalog.actions.show')}>
                  <i className="bi bi-eye" aria-hidden />
                </Button>
              ) : (
                <Button variant="outline-danger" onClick={() => hide(node.category.id)} title={t('catalog.actions.hide')}>
                  <i className="bi bi-eye-slash" aria-hidden />
                </Button>
              )}
            </ButtonGroup>
          </Card.Header>
          {node.children.length > 0 && (
            <Card.Body className="py-2">
              {node.children
                .filter((c) => showHidden || !c.hidden)
                .map((c) => (
                  <div key={c.id} className="tree-node d-flex align-items-center gap-2 ps-3 py-1 ms-2">
                    <i className="bi bi-tag text-secondary" aria-hidden />
                    <span className={c.hidden ? 'text-decoration-line-through text-muted' : ''}>{c.name}</span>
                    {c.hidden && <Badge bg="secondary">{t('catalog.hidden')}</Badge>}
                    <small className="text-muted">#{c.id}</small>
                    {c.description && <small className="text-muted text-truncate">— {c.description}</small>}
                    <ButtonGroup size="sm" className="ms-auto">
                      <Button variant="outline-secondary" onClick={() => setDialog({ type: 'edit', category: c })} title={t('common.actions.edit')}>
                        <i className="bi bi-pencil" aria-hidden />
                      </Button>
                      <Button variant="outline-secondary" onClick={() => setDialog({ type: 'move', category: c })} title={t('catalog.actions.move')}>
                        <i className="bi bi-arrows-move" aria-hidden />
                      </Button>
                      {c.hidden ? (
                        <Button variant="outline-success" onClick={() => show(c.id)} title={t('catalog.actions.show')}>
                          <i className="bi bi-eye" aria-hidden />
                        </Button>
                      ) : (
                        <Button variant="outline-danger" onClick={() => hide(c.id)} title={t('catalog.actions.hide')}>
                          <i className="bi bi-eye-slash" aria-hidden />
                        </Button>
                      )}
                    </ButtonGroup>
                  </div>
                ))}
            </Card.Body>
          )}
        </Card>
      ))}

      <CategoryFormDialog
        show={dialog?.type === 'root' || dialog?.type === 'child' || dialog?.type === 'edit'}
        title={dialog?.type === 'root' ? t('catalog.categories.createRoot') : dialog?.type === 'child' ? t('catalog.categories.addChildTo', { name: dialog.parent.name }) : t('catalog.categories.edit')}
        context={context}
        kind={kind}
        initial={dialog?.type === 'edit' ? { kind: dialog.category.kind, name: dialog.category.name, description: dialog.category.description ?? '' } : { kind }}
        busy={busy}
        error={dialogError}
        onSubmit={submit}
        onCancel={() => setDialog(null)}
      />

      <Modal show={dialog?.type === 'move'} onHide={() => setDialog(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title>{t('catalog.actions.move')}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {moveState.error ? <ProblemAlert error={moveState.error} /> : null}
          {dialog?.type === 'move' && (
            <>
              <p>{t('catalog.categories.moveHint', { name: dialog.category.name })}</p>
              <Form.Select
                aria-label={t('catalog.categories.newParent')}
                defaultValue=""
                onChange={async (e) => {
                  const parentId = Number(e.target.value);
                  if (!parentId) return;
                  try {
                    await move({ id: dialog.category.id, body: { parentId } }).unwrap();
                    setDialog(null);
                  } catch {
                    /* shown */
                  }
                }}
                disabled={moveState.isLoading}
              >
                <option value="">{t('catalog.categories.newParent')}</option>
                {(data ?? [])
                  .filter((n) => n.category.id !== dialog.category.parentId)
                  .map((n) => (
                    <option key={n.category.id} value={n.category.id}>
                      {n.category.name}
                    </option>
                  ))}
              </Form.Select>
              {moveState.isLoading && <Spinner animation="border" size="sm" className="mt-2" />}
            </>
          )}
        </Modal.Body>
      </Modal>
    </>
  );
}
