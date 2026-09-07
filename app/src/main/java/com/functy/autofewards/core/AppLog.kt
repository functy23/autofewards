package com.functy.autofewards.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单条日志。 */
data class LogEntry(
    val tag: String,
    val message: String,
    val time: Long = System.currentTimeMillis(),
) {
    val timeText: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))
}

/**
 * 内存环形日志（单例）+ 文件落盘（异步）。
 * 引擎与 UI 共用；tag 用于各任务卡过滤（WB/MHY/BING/SYS/HTTP/TASK）。
 * ⚠️ 日志严禁出现 cookie/token 值（引擎侧负责打码）。
 */
object AppLog {
    private const val MAX = 800
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var logFile: File? = null
    private val tsFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** 初始化文件落盘（Application onCreate 调用一次）。 */
    fun initFile(filesDir: File) {
        ioScope.launch {
            runCatching {
                val dir = File(filesDir, "logs").apply { mkdirs() }
                logFile = File(dir, "autofewards.log")
            }
        }
    }

    fun log(tag: String, message: String) {
        val entry = LogEntry(tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX)
        Log.d("AF/$tag", message)
        val f = logFile ?: return
        val line = "${tsFormat.format(Date(entry.time))} [$tag] ${message.replace('\n', ' ')}\n"
        ioScope.launch { runCatching { f.appendText(line) } }
    }

    fun i(tag: String, message: String) = log(tag, message)
    fun w(tag: String, message: String) = log(tag, "⚠️ $message")
    fun e(tag: String, message: String) = log(tag, "❌ $message")
    fun d(tag: String, message: String) = log(tag, message)
}
