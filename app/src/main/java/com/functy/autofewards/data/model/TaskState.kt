package com.functy.autofewards.data.model

/** 任务卡片状态。 */
enum class TaskStatus {
    NotConfigured, // 未配置
    Querying,      // 查询中
    Done,          // 已完成
    Failed,        // 未完成/出错
    Running,       // 运行中（多步任务）
}

/** 三个任务之一的描述与运行状态。 */
data class TaskState(
    val id: String,          // wb / mhy / bing
    val name: String,        // WorkBuddy / 米游社 / Bing
    val iconDesc: String,    // 图标兜底文案
    val status: TaskStatus = TaskStatus.NotConfigured,
    val detail: String = "",
    val logTag: String = "",
)
