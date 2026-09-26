import { authReducer, initialAuthState, loggedOut, restoreFailed, selectIsSuper, signedIn, type TokenResponse } from './authSlice';

const token: TokenResponse = {
  accessToken: 'jwt-1',
  tokenType: 'Bearer',
  expiresIn: 7200,
  admin: { id: 1, username: 'root', avatar: null, roleIds: [1], roles: ['ROLE_ADMIN', 'ROLE_SUPER'] },
};

describe('authSlice', () => {
  it('starts restoring with no token', () => {
    expect(initialAuthState.status).toBe('restoring');
    expect(initialAuthState.accessToken).toBeNull();
  });

  it('signedIn stores the token in memory and computes the expiry', () => {
    const before = Date.now();
    const state = authReducer(initialAuthState, signedIn(token));
    expect(state.status).toBe('authenticated');
    expect(state.accessToken).toBe('jwt-1');
    expect(state.admin?.username).toBe('root');
    expect(state.expiresAt).toBeGreaterThanOrEqual(before + 7200 * 1000);
    expect(selectIsSuper({ auth: state })).toBe(true);
  });

  it('restoreFailed leaves an anonymous session', () => {
    const state = authReducer(initialAuthState, restoreFailed());
    expect(state.status).toBe('anonymous');
    expect(state.accessToken).toBeNull();
  });

  it('loggedOut clears everything', () => {
    const signed = authReducer(initialAuthState, signedIn(token));
    const state = authReducer(signed, loggedOut());
    expect(state).toEqual({ accessToken: null, admin: null, expiresAt: null, status: 'anonymous' });
    expect(selectIsSuper({ auth: state })).toBe(false);
  });

  it('a plain admin is not super', () => {
    const state = authReducer(initialAuthState, signedIn({ ...token, admin: { ...token.admin, roles: ['ROLE_ADMIN'] } }));
    expect(selectIsSuper({ auth: state })).toBe(false);
  });
});
