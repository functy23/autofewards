package com.functy.autofewards.data.repository

import com.functy.autofewards.core.AppLog
import com.functy.autofewards.core.HttpBox
import org.json.JSONObject

/** WorkBuddy 任务结果（Flutter 版 WorkBuddyResult 翻译）。 */
class WorkBuddyResult(val ok: Boolean, val summary: String, val steps: List<String>) {
    override fun toString(): String = summary
}

/**
 * WorkBuddy（原 CodeBuddy）每日积分领取模块。
 * Flutter 版 lib/services/workbuddy/workbuddy_service.dart 的 Kotlin 翻译。
 *
 * - 签到接口：POST copilot.tencent.com/billing/meter/daily-checkin 与 checkin-status
 *   鉴权：Authorization: Bearer accessToken，body 为空 JSON `{}`，无额外签名
 * - 幂等：HTTP 400 + code=10001 = 今日已签到（官方幂等拒绝，视为成功）
 * - ⚠️ `today_checked_in` 字段不可靠（曾返回 false 但实际已签）——
 *   签到成功以 daily-checkin 的 code==0/10001 为准
 */
class WorkBuddyService(
    private val repo: SettingsRepositoryImpl,
    private val http: HttpBox = HttpBox(retries = 1),
) {
    companion object {
        const val API_BASE = "https://copilot.tencent.com"
    }

    fun importToken(token: String, uid: String = "", domain: String = "", enterpriseId: String = "") {
        repo.setSecret("wb.token", token.trim())
        repo.setSecret("wb.uid", uid)
        repo.setSecret("wb.domain", domain)
        repo.setSecret("wb.enterpriseId", enterpriseId)
        AppLog.i("WB", "token 手动导入成功 (uid=$uid)")
    }

    fun hasToken(): Boolean = !repo.secret("wb.token").isNullOrEmpty()

    /** 查询今日真实签到状态（checkin-status 接口）。null = 未配置/查询失败。 */
    suspend fun checkStatus(): Boolean? {
        val token = repo.secret("wb.token") ?: return null
        if (token.isEmpty()) return null
        val st = http.post(
            "$API_BASE/billing/meter/checkin-status",
            headers = wbHeaders(token),
            body = "{}",
        )
        if (st.status == 401 || st.status == 403 || !st.ok) return null
        return try {
            st.tryJsonObject()?.optJSONObject("data")?.optBoolean("today_checked_in") == true
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkin(): WorkBuddyResult {
        val steps = mutableListOf<String>()
        val token = repo.secret("wb.token")
        if (token.isNullOrEmpty()) {
            return WorkBuddyResult(false, "WorkBuddy 未配置 token", listOf("请先在账号页粘贴 accessToken"))
        }
        val headers = wbHeaders(token)

        // 1. 查询状态（只用于省一次请求 + 401 探测；幂等兜底在 checkin 的 10001）
        val st = http.post("$API_BASE/billing/meter/checkin-status", headers = headers, body = "{}")
        if (st.status == 401 || st.status == 403) {
            return WorkBuddyResult(
                false, "token 已过期（HTTP ${st.status}）",
                steps + "请打开 WorkBuddy 桌面端刷新登录态后重试",
            )
        }
        if (st.ok) {
            try {
                val checked = st.tryJsonObject()?.optJSONObject("data")?.optBoolean("today_checked_in")
                // 注意：today_checked_in 不可靠，仅作快速短路
                if (checked == true) {
                    steps.add("今日已签到（状态接口返回），无需重复领取")
                    return WorkBuddyResult(true, "WorkBuddy 今日已签到", steps)
                }
            } catch (_: Exception) {
            }
        } else {
            steps.add("状态查询 HTTP ${st.status}（继续尝试签到）")
        }

        // 2. 签到
        kotlinx.coroutines.delay(800 + (System.currentTimeMillis() % 900))
        val r = http.post("$API_BASE/billing/meter/daily-checkin", headers = headers, body = "{}")
        if (r.status == 401 || r.status == 403) {
            return WorkBuddyResult(false, "token 已过期（HTTP ${r.status}）", steps)
        }

        // 3. 解析结果（HTTP 400 + code=10001 = 今日已签到，是官方幂等拒绝）
        return try {
            val j = r.jsonSafe()
            when (val code = j.optInt("code", -2)) {
                0 -> {
                    val data = j.optJSONObject("data")
                    val credit = data?.opt("credit") ?: "?"
                    val streak = data?.opt("streak_days") ?: "?"
                    steps.add("🎉 领取成功 credit=$credit, streak_days=$streak")
                    WorkBuddyResult(true, "WorkBuddy 签到成功（+$credit 积分）", steps)
                }
                10001 -> {
                    steps.add("今日已签到（code=10001），无需重复领取")
                    WorkBuddyResult(true, "WorkBuddy 今日已签到", steps)
                }
                else -> {
                    val msg = j.optString("msg", j.optString("message"))
                    steps.add("签到失败 code=$code msg=$msg")
                    WorkBuddyResult(false, "WorkBuddy 签到失败 code=$code", steps)
                }
            }
        } catch (e: Exception) {
            val preview = r.body.take(200)
            steps.add("响应解析失败 HTTP ${r.status}: $preview")
            WorkBuddyResult(false, "WorkBuddy 响应异常", steps)
        }
    }

    private fun wbHeaders(token: String): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "Authorization" to "Bearer $token",
    )
}
