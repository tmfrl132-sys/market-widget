package com.tmfrl.marketwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.model.Event
import com.tmfrl.marketwidget.model.MarketData
import com.tmfrl.marketwidget.model.Quote
import com.tmfrl.marketwidget.model.Trend
import com.tmfrl.marketwidget.model.chartSummary
import com.tmfrl.marketwidget.model.flagOf
import com.tmfrl.marketwidget.model.formatChangeBadge
import com.tmfrl.marketwidget.model.formatDday
import com.tmfrl.marketwidget.model.formatEventDate
import com.tmfrl.marketwidget.model.formatPrice
import com.tmfrl.marketwidget.model.formatUpdatedAt
import com.tmfrl.marketwidget.model.seoulToday
import com.tmfrl.marketwidget.model.toggleSelection
import com.tmfrl.marketwidget.model.trendOf
import com.tmfrl.marketwidget.model.upcomingEvents
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import androidx.glance.appwidget.RadioButton
import androidx.glance.appwidget.RadioButtonDefaults
import com.tmfrl.marketwidget.model.Period
import com.tmfrl.marketwidget.model.chartValues
import com.tmfrl.marketwidget.model.parsePeriod
import com.tmfrl.marketwidget.model.eventRows
import com.tmfrl.marketwidget.model.fitBitmap

/** 이 높이보다 크면 시세 아래에 차트·일정 칸을 둔다. */
private val DETAIL_MIN_HEIGHT = 220.dp

/** 차트·일정 칸 위쪽(테두리, 머리줄, 시세 두 줄)이 차지하는 대략의 높이. */
private val TOP_PART_HEIGHT = 230.dp
private val EVENT_ROW_HEIGHT = 24.dp

/** 차트 비트맵 픽셀 상한. RGB_565라 약 500KB. */
private const val MAX_CHART_PIXELS = 250_000

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

private const val SPARK_POINTS = 20

/** 새 데이터를 받을 때마다 올리는 값. 위젯 상태가 바뀌어야 Glance가 다시 그린다. */
private val DATA_VERSION = longPreferencesKey("data_version")

/** 차트를 보여줄 종목 id. 없으면 발표 일정을 보여준다. */
private val SELECTED = stringPreferencesKey("selected")
private val QUOTE_ID = ActionParameters.Key<String>("quote_id")

/** 차트 기간(Period.name). 위젯마다 따로 기억한다. */
private val PERIOD = stringPreferencesKey("period")
private val PERIOD_NAME = ActionParameters.Key<String>("period_name")

class MarketWidget : GlanceAppWidget() {
    // 실제 크기로 그려야 위젯을 늘린 만큼 차트·일정 칸이 커진다
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.forContext(context)
        provideContent {
            // 상태를 읽어 두면 DATA_VERSION·SELECTED·PERIOD가 바뀔 때마다 다시 그리면서 캐시를 새로 읽는다
            val state = currentState<Preferences>()
            state[DATA_VERSION]
            WidgetContent(repository.cached(), ZonedDateTime.now(), state[SELECTED], parsePeriod(state[PERIOD]))
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

/** 모든 위젯의 상태 버전을 올리고 다시 그린다. */
suspend fun redrawAllWidgets(context: Context) {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(MarketWidget::class.java)
    ids.forEach { id ->
        updateAppWidgetState(context, id) { it[DATA_VERSION] = System.currentTimeMillis() }
    }
    MarketWidget().updateAll(context)
}

/** 시세 타일을 누르면 그 종목 차트를 보여주고, 다시 누르면 일정으로 돌아간다. */
class SelectQuoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tapped = parameters[QUOTE_ID] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            when (val next = toggleSelection(prefs[SELECTED], tapped)) {
                null -> prefs.remove(SELECTED)
                else -> prefs[SELECTED] = next
            }
        }
        MarketWidget().update(context, glanceId)
    }
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
private fun WidgetContent(data: MarketData?, now: ZonedDateTime, selected: String?, period: Period) {
    val size = LocalSize.current
    val large = size.height >= DETAIL_MIN_HEIGHT
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(BACKGROUND)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        Header(data, now)
        Spacer(GlanceModifier.height(6.dp))
        if (data == null) {
            Text(
                "시세를 불러오는 중입니다. 눌러서 새로고침",
                style = style(SUBTLE, 12),
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
            )
        } else {
            QuotesAndDetail(data, now, selected, period, size, large, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun QuotesAndDetail(
    data: MarketData, now: ZonedDateTime, selected: String?, period: Period, size: DpSize, large: Boolean,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val tileWidth = (size.width - 20.dp) / 5 - 4.dp
        data.quotes.chunked(5).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEach {
                    QuoteTile(it, it.id == selected, showSpark = large, tileWidth, GlanceModifier.defaultWeight())
                }
            }
        }
        if (large) {
            Spacer(GlanceModifier.height(8.dp))
            // 시세 두 줄 아래 남는 높이를 모두 차트·일정이 쓴다
            val detailHeight = maxOf(60.dp, size.height - TOP_PART_HEIGHT)
            val chosen = data.quotes.firstOrNull { it.id == selected }
            if (chosen != null && chartValues(chosen, period).size >= 2) {
                ChartPanel(chosen, period, size.width - 20.dp, detailHeight, GlanceModifier.defaultWeight())
            } else if (chosen != null) {
                // 앱 업데이트 직후처럼 기기에 저장된 data.json이 차트 데이터 없는 옛 형식일 때
                Text(
                    "${chosen.name} 차트 데이터를 받는 중입니다. ⟳를 눌러주세요",
                    style = style(SUBTLE, 12),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                )
            } else {
                val today = seoulToday(now)
                val rows = eventRows(detailHeight.value - 20f, EVENT_ROW_HEIGHT.value, min = 3)
                EventList(upcomingEvents(data.events, today, limit = rows), today)
            }
        }
    }
}

