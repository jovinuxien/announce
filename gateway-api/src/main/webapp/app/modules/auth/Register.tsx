import { FormEvent, useEffect, useState } from 'react';
import { Button, Card, Form } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { clearError, register } from 'app/shared/auth/authSlice';
import ProblemAlert from 'app/shared/components/ProblemAlert';

export default function Register() {
  const { t } = useTranslation('auth');
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { status, loading, error } = useAppSelector(s => s.auth);
  const [form, setForm] = useState({ username: '', password: '', confirm: '', nickname: '', mobile: '' });
  const [local, setLocal] = useState<string | null>(null);

  useEffect(() => {
    if (status === 'authenticated') navigate('/', { replace: true });
  }, [status, navigate]);
  useEffect(() => () => void dispatch(clearError()), [dispatch]);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (form.password !== form.confirm) {
      setLocal(t('register.passwordMismatch'));
      return;
    }
    if (form.password.length < 8) {
      setLocal(t('register.passwordTooShort'));
      return;
    }
    setLocal(null);
    void dispatch(register({ username: form.username, password: form.password, nickname: form.nickname || undefined, mobile: form.mobile || undefined }));
  };

  return (
    <Card className="mx-auto" style={{ maxWidth: 480 }}>
      <Card.Body>
        <h1 className="h4 mb-3">{t('register.title')}</h1>
        <ProblemAlert message={local ?? error} onClose={() => { setLocal(null); dispatch(clearError()); }} />
        <Form onSubmit={submit}>
          <Form.Group className="mb-3" controlId="reg-username">
            <Form.Label>{t('register.username')}</Form.Label>
            <Form.Control value={form.username} onChange={set('username')} minLength={3} maxLength={63} pattern="[A-Za-z0-9._@\-]+" required autoFocus />
            <Form.Text>{t('register.usernameHint')}</Form.Text>
          </Form.Group>
          <Form.Group className="mb-3" controlId="reg-nickname">
            <Form.Label>{t('register.nickname')}</Form.Label>
            <Form.Control value={form.nickname} onChange={set('nickname')} maxLength={63} />
          </Form.Group>
          <Form.Group className="mb-3" controlId="reg-mobile">
            <Form.Label>{t('register.mobile')}</Form.Label>
            <Form.Control value={form.mobile} onChange={set('mobile')} maxLength={20} inputMode="tel" />
          </Form.Group>
          <Form.Group className="mb-3" controlId="reg-password">
            <Form.Label>{t('register.password')}</Form.Label>
            <Form.Control type="password" value={form.password} onChange={set('password')} minLength={8} maxLength={63} autoComplete="new-password" required />
          </Form.Group>
          <Form.Group className="mb-3" controlId="reg-confirm">
            <Form.Label>{t('register.confirm')}</Form.Label>
            <Form.Control type="password" value={form.confirm} onChange={set('confirm')} autoComplete="new-password" required />
          </Form.Group>
          <Button type="submit" disabled={loading} className="w-100">
            {t('register.submit')}
          </Button>
        </Form>
        <p className="mt-3 mb-0 small">
          {t('register.haveAccount')} <Link to="/login">{t('register.login')}</Link>
        </p>
      </Card.Body>
    </Card>
  );
}
