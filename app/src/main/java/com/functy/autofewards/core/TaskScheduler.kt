package com.functy.autofewards.core

import android.content.Context
import com.functy.autofewards.data.repository.MihoyoBbsService
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.functy.autofewards.data.repository.WorkBuddyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 任务执行服务（Flutter 版 scheduler_service.dart 翻译，协程实现）。
 *
 * - runAll：勾选的任务并行执行（wb+mhy async 并行；Bing 由 UI 挂 WebView 自动跑）
 * - runSingle：单任务执行
 * - statuses：各任务真实完成状态（StateFlow），主页监听展示
 * - 本地完成标记短路：当日已完成（done.wb/mhy/bing = 日期）→ 直接打
 *   「已签到（本地标记）」不再查接口（接口 today_checked_in 不可靠，坑位 7）；
 *   次日自动重置
 */
object TaskScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _running = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _running.asStateFlow()

    private val _runningTask = MutableStateFlow("")
    val runningTask: StateFlow<String> = _runningTask.asStateFlow()

    /** 各任务真实完成状态（null = 未配置/查询失败），主页监听展示。 */
    private val _statuses: MutableStateFlow<Map<String, Boolean?>> =
        MutableStateFlow(mapOf("wb" to null, "mhy" to null, "bing" to null))
    val statuses: StateFlow<Map<String, Boolean?>> = _statuses.asStateFlow()

    fun taskName(task: String): String = when (task) {
        "wb" -> "WorkBuddy"
        "mhy" -> "米游社"
        "bing" -> "Bing"
        else -> task
    }

    fun mihoyoService(repo: SettingsRepositoryImpl) = MihoyoBbsService(repo)
    fun workBuddyService(repo: SettingsRepositoryImpl) = WorkBuddyService(repo)

    /**
     * 查询实际完成状态：WorkBuddy/米游社查线上接口，Bing 用本地当日标记。
     * 本地已完成标记直接短路（不打 API 查询日志）。
     */
    fun refreshStatuses(repo: SettingsRepositoryImpl) {
        scope.launch {
            refreshStatusesSuspend(repo)
        }
    }

    private suspend fun refreshStatusesSuspend(repo: SettingsRepositoryImpl) {
        var wb: Boolean?
        var mhy: Boolean?
        if (repo.isDoneToday("wb")) {
            wb = true
            AppLog.i("WB", "WorkBuddy 今日已签到（本地标记）")
        } else {
            wb = try {
                val api = workBuddyService(repo).checkStatus()
                if (api == null) {
                    AppLog.i("WB", "WorkBuddy 状态查询失败（未配置或 token 过期）")
                } else {
                    AppLog.i("WB", "WorkBuddy 今日${if (api) "已签到" else "未签到"}")
                }
                api
            } catch (e: Exception) {
                AppLog.w("WB", "WorkBuddy 状态查询异常: ${e.message}")
                null
            } ?: false
        }
        if (repo.isDoneToday("mhy")) {
            mhy = true
            AppLog.i("MHY", "米游社今日任务已完成（本地标记）")
        } else {
            mhy = try {
                val api = mihoyoService(repo).tasksAllDone()
                if (api == null) {
                    AppLog.i("MHY", "米游社状态查询失败（未配置或登录态过期）")
                } else {
                    AppLog.i("MHY", "米游社今日任务${if (api) "已完成" else "未完成"}")
                }
                api
            } catch (e: Exception) {
                AppLog.w("MHY", "米游社状态查询异常: ${e.message}")
                null
            } ?: false
        }
        _statuses.value = mapOf(
            "wb" to (wb || repo.isDoneToday("wb")),
            "mhy" to (mhy || repo.isDoneToday("mhy")),
            "bing" to repo.isDoneToday("bing"),
        )    }

    /** 单任务执行。返回是否真正启动（已在运行则忽略）。 */
    fun runSingle(context: Context, repo: SettingsRepositoryImpl, task: String): Boolean {
        if (_running.value) {
            AppLog.w("TASK", "已有任务在执行中，忽略本次触发")
            return false
        }
        _running.value = true
        _runningTask.value = task
        val name = taskName(task)
        TaskNotifier.busy(context, title = "$name 任务", text = "正在执行…")
        scope.launch {
            try {
                when (task) {
                    "wb" -> runWorkBuddy(context, repo)
                    "mhy" -> runMihoyo(context, repo)
                    "bing" -> AppLog.i("TASK", "Bing 刷分需要 WebView 环境，请到 Bing 页执行")
                }
                TaskNotifier.finish(context, text = "$name 任务结束")
            } finally {
                _running.value = false
                _runningTask.value = ""
                refreshStatuses(repo)
            }
        }
        return true
    }

    /**
     * 全部任务：勾选的任务并行执行；Bing 通过挂载回调由 UI 后台挂载 WebView
     * （脚本随页面原生注入并自动启动），不切换页面。
     */
    fun runAll(
        context: Context,
        repo: SettingsRepositoryImpl,
        selected: Set<String>,
        onBingStage: (suspend () -> Unit)? = null,
    ): Boolean {
        if (_running.value) {
            AppLog.w("TASK", "任务已在运行中，忽略本次触发")
            return false
        }
        _running.value = true
        _runningTask.value = "all"
        val wbOn = "wb" in selected && repo.wbEnabled
        val mhyOn = "mhy" in selected && repo.mhyEnabled
        val bingOn = "bing" in selected && repo.bingEnabled
        val jobs = listOfNotNull(
            if (wbOn) "wb" else null,
            if (mhyOn) "mhy" else null,
        )
        val total = jobs.size + if (bingOn) 1 else 0
        var done = 0
        fun bump(name: String) {
            done++
            TaskNotifier.progress(context, done = done, total = total, text = "$name 已完成")
        }

        TaskNotifier.start(context, title = "AutoFewards 任务", text = "并行执行任务…")
        scope.launch {
            try {
                AppLog.i("TASK", "===== 开始执行任务（${jobs.joinToString("/")}并行${if (bingOn) " + Bing" else ""}） =====")
                val results = jobs.map { task ->
                    async {
                        if (task == "wb") runWorkBuddy(context, repo) { bump("WorkBuddy") }
                        else runMihoyo(context, repo) { bump("米游社") }
                    }
                }.awaitAll()

                if (bingOn) {
                    if (onBingStage != null) {
                        AppLog.i("TASK", "【Bing Rewards】后台挂载 Bing 页，脚本将随页面自动执行")
                        onBingStage()
                        bump("Bing")
                    } else {
                        AppLog.i("TASK", "【Bing Rewards】请到「Bing」页打开 WebView 自动执行")
                    }
                }
                AppLog.i("TASK", "===== 全部任务结束 =====")
                TaskNotifier.finish(
                    context,
                    text = if (done >= total && total > 0) "全部任务已完成" else "任务执行结束",
                )
            } finally {
                _running.value = false
                _runningTask.value = ""
                refreshStatuses(repo)
            }
        }
        return true
    }

    private suspend fun runWorkBuddy(context: Context, repo: SettingsRepositoryImpl, onDone: (() -> Unit)? = null) {
        AppLog.i("WB", "【WorkBuddy】开始签到…")
        try {
            val r = workBuddyService(repo).checkin()
            AppLog.i("WB", "【WorkBuddy】${r.summary}")
            for (s in r.steps) {
                AppLog.i("WB", s.trim())
            }
            if (r.ok) repo.markDoneToday("wb")
        } catch (e: Exception) {
            AppLog.e("WB", "【WorkBuddy】异常: ${e.message}")
        } finally {
            onDone?.invoke()
        }
    }

    private suspend fun runMihoyo(context: Context, repo: SettingsRepositoryImpl, onDone: (() -> Unit)? = null) {
        AppLog.i("MHY", "【米游社】开始执行…")
        try {
            val r = mihoyoService(repo).runAll()
            AppLog.i("MHY", "【米游社】${r.summary}")
            for (s in r.steps) {
                AppLog.i("MHY", s.trim())
            }
            if (r.ok) repo.markDoneToday("mhy")
        } catch (e: Exception) {
            AppLog.e("MHY", "【米游社】异常: ${e.message}")
        } finally {
            onDone?.invoke()
        }
    }
}
