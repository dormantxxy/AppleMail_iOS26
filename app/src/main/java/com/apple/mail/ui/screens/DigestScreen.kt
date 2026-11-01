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
            // 顶部工具栏
            LiquidGlassContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack, tint = MailBlue, size = 40.dp)
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = senderName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(text = "${emails.size} 封邮件", fontSize = 12.sp, color = MailTextSecondary)
                    }
                    GlassIconButton(icon = Icons.Filled.CheckCircle, onClick = {}, tint = Color.Gray, size = 40.dp)
                }
            }

            // 发件人头像 + 信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name = senderName, colorIndex = 0, size = 56)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = senderName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = "${emails.size} 封邮件来自同一发件人", fontSize = 13.sp, color = MailTextSecondary)
                }
            }

            // 邮件列表
            LazyColumn(
                modifier = Modifier.weight(1f),
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
