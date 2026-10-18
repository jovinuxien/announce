"""Text helpers shared by the parsers: amounts, relative ages, Swedish and Danish short dates."""
import re
from datetime import datetime, timedelta, timezone

_DIGITS = re.compile(r"[^0-9]")


def now_utc():
    return datetime.now(timezone.utc).replace(microsecond=0)


def iso(dt):
    """ISO-8601 in UTC with a Z, or None."""
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def digits(s):
    """'223.000 km' -> 223000; None when nothing numeric."""
    if s is None:
        return None
    d = _DIGITS.sub("", s)
    return int(d) if d else None


def amount(s):
    """'46.000 kr.' / '374 800 kr' / '46000' -> int, None when nothing numeric."""
    return digits(s)


def clean(s):
    """Collapsed whitespace, stripped; None when empty."""
    if s is None:
        return None
    s = re.sub(r"\s+", " ", s).strip()
    return s or None


# relative ages on the list cards: dba "2 t.", "3 dage", "35 min."; tradera "Inkom 3 h sedan", "Inkom 2 dagar sedan"
_RELATIVE = re.compile(
    r"(?P<n>\d+)\s*(?P<unit>min|minut|minuter|m\b|t\.|t\b|tim|timmar|timme|h\b|dag|dage|dagar|d\b|uge|uger|vecka|veckor|v\b)",
    re.IGNORECASE,
)


def relative_age(text, now=None):
    """The moment `text` refers to ('2 t.' = two hours ago), or None when the text is not a relative age."""
    if not text:
        return None
    now = now or now_utc()
    t = text.strip().lower()
    if t in ("i dag", "idag", "nu", "just nu"):
        return now
    if t in ("i går", "igår"):
        return now - timedelta(days=1)
    m = _RELATIVE.search(t)
    if not m:
        return None
    n = int(m.group("n"))
    unit = m.group("unit").rstrip(".").lower()
    if unit.startswith("min") or unit == "m":
        return now - timedelta(minutes=n)
    if unit in ("t", "tim", "timmar", "timme", "h"):
        return now - timedelta(hours=n)
    if unit.startswith("dag") or unit == "d":
        return now - timedelta(days=n)
    if unit.startswith("uge") or unit.startswith("veck") or unit == "v":
        return now - timedelta(weeks=n)
    return None


_MONTHS = {
    "jan": 1, "feb": 2, "mar": 3, "apr": 4, "maj": 5, "jun": 6, "jul": 7, "aug": 8, "sep": 9, "sept": 9, "okt": 10,
    "nov": 11, "dec": 12,
}
_SHORT_DATE = re.compile(r"(?P<d>\d{1,2})\.?\s+(?P<mon>[a-zæøå]+)\.?(?:\s+(?P<y>\d{4}))?(?:,?\s+(?P<h>\d{1,2})[.:](?P<mi>\d{2}))?", re.IGNORECASE)


def short_date(text, now=None, future=False):
    """'23 okt 17:59' (tradera Sluttid) or '17. okt. 2026, 19.56' (dba Opdateret) -> datetime in UTC, or None.

    Without a year the current year is assumed; for a `future` date (an auction end) that already passed, next year.
    The sites show local time (CET/CEST); the result is treated as UTC, an hour or two of imprecision the importer
    accepts for a snippet feed.
    """
    if not text:
        return None
    now = now or now_utc()
    m = _SHORT_DATE.search(text.strip().lower())
    if not m or m.group("mon")[:4].rstrip(".") not in _MONTHS and m.group("mon")[:3] not in _MONTHS:
        return None
    month = _MONTHS.get(m.group("mon")[:4]) or _MONTHS.get(m.group("mon")[:3])
    year = int(m.group("y")) if m.group("y") else now.year
    try:
        dt = datetime(year, month, int(m.group("d")), int(m.group("h") or 0), int(m.group("mi") or 0), tzinfo=timezone.utc)
    except ValueError:
        return None
    if future and not m.group("y") and dt < now - timedelta(days=1):
        dt = dt.replace(year=year + 1)
    return dt
