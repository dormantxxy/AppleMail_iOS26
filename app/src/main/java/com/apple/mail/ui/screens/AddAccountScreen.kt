package com.apple.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.mail.data.MailAccount
import com.apple.mail.data.MailProvider
import com.apple.mail.data.ProviderPreset
import com.apple.mail.ui.components.GlassIconButton
import com.apple.mail.ui.components.LiquidGlassContainer
import com.apple.mail.ui.components.OAuthDialog
import com.apple.mail.oauth.OAuthManager
import com.apple.mail.oauth.OAuthProviders
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

private enum class AddStep { PROVIDER, CREDENTIALS, SYNC_OPTIONS }

/**
 * iOS 26 Mail「添加账户」登录流程：
 * 选择服务商 → 输入邮箱与密码/授权码 → 验证连接 → 选择同步内容 → 保存。
 */
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    onVerify: (MailAccount, (Boolean, String?) -> Unit) -> Unit,
    onSave: (MailAccount) -> Unit
) {
    var step by remember { mutableStateOf(AddStep.PROVIDER) }
    var selectedProvider by remember { mutableStateOf<ProviderPreset?>(null) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var showOAuth by remember { mutableStateOf(false) }
    var oauthToken by remember { mutableStateOf<OAuthManager.TokenSet?>(null) }

    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var imapSsl by remember { mutableStateOf(true) }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var smtpSsl by remember { mutableStateOf(true) }
    var smtpStartTls by remember { mutableStateOf(false) }

    var syncMail by remember { mutableStateOf(true) }
    var syncContacts by remember { mutableStateOf(false) }
    var syncCalendars by remember { mutableStateOf(false) }
    var syncNotes by remember { mutableStateOf(false) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    fun buildAccount(token: OAuthManager.TokenSet? = oauthToken): MailAccount {
        val p = selectedProvider ?: MailProvider.custom
        val oauthCfg = OAuthProviders.configFor(p.id)
        return MailAccount(
            id = email.trim().lowercase(),
            displayName = displayName.ifBlank { email.substringBefore('@') },
            email = email.trim(),
            password = if (token != null) "" else password,
            providerId = p.id,
            imapHost = imapHost.ifBlank { p.imapHost },
            imapPort = imapPort.toIntOrNull() ?: p.imapPort,
            imapSsl = imapSsl,
            smtpHost = smtpHost.ifBlank { p.smtpHost },
            smtpPort = smtpPort.toIntOrNull() ?: p.smtpPort,
            smtpSsl = smtpSsl,
            smtpStartTls = smtpStartTls,
            colorIndex = (email.hashCode() and 0x7fffffff) % 8,
            authType = if (token != null) "oauth" else "password",
            accessToken = token?.accessToken ?: "",
            refreshToken = token?.refreshToken ?: "",
            expiresAt = token?.expiresAt ?: 0L,
            tokenEndpoint = oauthCfg?.tokenEndpoint ?: "",
            clientId = oauthCfg?.clientId ?: "",
            clientSecret = oauthCfg?.clientSecret ?: "",
            scope = oauthCfg?.scope ?: ""
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF000000) else Color.White)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        when (step) {
                            AddStep.PROVIDER -> onBack()
                            AddStep.CREDENTIALS -> { step = AddStep.PROVIDER; errorMsg = null }
                            AddStep.SYNC_OPTIONS -> { step = AddStep.CREDENTIALS; errorMsg = null }
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MailBlue)
                }
                Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (step) {
                            AddStep.PROVIDER -> "添加账户"
                            AddStep.CREDENTIALS -> "登录邮箱"
                            AddStep.SYNC_OPTIONS -> "同步选项"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = when (step) {
                            AddStep.PROVIDER -> "选择你的邮箱服务商"
                            AddStep.CREDENTIALS -> selectedProvider?.displayName ?: ""
                            AddStep.SYNC_OPTIONS -> "选择要同步的内容"
                        },
                        fontSize = 12.sp,
                        color = MailTextSecondary
                    )
                }
            }
            HorizontalDivider(
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                thickness = 0.5.dp
            )

            when (step) {
                AddStep.PROVIDER -> ProviderStep(
                    onSelect = { p ->
                        selectedProvider = p
                        imapHost = p.imapHost
                        imapPort = p.imapPort.toString()
                        imapSsl = p.imapSsl
                        smtpHost = p.smtpHost
                        smtpPort = p.smtpPort.toString()
                        smtpSsl = p.smtpSsl
                        smtpStartTls = p.smtpStartTls
                        oauthToken = null
                        step = AddStep.CREDENTIALS
                        errorMsg = null
                    }
                )

                AddStep.CREDENTIALS -> CredentialsStep(
                    provider = selectedProvider,
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    showPassword = showPassword,
                    onToggleShowPassword = { showPassword = !showPassword },
                    isCustom = selectedProvider?.id == "custom",
                    imapHost = imapHost, onImapHost = { imapHost = it },
                    imapPort = imapPort, onImapPort = { imapPort = it },
                    imapSsl = imapSsl, onImapSsl = { imapSsl = it },
                    smtpHost = smtpHost, onSmtpHost = { smtpHost = it },
                    smtpPort = smtpPort, onSmtpPort = { smtpPort = it },
                    smtpSsl = smtpSsl, onSmtpSsl = { smtpSsl = it },
                    smtpStartTls = smtpStartTls, onSmtpStartTls = { smtpStartTls = it },
                    oauthAvailable = selectedProvider?.let { OAuthProviders.isConfigured(it.id) } == true,
                    onOAuthClick = {
                        if (email.isBlank()) {
                            errorMsg = "请先填写邮箱地址"
                        } else {
                            errorMsg = null
                            showOAuth = true
                        }
                    },
                    errorMsg = errorMsg,
                    verifying = verifying,
                    onNext = {
                        val p = selectedProvider
                        when {
                            p == null -> errorMsg = "请选择服务商"
                            email.isBlank() -> errorMsg = "请输入邮箱地址"
                            oauthToken == null && password.isBlank() -> errorMsg = "请输入密码/授权码，或使用 OAuth 登录"
                            p.id == "custom" && (imapHost.isBlank() || smtpHost.isBlank()) ->
                                errorMsg = "请填写 IMAP 与 SMTP 服务器地址"
                            else -> {
                                errorMsg = null
                                verifying = true
                                onVerify(buildAccount()) { ok, msg ->
                                    verifying = false
                                    if (ok) step = AddStep.SYNC_OPTIONS else errorMsg = msg
                                }
                            }
                        }
                    }
                )

                AddStep.SYNC_OPTIONS -> SyncOptionsStep(
                    email = email,
                    syncMail = syncMail, onSyncMail = { syncMail = it },
                    syncContacts = syncContacts, onSyncContacts = { syncContacts = it },
                    syncCalendars = syncCalendars, onSyncCalendars = { syncCalendars = it },
                    syncNotes = syncNotes, onSyncNotes = { syncNotes = it },
                    onDone = {
                        onSave(buildAccount())
                        onBack()
                    }
                )
            }

            if (showOAuth) {
                val cfg = selectedProvider?.let { OAuthProviders.configFor(it.id) }
                if (cfg != null) {
                    OAuthDialog(
                        config = cfg,
                        onCancel = { showOAuth = false },
                        onResult = { ts ->
                            showOAuth = false
                            if (ts == null) {
                                errorMsg = "OAuth 授权失败，请重试"
                            } else {
                                oauthToken = ts
                                errorMsg = null
                                verifying = true
                                onVerify(buildAccount(ts)) { ok, msg ->
                                    verifying = false
                                    if (ok) step = AddStep.SYNC_OPTIONS else errorMsg = msg
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ---------------- 步骤 1：选择服务商 ----------------

@Composable
private fun ProviderStep(onSelect: (ProviderPreset) -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val surface = if (isDark) Color(0xFF1C1C1E) else Color.White
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(bg),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Text("邮件", fontSize = 13.sp, color = MailTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().background(surface)) {
                MailProvider.presets.forEach { p -> ProviderRow(p, onSelect) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text("其他", fontSize = 13.sp, color = MailTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().background(surface)) {
                ProviderRow(MailProvider.custom, onSelect)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Text(
                text = "提示：Gmail / iCloud / Yahoo 需使用「应用专用密码」；QQ / 163 / 126 需先在网页端开启 IMAP/SMTP 并获取「授权码」。账号密码仅保存在本机。",
                fontSize = 12.sp, color = MailTextSecondary, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ProviderRow(p: ProviderPreset, onSelect: (ProviderPreset) -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF1C1C1E) else Color.White
    val divider = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Column(Modifier.fillMaxWidth().background(surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(p) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color(p.accentHex), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (p.displayName.firstOrNull() ?: '?').toString(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(p.displayName, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MailTextSecondary.copy(alpha = 0.6f)
            )
        }
        HorizontalDivider(color = divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
    }
}

// ---------------- 步骤 2：凭据输入 ----------------

@Composable
private fun CredentialsStep(
    provider: ProviderPreset?,
    displayName: String, onDisplayNameChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    showPassword: Boolean, onToggleShowPassword: () -> Unit,
    isCustom: Boolean,
    imapHost: String, onImapHost: (String) -> Unit,
    imapPort: String, onImapPort: (String) -> Unit,
    imapSsl: Boolean, onImapSsl: (Boolean) -> Unit,
    smtpHost: String, onSmtpHost: (String) -> Unit,
    smtpPort: String, onSmtpPort: (String) -> Unit,
    smtpSsl: Boolean, onSmtpSsl: (Boolean) -> Unit,
    smtpStartTls: Boolean, onSmtpStartTls: (Boolean) -> Unit,
    oauthAvailable: Boolean,
    onOAuthClick: () -> Unit,
    errorMsg: String?,
    verifying: Boolean,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("姓名", fontSize = 13.sp, color = MailTextSecondary)
        OutlinedTextField(
            value = displayName, onValueChange = onDisplayNameChange,
            placeholder = { Text("你的姓名") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Text("电子邮件", fontSize = 13.sp, color = MailTextSecondary)
        OutlinedTextField(
            value = email, onValueChange = onEmailChange,
            placeholder = { Text("name@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Text("密码 / 授权码", fontSize = 13.sp, color = MailTextSecondary)
        OutlinedTextField(
            value = password, onValueChange = onPasswordChange,
            placeholder = { Text("请输入密码或授权码") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShowPassword) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "显示/隐藏密码"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        provider?.let {
            Text(it.passwordHint, fontSize = 12.sp, color = MailTextSecondary, lineHeight = 17.sp)
        }

        if (oauthAvailable) {
            OutlinedButton(
                onClick = onOAuthClick,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("使用 OAuth 登录（Gmail / Outlook）", fontSize = 14.sp)
            }
        }

        if (isCustom) {
            Text("IMAP 服务器", fontSize = 13.sp, color = MailTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = imapHost, onValueChange = onImapHost,
                    placeholder = { Text("imap.example.com") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = imapPort, onValueChange = onImapPort,
                    placeholder = { Text("993") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(90.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = imapSsl, onCheckedChange = onImapSsl)
                Text("使用 SSL", fontSize = 14.sp)
            }

            Text("SMTP 服务器", fontSize = 13.sp, color = MailTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = smtpHost, onValueChange = onSmtpHost,
                    placeholder = { Text("smtp.example.com") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = smtpPort, onValueChange = onSmtpPort,
                    placeholder = { Text("465") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(90.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = smtpSsl, onCheckedChange = { onSmtpSsl(it); if (it) onSmtpStartTls(false) })
                Text("SSL", fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                Checkbox(checked = smtpStartTls, onCheckedChange = { onSmtpStartTls(it); if (it) onSmtpSsl(false) })
                Text("STARTTLS", fontSize = 14.sp)
            }
        }

        if (errorMsg != null) {
            Surface(
                color = Color(0xFFFF3B30).copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                        tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMsg, color = Color(0xFFFF3B30), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onNext,
            enabled = !verifying,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (verifying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在验证…")
            } else {
                Text("下一步", fontSize = 16.sp)
            }
        }
    }
}

// ---------------- 步骤 3：同步选项 ----------------

@Composable
private fun SyncOptionsStep(
    email: String,
    syncMail: Boolean, onSyncMail: (Boolean) -> Unit,
    syncContacts: Boolean, onSyncContacts: (Boolean) -> Unit,
    syncCalendars: Boolean, onSyncCalendars: (Boolean) -> Unit,
    syncNotes: Boolean, onSyncNotes: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("验证成功", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(email, fontSize = 13.sp, color = MailTextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("要同步哪些内容？", fontWeight = FontWeight.Medium, fontSize = 14.sp)

        SyncRow(Icons.Filled.Email, "邮件", syncMail, onSyncMail)
        SyncRow(Icons.Filled.Person, "通讯录", syncContacts, onSyncContacts)
        SyncRow(Icons.Filled.DateRange, "日历", syncCalendars, onSyncCalendars)
        SyncRow(Icons.Filled.Description, "备忘录", syncNotes, onSyncNotes)

        Text(
            "说明：本应用目前实现「邮件」同步；通讯录/日历/备忘录为界面占位。",
            fontSize = 11.sp, color = MailTextSecondary, lineHeight = 16.sp
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("保存", fontSize = 16.sp)
        }
    }
}

@Composable
private fun SyncRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MailBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}
