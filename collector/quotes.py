"""Yahoo 차트 API에서 시세를 가져온다."""

import logging
import time
from urllib.parse import quote

import requests

from collector.config import HTTP_TIMEOUT, RETRIES, SYMBOLS

log = logging.getLogger(__name__)

CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=10d&interval=1d"
HEADERS = {"User-Agent": "Mozilla/5.0"}


def parse_chart(payload: dict) -> tuple[float, float, list[float]]:
    """(현재값, 전일 종가, 최근 5개 종가)를 돌려준다."""
    result = payload["chart"]["result"][0]
    closes = [c for c in result["indicators"]["quote"][0]["close"] if c is not None]
    price = result["meta"].get("regularMarketPrice") or closes[-1]
    # 마지막 봉이 오늘(또는 마지막 거래일)이므로 그 앞 봉이 전일 종가다
    prev_close = closes[-2]
    return price, prev_close, closes[-5:]


def _get_chart(symbol: str, get) -> dict:
    url = CHART_URL.format(symbol=quote(symbol))
    for attempt in range(RETRIES):
        try:
            resp = get(url, headers=HEADERS, timeout=HTTP_TIMEOUT)
            resp.raise_for_status()
            return resp.json()
        except requests.RequestException:
            if attempt == RETRIES - 1:
                raise
            time.sleep(1)


def fetch_quotes(prev: dict[str, dict], get=requests.get) -> list[dict]:
    """설정된 시세를 모두 가져온다. 실패한 항목은 직전 값에 stale 표시를 붙인다."""
    items = []
    for id_, name, symbol in SYMBOLS:
        try:
            price, prev_close, history = parse_chart(_get_chart(symbol, get))
            change = price - prev_close
            items.append({
                "id": id_, "name": name, "price": price,
                "change": round(change, 2),
                "change_pct": round(change / prev_close * 100, 2),
                "history": history, "stale": False,
            })
        except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as e:
            log.warning("%s 시세 실패: %s", symbol, e)
            old = prev.get(id_)
            if old:
                items.append({**old, "stale": True})
            else:
                items.append({"id": id_, "name": name, "price": None, "change": None,
                              "change_pct": None, "history": [], "stale": True})
    return items
