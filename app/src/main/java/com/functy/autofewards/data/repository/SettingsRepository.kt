package com.functy.autofewards.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.functy.autofewards.AutoFewardsApp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** KSU 同款形态的轻量设置仓储（SharedPreferences）。 */
interface SettingsRepository {
    var uiMode: String
    var themeMode: Int
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var miuixMonet: Boolean
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var enableFloatingBottomBarBlur: Boolean
    var enableNavigationBadge: Boolean
    var pageScale: Float
}

class SettingsRepositoryImpl : SettingsRepository {
    val prefs: SharedPreferences =
        AutoFewardsApp.instance.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var uiMode: String
        get() = prefs.getString("ui_mode", "miuix") ?: "miuix"
        set(value) = prefs.edit().putString("ui_mode", value).apply()

    override var themeMode: Int
        get() = prefs.getInt("color_mode", 0)
        set(value) = prefs.edit().putInt("color_mode", value).apply()

    override var keyColor: Int
        get() = prefs.getInt("key_color", 0)
        set(value) = prefs.edit().putInt("key_color", value).apply()

    override var colorStyle: String
        get() = prefs.getString("color_style", "TonalSpot") ?: "TonalSpot"
        set(value) = prefs.edit().putString("color_style", value).apply()

    override var colorSpec: String
        get() = prefs.getString("color_spec", "SPEC_2025") ?: "SPEC_2025"
        set(value) = prefs.edit().putString("color_spec", value).apply()

    override var miuixMonet: Boolean
        get() = prefs.getBoolean("miuix_monet", false)
        set(value) = prefs.edit().putBoolean("miuix_monet", value).apply()

    override var enableBlur: Boolean
        get() = prefs.getBoolean("enable_blur", false)
        set(value) = prefs.edit().putBoolean("enable_blur", value).apply()

    override var enableFloatingBottomBar: Boolean
        get() = prefs.getBoolean("enable_floating_bottom_bar", false)
        set(value) = prefs.edit().putBoolean("enable_floating_bottom_bar", value).apply()

    override var enableFloatingBottomBarBlur: Boolean
        get() = prefs.getBoolean("enable_floating_bottom_bar_blur", false)
        set(value) = prefs.edit().putBoolean("enable_floating_bottom_bar_blur", value).apply()

    override var enableNavigationBadge: Boolean
        get() = prefs.getBoolean("enable_navigation_badge", true)
        set(value) = prefs.edit().putBoolean("enable_navigation_badge", value).apply()

    override var pageScale: Float
        get() = prefs.getFloat("page_scale", 1.0f)
        set(value) = prefs.edit().putFloat("page_scale", value).apply()

    // ── 任务开关（M4 引擎使用；UI 分栏的总开关 + 分项）──
    val wbEnabled: Boolean get() = prefs.getBoolean("task_wb_enabled", true)
    val mhyEnabled: Boolean get() = prefs.getBoolean("task_mhy_enabled", true)
    val mhyGameSign: Boolean get() = prefs.getBoolean("mhy_game_sign", true)
    val mhyBbsSign: Boolean get() = prefs.getBoolean("mhy_bbs_sign", true)
    val mhyRead: Boolean get() = prefs.getBoolean("mhy_read", true)
    val mhyLike: Boolean get() = prefs.getBoolean("mhy_like", true)
    val mhyCancelLike: Boolean get() = prefs.getBoolean("mhy_cancel_like", true)
    val mhyShare: Boolean get() = prefs.getBoolean("mhy_share", true)
    val bingEnabled: Boolean get() = prefs.getBoolean("task_bing_enabled", true)
    val bingFallback: Boolean get() = prefs.getBoolean("bing_fallback", true)
    val bingProgress: Boolean get() = prefs.getBoolean("bing_progress", true)
    val taskNotification: Boolean get() = prefs.getBoolean("task_notification", true)

    /** 游戏签到启用的游戏，逗号分隔：genshin/starrail/zzz/honkai3rd/tears/honkai2 */
    val mhySignGames: String get() = prefs.getString("mhy_sign_games", "genshin,starrail,zzz") ?: "genshin,starrail,zzz"
    fun setMhySignGames(v: String) = prefs.edit().putString("mhy_sign_games", v).apply()

    /** 社区签到/帖子分区（gids，逗号分隔，默认 5,2 = 大别野/原神） */
    val mhyForums: String get() = prefs.getString("mhy_forums", "5,2") ?: "5,2"
    fun setMhyForums(v: String) = prefs.edit().putString("mhy_forums", v).apply()

