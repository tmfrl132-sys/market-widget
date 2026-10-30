package com.tmfrl.marketwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tmfrl.marketwidget.widget.MarketWidgetReceiver

/** 설치 안내와 "홈 화면에 위젯 추가" 버튼. 핵심 기능은 홈 화면 위젯이다. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = getString(R.string.install_guide)
                textSize = 18f
            })
            addView(Button(context).apply {
                text = getString(R.string.add_widget)
                setOnClickListener { requestPinWidget() }
            })
        }
        // 안드로이드 15부터 화면이 상태 표시줄 밑까지 그려지므로 시스템 영역만큼 띄운다
        setContentView(FrameLayout(this).apply {
            fitsSystemWindows = true
            addView(content)
        })
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
