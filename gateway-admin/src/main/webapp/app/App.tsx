import { useEffect, useRef } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { useRefreshMutation } from 'app/shared/api/authApi';
import AppRoutes from './routes';

/** Restores the session once at start-up (POST /auth/refresh with the httpOnly cookie), then renders the routes. */
export default function App() {
  const [refresh] = useRefreshMutation();
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    refresh();
  }, [refresh]);
  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AppRoutes />
    </BrowserRouter>
  );
}
