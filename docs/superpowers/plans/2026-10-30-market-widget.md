# 경제지표 위젯 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GitHub Actions가 30분마다 만드는 `data.json`을 읽어, 안드로이드 홈 화면 위젯에 한·미 시세 10개와 다가오는 경제지표 발표 일정을 보여준다.

**Architecture:** Python 수집기(`collector/`)가 Yahoo 차트 API·FRED·고정 일정표에서 값을 모아 `data.json`을 만들고 GitHub Pages에 배포한다. Kotlin + Jetpack Glance 위젯 앱(`android/`)이 그 URL을 WorkManager로 30분마다 읽어 그린다. APK는 GitHub Actions가 빌드·서명해 Releases에 올린다.

**Tech Stack:** Python 3.12, uv, requests, pytest · Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Jetpack Glance 1.1.1, WorkManager 2.9.1, kotlinx-serialization-json 1.7.3, JDK 17 · GitHub Actions, GitHub Pages, GitHub Releases

**Spec:** `docs/superpowers/specs/2026-10-30-market-widget-design.md`

## Global Constraints

- 저장소: 공개 `tmfrl132-sys/market-widget`. Pages URL: `https://tmfrl132-sys.github.io/market-widget/data.json`
- 이 PC에는 Java·Gradle이 없다. Android 빌드와 Kotlin 테스트는 GitHub Actions에서만 돌린다.
- 이 PC의 gh는 PATH에 없다. 명령마다 `$env:PATH = "C:\Program Files\GitHub CLI;$env:PATH"`를 먼저 붙인다.
- 시세 10개와 순서: 코스피 `^KS11`, 코스닥 `^KQ11`, S&P500 `^GSPC`, 나스닥 `^IXIC`, 원/달러 `KRW=X`, 미 10년물 `^TNX`, VIX `^VIX`, WTI `CL=F`, 금 `GC=F`, 비트코인 `BTC-USD`
- `id`는 심볼에서 `^`와 `=X`·`=F`·`-USD`를 뺀 값이다 (`KS11`, `KRW`, `CL`, `BTC` …).
- 일정 범위: 오늘부터 14일 안. 미국 지표는 CPI·고용보고서·PCE·GDP.
- 수집 생략 구간: 한국 시간 토요일 08:00 이상부터 월요일 07:00 미만까지.
- 등락 색: 상승 빨강 `#D32F2F`, 하락 파랑 `#1976D2`, 보합 회색.
- 모든 시간 계산은 `Asia/Seoul` 기준이다.
- 오류 메시지에 `FRED_API_KEY` 값이 나오면 `***`로 가린다.
- 화면 문구는 한국어로 쓴다.

## Review Focus

1. **첫 실행 (직전 `data.json` 없음, 404)**: 실패한 시세는 `price: null`, `stale: true`로 남는다. 위젯은 값 자리에 `-`를 그린다. Task 2와 Task 4에서 테스트한다.
2. **Yahoo 응답 끝 값이 `null`** (장중 빈 봉, 휴장일): 마지막 `null`이 아닌 종가를 현재값으로 쓴다. Task 2에서 테스트한다.
3. **수집이 며칠 멈춤** (공개 저장소에서 60일 동안 활동이 없으면 예약 실행이 꺼진다): 갱신 시각이 오늘이 아니면 `10/28 10:30 기준`처럼 날짜를 붙여 오래된 값임을 드러낸다. Task 5에서 테스트하고, 재활성화 방법은 README에 적는다.
4. **태블릿 시간대가 서울이 아님**: "오늘 발표" 강조와 갱신 시각은 기기 시간대가 아니라 `Asia/Seoul`로 계산한다. Task 5에서 테스트한다.
5. **값 크기가 제각각** (비트코인 6자리, 금리 4.25, 환율 1,380.5): 1,000 이상은 소수 없이 천 단위 쉼표, 1,000 미만은 소수 둘째 자리까지 쓴다. Task 5에서 테스트한다.

---

### Task 1: 저장소 만들기와 접속 시험

이 PC는 네이버에 막혔던 전례가 있다. 그래서 코드를 쓰기 전에 GitHub Actions에서 Yahoo와 FRED에 접속되는지부터 확인한다.

**Files:**
- Create: `.github/workflows/probe.yml`, `.gitignore`, `README.md`

**Interfaces:**
- Produces: GitHub 저장소 `tmfrl132-sys/market-widget`, Secret `FRED_API_KEY`

