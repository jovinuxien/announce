import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import Profile from './Profile';
import { renderPage, server, useMockServer } from 'app/test/testUtils';
import { tokenStore } from 'app/config/tokenStore';
import { storageApi } from 'app/shared/api/storageApi';

useMockServer();

const me = { id: 1, username: 'anna', nickname: 'Anna', mobile: null, avatar: null, gender: 0, birthday: null };
const profileForm = () => screen.getByTestId('profile-form');
const passwordForm = () => screen.getByTestId('password-form');

describe('Profile', () => {
  beforeEach(() => {
    server.use(
      http.put('/auth/me', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        if (body.mobile === '+45 00000001')
          return HttpResponse.json(
            { status: 409, title: 'Conflict', detail: 'mobile already registered' },
            { status: 409, headers: { 'Content-Type': 'application/problem+json' } }
          );
        return HttpResponse.json({ ...me, ...body });
      }),
      http.post('/auth/password', async ({ request }) => {
        const body = (await request.json()) as { currentPassword: string; newPassword: string };
        if (body.currentPassword !== 'secret123')
          return HttpResponse.json(
            { status: 401, title: 'Unauthorized', detail: 'current password does not match' },
            { status: 401, headers: { 'Content-Type': 'application/problem+json' } }
          );
        return HttpResponse.json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 7200, user: { ...me, nickname: 'Anna' } });
      })
    );
  });

  it('shows the signed-in member and saves display name, mobile, gender and birthday through PUT /auth/me, updating the store', async () => {
    const { store } = renderPage(<Profile />, '/me');
    expect(screen.getByLabelText('Username')).toHaveValue('anna');
    expect(screen.getByLabelText('Username')).toHaveAttribute('readonly');
    expect(screen.getByText(/the username is your login/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Anna B.' } });
    fireEvent.change(screen.getByLabelText('Mobile'), { target: { value: '+45 12345678' } });
    fireEvent.change(screen.getByLabelText('Gender'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Birthday'), { target: { value: '1990-05-01' } });
    let sent: unknown = null;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PUT') sent = await request.clone().json();
    });
    fireEvent.click(within(profileForm()).getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Profile saved.')).toBeInTheDocument();
    expect(sent).toEqual({ nickname: 'Anna B.', mobile: '+45 12345678', avatar: '', gender: 2, birthday: '1990-05-01' });
    expect(store.getState().auth.user).toMatchObject({ nickname: 'Anna B.', mobile: '+45 12345678', gender: 2, birthday: '1990-05-01' });
  });

  it('shows the 409 of a mobile another member holds', async () => {
    renderPage(<Profile />, '/me');
    fireEvent.change(screen.getByLabelText('Mobile'), { target: { value: '+45 00000001' } });
    fireEvent.click(within(profileForm()).getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('mobile already registered')).toBeInTheDocument();
    expect(screen.queryByText('Profile saved.')).not.toBeInTheDocument();
  });

  it('uploads the picture through the storage route, previews it, sends its URL and can remove it', async () => {
    // multipart through jsdom's XMLHttpRequest does not reach msw, so the storage client is stubbed here
    const upload = jest.spyOn(storageApi, 'upload').mockResolvedValue({ key: 'k1', url: '/api/storage/fetch/k1', name: 'me.png', type: 'image/png', size: 3 });
    renderPage(<Profile />, '/me');
    const file = new File(['png'], 'me.png', { type: 'image/png' });
    fireEvent.change(screen.getByLabelText('Picture'), { target: { files: [file] } });
    expect(await screen.findByTestId('avatar-preview')).toHaveAttribute('src', '/api/storage/fetch/k1');
    expect(upload).toHaveBeenCalledWith(file);
    let sent: unknown = null;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PUT') sent = await request.clone().json();
    });
    fireEvent.click(within(profileForm()).getByRole('button', { name: 'Save profile' }));
    await screen.findByText('Profile saved.');
    expect(sent).toMatchObject({ avatar: '/api/storage/fetch/k1' });
    fireEvent.click(screen.getByRole('button', { name: 'Remove the picture' }));
    expect(screen.queryByTestId('avatar-preview')).not.toBeInTheDocument();
  });

  it('changes the password: client checks, 401 on a wrong current password, then a fresh session on success', async () => {
    const { store } = renderPage(<Profile />, '/me');
    const change = within(passwordForm()).getByRole('button', { name: 'Change password' });
    fireEvent.change(screen.getByLabelText('Current password'), { target: { value: 'wrong' } });
    fireEvent.change(screen.getByLabelText('Password (at least 8 characters)'), { target: { value: 'brand-new-secret' } });
    fireEvent.change(screen.getByLabelText('Repeat password'), { target: { value: 'brand-new-secre' } });
    expect(screen.getByText('The passwords do not match.')).toBeInTheDocument();
    expect(change).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Repeat password'), { target: { value: 'brand-new-secret' } });
    expect(change).toBeEnabled();
    fireEvent.click(change);
    expect(await screen.findByText('current password does not match')).toBeInTheDocument();
    expect(tokenStore.get()).toBe('t');
    fireEvent.change(screen.getByLabelText('Current password'), { target: { value: 'secret123' } });
    fireEvent.click(change);
    expect(await screen.findByText('Password changed. Your other devices were signed out.')).toBeInTheDocument();
    await waitFor(() => expect(tokenStore.get()).toBe('new-token'));
    expect(screen.getByLabelText('Current password')).toHaveValue('');
    expect(store.getState().auth.user?.nickname).toBe('Anna');
  });
});
