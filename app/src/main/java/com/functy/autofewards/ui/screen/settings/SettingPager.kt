package com.functy.autofewards.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.functy.autofewards.R
import com.functy.autofewards.core.ConfigTransfer
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.functy.autofewards.ui.theme.LocalEnableBlur
import com.functy.autofewards.ui.util.BlurredBar
import com.functy.autofewards.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 设置页：任务三分栏（总开关+分项）/ 界面 / 配置 / 关于。 */
@Composable
fun SettingPager(
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val repo = remember { SettingsRepositoryImpl() }
    var wbEnabled by rememberSaveable { mutableStateOf(repo.wbEnabled) }
    var mhyEnabled by rememberSaveable { mutableStateOf(repo.mhyEnabled) }
    var mhyGameSign by rememberSaveable { mutableStateOf(repo.mhyGameSign) }
    var mhyBbsSign by rememberSaveable { mutableStateOf(repo.mhyBbsSign) }
    var mhyRead by rememberSaveable { mutableStateOf(repo.mhyRead) }
    var mhyLike by rememberSaveable { mutableStateOf(repo.mhyLike) }
    var mhyCancelLike by rememberSaveable { mutableStateOf(repo.mhyCancelLike) }
    var mhyShare by rememberSaveable { mutableStateOf(repo.mhyShare) }
    var bingEnabled by rememberSaveable { mutableStateOf(repo.bingEnabled) }
    var bingFallback by rememberSaveable { mutableStateOf(repo.bingFallback) }
    var bingProgress by rememberSaveable { mutableStateOf(repo.bingProgress) }
    var notification by rememberSaveable { mutableStateOf(repo.taskNotification) }
    var enableBlurState by rememberSaveable { mutableStateOf(repo.enableBlur) }
    var floatingBar by rememberSaveable { mutableStateOf(repo.enableFloatingBottomBar) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
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
                    // ── WorkBuddy ──
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_wb_enable),
                            summary = stringResource(R.string.settings_wb_enable_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Workspaces,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_wb_enable),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = wbEnabled,
                            onCheckedChange = {
                                wbEnabled = it
                                repo.setTaskSwitch("task_wb_enabled", it)
                            }
                        )
                    }

                    // ── 米游社：总开关 + 分项 ──
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_mhy_enable),
                            summary = stringResource(R.string.settings_mhy_enable_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Security,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_mhy_enable),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = mhyEnabled,
                            onCheckedChange = {
                                mhyEnabled = it
                                repo.setTaskSwitch("task_mhy_enabled", it)
                            }
                        )
                        AnimatedVisibility(visible = mhyEnabled) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(R.string.settings_mhy_game_sign),
                                    summary = stringResource(R.string.settings_mhy_game_sign_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Security,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_mhy_game_sign),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = mhyGameSign,
                                    onCheckedChange = {
                                        mhyGameSign = it
                                        repo.setTaskSwitch("mhy_game_sign", it)
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_mhy_bbs_sign),
                                    summary = stringResource(R.string.settings_mhy_bbs_sign_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Share,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_mhy_bbs_sign),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = mhyBbsSign,
                                    onCheckedChange = {
                                        mhyBbsSign = it
                                        repo.setTaskSwitch("mhy_bbs_sign", it)
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_mhy_read),
                                    summary = stringResource(R.string.settings_mhy_read_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Visibility,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_mhy_read),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = mhyRead,
                                    onCheckedChange = {
                                        mhyRead = it
                                        repo.setTaskSwitch("mhy_read", it)
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_mhy_like),
                                    summary = stringResource(R.string.settings_mhy_like_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.ThumbUp,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_mhy_like),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = mhyLike,
                                    onCheckedChange = {
                                        mhyLike = it
                                        repo.setTaskSwitch("mhy_like", it)
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_mhy_cancel_like),
                                    summary = stringResource(R.string.settings_mhy_cancel_like_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.ThumbUp,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_mhy_cancel_like),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = mhyCancelLike,
                                    onCheckedChange = {
                                        mhyCancelLike = it
                                        repo.setTaskSwitch("mhy_cancel_like", it)
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_mhy_share),
                                    summary = stringResource(R.string.settings_mhy_share_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Share,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_mhy_share),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = mhyShare,
                                    onCheckedChange = {
                                        mhyShare = it
                                        repo.setTaskSwitch("mhy_share", it)
                                    }
                                )
                            }
                        }
                    }

                    // ── Bing：总开关 + 分项 ──
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_bing_enable),
                            summary = stringResource(R.string.settings_bing_enable_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Search,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_bing_enable),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = bingEnabled,
                            onCheckedChange = {
                                bingEnabled = it
                                repo.setTaskSwitch("task_bing_enabled", it)
                            }
                        )
                        AnimatedVisibility(visible = bingEnabled) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(R.string.settings_bing_fallback),
                                    summary = stringResource(R.string.settings_bing_fallback_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Search,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_bing_fallback),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = bingFallback,
                                    onCheckedChange = {
                                        bingFallback = it
                                        repo.setTaskSwitch("bing_fallback", it)
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.settings_notification),
                                    summary = stringResource(R.string.settings_notification_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.NotificationsActive,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(R.string.settings_notification),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = notification,
                                    onCheckedChange = {
                                        notification = it
                                        repo.setTaskSwitch("task_notification", it)
                                    }
                                )
                            }
                        }
                    }

                    // ── 界面 ──
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        var themeMode by rememberSaveable { mutableStateOf(repo.themeMode) }
                        TabRow(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            tabs = listOf(
                                stringResource(R.string.settings_theme_mode_system),
                                stringResource(R.string.settings_theme_mode_light),
                                stringResource(R.string.settings_theme_mode_dark),
                            ),
                            selectedTabIndex = themeMode.coerceIn(0, 2),
                            colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                            onTabSelected = { index ->
                                themeMode = index
                                repo.themeMode = index
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_blur),
                            summary = stringResource(R.string.settings_enable_blur_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_enable_blur),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = enableBlurState,
                            onCheckedChange = {
                                enableBlurState = it
                                repo.enableBlur = it
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_floating_bottom_bar),
                            summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.CallToAction,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_floating_bottom_bar),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = floatingBar,
                            onCheckedChange = {
                                floatingBar = it
                                repo.enableFloatingBottomBar = it
                            }
                        )
                    }

                    // ── 配置 ──
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        var showTransfer by rememberSaveable { mutableStateOf(false) }
                        ArrowPreference(
                            title = stringResource(R.string.settings_import_export),
                            summary = stringResource(R.string.settings_import_export_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.CloudSync,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_import_export),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showTransfer = true }
                        )
                        if (showTransfer) {
                            ConfigTransferCard(onClose = { showTransfer = false })
                        }
                    }

                    // ── 关于 ──
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_about_source),
                            summary = stringResource(R.string.settings_about_source_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Info,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_about_source),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { }
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding + 12.dp))
                }
            }
        }
    }
}

