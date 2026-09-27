import { activeFilterCount, defaultFilters, filtersFromSearch, filtersToParams, filtersToSearch } from './announceFilters';

describe('announceFilters', () => {
  it('parses a valid query string', () => {
    const f = filtersFromSearch('?kind=JOB&status=PUBLISHED&seller=42&region=7&q=%20nurse%20&sort=price_desc&page=3&size=50');
    expect(f).toEqual({ kind: 'JOB', status: 'PUBLISHED', seller: '42', region: '7', saleType: '', q: 'nurse', sort: 'price_desc', page: 3, size: 50 });
  });

  it('falls back to defaults for unknown or malformed values', () => {
    const f = filtersFromSearch('?kind=CARS&status=nope&seller=abc&region=-1&sort=random&page=0&size=99999');
    expect(f).toEqual(defaultFilters);
  });

  it('round-trips through the SPA query string, omitting defaults', () => {
    const f = { ...defaultFilters, kind: 'GOODS' as const, seller: '9', page: 2 };
    const search = filtersToSearch(f);
    expect(search).toBe('?kind=GOODS&seller=9&page=2');
    expect(filtersFromSearch(search)).toEqual(f);
    expect(filtersToSearch(defaultFilters)).toBe('');
  });

  it('builds the AnnounceAdminController parameters with numbers and without empty strings', () => {
    const params = filtersToParams({ ...defaultFilters, kind: 'EVENT', region: '12', q: 'jazz' });
    expect(params).toEqual({ kind: 'EVENT', status: undefined, seller: undefined, region: 12, saleType: undefined, q: 'jazz', page: 1, size: 20, sort: 'newest' });
  });

  it('counts active filters', () => {
    expect(activeFilterCount(defaultFilters)).toBe(0);
    expect(activeFilterCount({ ...defaultFilters, kind: 'JOB', q: 'x' })).toBe(2);
  });
});
