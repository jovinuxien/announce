import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { makeStore } from 'app/config/store';
import { ROLE_SUPER } from 'app/config/constants';
import { signedIn } from './authSlice';
import RequireRole from './RequireRole';

function renderWithRoles(roles: string[]) {
  const store = makeStore();
  store.dispatch(signedIn({ accessToken: 't', tokenType: 'Bearer', expiresIn: 100, admin: { id: 1, username: 'u', avatar: null, roleIds: [], roles } }));
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/admins']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route
            path="/admins"
            element={
              <RequireRole role={ROLE_SUPER}>
                <div data-testid="admins-page">Admins</div>
              </RequireRole>
            }
          />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

describe('RequireRole', () => {
  it('hides a super-only route from a plain administrator', () => {
    renderWithRoles(['ROLE_ADMIN']);
    expect(screen.queryByTestId('admins-page')).not.toBeInTheDocument();
    expect(screen.getByTestId('forbidden')).toHaveTextContent('Not allowed');
  });

  it('renders the route for a super administrator', () => {
    renderWithRoles(['ROLE_ADMIN', 'ROLE_SUPER']);
    expect(screen.getByTestId('admins-page')).toBeInTheDocument();
    expect(screen.queryByTestId('forbidden')).not.toBeInTheDocument();
  });

  it('renders nothing in silent mode', () => {
    const store = makeStore();
    store.dispatch(signedIn({ accessToken: 't', tokenType: 'Bearer', expiresIn: 100, admin: { id: 1, username: 'u', avatar: null, roleIds: [], roles: ['ROLE_ADMIN'] } }));
    const { container } = render(
      <Provider store={store}>
        <RequireRole role={ROLE_SUPER} silent>
          <span>secret</span>
        </RequireRole>
      </Provider>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
