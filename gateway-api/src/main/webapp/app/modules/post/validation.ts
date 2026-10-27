import { AnnounceRequest, Kind } from 'app/shared/model/announce';

/** Field -> i18n key under the `post` namespace. Mirrors the Announce aggregate's invariants. */
export type Errors = Record<string, string>;

export function validateAnnounce(req: AnnounceRequest, now: Date = new Date()): Errors {
  const errors: Errors = {};
  if (!req.kind) errors.kind = 'validation.kindRequired';
  if (!req.title || !req.title.trim()) errors.title = 'validation.titleRequired';
  else if (req.title.length > 255) errors.title = 'validation.titleTooLong';
  if (req.categoryId == null) errors.categoryId = 'validation.categoryRequired';
  if (req.kind === 'GOODS') Object.assign(errors, validateGoods(req, now));
  if (req.kind === 'JOB') Object.assign(errors, validateJob(req, now));
  if (req.kind === 'EVENT') Object.assign(errors, validateEvent(req, now));
  return errors;
}

function validateGoods(req: AnnounceRequest, now: Date): Errors {
  const errors: Errors = {};
  const g = req.goods;
  if (!g) return { goods: 'validation.goodsRequired' };
  const saleType = g.saleType ?? 'FIXED_PRICE';
  // a goods announce needs a price unless it is a giveaway or an auction
  if (saleType === 'FIXED_PRICE' && (g.price == null || Number.isNaN(g.price))) errors['goods.price'] = 'validation.priceRequired';
  if (g.price != null && g.price < 0) errors['goods.price'] = 'validation.priceNegative';
  if (saleType === 'AUCTION' && g.price != null && g.price <= 0) errors['goods.price'] = 'validation.startPricePositive';
  if (!g.condition) errors['goods.condition'] = 'validation.conditionRequired';
  // the auction schedule: optional, but start < end and the end still ahead (the aggregate: auction-schedule-order, auction-already-over)
  if (saleType === 'AUCTION') {
    const start = g.auctionStartsAt ? new Date(g.auctionStartsAt).getTime() : null;
    const end = g.auctionEndsAt ? new Date(g.auctionEndsAt).getTime() : null;
    if (start != null && end != null && !(start < end)) errors['goods.auctionEndsAt'] = 'validation.auctionEndAfterStart';
    else if (end != null && end <= now.getTime()) errors['goods.auctionEndsAt'] = 'validation.auctionEndFuture';
  }
  return errors;
}

function validateJob(req: AnnounceRequest, now: Date): Errors {
  const errors: Errors = {};
  const j = req.job;
  if (!j) return { job: 'validation.jobRequired' };
  if (j.saleType === 'AUCTION') errors['job.saleType'] = 'validation.noAuction';
  if (!j.employer || !j.employer.trim()) errors['job.employer'] = 'validation.employerRequired';
  if (!j.contractType) errors['job.contractType'] = 'validation.contractRequired';
  if (!j.howToApply || !j.howToApply.trim()) errors['job.howToApply'] = 'validation.howToApplyRequired';
  if (j.salaryMin != null && j.salaryMax != null && j.salaryMin > j.salaryMax) errors['job.salaryMax'] = 'validation.salaryRange';
  if (j.applicationDeadline && new Date(j.applicationDeadline).getTime() <= now.getTime()) errors['job.applicationDeadline'] = 'validation.deadlineFuture';
  return errors;
}

function validateEvent(req: AnnounceRequest, now: Date): Errors {
  const errors: Errors = {};
  const e = req.event;
  if (!e) return { event: 'validation.eventRequired' };
  if (e.saleType === 'AUCTION') errors['event.saleType'] = 'validation.noAuction';
  if (!e.startsAt) errors['event.startsAt'] = 'validation.startRequired';
  if (!e.endsAt) errors['event.endsAt'] = 'validation.endRequired';
  if (e.startsAt && e.endsAt) {
    const start = new Date(e.startsAt).getTime();
    const end = new Date(e.endsAt).getTime();
    // an event cannot publish unless start < end and end is in the future
    if (!(start < end)) errors['event.endsAt'] = 'validation.endAfterStart';
    else if (end <= now.getTime()) errors['event.endsAt'] = 'validation.endFuture';
  }
  if (e.ticketed && (e.ticketPrice == null || e.ticketPrice < 0)) errors['event.ticketPrice'] = 'validation.ticketPriceRequired';
  if (e.capacity != null && e.capacity <= 0) errors['event.capacity'] = 'validation.capacityPositive';
  return errors;
}

export function emptyRequest(kind: Kind): AnnounceRequest {
  const base: AnnounceRequest = { kind, title: '', brief: '', description: '', keywords: [], gallery: [], categoryId: null, location: { city: '', postalCode: '' } };
  if (kind === 'GOODS') base.goods = { price: null, condition: null, saleType: 'FIXED_PRICE', attributes: [] };
  if (kind === 'JOB') base.job = { employer: '', contractType: 'FULL_TIME', howToApply: '', currency: 'DKK', saleType: 'FIXED_PRICE' };
  if (kind === 'EVENT') base.event = { startsAt: '', endsAt: '', ticketed: false, saleType: 'FIXED_PRICE' };
  return base;
}

/** What the wizard sends: the kind's own object only, empty strings as null. */
export function toRequest(draft: AnnounceRequest): AnnounceRequest {
  const clean = <T extends object>(o: T): T =>
    Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v === '' ? null : v])) as T;
  const req: AnnounceRequest = {
    kind: draft.kind,
    title: draft.title.trim(),
    brief: draft.brief?.trim() || null,
    description: draft.description?.trim() || null,
    keywords: (draft.keywords ?? []).filter(Boolean),
    gallery: (draft.gallery ?? []).filter(Boolean),
    categoryId: draft.categoryId,
    location: draft.location && (draft.location.city || draft.location.postalCode || draft.location.regionId) ? clean(draft.location) : null,
  };
  if (draft.kind === 'GOODS' && draft.goods) {
    const g = clean(draft.goods);
    const auction = g.saleType === 'AUCTION';
    req.goods = {
      ...g,
      price: g.saleType === 'GIVEAWAY' ? null : g.price,
      attributes: (g.attributes ?? []).filter(a => a.name && a.value),
      // only an auction has a schedule (the aggregate refuses one on any other sale type)
      auctionStartsAt: auction ? g.auctionStartsAt ?? null : null,
      auctionEndsAt: auction ? g.auctionEndsAt ?? null : null,
    };
  }
  if (draft.kind === 'JOB' && draft.job) req.job = clean(draft.job);
  if (draft.kind === 'EVENT' && draft.event) req.event = clean(draft.event);
  return req;
}
