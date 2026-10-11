import { Container, Nav, Navbar, NavDropdown } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { features } from './config/features';
import { useAppDispatch, useAppSelector } from './config/store';
import { logout } from './shared/auth/authSlice';
import ErrorBoundary from './shared/components/ErrorBoundary';
import LanguageSwitcher from './shared/components/LanguageSwitcher';
import SuggestBox from './shared/components/SuggestBox';
import { KINDS } from './shared/model/announce';

export default function Layout() {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { status, user } = useAppSelector(s => s.auth);
  const q = new URLSearchParams(location.search).get('q') ?? '';

  return (
    <div className="d-flex flex-column min-vh-100">
      <header className="an-header">
        <Navbar expand="lg" className="py-2">
          <Container fluid="xl" className="gap-3">
            <Navbar.Brand as={Link} to="/" className="fw-bold text-primary">
              Announce
            </Navbar.Brand>
            <SuggestBox initial={q} />
            <Navbar.Toggle aria-controls="an-nav" />
            <Navbar.Collapse id="an-nav" className="flex-grow-0">
              <Nav className="align-items-lg-center gap-2">
                <Nav.Link as={NavLink} to="/post">
                  <i className="bi bi-plus-circle me-1" />
                  {t('header.post')}
                </Nav.Link>
                {status === 'authenticated' ? (
                  <NavDropdown
                    title={
                      <>
                        {user?.avatar && <img src={user.avatar} alt="" className="rounded-circle me-1" style={{ width: 22, height: 22, objectFit: 'cover' }} />}
                        {user?.nickname || user?.username || t('header.account')}
                      </>
                    }
                    align="end"
                  >
                    <NavDropdown.Item as={Link} to="/my/announces">
                      {t('header.myAnnounces')}
                    </NavDropdown.Item>
                    {features.trade && (
                      <NavDropdown.Item as={Link} to="/orders">
                        {t('header.orders')}
                      </NavDropdown.Item>
                    )}
                    {features.chat && (
                      <NavDropdown.Item as={Link} to="/messages">
                        {t('header.messages')}
                      </NavDropdown.Item>
                    )}
                    <NavDropdown.Item as={Link} to="/me">
                      {t('header.profile')}
                    </NavDropdown.Item>
                    <NavDropdown.Divider />
                    <NavDropdown.Item onClick={() => dispatch(logout()).then(() => navigate('/'))}>{t('header.logout')}</NavDropdown.Item>
                  </NavDropdown>
                ) : (
                  <>
                    <Nav.Link as={NavLink} to="/login">
                      {t('header.login')}
                    </Nav.Link>
                    <Nav.Link as={NavLink} to="/register">
                      {t('header.register')}
                    </Nav.Link>
                  </>
                )}
                <LanguageSwitcher className="w-auto" />
              </Nav>
            </Navbar.Collapse>
          </Container>
        </Navbar>
        <Container fluid="xl">
          <Nav className="pb-2 small">
            {KINDS.map(k => (
              <Nav.Link key={k} as={NavLink} to={`/search?kind=${k}`} className="pt-0 ps-0 pe-3">
                {t(`kind.${k}`)}
              </Nav.Link>
            ))}
          </Nav>
        </Container>
      </header>
      <main className="flex-grow-1 py-4">
        <Container fluid="xl">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </Container>
      </main>
      <footer className="an-footer py-3 bg-white">
        <Container fluid="xl" className="d-flex flex-wrap justify-content-between gap-2">
          <span>{t('footer.about')}</span>
          <span>{t('footer.version', { version: process.env.APP_VERSION })}</span>
        </Container>
      </footer>
    </div>
  );
}
