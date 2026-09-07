package com.functy.autofewards.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 统一 HTTP 结果（Flutter 版 HttpResult 翻译）。 */
class HttpResult(
    val status: Int,
    val body: String,
) {
    val ok: Boolean get() = status in 200..299

    /** 保守 JSON 对象解析；非 JSON 返回 null（调用方走 jsonSafe）。 */
    fun tryJsonObject(): JSONObject? = try {
        JSONObject(body)
    } catch (_: JSONException) {
        null
    }

    /**
     * 安全解析：非 JSON 响应（如 403 的 HTML/纯文本）不抛异常，
     * 返回合成的错误结构——任务流程据此记失败步骤而不是整体中断（坑位 4 / A.14.1）。
     */
    fun jsonSafe(): JSONObject {
        tryJsonObject()?.let { return it }
        val preview = if (body.length > 60) body.substring(0, 60) + "…" else body
        return JSONObject().put("retcode", -1).put("message", "响应不是 JSON (HTTP $status): $preview")
    }
}

/**
 * 统一 HTTP 客户端封装（Flutter 版 HttpBox 翻译，OkHttp 实现）。
 * - 统一超时 / 重试（指数退避）
 * - 统一日志（只记 URL + 状态码 + 耗时，不打 body）
 */
class HttpBox(
    private val timeoutSeconds: Long = 20,
    private val retries: Int = 2,
) {
    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap(), retries: Int = -1): HttpResult =
        send("GET", url, headers, null, retries)

    fun post(url: String, headers: Map<String, String> = emptyMap(), body: String? = null, retries: Int = -1): HttpResult =
        send("POST", url, headers, body, retries)

    private fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        retriesOverride: Int,
    ): HttpResult {
        val tries = if (retriesOverride < 0) retries else retriesOverride
        var lastErr = ""
        for (attempt in 0..tries) {
            try {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                if (method == "GET") {
                    builder.get()
                } else {
                    val payload = body ?: ""
                    val contentType = headers["Content-Type"]?.toMediaType() ?: jsonMediaType
                    builder.post(payload.toRequestBody(contentType))
                }
                val start = System.currentTimeMillis()
                client.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    AppLog.d(
                        "HTTP",
                        "$method $url -> ${resp.code} (${System.currentTimeMillis() - start}ms, ${text.length}B)",
                    )
                    return HttpResult(resp.code, text)
                }
            } catch (e: IOException) {
                lastErr = e.message ?: e.javaClass.simpleName
            } catch (e: Exception) {
                lastErr = e.toString()
            }
            if (attempt < tries) {
                val backoffMs = 600L shl attempt
                AppLog.w("HTTP", "$method $url 第${attempt + 1}次失败($lastErr)，${backoffMs}ms 后重试")
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        AppLog.e("HTTP", "$method $url 最终失败: $lastErr")
        return HttpResult(0, "{\"retcode\":-1,\"message\":\"network error: $lastErr\"}")
    }

    companion object {
        const val UA_MOBILE_CHROME =
            "Mozilla/5.0 (Linux; Android 12; Unspecified Device) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/103.0.5060.129 Mobile Safari/537.36"
        const val UA_DESKTOP =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