@Composable
private fun Header(data: MarketData?, now: ZonedDateTime) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("경제지표", style = style(TEXT, 13, bold = true))
        Spacer(GlanceModifier.defaultWeight())
        if (data != null) {
            Text(formatUpdatedAt(OffsetDateTime.parse(data.updated_at), now), style = style(SUBTLE, 10))
        }
        Box(
            modifier = GlanceModifier.padding(start = 8.dp, end = 2.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        ) {
            Text("⟳", style = style(TEXT, 15, bold = true))
        }
    }
}

@Composable
private fun QuoteTile(quote: Quote, selected: Boolean, showSpark: Boolean, tileWidth: Dp, modifier: GlanceModifier) {
    val alpha = if (quote.stale) 0.5f else 1f
    val tileColor = if (selected) TILE_SELECTED else TILE
    val (color, tint) = trendColors(quote.change_pct)
    Box(modifier = modifier.padding(2.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxWidth()
                .background(tileColor)
                .cornerRadius(12.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp)
                .clickable(actionRunCallback<SelectQuoteAction>(actionParametersOf(QUOTE_ID to quote.id))),
        ) {
            Text(quote.name, style = style(SUBTLE.copy(alpha = alpha), 10), maxLines = 1)
            Text(formatPrice(quote.price), style = style(TEXT.copy(alpha = alpha), 14, bold = true), maxLines = 1)
            Box(modifier = GlanceModifier.background(tint).cornerRadius(6.dp).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(formatChangeBadge(quote.change_pct), style = style(color.copy(alpha = alpha), 10, bold = true), maxLines = 1)
            }
            if (showSpark && quote.chart.size >= 2) {
                val density = LocalContext.current.resources.displayMetrics.density
                val sparkHeight = 18.dp
                val bitmap = renderLine(
                    values = quote.chart.takeLast(SPARK_POINTS),
                    lineColor = color.toArgb(),
                    backgroundColor = tileColor.toArgb(),
                    widthPx = (tileWidth - 12.dp).toPx(density),
                    heightPx = sparkHeight.toPx(density),
                    strokePx = 1.5f * density,
                    detailed = false,
                )
                Spacer(GlanceModifier.height(4.dp))
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(sparkHeight),
                )
            }
        }
    }
}

@Composable
private fun ChartPanel(quote: Quote, period: Period, width: Dp, height: Dp, modifier: GlanceModifier) {
    val values = chartValues(quote, period)
    val summary = chartSummary(values) ?: return
    val (color, tint) = trendColors(summary.changePct)
    val density = LocalContext.current.resources.displayMetrics.density
    // 차트 그림 높이 = 칸 높이 - 위 두 줄(이름·최저최고) - 아래 두 줄(기간 표시, 기간 선택)
    val imageHeight = maxOf(40.dp, height - 84.dp)
    // 전송 크기 제한 때문에 픽셀 수를 줄이고 위젯이 늘려서 그리게 한다
    val fullW = width.toPx(density)
    val (bw, bh) = fitBitmap(fullW, imageHeight.toPx(density), MAX_CHART_PIXELS)
    val scale = bw.toFloat() / fullW
    val bitmap = renderLine(
        values = values,
        lineColor = color.toArgb(),
        backgroundColor = BACKGROUND.toArgb(),
        widthPx = bw,
        heightPx = bh,
        strokePx = 2.5f * density * scale,
        detailed = true,
    )
    // 차트나 머리줄을 누르면 닫는다. 기간 선택 줄은 따로 눌린다.
    val close = actionRunCallback<SelectQuoteAction>(actionParametersOf(QUOTE_ID to quote.id))
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(close),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(quote.name, style = style(TEXT, 13, bold = true))
            Text("  ${period.label}", style = style(SUBTLE, 10))
            Spacer(GlanceModifier.defaultWeight())
            Box(modifier = GlanceModifier.background(tint).cornerRadius(6.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(formatChangeBadge(summary.changePct), style = style(color, 11, bold = true))
            }
        }
        Text(
            "최저 ${formatPrice(summary.min)}  ·  최고 ${formatPrice(summary.max)}  ·  누르면 닫기",
            style = style(SUBTLE, 10),
        )
        Spacer(GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${quote.name} ${period.label} 차트",
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(close),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(period.agoLabel, style = style(SUBTLE, 10))
            Spacer(GlanceModifier.defaultWeight())
            Text("현재 ${formatPrice(quote.price)}", style = style(color, 10, bold = true))
        }
        PeriodSelector(period)
    }
}

/** 차트 아래 라디오 버튼 줄. 누르면 바로 그 기간으로 바뀐다. */
@Composable
private fun PeriodSelector(current: Period) {
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
    }
}

@Composable
private fun EventList(events: List<Event>, today: LocalDate) {
    Text("다가오는 발표", style = style(SUBTLE, 10, bold = true))
    Spacer(GlanceModifier.height(4.dp))
    if (events.isEmpty()) {
        Text("2주 안에 예정된 발표가 없습니다", style = style(SUBTLE, 12))
    }
    events.forEach { event ->
        val date = LocalDate.parse(event.date)
        val isToday = date == today
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.width(40.dp)
                    .background(if (isToday) UP_TINT else TILE)
                    .cornerRadius(6.dp)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(formatDday(date, today), style = style(if (isToday) UP else TEXT, 10, bold = true))
            }
            Spacer(GlanceModifier.width(8.dp))
            Text("${flagOf(event.country)} ${event.title}", style = style(TEXT, 12, bold = isToday), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(formatEventDate(date), style = style(SUBTLE, 11))
        }
    }
}
