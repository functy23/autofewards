package com.functy.autofewards.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.functy.autofewards.core.LogEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val tagColor: Map<String, Color> = mapOf(
    "WB" to Color(0xFF2196F3),
    "MHY" to Color(0xFFFF6D00),
    "BING" to Color(0xFF00ACC1),
    "SYS" to Color(0xFF9E9E9E),
)

/** 单条日志行：左侧 3dp 色条 + tag + 消息；点击展开/收起完整内容。 */
@Composable
fun LogRow(entry: LogEntry, expanded: Boolean, onClick: () -> Unit) {
    // 竖条与整行垂直居中对齐：日志行常态为单行，居中即与文字对齐；
    // 展开多行时竖条沿整行高度拉伸（IntrinsicSize.Min），首尾视觉一致。
    val body2 = MiuixTheme.textStyles.body2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 色条
        Column(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    color = tagColor[entry.tag] ?: tagColor["SYS"]!!,
                    shape = RoundedCornerShape(1.5.dp),
                ),
        ) {}
        Text(
            text = "[${entry.tag}]",
            modifier = Modifier.padding(start = 5.dp),
            color = tagColor[entry.tag] ?: tagColor["SYS"]!!,
            style = body2,
        )
        Text(
            text = entry.message,
            modifier = Modifier
                .padding(start = 3.dp)
                .weight(1f),
            style = body2,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 日志卡片：标题 + 日志列表（默认折叠单行，点击行展开）。 */
@Composable
fun LogCard(
    tag: String,
    entries: List<LogEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val filtered = entries.filter { it.tag == tag || it.tag == "SYS" }
        if (filtered.isEmpty()) {
            Text(
                text = "暂无日志",
                modifier = Modifier.padding(16.dp),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body2,
            )
        } else {
            filtered.takeLast(50).forEach { entry ->
                var expanded by remember { mutableStateOf(false) }
                LogRow(
                    entry = entry,
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                )
            }
        }
    }
}
