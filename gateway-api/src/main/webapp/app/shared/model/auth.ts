// Mirrors gateway-api's AuthDtos.
export interface LoginRequest {
  username: string;
  password: string;
}
export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
  mobile?: string;
}
/** `announce_user.gender`: 0 unspecified, 1 male, 2 female. */
export type Gender = 0 | 1 | 2;
export interface UserResponse {
  id: number;
  username: string;
  nickname?: string | null;
  mobile?: string | null;
  avatar?: string | null;
  gender?: Gender | null;
  birthday?: string | null;
}
/** `PUT /auth/me`; the username stays the login. */
export interface ProfileUpdateRequest {
  nickname?: string | null;
  mobile?: string | null;
  avatar?: string | null;
  gender?: Gender | null;
  birthday?: string | null;
}
/** `POST /auth/password`; answers a TokenResponse (fresh session, other devices signed out). */
export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}
export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}
