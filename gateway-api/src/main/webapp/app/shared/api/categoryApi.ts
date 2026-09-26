import { http } from 'app/config/http';
import { CategoryNodeResponse, CategoryResponse, Kind } from 'app/shared/model/announce';

export const categoryApi = {
  tree: (kind?: Kind) => http.get<CategoryNodeResponse[]>(`/api/categories${kind ? `?kind=${kind}` : ''}`).then(r => r.data),
  get: (id: number) => http.get<CategoryResponse>(`/api/categories/${id}`).then(r => r.data),
};
