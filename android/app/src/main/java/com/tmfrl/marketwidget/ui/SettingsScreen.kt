package com.tmfrl.marketwidget.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tmfrl.marketwidget.data.CustomRepository
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.data.SettingsStore
import com.tmfrl.marketwidget.data.TickerSearchClient
import com.tmfrl.marketwidget.model.BUILT_IN_NAMES
import com.tmfrl.marketwidget.model.CustomEntry
import com.tmfrl.marketwidget.model.CustomTicker
import com.tmfrl.marketwidget.model.MAX_CUSTOM
import com.tmfrl.marketwidget.model.TickerCandidate
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.addCustom
import com.tmfrl.marketwidget.model.customId
import com.tmfrl.marketwidget.model.customStatusText
import com.tmfrl.marketwidget.model.isAdded
import com.tmfrl.marketwidget.model.mergeCandidates
import com.tmfrl.marketwidget.model.moveDown
import com.tmfrl.marketwidget.model.moveUp
import com.tmfrl.marketwidget.model.naverCodeFor
import com.tmfrl.marketwidget.model.orderedIds
import com.tmfrl.marketwidget.model.removeCustom
import com.tmfrl.marketwidget.model.resetOrder
import com.tmfrl.marketwidget.model.searchMessage
import com.tmfrl.marketwidget.model.searchReady
import com.tmfrl.marketwidget.model.setHidden
import com.tmfrl.marketwidget.model.shownCount
import com.tmfrl.marketwidget.model.withObjectParticle
import com.tmfrl.marketwidget.widget.redrawAllWidgets
import com.tmfrl.marketwidget.work.RefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 위젯과 같은 어두운 색. 같은 앱으로 보이게 한다.
private val BACKGROUND = Color(0xFF111827)
private val TILE = Color(0xFF1B2433)
private val TEXT = Color(0xFFF3F4F6)
private val SUBTLE = Color(0xFF8B95A7)
private val ON = Color(0xFF4D8DFF)

private val COLORS = darkColorScheme(
    primary = ON,
    onPrimary = TEXT,
    background = BACKGROUND,
    onBackground = TEXT,
    surface = BACKGROUND,
    onSurface = TEXT,
    surfaceVariant = TILE,
    onSurfaceVariant = SUBTLE,
    surfaceContainerHigh = TILE,
    outline = SUBTLE,
)

/** 이 폭(dp) 이상이면 지표 목록과 종목 추가를 좌우 두 칸으로 놓는다(태블릿 가로). */
private const val TWO_PANE_DP = 840

/** 글자를 입력하고 이만큼 멈추면 자동으로 검색한다. */
private const val SEARCH_DELAY_MS = 400L

/** 다른 곳(갱신 작업)이 추가 종목 캐시를 고쳤는지 보는 간격. 화면이 보일 때만 본다. */
private const val STATUS_POLL_MS = 3_000L

/**
 * 설정 화면 상태와 동작. ViewModel 없이 화면 하나에서만 쓴다.
 * 켜기·순서·삭제·기본값은 바로 저장하고 위젯을 다시 그린다(저장 버튼 없음). 추가와 꺼 둔 추가 종목 켜기는 바로 받기도 시작한다.
 */
