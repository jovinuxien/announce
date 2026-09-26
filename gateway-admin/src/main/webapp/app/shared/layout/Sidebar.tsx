import { Nav } from 'react-bootstrap';
import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ROLE_SUPER } from 'app/config/constants';
import { features } from 'app/config/features';
import { useAuth } from 'app/shared/auth/useAuth';

interface Entry {
  to: string;
  icon: string;
  label: string;
  role?: string;
  feature?: 'trade' | 'chat';
}

const sections: { title: string; entries: Entry[] }[] = [
  {
    title: 'common.nav.sections.overview',
    entries: [{ to: '/', icon: 'bi-speedometer2', label: 'common.nav.dashboard' }],
  },
  {
    title: 'common.nav.sections.catalog',
    entries: [
      { to: '/announces', icon: 'bi-megaphone', label: 'common.nav.announces' },
      { to: '/categories', icon: 'bi-diagram-3', label: 'common.nav.categories' },
      { to: '/brands', icon: 'bi-award', label: 'common.nav.brands' },
      { to: '/listings', icon: 'bi-globe', label: 'common.nav.listings' },
      { to: '/regions', icon: 'bi-geo-alt', label: 'common.nav.regions' },
    ],
  },
  {
    title: 'common.nav.sections.trade',
    entries: [
      { to: '/auctions', icon: 'bi-hammer', label: 'common.nav.auctions', feature: 'trade' },
      { to: '/orders', icon: 'bi-receipt', label: 'common.nav.orders', feature: 'trade' },
      { to: '/payouts', icon: 'bi-cash-stack', label: 'common.nav.payouts', feature: 'trade' },
    ],
  },
  {
    title: 'common.nav.sections.support',
    entries: [
      { to: '/support', icon: 'bi-chat-dots', label: 'common.nav.support', feature: 'chat' },
      { to: '/bulletins', icon: 'bi-broadcast', label: 'common.nav.bulletins', feature: 'chat' },
    ],
  },
  {
    title: 'common.nav.sections.people',
    entries: [
      { to: '/users', icon: 'bi-people', label: 'common.nav.users' },
      { to: '/admins', icon: 'bi-person-badge', label: 'common.nav.admins', role: ROLE_SUPER },
      { to: '/roles', icon: 'bi-shield-lock', label: 'common.nav.roles', role: ROLE_SUPER },
    ],
  },
];

interface Props {
  open: boolean;
  onNavigate: () => void;
}

export default function Sidebar({ open, onNavigate }: Props) {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const visible = sections
    .map((s) => ({
      ...s,
      entries: s.entries.filter((e) => (!e.role || hasRole(e.role)) && (!e.feature || features[e.feature])),
    }))
    .filter((s) => s.entries.length > 0);
  return (
    <aside className={`admin-sidebar bg-body-tertiary border-end ${open ? 'open' : ''}`} aria-label={t('common.menu')}>
      <Nav className="flex-column py-2">
        {visible.map((s) => (
          <div key={s.title} className="mb-2">
            <div className="px-3 pt-2 pb-1 text-uppercase text-muted small fw-semibold">{t(s.title)}</div>
            {s.entries.map((e) => (
              <Nav.Link key={e.to} as={NavLink} to={e.to} end={e.to === '/'} className="admin-nav-link px-3 py-2" onClick={onNavigate}>
                <i className={`bi ${e.icon} me-2`} aria-hidden />
                {t(e.label)}
              </Nav.Link>
            ))}
          </div>
        ))}
      </Nav>
    </aside>
  );
}
