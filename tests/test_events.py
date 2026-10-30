import json
import logging
from datetime import date

import requests

from collector import events

TODAY = date(2026, 10, 30)


class FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


def fred_get(dates):
    def get(url, params=None, **kwargs):
        return FakeResponse({"release_dates": [{"release_id": params["release_id"], "date": d}
                                               for d in dates]})
    return get


def test_fred_keeps_only_next_31_days():
    get = fred_get(["2026-10-29", "2026-10-30", "2026-11-30", "2026-12-01"])
    result = events.fetch_fred_events("KEY", TODAY, get=get)
    cpi = [e for e in result if e["title"] == "미국 CPI"]
    assert [e["date"] for e in cpi] == ["2026-10-30", "2026-11-30"]
    assert all(e["country"] == "US" for e in result)


def test_fred_failure_returns_empty_and_hides_key(caplog):
    def failing_get(url, params=None, **kwargs):
        raise requests.ConnectionError(f"failed url ?api_key={params['api_key']}")

    with caplog.at_level(logging.WARNING):
        result = events.fetch_fred_events("SECRET123", TODAY, get=failing_get)
    assert result == []
    assert "SECRET123" not in caplog.text
    assert "***" in caplog.text


def test_fixed_events_filtered_by_window(tmp_path):
    path = tmp_path / "schedule.json"
    path.write_text(json.dumps([
        {"date": "2026-10-29", "title": "지난 일정", "country": "KR"},
        {"date": "2026-11-12", "title": "금통위", "country": "KR"},
        {"date": "2026-12-09", "title": "FOMC", "country": "US"},
    ], ensure_ascii=False), encoding="utf-8")
    assert [e["title"] for e in events.load_fixed_events(path, TODAY)] == ["금통위"]


def test_merge_sorts_and_dedupes():
    a = [{"date": "2026-11-13", "title": "미국 CPI", "country": "US"}]
    b = [{"date": "2026-11-05", "title": "FOMC", "country": "US"},
         {"date": "2026-11-13", "title": "미국 CPI", "country": "US"}]
    assert [e["date"] for e in events.merge_events(a, b)] == ["2026-11-05", "2026-11-13"]


def test_bundled_schedule_is_valid():
    items = json.loads(events.SCHEDULE_PATH.read_text(encoding="utf-8"))
    for item in items:
        date.fromisoformat(item["date"])
        assert item["country"] in ("US", "KR")
