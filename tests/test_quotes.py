import json
from datetime import date, datetime, timedelta, timezone
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


def test_returns_all_eleven_in_config_order():
    result = quotes.fetch_quotes({}, get=ok_get)
    assert [q["id"] for q in result] == [
        "KS11", "KQ11", "GSPC", "NDX", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC"]


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


def day_payload(closes, today_index):
    # 서울 2026-11-02부터 하루 간격 일봉. regularMarketTime은 today_index 날의 장중이다.
    base = 1793577600 - 32400
    day = 86400
    return {"chart": {"result": [{
        "meta": {"regularMarketPrice": 5619.4, "regularMarketTime": base + today_index * day + 3600,
                 "gmtoffset": 32400},
        "timestamp": [base + i * day for i in range(len(closes))],
        "indicators": {"quote": [{"close": closes}]},
    }]}}


def test_prev_close_unknown_when_previous_day_bar_is_empty():
    # 11/4 10:37 실제 사례: 11/3 일봉이 비어 있어 11/2 종가와 비교해 -4.09%가 나왔다.
    _, prev_close, _ = quotes.parse_chart(day_payload([5859.08, None, 5619.4], today_index=2))
    assert prev_close is None


def test_prev_close_unknown_when_today_bar_absent_and_last_bar_empty():
    _, prev_close, _ = quotes.parse_chart(day_payload([5859.08, None], today_index=2))
    assert prev_close is None


def test_prev_close_when_today_bar_absent():
    _, prev_close, _ = quotes.parse_chart(day_payload([5859.08, 5648.79], today_index=2))
    assert prev_close == 5648.79


def test_unknown_prev_close_leaves_change_empty_but_keeps_price():
    def get(url, **kwargs):
        return FakeResponse(day_payload([5859.08, None, 5619.4], today_index=2))

    ks11 = next(q for q in quotes.fetch_quotes({}, get=get) if q["id"] == "KS11")
    assert ks11["price"] == 5619.4
    assert ks11["change"] is None
    assert ks11["change_pct"] is None
    assert ks11["stale"] is False


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
    assert len(fields["max_closes"]) <= 500
    assert len(fields["max_drawdowns"]) <= 500
    assert min(fields["max_drawdowns"]) == -80.0
    assert fields["mdd"] == -80.0
    assert fields["max_closes"][-1] == 90.0


def test_thin_closes_keeps_peak_trough_first_and_last():
    closes = [float(100 + i % 7) for i in range(14000)]
    closes[0] = 77.0
    closes[5000] = 9114.0
    closes[5003] = 11.0
    closes[-1] = 55.0
    points = quotes.thin_closes(closes)
    assert len(points) <= 500
    assert points[0] == 77.0
    assert points[-1] == 55.0
    assert 9114.0 in points and 11.0 in points
    assert points.index(9114.0) < points.index(11.0)


def test_event_marks_at_deepest_point_in_window():
    dates = [date(2020, 1, 15), date(2020, 2, 20), date(2020, 3, 23), date(2020, 4, 20), date(2020, 6, 1)]
    depths = [0.0, -10.0, -34.04, -20.0, -50.0]
    assert quotes.event_marks("GSPC", dates, depths, size=1) == [
        {"label": "코로나", "index": 2, "depth": -34.0, "date": "2020-03-23"}]


def test_event_marks_skips_shallow_drop():
    dates = [date(2020, 2, 20), date(2020, 3, 23)]
    assert quotes.event_marks("GSPC", dates, [-5.0, -14.9], size=1) == []


def test_event_marks_only_for_listed_symbols_and_covered_dates():
    dates = [date(1998, 6, 16)]
    assert quotes.event_marks("KS11", dates, [-64.7], size=1)[0]["label"] == "외환위기"
    assert quotes.event_marks("GSPC", dates, [-64.7], size=1) == []
    assert quotes.event_marks("KS11", [date(2015, 1, 1)], [-64.7], size=1) == []


def test_event_marks_index_follows_thinned_series():
    dates = [date(2020, 1, 1) + timedelta(days=i) for i in range(120)]
    depths = [0.0] * 120
    depths[70] = -30.0  # 2020-03-11
    assert quotes.event_marks("GSPC", dates, depths, size=7)[0]["index"] == 10


def test_max_fields_includes_events_only_with_drawdown():
    start = 1584921600  # 2020-03-23 00:00 UTC
    with_events = quotes.max_fields(max_payload([100.0, 50.0], start=start), True, "GSPC")
    assert with_events["max_events"][0]["label"] == "코로나"
    assert quotes.max_fields(max_payload([100.0, 50.0], start=start), False, "KRW")["max_events"] == []


def test_thin_closes_short_series_unchanged():
    assert quotes.thin_closes([1.0, 3.0, 2.0]) == [1.0, 3.0, 2.0]


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


# ---- 차트 날짜 (daily_dates, weekly_dates, max_dates) ----

def test_chart_dates_skip_null_close_bars_like_chart_closes():
    payload = max_payload([100.0, None, 40.0, None, 150.0])
    assert quotes.chart_dates(payload) == ["2026-10-29", "2026-10-31", "2026-11-02"]
    assert len(quotes.chart_dates(payload)) == len(quotes.chart_closes(payload)) == 3


def test_chart_dates_use_market_local_date_not_utc():
    # 2026-11-04 23:30 UTC 는 서울(+9h)로 11-05, 뉴욕(-5h)으로 11-04
    ts = 1793835000
    seoul = {"chart": {"result": [{"meta": {"gmtoffset": 32400}, "timestamp": [ts],
                                   "indicators": {"quote": [{"close": [1.0]}]}}]}}
    ny = {"chart": {"result": [{"meta": {"gmtoffset": -18000}, "timestamp": [ts],
                                "indicators": {"quote": [{"close": [1.0]}]}}]}}
    assert quotes.chart_dates(seoul) == ["2026-11-05"]
    assert quotes.chart_dates(ny) == ["2026-11-04"]


def test_chart_dates_without_gmtoffset_uses_utc():
    payload = {"chart": {"result": [{"meta": {}, "timestamp": [0, 86400],
                                     "indicators": {"quote": [{"close": [1.0, None]}]}}]}}
    assert quotes.chart_dates(payload) == ["1970-01-01"]


def utc_ts(*args):
    return int(datetime(*args, tzinfo=timezone.utc).timestamp())


def tz_payload(stamps, meta):
    return {"chart": {"result": [{"meta": meta, "timestamp": stamps,
                                  "indicators": {"quote": [{"close": [1.0] * len(stamps)}]}}]}}


def test_chart_dates_new_york_summer_bar_is_monday_not_sunday():
    # 2026-07-06 04:00 UTC = 뉴욕 월요일 00:00(EDT). 지금 오프셋(-18000, 표준시)을 그대로 쓰면 일요일이 된다
    payload = tz_payload([utc_ts(2026, 7, 6, 4)],
                         {"gmtoffset": -18000, "exchangeTimezoneName": "America/New_York"})
    assert quotes.chart_dates(payload) == ["2026-07-06"]


def test_chart_dates_new_york_winter_and_summer_bars_in_one_series():
    stamps = [utc_ts(2026, 1, 12, 5), utc_ts(2026, 7, 6, 4), utc_ts(2026, 11, 2, 5)]  # EST, EDT, EST(서머타임 끝난 뒤)
    payload = tz_payload(stamps, {"gmtoffset": -18000, "exchangeTimezoneName": "America/New_York"})
    assert quotes.chart_dates(payload) == ["2026-01-12", "2026-07-06", "2026-11-02"]


def test_chart_dates_london_krw_shape_summer_bar_at_2300_utc():
    # 원/달러 봉은 런던 자정에 시작한다. 여름에는 전날 23:00 UTC, 겨울에는 00:00 UTC. 지금(겨울) gmtoffset은 0이다
    stamps = [utc_ts(2026, 7, 5, 23), utc_ts(2026, 1, 12, 0)]
    payload = tz_payload(stamps, {"gmtoffset": 0, "exchangeTimezoneName": "Europe/London"})
    assert quotes.chart_dates(payload) == ["2026-07-06", "2026-01-12"]


def test_chart_dates_without_timezone_name_fall_back_to_gmtoffset():
    payload = tz_payload([utc_ts(2026, 7, 6, 4)], {"gmtoffset": -18000})
    assert quotes.chart_dates(payload) == ["2026-07-05"]


@pytest.mark.parametrize("name", ["Mars/Olympus_Mons", "../etc/passwd", "", None, 123, ["America/New_York"]])
def test_chart_dates_with_bad_timezone_name_fall_back_without_error(name):
    payload = tz_payload([utc_ts(2026, 11, 4, 23, 30)], {"gmtoffset": 32400, "exchangeTimezoneName": name})
    assert quotes.chart_dates(payload) == ["2026-11-05"]


def test_max_dates_use_timezone_name_per_bar():
    stamps = [utc_ts(2026, 1, 12, 5), utc_ts(2026, 7, 6, 4)]
    payload = tz_payload(stamps, {"gmtoffset": -18000, "exchangeTimezoneName": "America/New_York"})
    fields = quotes.max_fields(payload, with_drawdown=False)
    assert fields["max_dates"] == ["2026-01-12", "2026-07-06"]


def test_max_dates_bad_timezone_name_falls_back_without_error():
    payload = tz_payload([utc_ts(2026, 7, 6, 4)], {"gmtoffset": -18000, "exchangeTimezoneName": "Nowhere/City"})
    fields = quotes.max_fields(payload, with_drawdown=True)
    assert fields["max_dates"] == ["2026-07-05"]
    assert fields["max_start"] == 2026


def test_thin_indices_match_thin_closes_and_repeat_same_bar_twice():
    closes = [float(100 + i % 7) for i in range(14000)]
    closes[0] = 77.0
    closes[-1] = 55.0
    picks = quotes.thin_indices(closes)
    assert quotes.thin_closes(closes) == [closes[i] for i in picks]
    assert picks[0] == 0 and picks[-1] == len(closes) - 1
    assert picks[:3] == sorted(picks[:3])


def test_thin_indices_short_series_is_every_position():
    assert quotes.thin_indices([1.0, 3.0, 2.0]) == [0, 1, 2]
    assert quotes.thin_indices([]) == []


def test_max_dates_align_with_thinned_closes_including_duplicates():
    closes = [100.0] * 1500
    closes[0] = 50.0  # 처음 봉이 첫 묶음의 최저라 같은 봉이 두 번 들어간다
    closes[700] = 20.0
    closes[-1] = 90.0
    fields = quotes.max_fields(max_payload(closes), with_drawdown=True)
    assert len(fields["max_dates"]) == len(fields["max_closes"]) <= 500
    assert fields["max_dates"][0] == fields["max_dates"][1] == "2026-10-29"
    assert fields["max_closes"][:2] == [50.0, 50.0]
    trough = fields["max_closes"].index(20.0)
    expected = date(2026, 10, 29) + timedelta(days=700)
    assert fields["max_dates"][trough] == expected.isoformat()
    assert fields["max_dates"][-1] == (date(2026, 10, 29) + timedelta(days=1499)).isoformat()


def test_max_dates_skip_null_close_bars():
    fields = quotes.max_fields(max_payload([100.0, None, 40.0, 200.0]), with_drawdown=False)
    assert fields["max_closes"] == [100.0, 40.0, 200.0]
    assert fields["max_dates"] == ["2026-10-29", "2026-10-31", "2026-11-01"]


def test_fetch_quotes_dates_have_same_length_as_values_for_every_symbol():
    for q in quotes.fetch_quotes({}, get=split_get):
        assert len(q["daily_dates"]) == len(q["daily"])
        assert len(q["weekly_dates"]) == len(q["weekly"])
        assert len(q["max_dates"]) == len(q["max_closes"])


def test_fetch_quotes_weekly_dates_skip_null_bars():
    ks11 = next(q for q in quotes.fetch_quotes({}, get=split_get) if q["id"] == "KS11")
    assert ks11["weekly"] == [2000.0, 2500.56]
    # WEEKLY 봉 timestamp는 1, 2(null), 3 이고 gmtoffset이 없어 UTC 1970-01-01. null 봉은 빠진다
    assert ks11["weekly_dates"] == ["1970-01-01", "1970-01-01"]
    assert len(ks11["weekly_dates"]) == len(ks11["weekly"])


def test_weekly_failure_keeps_previous_weekly_dates():
    def get(url, **kwargs):
        if "interval=1wk" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "weekly": [1.0, 2.0], "weekly_dates": ["2026-01-05", "2026-01-12"]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["weekly_dates"] == ["2026-01-05", "2026-01-12"]


def test_weekly_failure_with_old_cache_without_dates_gives_empty_dates():
    def get(url, **kwargs):
        if "interval=1wk" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "weekly": [1.0, 2.0]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["weekly"] == [1.0, 2.0]
    assert ks11["weekly_dates"] == []


def test_weekly_failure_with_mismatched_previous_dates_empties_dates():
    def get(url, **kwargs):
        if "interval=1wk" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "weekly": [1.0, 2.0], "weekly_dates": ["2026-01-05"]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["weekly_dates"] == []


def test_max_failure_keeps_previous_max_dates():
    def get(url, **kwargs):
        if "period1=" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "max_closes": [1.0, 2.0], "max_dates": ["1996-01-02", "1996-01-03"],
                     "max_drawdowns": [0.0, -5.0], "max_start": 1996, "peak": 2.0, "mdd": -5.0}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["max_dates"] == ["1996-01-02", "1996-01-03"]
    assert len(ks11["max_dates"]) == len(ks11["max_closes"])


