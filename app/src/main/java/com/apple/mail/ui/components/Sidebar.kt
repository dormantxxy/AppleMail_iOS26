package com.apple.mail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.MailAccount
import com.apple.mail.ui.theme.AvatarPalette
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * iOS 26 Mail 邮箱侧栏：大标题 + 搜索 + 分组列表（智能邮箱 / 账户 / 文件夹）。
 */
@Composable
fun MailSidebar(
    accounts: List<MailAccount>,
    currentFolder: String,
    onSelectFolder: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    counts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val surface = if (isDark) Color(0xFF1C1C1E) else Color.White
    val divider = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val textColor = if (isDark) Color.White else Color.Black
    var query by remember { mutableStateOf("") }

    fun match(name: String) = query.isBlank() || name.contains(query, ignoreCase = true)

    Box(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "邮箱",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                IconButtonSpacer()
                TextButton(onClick = onRefresh) {
                    Text("刷新", color = MailBlue, fontSize = 15.sp)
                }
                TextButton(onClick = onClose) {
                    Text("完成", color = MailBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // 搜索
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0x33FFFFFF) else Color(0x14767680))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MailTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = textColor),
                    cursorBrush = SolidColor(MailBlue),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("搜索", color = MailTextSecondary, fontSize = 15.sp)
                            }
                            inner()
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 智能邮箱
            SectionHeader("智能邮箱")
            SurfaceGroup(surface) {
                val folders = listOf(
                    Triple("收件箱", Icons.Filled.Inbox, MailBlue),
                    Triple("VIP", Icons.Filled.Star, Color(0xFFFF9500)),
                    Triple("有附件", Icons.Filled.AttachFile, Color(0xFF34C759)),
                    Triple("未读", Icons.Filled.MarkEmailUnread, Color(0xFFAF52DE)),
                    Triple("今天", Icons.Filled.Today, Color(0xFF5AC8FA))
                )
                folders.filter { match(it.first) }.forEachIndexed { index, (name, icon, tint) ->
                    FolderRow(
                        icon = icon,
                        name = name,
                        tint = tint,
                        selected = currentFolder == name,
                        textColor = textColor,
                        count = counts[name] ?: 0,
                        onClick = { onSelectFolder(name) }
                    )
                    if (index != folders.filter { match(it.first) }.lastIndex) {
                        RowDivider(divider, startPadding = 52)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 账户
            SectionHeader("账户")
            SurfaceGroup(surface) {
                accounts.forEachIndexed { index, account ->
                    if (match(account.displayName) || match(account.email)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFolder("收件箱") }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(AvatarPalette[account.colorIndex % AvatarPalette.size])
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.displayName, fontSize = 16.sp, color = textColor)
                                Text(account.email, fontSize = 12.sp, color = MailTextSecondary)
                            }
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "移除账户",
                                tint = MailTextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemoveAccount(account.id) }
                            )
                        }
                        RowDivider(divider, startPadding = 38)
                    }
                }
                // 添加账户
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddAccount() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MailBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("添加账户", color = MailBlue, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // 文件夹
            SectionHeader("文件夹")
            SurfaceGroup(surface) {
                val folders = listOf(
                    Triple("草稿", Icons.Filled.Edit, MailTextSecondary),
                    Triple("已发送", Icons.AutoMirrored.Filled.Send, MailTextSecondary),
                    Triple("垃圾", Icons.Filled.Report, MailTextSecondary),
                    Triple("废纸篓", Icons.Filled.Delete, Color(0xFFFF3B30))
                )
                val visible = folders.filter { match(it.first) }
                visible.forEachIndexed { index, (name, icon, tint) ->
                    FolderRow(
                        icon = icon,
                        name = name,
                        tint = tint,
                        selected = currentFolder == name,
                        textColor = textColor,
                        onClick = { onSelectFolder(name) }
                    )
                    if (index != visible.lastIndex) {
                        RowDivider(divider, startPadding = 52)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MailTextSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SurfaceGroup(surface: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
    ) {
        content()
    }
}

@Composable
private fun RowDivider(color: Color, startPadding: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding.dp)
            .height(0.5.dp)
            .background(color)
    )
}

@Composable
private fun FolderRow(
    icon: ImageVector,
    name: String,
    tint: Color,
    selected: Boolean,
    textColor: Color,
    count: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 16.sp,
            color = if (selected) MailBlue else textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                color = MailTextSecondary,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Checkmark,
                contentDescription = null,
                tint = MailBlue,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun IconButtonSpacer() {
    // 占位，保持标题与按钮间距一致
    Spacer(Modifier.width(4.dp))
}
