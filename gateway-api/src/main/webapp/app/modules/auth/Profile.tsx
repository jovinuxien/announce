import { ChangeEvent, FormEvent, useState } from 'react';
import { Alert, Button, Card, Col, Form, Row } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { features } from 'app/config/features';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { tokenStore } from 'app/config/tokenStore';
import PayoutsCard from 'app/modules/trade/PayoutsCard';
import { authApi } from 'app/shared/api/authApi';
import { storageApi } from 'app/shared/api/storageApi';
import { userUpdated } from 'app/shared/auth/authSlice';
import ProblemAlert from 'app/shared/components/ProblemAlert';
import { Gender } from 'app/shared/model/auth';
import { describeError } from 'app/shared/util/problem';

const GENDERS: Gender[] = [0, 1, 2];

/**
 * The member's own profile (doc/frontend-edges.md §17): display name, mobile, gender, birthday and avatar through
 * `PUT /auth/me` (the username is the login and stays), the picture through the edge's storage route like the
 * announce gallery, and the password through `POST /auth/password`, which answers a fresh session and signs the
 * other devices out. Both answers update the signed-in user in the store, so the header follows.
 */
export default function Profile() {
  const { t } = useTranslation('auth');
  const dispatch = useAppDispatch();
  const user = useAppSelector(s => s.auth.user);
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [mobile, setMobile] = useState(user?.mobile ?? '');
  const [gender, setGender] = useState<Gender>((user?.gender ?? 0) as Gender);
  const [birthday, setBirthday] = useState(user?.birthday ?? '');
  const [avatar, setAvatar] = useState(user?.avatar ?? '');
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [changing, setChanging] = useState(false);
  const [changed, setChanged] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  if (!user) return null;

  const pick = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const up = await storageApi.upload(file);
      setAvatar(up.url);
    } catch (err) {
      setError(describeError(err, t('profile.uploadFailed')));
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const u = await authApi.updateProfile({ nickname: nickname.trim(), mobile: mobile.trim(), avatar, gender, birthday: birthday || null });
      dispatch(userUpdated(u));
      setSaved(true);
    } catch (err) {
      setError(describeError(err, t('profile.saveFailed')));
    } finally {
      setSaving(false);
    }
  };

  const mismatch = confirm.length > 0 && next !== confirm;
  const tooShort = next.length > 0 && next.length < 8;
  const changePassword = async (e: FormEvent) => {
    e.preventDefault();
    if (mismatch || tooShort) return;
    setChanging(true);
    setPasswordError(null);
    setChanged(false);
    try {
      const session = await authApi.changePassword({ currentPassword: current, newPassword: next });
      tokenStore.set(session.accessToken);
      dispatch(userUpdated(session.user));
      setCurrent('');
      setNext('');
      setConfirm('');
      setChanged(true);
    } catch (err) {
      setPasswordError(describeError(err, t('profile.changeFailed')));
    } finally {
      setChanging(false);
    }
  };

  return (
    <div className="mx-auto" style={{ maxWidth: 560 }}>
      <Card className="mb-3">
        <Card.Body>
          <h1 className="h4 mb-3">{t('profile.title')}</h1>
          <Form onSubmit={save} data-testid="profile-form">
            <Row className="g-3 align-items-start">
              <Col xs="auto">
                <div className="rounded-circle bg-light border d-flex align-items-center justify-content-center overflow-hidden" style={{ width: 72, height: 72 }}>
                  {avatar ? (
                    <img src={avatar} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} data-testid="avatar-preview" />
                  ) : (
                    <i className="bi bi-person fs-2 text-muted" />
                  )}
                </div>
              </Col>
              <Col>
                <Form.Group controlId="profile-avatar">
                  <Form.Label className="small mb-1">{t('profile.avatar')}</Form.Label>
                  <Form.Control type="file" accept="image/*" size="sm" onChange={pick} disabled={uploading} />
                </Form.Group>
                {avatar && (
                  <Button variant="link" size="sm" className="p-0 mt-1" onClick={() => setAvatar('')}>
                    {t('profile.removeAvatar')}
                  </Button>
                )}
              </Col>
            </Row>
            <Form.Group className="mt-3" controlId="profile-username">
              <Form.Label className="small mb-1">{t('register.username')}</Form.Label>
              <Form.Control value={user.username} readOnly plaintext />
              <Form.Text>{t('profile.usernameFixed', { id: user.id })}</Form.Text>
            </Form.Group>
            <Form.Group className="mt-2" controlId="profile-nickname">
              <Form.Label className="small mb-1">{t('register.nickname')}</Form.Label>
              <Form.Control value={nickname} onChange={e => setNickname(e.target.value)} maxLength={63} />
            </Form.Group>
            <Form.Group className="mt-2" controlId="profile-mobile">
              <Form.Label className="small mb-1">{t('register.mobile')}</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} maxLength={20} pattern="[0-9+ ]*" />
            </Form.Group>
            <Row className="g-2 mt-1">
              <Col sm={6}>
                <Form.Group controlId="profile-gender">
                  <Form.Label className="small mb-1">{t('profile.gender')}</Form.Label>
                  <Form.Select value={gender} onChange={e => setGender(Number(e.target.value) as Gender)}>
                    {GENDERS.map(g => (
                      <option key={g} value={g}>
                        {t(`profile.genders.${g}`)}
                      </option>
                    ))}
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col sm={6}>
                <Form.Group controlId="profile-birthday">
                  <Form.Label className="small mb-1">{t('profile.birthday')}</Form.Label>
                  <Form.Control type="date" value={birthday} max={new Date().toISOString().slice(0, 10)} onChange={e => setBirthday(e.target.value)} />
                </Form.Group>
              </Col>
            </Row>
            <ProblemAlert message={error} onClose={() => setError(null)} />
            {saved && (
              <Alert variant="success" className="py-1 my-2 small">
                {t('profile.saved')}
              </Alert>
            )}
            <Button type="submit" className="mt-3" disabled={saving || uploading}>
              {t('profile.save')}
            </Button>
          </Form>
        </Card.Body>
      </Card>
      <Card className="mb-3">
        <Card.Body>
          <h2 className="h6">{t('profile.passwordTitle')}</h2>
          <Form onSubmit={changePassword} data-testid="password-form">
            <Form.Group className="mt-2" controlId="password-current">
              <Form.Label className="small mb-1">{t('profile.current')}</Form.Label>
              <Form.Control type="password" value={current} onChange={e => setCurrent(e.target.value)} required autoComplete="current-password" />
            </Form.Group>
            <Form.Group className="mt-2" controlId="password-new">
              <Form.Label className="small mb-1">{t('register.password')}</Form.Label>
              <Form.Control
                type="password"
                value={next}
                onChange={e => setNext(e.target.value)}
                required
                minLength={8}
                maxLength={63}
                autoComplete="new-password"
                isInvalid={tooShort}
              />
              <Form.Control.Feedback type="invalid">{t('register.passwordTooShort')}</Form.Control.Feedback>
            </Form.Group>
            <Form.Group className="mt-2" controlId="password-confirm">
              <Form.Label className="small mb-1">{t('register.confirm')}</Form.Label>
              <Form.Control type="password" value={confirm} onChange={e => setConfirm(e.target.value)} required autoComplete="new-password" isInvalid={mismatch} />
              <Form.Control.Feedback type="invalid">{t('register.passwordMismatch')}</Form.Control.Feedback>
            </Form.Group>
            <ProblemAlert message={passwordError} onClose={() => setPasswordError(null)} />
            {changed && (
              <Alert variant="success" className="py-1 my-2 small">
                {t('profile.changed')}
              </Alert>
            )}
            <Button type="submit" variant="outline-primary" className="mt-3" disabled={changing || mismatch || tooShort || !current || !next || !confirm}>
              {t('profile.change')}
            </Button>
          </Form>
        </Card.Body>
      </Card>
      {features.trade && <PayoutsCard />}
    </div>
  );
}