def test_max_failure_without_previous_dates_gives_empty_dates():
    def get(url, **kwargs):
        if "period1=" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    ks11 = next(q for q in quotes.fetch_quotes({}, get=get) if q["id"] == "KS11")
    assert ks11["max_closes"] == [] and ks11["max_dates"] == []


def test_failed_symbol_without_previous_has_empty_date_lists():
    def failing_get(url, **kwargs):
        raise requests.ConnectionError("down")

    ks11 = next(q for q in quotes.fetch_quotes({}, get=failing_get) if q["id"] == "KS11")
    assert ks11["daily_dates"] == [] and ks11["weekly_dates"] == [] and ks11["max_dates"] == []


def test_failed_symbol_keeps_previous_dates_and_adds_missing_ones():
    def failing_get(url, **kwargs):
        raise requests.ConnectionError("down")

    prev = {"KS11": {"id": "KS11", "daily": [1.0], "daily_dates": ["2026-11-04"]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=failing_get) if q["id"] == "KS11")
    assert ks11["stale"] is True
    assert ks11["daily_dates"] == ["2026-11-04"]
    assert ks11["weekly_dates"] == [] and ks11["max_dates"] == []


# --- v0.9 ---

def test_symbols_config_v09():
    from zoneinfo import ZoneInfo
    from collector import config
    ids = [s[0] for s in config.SYMBOLS]
    assert ids == ["KS11", "KQ11", "GSPC", "NDX", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC"]
    assert dict((s[0], s[2]) for s in config.SYMBOLS)["NDX"] == "^NDX"
    assert dict((s[0], s[1]) for s in config.SYMBOLS)["NDX"] == "나스닥100"
    assert not {"IXIC", "ES", "NQ"} & set(ids)
    assert dict((s[0], s[2]) for s in config.SYMBOLS)["DXY"] == "DX-Y.NYB"
    for id_ in ids:
        assert id_ in config.BIG_MOVE_BASE
        ZoneInfo(config.MARKET_TZ[id_])
    assert "DXY" in config.NO_DRAWDOWN
    assert "NDX" in config.STOCKS and not {"IXIC", "ES", "NQ"} & config.STOCKS
    assert config.BIG_MOVE_BASE["NDX"] == 2.0
    assert config.ALWAYS_OPEN == {"BTC"}
    assert config.BIG_MOVE_DAYS == 60
    assert config.BIG_MOVE_FACTOR == 2.5


def dxy_payload(stamps, closes, market_time):
    return {"chart": {"result": [{
        "meta": {"regularMarketPrice": 102.5, "regularMarketTime": market_time, "gmtoffset": -18000},
        "timestamp": stamps,
        "indicators": {"quote": [{"close": closes}]},
    }]}}


def test_prev_close_skips_empty_sunday_bar():
    payload = dxy_payload([1793941200, 1794114000, 1794200400], [102.236, None, 102.5], 1794236400)
    price, prev_close, _ = quotes.parse_chart(payload)
    assert prev_close == 102.236
    get = lambda url, **kw: FakeResponse(payload)
    dxy = next(q for q in quotes.fetch_quotes({}, get=get) if q["id"] == "DXY")
    assert dxy["change"] == 0.26
    assert dxy["change_pct"] == 0.26


def test_prev_close_unknown_when_weekday_bar_empty_regression():
    # 목요일 11-05 봉이 비고 마지막 두 봉이 (11-05 None, 11-06 값). 평일 빈 봉은 등락률을 비운다.
    payload = dxy_payload([1793854800, 1793941200], [None, 102.5], 1793941200 + 36000)
    _, prev_close, _ = quotes.parse_chart(payload)
    assert prev_close is None


def test_big_move_pct_rules():
    assert quotes.big_move_pct("KS11", [100 * 1.01 ** i for i in range(61)]) == 2.5
    assert quotes.big_move_pct("KS11", [100 * 1.01 ** i for i in range(60)]) == 2.0
    assert quotes.big_move_pct("KS11", [100.0] * 61) == 2.0
    assert quotes.big_move_pct("BTC", [100 * 1.03 ** i for i in range(61)]) == 7.5


def test_fetch_quotes_new_fields_on_success():
    result = {q["id"]: q for q in quotes.fetch_quotes({}, get=ok_get)}
    assert result["BTC"]["market_tz"] == "UTC"
    assert result["BTC"]["always_open"] is True
    assert result["KS11"]["market_tz"] == "Asia/Seoul"
    assert result["KS11"]["always_open"] is False
    assert result["KS11"]["big_move_pct"] == 2.0


def failing_get(url, **kwargs):
    raise requests.ConnectionError("down")


def test_stale_without_previous_big_move_uses_default_and_table_fields():
    prev = {"KS11": {"id": "KS11", "name": "코스피", "price": 1.0}}
    result = {q["id"]: q for q in quotes.fetch_quotes(prev, get=failing_get)}
    assert result["KS11"]["stale"] is True
    assert result["KS11"]["big_move_pct"] == 2.0
    assert result["KS11"]["market_tz"] == "Asia/Seoul"
    assert result["KS11"]["always_open"] is False


def test_stale_keeps_previous_big_move():
    prev = {"KS11": {"id": "KS11", "name": "코스피", "price": 1.0, "big_move_pct": 4.3}}
    result = {q["id"]: q for q in quotes.fetch_quotes(prev, get=failing_get)}
    assert result["KS11"]["big_move_pct"] == 4.3


def test_failed_from_start_has_default_and_table_fields():
    result = {q["id"]: q for q in quotes.fetch_quotes({}, get=failing_get)}
    assert result["BTC"]["big_move_pct"] == 5.0
    assert result["BTC"]["market_tz"] == "UTC"
    assert result["BTC"]["always_open"] is True
    assert result["DXY"]["big_move_pct"] == 0.7


# ---- v1.0: MDD 구간, 남길 점, max_depths ----

import math


def s_series():
    return [round(100 + 40 * math.sin(i / 37) + 0.08 * i - (30 if 700 <= i < 760 else 0), 2) for i in range(1200)]


@pytest.mark.parametrize("closes, expected", [
    ([100, 110, 105, 120, 90, 95, 121, 80, 100], (6, 7, None, -33.9)),
    ([100, 101, 102, 103], None),
    ([100, 120, 60, 80, 110], (1, 2, None, -50.0)),
    ([100, 120, 60, 80, 125], (1, 2, 4, -50.0)),
    ([100, 120, 90, 120, 100, 130], (1, 2, 3, -25.0)),
    ([100, 90], (0, 1, None, -10.0)),
    ([50], None),
])
def test_drawdown_episode_fixed_values(closes, expected):
    assert quotes.drawdown_episode(closes) == expected


def test_s_series_fixed_values():
    s = s_series()
    assert quotes._bucket_size(len(s)) == 5
    assert len(quotes.thin_indices(s)) == 482
    assert quotes.drawdown_episode(s) == (61, 171, 257, -49.0)
    picks = quotes.max_picks(s, [61, 171, 257])
    assert len(picks) == 483
    assert picks[:10] == [0, 0, 4, 5, 9, 10, 14, 15, 19, 20]
    assert picks[-5:] == [1190, 1194, 1195, 1199, 1199]
    assert picks == sorted(picks)


def test_max_picks_keeps_duplicates_and_ignores_present_extras():
    assert quotes.max_picks([1.0, 3.0, 2.0], [1, 1]) == [0, 1, 2]


def s_payload(closes=None, tz="Asia/Seoul", start=1100000000):
    closes = s_series() if closes is None else closes
    return {"chart": {"result": [{
        "meta": {"gmtoffset": 32400, "exchangeTimezoneName": tz},
        "timestamp": [start + i * 86400 for i in range(len(closes))],
        "indicators": {"quote": [{"close": closes}]},
    }]}}


def test_max_fields_depths_align_with_closes_and_include_episode():
    f = quotes.max_fields(s_payload(), True, "")
    assert len(f["max_depths"]) == len(f["max_closes"]) == len(f["max_dates"]) == 483
    assert min(f["max_depths"]) == f["mdd"] == -49.0
    assert f["max_depths"][f["max_dates"].index(f["mdd_trough_date"])] == -49.0
    assert f["mdd_peak_date"] in f["max_dates"]
    assert f["mdd_recovery_date"] in f["max_dates"]
    assert f["max_depths"][f["max_dates"].index(f["mdd_peak_date"])] == 0.0


def test_max_fields_old_app_fields_unchanged_by_extra_points():
    f = quotes.max_fields(s_payload(), True, "")
    depths = quotes.drawdowns(s_series())
    assert f["max_drawdowns"] == [round(min(depths[i:i + 5]), 1) for i in range(0, 1200, 5)]
    assert f["peak"] == round(max(s_series()), 2)


def test_max_fields_depths_come_from_full_daily_series_not_thinned():
    f = quotes.max_fields(s_payload(), True, "")
    full = quotes.drawdowns(s_series())
    picks = quotes.max_picks(s_series(), [61, 171, 257])
    assert f["max_depths"] == [round(full[i], 1) for i in picks]


def test_max_fields_unrecovered_has_no_recovery_date():
    f = quotes.max_fields(max_payload([100.0, 120.0, 60.0, 80.0]), True, "")
    assert f["mdd_peak_date"] and f["mdd_trough_date"]
    assert f["mdd_recovery_date"] is None


def test_max_fields_no_drawdown_episode_has_null_dates():
    f = quotes.max_fields(max_payload([100.0, 101.0, 102.0]), True, "")
    assert f["max_depths"] == [0.0, 0.0, 0.0]
    assert f["mdd"] == 0.0
    assert f["mdd_peak_date"] is f["mdd_trough_date"] is f["mdd_recovery_date"] is None


def test_max_fields_no_drawdown_indicator_has_empty_depths_and_dates():
    f = quotes.max_fields(s_payload(), False, "")
    assert f["max_depths"] == []
    assert f["mdd_peak_date"] is f["mdd_trough_date"] is f["mdd_recovery_date"] is None
    assert len(f["max_closes"]) == 482


def test_event_marks_have_date_of_deepest_day():
    dates = [date(1998, 1, 1), date(1998, 6, 16), date(1998, 8, 1)]
    marks = quotes.event_marks("KS11", dates, [-10.0, -64.7, -30.0], size=1)
    assert marks == [{"label": "외환위기", "index": 1, "depth": -64.7, "date": "1998-06-16"}]


def test_ks11_event_date_is_in_max_dates_and_depth_matches():
    closes = [100.0] * 600
    closes[300] = 50.0
    start = 867715200  # 1997-07-01 00:00 UTC
    f = quotes.max_fields(s_payload(closes, start=start), True, "KS11")
    ev = f["max_events"][0]
    assert ev["label"] == "외환위기"
    assert ev["date"] in f["max_dates"]
    assert f["max_depths"][f["max_dates"].index(ev["date"])] == ev["depth"] == -50.0
    assert ev["index"] == 300 // quotes._bucket_size(600)


def test_max_failure_passes_previous_new_fields():
    def get(url, **kwargs):
        if "period1=" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "max_closes": [1.0, 2.0, 3.0], "max_dates": ["2000-01-01"] * 3,
                     "max_depths": [0.0, -5.0, -1.0], "mdd_peak_date": "2000-01-01",
                     "mdd_trough_date": "2000-01-01", "mdd_recovery_date": None}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["max_depths"] == [0.0, -5.0, -1.0]
    assert ks11["mdd_peak_date"] == "2000-01-01" and ks11["mdd_trough_date"] == "2000-01-01"
    assert ks11["mdd_recovery_date"] is None


