import { Form, Pagination as BsPagination } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { PAGE_SIZES } from 'app/config/constants';

interface Props {
  page: number;
  size: number;
  /** The total row count when the backend sends one (the PageResponse of the edge, announce-management, order). */
  total?: number;
  /** Without a total (messaging's page): how many rows this page holds and whether a next page may exist. */
  count?: number;
  hasMore?: boolean;
  onPage: (page: number) => void;
  onSize?: (size: number) => void;
}

/**
 * 1-based page control matching the backends' PageResponse. With `total` it shows the page numbers; without it
 * (messaging answers `{items, page, size}` only) it shows the current page and enables Next while a full page came
 * back.
 */
export default function Pagination({ page, size, total, count, hasMore, onPage, onSize }: Props) {
  const { t } = useTranslation();
  const known = total !== undefined;
  const pages = known ? Math.max(1, Math.ceil(total / Math.max(size, 1))) : Math.max(page, 1);
  const current = Math.min(Math.max(page, 1), pages);
  const window = 2;
  const items: number[] = [];
  for (let p = Math.max(1, current - window); p <= Math.min(pages, current + window); p++) items.push(p);
  const nextDisabled = known ? current >= pages : !hasMore;
  return (
    <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3">
      <small className="text-muted">
        {known ? t('common.pagination.summary', { page: current, pages, total }) : t('common.pagination.summaryMore', { page: current, count: count ?? 0 })}
      </small>
      <div className="d-flex align-items-center gap-3">
        {onSize && (
          <Form.Select size="sm" style={{ width: 'auto' }} value={size} onChange={(e) => onSize(Number(e.target.value))} aria-label={t('common.pagination.size')}>
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {t('common.pagination.perPage', { size: s })}
              </option>
            ))}
          </Form.Select>
        )}
        <BsPagination size="sm" className="mb-0">
          <BsPagination.First disabled={current <= 1} onClick={() => onPage(1)} />
          <BsPagination.Prev disabled={current <= 1} onClick={() => onPage(current - 1)} />
          {items[0] > 1 && <BsPagination.Ellipsis disabled />}
          {items.map((p) => (
            <BsPagination.Item key={p} active={p === current} onClick={() => onPage(p)}>
              {p}
            </BsPagination.Item>
          ))}
          {known && items[items.length - 1] < pages && <BsPagination.Ellipsis disabled />}
          <BsPagination.Next disabled={nextDisabled} onClick={() => onPage(current + 1)} />
          {known && <BsPagination.Last disabled={current >= pages} onClick={() => onPage(pages)} />}
        </BsPagination>
      </div>
    </div>
  );
}
