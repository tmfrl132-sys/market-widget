"""수집 대상 목록과 공용 상수."""

# (id, 표시 이름, Yahoo 심볼) — 위젯 타일 순서
SYMBOLS: list[tuple[str, str, str]] = [
    ("KS11", "코스피", "^KS11"),
    ("KQ11", "코스닥", "^KQ11"),
    ("GSPC", "S&P500", "^GSPC"),
    ("NDX", "나스닥100", "^NDX"),
    ("VIX", "VIX", "^VIX"),
    ("KRW", "원/달러", "KRW=X"),
    ("DXY", "달러인덱스", "DX-Y.NYB"),
    ("TNX", "미 10년물", "^TNX"),
    ("CL", "WTI", "CL=F"),
    ("GC", "금", "GC=F"),
    ("BTC", "비트코인", "BTC-USD"),
]

# 낙폭이 투자 손실을 뜻하지 않는 지표. 고점 대비 낙폭과 MDD를 만들지 않는다.
NO_DRAWDOWN = {"KRW", "TNX", "VIX", "DXY"}

# 전체 기간 차트에 보내는 최대 점 수
MAX_POINTS = 500

STOCKS = {"KS11", "KQ11", "GSPC", "NDX"}

# 낙폭 차트에 표시할 사건: (이름, 시작일, 끝일, 표시할 지표). 구간 안의 가장 깊은 지점에 붙인다.
MARKET_EVENTS: list[tuple[str, str, str, set[str]]] = [
    ("외환위기", "1997-07-01", "1999-01-31", {"KS11"}),
    ("닷컴버블", "2000-03-01", "2003-03-31", STOCKS),
    ("서브프라임", "2007-10-01", "2009-06-30", STOCKS | {"CL", "GC"}),
    ("코로나", "2020-02-01", "2020-04-30", STOCKS | {"CL", "GC", "BTC"}),
]

# 큰 움직임 강조 기준의 기본값(%). 실제 기준 = max(기본값, BIG_MOVE_FACTOR x 최근 BIG_MOVE_DAYS일 하루 등락폭 중앙값)
BIG_MOVE_BASE: dict[str, float] = {
    "KS11": 2.0, "KQ11": 2.5, "GSPC": 1.5, "NDX": 2.0, "VIX": 10.0,
    "KRW": 1.0, "DXY": 0.7, "TNX": 3.5, "CL": 4.0, "GC": 1.5, "BTC": 5.0,
}
BIG_MOVE_DAYS = 60
BIG_MOVE_FACTOR = 2.5

# 지표별 시장 시간대(Yahoo exchangeTimezoneName 실측값). 위젯의 장 상태 계산에 쓴다.
MARKET_TZ: dict[str, str] = {
    "KS11": "Asia/Seoul", "KQ11": "Asia/Seoul",
    "GSPC": "America/New_York", "NDX": "America/New_York", "DXY": "America/New_York", "CL": "America/New_York",
    "GC": "America/New_York", "KRW": "Europe/London",
    "TNX": "America/Chicago", "VIX": "America/Chicago", "BTC": "UTC",
}

# 늘 거래하는 지표
ALWAYS_OPEN = {"BTC"}

# 낙폭이 이보다 얕으면 사건을 표시하지 않는다
EVENT_MIN_DEPTH = -15.0

# FRED release_id -> 일정 제목
FRED_RELEASES: dict[int, str] = {
    10: "미국 CPI",
    50: "미국 고용보고서",
    54: "미국 PCE",
    53: "미국 GDP",
}

EVENT_WINDOW_DAYS = 31
HTTP_TIMEOUT = 10
RETRIES = 3
