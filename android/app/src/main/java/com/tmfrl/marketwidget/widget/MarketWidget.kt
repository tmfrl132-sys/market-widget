package com.tmfrl.marketwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tmfrl.marketwidget.MainActivity
import com.tmfrl.marketwidget.data.CustomRepository
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.data.SettingsStore
import com.tmfrl.marketwidget.model.ChartSelection
import com.tmfrl.marketwidget.model.CompareSeries
import com.tmfrl.marketwidget.model.EventListPage
import com.tmfrl.marketwidget.model.MarketData
import com.tmfrl.marketwidget.model.MarketState
import com.tmfrl.marketwidget.model.MarketStatus
import com.tmfrl.marketwidget.model.Quote
import com.tmfrl.marketwidget.model.Trend
import com.tmfrl.marketwidget.model.afterAutoOn
import com.tmfrl.marketwidget.model.afterCompareButton
import com.tmfrl.marketwidget.model.bigMoveTrend
import com.tmfrl.marketwidget.model.chartSummary
import com.tmfrl.marketwidget.model.combinedBands
import com.tmfrl.marketwidget.model.combinedChartFor
import com.tmfrl.marketwidget.model.combinedXAxis
import com.tmfrl.marketwidget.model.afterCloseChartFor
import com.tmfrl.marketwidget.model.combinedCloseMark
import com.tmfrl.marketwidget.model.combinedStartLabel
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.formatAfterCloseBadge
import com.tmfrl.marketwidget.model.formatCloseSummary
import com.tmfrl.marketwidget.model.convertedLabel
import com.tmfrl.marketwidget.model.compareLogChart
import com.tmfrl.marketwidget.model.compareSeries
import com.tmfrl.marketwidget.model.dateTicks
import com.tmfrl.marketwidget.model.flagOf
import com.tmfrl.marketwidget.model.chartDates
import com.tmfrl.marketwidget.model.commonGutter
import com.tmfrl.marketwidget.model.drawdownChart
import com.tmfrl.marketwidget.model.formatEpisode
import com.tmfrl.marketwidget.model.sanitizeSelection
import com.tmfrl.marketwidget.model.sparkMaxPixels
import com.tmfrl.marketwidget.model.visibleQuotes
import com.tmfrl.marketwidget.model.yAxisFor
import com.tmfrl.marketwidget.model.formatAxisPct
import com.tmfrl.marketwidget.model.formatChangeBadge
import com.tmfrl.marketwidget.model.formatCompareStart
import com.tmfrl.marketwidget.model.formatDday
import com.tmfrl.marketwidget.model.formatDrawdown
import com.tmfrl.marketwidget.model.formatEventDate
import com.tmfrl.marketwidget.model.formatIndex
import com.tmfrl.marketwidget.model.formatLegendChange
import com.tmfrl.marketwidget.model.formatLegendValue
import com.tmfrl.marketwidget.model.formatMarketState
import com.tmfrl.marketwidget.model.YAxis
import com.tmfrl.marketwidget.model.formatPowerOfTen
import com.tmfrl.marketwidget.model.formatPrice
import com.tmfrl.marketwidget.model.legendChangePct
import com.tmfrl.marketwidget.model.logChartFor
import com.tmfrl.marketwidget.model.formatSignedAmount
import com.tmfrl.marketwidget.model.formatUpdatedAt
import com.tmfrl.marketwidget.model.marketState
import com.tmfrl.marketwidget.model.parseUpdatedAt
import com.tmfrl.marketwidget.model.seoulToday
import com.tmfrl.marketwidget.model.autoQuoteIds
import com.tmfrl.marketwidget.model.nextAutoQuote
import com.tmfrl.marketwidget.model.tileGrid
import com.tmfrl.marketwidget.model.trendOf
import com.tmfrl.marketwidget.work.AutoTick
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import androidx.glance.appwidget.RadioButton
import androidx.glance.appwidget.RadioButtonDefaults
import com.tmfrl.marketwidget.model.Period
import com.tmfrl.marketwidget.model.XTick
import com.tmfrl.marketwidget.model.chartValues
import com.tmfrl.marketwidget.model.chartXAxis
import com.tmfrl.marketwidget.model.tileXPositions
import com.tmfrl.marketwidget.model.parsePeriod
import com.tmfrl.marketwidget.model.EVENT_LINE_GAP_DP
import com.tmfrl.marketwidget.model.EVENT_LINE_HEIGHT_DP
import com.tmfrl.marketwidget.model.EVENT_LINE_LABEL
import com.tmfrl.marketwidget.model.EVENT_LINE_SP
import com.tmfrl.marketwidget.model.EventLineText
import com.tmfrl.marketwidget.model.afterEventLineTap
import com.tmfrl.marketwidget.model.eventDays
import com.tmfrl.marketwidget.model.eventDaysFitting
import com.tmfrl.marketwidget.model.eventLineText
import com.tmfrl.marketwidget.model.eventLineWidthDp
import com.tmfrl.marketwidget.model.eventListPage
import com.tmfrl.marketwidget.model.eventListVisible
import com.tmfrl.marketwidget.model.isSoonEvent
import com.tmfrl.marketwidget.model.orientedWidgetSizeDp
import com.tmfrl.marketwidget.model.widgetLayoutFor
import com.tmfrl.marketwidget.model.eventListRows
import com.tmfrl.marketwidget.model.tileTapSelection
import com.tmfrl.marketwidget.model.widgetLayout
import com.tmfrl.marketwidget.model.fitBitmap

private val EVENT_ROW_HEIGHT = 24.dp

/** 차트 비트맵 픽셀 상한. RGB_565라 약 500KB. */
private const val MAX_CHART_PIXELS = 250_000

/** 이 높이보다 낮으면 잠수 차트를 빼고 가격 차트(구간 칠과 고점·바닥 점)만 보여준다. */
private val MIN_STACKED_HEIGHT = 140.dp

/** 차트 그림 높이 중 아래 잠수 차트가 차지하는 비율. 가격 차트를 우선한다. */
private const val UNDERWATER_SHARE = 0.35f