private class SettingsModel(
    private val context: Context,
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
) {
    private val store = SettingsStore.forContext(context)
    private val customRepository = CustomRepository.forContext(context)

    var settings by mutableStateOf(WidgetSettings())
    /** 기본 지표(id, 이름). data.json 캐시가 있으면 그 순서·이름, 없으면 BUILT_IN_NAMES. */
    var builtIn by mutableStateOf(BUILT_IN_NAMES.toList())
    var entries by mutableStateOf(emptyMap<String, CustomEntry>())
    var query by mutableStateOf("")
    var results by mutableStateOf(emptyList<TickerCandidate>())
    var message by mutableStateOf<String?>(null)
    var searching by mutableStateOf(false)
    var checking by mutableStateOf(emptySet<String>())
    var confirmDelete by mutableStateOf<CustomTicker?>(null)
    var confirmReset by mutableStateOf(false)
    private var entriesStamp = -1L

    val builtInIds: List<String> get() = builtIn.map { it.first }
    val ids: List<String> get() = orderedIds(builtInIds, settings)
    val shown: Int get() = shownCount(builtInIds, settings)
    val full: Boolean get() = settings.custom.size >= MAX_CUSTOM

    fun nameOf(id: String): String =
        settings.custom.firstOrNull { it.id == id }?.name ?: builtIn.firstOrNull { it.first == id }?.second ?: id

    fun detailOf(id: String): String {
        val ticker = settings.custom.firstOrNull { it.id == id } ?: return "$id · 기본"
        return "${ticker.symbol} · ${customStatusText(entries[id])}"
    }

    suspend fun load() {
        val (saved, quotes) = withContext(Dispatchers.IO) {
            store.read() to Repository.forContext(context).cached()?.quotes
        }
        settings = saved
        quotes?.takeIf { it.isNotEmpty() }?.let { list -> builtIn = list.map { it.id to it.name } }
        reloadEntries()
    }

    /** 추가 종목 캐시가 바뀌었으면 다시 읽는다(상태 문구 "HH:mm 받음"). */
    suspend fun reloadEntries() {
        val stamp = withContext(Dispatchers.IO) { customRepository.lastModified() }
        if (stamp == entriesStamp) return
        entriesStamp = stamp
        entries = withContext(Dispatchers.IO) { customRepository.entries().associateBy { it.id } }
    }

    /** 저장하고 위젯을 다시 그린다. fetch면 바로 받기도 시작한다. */
    private fun change(fetch: Boolean = false, transform: (WidgetSettings) -> WidgetSettings) {
        scope.launch {
            settings = store.update(transform)
            quietly { redrawAllWidgets(context) }
            if (fetch) RefreshWorker.runOnce(context)
        }
    }

    fun up(id: String) = change { moveUp(builtInIds, it, id) }

    fun down(id: String) = change { moveDown(builtInIds, it, id) }

    fun toggle(id: String, show: Boolean) {
        // 꺼 둔 추가 종목은 받지 않았으니 다시 켜면 바로 받는다
        val fetch = show && settings.custom.any { it.id == id }
        change(fetch) { setHidden(builtInIds, it, id, hidden = !show) }
    }

    fun delete(ticker: CustomTicker) = change { removeCustom(it, ticker.id) }

    fun reset() = change { resetOrder(it) }

    fun showMessage(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    /** 검색어가 바뀌면 0.4초 기다렸다가 검색한다. 그사이 또 바뀌면 이 호출은 취소된다. */
    suspend fun autoSearch(text: String) {
        if (!searchReady(text)) {
            results = emptyList()
            message = null
            return
        }
        delay(SEARCH_DELAY_MS)
        search(text)
    }

    fun searchNow() {
        val text = query
        if (text.isBlank()) return
        scope.launch { search(text) }
    }

    private suspend fun search(text: String) {
        searching = true
        try {
            val result = TickerSearchClient.search(text)
            results = mergeCandidates(result.naver.orEmpty(), result.yahoo.orEmpty())
            message = searchMessage(text, result.naver, result.yahoo)
        } finally {
            searching = false
        }
    }

    fun add(candidate: TickerCandidate) {
        if (candidate.symbol in checking) return
        checking = checking + candidate.symbol
        scope.launch {
            try {
                val check = TickerSearchClient.verify(candidate.symbol)
                if (check == null) {
                    showMessage("이 종목은 시세를 받을 수 없습니다")
                    return@launch
                }
                val ticker = CustomTicker(
                    id = customId(candidate.symbol),
                    symbol = candidate.symbol,
                    name = candidate.name,
                    kind = check.kind,
                    naver_code = naverCodeFor(candidate.symbol, check.kind),
                )
                val before = settings
                settings = store.update { addCustom(it, ticker) }
                if (settings == before) return@launch
                // 값이 빈 타일이 바로 생기고, 받기 작업이 끝나면 값과 차트가 채워진다
                quietly { redrawAllWidgets(context) }
                RefreshWorker.runOnce(context)
                showMessage("${withObjectParticle(ticker.name)} 추가했습니다. 위젯에 곧 나타납니다")
            } finally {
                checking = checking - candidate.symbol
            }
        }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 위젯이 없거나 다시 그리기에 실패해도 설정은 저장되어 있고 다음 갱신 때 반영된다
        }
    }
}

@Composable
fun SettingsScreen(lifecycle: Lifecycle, onAddWidget: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val model = remember { SettingsModel(context, scope, SnackbarHostState()) }

    LaunchedEffect(Unit) {
        model.load()
        // 화면이 보이는 동안 갱신 작업이 끝났는지 보고 상태 문구를 고친다
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                model.reloadEntries()
                delay(STATUS_POLL_MS)
            }
        }
    }
    LaunchedEffect(model.query) { model.autoSearch(model.query) }

    MaterialTheme(colorScheme = COLORS) {
        Scaffold(
            containerColor = BACKGROUND,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(model.snackbar) },
        ) { padding ->
            val wide = LocalConfiguration.current.screenWidthDp >= TWO_PANE_DP
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Text(
                    "경제지표 설정",
                    color = TEXT,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (wide) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LazyColumn(modifier = Modifier.weight(1f)) { indicatorItems(model) }
                        LazyColumn(modifier = Modifier.weight(1f)) { searchItems(model) }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        indicatorItems(model)
                        item { Spacer(Modifier.height(24.dp)) }
                        searchItems(model)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = onAddWidget) { Text("홈 화면에 위젯 추가") }
                }
            }
            ConfirmDialogs(model)
        }
    }
}

