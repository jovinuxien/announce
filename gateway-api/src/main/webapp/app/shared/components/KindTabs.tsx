import { Nav } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { Kind, KINDS } from 'app/shared/model/announce';

export function kindLabel(t: (k: string) => string, kind: Kind | string): string {
  return t(`kind.${kind}`);
}

export default function KindTabs({ value, onChange }: { value: Kind | null; onChange: (kind: Kind | null) => void }) {
  const { t } = useTranslation();
  return (
    <Nav variant="pills" className="an-kind-tabs" activeKey={value ?? 'ALL'} onSelect={k => onChange(k === 'ALL' ? null : (k as Kind))}>
      <Nav.Item>
        <Nav.Link eventKey="ALL">{t('kind.ALL')}</Nav.Link>
      </Nav.Item>
      {KINDS.map(k => (
        <Nav.Item key={k}>
          <Nav.Link eventKey={k}>{t(`kind.${k}`)}</Nav.Link>
        </Nav.Item>
      ))}
    </Nav>
  );
}
