package com.functy.autofewards

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** 手动装配根：全局持有 Application 实例供 SettingsRepository 等取 SharedPreferences。 */
class AutoFewardsApp : Application() {
    companion object {
        lateinit var instance: AutoFewardsApp
            private set

        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                ApplicationInfo::class.java
                    .getDeclaredMethod(
                        "setEnableOnBackInvokedCallback",
                        Boolean::class.javaPrimitiveType,
                    )
                    .apply { isAccessible = true }
                    .invoke(appInfo, enable)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.functy.autofewards.core.AppLog.initFile(filesDir)
        com.functy.autofewards.core.TaskNotifier.enabled =
            getSharedPreferences("settings", MODE_PRIVATE).getBoolean("task_notification", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            setEnableOnBackInvokedCallback(applicationInfo, true)
        }
    }
}
