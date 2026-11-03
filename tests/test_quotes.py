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


def test_requests_one_year_daily_and_five_year_weekly():
    urls = []

    def get(url, **kwargs):
        urls.append(url)
        return FakeResponse(FIXTURE)

    quotes.fetch_quotes({}, get=get)
    assert "range=1y" in urls[0] and "interval=1d" in urls[0]
    assert "range=5y" in urls[1] and "interval=1wk" in urls[1]


WEEKLY = {"chart": {"result": [{
    "meta": {"regularMarketPrice": 2650.12},
    "timestamp": [1, 2, 3],
    "indicators": {"quote": [{"close": [2000.0, None, 2500.556]}]},
}]}}


def split_get(url, **kwargs):
    return FakeResponse(WEEKLY if "interval=1wk" in url else FIXTURE)


def test_fetch_quotes_includes_daily_and_weekly():
    ks11 = next(q for q in quotes.fetch_quotes({}, get=split_get) if q["id"] == "KS11")
    assert ks11["daily"] == [2600.0, 2610.0, 2622.5, 2630.1, 2637.82, 2650.12]
    assert ks11["weekly"] == [2000.0, 2500.56]


def test_chart_keeps_last_63_daily_for_old_app_versions():
    payload = json.loads(json.dumps(FIXTURE))
    payload["chart"]["result"][0]["indicators"]["quote"][0]["close"] = [float(i) for i in range(100)]
    payload["chart"]["result"][0]["timestamp"] = list(range(100))

    def get(url, **kwargs):
        return FakeResponse(payload)

    ks11 = next(q for q in quotes.fetch_quotes({}, get=get) if q["id"] == "KS11")
    assert ks11["chart"] == [float(i) for i in range(37, 100)]


def test_weekly_failure_keeps_previous_weekly_and_is_not_stale():
    def get(url, **kwargs):
        if "interval=1wk" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "weekly": [1.0, 2.0]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["weekly"] == [1.0, 2.0]
    assert ks11["stale"] is False
    assert ks11["daily"]


def max_payload(closes, start=1793232000, day=86400):
    return {"chart": {"result": [{
        "meta": {"gmtoffset": 32400},
        "timestamp": [start + i * day for i in range(len(closes))],
        "indicators": {"quote": [{"close": closes}]},
    }]}}


def test_drawdowns_from_running_peak():
    assert quotes.drawdowns([100.0, 50.0, 200.0, 150.0]) == [0.0, -50.0, 0.0, -25.0]


def test_drawdowns_negative_price_stops_at_minus_100():
    assert quotes.drawdowns([100.0, -37.63, 50.0]) == [0.0, -100.0, -50.0]


def test_max_fields_peak_and_mdd():
    fields = quotes.max_fields(max_payload([100.0, None, 40.0, 200.0, 150.0]), with_drawdown=True)
    assert fields["max_closes"] == [100.0, 40.0, 200.0, 150.0]
    assert fields["max_drawdowns"] == [0.0, -60.0, 0.0, -25.0]
    assert fields["peak"] == 200.0
    assert fields["mdd"] == -60.0
    assert fields["max_start"] == 2026


def test_max_fields_without_drawdown_keeps_price_only():
    fields = quotes.max_fields(max_payload([100.0, 40.0]), with_drawdown=False)
    assert fields["max_closes"] == [100.0, 40.0]
    assert fields["max_drawdowns"] == []
    assert fields["peak"] is None
    assert fields["mdd"] is None


def test_max_fields_thins_points_but_keeps_trough_and_last():
    closes = [100.0] * 1500
    closes[700] = 20.0
    closes[-1] = 90.0
    fields = quotes.max_fields(max_payload(closes), with_drawdown=True)
    assert len(fields["max_closes"]) == 500
    assert len(fields["max_drawdowns"]) == 500
    assert min(fields["max_drawdowns"]) == -80.0
    assert fields["mdd"] == -80.0
    assert fields["max_closes"][-1] == 90.0


def test_requests_full_history_with_explicit_dates():
    urls = []

    def get(url, **kwargs):
        urls.append(url)
        return FakeResponse(FIXTURE)

    quotes.fetch_quotes({}, get=get)
    assert "period1=0" in urls[2] and "interval=1d" in urls[2]
    assert "range=" not in urls[2]


def test_no_drawdown_symbols_have_no_mdd():
    result = {q["id"]: q for q in quotes.fetch_quotes({}, get=ok_get)}
    for id_ in ("KRW", "TNX", "VIX"):
        assert result[id_]["mdd"] is None
        assert result[id_]["max_drawdowns"] == []
        assert result[id_]["max_closes"]
    assert result["KS11"]["mdd"] is not None


def test_max_failure_keeps_previous_and_is_not_stale():
    def get(url, **kwargs):
        if "period1=" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "max_closes": [1.0, 2.0], "max_drawdowns": [0.0, -5.0],
                     "max_start": 1996, "peak": 2.0, "mdd": -5.0}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["max_closes"] == [1.0, 2.0]
    assert ks11["mdd"] == -5.0
    assert ks11["max_start"] == 1996
    assert ks11["stale"] is False


def test_failed_symbol_without_previous_has_empty_chart():
    def failing_get(url, **kwargs):
        raise requests.ConnectionError("down")

    ks11 = next(q for q in quotes.fetch_quotes({}, get=failing_get) if q["id"] == "KS11")
    assert ks11["chart"] == []
