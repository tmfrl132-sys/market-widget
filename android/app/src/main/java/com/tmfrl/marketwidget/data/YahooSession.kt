package com.tmfrl.marketwidget.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TIMEOUT_MS = 10_000

/** quoteSummary 실적 일정 주소(v1.9). crumb는 YahooSession이 붙인다. */
internal const val EARNINGS_URL = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=calendarEvents"

private const val COOKIE_URL = "https://fc.yahoo.com"
private const val CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb"

/** 쿠키·crumb를 이 시간마다 새로 받는다. */
private const val SESSION_MS = 6 * 3_600_000L

private class Response(val code: Int, val body: String, val cookies: List<String>)

private fun request(url: String, cookie: String?): Response {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.useCaches = false
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (cookie != null) conn.setRequestProperty("Cookie", cookie)
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val cookies = conn.headerFields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }
            .map { it.substringBefore(';') }
        return Response(code, body, cookies)
    } finally {
        conn.disconnect()
    }
}

/**
 * Yahoo quoteSummary는 쿠키와 crumb가 있어야 답한다(v1.9 실적 일정). fc.yahoo.com에서 쿠키를 받고,
 * 그 쿠키로 getcrumb를 받아 주소에 붙인다. 401·403이면 세션을 새로 받아 한 번 더 시도한다.
 * 실패하면 IOException이고, 부르는 쪽이 이전 값을 쓴다. 메인 스레드에서 부르지 않는다.
 */
internal object YahooSession {
    private var cookie: String? = null
    private var crumb: String? = null
    private var at = 0L

    @Synchronized
    fun get(url: String): String {
        repeat(2) { attempt ->
            if (attempt > 0 || crumb == null || System.currentTimeMillis() - at > SESSION_MS) refresh()
            val response = request("$url&crumb=${URLEncoder.encode(crumb, "UTF-8")}", cookie)
            if (response.code == HttpURLConnection.HTTP_OK) return response.body
            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED && response.code != HttpURLConnection.HTTP_FORBIDDEN) {
                throw IOException("HTTP ${response.code}")
            }
        }
        throw IOException("Yahoo crumb 거부")
    }

    private fun refresh() {
        val cookies = request(COOKIE_URL, null).cookies
        if (cookies.isEmpty()) throw IOException("Yahoo 쿠키 없음")
        val jar = cookies.joinToString("; ")
        val response = request(CRUMB_URL, jar)
        val value = response.body.trim()
        if (response.code != HttpURLConnection.HTTP_OK || value.isEmpty() || value.contains('<')) throw IOException("crumb 실패")
        cookie = jar
        crumb = value
        at = System.currentTimeMillis()
    }
}
