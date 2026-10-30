"""경제지표 발표 일정: FRED 발표일 API와 고정 일정표."""

import json
import logging
from datetime import date, timedelta
from pathlib import Path

import requests

from collector.config import EVENT_WINDOW_DAYS, FRED_RELEASES, HTTP_TIMEOUT

log = logging.getLogger(__name__)

FRED_URL = "https://api.stlouisfed.org/fred/release/dates"
SCHEDULE_PATH = Path(__file__).parent / "schedule.json"


def _in_window(day: str, today: date) -> bool:
    return today <= date.fromisoformat(day) <= today + timedelta(days=EVENT_WINDOW_DAYS)


def fetch_fred_events(api_key: str, today: date, get=requests.get) -> list[dict]:
    """FRED에서 앞으로 31일 안의 미국 지표 발표일을 가져온다. 실패하면 빈 목록."""
    items = []
    try:
        for release_id, title in FRED_RELEASES.items():
            params = {
                "release_id": release_id, "api_key": api_key, "file_type": "json",
                "realtime_start": today.isoformat(),
                "include_release_dates_with_no_data": "true",
                "sort_order": "asc", "limit": 10,
            }
            resp = get(FRED_URL, params=params, timeout=HTTP_TIMEOUT)
            resp.raise_for_status()
            for row in resp.json()["release_dates"]:
                if _in_window(row["date"], today):
                    items.append({"date": row["date"], "title": title, "country": "US"})
    except (requests.RequestException, KeyError, ValueError) as e:
        log.warning("FRED 일정 실패: %s", str(e).replace(api_key, "***"))
        return []
    return items


def load_fixed_events(path: Path, today: date) -> list[dict]:
    """고정 일정표(FOMC·금통위)에서 앞으로 31일 안의 일정만 고른다."""
    items = json.loads(path.read_text(encoding="utf-8"))
    return [e for e in items if _in_window(e["date"], today)]


def merge_events(*lists: list[dict]) -> list[dict]:
    """날짜순으로 합치고 (날짜, 제목)이 같은 중복을 없앤다."""
    seen = {}
    for items in lists:
        for e in items:
            seen.setdefault((e["date"], e["title"]), e)
    return sorted(seen.values(), key=lambda e: (e["date"], e["title"]))