// 어두운 카드 디자인. 상승 빨강·하락 파랑은 어두운 배경에서 잘 보이게 밝게 잡았다.
private val BACKGROUND = Color(0xFF111827)
private val TILE = Color(0xFF1B2433)
private val TILE_SELECTED = Color(0xFF2B3A55)
private val TEXT = Color(0xFFF3F4F6)
private val SUBTLE = Color(0xFF8B95A7)
private val UP = Color(0xFFFF5A5F)
private val DOWN = Color(0xFF4D8DFF)
private val UP_TINT = Color(0x33FF5A5F)
private val DOWN_TINT = Color(0x334D8DFF)
private val FLAT_TINT = Color(0x338B95A7)

/** 최대 낙폭 구간 칠. 하락 파랑을 약 12% 알파로 옅게 깐다. */
private val EPISODE_SHADE = Color(0x1F4D8DFF)

/** 합친 24시간 차트의 정규장 띠. 낙폭 구간 칠(파랑)과 겹치지 않게 흰색을 약 8% 알파로 깐다. */
private val SESSION_BAND = Color(0x14FFFFFF)

/** 장중 점. 빨강·파랑은 등락 전용이라 초록을 쓴다. */
private val LIVE_DOT = Color(0xFF34D399)

/** 비교 지표 선과 비교 안내 글자. 빨강·파랑은 등락 뜻이라 선 색으로 쓰지 않는다. */
private val COMPARE_LINE = Color(0xFFFBBF24)

/** 오늘·내일 일정 강조. 빨강·파랑은 등락 뜻이라 팔레트의 호박색을 함께 쓴다. */
private val EVENT_SOON = COMPARE_LINE
private val EVENT_SOON_TINT = Color(0x33FBBF24)

/** 비교 차트의 기준값. 공통 시작일 값을 100으로 둔다. */
private const val COMPARE_BASE = 100.0

/** 새 데이터를 받을 때마다 올리는 값. 위젯 상태가 바뀌어야 Glance가 다시 그린다. */
private val DATA_VERSION = longPreferencesKey("data_version")

/** 차트를 보여줄 종목 id. 없으면 발표 일정을 보여준다. */
private val SELECTED = stringPreferencesKey("selected")
private val QUOTE_ID = ActionParameters.Key<String>("quote_id")

/** 차트 기간(Period.name). 위젯마다 따로 기억한다. */
private val PERIOD = stringPreferencesKey("period")
private val PERIOD_NAME = ActionParameters.Key<String>("period_name")

/** 5년·MAX 차트의 로그 눈금 사용 여부. 위젯마다 따로 기억한다. */
private val LOG_SCALE = booleanPreferencesKey("log_scale")

/** 자동모드 여부와, 끌 때 되돌아갈 켜기 전 선택. 위젯마다 따로 기억한다. */
private val AUTO = booleanPreferencesKey("auto")
private val SAVED_SELECTED = stringPreferencesKey("saved_selected")

/** 겹쳐 볼 지표 id와, 비교할 타일을 고르는 중인지. 위젯마다 따로 기억한다. */
private val COMPARE = stringPreferencesKey("compare")
private val COMPARE_PICKING = booleanPreferencesKey("compare_picking")

/** 일정 줄을 눌러 차트 자리에 전체 일정 목록을 열었는지. 위젯마다 따로 기억한다. */
private val EVENTS_OPEN = booleanPreferencesKey("events_open")

private fun MutablePreferences.setEventsOpen(open: Boolean) {
    if (open) this[EVENTS_OPEN] = true else remove(EVENTS_OPEN)
}

private fun MutablePreferences.setSelected(id: String?) {
    if (id == null) remove(SELECTED) else this[SELECTED] = id
}

private fun Preferences.selection() = ChartSelection(this[SELECTED], this[COMPARE], this[COMPARE_PICKING] == true)

private fun MutablePreferences.setSelection(s: ChartSelection) {
    setSelected(s.selected)
    if (s.compare == null) remove(COMPARE) else this[COMPARE] = s.compare
    if (s.picking) this[COMPARE_PICKING] = true else remove(COMPARE_PICKING)
}

/**
 * 위젯에 그릴 데이터. data.json 캐시의 기본 지표와 추가 종목 캐시를 설정(켜기·순서)대로 합친다.
 * data.json 캐시가 없으면 null이다(추가 종목만으로는 그리지 않는다. 기본 지표 캐시는 첫 갱신에서 바로 생긴다).
 */
private fun widgetData(context: Context, settings: WidgetSettings = SettingsStore.forContext(context).read()): MarketData? {
    val data = Repository.forContext(context).cached() ?: return null
    return data.copy(quotes = visibleQuotes(data.quotes, CustomRepository.forContext(context).cached(), settings))
}

/** 지금 위젯에 보이는 지표. 액션에서 선택 상태를 정리할 때 쓴다. */
private fun visibleQuotesFor(context: Context): List<Quote> = widgetData(context)?.quotes.orEmpty()

/** 앞으로의 일정이 있는지. 없으면 일정 줄이 숨겨져 목록을 닫을 수 없으므로 열림 상태를 무효로 본다. */
private fun hasUpcomingEvents(context: Context): Boolean {
    val events = Repository.forContext(context).cached()?.events.orEmpty()
    return eventDays(events, seoulToday(ZonedDateTime.now())).isNotEmpty()
}

/**
 * 일정 목록이 지금 이 위젯에 실제로 보이는지. 크기가 줄거나 화면을 돌려 목록을 그릴 수 없으면 false다.
 * 크기를 알 수 없어도 false라, 자동 순환이 멈춘 채로 남지 않는다.
 */
private fun eventListShowing(context: Context, glanceId: GlanceId, stored: Boolean, quoteCount: Int): Boolean {
    if (!stored) return false
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val (width, height) = orientedWidgetSizeDp(
        landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
    ) ?: return false
    return eventListVisible(stored, widgetLayoutFor(width, height, quoteCount, hasUpcomingEvents(context)))
}

class MarketWidget : GlanceAppWidget() {
    // 실제 크기로 그려야 위젯을 늘린 만큼 차트·일정 칸이 커진다
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 상태를 읽어 두면 DATA_VERSION·SELECTED·PERIOD가 바뀔 때마다 다시 그리면서 캐시와 설정을 새로 읽는다
            val state = currentState<Preferences>()
            state[DATA_VERSION]
            val settings = SettingsStore.forContext(context).read()
            val data = widgetData(context, settings)
            // 꺼지거나 지운 지표를 가리키는 선택은 그릴 때 정리한다. 저장된 상태는 다음 액션 때 덮인다.
            val selection = if (data == null) state.selection() else sanitizeSelection(state.selection(), data.quotes.map { it.id })
            WidgetContent(
                data, ZonedDateTime.now(), selection, parsePeriod(state[PERIOD]),
                state[LOG_SCALE] == true, state[AUTO] == true, settings.show_drawdown, state[EVENTS_OPEN] == true,
            )
        }
    }
}

