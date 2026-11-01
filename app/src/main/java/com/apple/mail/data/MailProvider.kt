package com.apple.mail.data

/**
 * 主流邮箱服务商预设配置，对齐 iOS 26 Mail「添加账户」中的服务商列表。
 *
 * - iCloud：需 App 专用密码（appleid.apple.com 生成）
 * - Gmail：第三方客户端需「应用专用密码」（开启两步验证后生成）
 * - Outlook / Exchange：Microsoft 账户
 * - Yahoo：需生成应用密码
 * - QQ / 163 / 126：需在网页版开启 IMAP/SMTP 并生成「授权码」
 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val iconEmoji: String,
    val accentHex: Long,
    val imapHost: String,
    val imapPort: Int,
    val imapSsl: Boolean,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSsl: Boolean,
    val smtpStartTls: Boolean,
    val passwordHint: String,
    val requiresAuthCode: Boolean
)

object MailProvider {

    val presets: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "icloud",
            displayName = "iCloud",
            iconEmoji = "☁️",
            accentHex = 0xFF007AFF,
            imapHost = "imap.mail.me.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.mail.me.com",
            smtpPort = 587, smtpSsl = false, smtpStartTls = true,
            passwordHint = "请输入 App 专用密码（在 appleid.apple.com 生成）",
            requiresAuthCode = true
        ),
        ProviderPreset(
            id = "gmail",
            displayName = "Google",
            iconEmoji = "🔴",
            accentHex = 0xFFEA4335,
            imapHost = "imap.gmail.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.gmail.com",
            smtpPort = 465, smtpSsl = true, smtpStartTls = false,
            passwordHint = "请输入应用专用密码（开启两步验证后在 Google 账户生成）",
            requiresAuthCode = true
        ),
        ProviderPreset(
            id = "outlook",
            displayName = "Outlook",
            iconEmoji = "📧",
            accentHex = 0xFF0078D4,
            imapHost = "outlook.office365.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.office365.com",
            smtpPort = 587, smtpSsl = false, smtpStartTls = true,
            passwordHint = "请输入 Microsoft 账户密码或应用密码",
            requiresAuthCode = false
        ),
        ProviderPreset(
            id = "exchange",
            displayName = "Microsoft Exchange",
            iconEmoji = "🏢",
            accentHex = 0xFF0078D4,
            imapHost = "outlook.office365.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.office365.com",
            smtpPort = 587, smtpSsl = false, smtpStartTls = true,
            passwordHint = "请输入 Exchange 账户密码",
            requiresAuthCode = false
        ),
        ProviderPreset(
            id = "yahoo",
            displayName = "Yahoo",
            iconEmoji = "🟣",
            accentHex = 0xFF6001D2,
            imapHost = "imap.mail.yahoo.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.mail.yahoo.com",
            smtpPort = 465, smtpSsl = true, smtpStartTls = false,
            passwordHint = "请输入 Yahoo 应用密码",
            requiresAuthCode = true
        ),
        ProviderPreset(
            id = "qq",
            displayName = "QQ 邮箱",
            iconEmoji = "🐧",
            accentHex = 0xFF12B7F5,
            imapHost = "imap.qq.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.qq.com",
            smtpPort = 465, smtpSsl = true, smtpStartTls = false,
            passwordHint = "请先在 QQ 邮箱网页版开启 IMAP/SMTP，并获取「授权码」",
            requiresAuthCode = true
        ),
        ProviderPreset(
            id = "163",
            displayName = "网易 163 邮箱",
            iconEmoji = "🟢",
            accentHex = 0xFFC60000,
            imapHost = "imap.163.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.163.com",
            smtpPort = 465, smtpSsl = true, smtpStartTls = false,
            passwordHint = "请先在网易邮箱网页版开启 IMAP/SMTP，并获取「授权码」",
            requiresAuthCode = true
        ),
        ProviderPreset(
            id = "126",
            displayName = "网易 126 邮箱",
            iconEmoji = "🟢",
            accentHex = 0xFFC60000,
            imapHost = "imap.126.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.126.com",
            smtpPort = 465, smtpSsl = true, smtpStartTls = false,
            passwordHint = "请先在网易邮箱网页版开启 IMAP/SMTP，并获取「授权码」",
            requiresAuthCode = true
        ),
        ProviderPreset(
            id = "sina",
            displayName = "新浪邮箱",
            iconEmoji = "🔶",
            accentHex = 0xFFFF6600,
            imapHost = "imap.sina.com",
            imapPort = 993, imapSsl = true,
            smtpHost = "smtp.sina.com",
            smtpPort = 465, smtpSsl = true, smtpStartTls = false,
            passwordHint = "请输入新浪邮箱授权码",
            requiresAuthCode = true
        )
    )

    val custom = ProviderPreset(
        id = "custom",
        displayName = "其他邮件账户",
        iconEmoji = "⚙️",
        accentHex = 0xFF8E8E93,
        imapHost = "",
        imapPort = 993, imapSsl = true,
        smtpHost = "",
        smtpPort = 465, smtpSsl = true, smtpStartTls = false,
        passwordHint = "请输入服务器地址与端口",
        requiresAuthCode = false
    )

    fun byId(id: String): ProviderPreset =
        presets.firstOrNull { it.id == id } ?: custom

    fun detect(email: String): ProviderPreset? {
        val domain = email.substringAfter('@', "").lowercase()
        if (domain.isBlank()) return null
        return when {
            domain.endsWith("icloud.com") || domain.endsWith("me.com") || domain.endsWith("mac.com") -> byId("icloud")
            domain == "gmail.com" || domain == "googlemail.com" -> byId("gmail")
            domain.endsWith("outlook.com") || domain.endsWith("hotmail.com") || domain.endsWith("live.com") -> byId("outlook")
            domain.endsWith("yahoo.com") -> byId("yahoo")
            domain.endsWith("qq.com") || domain.endsWith("foxmail.com") -> byId("qq")
            domain.endsWith("163.com") -> byId("163")
            domain.endsWith("126.com") -> byId("126")
            domain.endsWith("sina.com") || domain.endsWith("sina.cn") -> byId("sina")
            else -> null
        }
    }
}
