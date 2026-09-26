import { Pagination as BsPagination } from 'react-bootstrap';

export default function Pagination({ page, pages, onChange }: { page: number; pages: number; onChange: (p: number) => void }) {
  if (pages <= 1) return null;
  const window = 2;
  const from = Math.max(1, page - window);
  const to = Math.min(pages, page + window);
  const items = [];
  for (let p = from; p <= to; p++) {
    items.push(
      <BsPagination.Item key={p} active={p === page} onClick={() => onChange(p)}>
        {p}
      </BsPagination.Item>
    );
  }
  return (
    <BsPagination className="justify-content-center my-3">
      <BsPagination.First disabled={page === 1} onClick={() => onChange(1)} />
      <BsPagination.Prev disabled={page === 1} onClick={() => onChange(page - 1)} />
      {from > 1 && <BsPagination.Ellipsis disabled />}
      {items}
      {to < pages && <BsPagination.Ellipsis disabled />}
      <BsPagination.Next disabled={page === pages} onClick={() => onChange(page + 1)} />
      <BsPagination.Last disabled={page === pages} onClick={() => onChange(pages)} />
    </BsPagination>
  );
}
