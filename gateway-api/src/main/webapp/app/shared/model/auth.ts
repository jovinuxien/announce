// Mirrors gateway-api's AuthDtos.
export interface LoginRequest { username: string; password: string }
export interface RegisterRequest { username: string; password: string; nickname?: string; mobile?: string }
export interface UserResponse { id: number; username: string; nickname?: string | null; mobile?: string | null; avatar?: string | null }
export interface TokenResponse { accessToken: string; tokenType: string; expiresIn: number; user: UserResponse }
