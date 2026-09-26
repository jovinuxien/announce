const CURRENCY_BY_LANG: Record<string, string> = { da: 'DKK', sv: 'SEK', en: 'DKK' };

export function formatMoney(amount: number | null | undefined, currency?: string | null, lang = 'en'): string {
  if (amount == null || Number.isNaN(amount)) return '';
  const cur = currency || CURRENCY_BY_LANG[lang] || 'DKK';
  try {
    return new Intl.NumberFormat(lang === 'en' ? 'en-DK' : lang, { style: 'currency', currency: cur, maximumFractionDigits: 0 }).format(amount);
  } catch {
    return `${amount} ${cur}`;
  }
}

export const centsToAmount = (cents: number | null | undefined): number | undefined => (cents == null ? undefined : cents / 100);
export const amountToCents = (amount: number): number => Math.round(amount * 100);