/** 기간 라디오 버튼을 누르면 그 기간을 기억하고 다시 그린다. */
class SelectPeriodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val name = parameters[PERIOD_NAME] ?: return
        updateAppWidgetState(context, glanceId) { it[PERIOD] = name }
        MarketWidget().update(context, glanceId)
    }
}

/** 로그 버튼을 누르면 켜고 끈다. */
class ToggleLogAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[LOG_SCALE] = it[LOG_SCALE] != true }
        MarketWidget().update(context, glanceId)
    }
}

/** 비교 버튼. 누르면 비교할 타일 고르기를 시작하고, 고르는 중이거나 비교 중이면 끈다. */
class ToggleCompareAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs -> prefs.setSelection(afterCompareButton(prefs.selection())) }
        MarketWidget().update(context, glanceId)
    }
}

/** 일정 줄을 누르면 차트 자리에 전체 일정 목록을 열고, 다시 누르면 원래 차트로 돌아간다. */
class ToggleEventsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val visible = visibleQuotesFor(context).map { it.id }
        var auto = false
        updateAppWidgetState(context, glanceId) { prefs ->
            auto = prefs[AUTO] == true
            prefs.setEventsOpen(afterEventLineTap(prefs[EVENTS_OPEN] == true, sanitizeSelection(prefs.selection(), visible).selected))
        }
        MarketWidget().update(context, glanceId)
        // 목록을 닫자마자 다음 지표로 넘어가지 않게 1분을 새로 센다
        if (auto) AutoTick.schedule(context)
    }
}

/** 모든 위젯의 상태 버전을 올리고 다시 그린다. */
suspend fun redrawAllWidgets(context: Context) {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(MarketWidget::class.java)
    ids.forEach { id ->
        updateAppWidgetState(context, id) { it[DATA_VERSION] = System.currentTimeMillis() }
    }
    MarketWidget().updateAll(context)
}

/**
 * 시세 타일을 누르면 그 종목 차트를 보여주고, 다시 누르면 일정으로 돌아간다.
 * 비교할 타일을 고르는 중이면 그 타일이 비교 대상이 된다(afterTileTap).
 * 자동모드에서는 그 종목으로 건너뛰고, 거기서 1분을 새로 센다.
 * 일정 목록이 열려 있으면 목록을 닫고 그 종목 차트를 연다.
 */
class SelectQuoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tapped = parameters[QUOTE_ID] ?: return
        val visible = visibleQuotesFor(context).map { it.id }
        var auto = false
        updateAppWidgetState(context, glanceId) { prefs ->
            auto = prefs[AUTO] == true
            // 보이지 않는 목록은 열리지 않은 것으로 봐서 첫 탭이 평소처럼 동작한다
            val listOpen = eventListShowing(context, glanceId, prefs[EVENTS_OPEN] == true, visible.size)
            prefs.setSelection(tileTapSelection(sanitizeSelection(prefs.selection(), visible), tapped, auto, listOpen))
            prefs.setEventsOpen(false)
        }
        MarketWidget().update(context, glanceId)
        if (auto) AutoTick.schedule(context)
    }
}

/** 자동 버튼. 켜면 비교를 끄고 첫 종목부터 돌고, 끄면 켜기 전 화면으로 돌아간다. 어느 쪽이든 일정 목록은 닫는다. */
class ToggleAutoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val quotes = visibleQuotesFor(context)
        val visible = quotes.map { it.id }
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.setEventsOpen(false)
            if (prefs[AUTO] == true) {
                prefs.remove(AUTO)
                // 켜기 전 지표가 그사이 꺼졌으면 일정 화면으로 돌아간다
                prefs.setSelected(prefs[SAVED_SELECTED]?.takeIf { it in visible })
                prefs.remove(SAVED_SELECTED)
            } else {
                prefs[AUTO] = true
                prefs[SELECTED]?.let { prefs[SAVED_SELECTED] = it }
                val first = nextAutoQuote(null, autoQuoteIds(quotes, parsePeriod(prefs[PERIOD])))
                prefs.setSelection(afterAutoOn(sanitizeSelection(prefs.selection(), visible)).copy(selected = first))
            }
        }
        MarketWidget().update(context, glanceId)
        // 지금부터 1분을 새로 센다
        AutoTick.schedule(context)
    }
}

/** 자동모드인 위젯을 다음 종목으로 넘긴다. 일정 목록이 화면에 보이는 위젯은 넘기지 않는다. 홈 화면에 위젯이 하나라도 있는지 돌려준다. */
suspend fun advanceAutoWidgets(context: Context): Boolean {
    // 자동모드가 꺼져 있으면 파일을 읽지 않는다. 이 함수는 화면이 켜져 있는 동안 1분마다 불린다.
    val quotes by lazy { visibleQuotesFor(context) }
    val hasUpcoming by lazy { hasUpcomingEvents(context) }
    val ids = GlanceAppWidgetManager(context).getGlanceIds(MarketWidget::class.java)
    ids.forEach { id ->
        var advanced = false
        updateAppWidgetState(context, id) { prefs ->
            if (prefs[AUTO] == true) {
                val stored = prefs[EVENTS_OPEN] == true
                // 일정이 모두 지나면 열림 상태를 지운다. 크기 때문에 안 보일 때는 남겨 두어 크기가 돌아오면 다시 보인다.
                if (stored && !hasUpcoming) prefs.setEventsOpen(false)
                if (!eventListShowing(context, id, stored, quotes.size)) {
                    advanced = true
                    val next = nextAutoQuote(prefs[SELECTED], autoQuoteIds(quotes, parsePeriod(prefs[PERIOD])))
                    if (next != null) prefs[SELECTED] = next
                }
            }
        }
        if (advanced) MarketWidget().update(context, id)
    }
    return ids.isNotEmpty()
}

private fun style(color: Color, size: Int, bold: Boolean = false) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

private fun trendColors(pct: Double?): Pair<Color, Color> = when (trendOf(pct)) {
    Trend.UP -> UP to UP_TINT
    Trend.DOWN -> DOWN to DOWN_TINT
    Trend.FLAT -> SUBTLE to FLAT_TINT
}

