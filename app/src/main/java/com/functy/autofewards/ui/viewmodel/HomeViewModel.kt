package com.functy.autofewards.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.functy.autofewards.core.AppLog
import com.functy.autofewards.data.model.TaskState
import com.functy.autofewards.data.model.TaskStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 总览页视图模型。M1 阶段为占位：任务「运行」仅模拟耗时并写日志，
 * 引擎在 M4 接入（WB/MHY 并行、Bing WebView、本地完成标记短路等）。
 */
class HomeViewModel : ViewModel() {

    private val _tasks = MutableStateFlow(
        listOf(
            TaskState("wb", "WorkBuddy", "WB", logTag = "WB"),
            TaskState("mhy", "米游社", "MHY", logTag = "MHY"),
            TaskState("bing", "Bing", "BING", logTag = "BING"),
        )
    )
    val tasks: StateFlow<List<TaskState>> = _tasks.asStateFlow()

    private val runningJobs = mutableMapOf<String, Job>()

    fun isRunning(id: String): Boolean = runningJobs[id]?.isActive == true

    fun run(id: String) {
        if (isRunning(id)) return
        val state = _tasks.value.firstOrNull { it.id == id } ?: return
        runningJobs[id] = viewModelScope.launch {
            update(id) { it.copy(status = TaskStatus.Running, detail = "运行中…") }
            AppLog.log(state.logTag, "任务启动（M1 模拟，引擎待接入）")
            // 模拟耗时
            delay(1500)
            AppLog.log(state.logTag, "任务完成")
            update(id) { it.copy(status = TaskStatus.Done, detail = "已完成（本地标记）") }
        }
    }

    private fun update(id: String, transform: (TaskState) -> TaskState) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }
}
