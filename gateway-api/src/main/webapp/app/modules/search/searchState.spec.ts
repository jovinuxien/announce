import { clearRefinements, emptySearch, parseSearchParams, setKind, setRange, toggleFilter, toOcsParams, toSearchHit, toSearchParams, totalPages } from './searchState';

describe('search state <-> URL <-> OCS query', () => {
  it('parses the URL and rebuilds it identically', () => {
    const s = parseSearchParams('?q=toyota&kind=goods&city=Kolding,Vamdrup&price=100..500&sort=-price&page=2&junk=');
    expect(s.q).toBe('toyota');
    expect(s.kind).toBe('GOODS');
    expect(s.filters).toEqual({ city: ['Kolding', 'Vamdrup'] });
    expect(s.ranges).toEqual({ price: { min: 100, max: 500 } });
    expect(s.sort).toBe('-price');
    expect(s.page).toBe(2);
    expect(toSearchParams(s)).toBe('q=toyota&kind=GOODS&city=Kolding%2CVamdrup&price=100..500&sort=-price&page=2');
  });

  it('builds the searcher query with offset paging, facets and comma ranges', () => {
    const s = parseSearchParams('?q=toyota&kind=GOODS&city=Kolding&price=..500&page=3&size=10');
    const p = toOcsParams(s);
    expect(p.get('q')).toBe('toyota');
    expect(p.get('limit')).toBe('10');
    expect(p.get('offset')).toBe('20');
    expect(p.get('kind')).toBe('GOODS');
    expect(p.get('city')).toBe('Kolding');
    expect(p.get('price')).toBe('0,500');
    expect(p.get('withFacets')).toBe('true');
    expect(p.get('sort')).toBeNull();
  });

  it('toggles term filters, sets ranges and resets the page', () => {
    let s = { ...emptySearch(), page: 4 };
    s = toggleFilter(s, 'condition', 'GOOD');
    expect(s.filters.condition).toEqual(['GOOD']);
    expect(s.page).toBe(1);
    s = toggleFilter(s, 'condition', 'NEW');
    s = toggleFilter(s, 'condition', 'GOOD');
    expect(s.filters.condition).toEqual(['NEW']);
    s = toggleFilter(s, 'condition', 'NEW');
    expect(s.filters.condition).toBeUndefined();
    s = setRange(s, 'price', { min: 5 });
    expect(s.ranges.price).toEqual({ min: 5 });
    s = setRange(s, 'price', {});
    expect(s.ranges.price).toBeUndefined();
  });

  it('changing the kind drops kind-specific refinements; clear keeps q and kind', () => {
    let s = parseSearchParams('?q=x&kind=JOB&contract_type=FULL_TIME&salary_min=1..2');
    s = setKind(s, 'EVENT');
    expect(s.kind).toBe('EVENT');
    expect(s.filters).toEqual({});
    expect(s.ranges).toEqual({});
    s = toggleFilter(s, 'city', 'Kolding');
    s = clearRefinements(s);
    expect(s.q).toBe('x');
    expect(s.kind).toBe('EVENT');
    expect(s.filters).toEqual({});
  });

  it('normalises a hit of either source', () => {
    const local = toSearchHit({ index: 'i', document: { id: 'a1', data: { source: 'local', kind: 'GOODS', title: 'Bike', price: 120, sale_type: 'FIXED_PRICE', city: 'Kolding' } } });
    expect(local.source).toBe('local');
    expect(local.price).toBe(120);
    expect(local.externalUrl).toBeUndefined();
    const crawled = toSearchHit({ index: 'i', document: { id: 'dba-1', data: { source: 'dba', kind: 'GOODS', title: 'Car', price: '45000.0', currency: 'DKK', external_url: 'https://www.dba.dk/x/id-1/', ends_epoch: '1790001000' } } });
    expect(crawled.source).toBe('dba');
    expect(crawled.price).toBe(45000);
    expect(crawled.endsEpoch).toBe(1790001000);
    expect(crawled.externalUrl).toContain('dba.dk');
  });

  it('pages', () => {
    expect(totalPages(0, 24)).toBe(1);
    expect(totalPages(25, 24)).toBe(2);
  });
});
