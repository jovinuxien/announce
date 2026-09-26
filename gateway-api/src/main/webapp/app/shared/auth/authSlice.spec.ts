import reducer, { bootstrapSession, login, logout, sessionExpired } from './authSlice';
import { tokenStore } from 'app/config/tokenStore';

const payload = { accessToken: 'tok', tokenType: 'Bearer', expiresIn: 7200, user: { id: 1, username: 'alice', nickname: 'Alice' } };

describe('auth slice', () => {
  it('starts unknown, becomes authenticated on login and keeps the token in memory only', () => {
    let state = reducer(undefined, { type: 'init' });
    expect(state.status).toBe('unknown');
    state = reducer(state, login.pending('r', { username: 'alice', password: 'x' }));
    expect(state.loading).toBe(true);
    state = reducer(state, login.fulfilled(payload, 'r', { username: 'alice', password: 'x' }));
    expect(state.status).toBe('authenticated');
    expect(state.user?.username).toBe('alice');
    expect(tokenStore.get()).toBe('tok');
    expect(window.localStorage.length).toBe(0);
  });

  it('records the rejection reason', () => {
    const state = reducer(undefined, login.rejected(null, 'r', { username: 'a', password: 'b' }, 'bad credentials'));
    expect(state.error).toBe('bad credentials');
    expect(state.status).toBe('unknown');
  });

  it('bootstrap without a cookie is anonymous; expiry and logout clear the session', () => {
    let state = reducer(undefined, bootstrapSession.fulfilled(null, 'r'));
    expect(state.status).toBe('anonymous');
    state = reducer(state, bootstrapSession.fulfilled(payload, 'r'));
    expect(state.status).toBe('authenticated');
    state = reducer(state, sessionExpired());
    expect(state.status).toBe('anonymous');
    expect(tokenStore.get()).toBeNull();
    state = reducer(reducer(state, login.fulfilled(payload, 'r', { username: 'a', password: 'b' })), logout.fulfilled(undefined, 'r'));
    expect(state.status).toBe('anonymous');
    expect(state.user).toBeNull();
  });
});
