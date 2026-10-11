import { ORDER_STATUSES } from 'app/shared/api/ordersApi';
import { activeTradeFilterCount, tradeFiltersFromSearch, tradeFiltersToParams } from './tradeFilters';

describe('tradeFilters', () => {
  it('reads only known statuses and numeric ids from the query', () => {
    const f = tradeFiltersFromSearch(new URLSearchParams('status=DISPUTED&seller=7&buyer=abc&q=%20bike%20'), ORDER_STATUSES);
    expect(f).toEqual({ status: 'DISPUTED', seller: '7', buyer: '', q: 'bike' });
    expect(tradeFiltersFromSearch(new URLSearchParams('status=SNAGGED'), ORDER_STATUSES).status).toBe('');
    expect(activeTradeFilterCount(f)).toBe(3);
  });

  it('turns the filters into request parameters, dropping empty values', () => {
    expect(tradeFiltersToParams({ status: 'PAID', seller: '7', buyer: '', q: '' })).toEqual({ status: 'PAID', seller: 7, buyer: undefined, q: undefined });
    expect(tradeFiltersToParams({ status: '', seller: '', buyer: '42', q: 'drill' })).toEqual({ status: undefined, seller: undefined, buyer: 42, q: 'drill' });
  });
});
