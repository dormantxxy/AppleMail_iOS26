package com.apple.mail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.MailAccount
import com.apple.mail.ui.theme.AvatarPalette
import com.apple.mail.ui.theme.MailBlue

@Composable
fun MailSidebar(
    accounts: List<MailAccount>,
    currentFolder: String,
    onSelectFolder: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val panelColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

    Box(
        modifier = modifier
            .width(310.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(panelColor.copy(alpha = 0.99f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "邮箱",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    tint = MailBlue,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onRefresh() }
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { onClose() }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("智能邮箱", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FolderRow(Icons.Filled.Inbox, "收件箱", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.Star, "VIP", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.AttachFile, "有附件", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.MarkEmailUnread, "未读", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.Today, "今天", currentFolder, onSelectFolder)

            Spacer(Modifier.height(20.dp))

            Text("账户", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (accounts.isEmpty()) {
                Text("尚未添加账户", color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
            }
            accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AvatarPalette[account.colorIndex % AvatarPalette.size])
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(account.email, color = Color.Gray, fontSize = 12.sp)
                    }
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "移除账户",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onRemoveAccount(account.id) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAddAccount() }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null, tint = MailBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("添加账户", color = MailBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(20.dp))

            Text("文件夹", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FolderRow(Icons.Filled.Edit, "草稿", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.Send, "已发送", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.Delete, "废纸篓", currentFolder, onSelectFolder)
            FolderRow(Icons.Filled.Report, "垃圾", currentFolder, onSelectFolder)
        }
    }
}

@Composable
private fun FolderRow(icon: ImageVector, name: String, current: String, onSelect: (String) -> Unit) {
    val selected = current == name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MailBlue.copy(alpha = 0.13f) else Color.Transparent)
            .clickable { onSelect(name) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MailBlue else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            color = if (selected) MailBlue else Color.Unspecified,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp
        )
    }
}