private fun Dp.toPx(density: Float) = (value * density).toInt()

@Composable
private fun WidgetContent(
    data: MarketData?, now: ZonedDateTime, selection: ChartSelection, period: Period, logScale: Boolean, auto: Boolean,
    showDrawdown: Boolean, eventsOpen: Boolean,
) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(BACKGROUND)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        Header(data, now, auto)
        Spacer(GlanceModifier.height(6.dp))
        if (data == null) {
            Text(
                "시세를 불러오는 중입니다. 눌러서 새로고침",
                style = style(SUBTLE, 12),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
            )
        } else {
            QuotesAndDetail(data, now, selection, period, logScale, auto, showDrawdown, eventsOpen, size, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun QuotesAndDetail(
    data: MarketData, now: ZonedDateTime, selection: ChartSelection, period: Period, logScale: Boolean, auto: Boolean,
    showDrawdown: Boolean, eventsOpen: Boolean, size: DpSize, modifier: GlanceModifier,
) {
    // 한 줄 칸 수는 위젯 폭으로 정하고, 줄 수가 늘면 차트·일정 칸 위쪽 높이도 그만큼 늘린다
    val grid = tileGrid(size.width.value, data.quotes.size)
    val today = seoulToday(now)
    val days = eventDays(data.events, today)
    val layout = widgetLayout(size.height.value, grid.rows, days.isNotEmpty())
    val large = layout.showDetail
    // 장 상태는 화면의 숫자를 받은 시각 기준이다
    val asOf = parseUpdatedAt(data.updated_at)
    // 자식은 일정 줄, 타일 묶음, 간격, 차트 자리(최대 2개)로 Glance 한도(10개) 안이다
    Column(modifier = modifier.fillMaxWidth()) {
        if (layout.showEventLine) {
            // 글자 크기 설정만큼 글자가 넓어지므로 폭 추정에도 반영한다
            val fontScale = LocalContext.current.resources.configuration.fontScale
            val available = eventLineWidthDp(size.width.value, fontScale)
            val shown = days.take(eventDaysFitting(days, available, EVENT_LINE_SP * fontScale))
            // 차트 칸이 없는 크기에서는 목록을 열 자리가 없어 줄만 보인다
            EventLine(eventLineText(shown), tappable = large)
        }
        val tileWidth = (size.width - 20.dp) / grid.perRow - 4.dp
        // 타일 줄을 한 Column에 묶는다. Glance는 Row·Column 하나에 자식을 10개까지만 그려서, 줄이 늘어도 아래 일정이 빠지지 않게 한다.
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            data.quotes.chunked(grid.perRow).forEach { row ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    row.forEach {
                        val state = asOf?.let { time -> marketState(it, time) }
                        QuoteTile(
                            it, it.id == selection.selected, showSpark = large, tileWidth, sparkMaxPixels(data.quotes.size), state,
                            GlanceModifier.defaultWeight(),
                        )
                    }
                    // 마지막 줄의 빈 칸도 같은 너비로 채워 세로 열을 맞춘다
                    repeat(grid.perRow - row.size) { Spacer(GlanceModifier.defaultWeight()) }
                }
            }
        }
        if (large) {
            Spacer(GlanceModifier.height(8.dp))
            // 시세 줄 아래 남는 높이를 모두 차트·일정이 쓴다
            val detailHeight = layout.detailHeightDp.dp
            // 일정 목록을 열어 두면 고른 지표가 있어도 아래 목록 분기로 간다. 줄이 숨겨진 크기에서는 닫을 수 없어 열지 않는다.
            val listOpen = eventListVisible(eventsOpen, layout)
            val chosen = if (listOpen) null else data.quotes.firstOrNull { it.id == selection.selected }
            if (chosen != null && (chartValues(chosen, period).size >= 2 || combinedChartFor(chosen, period) != null)) {
                // 자동모드에서는 비교하지 않는다
                val other = if (auto) null else data.quotes.firstOrNull { it.id == selection.compare && it.id != chosen.id }
                val state = asOf?.let { marketState(chosen, it) }
                ChartPanel(
                    chosen, other, !auto && selection.picking, state, period, logScale, auto, showDrawdown,
                    size.width - 20.dp, detailHeight, GlanceModifier.defaultWeight(),
                )
            } else if (chosen != null) {
                // 앱 업데이트 직후처럼 기기에 저장된 data.json이 차트 데이터 없는 옛 형식일 때
                Text(
                    "${chosen.name} 차트 데이터를 받는 중입니다. ⟳를 눌러주세요",
                    style = style(SUBTLE, 12),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                )
                // 고른 기간의 데이터가 없어도 다른 기간으로 옮길 수 있게 둔다
                PeriodSelector(period, logScale, compareButton = null)
            } else {
                val rows = eventListRows(detailHeight.value - 20f, EVENT_ROW_HEIGHT.value)
                // 제목·간격과 일정 줄을 한 Column에 묶어 바깥 Column의 자식 수가 늘지 않게 한다
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    EventList(eventListPage(data.events, today, rows), today)
                }
            }
        }
    }
}

@Composable
private fun Header(data: MarketData?, now: ZonedDateTime, auto: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("경제지표", style = style(TEXT, 13, bold = true))
        Spacer(GlanceModifier.defaultWeight())
        if (data != null) {
            Text(formatUpdatedAt(OffsetDateTime.parse(data.updated_at), now), style = style(SUBTLE, 10))
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier.background(if (auto) DOWN else TILE)
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .clickable(actionRunCallback<ToggleAutoAction>()),
        ) {
            Text("자동", style = style(if (auto) TEXT else SUBTLE, 11, bold = auto))
        }
        // 설정 화면(지표 켜기·순서·종목 추가)을 연다. 자동 버튼과 같은 모양이다.
        Spacer(GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier.background(TILE)
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("설정", style = style(SUBTLE, 11))
        }
        Box(
            modifier = GlanceModifier.padding(start = 8.dp, end = 2.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        ) {
            Text("⟳", style = style(TEXT, 15, bold = true))
        }
    }
}

/**
 * 머리줄 아래 일정 한 줄. 오늘·내일 일정은 언제나 앞쪽이라 글자 조각 둘로 색을 나눈다.
 * 자식은 이름표, 간격, 글자 조각 2개로 Glance 한도(10개) 안이다.
 */
