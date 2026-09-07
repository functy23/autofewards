package com.functy.autofewards.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.functy.autofewards.data.model.TaskState
import com.functy.autofewards.data.model.TaskStatus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val statusColor: Map<TaskStatus, Color> = mapOf(
    TaskStatus.NotConfigured to Color(0xFF9E9E9E),
    TaskStatus.Querying to Color(0xFFFFB300),
    TaskStatus.Running to Color(0xFF4C6FFF),
    TaskStatus.Done to Color(0xFF4CAF50),
    TaskStatus.Failed to Color(0xFFF44336),
)

private fun statusText(status: TaskStatus): String = when (status) {
    TaskStatus.NotConfigured -> "未配置"
    TaskStatus.Querying -> "查询中"
    TaskStatus.Running -> "运行中"
    TaskStatus.Done -> "已完成"
    TaskStatus.Failed -> "未完成"
}

/** 单个任务的状态卡：名称 + 状态文字 + 播放/转圈按钮。 */
@Composable
fun StatusCard(
    task: TaskState,
    running: Boolean,
    onClickRun: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText(task.status),
                color = statusColor[task.status] ?: Color.Gray,
                style = MiuixTheme.textStyles.body2,
            )
            if (running) {
                InfiniteProgressIndicator(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(20.dp),
                    color = MiuixTheme.colorScheme.primary,
                )
            } else {
                IconButton(onClick = onClickRun) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = "运行${task.name}",
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
