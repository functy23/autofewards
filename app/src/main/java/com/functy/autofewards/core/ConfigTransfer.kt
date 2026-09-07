package com.functy.autofewards.core

import android.content.Context
import android.webkit.CookieManager
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置导入导出桥（Flutter 版 settings_page.dart 导出/导入逻辑的 Kotlin 对应物）。
 *
 * 导出格式与 Flutter 版兼容：根 `{_format:"autorewards-config", prefs:{...}, bingCookies:[...]}`。
 * 导入：prefs 自动剥 `flutter.` 前缀映射；bingCookies 写回 android CookieManager。
 */
object ConfigTransfer {
    private const val BING_URL = "https://www.bing.com"

    /** 导出全部配置（含敏感键）+ Bing cookies（CookieManager 同步读取）。 */
    fun exportJson(): String {
        val repo = SettingsRepositoryImpl()
        val cookies = JSONArray()
        try {
            val raw = CookieManager.getInstance().getCookie(BING_URL)
            if (!raw.isNullOrEmpty()) {
                for (pair in raw.split("; ")) {
                    val idx = pair.indexOf('=')
                    if (idx <= 0) continue
                    cookies.put(
                        JSONObject()
                            .put("name", pair.substring(0, idx))
                            .put("value", pair.substring(idx + 1))
                            .put("domain", "")
                            .put("path", "/"),
                    )
                }
            }
        } catch (e: Exception) {
            AppLog.w("CFG", "收集 Bing Cookie 失败: ${e.message}")
        }
        return repo.exportJson(cookies)
    }

    /**
     * 导入配置 JSON（Flutter 版 / 本应用导出均可）。
     * 返回 导入键数 to Bing cookie 数。
     */
    fun importJson(text: String): Pair<Int, Int> {
        val repo = SettingsRepositoryImpl()
        val count = repo.importJson(text)
        var cookieCount = 0
        try {
            val map = JSONObject(text.trim())
            val arr = map.optJSONArray("bingCookies")
            if (arr != null) {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val name = c.optString("name")
                    if (name.isEmpty()) continue
                    val value = c.optString("value")
                    val domain = c.optString("domain", "")
                    val url = if (domain.isNotEmpty()) "https://$domain" else BING_URL
                    cm.setCookie(url, "$name=$value")
                    cookieCount++
                }
                cm.flush()
            }
        } catch (e: Exception) {
            AppLog.w("CFG", "写入 Bing Cookie 失败: ${e.message}")
        }
        return count to cookieCount
    }
}
