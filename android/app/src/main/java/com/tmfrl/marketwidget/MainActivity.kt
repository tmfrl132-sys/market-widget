package com.tmfrl.marketwidget

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** 설치 안내 한 장. 핵심 기능은 홈 화면 위젯이다. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.install_guide)
            textSize = 18f
            setPadding(48, 96, 48, 48)
        })
    }
}
