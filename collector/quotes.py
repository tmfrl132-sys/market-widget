"""Yahoo 차트 API에서 시세를 가져온다."""

import logging
import time
from datetime import datetime, timezone
from urllib.parse import quote

import requests

from collector.config import HTTP_TIMEOUT, RETRIES, SYMBOLS

log = logging.getLogger(__name__)

CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range={range}&interval={interval}"
OLD_APP_CHART_POINTS = 63  # v0.3.0 이하 앱이 읽는 chart(3개월)
HEADERS = {"User-Agent": "Mozilla/5.0"}


def parse_chart(payload: dict) -> tuple[float, float, list[float]]:
    """(현재값, 전일 종가, 최근 5개 종가)를 돌려준다."""
    result = payload["chart"]["result"][0]
    meta = result["meta"]
    bars = [(ts, c) for ts, c in zip(result["timestamp"], result["indicators"]["quote"][0]["close"])
            if c is not None]
    closes = [c for _, c in bars]
    price = meta.get("regularMarketPrice") or closes[-1]
    # 마지막 유효 봉이 시세 시각과 같은 날이면 그 앞 봉이 전일 종가다.
    # 오늘 봉이 아직 비어 있으면(null) 마지막 유효 봉이 곧 전일 종가다.
    prev_close = closes[-2]
    market_time = meta.get("regularMarketTime")
    if market_time is not None:
        offset = meta.get("gmtoffset", 0)
        if _local_date(bars[-1][0], offset) != _local_date(market_time, offset):
            prev_close = closes[-1]
    return price, prev_close, closes[-5:]


def chart_closes(payload: dict) -> list[float]:
    """위젯 차트용 종가 (null 제외, 소수 둘째 자리)."""
    closes = payload["chart"]["result"][0]["indicators"]["quote"][0]["close"]
    return [round(c, 2) for c in closes if c is not None]


def _local_date(ts: int, offset: int):
    return datetime.fromtimestamp(ts + offset, timezone.utc).date()


def _get_chart(symbol: str, get, range_: str = "1y", interval: str = "1d") -> dict:
    url = CHART_URL.format(symbol=quote(symbol), range=range_, interval=interval)
    for attempt in range(RETRIES):
        try:
            resp = get(url, headers=HEADERS, timeout=HTTP_TIMEOUT)
            resp.raise_for_status()
            return resp.json()
        except requests.RequestException:
            if attempt == RETRIES - 1:
                raise
            time.sleep(1)


def _fetch_weekly(symbol: str, get, prev_item: dict) -> list[float]:
    """5년 주봉. 실패해도 시세는 살리고 직전 주봉을 그대로 쓴다."""
    try:
        return chart_closes(_get_chart(symbol, get, "5y", "1wk"))
    except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as e:
        log.warning("%s 주봉 실패: %s", symbol, e)
        return prev_item.get("weekly", [])


def fetch_quotes(prev: dict[str, dict], get=requests.get) -> list[dict]:
    """설정된 시세를 모두 가져온다. 실패한 항목은 직전 값에 stale 표시를 붙인다."""
    items = []
    for id_, name, symbol in SYMBOLS:
        try:
            payload = _get_chart(symbol, get)
            price, prev_close, history = parse_chart(payload)
            change = price - prev_close
            daily = chart_closes(payload)
            items.append({
                "id": id_, "name": name, "price": price,
                "change": round(change, 2),
                "change_pct": round(change / prev_close * 100, 2),
                "history": history,
                "chart": daily[-OLD_APP_CHART_POINTS:],
                "daily": daily,
                "weekly": _fetch_weekly(symbol, get, prev.get(id_, {})),
                "stale": False,
            })
        except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as e:
            log.warning("%s 시세 실패: %s", symbol, e)
            old = prev.get(id_)
            if old:
                items.append({**old, "stale": True})
            else:
                items.append({"id": id_, "name": name, "price": None, "change": None,
                              "change_pct": None, "history": [], "chart": [],
                              "daily": [], "weekly": [], "stale": True})
    return items
