package com.apple.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.EmailMessage
import com.apple.mail.ui.components.*
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * iOS 26 Mail 摘要视图（Digest View）。
 * 同一发件人的所有邮件聚合。
 */
@Composable
fun DigestScreen(
    senderName: String,
    emails: List<EmailMessage>,
    onBack: () -> Unit,
    onOpenEmail: (Long) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF000000) else Color.White)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MailBlue)
                }
                Column(Modifier.weight(1f)) {
                    Text(text = senderName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(text = "${emails.size} 封邮件", fontSize = 12.sp, color = MailTextSecondary)
                }
            }
            HorizontalDivider(
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                thickness = 0.5.dp
            )

            // 发件人头像 + 信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF000000) else Color.White)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name = senderName, colorIndex = 0, size = 48)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = senderName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = "${emails.size} 封邮件来自同一发件人", fontSize = 13.sp, color = MailTextSecondary)
                }
            }

            // 邮件列表
            LazyColumn(
                modifier = Modifier.weight(1f).background(if (isDark) Color(0xFF000000) else Color.White),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(emails, key = { it.id }) { email ->
                    MailCard(
                        email = email,
                        onClick = { onOpenEmail(email.id) },
                        onLongClick = {},
                        onToggleSelect = {}
                    )
                }
            }
        }
    }
}