- [ ] **Step 1: 사용자에게 FRED 인증키 발급을 안내한다.** 순서는 https://fredaccount.stlouisfed.org 가입 → API Keys → Request API Key다. 받은 키는 채팅에 붙여넣지 말고, Step 3의 명령을 사용자가 직접 실행해 넣게 한다.
- [ ] **Step 2: 공개 저장소를 만들고 push한다.** 저장소 생성은 바깥에 공개되는 작업이므로 실행 전에 사용자 확인을 받는다.

```powershell
$env:PATH = "C:\Program Files\GitHub CLI;$env:PATH"; gh repo create tmfrl132-sys/market-widget --public --source . --push
```

- [ ] **Step 3: 사용자가 Secret을 넣는다.** `gh secret set FRED_API_KEY` 를 실행한 뒤 프롬프트에 키를 붙여넣는다.
- [ ] **Step 4: `probe.yml`을 쓴다** (`workflow_dispatch`만). curl로 다음 두 요청을 보내 HTTP 코드와 본문 앞 300자를 출력한다.
  - Yahoo: `https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11?range=10d&interval=1d`, 헤더 `User-Agent: Mozilla/5.0`
  - FRED: `https://api.stlouisfed.org/fred/release/dates?release_id=10&api_key=$FRED_API_KEY&file_type=json&include_release_dates_with_no_data=true&sort_order=desc&limit=5`
- [ ] **Step 5: 실행하고 확인한다.** `gh workflow run probe.yml` 다음 `gh run watch`. 기대 결과는 두 요청 모두 HTTP 200이고, Yahoo 본문에 `"regularMarketPrice"`가 있는 것이다. Yahoo가 막히면 여기서 멈추고 사용자와 다른 출처를 정한다.
- [ ] **Step 6: probe.yml을 지우고 커밋한다.** `chore: 저장소 설정, 접속 시험 완료`

### Task 2: 시세 수집 (`collector/quotes.py`)

**Files:**
- Create: `pyproject.toml` (uv, 의존성 `requests`, 개발 의존성 `pytest`), `collector/__init__.py`, `collector/config.py`, `collector/quotes.py`
- Test: `tests/test_quotes.py`, `tests/fixtures/yahoo_ks11.json`

**Interfaces:**
- Produces:
  - `config.SYMBOLS: list[tuple[str, str, str]]` — `(id, name, symbol)`, Global Constraints에 적은 순서
  - `quotes.parse_chart(payload: dict) -> tuple[float, float, list[float]]` — `(price, prev_close, history5)`
  - `quotes.fetch_quotes(prev: dict[str, dict], get=requests.get) -> list[dict]` — 항목 키 `id, name, price, change, change_pct, history, stale`

- [ ] **Step 1: 실패하는 테스트를 쓴다**
  - `test_parse_chart_uses_last_non_null_close`: 픽스처의 `close`가 `[..., 2637.8, 2650.12, null]`이면 `price == 2650.12`, `history`는 `null`을 뺀 마지막 5개다.
  - `test_parse_chart_prev_close_is_meta_chartPreviousClose`
  - `test_fetch_quotes_change_pct`: price 2650.12, prev_close 2637.82이면 `change == 12.3`, `change_pct == 0.47`이다 (둘 다 소수 둘째 자리로 반올림).
  - `test_failed_symbol_keeps_previous_and_marks_stale`: `get`이 `requests.ConnectionError`를 3번 던지면 결과 항목은 `prev["KS11"]`의 값을 그대로 쓰고 `stale is True`다.
  - `test_failed_symbol_without_previous`: `prev`가 `{}`면 `price is None`, `change is None`, `history == []`, `stale is True`다.
  - `test_returns_all_ten_in_config_order`
- [ ] **Step 2: 실패하는지 확인한다.** `uv run pytest tests/test_quotes.py -v`, 기대 결과는 ImportError로 FAIL이다.
- [ ] **Step 3: 구현한다.** URL은 Task 1과 같다(`range=10d&interval=1d`, User-Agent 헤더, timeout 10초). 재시도는 3번, 간격은 1초다. 현재값은 `meta.regularMarketPrice`를 쓰고, 없으면 마지막 `null`이 아닌 종가를 쓴다.
- [ ] **Step 4: 통과하는지 확인한다.** `uv run pytest tests/test_quotes.py -v`
- [ ] **Step 5: 커밋한다.** `feat: Yahoo 시세 수집`