    fun setTaskSwitch(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    // ============================================================
    // 敏感数据命名空间（sec.*）：涉密读写只允许通过这组 API，永远不进 observedKeys
    //   sec.mhy.cookie / sec.mhy.stoken / sec.mhy.stuid / sec.mhy.mid
    //   sec.wb.token / sec.wb.uid / sec.wb.domain / sec.wb.enterpriseId
    // ============================================================
    fun secret(key: String): String? = prefs.getString("sec.$key", null)

    fun setSecret(key: String, value: String?) {
        val k = "sec.$key"
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(k).apply()
        } else {
            prefs.edit().putString(k, value).apply()
        }
    }

    fun hasSecret(key: String): Boolean = !secret(key).isNullOrEmpty()

    // ============================================================
    // 每日完成标记（键：wb / mhy / bing，值为 YYYY-MM-DD；跨天自动重置）
    // ============================================================
    fun isDoneToday(task: String): Boolean = prefs.getString("done.$task", null) == today()

    fun markDoneToday(task: String) {
        prefs.edit().putString("done.$task", today()).apply()
    }

    fun clearDoneMark(task: String) {
        prefs.edit().remove("done.$task").apply()
    }

    // ============================================================
    // 配置导出 / 导入（Flutter 版兼容：_format = autorewards-config）
    // ============================================================

    /** 导出全部 prefs（含敏感键）；bingCookies 由调用方（CookieManager）另附。 */
    fun dumpAll(): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> out.put(key, value)
                is Int -> out.put(key, value)
                is Long -> out.put(key, value)
                is Float -> out.put(key, value.toDouble())
                is String -> out.put(key, value)
            }
        }
        return out
    }

    fun exportJson(bingCookies: JSONArray): String {
        val payload = JSONObject()
            .put("_format", EXPORT_FORMAT)
            .put("_version", 1)
            .put("exported_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
            .put("prefs", dumpAll())
            .put("bingCookies", bingCookies)
        return payload.toString(2)
    }

    /**
     * 导入 Flutter 版 / 本应用导出的配置 JSON（只合并，不删除本机已有键）。
     * Flutter 版 prefs 键带 `flutter.` 前缀（如 flutter.sec.mhy.stoken）——自动剥前缀映射。
     * 返回写入的键数量。
     */
    fun importJson(jsonStr: String): Int {
        val map = JSONObject(jsonStr.trim())
        val prefsObj = if (map.has("prefs") && map.optJSONObject("prefs") != null) {
            map.getJSONObject("prefs")
        } else {
            map
        }
        var count = 0
        val editor = prefs.edit()
        for (rawKey in prefsObj.keys()) {
            val key = if (rawKey.startsWith("flutter.")) rawKey.removePrefix("flutter.") else rawKey
            // 只导入语义认可的键：sec.* / done.* / 任务开关 / mhy 配置（避免垃圾键污染）
            val accepted = key.startsWith("sec.") || key.startsWith("done.") ||
                key in observedKeys || key.startsWith("mhy_") || key.startsWith("bing_")
            if (!accepted) continue
            when (val v = prefsObj.get(rawKey)) {
                is Boolean -> if (key in booleanKeys) { editor.putBoolean(key, v); count++ }
                is Int -> if (key in intKeys) { editor.putInt(key, v); count++ }
                is Double -> if (key in floatKeys) { editor.putFloat(key, v.toFloat()); count++ }
                is String -> { editor.putString(key, v); count++ }
                else -> {}
            }
        }
        editor.apply()
        return count
    }

    companion object {
        const val EXPORT_FORMAT = "autorewards-config"

        /** 与 MainActivityViewModel.observedKeys 对齐；外部用于监听设置变化。 */
        val observedKeys = setOf(
            "color_mode", "key_color", "color_style", "color_spec",
            "page_scale", "enable_blur", "enable_floating_bottom_bar",
            "enable_floating_bottom_bar_blur", "enable_navigation_badge", "ui_mode",
            "task_wb_enabled", "task_mhy_enabled", "mhy_game_sign", "mhy_bbs_sign",
            "mhy_read", "mhy_like", "mhy_cancel_like", "mhy_share",
            "task_bing_enabled", "bing_fallback", "bing_progress", "task_notification",
            "miuix_monet",
        )

        private val booleanKeys = observedKeys + setOf(
            "app_auto_run", "app_predictive_back", "bing_auto_start",
        )
        private val intKeys = setOf("color_mode", "key_color")
        private val floatKeys = setOf("page_scale")

        private fun today(): String {
            val n = java.util.Calendar.getInstance()
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(n.time)
        }
    }
}

/** 监听上述键变化的 Flow（供 ViewModel 组装 uiState 用）。 */
fun SharedPreferences.changes(keys: Set<String>): Flow<String?> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in keys) trySend(key)
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
