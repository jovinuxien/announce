import { lazy, Suspense } from 'react';
import { Spinner } from 'react-bootstrap';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Layout from './Layout';
import { features } from './config/features';
import RequireAuth from './shared/auth/RequireAuth';

const Home = lazy(() => import('./modules/home/Home'));
const Search = lazy(() => import('./modules/search/Search'));
const AnnounceDetail = lazy(() => import('./modules/announce/AnnounceDetail'));
const Login = lazy(() => import('./modules/auth/Login'));
const Register = lazy(() => import('./modules/auth/Register'));
const Profile = lazy(() => import('./modules/auth/Profile'));
const PostWizard = lazy(() => import('./modules/post/PostWizard'));
const MyAnnounces = lazy(() => import('./modules/my/MyAnnounces'));
const Orders = lazy(() => import('./modules/trade/Orders'));
const OrderDetail = lazy(() => import('./modules/trade/OrderDetail'));
const Inbox = lazy(() => import('./modules/chat/Inbox'));
const Thread = lazy(() => import('./modules/chat/Thread'));

const fallback = (
  <div className="text-center py-5">
    <Spinner animation="border" />
  </div>
);

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={fallback}>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<Home />} />
            <Route path="search" element={<Search />} />
            <Route path="announce/:id" element={<AnnounceDetail />} />
            <Route path="login" element={<Login />} />
            <Route path="register" element={<Register />} />
            <Route element={<RequireAuth />}>
              <Route path="me" element={<Profile />} />
              <Route path="post" element={<PostWizard />} />
              <Route path="post/edit/:id" element={<PostWizard />} />
              <Route path="post/:kind" element={<PostWizard />} />
              <Route path="my/announces" element={<MyAnnounces />} />
              {features.trade && <Route path="orders" element={<Orders />} />}
              {features.trade && <Route path="orders/:id" element={<OrderDetail />} />}
              {features.chat && <Route path="messages" element={<Inbox />} />}
              {features.chat && <Route path="messages/:id" element={<Thread />} />}
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