### Task 3: 발표 일정 (`collector/events.py`)

**Files:**
- Create: `collector/events.py`, `collector/schedule.json`
- Test: `tests/test_events.py`

**Interfaces:**
- Produces:
  - `config.FRED_RELEASES: dict[int, str]` = `{10: "미국 CPI", 50: "미국 고용보고서", 54: "미국 PCE", 53: "미국 GDP"}`
  - `events.fetch_fred_events(api_key: str, today: date, get=requests.get) -> list[dict]`
  - `events.load_fixed_events(path: Path, today: date) -> list[dict]`
  - `events.merge_events(*lists) -> list[dict]` — 날짜순 정렬, `(date, title)`가 같은 중복 제거
  - 항목 키: `date` ("YYYY-MM-DD"), `title`, `country` ("US"|"KR")

- [ ] **Step 1: 실패하는 테스트를 쓴다**
  - `test_fred_keeps_only_next_14_days`: today가 2026-10-30이면 2026-10-30부터 2026-11-13까지는 포함하고, 2026-10-29와 2026-11-14는 뺀다.
  - `test_fred_failure_returns_empty_and_hides_key`: 예외 메시지에 키가 있으면, 로그에는 `***`로 바뀌어 남고 함수는 `[]`를 돌려준다 (`caplog`로 확인).
  - `test_fixed_events_filtered_by_window`
  - `test_merge_sorts_and_dedupes`
- [ ] **Step 2: 실패하는지 확인한다.** `uv run pytest tests/test_events.py -v`
- [ ] **Step 3: 구현한다.** 요청 파라미터는 `realtime_start=today`, `include_release_dates_with_no_data=true`, `sort_order=asc`, `limit=10`이다. `schedule.json` 형식은 `[{"date": "...", "title": "FOMC 금리 결정", "country": "US"}, ...]`이다. 2026-11-01 이후의 FOMC 결정일과 한국은행 금통위 통화정책방향 결정일을 2027년 말까지 넣는다. 날짜는 federalreserve.gov와 bok.or.kr 공식 일정에서 확인한 것만 넣는다.
- [ ] **Step 4: 통과하는지 확인한다.**
- [ ] **Step 5: 커밋한다.** `feat: 경제지표 발표 일정 수집`

### Task 4: `data.json` 조립과 30분 배포

**Files:**
- Create: `collector/build.py`, `.github/workflows/collect.yml`
- Test: `tests/test_build.py`

**Interfaces:**
- Consumes: `fetch_quotes`, `fetch_fred_events`, `load_fixed_events`, `merge_events`
- Produces: `build.should_run(now: datetime) -> bool`, `build.build_data(now, prev: dict | None, ...) -> dict`, `python -m collector.build --out site/data.json`

- [ ] **Step 1: 실패하는 테스트를 쓴다**
  - `test_should_run_weekend_window`: 서울 시간 기준 토 07:59는 True, 토 08:00은 False, 일 15:00은 False, 월 06:59는 False, 월 07:00은 True다.
  - `test_build_data_shape`: `updated_at`이 `+09:00` ISO 문자열이고, `quotes`가 10개이며, `events` 키가 있다.
  - `test_build_without_previous`: `prev=None`이어도 예외 없이 만들어진다.
- [ ] **Step 2: 실패하는지 확인한다.**
- [ ] **Step 3: 구현한다.** `main()`은 기존 Pages URL에서 직전 `data.json`을 받는다. 실패하면 `None`으로 둔다. `should_run`이 False면 직전 파일을 그대로 다시 쓰고 끝낸다. `--force`를 주면 `should_run` 검사를 건너뛴다.
- [ ] **Step 4: 통과하는지 확인한다.** `uv run pytest -v`. 전체 테스트가 PASS여야 한다.
- [ ] **Step 5: `collect.yml`을 쓴다.** 트리거는 `schedule: "*/30 * * * *"`와 `workflow_dispatch`(입력 `force` boolean)다. 실행 순서는 `astral-sh/setup-uv` → `uv run python -m collector.build --out site/data.json` → `actions/upload-pages-artifact`(path `site`) → `actions/deploy-pages`다. 권한은 `pages: write`, `id-token: write`다.
- [ ] **Step 6: Pages를 켜고 확인한다.** `gh api -X POST repos/tmfrl132-sys/market-widget/pages -f build_type=workflow`, `gh workflow run collect.yml -f force=true`. 기대 결과는 `curl https://tmfrl132-sys.github.io/market-widget/data.json`이 시세 10개를 돌려주는 것이다.
- [ ] **Step 7: 커밋한다.** `feat: 30분마다 data.json 배포`

