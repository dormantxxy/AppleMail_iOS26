package com.apple.mail.data

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Address
import javax.mail.Authenticator
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

data class MailAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

enum class ServerFlag { SEEN, FLAGGED }

/**
 * 真实邮件仓库（IMAP 收 / SMTP 发 / 服务器回写）。
 */
object MailRepository {

    private const val FETCH_LIMIT = 40

    private val TRASH_NAMES = listOf(
        "Trash", "Deleted Messages", "Deleted Items", "[Gmail]/Trash", "已删除邮件", "已删除", "废纸篓"
    )
    private val ARCHIVE_NAMES = listOf(
        "Archive", "Archives", "[Gmail]/All Mail", "归档"
    )
    private val SENT_NAMES = listOf(
        "Sent", "Sent Messages", "Sent Items", "[Gmail]/Sent Mail", "已发送", "已发送邮件"
    )
    private val DRAFT_NAMES = listOf(
        "Drafts", "[Gmail]/Drafts", "草稿", "草稿箱"
    )
    private val JUNK_NAMES = listOf(
        "Junk", "Spam", "[Gmail]/Spam", "垃圾邮件", "垃圾"
    )

    private fun candidatesFor(folderName: String): List<String> = when (folderName) {
        "Sent" -> SENT_NAMES
        "Drafts" -> DRAFT_NAMES
        "Junk" -> JUNK_NAMES
        "Trash" -> TRASH_NAMES
        "INBOX" -> listOf("INBOX")
        else -> listOf(folderName, "INBOX")
    }

    // ---------------- 属性 / 连接 ----------------

    private fun imapProperties(account: MailAccount): Properties {
        val props = Properties()
        props["mail.store.protocol"] = "imap"
        props["mail.imap.host"] = account.imapHost
        props["mail.imap.port"] = account.imapPort.toString()
        if (account.imapSsl) {
            props["mail.imap.ssl.enable"] = "true"
            props["mail.imap.ssl.trust"] = account.imapHost
        }
        props["mail.imap.connectiontimeout"] = "15000"
        props["mail.imap.timeout"] = "30000"
        props["mail.imap.writetimeout"] = "30000"
        if (account.providerId == "qq") {
            props["mail.imap.auth.login.disable"] = "true"
            props["mail.imap.auth.plain.disable"] = "true"
        }
        props["mail.mime.decodetext.strict"] = "false"
        props["mail.mime.charset"] = "UTF-8"
        return props
    }

