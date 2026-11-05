"""Yahoo 차트 API에서 시세를 가져온다."""

import logging
import time
from datetime import datetime, timezone
from urllib.parse import quote
from zoneinfo import ZoneInfo

import requests

from collector.config import (EVENT_MIN_DEPTH, HTTP_TIMEOUT, MARKET_EVENTS, MAX_POINTS, NO_DRAWDOWN,
                              RETRIES, SYMBOLS)

log = logging.getLogger(__name__)

CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range={range}&interval={interval}"
# range=max는 간격을 3개월까지 멋대로 늘리고, 주봉·월봉에는 잘못된 값이 섞여 있어 날짜를 지정해 일봉을 받는다
MAX_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?period1=0&period2={now}&interval=1d"
OLD_APP_CHART_POINTS = 63  # v0.3.0 이하 앱이 읽는 chart(3개월)
HEADERS = {"User-Agent": "Mozilla/5.0"}


def parse_chart(payload: dict) -> tuple[float, float | None, list[float]]:
    """(현재값, 전일 종가, 최근 5개 종가)를 돌려준다. 전일 종가를 알 수 없으면 None이다."""
    result = payload["chart"]["result"][0]
    meta = result["meta"]
    rows = list(zip(result["timestamp"], result["indicators"]["quote"][0]["close"]))
    closes = [c for _, c in rows if c is not None]
    price = meta.get("regularMarketPrice") or closes[-1]
    market_time = meta.get("regularMarketTime")
    if market_time is None:
        prev_close = closes[-2]
    else:
        # 마지막 봉이 시세 시각과 같은 날이면 그 앞 봉이 전일 종가이고, 아니면 마지막 봉이 전일 종가다.
        # 그 봉이 비어 있으면 더 앞 종가와 비교하지 않는다. 전전일 종가와 비교하면 등락률이 틀린다.
        offset = meta.get("gmtoffset", 0)
        last_is_today = _local_date(rows[-1][0], offset) == _local_date(market_time, offset)
        prev_close = rows[-2][1] if last_is_today else rows[-1][1]
    return price, prev_close, closes[-5:]


def chart_closes(payload: dict) -> list[float]:
    """위젯 차트용 종가 (null 제외, 소수 둘째 자리)."""
    closes = payload["chart"]["result"][0]["indicators"]["quote"][0]["close"]
    return [round(c, 2) for c in closes if c is not None]


def chart_dates(payload: dict) -> list[str]:
    """chart_closes와 같은 봉의 시장 현지 날짜(YYYY-MM-DD). null 종가 봉은 날짜도 뺀다."""
    result = payload["chart"]["result"][0]
    to_date = _bar_date_fn(result["meta"])
    closes = result["indicators"]["quote"][0]["close"]
    return [to_date(ts).isoformat() for ts, c in zip(result["timestamp"], closes) if c is not None]


def _local_date(ts: int, offset: int):
    return datetime.fromtimestamp(ts + offset, timezone.utc).date()


def _bar_date_fn(meta: dict):
    """봉 시각 -> 시장 현지 날짜 함수. gmtoffset은 지금의 오프셋 하나뿐이라 서머타임 기간의 과거 봉이 하루 어긋난다.
    그래서 exchangeTimezoneName으로 봉마다 계산하고, 이름이 없거나 모르는 시간대면 gmtoffset으로 대체한다."""
    offset = meta.get("gmtoffset", 0)
    name = meta.get("exchangeTimezoneName")
    zone = None
    if isinstance(name, str) and name:
        try:
            zone = ZoneInfo(name)
        except Exception:  # 모르는 이름, 잘못된 형식, 시간대 자료 없음 모두 대체 경로로 간다
            log.warning("알 수 없는 시간대 %r, gmtoffset으로 대체", name)
    if zone is None:
        return lambda ts: _local_date(ts, offset)
    return lambda ts: datetime.fromtimestamp(ts, zone).date()


