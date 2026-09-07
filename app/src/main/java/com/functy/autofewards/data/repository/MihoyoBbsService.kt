package com.functy.autofewards.data.repository

import com.functy.autofewards.core.AppLog
import com.functy.autofewards.core.DsSign
import com.functy.autofewards.core.HttpBox
import com.functy.autofewards.core.HttpResult
import com.functy.autofewards.core.MihoyoIds
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 米游社任务结果（Flutter 版 MhyResult 翻译）。 */
class MhyResult(val ok: Boolean, val summary: String, val steps: List<String>) {
    override fun toString(): String = summary
}

private class MhyPost(val id: String, val title: String, val gids: String)

/**
 * 米游社（miyoushe / 米游币）自动任务模块。
 * Flutter 版 lib/services/mihoyobbs/mihoyobbs_service.dart 的 1:1 Kotlin 手工翻译。
 *
 * 逆向来源：MihoyoBBSTools-master + MiyoQian；踩坑结论（务必遵守）：
 * - stoken 验证**必须用老接口** getCookieAccountInfoBySToken——绝不要用
 *   ma-cn-session getTokenBySToken（对非官方设备风控 -5300，全新真 stoken 也死）
 * - web salt（G1ktdwFL…）配 2.106.2 用于 luna 游戏签到；app salt（idMMaGYm…）
 *   配 2.106.2 用于米游币任务；X6 salt 只做 POST 签名
 * - 点赞用 post/api/post/upvote（勿用旧 apihub/sapi/upvotePost）
 * - gids 与 forum_id 是两套编号（2↔26）
 * - retcode -100 = 登录态过期 → 刷 cookie_token 重试一次；1034 = 极验 → 跳过
 */
