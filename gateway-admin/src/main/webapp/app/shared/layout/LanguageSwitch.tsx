import { Dropdown } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { SUPPORTED_LANGUAGES, type Language } from 'app/config/constants';
import { changeLanguage } from 'app/config/i18n';

const labels: Record<Language, string> = { en: 'English', da: 'Dansk', sv: 'Svenska' };

/** Language menu; the choice is persisted in localStorage (see config/i18n.ts). */
export default function LanguageSwitch() {
  const { t, i18n } = useTranslation();
  const current = (i18n.language?.slice(0, 2) as Language) || 'en';
  return (
    <Dropdown align="end">
      <Dropdown.Toggle variant="outline-light" size="sm" id="language-switch" aria-label={t('common.language')}>
        <i className="bi bi-translate me-1" aria-hidden />
        {labels[current] ?? current}
      </Dropdown.Toggle>
      <Dropdown.Menu>
        {SUPPORTED_LANGUAGES.map((lng) => (
          <Dropdown.Item key={lng} active={lng === current} onClick={() => changeLanguage(lng)}>
            {labels[lng]}
          </Dropdown.Item>
        ))}
      </Dropdown.Menu>
    </Dropdown>
  );
}
