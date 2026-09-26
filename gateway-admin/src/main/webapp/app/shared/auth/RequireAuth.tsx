import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useAuth } from './useAuth';

/** Waits for the start-up refresh, then either renders the children or sends the visitor to /login (remembering where they were). */
export default function RequireAuth() {
  const { isAuthenticated, isRestoring } = useAuth();
  const location = useLocation();
  const { t } = useTranslation();
  if (isRestoring) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100" role="status">
        <Spinner animation="border" />
        <span className="ms-2">{t('common.loading')}</span>
      </div>
    );
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
