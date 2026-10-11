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
    // features.mocks only: MSW serves the phase-2 admin paths from app/mocks/handlers.ts (otherwise the trade screens
    // read the order module and the chat screens messaging, through the edge). Everything else passes through.
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
