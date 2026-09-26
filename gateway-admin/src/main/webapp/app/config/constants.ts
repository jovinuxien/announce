export const APP_NAME = 'Announce Admin';
export const APP_VERSION: string = (process.env.APP_VERSION as string) || 'dev';

export const ROLE_ADMIN = 'ROLE_ADMIN';
export const ROLE_SUPER = 'ROLE_SUPER';

export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZES = [10, 20, 50, 100];

export const LANGUAGE_KEY = 'announce.admin.lang';
export const FEATURES_KEY = 'announce.features';

export const SUPPORTED_LANGUAGES = ['en', 'da', 'sv'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];