/** 配置导入/导出内嵌卡：导出（复制到剪贴板）+ 导入（粘贴 Flutter 版或本应用导出的 JSON）。 */
@Composable
private fun ConfigTransferCard(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var transferText by rememberSaveable { mutableStateOf("") }
    var transferMsg by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        TextButton(
            text = "导出配置（复制到剪贴板）",
            onClick = {
                try {
                    val json = ConfigTransfer.exportJson()
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("autorewards-config", json),
                    )
                    transferMsg = "已导出并复制到剪贴板（${json.length} 字符，含登录态，注意保管）"
                } catch (e: Exception) {
                    transferMsg = "导出失败: ${e.message}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
        )
        TextField(
            value = transferText,
            onValueChange = { transferText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = "粘贴配置 JSON（Flutter 版或本应用导出）",
            maxLines = 5,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            TextButton(
                text = "导入",
                onClick = {
                    if (transferText.isBlank()) {
                        transferMsg = "请先粘贴配置 JSON"
                    } else {
                        try {
                            val (keys, cookies) = ConfigTransfer.importJson(transferText.trim())
                            transferMsg = "已导入 $keys 项配置、$cookies 条 Bing Cookie（部分配置重启后生效）"
                            transferText = ""
                        } catch (e: Exception) {
                            transferMsg = "导入失败: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "关闭",
                onClick = onClose,
                modifier = Modifier.weight(1f),
            )
        }
        transferMsg?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
