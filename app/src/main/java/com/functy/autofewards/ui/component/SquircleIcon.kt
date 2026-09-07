package com.functy.autofewards.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 原版 Flutter SquircleIcon 的 Compose 对应物：
 * 尺寸×0.30 的 squircle（continuous corner）+ surfaceContainerHighest 底色 + cover 填充。
 * 图片以同半径 rounded clip 覆盖在 squircle 底上（边缘即底色，视觉与 Flutter 版一致）。
 */
@Composable
fun SquircleIcon(
    @DrawableRes asset: Int,
    size: Dp = 28.dp,
    contentDescription: String? = null,
) {
    val radius = size * 0.30f
    Box(
        modifier = Modifier
            .size(size)
            .squircleBackground(
                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                cornerRadius = radius,
            ),
    ) {
        Image(
            painter = painterResource(asset),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius)),
            contentScale = ContentScale.Crop,
        )
    }
}
