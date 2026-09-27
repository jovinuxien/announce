import { useState, type FormEvent } from 'react';
import { Button, Card, Form, Spinner } from 'react-bootstrap';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { APP_NAME } from 'app/config/constants';
import { useLoginMutation } from 'app/shared/api/authApi';
import { useAuth } from 'app/shared/auth/useAuth';
import LanguageSwitch from 'app/shared/layout/LanguageSwitch';
import ProblemAlert from 'app/shared/ui/ProblemAlert';

interface FromState {
  from?: { pathname: string; search?: string };
}

export default function LoginPage() {
  const { t } = useTranslation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [login, { isLoading, error, reset }] = useLoginMutation();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as FromState | null)?.from;
  const target = from ? `${from.pathname}${from.search ?? ''}` : '/';

  if (isAuthenticated) return <Navigate to={target} replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await login({ username: username.trim(), password }).unwrap();
      navigate(target, { replace: true });
    } catch {
      /* ProblemAlert shows it */
    }
  };

  return (
    <div className="d-flex align-items-center justify-content-center min-vh-100 bg-dark bg-gradient">
      <Card style={{ width: 380 }} className="shadow">
        <Card.Body className="p-4">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <h1 className="h4 mb-0">
              <i className="bi bi-megaphone me-2" aria-hidden />
              {APP_NAME}
            </h1>
            <div className="bg-dark rounded px-1">
              <LanguageSwitch />
            </div>
          </div>
          <p className="text-muted small">{t('auth.intro')}</p>
          {error ? <ProblemAlert error={error} onClose={reset} /> : null}
          <Form onSubmit={submit} noValidate>
            <Form.Group className="mb-3" controlId="login-username">
              <Form.Label>{t('auth.username')}</Form.Label>
              <Form.Control autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
            </Form.Group>
            <Form.Group className="mb-4" controlId="login-password">
              <Form.Label>{t('auth.password')}</Form.Label>
              <Form.Control type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </Form.Group>
            <Button type="submit" className="w-100" disabled={isLoading || !username || !password}>
              {isLoading && <Spinner size="sm" animation="border" className="me-2" />}
              {t('auth.signIn')}
            </Button>
          </Form>
        </Card.Body>
      </Card>
    </div>
  );
}
