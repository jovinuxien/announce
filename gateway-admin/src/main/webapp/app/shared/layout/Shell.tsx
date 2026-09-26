import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Container } from 'react-bootstrap';
import TopBar from './TopBar';
import Sidebar from './Sidebar';

/** Fixed top bar, collapsible sidebar on small screens, routed content. */
export default function Shell() {
  const [open, setOpen] = useState(false);
  return (
    <div className="admin-shell">
      <TopBar onToggleSidebar={() => setOpen((o) => !o)} />
      <Sidebar open={open} onNavigate={() => setOpen(false)} />
      {open && <div className="admin-backdrop d-lg-none" onClick={() => setOpen(false)} aria-hidden />}
      <main className="admin-main">
        <Container fluid className="py-3">
          <Outlet />
        </Container>
      </main>
    </div>
  );
}
