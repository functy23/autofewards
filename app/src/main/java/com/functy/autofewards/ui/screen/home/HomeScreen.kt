package com.functy.autofewards.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Checkbox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.functy.autofewards.R
import com.functy.autofewards.R.drawable.bing
import com.functy.autofewards.R.drawable.miyoushe
import com.functy.autofewards.R.drawable.workbuddy
import com.functy.autofewards.core.AppLog
import com.functy.autofewards.core.TaskNotifier
import com.functy.autofewards.core.TaskScheduler
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.functy.autofewards.ui.component.LogRow
import com.functy.autofewards.ui.component.SquircleIcon
import com.functy.autofewards.ui.screen.bing.BingEngine
import com.functy.autofewards.ui.theme.LocalEnableBlur
import com.functy.autofewards.ui.theme.isInDarkTheme
import com.functy.autofewards.ui.util.BlurredBar
import com.functy.autofewards.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomeScreen(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean,
) {
    HomePagerMiuix(bottomInnerPadding = bottomInnerPadding)
}

@Composable
private fun HomePagerMiuix(
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusCard()
                        TaskPickerRow()
                        LogCard()
                        SupportLinks(
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(bottomInnerPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),
            scrollBehavior = scrollBehavior
        )
    }
}

/** 任务定义：图标资产（Flutter 原版 png）+ 名称 + 配置状态。 */
private data class TaskDef(
    val id: String,
    val label: String,
    val asset: Int,
    val configured: Boolean,
)

@Composable
private fun rememberTaskDefs(): List<TaskDef> {
    val repo = remember { SettingsRepositoryImpl() }
    // 读日志流作为签名：登录态写入（伴随日志）触发 recomposition 重新计算
    val logTick by AppLog.entries.collectAsStateWithLifecycle()
    return remember(logTick.size) {
        listOf(
            TaskDef("wb", "WorkBuddy", workbuddy, repo.hasSecret("wb.token")),
            TaskDef("mhy", "米游社", miyoushe, repo.hasSecret("mhy.stoken") || repo.hasSecret("mhy.cookie")),
            TaskDef("bing", "Bing", bing, true),
        )
    }
}

/** 工作中大卡：绿色底 + 「工作中」+ 三任务配置清单（原版 png 图标 + squircle 圆角）。 */
@Composable
private fun StatusCard() {
    val tasks = rememberTaskDefs()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = when {
                    isDynamicColor -> colorScheme.secondaryContainer
                    isInDarkTheme() -> Color(0xFF1A3825)
                    else -> Color(0xFFDFFAE4)
                }
            ),
            showIndication = false,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        imageVector = Icons.Rounded.CheckCircleOutline,
                        tint = if (isDynamicColor) {
                            colorScheme.primary.copy(alpha = 0.8f)
                        } else {
                            Color(0xFF36D167)
                        },
                        contentDescription = null
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 14.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_working),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        tasks.forEach { task ->
                            TaskConfigRow(
                                asset = task.asset,
                                label = task.label,
                                configured = task.configured,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskConfigRow(
    asset: Int,
    label: String,
    configured: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquircleIcon(
            asset = asset,
            size = 18.dp,
            contentDescription = label,
        )
        Text(
            text = "$label: ${if (configured) "已配置" else "未配置"}",
            fontSize = 15.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 工作卡下方左右两卡：左卡 = 三任务复选框（图标+名字），右卡 = 执行按钮
 * （执行勾选的任务 → TaskScheduler 并行运行；运行中按钮变「执行中…」置灰）。
 */
@Composable
private fun TaskPickerRow() {
    val tasks = rememberTaskDefs()
    val checked = remember { mutableStateMapOf("wb" to true, "mhy" to true, "bing" to false) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val repo = remember { SettingsRepositoryImpl() }
    val scope = rememberCoroutineScope()

    val running by TaskScheduler.isRunning.collectAsStateWithLifecycle()
    val statuses by TaskScheduler.statuses.collectAsStateWithLifecycle()

    // 一键运行勾了 Bing：请求通知权限（M5；拒绝也不影响任务）
    androidx.compose.runtime.LaunchedEffect(running) {
        if (running && !TaskNotifier.hasPermission(context)) {
            androidx.core.app.ActivityCompat.requestPermissions(
                context as androidx.activity.ComponentActivity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }
    }

    // 状态实时读取（rememberTaskDefs 内部已用日志流驱动，登录后自动刷新）
    val configuredNow = tasks.associate { it.id to it.configured }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左卡：任务复选框列表（运行中置灰）
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                tasks.forEach { task ->
                    val done = statuses[task.id] == true
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SquircleIcon(
                            asset = task.asset,
                            size = 20.dp,
                            contentDescription = task.label,
                        )
                        Text(
                            text = when {
                                !configuredNow[task.id]!! -> task.label
                                done -> "${task.label} ✓"
                                else -> task.label
                            },
                            fontSize = 15.sp,
                            color = if (running) colorScheme.onSurfaceVariantSummary else colorScheme.onSurface,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .widthIn(min = 88.dp)
                                .height(20.dp)
                                .wrapContentHeight(align = Alignment.CenterVertically),
                            maxLines = 1,
                        )
                        Checkbox(
                            state = if (checked[task.id] == true) ToggleableState.On else ToggleableState.Off,
                            onClick = if (running) {
                                null
                            } else {
                                { checked[task.id] = checked[task.id] != true }
                            },
                            modifier = Modifier
                                .size(18.dp)
                                .offset(y = 1.dp),
                            enabled = !running,
                        )
                    }
                }
            }
        }

        // 右卡：执行按钮（运行中置灰不消失）
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (running) "任务执行中…" else "将执行勾选的任务",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = if (running) "执行中…" else "开始执行",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val selected = tasks.filter { checked[it.id] == true }.map { it.id }.toSet()
                        if (selected.isEmpty()) {
                            AppLog.log("SYS", "未勾选任何任务")
                        } else {
                            val names = selected.map { TaskScheduler.taskName(it) }
                            AppLog.log("SYS", "开始执行: ${names.joinToString("、")}")
                            scope.launch {
                                TaskScheduler.runAll(
                                    context = context,
                                    repo = repo,
                                    selected = selected,
                                    onBingStage = {
                                        // 后台挂载 Bing WebView 自动跑（不切页）
                                        BingEngine.mounted.value = true
                                        kotlinx.coroutines.delay(1500)
                                    },
                                )
                            }
                        }
                    },
                    enabled = !running,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (running) "勾选项已锁定" else "Bing 将在后台自动挂载执行",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun LogCard() {
    val logs by AppLog.entries.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "日志",
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            val recent = logs.takeLast(20)
            if (recent.isEmpty()) {
                Text(
                    text = "暂无日志",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.onSurface,
                )
            } else {
                recent.forEach { entry ->
                    LogRow(entry = entry, expanded = false, onClick = {})
                }
            }
        }
    }
}

@Composable
private fun SupportLinks(
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = stringResource(R.string.home_support_title),
            summary = stringResource(R.string.home_support_content),
            onClick = { },
        )
        top.yukonga.miuix.kmp.preference.ArrowPreference(
            title = stringResource(R.string.home_learn_kernelsu),
            summary = stringResource(R.string.home_click_to_learn_kernelsu),
            onClick = { },
        )
    }
}