def test_max_failure_clears_previous_depths_when_length_differs():
    def get(url, **kwargs):
        if "period1=" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    prev = {"KS11": {"id": "KS11", "max_closes": [1.0, 2.0, 3.0], "max_dates": ["2000-01-01"] * 3,
                     "max_depths": [0.0, -5.0]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=get) if q["id"] == "KS11")
    assert ks11["max_depths"] == []


def test_max_failure_without_previous_has_empty_new_fields():
    def get(url, **kwargs):
        if "period1=" in url:
            raise requests.ConnectionError("down")
        return FakeResponse(FIXTURE)

    ks11 = next(q for q in quotes.fetch_quotes({}, get=get) if q["id"] == "KS11")
    assert ks11["max_depths"] == []
    assert ks11["mdd_peak_date"] is ks11["mdd_trough_date"] is ks11["mdd_recovery_date"] is None


def test_failed_from_start_has_empty_new_fields():
    items = quotes.fetch_quotes({}, get=failing_get)
    for q in items:
        assert q["max_depths"] == []
        assert q["mdd_peak_date"] is q["mdd_trough_date"] is q["mdd_recovery_date"] is None


def test_stale_from_old_cache_without_new_fields_gets_empty_defaults():
    prev = {"KS11": {"id": "KS11", "name": "코스피", "price": 1.0, "max_closes": [1.0]}}
    ks11 = next(q for q in quotes.fetch_quotes(prev, get=failing_get) if q["id"] == "KS11")
    assert ks11["stale"] is True
    assert ks11["max_depths"] == []
    assert ks11["mdd_peak_date"] is ks11["mdd_trough_date"] is ks11["mdd_recovery_date"] is None
