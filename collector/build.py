"""data.json을 만든다. GitHub Actions가 30분마다 실행한다."""

import argparse
import json
import logging
import os
from datetime import datetime, time
from pathlib import Path
from zoneinfo import ZoneInfo

import requests

from collector import events, quotes
from collector.config import HTTP_TIMEOUT

SEOUL = ZoneInfo("Asia/Seoul")
PAGES_URL = "https://tmfrl132-sys.github.io/market-widget/data.json"

log = logging.getLogger(__name__)


def should_run(now: datetime) -> bool:
    """서울 시간 토 08:00 ~ 월 07:00에는 수집하지 않는다."""
    now = now.astimezone(SEOUL)
    weekday, t = now.weekday(), now.time()
    if weekday == 5:  # 토
        return t < time(8, 0)
    if weekday == 6:  # 일
        return False
    if weekday == 0:  # 월
        return t >= time(7, 0)
    return True


def default_events(today) -> list[dict]:
    api_key = os.environ.get("FRED_API_KEY", "")
    fred = events.fetch_fred_events(api_key, today) if api_key else []
    return events.merge_events(fred, events.load_fixed_events(events.SCHEDULE_PATH, today))


def build_data(now: datetime, prev: dict | None,
               fetch_quotes=quotes.fetch_quotes, fetch_events=default_events) -> dict:
    now = now.astimezone(SEOUL)
    prev_quotes = {q["id"]: q for q in (prev or {}).get("quotes", [])}
    return {
        "updated_at": now.isoformat(timespec="seconds"),
        "quotes": fetch_quotes(prev_quotes),
        "events": fetch_events(now.date()),
    }


def load_previous() -> dict | None:
    try:
        resp = requests.get(PAGES_URL, timeout=HTTP_TIMEOUT)
        resp.raise_for_status()
        return resp.json()
    except (requests.RequestException, ValueError) as e:
        log.warning("직전 data.json 없음: %s", e)
        return None


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    prev = load_previous()
    now = datetime.now(SEOUL)
    if prev is not None and not args.force and not should_run(now):
        data = prev
        log.info("주말 구간이라 직전 값을 그대로 씁니다")
    else:
        data = build_data(now, prev)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    log.info("%s 저장 (시세 %d, 일정 %d)", args.out, len(data["quotes"]), len(data["events"]))


if __name__ == "__main__":
    main()
