package com.apple.mail.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.MailCategory
import com.apple.mail.ui.theme.MailTextSecondary

private fun categoryIcon(category: MailCategory): ImageVector = when (category) {
    MailCategory.PRIMARY -> Icons.Filled.Inbox
    MailCategory.TRANSACTIONS -> Icons.Filled.CreditCard
    MailCategory.UPDATES -> Icons.Filled.Notifications
    MailCategory.PROMOTIONS -> Icons.Filled.Sell
}

/**
 * iOS 26 Mail 分类分段控件：四个等宽胶囊，选中项以分类色填充。
 */
@Composable
fun CategoryTabs(
    selected: MailCategory,
    onSelect: (MailCategory) -> Unit,
    unreadCounts: Map<MailCategory, Int>,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val track = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isDark) Color(0xFF000000) else Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MailCategory.entries.forEach { cat ->
            val isSelected = selected == cat
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) cat.accent else track,
                label = "catBg"
            )
            val fgColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MailTextSecondary,
                label = "catFg"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelect(cat) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = categoryIcon(cat),
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = cat.title,
                    color = fgColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                val count = unreadCounts[cat] ?: 0
                if (count > 0) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = count.toString(),
                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else cat.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
