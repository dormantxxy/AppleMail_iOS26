package com.apple.mail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.MailCategory
import com.apple.mail.ui.theme.MailBlue

private fun categoryIcon(category: MailCategory) = when (category) {
    MailCategory.PRIMARY -> Icons.Filled.Inbox
    MailCategory.TRANSACTIONS -> Icons.Filled.CreditCard
    MailCategory.UPDATES -> Icons.Filled.Notifications
    MailCategory.PROMOTIONS -> Icons.Filled.Sell
}

/**
 * iOS 26 分类标签栏（Liquid Glass 分段控件）。
 * 主要 / 交易 / 更新 / 推广 四个玻璃胶囊标签。
 */
@Composable
fun CategoryTabs(
    selected: MailCategory,
    onSelect: (MailCategory) -> Unit,
    unreadCounts: Map<MailCategory, Int>,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val trackColor = if (isDark) Color(0x33222222) else Color(0x11000000)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .padding(4.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MailCategory.entries.forEach { cat ->
                item {
                    val isSelected = selected == cat
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MailBlue.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable { onSelect(cat) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIcon(cat),
                            contentDescription = null,
                            tint = if (isSelected) cat.accent else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = cat.title,
                            color = if (isSelected) cat.accent else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        val count = unreadCounts[cat] ?: 0
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cat.accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = cat.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