@Composable
private fun EventLine(text: EventLineText, tappable: Boolean) {
    val base = GlanceModifier.fillMaxWidth().height(EVENT_LINE_HEIGHT_DP.dp)
    Row(
        modifier = if (tappable) base.clickable(actionRunCallback<ToggleEventsAction>()) else base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(EVENT_LINE_LABEL, style = style(SUBTLE, EVENT_LINE_SP, bold = true), maxLines = 1)
        Spacer(GlanceModifier.width(EVENT_LINE_GAP_DP.dp))
        if (text.soon.isNotEmpty()) {
            Text(text.soon, style = style(EVENT_SOON, EVENT_LINE_SP, bold = true), maxLines = 1)
        }
        if (text.later.isNotEmpty()) {
            Text(text.later, style = style(TEXT, EVENT_LINE_SP), maxLines = 1)
        }
    }
}

/**
 * 시세 타일. 바깥 Box(테두리색) 안에 Column(타일색)을 겹쳐 2dp 테두리처럼 보이게 한다.
 * 큰 움직임이면 테두리와 등락 배지를 상승 빨강·하락 파랑으로 칠하고, 아니면 테두리색이 타일색이라 보이지 않는다.
 */
@Composable
private fun QuoteTile(
    quote: Quote, selected: Boolean, showSpark: Boolean, tileWidth: Dp, sparkPixels: Int, state: MarketState?,
    modifier: GlanceModifier,
) {
    val alpha = if (quote.stale) 0.5f else 1f
    val tileColor = if (selected) TILE_SELECTED else TILE
    // S&P500·나스닥100이 정규장 마감 뒤(다음 개장 전)면 큰 차트 머리줄과 같은 기준으로 보인다:
    // 선물 환산가와 종가 대비 "마감 후" 등락률, 그 아래 종가와 전일종가 대비 등락률.
    val afterClose = afterCloseChartFor(quote)
    val shownPct = afterClose?.change_pct ?: quote.change_pct
    val (color, tint) = trendColors(shownPct)
    // 큰 움직임 강조는 정규장 등락 기준이라, 마감 후 등락을 보일 때는 색과 부호가 어긋나지 않게 끈다
    val accent = if (afterClose != null) null else when (bigMoveTrend(quote)) {
        Trend.UP -> UP
        Trend.DOWN -> DOWN
        else -> null
    }
    Box(modifier = modifier.padding(2.dp)) {
        Box(
            modifier = GlanceModifier.fillMaxWidth()
                .background(accent ?: tileColor)
                .cornerRadius(12.dp)
                .padding(2.dp)
                .clickable(actionRunCallback<SelectQuoteAction>(actionParametersOf(QUOTE_ID to quote.id))),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth()
                    .background(tileColor)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        quote.name,
                        style = style(SUBTLE.copy(alpha = alpha), 10),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    // 장중은 초록 점, 나머지는 회색 글자. 판단할 수 없으면 아무것도 없다.
                    if (state != null) {
                        if (state.status == MarketStatus.LIVE) {
                            Text("●", style = style(LIVE_DOT.copy(alpha = alpha), 9), maxLines = 1)
                        } else {
                            Text(state.status.label, style = style(SUBTLE.copy(alpha = alpha), 9), maxLines = 1)
                        }
                    }
                }
                Text(formatPrice(afterClose?.price ?: quote.price), style = style(TEXT.copy(alpha = alpha), 14, bold = true), maxLines = 1)
                Box(
                    modifier = GlanceModifier.background(accent ?: tint)
                        .cornerRadius(6.dp)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        afterClose?.let(::formatAfterCloseBadge) ?: formatChangeBadge(quote.change_pct),
                        style = style(if (accent != null) TEXT else color.copy(alpha = alpha), 10, bold = true),
                        maxLines = 1,
                    )
                }
                if (afterClose != null) {
                    Text(formatCloseSummary(afterClose), style = style(SUBTLE.copy(alpha = alpha), 9), maxLines = 1)
                }
                if (showSpark) {
                    val density = LocalContext.current.resources.displayMetrics.density
                    // 종가 줄이 들어가면 그 높이만큼 작은 차트를 줄여 타일 높이를 다른 타일과 같게 둔다
                    val sparkHeight = if (afterClose != null) 8.dp else 18.dp
                    Spacer(GlanceModifier.height(if (afterClose != null) 2.dp else 4.dp))
                    // 큰 오늘 차트처럼 장 시작~끝 중 받은 시각까지만 그린다. 장 시간을 믿을 수 없으면 가로 전체로 늘린다.
                    // 아직 점이 모자라면 타일 높이만 맞춘다.
                    if (quote.intraday.size >= 2) {
                        // 타일이 많아도 작은 차트 전체가 전송 크기 제한을 넘지 않게 한 개당 픽셀을 나눠 쓴다
                        val fullW = (tileWidth - 12.dp).toPx(density).coerceAtLeast(1)
                        val (sparkW, sparkH) = fitBitmap(fullW, sparkHeight.toPx(density), sparkPixels)
                        val bitmap = renderLine(
                            values = quote.intraday,
                            lineColor = color.toArgb(),
                            backgroundColor = tileColor.toArgb(),
                            widthPx = sparkW,
                            heightPx = sparkH,
                            strokePx = 1.5f * density * sparkW / fullW,
                            detailed = false,
                            xPositions = tileXPositions(quote),
                        )
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = GlanceModifier.fillMaxWidth().height(sparkHeight),
                        )
                    } else {
                        Spacer(GlanceModifier.height(sparkHeight))
                    }
                }
            }
        }
    }
}

/** 기간 줄 끝 비교 버튼의 모양. 자동모드에서는 버튼을 숨긴다(null). */
private enum class CompareButton { OFF, PICKING, ON }

/**
 * 고른 지표의 차트. other가 있고 두 지표의 날짜를 맞출 수 있으면 겹쳐 그린다(ComparePanel).
 * 맞출 수 없거나 오늘 기간이면 주 지표만 그리고 두 번째 줄에 이유를 쓴다.
 */
