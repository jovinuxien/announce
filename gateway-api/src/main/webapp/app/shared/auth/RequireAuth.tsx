import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from 'react-bootstrap';
import { useAppSelector } from 'app/config/store';

/** Guards member-only routes: waits for the session bootstrap, then redirects anonymous visitors to /login. */
export default function RequireAuth() {
  const status = useAppSelector(s => s.auth.status);
  const location = useLocation();
  if (status === 'unknown') return <div className="text-center py-5"><Spinner animation="border" /></div>;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}
