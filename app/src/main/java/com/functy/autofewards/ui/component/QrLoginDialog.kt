package com.functy.autofewards.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.functy.autofewards.data.repository.MihoyoBbsService
import com.functy.autofewards.data.repository.SettingsRepositoryImpl
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 生成二维码 Bitmap（zxing core，固定尺寸绘制——与 Flutter 版 A.7 同理不用 intrinsics）。 */
private fun makeQrBitmap(content: String, sizePx: Int = 660): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

/**
 * 米游社扫码登录对话框：展示二维码 + 每 2 秒轮询确认状态。
 * Flutter 版 _QrLoginDialog 翻译（WindowDialog + zxing 画码）。
 */
@Composable
fun QrLoginDialog(
    repo: SettingsRepositoryImpl,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val service = remember { MihoyoBbsService(repo) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("正在创建二维码…") }
    var terminal by remember { mutableStateOf(false) }

    // 创建会话（网络请求必须离开主线程：OkHttp 同步调用在 Main 会抛 NetworkOnMainThreadException）
    LaunchedEffect(Unit) {
        val session = withContext(Dispatchers.IO) {
            service.createQrLogin()
        }
        if (session == null) {
            statusText = "创建二维码失败，详情见日志"
            terminal = true
            return@LaunchedEffect
        }
        qrBitmap = runCatching { makeQrBitmap(session.url) }.getOrNull()
        if (qrBitmap == null) {
            statusText = "二维码生成失败"
            terminal = true
            return@LaunchedEffect
        }
        statusText = "请用米游社 App 扫描二维码"
        // 轮询（2s 间隔；与 Flutter 版一致）
        var elapsed = 0
        while (!terminal && elapsed < 120_000) {
            delay(2000)
            elapsed += 2000
            if (terminal) break
            val s = withContext(Dispatchers.IO) {
                service.pollQrLoginStatus(ticket = session.ticket, deviceId = session.deviceId)
            }
            when {
                s == "Created" -> {}
                s == "Scanned" -> statusText = "已扫码，请在手机上确认"
                s == "Confirmed" -> {
                    terminal = true
                    onSuccess()
                }
                s == "Expired" -> {
                    terminal = true
                    statusText = "二维码已过期，请重新发起"
                }
                s.startsWith("Error") -> {
                    terminal = true
                    statusText = s.removePrefix("Error:")
                }
            }
        }
        if (!terminal && elapsed >= 120_000) {
            statusText = "二维码已超时，请重新发起"
            terminal = true
        }
    }

    WindowDialog(
        show = true,
        title = "米游社扫码登录",
        onDismissRequest = { onDismiss() },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val bmp = qrBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "米游社登录二维码",
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(colorScheme.surfaceContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = statusText,
                    color = colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    text = "关闭",
                    onClick = { onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** 轻量 Toast（自动消失）。 */
@Composable
fun ToastHost(
    message: String?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            onDismiss()
        }
    }
    if (message != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 96.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = message,
                modifier = Modifier
                    .background(
                        colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                color = colorScheme.onSurface,
                fontSize = 14.sp,
            )
        }
    }
}