@Composable
private fun ChartPanel(
    quote: Quote, other: Quote?, picking: Boolean, state: MarketState?, period: Period, logScale: Boolean, auto: Boolean,
    showDrawdown: Boolean, width: Dp, height: Dp, modifier: GlanceModifier,
) {
    val series = other?.let { compareSeries(quote, it, period) }
    if (other != null && series != null) {
        ComparePanel(quote, other, series, period, logScale, width, height, modifier)
        return
    }
    // S&P500·나스닥100의 오늘은 현물·선물 합친 24시간 차트다. 결과가 없으면 기존 오늘 차트를 그린다.
    val combined = combinedChartFor(quote, period)
    val values = combined?.values ?: chartValues(quote, period)
    val summary = chartSummary(values) ?: return
    // 오늘 차트는 전일 종가가 기준이고, 나머지는 기간 첫 값이 기준이다. 합친 차트는 기준선·등락을 합친 값으로 계산해 두었다.
    val today = period == Period.TODAY
    val change = when {
        combined != null -> combined.change
        today -> quote.change ?: summary.change
        else -> summary.change
    }
    val changePct = when {
        combined != null -> combined.change_pct
        today -> quote.change_pct ?: summary.changePct
        else -> summary.changePct
    }
    val baseline = when {
        combined != null -> combined.baseline
        today -> quote.prev_close ?: values.first()
        else -> values.first()
    }
    val (color, tint) = trendColors(changePct)
    val density = LocalContext.current.resources.displayMetrics.density
    // 잠수 차트. 오늘, 낙폭 없는 지표(원/달러·달러인덱스·미 10년물·VIX), 점이 모자라면 null이다.
    // 설정에서 MDD 표시를 끄면 null로 두어 낙폭 줄, 잠수 차트, 최대 낙폭 구간 칠, 고점·바닥 점을 모두 그리지 않는다.
    val drawdown = if (showDrawdown) drawdownChart(quote, period) else null
    // 차트 그림 높이 = 칸 높이 - 위 두 줄(이름·최저최고) - 낙폭 줄 - 아래 두 줄(기간 표시, 기간 선택)
    val imagesHeight = maxOf(40.dp, height - (if (drawdown != null) 100.dp else 84.dp))
    val showUnderwater = drawdown != null && imagesHeight >= MIN_STACKED_HEIGHT
    val underwaterHeight = if (showUnderwater) imagesHeight * UNDERWATER_SHARE else 0.dp
    val priceHeight = imagesHeight - underwaterHeight
    val fullW = width.toPx(density).coerceAtLeast(1)
    val priceH = priceHeight.toPx(density)
    val underwaterH = underwaterHeight.toPx(density)
    // 두 그림을 합친 크기로 한 번만 줄인다. 같은 축척이라 폭(px), 글자 크기, 선 굵기가 같다.
    val (bw, bhTotal) = fitBitmap(fullW, priceH + underwaterH, MAX_CHART_PIXELS)
    val scale = bw.toFloat() / fullW
    val priceBh = if (showUnderwater) (priceH * scale).toInt() else bhTotal
    val underwaterBh = (underwaterH * scale).toInt()
    val textPx = DETAIL_TEXT_SP * density * scale
    // 로그는 첫 값 대비 배수의 지수로 그린다. 점선 문구에는 실제 값을 그대로 쓴다.
    val logChart = if (logScale && period.supportsLog) logChartFor(values) else null
    val axisLabel: (Double) -> String = if (logChart != null) ::formatPowerOfTen else ::formatPrice
    val priceValues = logChart?.values ?: values
    // 합친 차트의 가로 점선은 등락 기준(마감 뒤엔 종가)이 아니라 가장 최근 정규장의 전일종가다. 없으면(옛 캐시) 기준선.
    val lineValue = combined?.prev_close?.takeIf { it > 0 } ?: baseline
    val priceBaseline = if (logChart != null) 0.0 else lineValue
    val lineLabel = when {
        // 전일종가가 없는 옛 캐시의 마감 후 기준선은 그날 종가다
        combined != null && combined.prev_close <= 0 && combined.afterClose -> "종가 ${formatPrice(lineValue)}"
        combined != null -> "전일종가 ${formatPrice(lineValue)}"
        today -> "전일 ${formatPrice(baseline)}"
        else -> "시작 ${formatPrice(baseline)}"
    }
    // renderLine이 스스로 정하는 축과 같다(값 + 기준값). 숫자 칸 폭을 미리 재려고 먼저 구한다.
    val priceAxis = logChart?.axis ?: yAxisFor(priceValues + priceBaseline)
    val underwaterAxis = if (showUnderwater) drawdown?.let { yAxisFor(it.depths + 0.0) } else null
    // 두 축의 숫자 칸 중 넓은 것 하나를 위아래가 함께 써서 그림 영역 폭이 같다. 같은 인덱스가 같은 가로 위치다.
    val gutter = if (underwaterAxis != null && priceAxis != null) {
        commonGutter(
            listOf(axisLabelWidth(priceAxis, axisLabel, textPx), axisLabelWidth(underwaterAxis, ::formatAxisPct, textPx)),
            textPx,
        )
    } else {
        null
    }
    // 최대 낙폭 구간: 고점 ~ 회복(회복하지 못했으면 마지막 점)
    val episode = drawdown?.episode
    val shade = drawdown?.let { chart -> chart.episode?.let { it.peak..(it.recovery ?: chart.depths.lastIndex) } }
    val dots = episode?.let { listOf(it.peak to TEXT.toArgb(), it.trough to DOWN.toArgb()) }.orEmpty()
    // 로그 값도 개수가 같아 같은 눈금을 쓴다. 잠수 차트를 쌓으면 글자는 아래 잠수 차트에만 두고 가격 차트에는 세로 보조선만 그린다.
    val xAxis = combined?.let(::combinedXAxis) ?: chartXAxis(quote, period)
    val bitmap = detailBitmap(
        priceValues, color, bw, priceBh, scale, density,
        priceBaseline,
        lineLabel,
        axisLabel, null, priceAxis,
        xTicks = xAxis.ticks,
        xPositions = xAxis.positions,
        showXLabels = !showUnderwater,
        gutterPx = gutter,
        shade = shade,
        dots = dots,
        breaks = combined?.breaks.orEmpty(),
        bands = combined?.let(::combinedBands).orEmpty(),
        // 장마감 지점: 세로 점선 대신 선 위 점과 "장마감 7,417"(v1.3). 창 밖이면 없다.
        marks = listOfNotNull(combined?.let(::combinedCloseMark)),
    )
    // 마감 뒤(다음 개장 전)면 머리줄 등락이 종가 대비 "마감 후"이고, 둘째 줄 앞에 종가와 그 등락률을 쓴다
    val afterClose = combined?.takeIf { it.afterClose }
    val episodeText = drawdown?.let { formatEpisode(chartDates(quote, period), it.episode, period) }
    // 비교 안내. 고르는 중, 오늘·7일 기간, 날짜를 맞출 수 없을 때 최저·최고 줄 자리에 쓴다.
    val compareNotice = when {
        picking -> "비교할 지표 타일을 누르세요"
        other != null && (today || period == Period.WEEK) -> "비교는 1개월~MAX에서 보입니다"
        other != null -> "두 지표의 날짜를 맞출 수 없어 비교를 그리지 않았습니다"
        else -> null
    }
    val compareButton = when {
        auto -> null
        picking -> CompareButton.PICKING
        other != null -> CompareButton.ON
        else -> CompareButton.OFF
    }
    val stateText = state?.let { " · ${formatMarketState(it)}" }.orEmpty()
    // 차트나 머리줄을 누르면 닫는다. 기간 선택 줄은 따로 눌린다.
    val close = actionRunCallback<SelectQuoteAction>(actionParametersOf(QUOTE_ID to quote.id))
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(close),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(quote.name, style = style(TEXT, 13, bold = true))
            Text("  ${period.label}$stateText", style = style(SUBTLE, 10), maxLines = 1)
            // 합친 차트의 마지막 점이 선물 환산값이면 그 봉 시각을 작게 알린다
            combined?.let(::convertedLabel)?.let { Text("  $it", style = style(SUBTLE, 9), maxLines = 1) }
            Spacer(GlanceModifier.defaultWeight())
            Box(modifier = GlanceModifier.background(tint).cornerRadius(6.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(
                    (if (afterClose != null) "마감 후 " else "") + "${formatChangeBadge(changePct)} (${formatSignedAmount(change)})",
                    style = style(color, 11, bold = true),
                )
            }
        }
        if (compareNotice != null) {
            Text(compareNotice, style = style(COMPARE_LINE, 10), maxLines = 1)
        } else {
            Text(
                afterClose?.let { formatCloseSummary(it) + "  ·  " }.orEmpty() +
                    "최저 ${formatPrice(summary.min)}  ·  최고 ${formatPrice(summary.max)}" + if (auto) "" else "  ·  누르면 닫기",
                style = style(SUBTLE, 10),
            )
        }
        if (drawdown != null) {
            Text(
                "고점 대비 ${formatDrawdown(drawdown.current)}  ·  MDD ${formatDrawdown(drawdown.mdd)}" +
                    episodeText?.let { "  ·  $it" }.orEmpty(),
                style = style(TEXT, 11, bold = true),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${quote.name} ${period.label} 차트",
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(close),
        )
        if (showUnderwater && drawdown != null) {
            // 가격과 낙폭은 눈금이 달라 위아래로 놓는다. 점이 1:1이고 폭·숫자 칸이 같아 시점이 맞는다.
            // 0% 위 점선에서 아래로 내려가는 잠수 차트이고, 면적을 고르게 칠한다.
            val underwaterBitmap = detailBitmap(
                drawdown.depths, DOWN, bw, underwaterBh, scale, density,
                0.0, "", ::formatAxisPct, 0.0, underwaterAxis,
                marks = drawdown.marks,
                xTicks = xAxis.ticks,
                xPositions = xAxis.positions,
                gutterPx = gutter,
                areaGradient = false,
                shade = shade,
            )
            Image(
                provider = ImageProvider(underwaterBitmap),
                contentDescription = "${quote.name} 고점 대비 낙폭 차트",
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(underwaterHeight).clickable(close),
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            val startLabel = when {
                combined != null -> combinedStartLabel(combined)
                period == Period.MAX && quote.max_start != null -> "${quote.max_start}년~"
                else -> period.agoLabel
            }
            Text(startLabel, style = style(SUBTLE, 10))
            Spacer(GlanceModifier.defaultWeight())
            // 합친 차트의 현재는 차트 마지막 점이다(장외에는 선물 환산값이라 카드 숫자와 다르다)
            Text("현재 ${formatPrice(combined?.price ?: quote.price)}", style = style(color, 10, bold = true))
        }
        PeriodSelector(period, logScale, compareButton)
    }
}

/**
 * 두 지표 겹쳐 보기. 공통 시작일을 100으로 맞춘 두 선을 한 차트에 그린다.
 * 최저·최고, 고점 대비, 낙폭 차트와 사건 표시는 한 지표에 대한 값이라 숨기고 그 자리에 범례를 둔다.
 */
@Composable
private fun ComparePanel(
    quote: Quote, other: Quote, series: CompareSeries, period: Period, logScale: Boolean,
    width: Dp, height: Dp, modifier: GlanceModifier,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    // 차트 그림 높이 = 칸 높이 - 위 두 줄(이름·범례) - 아래 두 줄(시작일, 기간 선택)
    val imageHeight = maxOf(40.dp, height - 84.dp)
    // 로그는 두 선 모두 시작 대비 배수의 지수다. 0 이하 값이 있으면 일반 눈금으로 그린다.
    val log = if (logScale && period.supportsLog) compareLogChart(series.primary, series.other) else null
    val axisLabel: (Double) -> String = if (log != null) ::formatPowerOfTen else ::formatIndex
    val fullW = width.toPx(density).coerceAtLeast(1)
    val (bw, bh) = fitBitmap(fullW, imageHeight.toPx(density), MAX_CHART_PIXELS)
    val bitmap = detailBitmap(
        log?.primary ?: series.primary, TEXT, bw, bh, bw.toFloat() / fullW, density,
        if (log != null) 0.0 else COMPARE_BASE, "시작 100",
        axisLabel, null, log?.axis,
        xTicks = dateTicks(series.dates, series.primary.size, period),
        secondValues = log?.other ?: series.other,
        secondColor = COMPARE_LINE,
    )
    val primaryLast = series.primary.last()
    val otherLast = series.other.last()
    val close = actionRunCallback<SelectQuoteAction>(actionParametersOf(QUOTE_ID to quote.id))
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(close),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${quote.name} vs ${other.name}", style = style(TEXT, 13, bold = true), maxLines = 1)
            Text("  ${period.label}", style = style(SUBTLE, 10), maxLines = 1)
        }
        // 범례: 선 색으로 이름과 값, 괄호 안 등락은 상승 빨강·하락 파랑
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("━ ${quote.name} ${formatLegendValue(primaryLast)}", style = style(TEXT, 10, bold = true), maxLines = 1)
            Text(" (${formatLegendChange(primaryLast)})", style = style(trendColors(legendChangePct(primaryLast)).first, 10), maxLines = 1)
            Text("   ━ ${other.name} ${formatLegendValue(otherLast)}", style = style(COMPARE_LINE, 10, bold = true), maxLines = 1)
            Text(" (${formatLegendChange(otherLast)})", style = style(trendColors(legendChangePct(otherLast)).first, 10), maxLines = 1)
        }
        Spacer(GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${quote.name}와 ${other.name} ${period.label} 비교 차트",
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(close),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(formatCompareStart(series.dates.first(), period), style = style(SUBTLE, 10))
        }
        PeriodSelector(period, logScale, CompareButton.ON)
    }
}

