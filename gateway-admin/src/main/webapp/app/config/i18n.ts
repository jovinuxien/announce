import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import { LANGUAGE_KEY, SUPPORTED_LANGUAGES, type Language } from './constants';

import enCommon from '../i18n/en/common.json';
import enAuth from '../i18n/en/auth.json';
import enDashboard from '../i18n/en/dashboard.json';
import enCatalog from '../i18n/en/catalog.json';
import enAnnounces from '../i18n/en/announces.json';
import enListings from '../i18n/en/listings.json';
import enMembers from '../i18n/en/members.json';
import enAdmins from '../i18n/en/admins.json';
import enRegions from '../i18n/en/regions.json';
import enTrade from '../i18n/en/trade.json';
import enChat from '../i18n/en/chat.json';

import daCommon from '../i18n/da/common.json';
import daAuth from '../i18n/da/auth.json';
import daDashboard from '../i18n/da/dashboard.json';
import daCatalog from '../i18n/da/catalog.json';
import daAnnounces from '../i18n/da/announces.json';
import daListings from '../i18n/da/listings.json';
import daMembers from '../i18n/da/members.json';
import daAdmins from '../i18n/da/admins.json';
import daRegions from '../i18n/da/regions.json';
import daTrade from '../i18n/da/trade.json';
import daChat from '../i18n/da/chat.json';

import svCommon from '../i18n/sv/common.json';
import svAuth from '../i18n/sv/auth.json';
import svDashboard from '../i18n/sv/dashboard.json';
import svCatalog from '../i18n/sv/catalog.json';
import svAnnounces from '../i18n/sv/announces.json';
import svListings from '../i18n/sv/listings.json';
import svMembers from '../i18n/sv/members.json';
import svAdmins from '../i18n/sv/admins.json';
import svRegions from '../i18n/sv/regions.json';
import svTrade from '../i18n/sv/trade.json';
import svChat from '../i18n/sv/chat.json';

/**
 * One namespace ("translation") per language; each JSON file under app/i18n/<lng>/ becomes a top-level key named
 * after the file, so `t('announces.title')` reads app/i18n/<lng>/announces.json -> "title". scripts/i18n-check.mjs
 * relies on this convention.
 */
export const resources = {
  en: {
    translation: {
      common: enCommon, auth: enAuth, dashboard: enDashboard, catalog: enCatalog, announces: enAnnounces,
      listings: enListings, members: enMembers, admins: enAdmins, regions: enRegions, trade: enTrade, chat: enChat,
    },
  },
  da: {
    translation: {
      common: daCommon, auth: daAuth, dashboard: daDashboard, catalog: daCatalog, announces: daAnnounces,
      listings: daListings, members: daMembers, admins: daAdmins, regions: daRegions, trade: daTrade, chat: daChat,
    },
  },
  sv: {
    translation: {
      common: svCommon, auth: svAuth, dashboard: svDashboard, catalog: svCatalog, announces: svAnnounces,
      listings: svListings, members: svMembers, admins: svAdmins, regions: svRegions, trade: svTrade, chat: svChat,
    },
  },
};

export function storedLanguage(): Language {
  try {
    const stored = typeof window !== 'undefined' ? window.localStorage.getItem(LANGUAGE_KEY) : null;
    if (stored && (SUPPORTED_LANGUAGES as readonly string[]).includes(stored)) return stored as Language;
    const nav = typeof navigator !== 'undefined' ? navigator.language.slice(0, 2) : 'en';
    if ((SUPPORTED_LANGUAGES as readonly string[]).includes(nav)) return nav as Language;
  } catch {
    /* ignore */
  }
  return 'en';
}

export async function initI18n(lng: Language = storedLanguage()) {
  if (i18next.isInitialized) {
    if (i18next.language !== lng) await i18next.changeLanguage(lng);
    return i18next;
  }
  await i18next.use(initReactI18next).init({
    resources,
    lng,
    fallbackLng: 'en',
    supportedLngs: [...SUPPORTED_LANGUAGES],
    interpolation: { escapeValue: false },
    returnNull: false,
  });
  return i18next;
}

export async function changeLanguage(lng: Language) {
  await i18next.changeLanguage(lng);
  try {
    window.localStorage.setItem(LANGUAGE_KEY, lng);
  } catch {
    /* private mode */
  }
  if (typeof document !== 'undefined') document.documentElement.lang = lng;
}

export default i18next;
