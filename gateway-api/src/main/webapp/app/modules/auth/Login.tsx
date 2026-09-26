import { FormEvent, useEffect, useState } from 'react';
import { Button, Card, Form } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { clearError, login } from 'app/shared/auth/authSlice';
import ProblemAlert from 'app/shared/components/ProblemAlert';

export default function Login() {
  const { t } = useTranslation('auth');
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { status, loading, error } = useAppSelector(s => s.auth);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  useEffect(() => {
    if (status === 'authenticated') navigate(from, { replace: true });
  }, [status, from, navigate]);
  useEffect(() => () => void dispatch(clearError()), [dispatch]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    void dispatch(login({ username, password }));
  };

  return (
    <Card className="mx-auto" style={{ maxWidth: 420 }}>
      <Card.Body>
        <h1 className="h4 mb-3">{t('login.title')}</h1>
        <ProblemAlert message={error} onClose={() => dispatch(clearError())} />
        <Form onSubmit={submit}>
          <Form.Group className="mb-3" controlId="login-username">
            <Form.Label>{t('login.username')}</Form.Label>
            <Form.Control value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" required autoFocus />
          </Form.Group>
          <Form.Group className="mb-3" controlId="login-password">
            <Form.Label>{t('login.password')}</Form.Label>
            <Form.Control type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required />
          </Form.Group>
          <Button type="submit" disabled={loading} className="w-100">
            {t('login.submit')}
          </Button>
        </Form>
        <p className="mt-3 mb-0 small">
          {t('login.noAccount')} <Link to="/register">{t('login.register')}</Link>
        </p>
      </Card.Body>
    </Card>
  );
}
