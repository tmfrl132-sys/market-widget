@file:Suppress("PropertyName")

package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 낙폭 차트의 사건 표시. index는 max_drawdowns 안의 위치(v0.9.0 이하 앱용), depth는 그 지점의 낙폭 %.
 * date는 사건 구간 최저점 날짜(max_dates 안에 있다). v1.0부터 이 날짜로 위치를 찾고, 없는 옛 데이터의 사건은 그리지 않는다.
 */
@Serializable
data class MaxEvent(val label: String, val index: Int, val depth: Double, val date: String? = null)

/** data.json 형식. 필드 이름은 수집기(collector/build.py)와 같다. */
@Serializable
data class Quote(
    val id: String,
    val name: String,
    val price: Double?,
    val change: Double?,
    val change_pct: Double?,
    val history: List<Double> = emptyList(),
    val chart: List<Double> = emptyList(), // 3개월 일봉 종가 (v0.3.0 이하 호환)
    val daily: List<Double> = emptyList(), // 1년 일봉 종가
    val weekly: List<Double> = emptyList(), // 5년 주봉 종가
    val max_closes: List<Double> = emptyList(), // 전체 기간 종가 (점을 줄임)
    val max_drawdowns: List<Double> = emptyList(), // 전체 기간 고점 대비 낙폭 %
    val max_events: List<MaxEvent> = emptyList(), // 낙폭 차트에 표시할 사건
    val max_start: Int? = null, // 전체 기간 시작 연도
    val peak: Double? = null, // 전체 기간 최고 종가. 낙폭을 안 쓰는 지표는 null
    val mdd: Double? = null, // 전체 기간 최대 낙폭 %. 낙폭을 안 쓰는 지표는 null
    val intraday: List<Double> = emptyList(), // 오늘 5분봉 종가. 기기가 직접 받는다
    val prev_close: Double? = null, // 전일 종가. 기기가 직접 받는다
    val daily_dates: List<String> = emptyList(), // daily와 1:1, YYYY-MM-DD
    val weekly_dates: List<String> = emptyList(), // weekly와 1:1, YYYY-MM-DD
    val max_dates: List<String> = emptyList(), // max_closes와 1:1, YYYY-MM-DD
    val intraday_times: List<Long> = emptyList(), // intraday와 1:1, 초 단위 epoch. 기기가 직접 받는다
    val session_start: Long? = null, // 오늘 차트의 장 시작, 초 단위 epoch. 기기가 직접 받는다
    val session_end: Long? = null, // 오늘 차트의 장 끝, 초 단위 epoch. 기기가 직접 받는다
    val big_move_pct: Double? = null, // 큰 움직임 강조 기준 %. 수집기가 보낸다. null이면 강조하지 않는다
    val market_tz: String? = null, // 시장 시간대(IANA 이름). 수집기가 보낸다. null이면 장 상태를 보이지 않는다
    val always_open: Boolean = false, // 늘 거래하는 지표(비트코인). 수집기가 보낸다
    val max_depths: List<Double> = emptyList(), // max_closes와 1:1인 고점 대비 낙폭 %(전체 일봉 기준). 낙폭 없는 지표는 빈 목록
    val mdd_peak_date: String? = null, // MDD 직전 고점 날짜 YYYY-MM-DD
    val mdd_trough_date: String? = null, // MDD 바닥 날짜
    val mdd_recovery_date: String? = null, // 바닥 뒤 처음 고점 이상이 된 날짜. 미회복이면 null
    val combined: CombinedChart? = null, // 현물·선물 합친 24시간 차트(S&P500·나스닥100). 기기가 갱신 때 계산한다. 수집기는 보내지 않는다

    val stale: Boolean = false,
)

@Serializable
data class Event(val date: String, val title: String, val country: String)

@Serializable
data class MarketData(val updated_at: String, val quotes: List<Quote>, val events: List<Event>)

private val json = Json { ignoreUnknownKeys = true }

fun parseMarketData(text: String): MarketData = json.decodeFromString(text)

fun encodeMarketData(data: MarketData): String = json.encodeToString(data)
