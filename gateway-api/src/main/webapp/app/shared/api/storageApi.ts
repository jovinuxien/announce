import { http } from 'app/config/http';

export interface UploadResponse { key: string; url: string; name: string; type: string; size: number }

/** Picture upload at the edge (member): the answer's `url` goes into the announce gallery. */
export const storageApi = {
  upload: (file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData();
    form.append('file', file, file.name);
    return http
      .post<UploadResponse>('/api/storage/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60000,
        onUploadProgress: e => onProgress?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0),
      })
      .then(r => r.data);
  },
};
