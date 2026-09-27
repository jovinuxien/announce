import { ChangeEvent, useRef, useState } from 'react';
import { Button, ProgressBar } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { storageApi } from 'app/shared/api/storageApi';
import { describeError } from 'app/shared/util/problem';
import ProblemAlert from './ProblemAlert';

const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,image/avif';
const MAX_BYTES = 10 * 1024 * 1024;

interface Props {
  urls: string[];
  onChange: (urls: string[]) => void;
  max?: number;
}

/** Gallery editor of the post wizard: pick files (uploaded one by one to /api/storage/upload), reorder, remove. */
export default function ImageUploader({ urls, onChange, max = 12 }: Props) {
  const { t } = useTranslation('post');
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<{ name: string; percent: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pick = async (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    setError(null);
    let next = [...urls];
    for (const file of files) {
      if (next.length >= max) {
        setError(t('gallery.tooMany', { max }));
        break;
      }
      if (file.size > MAX_BYTES) {
        setError(t('gallery.tooBig', { name: file.name }));
        continue;
      }
      setBusy({ name: file.name, percent: 0 });
      try {
        const res = await storageApi.upload(file, percent => setBusy({ name: file.name, percent }));
        next = [...next, res.url];
        onChange(next);
      } catch (err) {
        setError(describeError(err, t('gallery.uploadFailed', { name: file.name })));
      }
    }
    setBusy(null);
  };

  const move = (from: number, to: number) => {
    if (to < 0 || to >= urls.length) return;
    const next = [...urls];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onChange(next);
  };

  return (
    <div>
      <div className="d-flex flex-wrap gap-2 mb-2">
        {urls.map((u, i) => (
          <div key={u + i} className="position-relative border rounded p-1 bg-white" style={{ width: 120 }}>
            <img src={u} alt="" style={{ width: '100%', height: 90, objectFit: 'cover' }} className="rounded" />
            <div className="d-flex justify-content-between mt-1">
              <Button size="sm" variant="light" onClick={() => move(i, i - 1)} disabled={i === 0} aria-label={t('gallery.moveLeft')}>‹</Button>
              <Button size="sm" variant="light" onClick={() => onChange(urls.filter((_, j) => j !== i))} aria-label={t('gallery.remove')}>×</Button>
              <Button size="sm" variant="light" onClick={() => move(i, i + 1)} disabled={i === urls.length - 1} aria-label={t('gallery.moveRight')}>›</Button>
            </div>
            {i === 0 && <span className="badge bg-primary position-absolute top-0 start-0 m-1">{t('gallery.main')}</span>}
          </div>
        ))}
      </div>
      <input ref={input} type="file" accept={ACCEPT} multiple hidden onChange={pick} />
      <Button variant="outline-primary" size="sm" disabled={!!busy || urls.length >= max} onClick={() => input.current?.click()}>
        <i className="bi bi-images me-1" />
        {t('gallery.add')}
      </Button>
      <span className="text-muted small ms-2">{t('gallery.hint', { max })}</span>
      {busy && <ProgressBar now={busy.percent} label={`${busy.name} ${busy.percent}%`} className="mt-2" />}
      <ProblemAlert message={error} onClose={() => setError(null)} />
    </div>
  );
}
