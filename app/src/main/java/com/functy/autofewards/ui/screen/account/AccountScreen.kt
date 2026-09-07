package com.functy.autofewards.ui.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.functy.autofewards.R
import com.functy.autofewards.core.AppLog
import com.functy.autofewards.core.TaskScheduler
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.functy.autofewards.ui.component.QrLoginDialog
import com.functy.autofewards.ui.component.ToastHost
import com.functy.autofewards.ui.theme.LocalEnableBlur
import com.functy.autofewards.ui.util.BlurredBar
import com.functy.autofewards.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 账号页：米游社（扫码登录 / Cookie 粘贴）+ WorkBuddy token + 配置导入。
 * 对应 Flutter 版 accounts_page.dart。
 */
@Composable
fun AccountScreen(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val repo = remember { SettingsRepositoryImpl() }
    val scope = rememberCoroutineScope()

    var mhyCookie by rememberSaveable { mutableStateOf("") }
    var wbToken by rememberSaveable { mutableStateOf(repo.secret("wb.token") ?: "") }
    var wbUid by rememberSaveable { mutableStateOf(repo.secret("wb.uid") ?: "") }
    var importText by rememberSaveable { mutableStateOf("") }
    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val hasStoken = remember { mutableStateOf(repo.hasSecret("mhy.stoken")) }
    val hasCookie = remember { mutableStateOf(repo.hasSecret("mhy.cookie")) }
    val hasWbToken = remember { mutableStateOf(repo.hasSecret("wb.token")) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = androidx.compose.ui.res.stringResource(R.string.tab_account),
                    scrollBehavior = scrollBehavior
                )
            }
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
                        modifier = Modifier.padding(top = 12.dp, bottom = bottomInnerPadding),
                    ) {
                        // ── 米游社 ──
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "米游社",
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                val mhyStatus = when {
                                    hasStoken.value -> "已登录（stoken）"
                                    hasCookie.value -> "已配置（仅 web cookie，任务需扫码）"
                                    else -> "未登录"
                                }
                                Text(
                                    text = "状态: $mhyStatus",
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TextButton(
                                        text = "扫码登录",
                                        onClick = { showQrDialog = true },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                    )
                                    TextButton(
                                        text = "粘贴 Cookie 登录",
                                        onClick = {
                                            if (mhyCookie.isBlank()) {
                                                toast = "请先在下方输入框粘贴 cookie"
                                            } else {
                                                toast = "验证中…"
                                                scope.launch {
                                                    val r = withContext(Dispatchers.IO) {
                                                        TaskScheduler.mihoyoService(repo).importCookie(mhyCookie.trim())
                                                    }
                                                    hasStoken.value = repo.hasSecret("mhy.stoken")
                                                    hasCookie.value = repo.hasSecret("mhy.cookie")
                                                    toast = r.summary
                                                    mhyCookie = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                TextField(
                                    value = mhyCookie,
                                    onValueChange = { mhyCookie = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    label = "Cookie（stoken=v2_...; stuid=...; mid=...）",
                                    maxLines = 3,
                                )
                                Text(
                                    text = "推荐「扫码登录」（免抓包、得到完整 stoken）；也可以直接粘贴抓包 Cookie。仅存本机。",
                                    modifier = Modifier.padding(top = 8.dp),
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── WorkBuddy ──
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "WorkBuddy",
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = "状态: ${if (hasWbToken.value) "已配置 token" else "未配置"}",
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                TextField(
                                    value = wbToken,
                                    onValueChange = { wbToken = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    label = "accessToken（等同账号密码，仅存本机）",
                                    maxLines = 2,
                                )
                                TextField(
                                    value = wbUid,
                                    onValueChange = { wbUid = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    label = "uid（可选）",
                                    maxLines = 1,
                                )
                                TextButton(
                                    text = "保存",
                                    onClick = {
                                        if (wbToken.isBlank()) {
                                            toast = "token 不能为空"
                                        } else {
                                            TaskScheduler.workBuddyService(repo)
                                                .importToken(wbToken.trim(), uid = wbUid.trim())
                                            hasWbToken.value = repo.hasSecret("wb.token")
                                            toast = "WorkBuddy token 已保存（仅本机）"
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── 从 Flutter 版导入 ──
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "从 Flutter 版导入",
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = "粘贴 Flutter 版「导出配置」JSON（_format: autorewards-config），自动迁移登录态与设置。",
                                    modifier = Modifier.padding(top = 4.dp),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                TextField(
                                    value = importText,
                                    onValueChange = { importText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    label = "配置 JSON",
                                    maxLines = 4,
                                )
                                TextButton(
                                    text = "导入",
                                    onClick = {
                                        if (importText.isBlank()) {
                                            toast = "请先粘贴配置 JSON"
                                        } else {
                                            try {
                                                val n = repo.importJson(importText.trim())
                                                hasStoken.value = repo.hasSecret("mhy.stoken")
                                                hasCookie.value = repo.hasSecret("mhy.cookie")
                                                hasWbToken.value = repo.hasSecret("wb.token")
                                                wbToken = repo.secret("wb.token") ?: ""
                                                wbUid = repo.secret("wb.uid") ?: ""
                                                importText = ""
                                                toast = "已导入 $n 项配置（Bing Cookie 请到 Bing 页重新登录）"
                                            } catch (e: Exception) {
                                                toast = "导入失败: ${e.message}"
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        QrLoginDialog(
            repo = repo,
            onDismiss = { showQrDialog = false },
            onSuccess = {
                hasStoken.value = repo.hasSecret("mhy.stoken")
                hasCookie.value = repo.hasSecret("mhy.cookie")
                showQrDialog = false
                toast = "米游社扫码登录成功"
            },
        )
    }

    ToastHost(
        message = toast,
        onDismiss = { toast = null },
    )
}
