import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react';
import { Button, Form, InputGroup, ListGroup } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { searchApi } from 'app/shared/api/searchApi';

/** The header search box: 200 ms debounced suggestions from /api/suggest, Enter or a click runs the search. */
export default function SuggestBox({ initial = '' }: { initial?: string }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState(initial);
  const [items, setItems] = useState<string[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => setValue(initial), [initial]);

  useEffect(() => {
    window.clearTimeout(timer.current);
    if (value.trim().length < 2) {
      setItems([]);
      return;
    }
    timer.current = window.setTimeout(() => {
      searchApi
        .suggest(value.trim())
        .then(s => setItems(s.map(x => x.phrase).filter(Boolean).slice(0, 8)))
        .catch(() => setItems([]));
    }, 200);
    return () => window.clearTimeout(timer.current);
  }, [value]);

  const go = (q: string) => {
    setOpen(false);
    setActive(-1);
    navigate(`/search?q=${encodeURIComponent(q.trim())}`);
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    go(active >= 0 && items[active] ? items[active] : value);
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!items.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive(a => Math.min(items.length - 1, a + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive(a => Math.max(-1, a - 1));
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <Form onSubmit={submit} className="position-relative flex-grow-1" role="search">
      <InputGroup>
        <Form.Control
          type="search"
          placeholder={t('header.searchPlaceholder')}
          aria-label={t('header.search')}
          value={value}
          onChange={e => {
            setValue(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 150)}
          onKeyDown={onKey}
        />
        <Button type="submit" variant="primary">
          {t('header.search')}
        </Button>
      </InputGroup>
      {open && items.length > 0 && (
        <ListGroup className="an-suggest shadow-sm">
          {items.map((s, i) => (
            <ListGroup.Item key={s} action active={i === active} onMouseDown={() => go(s)}>
              {s}
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
    </Form>
  );
}
