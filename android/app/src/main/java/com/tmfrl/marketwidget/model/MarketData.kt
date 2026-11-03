@file:Suppress("PropertyName")

package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    val stale: Boolean = false,
)

@Serializable
data class Event(val date: String, val title: String, val country: String)

@Serializable
data class MarketData(val updated_at: String, val quotes: List<Quote>, val events: List<Event>)

private val json = Json { ignoreUnknownKeys = true }

fun parseMarketData(text: String): MarketData = json.decodeFromString(text)

fun encodeMarketData(data: MarketData): String = json.encodeToString(data)
