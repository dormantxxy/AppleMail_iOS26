package com.apple.mail.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.MailViewModel
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * 撰写邮件（iOS 26 Mail 风格：取消 / 发送 文本按钮 + 分组字段）。
 * 支持由「回复/转发」预填收件人、主题与正文。
 */
@Composable
fun ComposeScreen(
    viewModel: MailViewModel,
    onCancel: () -> Unit,
    onSent: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pending = remember { viewModel.pendingCompose }
    val savedDraft = remember { if (pending == null) com.apple.mail.data.DraftStore.load(context) else null }
    var to by remember { mutableStateOf(pending?.to ?: savedDraft?.to.orEmpty()) }
    var cc by remember { mutableStateOf(pending?.cc ?: savedDraft?.cc.orEmpty()) }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(pending?.subject ?: savedDraft?.subject.orEmpty()) }
    var body by remember { mutableStateOf(pending?.body ?: savedDraft?.body.orEmpty()) }
    var showCcBcc by remember { mutableStateOf(false) }
    var accountId by remember {
        mutableStateOf(
            savedDraft?.accountId?.takeIf { id -> state.accounts.any { it.id == id } }
                ?: state.accounts.firstOrNull()?.id
                ?: ""
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var sentSuccessfully by remember { mutableStateOf(false) }
    val pickedUris = remember { mutableStateListOf<Uri>() }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris?.forEach { u -> if (!pickedUris.contains(u)) pickedUris.add(u) }
    }

    fun attachmentName(uri: Uri): String {
        return try {
            var name = uri.lastPathSegment ?: "附件"
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { name = it }
                }
            }
            name
        } catch (_: Throwable) {
            "附件"
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF1C1C1E) else Color.White
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val divider = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val textColor = if (isDark) Color.White else Color.Black
    val sending = state.isLoading

    LaunchedEffect(to, cc, bcc, subject, body, accountId) {
        kotlinx.coroutines.delay(700)
        if (!sentSuccessfully) {
            com.apple.mail.data.DraftStore.save(
                context,
                com.apple.mail.data.DraftStore.Draft(to, cc, subject, body, accountId)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!sentSuccessfully) {
                com.apple.mail.data.DraftStore.save(
                    context,
                    com.apple.mail.data.DraftStore.Draft(to, cc, subject, body, accountId)
                )
            }
            viewModel.clearPendingCompose()
        }
    }

    fun cancel() {
        onCancel()
    }

    fun doSend() {
        if (sending) return
        if (to.isBlank()) {
            error = "请输入收件人"
            return
        }
        error = null
        viewModel.sendMail(accountId, to, cc, subject, body, pickedUris.toList()) { ok, msg ->
            if (ok) {
                sentSuccessfully = true
                com.apple.mail.data.DraftStore.clear(context)
                viewModel.clearPendingCompose()
                onSent()
            } else {
                error = msg
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // 导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { cancel() }) {
                Text("取消", color = MailBlue, fontSize = 16.sp)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("新邮件", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val acc = state.accounts.firstOrNull { it.id == accountId }
                Text(
                    text = acc?.email ?: "未添加账户",
                    fontSize = 11.sp,
                    color = MailTextSecondary
                )
            }
            TextButton(onClick = { doSend() }, enabled = !sending) {
                Text(
                    text = if (sending) "发送中…" else "发送",
                    color = if (sending) MailTextSecondary else MailBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
        HorizontalDivider(color = divider, thickness = 0.5.dp)

        if (state.accounts.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .clickable { showAccountPicker = !showAccountPicker }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("发件账户", color = MailTextSecondary, fontSize = 14.sp, modifier = Modifier.width(72.dp))
                Text(
                    text = state.accounts.firstOrNull { it.id == accountId }?.email ?: "",
                    fontSize = 14.sp,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.SwapHoriz, contentDescription = "切换账户", tint = MailBlue, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(color = divider, thickness = 0.5.dp)
            if (showAccountPicker) {
                Column(Modifier.fillMaxWidth().background(surface)) {
                    state.accounts.forEach { acc ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { accountId = acc.id; showAccountPicker = false }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = acc.id == accountId,
                                onClick = { accountId = acc.id; showAccountPicker = false }
                            )
                            Text("${acc.displayName} · ${acc.email}", fontSize = 14.sp)
                        }
                    }
                }
                HorizontalDivider(color = divider, thickness = 0.5.dp)
            }
        }

        // 字段区
        FieldRow("收件人", to, { to = it }, "name@example.com", KeyboardType.Email, surface, divider, textColor) {
            IconButton(onClick = { showCcBcc = !showCcBcc }) {
                Icon(Icons.Filled.Add, contentDescription = "抄送/密送", tint = MailBlue)
            }
        }
        if (showCcBcc) {
            FieldRow("抄送", cc, { cc = it }, "抄送", KeyboardType.Email, surface, divider, textColor)
            FieldRow("密送", bcc, { bcc = it }, "密送", KeyboardType.Email, surface, divider, textColor)
        }
        FieldRow("主题", subject, { subject = it }, "主题", KeyboardType.Text, surface, divider, textColor)

        if (pickedUris.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
            ) {
                pickedUris.toList().forEach { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = MailBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = attachmentName(uri),
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { pickedUris.remove(uri) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除附件",
                                tint = MailTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = divider, thickness = 0.5.dp)
        }

        // 正文
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                decorationBox = { inner ->
                    Box {
                        if (body.isEmpty()) {
                            Text("正文内容…", color = MailTextSecondary, fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )
        }

        if (error != null) {
            Text(
                text = error ?: "",
                color = Color(0xFFFF3B30),
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 格式栏
        HorizontalDivider(color = divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) { Icon(Icons.Filled.FormatBold, contentDescription = "加粗", tint = MailBlue) }
            IconButton(onClick = {}) { Icon(Icons.Filled.FormatItalic, contentDescription = "斜体", tint = MailBlue) }
            IconButton(onClick = {}) { Icon(Icons.Filled.FormatUnderlined, contentDescription = "下划线", tint = MailBlue) }
            IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.AttachFile, contentDescription = "附件", tint = MailBlue)
            }
            IconButton(onClick = {}) { Icon(Icons.Filled.Image, contentDescription = "图片", tint = MailBlue) }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    surface: Color,
    divider: Color,
    textColor: Color,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MailTextSecondary, fontSize = 15.sp, modifier = Modifier.width(64.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = textColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = MailTextSecondary.copy(alpha = 0.7f), fontSize = 16.sp)
                    }
                    inner()
                }
            }
        )
        if (trailing != null) trailing()
    }
    HorizontalDivider(color = divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
}
