import { useEffect, useState } from 'react';
import { categoryApi } from 'app/shared/api/categoryApi';
import { CategoryNodeResponse, Kind } from 'app/shared/model/announce';

const cache = new Map<string, Promise<CategoryNodeResponse[]>>();

/** The category tree of a kind (or of every kind), fetched once per session. */
export function loadCategories(kind: Kind | null): Promise<CategoryNodeResponse[]> {
  const key = kind ?? 'ALL';
  let p = cache.get(key);
  if (!p) {
    p = categoryApi.tree(kind ?? undefined).catch(e => {
      cache.delete(key);
      throw e;
    });
    cache.set(key, p);
  }
  return p;
}

export function useCategories(kind: Kind | null): { tree: CategoryNodeResponse[]; error: string | null } {
  const [tree, setTree] = useState<CategoryNodeResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    loadCategories(kind)
      .then(t => alive && setTree(t))
      .catch(e => alive && setError(e?.message ?? 'categories unavailable'));
    return () => {
      alive = false;
    };
  }, [kind]);
  return { tree, error };
}

/** id -> name for the category facet labels. */
export function useCategoryNames(kind: Kind | null): Record<string, string> {
  const { tree } = useCategories(kind);
  const names: Record<string, string> = {};
  tree.forEach(n => {
    names[String(n.category.id)] = n.category.name;
    n.children.forEach(c => (names[String(c.id)] = c.name));
  });
  return names;
}