class MihoyoBbsService(
    private val repo: SettingsRepositoryImpl,
    private val http: HttpBox = HttpBox(retries = 1),
) {
    companion object {
        const val BBS_API = "https://bbs-api.miyoushe.com"
        const val WEB_API = "https://api-takumi.mihoyo.com"
        const val PASSPORT_API = "https://passport-api.mihoyo.com"
        const val TAKUMI_API = "https://api-takumi.mihoyo.com"
        const val ZZZ_ACT_API = "https://act-nap-api.mihoyo.com"

        const val APP_VERSION = "2.109.0"
        const val CLIENT_TYPE_ANDROID = "2"
        const val CLIENT_TYPE_WEB = "5"
        const val VERIFY_KEY = "bll8iq97cem8"

        private const val MIYO_UA =
            "Mozilla/5.0 (Linux; Android 12; Unspecified Device) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/103.0.5060.129 Mobile Safari/537.36 miHoYoBBS/2.106.2"

        /** gids → (id, forum_id, name)，与 MiyoQian BBS_FORUMS 一致 */
        val FORUMS: Map<String, Triple<String, String, String>> = mapOf(
            "1" to Triple("1", "1", "崩坏3"),
            "2" to Triple("2", "26", "原神"),
            "3" to Triple("3", "30", "崩坏2"),
            "4" to Triple("4", "37", "未定事件簿"),
            "5" to Triple("5", "34", "大别野"),
            "6" to Triple("6", "52", "崩坏：星穹铁道"),
            "8" to Triple("8", "57", "绝区零"),
        )
        val FORUM_ID_TO_GIDS: Map<String, String> = mapOf(
            "1" to "1", "26" to "2", "30" to "3", "37" to "4",
            "34" to "5", "52" to "6", "57" to "8",
        )
    }

    /** 游戏常量表（MiyoQian constants.py 原值）。 */
    data class Game(
        val key: String,
        val name: String,
        val gameBiz: String,
        val actId: String,
        val zzz: Boolean,
        val signGame: String,
    )

    val GAMES: Map<String, Game> = mapOf(
        "genshin" to Game("genshin", "原神", "hk4e_cn", "e202311201442471", false, "hk4e"),
        "starrail" to Game("starrail", "崩坏：星穹铁道", "hkrpg_cn", "e202304121516551", false, ""),
        "zzz" to Game("zzz", "绝区零", "nap_cn", "e202406242138391", true, "zzz"),
        "honkai3rd" to Game("honkai3rd", "崩坏3", "bh3_cn", "e202306201626331", false, ""),
        "tears" to Game("tears", "未定事件簿", "nxx_cn", "e202202251749321", false, ""),
        "honkai2" to Game("honkai2", "崩坏学园2", "bh2_cn", "e202203291431091", false, ""),
    )

    // 登录态（内存态；loadSaved 从 repo 恢复）
    private var stoken: String = ""
    private var stuid: String = ""
    private var mid: String = ""
    private var cookie: String = "" // 完整用户 cookie（任务查询用 web 登录态）
    private var deviceId: String = ""
    private var deviceFp: String = ""

    private val rng = Random(System.nanoTime())

    // ============================================================
    // 登录态解析
    // ============================================================

    /** 从用户粘贴的 cookie 串解析登录态，并做一次活体验证。 */
    suspend fun importCookie(rawCookie: String): MhyResult {
        val steps = mutableListOf<String>()
        val tidy = rawCookie.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
        cookie = tidy

        var st = extract(tidy, "stoken")
        val uid = extract(tidy, "stuid") ?: extract(tidy, "ltuid") ?: extract(tidy, "account_id")
        val midV = extract(tidy, "mid") ?: extract(tidy, "account_mid_v2") ?: extract(tidy, "ltmid_v2")
        val loginTicket = extract(tidy, "login_ticket")

        // B. login_ticket → 换 stoken（v1）
        if (st.isNullOrEmpty() && !loginTicket.isNullOrEmpty() && uid != null) {
            steps.add("检测到 login_ticket，尝试换取 stoken…")
            val r = getStokenByLoginTicket(loginTicket, uid)
            if (r != null) {
                st = r
                steps.add("stoken 获取成功")
            } else {
                steps.add("login_ticket 已失效（有效期约 30 分钟）")
            }
        }

        if (!st.isNullOrEmpty()) {
            stoken = st
            stuid = uid ?: ""
            mid = midV ?: ""
            if (stoken.startsWith("v2_") && mid.isEmpty()) {
                steps.add("⚠️ stoken 为 v2 但 cookie 里没有 mid，任务接口可能不可用")
            }
            deviceId = MihoyoIds.deviceIdFrom(stoken, stuid)
            deviceFp = MihoyoIds.deviceFp(deviceId)

            val ok = verifyStoken()
            if (ok) {
                repo.setSecret("mhy.cookie", tidy)
                repo.setSecret("mhy.stoken", stoken)
                repo.setSecret("mhy.stuid", stuid)
                repo.setSecret("mhy.mid", mid)
                AppLog.i("MHY", "stoken 导入成功 (uid=$stuid)")
                steps.add("✅ stoken 验证通过，登录态已保存到本机")
                return MhyResult(true, "米游社登录态导入成功", steps)
            }
            steps.add("❌ stoken 验证失败（可能已过期）")
            stoken = ""
        }

        // C. 退回纯 cookie 模式（能查询任务，但任务接口需要 stoken）
        if (tidy.contains("cookie_token") || tidy.contains("account_id")) {
            repo.setSecret("mhy.cookie", tidy)
            if (verifyWebCookie()) {
                steps.add("✅ web cookie 可用（仅任务状态查询）")
                steps.add("⚠️ 签到/看帖等任务需要 stoken —— 请用「扫码登录」重新登录")
                return MhyResult(true, "米游社 web cookie 导入成功（无 stoken，任务需扫码登录）", steps)
            }
        }

        return MhyResult(false, "米游社登录态导入失败", steps)
    }

    suspend fun loadSaved() {
        cookie = repo.secret("mhy.cookie") ?: ""
        stoken = repo.secret("mhy.stoken") ?: ""
        stuid = repo.secret("mhy.stuid") ?: ""
        mid = repo.secret("mhy.mid") ?: ""
        if (stoken.isNotEmpty()) {
            deviceId = MihoyoIds.deviceIdFrom(stoken, stuid)
            deviceFp = MihoyoIds.deviceFp(deviceId)
        }
    }

    val hasLogin: Boolean
        get() = stoken.isNotEmpty() ||
            (cookie.contains("cookie_token") && cookie.contains("account_id")) ||
            repo.secret("mhy.stoken")?.isNotEmpty() == true

    val hasStoken: Boolean get() = stoken.isNotEmpty() || repo.secret("mhy.stoken")?.isNotEmpty() == true

    private fun extract(cookieStr: String, key: String): String? {
        val m = Regex("$key=([^;]+)").find(cookieStr) ?: return null
        return m.groupValues[1]
    }

    // ============================================================
    // 登录态兑换 / 验证
    // ============================================================

    /** login_ticket 换 stoken（token_types=3 → stoken v1）。 */
    private suspend fun getStokenByLoginTicket(ticket: String, uid: String): String? {
        val url = "$WEB_API/auth/api/getMultiTokenByLoginTicket" +
            "?login_ticket=$ticket&token_types=3&uid=$uid"
        val r = http.get(url, headers = baseWebHeaders())
        val j = r.jsonSafe()
        if (j.optInt("retcode") == 0) {
            val list = j.optJSONObject("data")?.optJSONArray("list")
            if (list != null && list.length() > 0) {
                return list.getJSONObject(0).optString("token", null)
            }
        }
        AppLog.w("MHY", "getMultiTokenByLoginTicket: ${j.optString("message")}")
        return null
    }

    /**
     * stoken 活体验证 + 刷新 web cookie_token。
     * 用老接口 auth/api/getCookieAccountInfoBySToken（api-takumi，GET），
     * 头仅 Cookie stuid/stoken/mid + UA；不用 ma-cn-session/app/getTokenBySToken
     * ——后者对非官方设备指纹风控 -5300（Flutter 版穷举验证死路）。
     */
    suspend fun verifyStoken(): Boolean {
        if (stoken.isEmpty()) return false
        val r = http.get(
            "$WEB_API/auth/api/getCookieAccountInfoBySToken",
            headers = mapOf(
                "User-Agent" to "okhttp/4.9.3",
                "x-rpc-client_type" to CLIENT_TYPE_ANDROID,
                "x-rpc-app_version" to APP_VERSION,
                "DS" to DsSign.ds1(),
                "Cookie" to stokenCookie(),
                "Referer" to "https://app.mihoyo.com",
            ),
        )
        val j = r.jsonSafe()
        if (j.optInt("retcode") != 0) {
            AppLog.e("MHY", "getCookieAccountInfoBySToken 失败: retcode=${j.optInt("retcode")} ${j.optString("message")}")
            return false
        }
        val ct = j.optJSONObject("data")?.optString("cookie_token", "") ?: ""
        if (ct.isNotEmpty()) {
            cookie = mergeCookieToken(cookie, ct)
            repo.setSecret("mhy.cookie", cookie)
        }
        return true
    }

    private suspend fun verifyWebCookie(): Boolean {
        val r = http.get(
            "$WEB_API/binding/api/getUserGameRolesByCookie",
            headers = mapOf("Cookie" to cookie, "User-Agent" to HttpBox.UA_MOBILE_CHROME),
        )
        return r.jsonSafe().optInt("retcode") == 0
    }

    /** stoken 刷新 web cookie_token；成功即更新 cookie 并持久化。 */
    private suspend fun refreshCookieToken(): Boolean {
        return try {
            val r = http.get(
                "$TAKUMI_API/auth/api/getCookieAccountInfoBySToken",
                headers = mapOf("Cookie" to stokenCookie(), "User-Agent" to MIYO_UA),
            )
            val j = r.jsonSafe()
            if (j.optInt("retcode") != 0) return false
            val ct = j.optJSONObject("data")?.optString("cookie_token", "") ?: ""
            if (ct.isEmpty()) return false
            cookie = mergeCookieToken(cookie, ct)
            repo.setSecret("mhy.cookie", cookie)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mergeCookieToken(cookieStr: String, newToken: String): String {
        return if (cookieStr.contains("cookie_token=")) {
            cookieStr.replaceFirst(Regex("cookie_token=[^;]*"), "cookie_token=$newToken")
        } else if (cookieStr.isEmpty()) {
            "cookie_token=$newToken"
        } else {
            "$cookieStr; cookie_token=$newToken"
        }
    }

    /** 查询今日任务真实完成状态：can_get_points == 0 即全部完成。null = 未配置/查询失败。 */
    suspend fun tasksAllDone(): Boolean? {
        loadSaved()
        if (!hasLogin) return null
        val r = http.get(
            "$BBS_API/apihub/wapi/getUserMissionsState?point_sn=myb",
            headers = baseWebHeaders() + ("Cookie" to cookie),
        )
        val j = r.jsonSafe()
        if (j.optInt("retcode") != 0) return null
        return (j.optJSONObject("data")?.optInt("can_get_points", 1) ?: 1) == 0
    }

    // ============================================================
    // Headers
    // ============================================================

    private fun baseWebHeaders(): Map<String, String> = mapOf(
        "User-Agent" to HttpBox.UA_MOBILE_CHROME,
        "Accept" to "application/json, text/plain, */*",
        "x-rpc-app_version" to APP_VERSION,
        "x-rpc-client_type" to CLIENT_TYPE_WEB,
        "x-rpc-channel" to "miyousheluodi",
        "Accept-Language" to "zh-CN,en-US;q=0.8",
        "Origin" to "https://webstatic.mihoyo.com",
        "Referer" to "https://webstatic.mihoyo.com/",
        "X-Requested-With" to "com.mihoyo.hyperion",
    )

    private fun stokenCookie(): String {
        val sb = StringBuilder("stuid=$stuid;stoken=$stoken")
        if (stoken.startsWith("v2_") && mid.isNotEmpty()) sb.append(";mid=$mid")
        return sb.toString()
    }

    /** MiyoQian 风格 app/bbs 通道头（BBS 2.106.2 salt 配对，米游币任务用）。 */
    private fun miyoAppHeaders(ds: String = ""): Map<String, String> {
        val m = mutableMapOf(
            "DS" to ds,
            "Cookie" to stokenCookie(),
            "x-rpc-client_type" to "2",
            "x-rpc-app_version" to DsSign.BBS_VERSION_V206,
            "x-rpc-sys_version" to "12",
            "x-rpc-channel" to "miyousheluodi",
            "x-rpc-device_id" to deviceId,
            "x-rpc-device_name" to "Xiaomi MI 6",
            "x-rpc-device_model" to "Mi 6",
            "x-rpc-h265_supported" to "1",
            "Referer" to "https://app.mihoyo.com",
            "Content-Type" to "application/json; charset=UTF-8",
            "x-rpc-verify_key" to VERIFY_KEY,
            "x-rpc-csm_source" to "home",
            "User-Agent" to "okhttp/4.9.3",
        )
        if (deviceFp.isNotEmpty()) m["x-rpc-device_fp"] = deviceFp
        return m
    }

    /** MiyoQian 风格 web 通道头；gameSign=true 时带 luna 签到全套（web DS + client_type 5）。 */
    private fun miyoWebHeaders(gameSign: Boolean = false, signGame: String = ""): Map<String, String> {
        val m = mutableMapOf(
            "Accept" to "application/json, text/plain, */*",
            "User-Agent" to MIYO_UA,
            "Cookie" to cookie,
        )
        if (gameSign) {
            m["DS"] = DsSign.ds1(salt = DsSign.SALT_BBS_WEB_V206)
            m["x-rpc-channel"] = "miyousheluodi"
            m["Origin"] = "https://act.mihoyo.com"
            m["x-rpc-app_version"] = DsSign.BBS_VERSION_V206
            m["x-rpc-client_type"] = "5"
            m["X-Requested-With"] = "com.mihoyo.hyperion"
            m["Referer"] = "https://act.mihoyo.com/"
            m["Accept-Language"] = "zh-CN,en-US;q=0.8"
            m["x-rpc-device_id"] = deviceId
        }
        if (signGame.isNotEmpty()) m["x-rpc-signgame"] = signGame
        return m
    }

    // ============================================================
    // 任务主流程（MiyoQian 移植：游戏签到 luna + 米游币社区任务）
    // ============================================================

    private suspend fun humanDelay(a: Int, b: Int) {
        kotlinx.coroutines.delay(((a..b).random(rng)) * 1000L)
    }

    private fun short(s: String): String = if (s.length > 18) s.substring(0, 18) + "…" else s

    /** 完整跑一遍米游社任务：游戏签到（luna）+ 米游币社区任务。 */
    suspend fun runAll(): MhyResult {
        val steps = mutableListOf<String>()
        loadSaved()
        if (!hasLogin) {
            return MhyResult(false, "米游社未配置登录态", listOf("请先在账号页扫码登录"))
        }
        try {
            if (stoken.isEmpty()) {
                steps.add("⚠️ 当前登录态没有 stoken（网页登录仅支持状态查询）")
                steps.add("请到「账号」页用「扫码登录」重新登录后再执行任务")
                return MhyResult(false, "米游社任务需要 stoken（扫码登录）", steps)
            }

            // 1. 游戏社区签到（luna）
            if (repo.mhyGameSign) {
                gameSign(steps)
            } else {
                steps.add("游戏签到未启用，跳过")
            }

            // 2. 米游币社区任务
            if (repo.mhyBbsSign) {
                bbsTasks(steps)
            } else {
                steps.add("米游币社区任务未启用，跳过")
            }

            steps.add("✅ 米游社任务流程结束")
            return MhyResult(true, "米游社任务完成", steps)
        } catch (e: Exception) {
            AppLog.e("MHY", "runAll 异常: ${e.message}")
            return MhyResult(false, "米游社任务异常: ${e.message}", steps)
        }
    }

    // ---- 游戏社区签到（luna，MiyoQian GameCheckin 移植）----

    private suspend fun gameSign(steps: MutableList<String>) {
        val enabled = repo.mhySignGames.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (enabled.isEmpty()) {
            steps.add("未配置任何游戏，跳过游戏签到")
            return
        }
        for (key in enabled) {
            val g = GAMES[key]
            if (g == null) {
                steps.add("[跳过] 未知游戏配置: $key")
                continue
            }
            steps.add("== ${g.name} ==")
            steps.add("正在获取${g.name}绑定角色")
            val roles = gameRoles(g)
            if (roles.length() == 0) {
                steps.add("${g.name}: 未找到绑定角色")
                continue
            }
            val awards = gameAwards(g)
            for (i in 0 until roles.length()) {
                val role = roles.optJSONObject(i) ?: continue
                val uid = role.optString("game_uid")
                val nickname = if (role.optString("nickname").isNotEmpty()) role.optString("nickname") else uid
                val label = "${g.name} $nickname($uid)"
                val info = gameInfo(g, role)
                if (info.length() == 0) {
                    steps.add("$label 签到状态查询失败")
                    continue
                }
                if (info.optBoolean("first_bind")) {
                    steps.add("$label 首次绑定，请先手动签到一次")
                    continue
                }
                val signed = info.optBoolean("is_sign")
                val dayIndex = maxOf((info.optString("total_sign_day", "1").toIntOrNull() ?: 1) - 1, 0)
                if (signed) {
                    steps.add("$label 今日已签到，奖励 ${describeAward(awards, dayIndex)}")
                    continue
                }
                val r = gameSignRequest(g, role)
                if (r.optInt("retcode") == -5003) {
                    steps.add("$label 今日已签到，奖励 ${describeAward(awards, dayIndex)}")
                    continue
                }
                if (r.optInt("retcode") != 0) {
                    steps.add("$label 签到失败: ${r.optString("message")}(${r.optInt("retcode")})")
                    continue
                }
                val data = r.optJSONObject("data") ?: JSONObject()
                if (data.optInt("success") == 1) {
                    steps.add("⚠️ $label 触发验证码，本次跳过")
                    continue
                }
                steps.add("$label 签到成功，奖励 ${describeAward(awards, dayIndex + 1)}")
                humanDelay(1, 3)
            }
        }
    }

    /** 绑定角色（web cookie）；-100 时用 stoken 刷新 cookie_token 重试一次。 */
    private suspend fun gameRoles(g: Game, retried: Boolean = false): JSONArray {
        val r = http.get(
            "$TAKUMI_API/binding/api/getUserGameRolesByCookie?game_biz=${g.gameBiz}",
            headers = miyoWebHeaders(gameSign = true, signGame = g.signGame),
        )
        val j = r.jsonSafe()
        if (j.optInt("retcode") == -100 && !retried) {
            if (refreshCookieToken()) return gameRoles(g, retried = true)
        }
        val list = j.optJSONObject("data")?.optJSONArray("list")
        return list ?: JSONArray()
    }

    private suspend fun gameAwards(g: Game): JSONArray {
        val base = if (g.zzz) "$ZZZ_ACT_API/event/luna/zzz/home" else "$TAKUMI_API/event/luna/home"
        val r = http.get(
            "$base?lang=zh-cn&act_id=${g.actId}",
            headers = miyoWebHeaders(gameSign = true, signGame = g.signGame),
        )
        return r.jsonSafe().optJSONObject("data")?.optJSONArray("awards") ?: JSONArray()
    }

    private suspend fun gameInfo(g: Game, role: JSONObject): JSONObject {
        val base = if (g.zzz) "$ZZZ_ACT_API/event/luna/zzz/info" else "$TAKUMI_API/event/luna/info"
        val region = java.net.URLEncoder.encode(role.optString("region"), "UTF-8")
        val uid = java.net.URLEncoder.encode(role.optString("game_uid"), "UTF-8")
        val r = http.get(
            "$base?lang=zh-cn&act_id=${g.actId}&region=$region&uid=$uid",
            headers = miyoWebHeaders(gameSign = true, signGame = g.signGame),
        )
        val j = r.jsonSafe()
        return if (j.optInt("retcode") == 0) j.optJSONObject("data") ?: JSONObject() else JSONObject()
    }

    private suspend fun gameSignRequest(g: Game, role: JSONObject): JSONObject {
        val base = if (g.zzz) "$ZZZ_ACT_API/event/luna/zzz/sign" else "$TAKUMI_API/event/luna/sign"
        val body = JSONObject()
            .put("act_id", g.actId)
            .put("region", role.optString("region"))
            .put("uid", role.optString("game_uid"))
            .toString()
        val r = http.post(base, headers = miyoWebHeaders(gameSign = true, signGame = g.signGame), body = body)
        return r.jsonSafe()
    }

    private fun describeAward(awards: JSONArray, index: Int): String {
        if (awards.length() == 0) return "未知"
        val i = index.coerceIn(0, awards.length() - 1)
        val a = awards.optJSONObject(i) ?: return "未知"
        return "「${a.optString("name", "未知")}」x${a.optInt("cnt")}"
    }

    // ---- 米游币社区任务（MiyoQian BbsTasks 移植）----

    private data class TaskFlags(
        val sign: Boolean, val read: Boolean, val readNum: Int,
        val like: Boolean, val likeNum: Int, val share: Boolean,
    )

    private suspend fun bbsTasks(steps: MutableList<String>) {
        steps.add("== 米游币社区任务 ==")
        steps.add("正在获取米游币任务状态")
        val state = taskState()
        if (state.length() == 0) {
            steps.add("任务状态获取失败，请检查 cookie/stoken")
            return
        }
        val canGet = state.optInt("can_get_points", 0)
        val received = state.optInt("already_received_points", 0)
        val total = state.optInt("total_points", 0)
        val flags = taskFlags(state)
        val possibleToday = received + canGet
        steps.add("米游币今日进度：已获得 $received，还可获得 $canGet，预计总共可获得 $possibleToday")
        if (canGet == 0) {
            steps.add("今日任务已完成，今日已得 $received，当前总计 $total")
            return
        }

        if (repo.mhyRead && !flags.sign) {
            communitySign(steps)
            humanDelay(1, 3)
        } else if (flags.sign) {
            steps.add("社区签到已完成，跳过")
        }

        val needsPosts = (repo.mhyRead && !flags.read) ||
            (repo.mhyLike && !flags.like) ||
            (repo.mhyShare && !flags.share)
        if (needsPosts) {
            steps.add("正在获取帖子列表")
            val posts = fetchPosts()
            if (posts.isEmpty()) {
                steps.add("获取帖子列表失败，无法执行看帖/点赞/分享")
                return
            }
            if (repo.mhyRead && !flags.read) {
                readPosts(posts.take(flags.readNum), steps)
            } else if (repo.mhyRead) {
                steps.add("看帖任务已完成，跳过")
            }
            if (repo.mhyLike && !flags.like) {
                likePosts(posts.take(flags.likeNum), steps)
            } else if (repo.mhyLike) {
                steps.add("点赞任务已完成，跳过")
            }
            if (repo.mhyShare && !flags.share) {
                sharePost(posts.first(), steps)
            } else if (repo.mhyShare) {
                steps.add("分享任务已完成，跳过")
            }
        } else {
            if (repo.mhyRead && flags.read) steps.add("看帖任务已完成，跳过")
            if (repo.mhyLike && flags.like) steps.add("点赞任务已完成，跳过")
            if (repo.mhyShare && flags.share) steps.add("分享任务已完成，跳过")
        }

        val after = taskState()
        val s2 = if (after.length() > 0) after else state
        val finalReceived = s2.optInt("already_received_points", received)
        val finalTotal = s2.optInt("total_points", total)
        val gained = maxOf(finalReceived - received, 0)
        steps.add(
            "社区任务结束：今日已得 $finalReceived，还能获得 ${maxOf(possibleToday - finalReceived, 0)}，" +
                "当前总计 $finalTotal，本次新增 $gained",
        )
    }

    /** 任务状态（web 头，无 DS）；-100 时刷新 cookie_token 重试一次。 */
    private suspend fun taskState(retried: Boolean = false): JSONObject {
        val r = http.get(
            "$BBS_API/apihub/wapi/getUserMissionsState?point_sn=myb",
            headers = miyoWebHeaders(),
        )
        val j = r.jsonSafe()
        if (j.optInt("retcode") == -100 && !retried) {
            if (refreshCookieToken()) return taskState(retried = true)
        }
        val d = j.optJSONObject("data")
        return if (j.optInt("retcode") == 0 && d != null) d else JSONObject()
    }

    /** mission 58/59/60/61 → 签到/看帖/点赞/分享完成态与剩余次数。 */
    private fun taskFlags(state: JSONObject): TaskFlags {
        var sign = false; var read = false; var like = false; var share = false
        var readNum = 3; var likeNum = 5
        val states = state.optJSONArray("states") ?: JSONArray()
        for (i in 0 until states.length()) {
            val m = states.optJSONObject(i) ?: continue
            val done = m.optBoolean("is_get_award")
            val happened = m.optInt("happened_times", 0)
            when (m.optInt("mission_id")) {
                58 -> if (done) sign = true
                59 -> if (done) read = true else readNum = maxOf(readNum - happened, 0)
                60 -> if (done) like = true else likeNum = maxOf(likeNum - happened, 0)
                61 -> if (done) share = true
            }
        }
        return TaskFlags(sign, read, readNum, like, likeNum, share)
    }

    /** 讨论区社区签到（apihub/app/api/signIn，DS2/X6 + stoken app 头）。 */
    private suspend fun communitySign(steps: MutableList<String>) {
        for (v in configuredGids()) {
            val forum = FORUMS[v] ?: continue
            steps.add("正在进行${forum.third}社区签到")
            // MiyoQian 传 {"gids": forum.id}，id 为字符串 → 带引号
            val bodyStr = "{\"gids\":\"${forum.first}\"}"
            val headers = miyoAppHeaders(ds = DsSign.ds2(bodyStr, query = ""))
            val r = http.post("$BBS_API/apihub/app/api/signIn", headers = headers, body = bodyStr)
            val j = r.jsonSafe()
            when (j.optInt("retcode")) {
                0 -> steps.add("${forum.third} 社区签到成功")
                1034 -> steps.add("${forum.third} 社区签到触发验证码，已跳过")
                -100 -> {
                    steps.add("登录态过期，社区签到终止")
                    return
                }
                else -> steps.add("${forum.third} 社区签到失败: ${j.optString("message")}")
            }
            humanDelay(1, 3)
        }
    }

    private fun configuredGids(): List<String> {
        val out = mutableListOf<String>()
        for (v in repo.mhyForums.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val gid = FORUM_ID_TO_GIDS[v] ?: v // 26→2；已是 gids 的原样保留
            if (FORUMS.containsKey(gid) && !out.contains(gid)) out.add(gid)
        }
        return out
    }

    /** 候选帖子：第一个有效分区的列表（页大小 20），打乱后取前 5。 */
    private suspend fun fetchPosts(): List<MhyPost> {
        for (gid in configuredGids()) {
            val forum = FORUMS[gid] ?: continue
            val r = http.get(
                "$BBS_API/post/api/getForumPostList" +
                    "?forum_id=${forum.second}&is_good=false&is_hot=false&page_size=20&sort_type=1",
                headers = miyoAppHeaders(ds = DsSign.ds1(salt = DsSign.SALT_BBS_V206)),
            )
            val list = r.jsonSafe().optJSONObject("data")?.optJSONArray("list") ?: continue
            val posts = mutableListOf<MhyPost>()
            for (i in 0 until list.length()) {
                val post = list.optJSONObject(i)?.optJSONObject("post") ?: continue
                val id = post.optString("post_id")
                val title = post.optString("subject")
                if (id.isNotEmpty()) posts.add(MhyPost(id, title, gid))
            }
            if (posts.isNotEmpty()) {
                posts.shuffle(rng)
                return posts.take(5)
            }
        }
        return emptyList()
    }

    private suspend fun readPosts(posts: List<MhyPost>, steps: MutableList<String>) {
        for (p in posts) {
            steps.add("正在浏览: ${short(p.title)}")
            val r = http.get(
                "$BBS_API/post/api/getPostFull?post_id=${p.id}",
                headers = miyoAppHeaders(ds = DsSign.ds1(salt = DsSign.SALT_BBS_V206)),
            )
            steps.add(
                if (r.jsonSafe().optString("message") == "OK") "阅读成功: ${short(p.title)}"
                else "阅读失败: ${short(p.title)}",
            )
            humanDelay(1, 3)
        }
    }

    private suspend fun likePosts(posts: List<MhyPost>, steps: MutableList<String>) {
        for (p in posts) {
            steps.add("正在点赞: ${short(p.title)}")
            val body = "{\"post_id\":\"${p.id}\",\"is_cancel\":false,\"gids\":\"${p.gids}\"}"
            val r = http.post(
                "$BBS_API/post/api/post/upvote",
                headers = miyoAppHeaders(ds = DsSign.ds1(salt = DsSign.SALT_BBS_V206)),
                body = body,
            )
            val j = r.jsonSafe()
            if (j.optString("message") == "OK") {
                steps.add("点赞成功: ${short(p.title)}")
                if (repo.mhyCancelLike) {
                    humanDelay(1, 3)
                    steps.add("正在取消点赞: ${short(p.title)}")
                    val bodyC = "{\"post_id\":\"${p.id}\",\"is_cancel\":true,\"gids\":\"${p.gids}\"}"
                    http.post(
                        "$BBS_API/post/api/post/upvote",
                        headers = miyoAppHeaders(ds = DsSign.ds1(salt = DsSign.SALT_BBS_V206)),
                        body = bodyC,
                    )
                }
            } else if (j.optInt("retcode") == 1034) {
                steps.add("点赞触发验证码，已跳过: ${short(p.title)}")
            } else {
                steps.add("点赞失败: ${short(p.title)} (${j.optString("message")})")
            }
            humanDelay(1, 3)
        }
    }

    private suspend fun sharePost(p: MhyPost, steps: MutableList<String>) {
        steps.add("正在分享: ${short(p.title)}")
        var r = http.get(
            "$BBS_API/apihub/api/getShareConf?entity_id=${p.id}&entity_type=1",
            headers = miyoWebHeaders(),
        )
        // 实测 web 裸头可能 403（非 JSON 响应）：退避一次用 app 通道（stoken+DS）重试
        if (r.jsonSafe().optString("message") != "OK") {
            humanDelay(1, 2)
            r = http.get(
                "$BBS_API/apihub/api/getShareConf?entity_id=${p.id}&entity_type=1",
                headers = miyoAppHeaders(ds = DsSign.ds1(salt = DsSign.SALT_BBS_V206)),
            )
        }
        steps.add(
            if (r.jsonSafe().optString("message") == "OK") "分享成功: ${short(p.title)}"
            else "分享失败: ${short(p.title)} (${r.jsonSafe().optString("message")})",
        )
    }

    // ============================================================
    // 扫码登录（passport QR Login）
    // createQRLogin → 用户在米游社 App 扫码确认 → queryQRLoginStatus 轮询
    // Confirmed 后 tokens 填充（token_type=1 即 stoken v2）+ user_info(aid/mid)
    // ============================================================

    private fun qrHeaders(deviceId: String): Map<String, String> = mapOf(
        "Content-Type" to "application/json; charset=UTF-8",
        "User-Agent" to "okhttp/4.9.3",
        "x-rpc-app_id" to VERIFY_KEY, // 米游社 app_id
        "x-rpc-app_version" to APP_VERSION,
        "x-rpc-client_type" to CLIENT_TYPE_ANDROID,
        "x-rpc-device_id" to deviceId,
        "x-rpc-sys_version" to "12",
        "x-rpc-channel" to "miyousheluodi",
    )

    data class QrSession(val url: String, val ticket: String, val deviceId: String)

    /** 创建扫码登录会话，返回二维码内容 URL；失败返回 null。 */
    suspend fun createQrLogin(): QrSession? {
        val devId = MihoyoIds.randomUuid4()
        val r = http.post(
            "$PASSPORT_API/account/ma-cn-passport/app/createQRLogin",
            headers = qrHeaders(devId),
            body = "{}",
        )
        val j = r.jsonSafe()
        if (j.optInt("retcode") != 0) {
            AppLog.e("MHY", "createQRLogin 失败: ${j.optInt("retcode")} ${j.optString("message")}")
            return null
        }
        val data = j.optJSONObject("data")
        val ticket = data?.optString("ticket", null)
        val url = data?.optString("url", null)
        if (ticket.isNullOrEmpty() || url.isNullOrEmpty()) {
            AppLog.e("MHY", "createQRLogin 响应缺字段")
            return null
        }
        return QrSession(url, ticket, devId)
    }

    /**
     * 轮询扫码状态。返回：Created / Scanned / Confirmed / Expired / Error:xxx
     * Confirmed 时自动解析 stoken 并保存验证。
     */
    suspend fun pollQrLoginStatus(ticket: String, deviceId: String): String {
        val r = http.post(
            "$PASSPORT_API/account/ma-cn-passport/app/queryQRLoginStatus?ticket=$ticket",
            headers = qrHeaders(deviceId),
            body = "{}",
        )
        val j = r.jsonSafe()
        if (j.optInt("retcode") != 0) {
            // -3001 参数不合法 = ticket 失效/过期
            if (j.optInt("retcode") == -3001 || j.optInt("retcode") == -106) return "Expired"
            return "Error:${j.optString("message", "unknown")}"
        }
        val data = j.optJSONObject("data")
        val status = data?.optString("status", "Created") ?: "Created"
        if (status != "Confirmed") return status

        // Confirmed：提取 stoken（token_type=1）+ uid/mid
        val userInfo = data.optJSONObject("user_info")
        val tokens = data.optJSONArray("tokens") ?: JSONArray()
        // 结构留痕（只记类型与长度，不记 token 值）
        val shapes = (0 until tokens.length()).map { i ->
            val t = tokens.optJSONObject(i)
            if (t != null) "type=${t.optInt("token_type")} len=${t.optString("token").length}" else "?"
        }.joinToString(", ")
        AppLog.i("MHY", "QR Confirmed: tokens=[$shapes] aid=${userInfo?.optString("aid")} mid=${userInfo?.optString("mid")}")
        var st: String? = null
        for (i in 0 until tokens.length()) {
            val t = tokens.optJSONObject(i) ?: continue
            if (t.optInt("token_type") == 1) {
                st = t.optString("token", null)
                break
            }
        }
        if (st.isNullOrEmpty()) {
            AppLog.e("MHY", "QR Confirmed 但未找到 stoken")
            return "Error:未从确认结果中取到 stoken，请改用 Cookie 登录"
        }
        stoken = st
        stuid = userInfo?.optString("aid", "") ?: ""
        mid = userInfo?.optString("mid", "") ?: ""
        // 验证时沿用二维码会话的 device_id（签发与验证保持同一设备）
        this.deviceId = deviceId
        deviceFp = MihoyoIds.deviceFp(deviceId)

        val ok = verifyStoken()
        if (!ok) {
            return "Error:stoken 验证未通过，请改用 Cookie 登录"
        }

        repo.setSecret("mhy.stoken", stoken)
        repo.setSecret("mhy.stuid", stuid)
        repo.setSecret("mhy.mid", mid)
        if (cookie.isEmpty()) repo.setSecret("mhy.cookie", "")
        AppLog.i("MHY", "扫码登录成功 (uid=$stuid)")
        return "Confirmed"
    }
}
