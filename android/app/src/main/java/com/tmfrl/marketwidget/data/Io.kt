package com.tmfrl.marketwidget.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TIMEOUT_MS = 10_000

/** 위젯이 쓰다 만 파일을 읽지 않게 임시 파일에 다 쓴 뒤 한 번에 바꿔치기한다. */
internal fun writeAtomic(target: File, text: String) {
    val temp = File.createTempFile(target.nameWithoutExtension, ".tmp", target.parentFile)
    try {
        temp.writeText(text)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temp.delete()
    }
}

/**
 * GET 요청 본문. 200이 아니면 IOException. 메인 스레드에서 부르지 않는다(Dispatchers.IO에서 부른다).
 * gzip은 HttpURLConnection이 알아서 요청하고 푼다.
 */
internal fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.useCaches = false
        // Yahoo는 브라우저 이름이 없는 요청을 막는다
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/** 네트워크 오류나 잘못된 응답(SerializationException 포함)이면 fallback을 돌려준다. */
internal inline fun <T> orElse(fallback: T, block: () -> T): T = try {
    block()
} catch (e: IOException) {
    fallback
} catch (e: IllegalArgumentException) {
    fallback
}
