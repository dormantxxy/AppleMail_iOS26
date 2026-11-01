package com.apple.mail.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.EmailMessage
import com.apple.mail.data.MailPriority
import com.apple.mail.ui.theme.AvatarPalette
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * 发件人头像（圆形 + 首字母 + 多彩背景）。
 */
@Composable
fun Avatar(name: String, colorIndex: Int, size: Int = 48) {
    val color = AvatarPalette[colorIndex % AvatarPalette.size]
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = (size * 0.42).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 邮件列表卡片（Liquid Glass 风格，独立圆角卡片）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailCard(
    email: EmailMessage,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected) MailBlue.copy(alpha = 0.15f) else cardColor.copy(alpha = 0.96f)
            )
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, end = 10.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelectionMode ->
                                if (isSelected) MailBlue else Color.Transparent
                            !email.isRead -> MailBlue
                            else -> Color.Transparent
                        }
                    )
            )
            Avatar(name = email.senderName, colorIndex = email.avatarColorIndex, size = 46)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = email.senderName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (email.isRead) FontWeight.Medium else FontWeight.Bold,
                        color = if (email.isRead) MailTextSecondary else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (email.isFlagged) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (email.hasAttachment) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = MailTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = email.displayTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MailTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = email.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )

                Text(
                    text = email.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MailTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )

                if (email.priority == MailPriority.PRIORITY) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "优先",
                            color = Color(0xFFFF9500),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 摘要卡片（同发件人邮件聚合，Digest View）。
 */
@Composable
fun DigestCard(
    senderName: String,
    colorIndex: Int,
    emails: List<EmailMessage>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor.copy(alpha = 0.96f))
            .clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(name = senderName, colorIndex = colorIndex, size = 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = senderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${emails.size} 封邮件 · 最近 ${emails.first().displayTime}",
                    color = MailTextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "展开",
                tint = MailTextSecondary
            )
        }
        emails.take(2).forEach { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (e.isRead) Color.Transparent else MailBlue)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = e.subject,
                    fontWeight = if (e.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = e.displayTime, color = MailTextSecondary, fontSize = 11.sp)
            }
        }
    }
}
