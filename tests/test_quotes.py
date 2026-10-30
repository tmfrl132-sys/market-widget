import json
from pathlib import Path

import pytest
import requests

from collector import quotes
from collector.config import SYMBOLS

FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "yahoo_ks11.json").read_text())


class FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


def ok_get(url, **kwargs):
    return FakeResponse(FIXTURE)


@pytest.fixture(autouse=True)
def no_sleep(monkeypatch):
    monkeypatch.setattr(quotes.time, "sleep", lambda s: None)


def test_parse_chart_uses_last_non_null_close():
    payload = json.loads(json.dumps(FIXTURE))
    del payload["chart"]["result"][0]["meta"]["regularMarketPrice"]
    price, _, history = quotes.parse_chart(payload)
    assert price == 2650.12
    assert history == [2610.0, 2622.5, 2630.1, 2637.82, 2650.12]


def test_parse_chart_prev_close_is_second_last_close():
    _, prev_close, _ = quotes.parse_chart(FIXTURE)
    assert prev_close == 2637.82


def test_fetch_quotes_change_pct():
    result = quotes.fetch_quotes({}, get=ok_get)
    ks11 = next(q for q in result if q["id"] == "KS11")
    assert ks11["name"] == "코스피"
    assert ks11["price"] == 2650.12
    assert ks11["change"] == 12.3
    assert ks11["change_pct"] == 0.47
    assert ks11["stale"] is False


def test_failed_symbol_keeps_previous_and_marks_stale():
    calls = []

    def failing_get(url, **kwargs):
        calls.append(url)
        raise requests.ConnectionError("down")

    prev_item = {"id": "KS11", "name": "코스피", "price": 2600.0, "change": 1.0,
                 "change_pct": 0.04, "history": [2600.0], "stale": False}
    result = quotes.fetch_quotes({"KS11": prev_item}, get=failing_get)
    ks11 = next(q for q in result if q["id"] == "KS11")
    assert ks11["price"] == 2600.0
    assert ks11["history"] == [2600.0]
    assert ks11["stale"] is True
    assert len(calls) == 3 * len(SYMBOLS)


def test_failed_symbol_without_previous():
    def failing_get(url, **kwargs):
        raise requests.ConnectionError("down")

    ks11 = next(q for q in quotes.fetch_quotes({}, get=failing_get) if q["id"] == "KS11")
    assert ks11["price"] is None
    assert ks11["change"] is None
    assert ks11["change_pct"] is None
    assert ks11["history"] == []
    assert ks11["stale"] is True


def test_returns_all_ten_in_config_order():
    result = quotes.fetch_quotes({}, get=ok_get)
    assert [q["id"] for q in result] == [
        "KS11", "KQ11", "GSPC", "IXIC", "KRW", "TNX", "VIX", "CL", "GC", "BTC"]


def test_prev_close_when_today_bar_is_null():
    # 서울 2026-10-28, 29, 30 일봉. 오늘(30일) 봉 close가 아직 null이고 시세는 meta에만 있음.
    day = 86400
    base = 1793232000  # 2026-10-28 00:00 UTC
    payload = {"chart": {"result": [{
        "meta": {"regularMarketPrice": 110.0, "regularMarketTime": base + 2 * day + 3600,
                 "gmtoffset": 32400},
        "timestamp": [base, base + day, base + 2 * day],
        "indicators": {"quote": [{"close": [90.0, 100.0, None]}]},
    }]}}
    price, prev_close, _ = quotes.parse_chart(payload)
    assert price == 110.0
    assert prev_close == 100.0


def test_fetch_quotes_includes_chart_closes():
    ks11 = next(q for q in quotes.fetch_quotes({}, get=ok_get) if q["id"] == "KS11")
    assert ks11["chart"] == [2600.0, 2610.0, 2622.5, 2630.1, 2637.82, 2650.12]


def test_chart_url_requests_three_months():
    urls = []

    def get(url, **kwargs):
        urls.append(url)
        return FakeResponse(FIXTURE)

    quotes.fetch_quotes({}, get=get)
    assert "range=3mo" in urls[0] and "interval=1d" in urls[0]


def test_failed_symbol_without_previous_has_empty_chart():
    def failing_get(url, **kwargs):
        raise requests.ConnectionError("down")

    ks11 = next(q for q in quotes.fetch_quotes({}, get=failing_get) if q["id"] == "KS11")
    assert ks11["chart"] == []
