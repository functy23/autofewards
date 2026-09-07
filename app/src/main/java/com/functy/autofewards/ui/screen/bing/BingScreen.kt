package com.functy.autofewards.ui.screen.bing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.functy.autofewards.AutoFewardsApp
import com.functy.autofewards.R
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.functy.autofewards.ui.component.SquircleIcon
import com.functy.autofewards.ui.theme.LocalEnableBlur
import com.functy.autofewards.ui.util.BlurredBar
import com.functy.autofewards.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Bing 页（M4）：挂载原生 WebView 自动搜索。
 * - 引擎脚本准备完成后才创建 WebView（脚本随 onLoadFinished 注入）
 * - 状态条 + 操作按钮（手动开始 / 兜底搜索器 / 重新注入）
 */
@Composable
fun BingScreen(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val repo = remember { SettingsRepositoryImpl() }

    // WebView 只在首次进入本页时挂载；主页一键运行也会通过 BingEngine.mounted
    // 触发后台挂载（不切页，Pager beyondViewportPageCount=3 保证页面保活）
    var webViewCreated by rememberSaveable { mutableStateOf(false) }
    val engineMounted by BingEngine.mounted
    val shouldMount = webViewCreated || engineMounted
    val status by BingEngine.status

    androidx.compose.runtime.DisposableEffect(shouldMount) {
        onDispose {
            // 仅在整个页面离开组合（Activity 销毁）时释放
            if (!shouldMount) BingEngine.releaseWebView()
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = androidx.compose.ui.res.stringResource(R.string.tab_bing),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
            ) {
                // 状态条
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = "状态: $status",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                text = "手动开始",
                                onClick = { BingEngine.autoStartScript() },
                                modifier = Modifier.weight(1f),
                                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                            )
                            TextButton(
                                text = "兜底搜索",
                                onClick = { BingEngine.startFallbackSearcher(repo) },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "重新注入",
                                onClick = { BingEngine.reloadAndReinject() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (!shouldMount) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Bing Rewards",
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "点击下方按钮挂载 WebView 并自动开始积分搜索。",
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                            TextButton(
                                text = "挂载并开始",
                                onClick = { webViewCreated = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 12.dp),
                        factory = { context ->
                            BingEngine.createWebView(context, repo)
                        },
                        onRelease = { /* 保活：不销毁，由引擎统一管理 */ },
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }
}
