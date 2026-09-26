import { Form } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { changeLanguage, LANGUAGES, Language } from 'app/i18n';

const LABELS: Record<Language, string> = { en: 'English', da: 'Dansk', sv: 'Svenska' };

export default function LanguageSwitcher({ className }: { className?: string }) {
  const { i18n, t } = useTranslation();
  const current = (i18n.language || 'en').slice(0, 2) as Language;
  return (
    <Form.Select
      size="sm"
      className={className}
      aria-label={t('header.language')}
      value={LANGUAGES.includes(current) ? current : 'en'}
      onChange={e => changeLanguage(e.target.value as Language)}
    >
      {LANGUAGES.map(l => (
        <option key={l} value={l}>
          {LABELS[l]}
        </option>
      ))}
    </Form.Select>
  );
}
