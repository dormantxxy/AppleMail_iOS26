package com.apple.mail.data

import android.content.Context
import com.apple.mail.data.db.CachedEmailEntity
import com.apple.mail.data.db.FolderMetaEntity
import com.apple.mail.data.db.MailDatabase

/**
 * 本地邮件缓存（Room 实现，对外 API 保持不变）：
 * - 按「账户 + 文件夹」持久化邮件与 UID 水位（maxUid / uidValidity）
 * - 下次启动先读缓存立即展示，再增量拉取新邮件
 */
object MailCacheStore {

    const val MAX_CACHE = 120

    /** 超过该长度的 HTML 不再入库，避免数据库膨胀（回退为纯文本预览）。 */
    private const val MAX_HTML_CHARS = 200_000
    private const val ATT_SEP = "\u0001"

    data class CachedFolder(
        val uidValidity: Long,
        val maxUid: Long,
        val savedAt: Long,
        val emails: List<EmailMessage>
    )

    fun load(context: Context, accountId: String, folder: String): CachedFolder? {
        return try {
            val dao = MailDatabase.get(context).cacheDao()
            val meta = dao.meta(accountId, folder) ?: return null
            val rows = dao.emails(accountId, folder, MAX_CACHE)
            CachedFolder(
                uidValidity = meta.uidValidity,
                maxUid = meta.maxUid,
                savedAt = meta.savedAt,
                emails = rows.map { it.toEmail() }
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun save(
        context: Context,
        accountId: String,
        folder: String,
        uidValidity: Long,
        maxUid: Long,
        emails: List<EmailMessage>
    ) {
        try {
            val db = MailDatabase.get(context)
            val dao = db.cacheDao()
            val rows = emails.take(MAX_CACHE).map { it.toEntity(accountId, folder) }
            run {
                dao.deleteEmails(accountId, folder)
                dao.insertEmails(rows)
                dao.upsertMeta(
                    FolderMetaEntity(
                        accountId = accountId,
                        folder = folder,
                        uidValidity = uidValidity,
                        maxUid = maxUid,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }

    fun clearAccount(context: Context, accountId: String) {
        try {
            val db = MailDatabase.get(context)
            val dao = db.cacheDao()
            run {
                dao.deleteAccountEmails(accountId)
                dao.deleteAccountMeta(accountId)
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------- 映射 ----------------

    private fun CachedEmailEntity.toEmail(): EmailMessage {
        val atts = if (attachmentNames.isBlank()) {
            emptyList()
        } else {
            attachmentNames.split(ATT_SEP).filter { it.isNotBlank() }
        }
        val category = MailCategory.entries.firstOrNull { it.name == this.category }
            ?: MailCategory.PRIMARY
        val priority = if (this.priority == MailPriority.PRIORITY.name) {
            MailPriority.PRIORITY
        } else {
            MailPriority.NORMAL
        }
        return EmailMessage(
            id = emailId,
            accountId = accountId,
            uid = uid,
            senderName = senderName,
            senderEmail = senderEmail,
            recipients = recipients,
            subject = subject,
            preview = preview,
            body = body,
            bodyHtml = bodyHtml,
            timestamp = timestamp,
            category = category,
            isRead = isRead,
            isFlagged = isFlagged,
            hasAttachment = hasAttachment,
            attachmentName = attachmentName?.ifBlank { null },
            attachmentNames = atts,
            priority = priority,
            avatarColorIndex = avatarColorIndex
        )
    }

    private fun EmailMessage.toEntity(accountIdFilter: String, folder: String): CachedEmailEntity {
        val accId = if (accountId.isBlank()) accountIdFilter else accountId
        val html = if (bodyHtml.length > MAX_HTML_CHARS) "" else bodyHtml
        return CachedEmailEntity(
            accountId = accId,
            folder = folder,
            uidKey = if (uid >= 0) "u$uid" else "i$id",
            emailId = id,
            uid = uid,
            senderName = senderName,
            senderEmail = senderEmail,
            recipients = recipients,
            subject = subject,
            preview = preview,
            body = body,
            bodyHtml = html,
            timestamp = timestamp,
            category = category.name,
            isRead = isRead,
            isFlagged = isFlagged,
            hasAttachment = hasAttachment,
            attachmentName = attachmentName,
            attachmentNames = attachmentNames.joinToString(ATT_SEP),
            priority = priority.name,
            avatarColorIndex = avatarColorIndex
        )
    }
}
