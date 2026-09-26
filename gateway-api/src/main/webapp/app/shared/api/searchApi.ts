import { http } from 'app/config/http';
import { OcsSearchResult, OcsSuggestion } from 'app/shared/model/search';

/** GET /api/search is rewritten by the edge to the OCS searcher's tenant API; the query string passes through. */
export const searchApi = {
  search: (params: URLSearchParams) => http.get<OcsSearchResult>(`/api/search?${params.toString()}`).then(r => r.data),
  suggest: (userQuery: string, limit = 8) =>
    http
      .get<OcsSuggestion[]>(`/api/suggest?userQuery=${encodeURIComponent(userQuery)}&limit=${limit}`)
      .then(r => (Array.isArray(r.data) ? r.data : [])),
};
