import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import './app.css';
import App from './App';
import { store } from './config/store';
import { initI18n } from './config/i18n';
import { shouldStartMocks } from './config/features';

async function bootstrap() {
  await initI18n();
  if (shouldStartMocks()) {
    // Phase-2 screens (features.trade / features.chat) are served by MSW until the order and messaging modules
    // are routed through the edge. The worker only intercepts the mocked admin paths; everything else passes through.
    const { startMocks } = await import('./mocks/browser');
    await startMocks();
  }
  const container = document.getElementById('root');
  if (!container) throw new Error('#root missing');
  createRoot(container).render(
    <Provider store={store}>
      <App />
    </Provider>,
  );
}

bootstrap();
