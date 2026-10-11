import { Navigate, Route, Routes } from 'react-router-dom';
import { ROLE_SUPER } from 'app/config/constants';
import { features } from 'app/config/features';
import RequireAuth from 'app/shared/auth/RequireAuth';
import RequireRole from 'app/shared/auth/RequireRole';
import Shell from 'app/shared/layout/Shell';
import LoginPage from 'app/modules/auth/LoginPage';
import DashboardPage from 'app/modules/dashboard/DashboardPage';
import CategoriesPage from 'app/modules/categories/CategoriesPage';
import BrandsPage from 'app/modules/brands/BrandsPage';
import AnnouncesPage from 'app/modules/announces/AnnouncesPage';
import AnnounceDetailPage from 'app/modules/announces/AnnounceDetailPage';
import ListingsPage from 'app/modules/listings/ListingsPage';
import UsersPage from 'app/modules/users/UsersPage';
import UserDetailPage from 'app/modules/users/UserDetailPage';
import AdminsPage from 'app/modules/admins/AdminsPage';
import RolesPage from 'app/modules/roles/RolesPage';
import RegionsPage from 'app/modules/regions/RegionsPage';
import AuctionsPage from 'app/modules/trade/AuctionsPage';
import OrdersPage from 'app/modules/trade/OrdersPage';
import OrderDetailPage from 'app/modules/trade/OrderDetailPage';
import PayoutsPage from 'app/modules/trade/PayoutsPage';
import SupportQueuePage from 'app/modules/chat/SupportQueuePage';
import SupportThreadPage from 'app/modules/chat/SupportThreadPage';
import BulletinsPage from 'app/modules/chat/BulletinsPage';
import NotFoundPage from 'app/modules/NotFoundPage';

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Shell />}>
          <Route index element={<DashboardPage />} />
          <Route path="/announces" element={<AnnouncesPage />} />
          <Route path="/announces/:id" element={<AnnounceDetailPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/brands" element={<BrandsPage />} />
          <Route path="/listings" element={<ListingsPage />} />
          <Route path="/regions" element={<RegionsPage />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/users/:id" element={<UserDetailPage />} />
          <Route
            path="/admins"
            element={
              <RequireRole role={ROLE_SUPER}>
                <AdminsPage />
              </RequireRole>
            }
          />
          <Route
            path="/roles"
            element={
              <RequireRole role={ROLE_SUPER}>
                <RolesPage />
              </RequireRole>
            }
          />
          {features.trade && (
            <>
              <Route path="/auctions" element={<AuctionsPage />} />
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="/orders/:id" element={<OrderDetailPage />} />
              <Route path="/payouts" element={<PayoutsPage />} />
            </>
          )}
          {features.chat && (
            <>
              <Route path="/support" element={<SupportQueuePage />} />
              <Route path="/support/:id" element={<SupportThreadPage />} />
              <Route path="/bulletins" element={<BulletinsPage />} />
            </>
          )}
          <Route path="/index.html" element={<Navigate to="/" replace />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
