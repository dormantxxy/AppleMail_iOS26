package com.apple.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.EmailMessage
import com.apple.mail.ui.components.GlassIconButton
import com.apple.mail.ui.components.LiquidGlassContainer
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * iOS 26 Mail 邮件阅读页（支持附件下载）。
 */
@Composable
fun EmailDetailScreen(
    email: EmailMessage,
    onBack: () -> Unit,
    onToggleFlag: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onDownloadAttachment: (cb: (String) -> Unit) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var downloadStatus by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
    ) {
        Column(Modifier.fillMaxSize()) {
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
                        Text(text = email.senderName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(text = email.senderEmail, fontSize = 12.sp, color = MailTextSecondary)
                    }
                    GlassIconButton(
                        icon = Icons.Outlined.Flag,
                        onClick = onToggleFlag,
                        tint = if (email.isFlagged) Color(0xFFFF9500) else Color.Gray,
                        size = 40.dp
                    )
                    Spacer(Modifier.width(4.dp))
                    GlassIconButton(icon = Icons.Filled.MoreHoriz, onClick = {}, tint = Color.Gray, size = 40.dp)
                }
            }

            Text(
                text = email.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "发往：${email.recipients.ifBlank { "我" }}",
                    fontSize = 13.sp, color = MailTextSecondary,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = email.displayTime, fontSize = 13.sp, color = MailTextSecondary)
            }

            LiquidGlassContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .weight(1f),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Text(
                        text = email.body,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp
                    )

                    if (email.hasAttachment) {
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MailBlue.copy(alpha = 0.08f))
                                .clickable {
                                    downloadStatus = "正在下载…"
                                    onDownloadAttachment { msg -> downloadStatus = msg }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = MailBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = email.attachmentName ?: "附件",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = downloadStatus ?: "点击下载",
                                    fontSize = 12.sp,
                                    color = MailTextSecondary
                                )
                            }
                            Icon(Icons.Filled.Download, contentDescription = "下载", tint = MailBlue)
                        }
                    }
                }
            }
        }

        LiquidGlassContainer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            cornerRadius = 28.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GlassIconButton(icon = Icons.Filled.Archive, onClick = onArchive, tint = MailBlue, size = 44.dp)
                GlassIconButton(icon = Icons.Filled.Folder, onClick = {}, tint = Color.Gray, size = 44.dp)
                GlassIconButton(icon = Icons.Outlined.Flag, onClick = onToggleFlag, tint = Color.Gray, size = 44.dp)
                GlassIconButton(icon = Icons.AutoMirrored.Filled.Reply, onClick = onReply, tint = MailBlue, size = 44.dp)
            }
        }
    }
}
