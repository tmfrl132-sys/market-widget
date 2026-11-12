package com.tmfrl.marketwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tmfrl.marketwidget.ui.SettingsScreen
import com.tmfrl.marketwidget.widget.MarketWidgetReceiver

/** 설정 화면. 위젯에 보일 지표 켜기·순서, 종목 추가·삭제, 홈 화면에 위젯 추가. 런처 아이콘과 위젯 "설정" 버튼이 연다. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 안드로이드 15부터 화면 끝까지 그리므로 다른 버전도 같게 맞추고, 어두운 배경에 맞게 밝은 상태 표시줄 글자를 쓴다
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen(lifecycle, onAddWidget = ::requestPinWidget) }
    }

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, MarketWidgetReceiver::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
        }
    }
}
