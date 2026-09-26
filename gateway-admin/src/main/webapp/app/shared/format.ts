/** Small display helpers; dates arrive as ISO strings from Jackson (LocalDateTime without zone). */

export function formatDateTime(value: string | null | undefined, locale?: string): string {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDate(value: string | null | undefined, locale?: string): string {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleDateString(locale, { dateStyle: 'medium' });
}

export function formatMoney(amount: number | string | null | undefined, currency?: string | null, locale?: string): string {
  if (amount === null || amount === undefined || amount === '') return '';
  const n = typeof amount === 'string' ? Number(amount) : amount;
  if (Number.isNaN(n)) return String(amount);
  if (currency) {
    try {
      return new Intl.NumberFormat(locale, { style: 'currency', currency, maximumFractionDigits: 2 }).format(n);
    } catch {
      return `${n.toFixed(2)} ${currency}`;
    }
  }
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(n);
}

export function formatCents(cents: number | null | undefined, currency?: string | null, locale?: string): string {
  if (cents === null || cents === undefined) return '';
  return formatMoney(cents / 100, currency, locale);
}

export function hostOf(url: string | null | undefined): string {
  if (!url) return '';
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}
