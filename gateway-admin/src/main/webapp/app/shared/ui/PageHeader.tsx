import type { ReactNode } from 'react';

interface Props {
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  icon?: string;
}

export default function PageHeader({ title, subtitle, actions, icon }: Props) {
  return (
    <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
      <div>
        <h1 className="h3 mb-0">
          {icon && <i className={`bi ${icon} me-2 text-secondary`} aria-hidden />}
          {title}
        </h1>
        {subtitle && <div className="text-muted small">{subtitle}</div>}
      </div>
      {actions && <div className="d-flex gap-2">{actions}</div>}
    </div>
  );
}
