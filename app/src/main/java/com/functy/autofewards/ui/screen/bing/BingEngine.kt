package com.functy.autofewards.ui.screen.bing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import com.functy.autofewards.core.AppLog
import com.functy.autofewards.core.BingUserscript
import com.functy.autofewards.core.CnWords
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Bing Rewards 引擎（单例）：WebView 生命周期之外的脚本准备 / 兜底搜索器 /
 * 自动启动，Flutter 版 bing_webview_page.dart 的 Kotlin 翻译。
 *
 * 两层实现：
 * 1. 油猴脚本层：注入原版「Microsoft Bing Rewards 自动搜索助手」（greasyfork
 *    538825）+ 自动启动补丁。原脚本负责：从 Rewards 侧栏抓搜索词、进度检查、
 *    随机滚动、休息策略 —— 全部保留。
 * 2. 兜底层：若原脚本 UI 没出现（改版/加载失败），用内置词库 + 随机延迟直接
 *    填 #sb_form_q submit，30 次、间隔 12–28s。
 */
object BingEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** WebView 引用（页面 AndroidView 创建时注入；供后台 evaluateJavascript 用）。 */
    var webView: WebView? = null

    /** 页面是否挂载（进 Bing 页或一键运行时置 true）。 */
    val mounted by lazy { mutableStateOf(false) }

    /** 状态条文本。 */
    val status by lazy { mutableStateOf("加载中…") }

    /** 脚本是否已准备好（包装 + 自动启动补丁）。 */
    @Volatile
    private var scriptReady = false

    @Volatile
    private var wrappedScript: String = ""

    /** 起始页：必应搜索结果页，词从内置二字词库随机。 */
    fun randomSearchUrl(): String {
        val word = CnWords.random()
        val encoded = URLEncoder.encode(word, "UTF-8")
        return "https://www.bing.com/search?q=$encoded&PC=U316&FORM=CHROMN"
    }

    /** 主线程执行 JS（忽略 WebView 已销毁等异常）。 */
    private fun evalJs(source: String) {
        val wv = webView ?: return
        try {
            wv.evaluateJavascript(source, null)
        } catch (e: Exception) {
            AppLog.w("BING", "evaluateJavascript 失败: ${e.message}")
        }
    }

    private fun evalJsAsync(source: String, onResult: (String?) -> Unit = {}) {
        val wv = webView ?: run { onResult(null); return }
        try {
            wv.evaluateJavascript(source) { onResult(it) }
        } catch (e: Exception) {
            AppLog.w("BING", "evaluateJavascript 失败: ${e.message}")
            onResult(null)
        }
    }

    /** 读取内置脚本资产并包装（同步 IO，页面创建前在后台调用）。 */
    fun prepareScript(context: Context): Boolean {
        if (scriptReady) return true
        return try {
            val raw = context.assets.open("userscripts/bing_rewards_1.3.2.user.js")
                .bufferedReader().use { it.readText() }
            AppLog.i("BING", "使用内置脚本 bing_rewards_1.3.2 (${raw.length}B)")
            var wrapped = BingUserscript.wrapForInjection(
                raw,
                storageKey = "bing_rewards_auto_searcher_config",
            )
            // 自动启动补丁并入同一脚本（多脚本曾出现只有第一个被执行的情况）
            wrapped = "$wrapped;\n${BingUserscript.autoStartPatch(force = false)}"
            wrappedScript = wrapped
            scriptReady = true
            true
        } catch (e: Exception) {
            AppLog.e("BING", "脚本准备失败: ${e.message}")
            status.value = "脚本加载失败: ${e.message}"
            false
        }
    }

    /** 手动触发：强制执行自动启动补丁（不受 30 分钟去重限制）。 */
    fun autoStartScript() {
        evalJs(BingUserscript.autoStartPatch(force = true))
        AppLog.i("BING", "手动触发自动启动补丁")
    }

    @Volatile
    private var fallbackStarted = false
    private var fallbackJob: Job? = null

    /** 兜底自动搜索器（不依赖油猴脚本）：30 次、间隔 12–28s。 */
    fun startFallbackSearcher(repo: SettingsRepositoryImpl) {
        if (fallbackStarted) return
        fallbackStarted = true
        if (!repo.bingFallback) {
            AppLog.i("BING", "兜底搜索已关闭（设置），跳过")
            return
        }
        val count = 30
        val minD = 12
        val maxD = 28
        var tick = 0
        fallbackJob = scope.launch {
            var done = 0
            while (done < count) {
                delay(1000)
                if (webView == null) {
                    AppLog.i("BING", "WebView 已释放，兜底搜索终止")
                    return@launch
                }
                val interval = minD + Random.nextInt((maxD - minD).coerceAtLeast(1))
                // 按 tick 间隔触发（与 Flutter 版 Timer.periodic + remain % interval 语义一致）
                tick++
                if (tick % interval != 0) continue
                val term = CnWords.random()
                var ok = false
                withContext(Dispatchers.Main) {
                    evalJsAsync(BingUserscript.fallbackSearchStep(term)) { result ->
                        ok = result?.contains("true") == true
                    }
                }
                // evaluateJavascript 回调是异步的，稍等取结果
                delay(300)
                if (ok) {
                    done++
                    status.value = "兜底搜索 $done/$count: $term"
                    AppLog.i("BING", "fallback search #$done: $term")
                    // 随机滚动，模拟真人浏览
                    delay(2000)
                    evalJs("window.scrollBy({top: ${200 + Random.nextInt(600)}, behavior: \"smooth\"});")
                }
            }
            status.value = "兜底搜索完成（$count 次）"
        }
    }

    /** 重载并重新注入（UserScript 每次导航都会执行全部脚本）。 */
    fun reloadAndReinject() {
        fallbackStarted = false
        fallbackJob?.cancel()
        val wv = webView ?: return
        wv.post { wv.reload() }
    }

    /** onLoadStop 后调用：注入脚本 + 标记今日完成 + 兜底监测。 */
    fun onPageFinished(context: Context, repo: SettingsRepositoryImpl, url: String) {
        status.value = "页面加载完成: ${runCatching { java.net.URI(url).host }.getOrDefault(url)}（脚本已随页面执行）"
        repo.markDoneToday("bing")
        // 注入主脚本（每次导航都注入；脚本自带 localStorage 去重，不会循环点击）
        if (scriptReady) {
            evalJs(wrappedScript)
        }
        // 兜底监测：20 秒后若还没检测到脚本 UI，用兜底搜索器
        if (!fallbackStarted) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (webView == null || fallbackStarted) return@postDelayed
                evalJsAsync(BingUserscript.hasScriptUiJs) { result ->
                    val hasUi = result?.contains("true") == true
                    if (!hasUi) {
                        status.value = "未检测到脚本 UI，启用兜底自动搜索"
                        startFallbackSearcher(repo)
                    }
                }
            }, 20_000)
        }
    }

    /** 创建 WebView（页面 AndroidView 工厂调用；保留 cookie 与 localStorage）。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(context: Context, repo: SettingsRepositoryImpl): WebView {
        prepareScript(context)
        CookieManager.getInstance().setAcceptCookie(true)
        val wv = WebView(context)
        webView = wv
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true // localStorage：GM 存储与去重标记
        wv.settings.databaseEnabled = true
        wv.settings.loadsImagesAutomatically = true
        wv.settings.useWideViewPort = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.setSupportZoom(true)
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // 非隐身：要保留 Bing 登录 cookie
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                status.value = "加载中: $url"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) onPageFinished(context, repo, url)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                val text = message?.message() ?: return super.onConsoleMessage(message)
                if (text.contains("[AutoStart]") ||
                    text.contains("Rewards") ||
                    text.contains("[UserscriptEngine]")
                ) {
                    AppLog.d("BING-JS", text)
                }
                return super.onConsoleMessage(message)
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.deny()
            }
        }
        wv.loadUrl(randomSearchUrl())
        status.value = "WebView 已挂载"
        return wv
    }

    /** 释放 WebView（页面离开组合时调用；保活模式下页面常驻不会频繁触发）。 */
    fun releaseWebView() {
        webView?.runCatching {
            stopLoading()
            destroy()
        }
        webView = null
        scriptReady = false
        fallbackStarted = false
        fallbackJob?.cancel()
    }
}
