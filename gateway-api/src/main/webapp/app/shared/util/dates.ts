/** The backends use LocalDateTime (ISO without zone); the browser shows it in the local zone as is. */
export function formatDateTime(iso: string | null | undefined, lang = 'en'): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat(lang, { dateStyle: 'medium', timeStyle: 'short' }).format(d);
}

export function formatDate(iso: string | null | undefined, lang = 'en'): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat(lang, { dateStyle: 'medium' }).format(d);
}

export function formatEpoch(epochSeconds: number | null | undefined, lang = 'en'): string {
  if (epochSeconds == null) return '';
  return formatDate(new Date(epochSeconds * 1000).toISOString(), lang);
}

/** `<input type="datetime-local">` value (yyyy-MM-ddTHH:mm) from an ISO LocalDateTime and back. */
export function toInputDateTime(iso: string | null | undefined): string {
  return iso ? iso.slice(0, 16) : '';
}
export function fromInputDateTime(value: string): string | null {
  return value ? (value.length === 16 ? `${value}:00` : value) : null;
}

export function countdown(untilIso: string | null | undefined, now: Date = new Date()): string {
  if (!untilIso) return '';
  const ms = new Date(untilIso).getTime() - now.getTime();
  if (Number.isNaN(ms) || ms <= 0) return '0m';
  const m = Math.floor(ms / 60000);
  const d = Math.floor(m / 1440);
  const h = Math.floor((m % 1440) / 60);
  const mm = m % 60;
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${mm}m` : `${mm}m`;
}