    private fun openStore(account: MailAccount): Store {
        val session = Session.getInstance(imapProperties(account))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.email, account.password)
        try {
            if (store is IMAPStore) {
                store.id(
                    mapOf(
                        "name" to "AppleMail",
                        "version" to "1.0",
                        "vendor" to "Apple",
                        "support-email" to account.email
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return store
    }

    // ---------------- 验证登录 ----------------

    suspend fun verifyAccount(account: MailAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val store = openStore(account)
            try {
                val folder = store.getFolder("INBOX")
                if (!folder.exists()) throw IllegalStateException("收件箱不存在")
                folder.open(Folder.READ_ONLY)
                folder.close(false)
            } finally {
                store.close()
            }
        }
    }

    // ---------------- 文件夹定位 ----------------

    private fun findSpecialFolder(store: Store, candidates: List<String>): Folder? {
        for (name in candidates) {
            val f = try { store.getFolder(name) } catch (_: Throwable) { null }
            if (f != null && f.exists()) return f
        }
        return try {
            val all = store.defaultFolder?.list("*") ?: return null
            all.firstOrNull { f ->
                candidates.any { c ->
                    f.name.equals(c, true) || f.fullName.equals(c, true)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveTarget(store: Store, folderName: String, writable: Boolean): Folder {
        val candidates = candidatesFor(folderName)
        val direct = try { store.getFolder(folderName) } catch (_: Throwable) { null }
        val folder = if (direct != null && direct.exists()) {
            direct
        } else {
            findSpecialFolder(store, candidates) ?: store.getFolder("INBOX")
        }
        folder.open(if (writable) Folder.READ_WRITE else Folder.READ_ONLY)
        return folder
    }

    // ---------------- 拉取邮件 ----------------

    suspend fun fetchInbox(account: MailAccount): Result<List<EmailMessage>> =
        fetchFolder(account, "INBOX")

    suspend fun fetchFolder(account: MailAccount, folderName: String): Result<List<EmailMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = mutableListOf<EmailMessage>()
                val store = openStore(account)
                try {
                    val folder = resolveTarget(store, folderName, writable = false)
                    try {
                        val total = folder.messageCount
                        if (total > 0) {
                            val start = maxOf(1, total - FETCH_LIMIT + 1)
                            val messages = folder.getMessages(start, total)
                            for (i in messages.indices.reversed()) {
                                try {
                                    result.add(parseMessage(account, messages[i]))
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    } finally {
                        folder.close(false)
                    }
                } finally {
                    store.close()
                }
                result
            }
        }

    private fun parseMessage(account: MailAccount, msg: Message): EmailMessage {
        val subject = msg.subject?.toString()?.trim().orEmpty().ifBlank { "(无主题)" }
        val fromAddrs: Array<Address>? = msg.from
        val senderEmail = (fromAddrs?.firstOrNull() as? InternetAddress)?.address.orEmpty()
        val senderName = (fromAddrs?.firstOrNull() as? InternetAddress)?.personal
            ?: senderEmail.substringBefore('@').ifBlank { "未知发件人" }
        val recipients = try {
            msg.getRecipients(Message.RecipientType.TO)
                ?.filterIsInstance<InternetAddress>()
                ?.joinToString(", ") { it.address }
                .orEmpty()
        } catch (_: Throwable) { "" }
        val date = msg.receivedDate?.time ?: msg.sentDate?.time ?: System.currentTimeMillis()
        val flags = msg.flags
        val seen = flags?.contains(Flags.Flag.SEEN) ?: false
        val flagged = flags?.contains(Flags.Flag.FLAGGED) ?: false

        val (bodyText, hasAttach, attachName) = extractBody(msg)
        val preview = bodyText.replace(Regex("\\s+"), " ").trim().take(120)
        val category = categorize(senderName, senderEmail, subject, preview)

        val uid = try {
            when (msg) {
                is com.sun.mail.imap.IMAPMessage -> msg.getUid()
                else -> -1L
            }
        } catch (_: Throwable) { -1L }

        return EmailMessage(
            id = if (uid >= 0) (account.id.hashCode().toLong() shl 32) + uid else System.nanoTime(),
            accountId = account.id,
            uid = uid,
            senderName = senderName,
            senderEmail = senderEmail,
            recipients = recipients,
            subject = subject,
            preview = preview,
            body = bodyText,
            timestamp = date,
            category = category,
            isRead = seen,
            isFlagged = flagged,
            hasAttachment = hasAttach,
            attachmentName = attachName,
            avatarColorIndex = account.colorIndex
        )
    }

    private fun extractBody(part: Part): Triple<String, Boolean, String?> {
        var hasAttachment = false
        var attachmentName: String? = null
        val text = StringBuilder()

        fun walk(p: Part) {
            val disposition = try { p.disposition } catch (_: Throwable) { null }
            val fileName = try { p.fileName } catch (_: Throwable) { null }
            if (disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)) {
                hasAttachment = true
                if (attachmentName == null) attachmentName = fileName
                return
            }
            if (fileName != null && !p.isMimeType("text/plain") && !p.isMimeType("text/html")) {
                hasAttachment = true
                if (attachmentName == null) attachmentName = fileName
            }
            try {
                when {
                    p.isMimeType("text/plain") -> text.append(p.content?.toString().orEmpty()).append("\n")
                    p.isMimeType("text/html") -> {
                        if (text.isBlank()) text.append(stripHtml(p.content?.toString().orEmpty())).append("\n")
                    }
                    p.isMimeType("multipart/*") -> {
                        (p.content as? Multipart)?.let { mp ->
                            for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        walk(part)
        val clean = text.toString().trim().ifBlank { "(此邮件无可读文本内容)" }
        return Triple(clean.take(20000), hasAttachment, attachmentName)
    }

    private fun stripHtml(html: String): String {
        var s = html
        s = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</p>"), "\n\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = s.replace("&nbsp;", " ")
        s = s.replace("&amp;", "&")
        s = s.replace("&lt;", "<")
        s = s.replace("&gt;", ">")
        s = s.replace("&quot;", "\"")
        s = s.replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }

    fun categorize(senderName: String, senderEmail: String, subject: String, preview: String): MailCategory {
        val hay = "$senderName $senderEmail $subject $preview".lowercase()
        val transactions = listOf(
            "收据", "发票", "订单", "发货", "快递", "物流", "支付", "账单", "退款", "出票", "行程",
            "order", "receipt", "invoice", "shipment", "shipping", "payment", "delivery", "confirmation", "booking", "ticket"
        )
        val updates = listOf(
            "新闻", "日报", "周报", "订阅", "通讯", "通知", "社交通知", "关注", "动态", "日历", "提醒",
            "newsletter", "update", "notification", "digest", "github", "linkedin", "twitter", "facebook", "news"
        )
        val promotions = listOf(
            "促销", "优惠", "折扣", "大促", "活动", "限时", "领券", "红包", "满减", "特惠", "会员",
            "sale", "promo", "discount", "coupon", "offer", "deal", "off", "save", "black friday"
        )
        return when {
            transactions.any { hay.contains(it) } -> MailCategory.TRANSACTIONS
            updates.any { hay.contains(it) } -> MailCategory.UPDATES
            promotions.any { hay.contains(it) } -> MailCategory.PROMOTIONS
            else -> MailCategory.PRIMARY
        }
    }

    // ---------------- 服务器回写：已读 / 旗标 ----------------

    suspend fun setFlag(
        account: MailAccount, folderName: String, uid: Long, flag: ServerFlag, value: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (uid < 0) throw IllegalStateException("缺少 UID")
            val store = openStore(account)
            try {
                val folder = resolveTarget(store, folderName, writable = true)
                try {
                    val imapFolder = folder as? IMAPFolder ?: throw IllegalStateException("非 IMAP 文件夹")
                    val msg = imapFolder.getMessageByUID(uid) ?: throw IllegalStateException("找不到邮件")
                    val flags = Flags()
                    when (flag) {
                        ServerFlag.SEEN -> flags.add(Flags.Flag.SEEN)
                        ServerFlag.FLAGGED -> flags.add(Flags.Flag.FLAGGED)
                    }
                    msg.setFlags(flags, value)
                } finally {
                    folder.close(true)
                }
            } finally {
                store.close()
            }
        }
    }

    // ---------------- 服务器回写：删除 / 归档 ----------------

    suspend fun deleteMessage(account: MailAccount, folderName: String, uid: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uid < 0) throw IllegalStateException("缺少 UID")
                val store = openStore(account)
                try {
                    val folder = resolveTarget(store, folderName, writable = true)
                    try {
                        val imapFolder = folder as? IMAPFolder ?: throw IllegalStateException("非 IMAP 文件夹")
                        val msg = imapFolder.getMessageByUID(uid) ?: return@runCatching
                        val trash = findSpecialFolder(store, TRASH_NAMES)
                        if (trash != null && !trash.fullName.equals(folder.fullName, true)) {
                            try { folder.copyMessages(arrayOf(msg), trash) } catch (_: Throwable) {}
                        }
                        msg.setFlag(Flags.Flag.DELETED, true)
                        folder.expunge()
                    } finally {
                        folder.close(true)
                    }
                } finally {
                    store.close()
                }
            }
        }

    suspend fun archiveMessage(account: MailAccount, folderName: String, uid: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uid < 0) throw IllegalStateException("缺少 UID")
                val store = openStore(account)
                try {
                    val folder = resolveTarget(store, folderName, writable = true)
                    try {
                        val imapFolder = folder as? IMAPFolder ?: throw IllegalStateException("非 IMAP 文件夹")
                        val msg = imapFolder.getMessageByUID(uid) ?: return@runCatching
                        val archive = findSpecialFolder(store, ARCHIVE_NAMES)
                        if (archive != null && !archive.fullName.equals(folder.fullName, true)) {
                            try { folder.copyMessages(arrayOf(msg), archive) } catch (_: Throwable) {}
                        }
                        msg.setFlag(Flags.Flag.DELETED, true)
                        folder.expunge()
                    } finally {
                        folder.close(true)
                    }
                } finally {
                    store.close()
                }
            }
        }

    // ---------------- 附件下载 ----------------

    suspend fun downloadFirstAttachment(account: MailAccount, folderName: String, uid: Long): Result<MailAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uid < 0) throw IllegalStateException("缺少 UID")
                val store = openStore(account)
                try {
                    val folder = resolveTarget(store, folderName, writable = false)
                    try {
                        val imapFolder = folder as? IMAPFolder ?: throw IllegalStateException("非 IMAP 文件夹")
                        val msg = imapFolder.getMessageByUID(uid) ?: throw IllegalStateException("找不到邮件")
                        findFirstAttachment(msg) ?: throw IllegalStateException("没有可下载的附件")
                    } finally {
                        folder.close(false)
                    }
                } finally {
                    store.close()
                }
            }
        }

    private fun findFirstAttachment(part: Part): MailAttachment? {
        val disposition = try { part.disposition } catch (_: Throwable) { null }
        val fileName = try { part.fileName } catch (_: Throwable) { null }
        val isAttachment = (disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)) ||
            (fileName != null && !part.isMimeType("text/plain") && !part.isMimeType("text/html") && !part.isMimeType("multipart/*"))
        if (isAttachment) {
            val input = part.inputStream ?: return null
            val bytes = input.use { readAll(it) }
            return MailAttachment(
                fileName = fileName ?: "attachment",
                mimeType = try { part.contentType ?: "application/octet-stream" } catch (_: Throwable) { "application/octet-stream" },
                bytes = bytes
            )
        }
        try {
            if (part.isMimeType("multipart/*")) {
                val mp = part.content as? Multipart ?: return null
                for (i in 0 until mp.count) {
                    findFirstAttachment(mp.getBodyPart(i))?.let { return it }
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun readAll(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // ---------------- 发送邮件 ----------------

    private fun smtpProperties(account: MailAccount): Properties {
        val props = Properties()
        props["mail.transport.protocol"] = "smtp"
        props["mail.smtp.host"] = account.smtpHost
        props["mail.smtp.port"] = account.smtpPort.toString()
        props["mail.smtp.auth"] = "true"
        if (account.smtpSsl) {
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.ssl.trust"] = account.smtpHost
        }
        if (account.smtpStartTls) {
            props["mail.smtp.starttls.enable"] = "true"
            props["mail.smtp.ssl.trust"] = account.smtpHost
        }
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "30000"
        props["mail.smtp.writetimeout"] = "30000"
        return props
    }

    suspend fun sendMail(
        account: MailAccount, to: String, cc: String, subject: String, body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val props = smtpProperties(account)
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(account.email, account.password)
            })
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(account.email, account.displayName))
            val toList = to.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            if (toList.isEmpty()) throw IllegalArgumentException("收件人不能为空")
            message.setRecipients(Message.RecipientType.TO, toList.map { InternetAddress(it) }.toTypedArray())
            val ccList = cc.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            if (ccList.isNotEmpty()) {
                message.setRecipients(Message.RecipientType.CC, ccList.map { InternetAddress(it) }.toTypedArray())
            }
            message.subject = subject.ifBlank { "(无主题)" }
            message.setText(body, "UTF-8")
            Transport.send(message)
        }
    }
}
