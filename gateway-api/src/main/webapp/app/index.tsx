import 'bootstrap-icons/font/bootstrap-icons.css';
import './sass/global.scss';
import './i18n';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import App from './App';
import { features } from './config/features';
import store from './config/store';
import { bootstrapSession } from './shared/auth/authSlice';

async function start() {
  if (features.mocks) {
    const { startMocks } = await import('./mocks/browser');
    await startMocks();
  }
  void store.dispatch(bootstrapSession());
  const root = createRoot(document.getElementById('root') as HTMLElement);
  root.render(
    <Provider store={store}>
      <App />
    </Provider>
  );
}

void start();
