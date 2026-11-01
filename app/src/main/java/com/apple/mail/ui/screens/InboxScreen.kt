package com.apple.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.MailViewModel
import com.apple.mail.data.MailCategory
import com.apple.mail.ui.components.*
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: MailViewModel,
    onOpenEmail: (Long) -> Unit,
    onCompose: () -> Unit,
    onOpenDigest: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val emails = viewModel.filteredEmails()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val hazeState = remember { HazeState() }
    var menuOpen by remember { mutableStateOf(false) }
    val folderCounts = mapOf(
        "收件箱" to viewModel.folderUnread("收件箱"),
        "VIP" to viewModel.folderUnread("VIP"),
        "有附件" to viewModel.folderUnread("有附件"),
        "未读" to viewModel.folderUnread("未读"),
        "今天" to viewModel.folderUnread("今天")
    )

    // 操作结果提示自动消失
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2600)
            viewModel.consumeToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) listOf(Color(0xFF000000), Color(0xFF000000))
                        else listOf(Color(0xFFF2F2F7), Color(0xFFF2F2F7))
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF000000) else Color.White)
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleSidebar() }) {
                        Icon(Icons.Filled.Menu, contentDescription = "邮箱", tint = MailBlue)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.currentFolder,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val unreadCount = emails.count { !it.isRead }
                        Text(
                            text = when {
                                state.isLoading -> "同步中…"
                                state.lastSyncTime > 0L ->
                                    "$unreadCount 封未读 · ${android.text.format.DateFormat.format("HH:mm", state.lastSyncTime)}"
                                else -> "$unreadCount 封未读"
                            },
                            fontSize = 12.sp,
                            color = MailTextSecondary
                        )
                    }
                    TextButton(onClick = { viewModel.toggleSelectionMode() }) {
                        Text(
                            text = if (state.isSelectionMode) "完成" else "编辑",
                            color = MailBlue,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索", tint = MailBlue)
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = "更多",
                                tint = MailBlue
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (state.showListMode) "分类视图" else "列表视图") },
                                onClick = { viewModel.toggleListMode(); menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("全部标为已读") },
                                onClick = { viewModel.markAllRead(); menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = { viewModel.refreshAll(); menuOpen = false }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                thickness = 0.5.dp
            )

            if (state.isLoading && emails.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MailBlue,
                    trackColor = Color.Transparent
                )
            }

            if (state.searchActive) {
                MailSearchBar(
                    query = state.query,
                    onQueryChange = { viewModel.setQuery(it) },
                    onClose = { viewModel.toggleSearch() }
                )
            }

            if (!state.showListMode && state.currentFolder == "收件箱") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF000000) else Color.White)
                        .padding(bottom = 6.dp)
                ) {
                    CategoryTabs(
                        selected = state.selectedCategory,
                        onSelect = { viewModel.selectCategory(it) },
                        unreadCounts = MailCategory.entries.associateWith { viewModel.unreadCount(it) }
                    )
                }
            }

            if (state.errorMessage != null) {
                Surface(
                    color = Color(0xFFFF3B30).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.errorMessage ?: "", color = Color(0xFFFF3B30), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.refreshAll() }) { Text("重试", fontSize = 12.sp) }
                    }
                }
            }

            if (state.accounts.isEmpty()) {
                EmptyAccounts(onAddAccount)
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.refreshAll() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (emails.isEmpty()) {
                        if (state.isLoading) LoadingState() else EmptyMailState(onAddAccount)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isDark) Color(0xFF000000) else Color.White)
                                .hazeSource(hazeState),
                            contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp)
                        ) {
                            items(emails, key = { it.id }) { email ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        when (value) {
                                            SwipeToDismissBoxValue.StartToEnd -> viewModel.archiveEmail(email.id)
                                            SwipeToDismissBoxValue.EndToStart -> viewModel.deleteEmail(email.id)
                                            else -> {}
                                        }
                                        false
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        Row(Modifier.fillMaxSize()) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .background(Color(0xFF34C759)),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 20.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Filled.Archive, contentDescription = null, tint = Color.White)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("归档", color = Color.White, fontSize = 14.sp)
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .background(Color(0xFFFF3B30)),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(end = 20.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("删除", color = Color.White, fontSize = 14.sp)
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    MailCard(
                                        email = email,
                                        isSelected = state.selectedEmails.contains(email.id),
                                        isSelectionMode = state.isSelectionMode,
                                        onClick = {
                                            viewModel.markAsRead(email.id)
                                            if (email.category != MailCategory.PRIMARY && state.currentFolder == "收件箱" && !state.showListMode) {
                                                onOpenDigest(email.senderName)
                                            } else {
                                                onOpenEmail(email.id)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelectionMode() },
                                        onToggleSelect = { viewModel.toggleSelectEmail(email.id) }
                                    )
                                }
                            }
                            item {
                                if (state.isLoadingMore) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MailBlue,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    TextButton(
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    ) {
                                        Text("加载更多", color = MailBlue, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 侧栏
        if (state.showSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { viewModel.closeSidebar() }
            )
            MailSidebar(
                accounts = state.accounts,
                currentFolder = state.currentFolder,
                onSelectFolder = { viewModel.setFolder(it) },
                onAddAccount = {
                    viewModel.closeSidebar()
                    onAddAccount()
                },
                onRemoveAccount = { viewModel.removeAccount(it) },
                onRefresh = { viewModel.refreshAll(); viewModel.closeSidebar() },
                onClose = { viewModel.closeSidebar() },
                counts = folderCounts,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        // 操作结果提示
        if (state.toast != null) {
            Surface(
                color = Color(0xE6000000),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
            ) {
                Text(
                    text = state.toast ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // 底部工具栏
        if (!state.isSelectionMode) {
            BottomToolbar(
                onCompose = if (state.accounts.isEmpty()) onAddAccount else onCompose,
                onRefresh = { viewModel.refreshAll() },
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            SelectionToolbar(
                count = state.selectedEmails.size,
                onMarkRead = { viewModel.markSelectedRead() },
                onMarkUnread = { viewModel.markSelectedUnread() },
                onArchive = { viewModel.archiveSelected() },
                onDelete = { viewModel.deleteSelected() },
                onCancel = { viewModel.toggleSelectionMode() },
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun EmptyAccounts(onAddAccount: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.Email, contentDescription = null, tint = MailBlue, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("添加邮箱账户", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "登录 iCloud、Gmail、Outlook、QQ、163 等邮箱，开始收发邮件",
                color = MailTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAddAccount,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加账户", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MailBlue)
            Spacer(Modifier.height(12.dp))
            Text("正在同步邮件…", color = MailTextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyMailState(onAddAccount: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Inbox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("此分类暂无邮件", color = Color.Gray, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAddAccount) { Text("添加更多账户") }
        }
    }
}

@Composable
private fun BottomToolbar(
    onCompose: () -> Unit,
    onRefresh: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    LiquidGlassContainer(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        hazeState = hazeState
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(icon = Icons.Filled.Refresh, onClick = onRefresh, tint = Color.Gray)
                GlassIconButton(icon = Icons.Outlined.Flag, onClick = {}, tint = Color.Gray)
                GlassIconButton(icon = Icons.Filled.Folder, onClick = {}, tint = Color.Gray)
            }
            GlassIconButton(icon = Icons.Filled.Edit, onClick = onCompose, tint = MailBlue, size = 48.dp)
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    LiquidGlassContainer(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        hazeState = hazeState
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("已选 $count 封", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassIconButton(icon = Icons.Filled.MarkEmailRead, onClick = onMarkRead, tint = MailBlue, size = 40.dp)
                GlassIconButton(icon = Icons.Filled.MarkEmailUnread, onClick = onMarkUnread, tint = Color.Gray, size = 40.dp)
                GlassIconButton(icon = Icons.Filled.Archive, onClick = onArchive, tint = Color.Gray, size = 40.dp)
                GlassIconButton(icon = Icons.Filled.Delete, onClick = onDelete, tint = Color(0xFFFF3B30), size = 40.dp)
            }
            GlassIconButton(icon = Icons.Filled.Close, onClick = onCancel, tint = Color.Gray, size = 40.dp)
        }
    }
}
