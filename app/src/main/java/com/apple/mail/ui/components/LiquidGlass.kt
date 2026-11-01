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
 * Liquid Glass 容器：毛玻璃 + 轻微高光描边 + 极淡渐变（贴近 iOS 26 的克制质感）。
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
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.14f else 0.45f),
                        Color.White.copy(alpha = if (isDark) 0.03f else 0.08f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.06f else 0.28f),
                        Color.White.copy(alpha = if (isDark) 0.01f else 0.06f)
                    )
                )
            ),
        content = { content() }
    )
}

/** 圆形玻璃图标按钮（iOS 26 工具栏按钮风格）。 */
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
                color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(size / 2)
            )
            .background(
                if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.55f)
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

/** 玻璃胶囊按钮（带文字）。 */
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
                color = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
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
            Text(text = text, color = tint, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
    }
}
