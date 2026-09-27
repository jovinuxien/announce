import type { Kind } from 'app/shared/types';
import type { CategoryRequest } from 'app/shared/api/categoriesApi';

export interface CategoryFormValues {
  kind: Kind;
  name: string;
  description: string;
  iconUrl: string;
  pictureUrl: string;
  keywords: string;
  sortOrder: string;
}

export interface CategoryFormContext {
  /** Set when creating a child or editing: the kind is inherited and cannot change. */
  parentKind?: Kind;
  mode: 'root' | 'child' | 'edit';
}

export type CategoryFormErrors = Partial<Record<keyof CategoryFormValues, string>>;

export function emptyCategoryForm(kind: Kind): CategoryFormValues {
  return { kind, name: '', description: '', iconUrl: '', pictureUrl: '', keywords: '', sortOrder: '' };
}

/** Returns translation keys (under `catalog.errors`) per field; empty object when valid. */
export function validateCategoryForm(values: CategoryFormValues, ctx: CategoryFormContext): CategoryFormErrors {
  const errors: CategoryFormErrors = {};
  const name = values.name.trim();
  if (!name) errors.name = 'catalog.errors.nameRequired';
  else if (name.length > 63) errors.name = 'catalog.errors.nameTooLong';
  if (!values.kind) errors.kind = 'catalog.errors.kindRequired';
  if (ctx.mode !== 'root' && ctx.parentKind && values.kind !== ctx.parentKind) errors.kind = 'catalog.errors.kindFixed';
  if (values.sortOrder !== '' && (!/^-?\d+$/.test(values.sortOrder.trim()) || Number(values.sortOrder) < 0)) {
    errors.sortOrder = 'catalog.errors.sortOrderInvalid';
  }
  if (values.description.length > 255) errors.description = 'catalog.errors.descriptionTooLong';
  return errors;
}

export function toCategoryRequest(values: CategoryFormValues): CategoryRequest {
  const opt = (s: string) => (s.trim() === '' ? null : s.trim());
  return {
    kind: values.kind,
    name: values.name.trim(),
    description: opt(values.description),
    iconUrl: opt(values.iconUrl),
    pictureUrl: opt(values.pictureUrl),
    keywords: opt(values.keywords),
    sortOrder: values.sortOrder.trim() === '' ? null : Number(values.sortOrder),
  };
}
