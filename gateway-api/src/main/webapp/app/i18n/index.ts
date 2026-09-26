import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import enCommon from './locales/en/common.json';
import enAuth from './locales/en/auth.json';
import enSearch from './locales/en/search.json';
import enAnnounce from './locales/en/announce.json';
import enPost from './locales/en/post.json';
import enMy from './locales/en/my.json';
import enTrade from './locales/en/trade.json';
import enChat from './locales/en/chat.json';
import enErrors from './locales/en/errors.json';
import daCommon from './locales/da/common.json';
import daAuth from './locales/da/auth.json';
import daSearch from './locales/da/search.json';
import daAnnounce from './locales/da/announce.json';
import daPost from './locales/da/post.json';
import daMy from './locales/da/my.json';
import daTrade from './locales/da/trade.json';
import daChat from './locales/da/chat.json';
import daErrors from './locales/da/errors.json';
import svCommon from './locales/sv/common.json';
import svAuth from './locales/sv/auth.json';
import svSearch from './locales/sv/search.json';
import svAnnounce from './locales/sv/announce.json';
import svPost from './locales/sv/post.json';
import svMy from './locales/sv/my.json';
import svTrade from './locales/sv/trade.json';
import svChat from './locales/sv/chat.json';
import svErrors from './locales/sv/errors.json';

export const LANGUAGES = ['en', 'da', 'sv'] as const;
export type Language = (typeof LANGUAGES)[number];
export const NAMESPACES = ['common', 'auth', 'search', 'announce', 'post', 'my', 'trade', 'chat', 'errors'] as const;
const LANG_KEY = 'announceLang';

export const resources = {
  en: { common: enCommon, auth: enAuth, search: enSearch, announce: enAnnounce, post: enPost, my: enMy, trade: enTrade, chat: enChat, errors: enErrors },
  da: { common: daCommon, auth: daAuth, search: daSearch, announce: daAnnounce, post: daPost, my: daMy, trade: daTrade, chat: daChat, errors: daErrors },
  sv: { common: svCommon, auth: svAuth, search: svSearch, announce: svAnnounce, post: svPost, my: svMy, trade: svTrade, chat: svChat, errors: svErrors },
};

export function detectLanguage(): Language {
  try {
    const saved = window.localStorage.getItem(LANG_KEY);
    if (saved && (LANGUAGES as readonly string[]).includes(saved)) return saved as Language;
    const nav = (navigator.language || 'en').slice(0, 2);
    if ((LANGUAGES as readonly string[]).includes(nav)) return nav as Language;
  } catch {
    // no storage / no navigator
  }
  return 'en';
}

export function changeLanguage(lang: Language): void {
  try {
    window.localStorage.setItem(LANG_KEY, lang);
  } catch {
    // ignore
  }
  void i18next.changeLanguage(lang);
  document.documentElement.lang = lang;
}

if (!i18next.isInitialized) {
  void i18next.use(initReactI18next).init({
    resources,
    lng: typeof window === 'undefined' ? 'en' : detectLanguage(),
    fallbackLng: 'en',
    ns: [...NAMESPACES],
    defaultNS: 'common',
    nsSeparator: ':',
    keySeparator: '.',
    interpolation: { escapeValue: false },
    returnNull: false,
  });
}

export default i18next;
