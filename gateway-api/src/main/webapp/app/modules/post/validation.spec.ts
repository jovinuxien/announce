import { emptyRequest, toRequest, validateAnnounce } from './validation';

const NOW = new Date('2026-09-26T12:00:00');

describe('post wizard validation mirrors the aggregate invariants', () => {
  it('goods needs a price unless giveaway or auction', () => {
    const goods = emptyRequest('GOODS');
    goods.title = 'Bike';
    goods.categoryId = 1;
    goods.goods = { saleType: 'FIXED_PRICE', price: null, condition: 'GOOD' };
    expect(validateAnnounce(goods, NOW)['goods.price']).toBe('validation.priceRequired');

    goods.goods = { saleType: 'GIVEAWAY', price: null, condition: 'GOOD' };
    expect(validateAnnounce(goods, NOW)).toEqual({});

    goods.goods = { saleType: 'AUCTION', price: null, condition: 'GOOD' };
    expect(validateAnnounce(goods, NOW)).toEqual({});

    goods.goods = { saleType: 'FIXED_PRICE', price: 120, condition: null };
    expect(validateAnnounce(goods, NOW)['goods.condition']).toBe('validation.conditionRequired');
  });

  it('an event cannot publish unless start < end and end is in the future', () => {
    const ev = emptyRequest('EVENT');
    ev.title = 'Concert';
    ev.categoryId = 5;
    ev.event = { startsAt: '2026-10-02T20:00:00', endsAt: '2026-10-02T18:00:00' };
    expect(validateAnnounce(ev, NOW)['event.endsAt']).toBe('validation.endAfterStart');

    ev.event = { startsAt: '2026-09-01T20:00:00', endsAt: '2026-09-01T22:00:00' };
    expect(validateAnnounce(ev, NOW)['event.endsAt']).toBe('validation.endFuture');

    ev.event = { startsAt: '2026-10-02T18:00:00', endsAt: '2026-10-02T20:00:00', ticketed: true, ticketPrice: null };
    expect(validateAnnounce(ev, NOW)['event.ticketPrice']).toBe('validation.ticketPriceRequired');

    ev.event = { startsAt: '2026-10-02T18:00:00', endsAt: '2026-10-02T20:00:00', ticketed: false };
    expect(validateAnnounce(ev, NOW)).toEqual({});
  });

  it('a job or event never has an auction sale type', () => {
    const job = emptyRequest('JOB');
    job.title = 'Mechanic';
    job.categoryId = 7;
    job.job = { employer: 'Auto', contractType: 'FULL_TIME', howToApply: 'mail', saleType: 'AUCTION' };
    expect(validateAnnounce(job, NOW)['job.saleType']).toBe('validation.noAuction');

    const ev = emptyRequest('EVENT');
    ev.title = 'x';
    ev.categoryId = 1;
    ev.event = { startsAt: '2026-10-02T18:00:00', endsAt: '2026-10-02T20:00:00', saleType: 'AUCTION' };
    expect(validateAnnounce(ev, NOW)['event.saleType']).toBe('validation.noAuction');
  });

  it('job fields: employer, how to apply, salary range, future deadline', () => {
    const job = emptyRequest('JOB');
    job.title = 'Mechanic';
    job.categoryId = 7;
    job.job = { employer: '', contractType: 'FULL_TIME', howToApply: '', salaryMin: 30, salaryMax: 20, applicationDeadline: '2026-01-01T00:00:00' };
    const errors = validateAnnounce(job, NOW);
    expect(errors['job.employer']).toBe('validation.employerRequired');
    expect(errors['job.howToApply']).toBe('validation.howToApplyRequired');
    expect(errors['job.salaryMax']).toBe('validation.salaryRange');
    expect(errors['job.applicationDeadline']).toBe('validation.deadlineFuture');
  });

  it('shared fields: title and category', () => {
    const goods = emptyRequest('GOODS');
    const errors = validateAnnounce(goods, NOW);
    expect(errors.title).toBe('validation.titleRequired');
    expect(errors.categoryId).toBe('validation.categoryRequired');
  });

  it('toRequest sends only the kind object, blanks as null, no price for a giveaway', () => {
    const goods = emptyRequest('GOODS');
    goods.title = ' Bike ';
    goods.categoryId = 1;
    goods.goods = { saleType: 'GIVEAWAY', price: 10, condition: 'GOOD', attributes: [{ name: 'color', value: 'red' }, { name: '', value: '' }] };
    const req = toRequest(goods);
    expect(req.title).toBe('Bike');
    expect(req.goods?.price).toBeNull();
    expect(req.goods?.attributes).toEqual([{ name: 'color', value: 'red' }]);
    expect(req.job).toBeUndefined();
    expect(req.event).toBeUndefined();
    expect(req.location).toBeNull();
    expect(req.brief).toBeNull();
  });
});
