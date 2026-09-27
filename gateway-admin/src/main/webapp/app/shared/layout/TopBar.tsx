import { Button, Navbar } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { APP_NAME } from 'app/config/constants';
import { useLogoutMutation } from 'app/shared/api/authApi';
import { useAuth } from 'app/shared/auth/useAuth';
import LanguageSwitch from './LanguageSwitch';

interface Props {
  onToggleSidebar: () => void;
}

export default function TopBar({ onToggleSidebar }: Props) {
  const { t } = useTranslation();
  const { admin, isSuper } = useAuth();
  const [logout, { isLoading }] = useLogoutMutation();
  const navigate = useNavigate();

  const onLogout = async () => {
    try {
      await logout().unwrap();
    } finally {
      navigate('/login', { replace: true });
    }
  };

  return (
    <Navbar bg="dark" variant="dark" className="px-3 shadow-sm" fixed="top">
      <Button variant="outline-light" size="sm" className="me-2 d-lg-none" onClick={onToggleSidebar} aria-label={t('common.menu')}>
        <i className="bi bi-list" aria-hidden />
      </Button>
      <Navbar.Brand as={Link} to="/">
        <i className="bi bi-megaphone me-2" aria-hidden />
        {APP_NAME}
      </Navbar.Brand>
      <div className="ms-auto d-flex align-items-center gap-3">
        <LanguageSwitch />
        {admin && (
          <span className="text-light small d-none d-md-inline">
            <i className="bi bi-person-circle me-1" aria-hidden />
            {admin.username}
            {isSuper && <span className="badge bg-warning text-dark ms-2">{t('common.roles.ROLE_SUPER')}</span>}
          </span>
        )}
        <Button variant="outline-light" size="sm" onClick={onLogout} disabled={isLoading}>
          <i className="bi bi-box-arrow-right me-1" aria-hidden />
          {t('auth.logout')}
        </Button>
      </div>
    </Navbar>
  );
}
