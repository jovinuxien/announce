import { http } from 'app/config/http';
import { BrandResponse, PageResponse } from 'app/shared/model/announce';

export const brandApi = {
  list: (page = 1, size = 100) => http.get<PageResponse<BrandResponse>>(`/api/brands?page=${page}&size=${size}`).then(r => r.data),
};
