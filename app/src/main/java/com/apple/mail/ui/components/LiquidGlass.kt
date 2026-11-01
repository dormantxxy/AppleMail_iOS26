package com.apple.mail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid Glass（液态玻璃）容器。
 * 用 Haze 毛玻璃 + 高光描边 + 内部渐变色，模拟 iOS 26 玻璃质感。
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tintAlpha: Float = 0.6f,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit
) {
    val state = hazeState ?: remember { HazeState() }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .hazeEffect(state = state)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.3f else 0.8f),
                        Color.White.copy(alpha = if (isDark) 0.05f else 0.2f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.10f else 0.45f),
                        Color.White.copy(alpha = if (isDark) 0.02f else 0.15f)
                    )
                )
            ),
        content = { content() }
    )
}

/**
 * 玻璃图标按钮（圆角胶囊），对应 iOS 26 工具栏中的玻璃按钮。
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
    size: Dp = 40.dp
) {
    val hazeState = remember { HazeState() }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(size / 2))
            .hazeEffect(state = hazeState)
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(size / 2)
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.4f),
                        Color.White.copy(alpha = if (isDark) 0.0f else 0.1f)
                    )
                )
            ),
        content = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    )
}

/**
 * 玻璃胶囊按钮（带文字）。
 */
@Composable
fun GlassPillButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    val hazeState = remember { HazeState() }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .hazeEffect(state = hazeState)
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.12f else 0.5f),
                        Color.White.copy(alpha = if (isDark) 0.02f else 0.15f)
                    )
                )
            )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Text(text = text, color = tint)
        }
    }
}
