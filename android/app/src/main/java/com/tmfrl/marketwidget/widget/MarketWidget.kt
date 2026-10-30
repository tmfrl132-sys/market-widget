package com.tmfrl.marketwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.currentState
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.model.Event
import com.tmfrl.marketwidget.model.MarketData
import com.tmfrl.marketwidget.model.Quote
import com.tmfrl.marketwidget.model.Trend
import com.tmfrl.marketwidget.model.formatChangePct
import com.tmfrl.marketwidget.model.formatEventDate
import com.tmfrl.marketwidget.model.formatPrice
import com.tmfrl.marketwidget.model.formatUpdatedAt
import com.tmfrl.marketwidget.model.seoulToday
import com.tmfrl.marketwidget.model.trendOf
import com.tmfrl.marketwidget.model.upcomingEvents
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

private val SMALL = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(320.dp, 220.dp)

private val BACKGROUND = Color(0xF2FFFFFF)
private val TEXT = Color(0xFF212121)
private val SUBTLE = Color(0xFF757575)
private val UP = Color(0xFFD32F2F)
private val DOWN = Color(0xFF1976D2)

/** 새 데이터를 받을 때마다 올리는 값. 위젯 상태가 바뀌어야 Glance가 다시 그린다. */
private val DATA_VERSION = longPreferencesKey("data_version")

class MarketWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.forContext(context)
        provideContent {
            // 상태를 읽어 두면 DATA_VERSION이 바뀔 때마다 다시 그리면서 캐시를 새로 읽는다
            currentState<Preferences>()[DATA_VERSION]
            WidgetContent(repository.cached(), ZonedDateTime.now())
        }
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

private fun style(color: Color, size: Int, bold: Boolean = false) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

@Composable
private fun WidgetContent(data: MarketData?, now: ZonedDateTime) {
    val showEvents = LocalSize.current.height >= LARGE.height
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(BACKGROUND)
            .cornerRadius(16.dp)
            .padding(8.dp)
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        if (data == null) {
            Text("시세를 불러오는 중입니다. 눌러서 새로고침", style = style(SUBTLE, 12))
        } else {
            data.quotes.chunked(5).forEach { row ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    row.forEach { QuoteTile(it, GlanceModifier.defaultWeight()) }
                }
            }
            if (showEvents) {
                val today = seoulToday(now)
                Spacer(GlanceModifier.height(6.dp))
                EventList(upcomingEvents(data.events, today), today)
            }
            // 작은 크기에서도 갱신 시각은 보여야 오래된 값을 알아챌 수 있다
            Spacer(GlanceModifier.defaultWeight())
            Footer(data, now)
        }
    }
}

@Composable
private fun QuoteTile(quote: Quote, modifier: GlanceModifier) {
    val alpha = if (quote.stale) 0.5f else 1f
    val trendColor = when (trendOf(quote.change_pct)) {
        Trend.UP -> UP
        Trend.DOWN -> DOWN
        Trend.FLAT -> SUBTLE
    }
    Column(modifier = modifier.padding(horizontal = 2.dp, vertical = 1.dp)) {
        Text(quote.name, style = style(SUBTLE.copy(alpha = alpha), 10), maxLines = 1)
        Text(formatPrice(quote.price), style = style(TEXT.copy(alpha = alpha), 13, bold = true), maxLines = 1)
        Text(formatChangePct(quote.change_pct), style = style(trendColor.copy(alpha = alpha), 10), maxLines = 1)
    }
}

@Composable
private fun EventList(events: List<Event>, today: LocalDate) {
    Text("다가오는 발표", style = style(SUBTLE, 10))
    if (events.isEmpty()) {
        Text("2주 안에 예정된 발표가 없습니다", style = style(SUBTLE, 12))
    }
    events.forEach { event ->
        val date = LocalDate.parse(event.date)
        val isToday = date == today
        val prefix = if (isToday) "오늘 · " else ""
        Text("$prefix${formatEventDate(date)} ${event.title}", style = style(TEXT, 12, bold = isToday), maxLines = 1)
    }
}

@Composable
private fun Footer(data: MarketData, now: ZonedDateTime) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatUpdatedAt(OffsetDateTime.parse(data.updated_at), now),
            style = style(SUBTLE, 10),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text("새로고침", style = style(DOWN, 11, bold = true))
    }
}
