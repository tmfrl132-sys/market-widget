from datetime import datetime
from zoneinfo import ZoneInfo

from collector import build

SEOUL = ZoneInfo("Asia/Seoul")


def kst(y, m, d, hh, mm):
    return datetime(y, m, d, hh, mm, tzinfo=SEOUL)


def test_should_run_weekend_window():
    # 2026-10-31은 토요일
    assert build.should_run(kst(2026, 10, 31, 7, 59)) is True
    assert build.should_run(kst(2026, 10, 31, 8, 0)) is False
    assert build.should_run(kst(2026, 11, 1, 15, 0)) is False
    assert build.should_run(kst(2026, 11, 2, 6, 59)) is False
    assert build.should_run(kst(2026, 11, 2, 7, 0)) is True


def test_should_run_converts_utc_to_seoul():
    # UTC 금 23:00 = 서울 토 08:00
    assert build.should_run(datetime(2026, 10, 30, 23, 0, tzinfo=ZoneInfo("UTC"))) is False


def fake_quotes(prev):
    return [{"id": f"Q{i}", "name": "x", "price": 1.0, "change": 0.0, "change_pct": 0.0,
             "history": [1.0], "stale": False} for i in range(10)]


def test_build_data_shape():
    seen = {}

    def quotes(prev):
        seen["prev"] = prev
        return fake_quotes(prev)

    prev = {"quotes": [{"id": "KS11", "price": 1.0}], "events": []}
    data = build.build_data(kst(2026, 10, 30, 10, 30), prev,
                            fetch_quotes=quotes, fetch_events=lambda today: [])
    assert data["updated_at"] == "2026-10-30T10:30:00+09:00"
    assert len(data["quotes"]) == 10
    assert data["events"] == []
    assert seen["prev"] == {"KS11": {"id": "KS11", "price": 1.0}}


def test_build_without_previous():
    data = build.build_data(kst(2026, 10, 30, 10, 30), None,
                            fetch_quotes=fake_quotes, fetch_events=lambda today: [])
    assert len(data["quotes"]) == 10
