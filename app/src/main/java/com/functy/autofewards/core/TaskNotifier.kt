package com.functy.autofewards.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 任务进度通知（M5，Flutter 版 task_notifier.dart 的 Kotlin 原生直写）。
 * Android 常驻进度通知：
 * - 单任务：通知标题=任务名，转圈样式
 * - 一键运行：总进度（x/3）+ 当前完成项
 * - 结束：显示完成文案，数秒后自动消失
 * 所有方法吞异常——通知失败绝不能影响任务执行。
 */
object TaskNotifier {
    private const val CHANNEL_ID = "task_progress"
    private const val NOTIFICATION_ID = 1001

    /** 进度通知开关（settings.task_notification，false 时完全 no-op）。 */
    @Volatile
    var enabled: Boolean = true

    private fun builder(context: android.content.Context): android.app.Notification.Builder? {
        if (!enabled) return null
        return runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return null
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "任务进度", android.app.NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
            android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
        }.getOrNull()
    }

    private fun notify(context: android.content.Context, b: android.app.Notification.Builder) {
        runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, b.build())
        }
    }

    /** 开始一轮任务：创建/更新常驻通知。 */
    fun start(context: android.content.Context, title: String, text: String) {
        val b = builder(context) ?: return
        b.setContentTitle(title).setContentText(text)
            .setProgress(0, 0, true)
        notify(context, b)
    }

    /** 单任务进度（不确定进度，转圈样式）。 */
    fun busy(context: android.content.Context, title: String, text: String) {
        val b = builder(context) ?: return
        b.setContentTitle(title).setContentText(text)
            .setProgress(0, 0, true)
        notify(context, b)
    }

    /** 更新总进度（一键运行：done/total）。 */
    fun progress(context: android.content.Context, done: Int, total: Int, text: String) {
        val b = builder(context) ?: return
        b.setContentTitle("AutoFewards 任务").setContentText(text)
            .setProgress(total, done, false)
        notify(context, b)
    }

    /** 结束：显示完成文案，数秒后自动消失。 */
    fun finish(context: android.content.Context, text: String) {
        if (!enabled) return
        runCatching {
            val b = builder(context) ?: return
            b.setContentTitle("AutoFewards").setContentText(text)
                .setProgress(0, 0, false)
                .setOngoing(false)
            notify(context, b)
            // 5 秒后自动消失
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching {
                    context.getSystemService(android.app.NotificationManager::class.java)
                        ?.cancel(NOTIFICATION_ID)
                }
            }, 5000)
        }
    }

    fun cancel(context: android.content.Context) {
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    /** 通知权限是否已授予（Android 13+ POST_NOTIFICATIONS 运行时权限）。 */
    fun hasPermission(context: android.content.Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
