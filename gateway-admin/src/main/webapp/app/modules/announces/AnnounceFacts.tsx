import { useTranslation } from 'react-i18next';
import type { AnnounceSummaryResponse } from 'app/shared/api/announcesApi';
import { formatDateTime, formatMoney } from 'app/shared/format';

/** The per-kind cell of the list: price for goods, employer for jobs, dates for events. */
export default function AnnounceFacts({ a }: { a: AnnounceSummaryResponse }) {
  const { t, i18n } = useTranslation();
  switch (a.kind) {
    case 'GOODS':
      return (
        <>
          <div>{a.saleType === 'GIVEAWAY' ? t('common.saleType.GIVEAWAY') : formatMoney(a.price, undefined, i18n.language)}</div>
          <small className="text-muted">
            {a.saleType && t(`common.saleType.${a.saleType}`)}
            {a.condition && ` · ${t(`common.condition.${a.condition}`)}`}
          </small>
        </>
      );
    case 'JOB':
      return (
        <>
          <div>{a.employer}</div>
          <small className="text-muted">
            {a.contractType && t(`common.contractType.${a.contractType}`)}
            {a.applicationDeadline && ` · ${t('announces.fields.deadline')} ${formatDateTime(a.applicationDeadline, i18n.language)}`}
          </small>
        </>
      );
    case 'EVENT':
      return (
        <>
          <div>{formatDateTime(a.startsAt, i18n.language)}</div>
          <small className="text-muted">
            {t('announces.fields.until')} {formatDateTime(a.endsAt, i18n.language)}
            {a.ticketed ? ` · ${formatMoney(a.ticketPrice, undefined, i18n.language)}` : ` · ${t('announces.fields.free')}`}
          </small>
        </>
      );
    default:
      return null;
  }
}