private fun LazyListScope.indicatorItems(model: SettingsModel) {
    item(key = "indicator-header") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("위젯에 보일 지표", color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("켜짐 ${model.shown} / ${model.ids.size}", color = SUBTLE, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { model.confirmReset = true }) { Text("기본값으로") }
        }
        if (model.shown <= 1) {
            Text("최소 1개는 켜 두어야 합니다", color = SUBTLE, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
    val ids = model.ids
    items(ids, key = { it }) { id ->
        val index = ids.indexOf(id)
        val shown = id !in model.settings.hidden
        val ticker = model.settings.custom.firstOrNull { it.id == id }
        IndicatorRow(
            name = model.nameOf(id),
            detail = model.detailOf(id),
            shown = shown,
            canUp = index > 0,
            canDown = index < ids.lastIndex,
            // 마지막 하나 남은 켜진 지표는 끌 수 없다
            canToggle = !shown || model.shown > 1,
            onUp = { model.up(id) },
            onDown = { model.down(id) },
            onToggle = { model.toggle(id, it) },
            onDelete = ticker?.let { { model.confirmDelete = it } },
        )
        HorizontalDivider(color = TILE)
    }
}

@Composable
private fun IndicatorRow(
    name: String, detail: String, shown: Boolean, canUp: Boolean, canDown: Boolean, canToggle: Boolean,
    onUp: () -> Unit, onDown: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: (() -> Unit)?,
) {
    val compact = PaddingValues(horizontal = 4.dp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        TextButton(onClick = onUp, enabled = canUp, contentPadding = compact) { Text("▲") }
        TextButton(onClick = onDown, enabled = canDown, contentPadding = compact) { Text("▼") }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = TEXT, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = SUBTLE, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, contentPadding = compact) { Text("삭제") }
        }
        Switch(checked = shown, onCheckedChange = onToggle, enabled = canToggle)
    }
}

private fun LazyListScope.searchItems(model: SettingsModel) {
    item(key = "search-header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("종목 추가", color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${model.settings.custom.size} / 최대 $MAX_CUSTOM", color = SUBTLE, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.query = it },
                placeholder = { Text("이름이나 코드 (예: 삼성전자, AAPL)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.searchNow() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { model.searchNow() }) { Text("검색") }
        }
        if (model.full) {
            Text("최대 ${MAX_CUSTOM}개까지 추가할 수 있습니다", color = SUBTLE, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (model.searching) {
            Text("검색 중…", color = SUBTLE, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        model.message?.let { Text(it, color = SUBTLE, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(4.dp))
    }
    items(model.results, key = { "result-${it.symbol}" }) { candidate ->
        val added = isAdded(model.settings, candidate.symbol)
        val checking = candidate.symbol in model.checking
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.name, color = TEXT, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${candidate.displayCode} · ${candidate.market}", color = SUBTLE, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { model.add(candidate) },
                enabled = !added && !checking && !model.full,
            ) {
                Text(
                    when {
                        added -> "추가됨"
                        checking -> "확인 중"
                        else -> "추가"
                    },
                )
            }
        }
        HorizontalDivider(color = TILE)
    }
}

@Composable
private fun ConfirmDialogs(model: SettingsModel) {
    model.confirmDelete?.let { ticker ->
        AlertDialog(
            onDismissRequest = { model.confirmDelete = null },
            text = { Text("${withObjectParticle(ticker.name)} 지울까요?") },
            confirmButton = {
                TextButton(onClick = {
                    model.confirmDelete = null
                    model.delete(ticker)
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { model.confirmDelete = null }) { Text("취소") } },
        )
    }
    if (model.confirmReset) {
        AlertDialog(
            onDismissRequest = { model.confirmReset = false },
            text = { Text("순서를 처음대로 되돌리고 모든 지표를 켭니다. 추가한 종목은 그대로 둡니다.") },
            confirmButton = {
                TextButton(onClick = {
                    model.confirmReset = false
                    model.reset()
                }) { Text("기본값으로") }
            },
            dismissButton = { TextButton(onClick = { model.confirmReset = false }) { Text("취소") } },
        )
    }
}
