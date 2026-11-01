package com.apple.mail.data

import androidx.compose.ui.graphics.Color

/**
 * iOS 26 Mail 四大分类，对应 Apple Mail 的 Primary / Transactions / Updates / Promotions。
 */
enum class MailCategory(
    val title: String,
    val icon: String,
    val accent: Color,
    val subtitle: String
) {
    PRIMARY("主要", "person.crop.circle", Color(0xFF007AFF), "个人与重要邮件"),
    TRANSACTIONS("交易", "creditcard", Color(0xFF34C759), "收据、订单、物流"),
    UPDATES("更新", "bell.badge", Color(0xFFAF52DE), "新闻、订阅、社交"),
    PROMOTIONS("推广", "tag", Color(0xFFFF9500), "营销、优惠、促销");

    companion object {
        fun fromTitle(title: String): MailCategory = entries.firstOrNull { it.title == title } ?: PRIMARY
    }
}

/** 邮件优先级：普通 / 优先（Apple Intelligence Priority Message） */
enum class MailPriority { NORMAL, PRIORITY }

/**
 * 一封邮件（真实来自 IMAP 服务器）。
 */
data class EmailMessage(
    val id: Long,
    /** 所属账户 id */
    val accountId: String = "",
    /** IMAP UID，用于回写已读/标记等操作 */
    val uid: Long = -1L,
    val senderName: String,
    val senderEmail: String,
    val recipients: String = "",
    val subject: String,
    val preview: String,
    val body: String,
    val bodyHtml: String = "",
    val timestamp: Long,
    val category: MailCategory,
    val isRead: Boolean = false,
    val isFlagged: Boolean = false,
    val hasAttachment: Boolean = false,
    val attachmentName: String? = null,
    val attachmentNames: List<String> = emptyList(),
    val priority: MailPriority = MailPriority.NORMAL,
    val avatarColorIndex: Int = 0
) {
    val displayTime: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000L -> "现在"
                diff < 3_600_000L -> "${diff / 60_000L}分钟前"
                diff < 86_400_000L -> "${diff / 3_600_000L}小时前"
                diff < 7 * 86_400_000L -> "${diff / 86_400_000L}天前"
                else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(timestamp))
            }
        }
}

/**
 * 邮箱账户（真实登录凭证 + 服务器配置）。
 */
data class MailAccount(
    val id: String,
    val displayName: String,
    val email: String,
    /** 授权码 / 应用专用密码（不是网页登录密码） */
    val password: String,
    val providerId: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSsl: Boolean,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSsl: Boolean,
    val colorIndex: Int,
    /** 服务器要求 STARTTLS 而非直接 SSL（如 Outlook 587 端口） */
    val smtpStartTls: Boolean = false,
    // OAuth（XOAUTH2）
    val authType: String = "password",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val tokenEndpoint: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scope: String = ""
)

/** 邮件文件夹 / 智能邮箱 */
data class MailFolder(
    val name: String,
    val icon: String,
    val unreadCount: Int = 0
)