/** 큰 차트 글자 크기(sp). 숫자 칸 폭을 미리 잴 때도 같은 값을 쓴다. */
private const val DETAIL_TEXT_SP = 11f

/**
 * 큰 차트 비트맵. 전송 크기 제한 때문에 픽셀 수를 줄이고(bw×bh, 줄인 비율 scale) 위젯이 늘려서 그리게 하므로
 * 선과 글자도 같은 비율로 줄인다. 위아래로 쌓는 두 차트는 같은 scale을 넘긴다.
 */
private fun detailBitmap(
    values: List<Double>, color: Color, bw: Int, bh: Int, scale: Float, density: Float,
    baseline: Double?, baselineLabel: String, axisLabel: (Double) -> String, areaBase: Double?, fixedAxis: YAxis? = null,
    marks: List<Pair<Int, String>> = emptyList(),
    xTicks: List<XTick> = emptyList(),
    xPositions: List<Float>? = null,
    showXLabels: Boolean = true,
    secondValues: List<Double>? = null,
    secondColor: Color = TEXT,
    gutterPx: Float? = null,
    areaGradient: Boolean = true,
    shade: IntRange? = null,
    dots: List<Pair<Int, Int>> = emptyList(),
    breaks: List<Int> = emptyList(),
    bands: List<ClosedFloatingPointRange<Float>> = emptyList(),
): Bitmap {
    return renderLine(
        values = values,
        lineColor = color.toArgb(),
        backgroundColor = BACKGROUND.toArgb(),
        widthPx = bw,
        heightPx = bh,
        strokePx = 2.5f * density * scale,
        detailed = true,
        textPx = DETAIL_TEXT_SP * density * scale,
        textColor = SUBTLE.toArgb(),
        baseline = baseline,
        baselineLabel = baselineLabel,
        axisLabel = axisLabel,
        areaBase = areaBase,
        fixedAxis = fixedAxis,
        marks = marks,
        xTicks = xTicks,
        xPositions = xPositions,
        showXLabels = showXLabels,
        secondValues = secondValues,
        secondColor = secondColor.toArgb(),
        gutterPx = gutterPx,
        areaGradient = areaGradient,
        shade = shade,
        shadeColor = EPISODE_SHADE.toArgb(),
        dots = dots,
        breaks = breaks,
        bands = bands,
        bandColor = SESSION_BAND.toArgb(),
    )
}