def drawdowns(closes: list[float]) -> list[float]:
    """날마다 그때까지의 고점 대비 낙폭(%). 마이너스 유가 때문에 -100 아래는 -100으로 막는다."""
    peak = closes[0]
    result = []
    for close in closes:
        peak = max(peak, close)
        result.append(max(-100.0, (close / peak - 1) * 100) if peak > 0 else 0.0)
    return result


def _bucket_size(count: int) -> int:
    """묶음 크기. 묶음마다 2점에 처음·마지막 값까지 더해도 MAX_POINTS를 넘지 않게 한다."""
    if count <= MAX_POINTS:
        return 1
    return -(-count // ((MAX_POINTS - 2) // 2))


def thin_indices(closes: list[float]) -> list[int]:
    """차트용으로 남길 점의 위치. 묶음의 최저·최고를 시간 순서로 남겨 고점과 바닥이 사라지지 않게 하고, 처음·마지막 점은 지킨다.
    처음·마지막 점이 묶음 점과 겹치면 같은 위치가 두 번 들어간다."""
    size = _bucket_size(len(closes))
    if size == 1:
        return list(range(len(closes)))
    picks = [0]
    for start in range(0, len(closes), size):
        chunk = closes[start:start + size]
        low, high = chunk.index(min(chunk)), chunk.index(max(chunk))
        picks += [start + i for i in sorted({low, high})]
    picks.append(len(closes) - 1)
    return picks


def thin_closes(closes: list[float]) -> list[float]:
    """thin_indices가 고른 위치의 값."""
    return [closes[i] for i in thin_indices(closes)]


def event_marks(id_: str, dates: list, depths: list[float], size: int) -> list[dict]:
    """낙폭 차트에 표시할 사건. index는 max_drawdowns 안의 위치다."""
    marks = []
    for label, start, end, symbols in MARKET_EVENTS:
        if id_ not in symbols:
            continue
        window = [i for i, d in enumerate(dates) if start <= d.isoformat() <= end]
        if not window:
            continue
        deepest = min(window, key=lambda i: depths[i])
        if depths[deepest] <= EVENT_MIN_DEPTH:
            marks.append({"label": label, "index": deepest // size, "depth": round(depths[deepest], 1)})
    return marks


def max_fields(payload: dict, with_drawdown: bool, id_: str = "") -> dict:
    """전체 기간 차트와 고점·MDD. 낙폭은 일봉 전체로 계산하고 차트 점만 줄인다."""
    result = payload["chart"]["result"][0]
    bars = [(ts, c) for ts, c in zip(result["timestamp"], result["indicators"]["quote"][0]["close"])
            if c is not None]
    closes = [c for _, c in bars]
    offset = result["meta"].get("gmtoffset", 0)  # max_start, event_marks는 기존대로 이 오프셋을 쓴다
    to_date = _bar_date_fn(result["meta"])
    size = _bucket_size(len(closes))
    chunks = range(0, len(closes), size)
    picks = thin_indices(closes)
    fields = {
        "max_closes": [round(closes[i], 2) for i in picks],
        "max_dates": [to_date(bars[i][0]).isoformat() for i in picks],
        "max_drawdowns": [],
        "max_events": [],
        "max_start": _local_date(bars[0][0], offset).year,
        "peak": None,
        "mdd": None,
    }
    if with_drawdown:
        depths = drawdowns(closes)
        # 묶음의 가장 깊은 값을 써서 MDD 바닥이 차트에서 사라지지 않게 한다
        fields["max_drawdowns"] = [round(min(depths[i:i + size]), 1) for i in chunks]
        fields["max_events"] = event_marks(id_, [_local_date(ts, offset) for ts, _ in bars], depths, size)
        fields["peak"] = round(max(closes), 2)
        fields["mdd"] = round(min(depths), 1)
    return fields


def _get_chart(symbol: str, get, range_: str = "1y", interval: str = "1d") -> dict:
    return _get_json(CHART_URL.format(symbol=quote(symbol), range=range_, interval=interval), get)


def _get_json(url: str, get) -> dict:
    for attempt in range(RETRIES):
        try:
            resp = get(url, headers=HEADERS, timeout=HTTP_TIMEOUT)
            resp.raise_for_status()
            return resp.json()
        except requests.RequestException:
            if attempt == RETRIES - 1:
                raise
            time.sleep(1)


def _kept(prev_item: dict, values_key: str, dates_key: str) -> tuple[list, list]:
    """직전 값과 날짜. 날짜가 없거나 길이가 어긋나면 날짜만 비워 둔다(위젯은 날짜가 없으면 눈금을 안 그린다)."""
    values = prev_item.get(values_key, [])
    dates = prev_item.get(dates_key, [])
    return values, (dates if len(dates) == len(values) else [])


def _fetch_weekly(symbol: str, get, prev_item: dict) -> dict:
    """5년 주봉. 실패해도 시세는 살리고 직전 주봉과 날짜를 그대로 쓴다."""
    try:
        payload = _get_chart(symbol, get, "5y", "1wk")
        return {"weekly": chart_closes(payload), "weekly_dates": chart_dates(payload)}
    except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as e:
        log.warning("%s 주봉 실패: %s", symbol, e)
        values, dates = _kept(prev_item, "weekly", "weekly_dates")
        return {"weekly": values, "weekly_dates": dates}


def _fetch_max(id_: str, symbol: str, get, prev_item: dict) -> dict:
    """전체 기간. 실패해도 시세는 살리고 직전 값을 그대로 쓴다."""
    try:
        url = MAX_URL.format(symbol=quote(symbol), now=int(time.time()))
        return max_fields(_get_json(url, get), id_ not in NO_DRAWDOWN, id_)
    except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as e:
        log.warning("%s 전체 기간 실패: %s", symbol, e)
        values, dates = _kept(prev_item, "max_closes", "max_dates")
        return {
            "max_closes": values,
            "max_dates": dates,
            "max_drawdowns": prev_item.get("max_drawdowns", []),
            "max_events": prev_item.get("max_events", []),
            "max_start": prev_item.get("max_start"),
            "peak": prev_item.get("peak"),
            "mdd": prev_item.get("mdd"),
        }


def fetch_quotes(prev: dict[str, dict], get=requests.get) -> list[dict]:
    """설정된 시세를 모두 가져온다. 실패한 항목은 직전 값에 stale 표시를 붙인다."""
    items = []
    for id_, name, symbol in SYMBOLS:
        try:
            payload = _get_chart(symbol, get)
            price, prev_close, history = parse_chart(payload)
            change = None if prev_close is None else round(price - prev_close, 2)
            change_pct = None if prev_close is None else round((price - prev_close) / prev_close * 100, 2)
            daily = chart_closes(payload)
            items.append({
                "id": id_, "name": name, "price": price,
                "change": change,
                "change_pct": change_pct,
                "history": history,
                "chart": daily[-OLD_APP_CHART_POINTS:],
                "daily": daily,
                "daily_dates": chart_dates(payload),
                **_fetch_weekly(symbol, get, prev.get(id_, {})),
                **_fetch_max(id_, symbol, get, prev.get(id_, {})),
                "stale": False,
            })
        except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as e:
            log.warning("%s 시세 실패: %s", symbol, e)
            old = prev.get(id_)
            if old:
                items.append({"daily_dates": [], "weekly_dates": [], "max_dates": [], **old, "stale": True})
            else:
                items.append({"id": id_, "name": name, "price": None, "change": None,
                              "change_pct": None, "history": [], "chart": [],
                              "daily": [], "daily_dates": [], "weekly": [], "weekly_dates": [],
                              "max_closes": [], "max_dates": [], "max_drawdowns": [],
                              "max_events": [], "max_start": None, "peak": None, "mdd": None, "stale": True})
    return items