### Task 5: 위젯 데이터 모델과 표시 형식 (Kotlin, JVM 테스트)

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/app/build.gradle.kts` (applicationId `com.tmfrl.marketwidget`, minSdk 26, compileSdk·targetSdk 35), `android/app/src/main/java/com/tmfrl/marketwidget/model/MarketData.kt`, `.../model/Format.kt`, `.github/workflows/android.yml`
- Test: `android/app/src/test/java/com/tmfrl/marketwidget/model/MarketDataTest.kt`, `FormatTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Quote(id: String, name: String, price: Double?, change: Double?, change_pct: Double?, history: List<Double>, stale: Boolean)`
  - `@Serializable data class Event(date: String, title: String, country: String)`
  - `@Serializable data class MarketData(updated_at: String, quotes: List<Quote>, events: List<Event>)`
  - `fun parseMarketData(json: String): MarketData` (`ignoreUnknownKeys = true`)
  - `val SEOUL: ZoneId`
  - `fun formatPrice(price: Double?): String`
  - `fun formatChangePct(pct: Double?): String`
  - `fun formatEventDate(date: LocalDate): String`
  - `fun formatUpdatedAt(updatedAt: OffsetDateTime, now: ZonedDateTime): String`
  - `fun upcomingEvents(events: List<Event>, today: LocalDate, limit: Int = 4): List<Event>`
  - `enum class Trend { UP, DOWN, FLAT }`, `fun trendOf(pct: Double?): Trend`

- [ ] **Step 1: 실패하는 테스트를 쓴다**
  - `formatPrice`: `68123.4`는 `"68,123"`, `1380.4`는 `"1,380"`(1,000 이상은 소수 없음), `4.25`는 `"4.25"`, `null`은 `"-"`다.
  - `formatChangePct`: `0.47`은 `"+0.47%"`, `-1.2`는 `"-1.20%"`, `null`은 `""`다.
  - `formatEventDate(2026-11-13)`는 `"11/13(금)"`다.
  - `formatUpdatedAt`: 같은 날이면 `"10:30 기준"`, 다른 날이면 `"10/28 10:30 기준"`이다. now가 UTC로 주어져도 서울 날짜로 비교한다 (2026-10-30T16:00Z는 서울로 10/31이다).
  - `upcomingEvents`: today 이전은 빼고 최대 4개를 돌려준다.
  - `trendOf`: 0.0과 null은 FLAT이다.
  - `parseMarketData`: 알 수 없는 키가 있어도 해석하고, `price: null`도 해석한다.
- [ ] **Step 2: `android.yml`을 쓴다.** `android/**`가 바뀐 push에서 실행한다. 순서는 `actions/setup-java`(temurin 17) → `gradle/actions/setup-gradle`(gradle-version `8.10.2`) → `gradle -p android testDebugUnitTest`다.
- [ ] **Step 3: push하고 테스트가 실패하는지 확인한다.** `gh run watch`, 기대 결과는 컴파일 오류로 FAIL이다.
- [ ] **Step 4: 구현한다.**
- [ ] **Step 5: push하고 통과하는지 확인한다.** 기대 결과는 `testDebugUnitTest` PASS다.
- [ ] **Step 6: 커밋한다.** `feat: 위젯 데이터 모델과 표시 형식`

### Task 6: 위젯 화면과 갱신

**Files:**
- Create: `.../data/Repository.kt`, `.../widget/MarketWidget.kt`, `.../widget/MarketWidgetReceiver.kt`, `.../work/RefreshWorker.kt`, `.../MainActivity.kt`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/xml/market_widget_info.xml`
- Test: `android/app/src/test/java/com/tmfrl/marketwidget/data/RepositoryTest.kt`

**Interfaces:**
- Consumes: Task 5의 전부
- Produces:
  - `class Repository(cacheFile: File, fetch: () -> String)`
    - `suspend fun refresh(): MarketData?` — 성공하면 캐시를 쓰고 값을 돌려준다. 실패하면 `cached()`를 돌려준다.
    - `fun cached(): MarketData?`
  - `const val DATA_URL = "https://tmfrl132-sys.github.io/market-widget/data.json"`
  - `RefreshWorker.schedule(context)` — 고유 이름 `market-refresh`, 30분 주기, 네트워크 연결 조건, `KEEP`
  - `RefreshAction : ActionCallback` — 새로고침 버튼

- [ ] **Step 1: 실패하는 테스트를 쓴다.** `refresh_failure_returns_cached`, `refresh_success_overwrites_cache`. 둘 다 `fetch` 람다로 성공과 실패를 흉내 낸다.
- [ ] **Step 2: push하고 실패하는지 확인한다.**
- [ ] **Step 3: 구현한다.**
  - `fetch`는 `HttpURLConnection`으로 하고 timeout은 10초다.
  - 위젯은 `SizeMode.Responsive(setOf(DpSize(250.dp, 110.dp), DpSize(320.dp, 220.dp)))`를 쓴다. 작은 크기에서는 시세 타일만 보인다. 큰 크기에서는 시세 5×2, 일정, 갱신 시각과 새로고침 버튼(`RefreshAction`)이 보인다.
  - 타일의 등락률 색은 `trendOf`로 정한다. `stale` 항목은 글자 alpha를 0.5로 한다. 오늘 발표는 굵게 하고 앞에 `오늘 ·`을 붙인다.
  - 위젯 탭은 새로고침, 앱 실행은 설치 안내 화면이다.
  - Receiver `onEnabled`에서 `RefreshWorker.schedule`을 부른다. Worker는 `refresh()` 후 `MarketWidget().updateAll(context)`를 부른다.
  - `market_widget_info.xml`: `minWidth 250dp`, `minHeight 110dp`, `targetCellWidth 4`, `targetCellHeight 3`, `resizeMode horizontal|vertical`, `updatePeriodMillis 0`(갱신은 WorkManager가 맡는다).
  - `MainActivity`는 한 화면이다. 문구는 "홈 화면을 길게 누르고 → 위젯 → 경제지표를 추가하세요"다.
- [ ] **Step 4: push하고 통과하는지 확인한다.** `testDebugUnitTest`와 `assembleDebug`가 모두 PASS여야 한다.
- [ ] **Step 5: 커밋한다.** `feat: 홈 화면 위젯과 30분 갱신`

### Task 7: 서명된 APK 배포와 설치 안내

**Files:**
- Modify: `.github/workflows/android.yml` (태그 `v*`에서 서명 빌드 후 Release), `android/app/build.gradle.kts` (환경변수로 받는 `signingConfigs.release`), `README.md`
- Create: `scripts/make_keystore.py` (cryptography, `uv run --with cryptography`)

**Interfaces:**
- Produces: Secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`(`market`); Release 자산 `market-widget-vX.Y.Z.apk`

서명 키가 빌드마다 달라지면 업데이트할 때 앱을 지우고 다시 깔아야 한다. 그래서 키를 한 번 만들어 Secrets에 고정한다.

- [ ] **Step 1: `make_keystore.py`를 쓴다.** 자체 서명 RSA 2048 인증서(유효기간 30년)를 담은 PKCS12 파일을 만든다. 출력 위치는 스크래치 폴더다. 비밀번호는 `secrets.token_urlsafe(24)`로 만든다. 키와 비밀번호는 `gh secret set`으로 바로 넣고 화면이나 채팅에 출력하지 않는다. 같은 PKCS12와 비밀번호를 사용자의 PC `C:\Users\tmfrl\market-widget-keystore\`에도 백업한다. 이 경로는 저장소 밖이다.
- [ ] **Step 2: 서명 설정을 넣는다.** `android.yml`의 태그 빌드가 `KEYSTORE_B64`를 파일로 풀고 `gradle -p android assembleRelease`를 실행한 뒤, `softprops/action-gh-release`로 APK를 올린다.
- [ ] **Step 3: `v0.1.0` 태그를 push하고 확인한다.** 기대 결과는 `gh release view v0.1.0`에 APK가 있는 것이다.
- [ ] **Step 4: README에 설치 방법을 쓴다.** 태블릿에서 Releases의 APK 받기 → "출처를 알 수 없는 앱" 허용 → 설치 → 위젯 추가. 수집이 멈췄을 때 Actions 탭에서 `collect.yml`을 다시 켜는 방법도 적는다.
- [ ] **Step 5: 커밋하고 사용자에게 설치를 요청한다.** 커밋 메시지는 `feat: 서명된 APK 릴리스`다. 실제 위젯 화면은 사용자가 태블릿에서 확인한다.
