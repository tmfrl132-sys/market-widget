"""경제지표 발표 일정: FRED 발표일 API와 고정 일정표."""

import json
import logging
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

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


# 발표 시각(v1.8). 일정 제목 -> (시간대, 현지 시각). 기관이 매번 같은 시각에 발표한다.
# 미국 지표는 BLS·BEA 발표 08:30 ET, FOMC 성명은 14:00 ET, 한국은행 기준금리 결정은 10:00 KST(관행 기준, 확인 필요).
EVENT_TIMES: dict[str, tuple[str, time]] = {
    "미국 CPI": ("America/New_York", time(8, 30)),
    "미국 고용보고서": ("America/New_York", time(8, 30)),
    "미국 PCE": ("America/New_York", time(8, 30)),
    "미국 GDP": ("America/New_York", time(8, 30)),
    "FOMC 금리 결정": ("America/New_York", time(14, 0)),
    "한국은행 금리 결정": ("Asia/Seoul", time(10, 0)),
}
SEOUL = ZoneInfo("Asia/Seoul")


def with_times(items: list[dict]) -> list[dict]:
    """일정에 서울 시각 "kst"(ISO, 예: 2026-12-10T22:30:00+09:00)를 붙인다. 시각을 모르는 일정은 그대로 둔다.
    date는 현지(발표 기관) 날짜 그대로라 옛 앱도 같은 날짜로 보인다. 서머타임은 zoneinfo가 날짜마다 맞춘다."""
    out = []
    for e in items:
        rule = EVENT_TIMES.get(e["title"])
        if rule is None:
            out.append(e)
            continue
        zone, at = rule
        local = datetime.combine(date.fromisoformat(e["date"]), at, ZoneInfo(zone))
        out.append({**e, "kst": local.astimezone(SEOUL).isoformat(timespec="seconds")})
    return out
