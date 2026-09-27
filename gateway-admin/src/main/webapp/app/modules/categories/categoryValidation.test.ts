import { emptyCategoryForm, toCategoryRequest, validateCategoryForm } from './categoryValidation';

describe('validateCategoryForm', () => {
  it('requires a name', () => {
    const errors = validateCategoryForm(emptyCategoryForm('GOODS'), { mode: 'root' });
    expect(errors.name).toBe('catalog.errors.nameRequired');
    expect(validateCategoryForm({ ...emptyCategoryForm('GOODS'), name: '   ' }, { mode: 'root' }).name).toBe('catalog.errors.nameRequired');
  });

  it('accepts a valid root of any kind', () => {
    expect(validateCategoryForm({ ...emptyCategoryForm('JOB'), name: 'Healthcare' }, { mode: 'root' })).toEqual({});
    expect(validateCategoryForm({ ...emptyCategoryForm('EVENT'), name: 'Concerts', sortOrder: '10' }, { mode: 'root' })).toEqual({});
  });

  it('keeps the kind fixed for a child: it must match the parent', () => {
    const child = { ...emptyCategoryForm('GOODS'), name: 'Cars' };
    expect(validateCategoryForm(child, { mode: 'child', parentKind: 'GOODS' })).toEqual({});
    expect(validateCategoryForm({ ...child, kind: 'JOB' }, { mode: 'child', parentKind: 'GOODS' }).kind).toBe('catalog.errors.kindFixed');
  });

  it('keeps the kind fixed when editing', () => {
    expect(validateCategoryForm({ ...emptyCategoryForm('EVENT'), name: 'Sports' }, { mode: 'edit', parentKind: 'JOB' }).kind).toBe('catalog.errors.kindFixed');
  });

  it('rejects a negative or non-integer sort order and over-long text', () => {
    expect(validateCategoryForm({ ...emptyCategoryForm('GOODS'), name: 'x', sortOrder: '-1' }, { mode: 'root' }).sortOrder).toBe('catalog.errors.sortOrderInvalid');
    expect(validateCategoryForm({ ...emptyCategoryForm('GOODS'), name: 'x', sortOrder: '1.5' }, { mode: 'root' }).sortOrder).toBe('catalog.errors.sortOrderInvalid');
    expect(validateCategoryForm({ ...emptyCategoryForm('GOODS'), name: 'a'.repeat(64) }, { mode: 'root' }).name).toBe('catalog.errors.nameTooLong');
    expect(validateCategoryForm({ ...emptyCategoryForm('GOODS'), name: 'x', description: 'd'.repeat(256) }, { mode: 'root' }).description).toBe('catalog.errors.descriptionTooLong');
  });

  it('maps the form to CategoryController.CategoryRequest with nulls for blanks', () => {
    expect(toCategoryRequest({ ...emptyCategoryForm('JOB'), name: ' Nursing ', sortOrder: '5' })).toEqual({
      kind: 'JOB', name: 'Nursing', description: null, iconUrl: null, pictureUrl: null, keywords: null, sortOrder: 5,
    });
  });
});
