package com.apple.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.MailViewModel
import com.apple.mail.ui.components.GlassIconButton
import com.apple.mail.ui.components.GlassPillButton
import com.apple.mail.ui.components.LiquidGlassContainer
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * iOS 26 Mail 撰写邮件界面（真实 SMTP 发送）。
 */
@Composable
fun ComposeScreen(
    viewModel: MailViewModel,
    onCancel: () -> Unit,
    onSent: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var showCcBcc by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(state.accounts.firstOrNull()?.id ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var showAccountPicker by remember { mutableStateOf(false) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val sending = state.isLoading

    fun doSend() {
        if (sending) return
        if (to.isBlank()) {
            error = "请输入收件人"
            return
        }
        error = null
        viewModel.sendMail(accountId, to, cc, subject, body) { ok, msg ->
            if (ok) onSent() else error = msg
        }
    }

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
                    GlassIconButton(icon = Icons.Filled.Close, onClick = onCancel, tint = Color.Gray, size = 40.dp)
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("新邮件", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val acc = state.accounts.firstOrNull { it.id == accountId }
                        Text(text = acc?.email ?: "未添加账户", fontSize = 11.sp, color = MailTextSecondary)
                    }
                    if (state.accounts.size > 1) {
                        GlassIconButton(
                            icon = Icons.Filled.SwapHoriz,
                            onClick = { showAccountPicker = !showAccountPicker },
                            tint = MailBlue,
                            size = 40.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    GlassPillButton(
                        text = if (sending) "发送中…" else "发送",
                        icon = Icons.AutoMirrored.Filled.Send,
                        onClick = { doSend() },
                        tint = MailBlue
                    )
                }
            }

            if (showAccountPicker && state.accounts.size > 1) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    state.accounts.forEach { acc ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = to, onValueChange = { to = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("收件人", color = MailTextSecondary) },
                    leadingIcon = { Text("收件人：", color = MailTextSecondary, fontSize = 13.sp) },
                    trailingIcon = {
                        IconButton(onClick = { showCcBcc = !showCcBcc }) {
                            Icon(Icons.Filled.Add, contentDescription = "抄送/密送", tint = MailBlue)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = transparentFieldColors()
                )

                if (showCcBcc) {
                    OutlinedTextField(
                        value = cc, onValueChange = { cc = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("抄送", color = MailTextSecondary) },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = transparentFieldColors()
                    )
                    OutlinedTextField(
                        value = bcc, onValueChange = { bcc = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("密送", color = MailTextSecondary) },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = transparentFieldColors()
                    )
                }

                OutlinedTextField(
                    value = subject, onValueChange = { subject = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("主题", color = MailTextSecondary) },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = transparentFieldColors()
                )

                HorizontalDivider(color = Color(0x22000000), thickness = 1.dp)

                TextField(
                    value = body, onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("正文内容…", color = MailTextSecondary) },
                    colors = transparentFieldColors()
                )

                if (error != null) {
                    Text(
                        error ?: "", color = Color(0xFFFF3B30), fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            LiquidGlassContainer(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                cornerRadius = 24.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GlassIconButton(icon = Icons.Filled.FormatBold, onClick = {}, tint = Color.Gray, size = 36.dp)
                    GlassIconButton(icon = Icons.Filled.FormatItalic, onClick = {}, tint = Color.Gray, size = 36.dp)
                    GlassIconButton(icon = Icons.Filled.FormatUnderlined, onClick = {}, tint = Color.Gray, size = 36.dp)
                    GlassIconButton(icon = Icons.Filled.AttachFile, onClick = {}, tint = Color.Gray, size = 36.dp)
                    GlassIconButton(icon = Icons.Filled.Image, onClick = {}, tint = Color.Gray, size = 36.dp)
                }
            }
        }
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)
