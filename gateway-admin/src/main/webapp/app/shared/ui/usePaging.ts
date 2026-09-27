import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DEFAULT_PAGE_SIZE } from 'app/config/constants';

/** Page and size kept in the URL so lists survive a reload and the back button. */
export function usePaging(defaultSize = DEFAULT_PAGE_SIZE) {
  const [params, setParams] = useSearchParams();
  const page = Math.max(1, Number(params.get('page') ?? 1) || 1);
  const size = Math.max(1, Number(params.get('size') ?? defaultSize) || defaultSize);
  const setPage = useCallback(
    (p: number) => {
      const next = new URLSearchParams(params);
      next.set('page', String(p));
      setParams(next);
    },
    [params, setParams],
  );
  const setSize = useCallback(
    (s: number) => {
      const next = new URLSearchParams(params);
      next.set('size', String(s));
      next.set('page', '1');
      setParams(next);
    },
    [params, setParams],
  );
  const setParam = useCallback(
    (key: string, value: string | null | undefined) => {
      const next = new URLSearchParams(params);
      if (value === null || value === undefined || value === '') next.delete(key);
      else next.set(key, value);
      next.set('page', '1');
      setParams(next);
    },
    [params, setParams],
  );
  return { params, page, size, setPage, setSize, setParam };
}