/**
 * 차트 아래 라디오 버튼 줄. 누르면 바로 그 기간으로 바뀐다. 5년·MAX에서는 끝에 로그 버튼이 나온다.
 * compareButton이 있으면 맨 끝에 비교 버튼을 둔다. 자식은 라디오 7개와 버튼 2개로 Glance 한도(10개) 안이다.
 */
@Composable
private fun PeriodSelector(current: Period, logScale: Boolean, compareButton: CompareButton?) {
    val colors = RadioButtonDefaults.colors(
        checkedColor = ColorProvider(DOWN),
        uncheckedColor = ColorProvider(SUBTLE),
    )
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Period.entries.forEach { p ->
            RadioButton(
                checked = p == current,
                onClick = actionRunCallback<SelectPeriodAction>(actionParametersOf(PERIOD_NAME to p.name)),
                text = p.label,
                style = style(if (p == current) TEXT else SUBTLE, 11, bold = p == current),
                colors = colors,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        if (current.supportsLog) {
            Box(
                modifier = GlanceModifier.background(if (logScale) DOWN else TILE)
                    .cornerRadius(6.dp)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .clickable(actionRunCallback<ToggleLogAction>()),
            ) {
                Text("로그", style = style(if (logScale) TEXT else SUBTLE, 11, bold = logScale))
            }
        }
        if (compareButton != null) {
            val on = compareButton != CompareButton.OFF
            Box(
                modifier = GlanceModifier.padding(start = 4.dp)
                    .background(if (on) DOWN else TILE)
                    .cornerRadius(6.dp)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .clickable(actionRunCallback<ToggleCompareAction>()),
            ) {
                Text(
                    if (compareButton == CompareButton.ON) "비교 ✕" else "비교",
                    style = style(if (on) TEXT else SUBTLE, 11, bold = on),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EventList(page: EventListPage, today: LocalDate) {
    Text("다가오는 발표", style = style(SUBTLE, 10, bold = true))
    Spacer(GlanceModifier.height(4.dp))
    if (page.events.isEmpty() && page.more == null) {
        Text("한 달 안에 예정된 발표가 없습니다", style = style(SUBTLE, 12))
    }
    page.events.forEach { event ->
        val date = LocalDate.parse(event.date)
        // 오늘·내일은 일정 줄과 같은 호박색이다. 빨강·파랑은 등락 전용이다.
        val soon = isSoonEvent(date, today)
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.width(40.dp)
                    .background(if (soon) EVENT_SOON_TINT else TILE)
                    .cornerRadius(6.dp)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(formatDday(date, today), style = style(if (soon) EVENT_SOON else TEXT, 10, bold = true))
            }
            Spacer(GlanceModifier.width(8.dp))
            Text("${flagOf(event.country)} ${event.title}", style = style(TEXT, 12, bold = soon), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(formatEventDate(date), style = style(SUBTLE, 11))
        }
    }
    // 자식은 제목, 간격, 일정 7줄, 이 줄까지 10개로 Glance 한도 안이다
    page.more?.let { Text(it, style = style(SUBTLE, 11, bold = true), maxLines = 1) }
}
