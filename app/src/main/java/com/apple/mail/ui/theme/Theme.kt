package com.apple.mail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * iOS 26 Mail 主题配色（Liquid Glass）。
 * 参考 Apple Mail 蓝 + 系统强调色。
 */
val MailBlue = Color(0xFF007AFF)
val MailBlueDark = Color(0xFF0A84FF)
val MailBackgroundLight = Color(0xFFF2F2F7)
val MailBackgroundDark = Color(0xFF000000)
val MailCardLight = Color(0xFFFFFFFF)
val MailCardDark = Color(0xFF1C1C1E)
val MailTextPrimary = Color(0xFF000000)
val MailTextPrimaryDark = Color(0xFFFFFFFF)
val MailTextSecondary = Color(0xFF8E8E93)
val MailTextSecondaryDark = Color(0xFF8E8E93)
val MailDivider = Color(0xFFE5E5EA)
val MailDividerDark = Color(0xFF38383A)
val MailGlassTintLight = Color(0x99FFFFFF)
val MailGlassTintDark = Color(0x66000000)

/** 头像调色板（iOS 系统多彩） */
val AvatarPalette = listOf(
    Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500),
    Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFF5AC8FA),
    Color(0xFFFFCC00), Color(0xFFFF3B30)
)

private val LightColors = lightColorScheme(
    primary = MailBlue,
    onPrimary = Color.White,
    secondary = MailBlueDark,
    background = MailBackgroundLight,
    onBackground = MailTextPrimary,
    surface = MailCardLight,
    onSurface = MailTextPrimary,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = MailTextSecondary,
    outline = MailDivider
)

private val DarkColors = darkColorScheme(
    primary = MailBlueDark,
    onPrimary = Color.White,
    secondary = MailBlueDark,
    background = MailBackgroundDark,
    onBackground = MailTextPrimaryDark,
    surface = MailCardDark,
    onSurface = MailTextPrimaryDark,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = MailTextSecondaryDark,
    outline = MailDividerDark
)

@Composable
fun AppleMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
