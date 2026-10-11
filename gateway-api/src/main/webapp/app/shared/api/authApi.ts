import axios from 'axios';
import { http, toApiError } from 'app/config/http';
import { LoginRequest, PasswordChangeRequest, ProfileUpdateRequest, RegisterRequest, TokenResponse, UserResponse } from 'app/shared/model/auth';

export const authApi = {
  login: (body: LoginRequest) => http.post<TokenResponse>('/auth/login', body).then(r => r.data),
  register: (body: RegisterRequest) => http.post<TokenResponse>('/auth/register', body).then(r => r.data),
  /** Uses the httpOnly cookie; a plain axios call so the 401 interceptor does not loop. */
  refresh: () =>
    axios
      .post<TokenResponse>('/auth/refresh', null, { withCredentials: true })
      .then(r => r.data)
      .catch(e => Promise.reject(toApiError(e))),
  logout: () => http.post<void>('/auth/logout').then(() => undefined),
  me: () => http.get<UserResponse>('/auth/me').then(r => r.data),
  updateProfile: (body: ProfileUpdateRequest) => http.put<UserResponse>('/auth/me', body).then(r => r.data),
  changePassword: (body: PasswordChangeRequest) => http.post<TokenResponse>('/auth/password', body).then(r => r.data),
};
