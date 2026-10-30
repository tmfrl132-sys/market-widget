"""수집 대상 목록과 공용 상수."""

# (id, 표시 이름, Yahoo 심볼) — 위젯 타일 순서
SYMBOLS: list[tuple[str, str, str]] = [
    ("KS11", "코스피", "^KS11"),
    ("KQ11", "코스닥", "^KQ11"),
    ("GSPC", "S&P500", "^GSPC"),
    ("IXIC", "나스닥", "^IXIC"),
    ("KRW", "원/달러", "KRW=X"),
    ("TNX", "미 10년물", "^TNX"),
    ("VIX", "VIX", "^VIX"),
    ("CL", "WTI", "CL=F"),
    ("GC", "금", "GC=F"),
    ("BTC", "비트코인", "BTC-USD"),
]

# FRED release_id -> 일정 제목
FRED_RELEASES: dict[int, str] = {
    10: "미국 CPI",
    50: "미국 고용보고서",
    54: "미국 PCE",
    53: "미국 GDP",
}

EVENT_WINDOW_DAYS = 14
HTTP_TIMEOUT = 10
RETRIES = 3
