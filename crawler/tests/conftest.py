from datetime import datetime, timezone
from pathlib import Path

import pytest
from scrapy.http import HtmlResponse

FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 10, 17, 20, 0, tzinfo=timezone.utc)


def response(name, url):
    return HtmlResponse(url=url, body=(FIXTURES / name).read_bytes(), encoding="utf-8")


@pytest.fixture
def now():
    return NOW
