package com.dualsub.player.download

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, message: String) : IOException(message)

class HttpResponse(val code: Int, val bytes: ByteArray) {
    fun text(): String = String(bytes, Charsets.UTF_8)
}

/** Minimal HTTPS client with no extra libraries. Only for the subtitle APIs. */
object HttpClient {

    private const val MAX_BYTES = 15 * 1024 * 1024

    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        request(url, "GET", headers, null)

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        request(url, "POST", headers + ("Content-Type" to "application/json"), json.toByteArray(Charsets.UTF_8))

    fun encodeQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    private fun request(url: String, method: String, headers: Map<String, String>, body: ByteArray?): HttpResponse {
        require(url.startsWith("https://")) { "Only HTTPS is allowed" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }

            val code = connection.responseCode
            if (code >= 400) {
                val message = connection.errorStream?.use { stream ->
                    val buffer = ByteArray(2048)
                    val n = stream.read(buffer)
                    if (n > 0) String(buffer, 0, n, Charsets.UTF_8).take(300) else ""
                }.orEmpty()
                throw HttpException(code, "HTTP $code $message".trim())
            }
            val bytes = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("The response is too large")
                }
                out.toByteArray()
            }
            return HttpResponse(code, bytes)
        } finally {
            connection.disconnect()
        }
    }
}
