package com.apple.mail.data

import com.apple.mail.oauth.OAuthManager
import com.apple.mail.oauth.OAuthProviders
import com.apple.mail.oauth.OAuthTokenCache
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Address
import javax.mail.Authenticator
import javax.mail.Flags
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.UIDFolder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.util.ByteArrayDataSource

data class MailAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)


enum class ServerFlag { SEEN, FLAGGED }

data class FolderFetch(
    val emails: List<EmailMessage>,
    val uidValidity: Long,
    val maxUid: Long
)

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

    /** 返回连接凭据：普通密码，或 OAuth access token（按需刷新）。 */
    private fun credential(account: MailAccount): String {
        if (account.authType != "oauth") return account.password
        val now = System.currentTimeMillis()
        val cached = OAuthTokenCache.get(account.id)
        if (cached != null && cached.expiresAt - 60_000L > now) return cached.accessToken
        if (account.accessToken.isNotBlank() && account.expiresAt - 60_000L > now) {
            OAuthTokenCache.put(
                account.id,
                OAuthManager.TokenSet(account.accessToken, account.refreshToken, account.expiresAt)
            )
            return account.accessToken
        }
        if (account.refreshToken.isNotBlank() && account.tokenEndpoint.isNotBlank()) {
            val cfg = OAuthProviders.Config(
                authEndpoint = "",
                tokenEndpoint = account.tokenEndpoint,
                scope = account.scope,
                clientId = account.clientId,
                clientSecret = account.clientSecret,
                redirectUri = OAuthProviders.REDIRECT_URI
            )
            val ts = OAuthManager.refresh(cfg, account.refreshToken)
            if (ts != null) {
                OAuthTokenCache.put(account.id, ts)
                return ts.accessToken
            }
        }
        return account.accessToken
    }

    private fun connectOnce(account: MailAccount, extra: Map<String, String>): Store {
        val props = imapProperties(account)
        if (account.authType == "oauth") {
            props["mail.imap.auth.mechanisms"] = "XOAUTH2"
        }
        for ((k, v) in extra) props[k] = v
        val session = Session.getInstance(props)
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.email, credential(account))
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

    private fun openStore(account: MailAccount): Store {
        if (account.authType == "oauth") {
            return connectOnce(account, emptyMap())
        }
        var last: Throwable? = null
        try {
            return connectOnce(account, emptyMap())
        } catch (e: Throwable) {
            last = e
        }
        // 认证机制兜底：部分服务商 AUTHENTICATE 不兼容时切换到 LOGIN/PLAIN
        val fallbacks = listOf(
            mapOf("mail.imap.auth.login.disable" to "true"),
            mapOf("mail.imap.auth.plain.disable" to "true"),
            mapOf("mail.imap.auth.login.disable" to "true", "mail.imap.auth.plain.disable" to "true")
        )
        for (extra in fallbacks) {
            try {
                return connectOnce(account, extra)
            } catch (e: Throwable) {
                last = e
            }
        }
        throw (last ?: IllegalStateException("无法连接邮件服务器"))
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

                            try {
                                val fp = FetchProfile()
                                fp.add(UIDFolder.FetchProfileItem.UID)
                                folder.fetch(messages, fp)
                            } catch (_: Throwable) {
                            }
                            for (i in messages.indices.reversed()) {
                                try {
                                    result.add(parseMessage(account, messages[i], folder))
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

    /** 增量拉取：优先按 UID 水位拉新邮件；UIDVALIDITY 变化或无水位时回退全量最近邮件。 */
    suspend fun fetchFolderIncremental(
        account: MailAccount,
        folderName: String,
        sinceUid: Long,
        sinceValidity: Long
    ): Result<FolderFetch> = withContext(Dispatchers.IO) {
        runCatching {
            val store = openStore(account)
            try {
                val folder = resolveTarget(store, folderName, writable = false)
                try {
                    val uidFolder = folder as? UIDFolder
                    if (uidFolder == null) {
                        val recent = fetchRecentInFolder(account, folder)
                        return@runCatching FolderFetch(
                            recent,
                            -1L,
                            recent.maxOfOrNull { it.uid } ?: 0L
                        )
                    }
                    val validity = try { uidFolder.uidValidity } catch (_: Throwable) { -1L }
                    val needFull = sinceUid <= 0L ||
                        (sinceValidity > 0L && validity > 0L && validity != sinceValidity)
                    if (needFull) {
                        val recent = fetchRecentInFolder(account, folder)
                        FolderFetch(
                            recent,
                            validity,
                            recent.maxOfOrNull { it.uid } ?: 0L
                        )
                    } else {
                        val msgs = uidFolder.getMessagesByUID(sinceUid + 1, UIDFolder.LASTUID)
                        if (msgs.isEmpty()) {
                            FolderFetch(emptyList(), validity, sinceUid)
                        } else {
                            try {
                                val fp = FetchProfile()
                                fp.add(UIDFolder.FetchProfileItem.UID)
                                folder.fetch(msgs, fp)
                            } catch (_: Throwable) {
                            }
                            val list = mutableListOf<EmailMessage>()
                            for (i in msgs.indices.reversed()) {
                                try {
                                    list.add(parseMessage(account, msgs[i], folder))
                                } catch (_: Throwable) {
                                }
                            }
                            // 规避 IMAP `UID n:*` 在无新邮件时返回最后一条的怪癖
                            list.removeAll { it.uid >= 0 && it.uid <= sinceUid }
                            val maxUid = maxOf(
                                sinceUid,
                                list.maxOfOrNull { it.uid } ?: sinceUid
                            )
                            FolderFetch(list, validity, maxUid)
                        }
                    }
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    /** 拉取比 beforeUid 更旧的一页（UID 窗口，避免全量扫描）。 */
    suspend fun fetchOlder(
        account: MailAccount,
        folderName: String,
        beforeUid: Long,
        limit: Int = 30
    ): Result<List<EmailMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            if (beforeUid <= 1L) return@runCatching emptyList()
            val store = openStore(account)
            try {
                val folder = resolveTarget(store, folderName, writable = false)
                try {
                    val uidFolder = folder as? UIDFolder ?: return@runCatching emptyList()
                    val start = maxOf(1L, beforeUid - 500L)
                    val end = beforeUid - 1L
                    if (end < start) return@runCatching emptyList()
                    val msgs = uidFolder.getMessagesByUID(start, end)
                    if (msgs.isEmpty()) return@runCatching emptyList()
                    try {
                        val fp = FetchProfile()
                        fp.add(UIDFolder.FetchProfileItem.UID)
                        folder.fetch(msgs, fp)
                    } catch (_: Throwable) {
                    }
                    val list = mutableListOf<EmailMessage>()
                    for (m in msgs) {
                        try {
                            val e = parseMessage(account, m, folder)
                            if (e.uid in start..end) list.add(e)
                        } catch (_: Throwable) {
                        }
                    }
                    list.sortedByDescending { it.uid }.take(limit)
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    private fun fetchRecentInFolder(account: MailAccount, folder: Folder): List<EmailMessage> {
        val list = mutableListOf<EmailMessage>()
        val total = folder.messageCount
        if (total > 0) {
            val start = maxOf(1, total - FETCH_LIMIT + 1)
            val messages = folder.getMessages(start, total)
            try {
                val fp = FetchProfile()
                fp.add(UIDFolder.FetchProfileItem.UID)
                folder.fetch(messages, fp)
            } catch (_: Throwable) {
            }
            for (i in messages.indices.reversed()) {
                try {
                    list.add(parseMessage(account, messages[i], folder))
                } catch (_: Throwable) {
                }
            }
        }
        return list
    }

    private fun parseMessage(account: MailAccount, msg: Message, folder: Folder): EmailMessage {
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

        val content = extractBody(msg)
        val attachmentNames = collectAttachmentNames(msg)
        val preview = content.text.replace(Regex("\\s+"), " ").trim().take(120)
        val category = categorize(senderName, senderEmail, subject, preview)

        val uid = try {
            (folder as? UIDFolder)?.getUID(msg) ?: -1L
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
            body = content.text,
            bodyHtml = content.html,
            timestamp = date,
            category = category,
            isRead = seen,
            isFlagged = flagged,
            hasAttachment = content.hasAttachment || attachmentNames.isNotEmpty(),
            attachmentName = content.attachmentName ?: attachmentNames.firstOrNull(),
            attachmentNames = attachmentNames,
            avatarColorIndex = account.colorIndex
        )
    }

    private data class BodyContent(
        val text: String,
        val html: String,
        val hasAttachment: Boolean,
        val attachmentName: String?
    )

    private fun extractBody(part: Part): BodyContent {
        var hasAttachment = false
        var attachmentName: String? = null
        val text = StringBuilder()
        var html: String? = null
        val inlineImages = HashMap<String, Pair<String, ByteArray>>()
        var inlineBytes = 0
        val maxInline = 800 * 1024
        val maxPerImage = 300 * 1024

        fun normCid(raw: String): String =
            raw.trim().removePrefix("<").removeSuffix(">").trim()

        fun walk(p: Part) {
            val disposition = try { p.disposition } catch (_: Throwable) { null }
            val fileName = try { p.fileName } catch (_: Throwable) { null }
            val contentId = try {
                p.getHeader("Content-ID")?.firstOrNull()?.toString()
            } catch (_: Throwable) { null }

            // 内嵌图片（cid:xxx）
            if (contentId != null &&
                !p.isMimeType("multipart/*") &&
                !p.isMimeType("text/plain") &&
                !p.isMimeType("text/html") &&
                inlineBytes < maxInline
            ) {
                val isAttachmentDisp = disposition != null &&
                    disposition.equals(Part.ATTACHMENT, true)
                if (!isAttachmentDisp) {
                    try {
                        val stream = p.inputStream
                        if (stream != null) {
                            val bytes = stream.use { readLimited(it, maxPerImage) }
                            if (bytes.isNotEmpty() && inlineBytes + bytes.size <= maxInline) {
                                val mime = try {
                                    p.contentType ?: "image/png"
                                } catch (_: Throwable) { "image/png" }
                                inlineImages[normCid(contentId)] = mime to bytes
                                inlineBytes += bytes.size
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            }

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
                        if (html == null) html = p.content?.toString().orEmpty()
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

        val rawHtml = html.orEmpty()
        val plain = text.toString().trim().ifBlank {
            if (rawHtml.isNotBlank()) stripHtml(rawHtml) else "(此邮件无可读文本内容)"
        }
        var finalHtml = rawHtml
        if (finalHtml.isNotBlank() && inlineImages.isNotEmpty()) {
            try {
                val regex = Regex("cid:([^\"'\\s)>]+)", RegexOption.IGNORE_CASE)
                finalHtml = regex.replace(finalHtml) { m ->
                    val key = normCid(m.groupValues[1])
                    val img = inlineImages[key]
                    if (img == null) {
                        m.value
                    } else {
                        val b64 = android.util.Base64.encodeToString(img.second, android.util.Base64.NO_WRAP)
                        "data:${img.first};base64,$b64"
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return BodyContent(plain.take(20000), finalHtml, hasAttachment, attachmentName)
    }

    private fun collectAttachmentNames(part: Part): List<String> {
        val names = mutableListOf<String>()
        fun walk(p: Part) {
            val disposition = try { p.disposition } catch (_: Throwable) { null }
            val fileName = try { p.fileName } catch (_: Throwable) { null }
            val isAttachment = (disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)) ||
                (fileName != null && !p.isMimeType("text/plain") && !p.isMimeType("text/html") && !p.isMimeType("multipart/*"))
            if (isAttachment) {
                if (fileName != null && !names.contains(fileName)) names.add(fileName)
                return
            }
            try {
                if (p.isMimeType("multipart/*")) {
                    (p.content as? Multipart)?.let { mp ->
                        for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                    }
                }
            } catch (_: Throwable) {
            }
        }
        walk(part)
        return names
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

    /** 一次性把多个 UID 标记为已读（单连接，避免大量连接）。 */
    suspend fun markAllSeen(account: MailAccount, folderName: String, uids: List<Long>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uids.isEmpty()) return@runCatching
                val store = openStore(account)
                try {
                    val folder = resolveTarget(store, folderName, writable = true)
                    try {
                        val imapFolder = folder as? IMAPFolder ?: throw IllegalStateException("非 IMAP 文件夹")
                        val flags = Flags()
                        flags.add(Flags.Flag.SEEN)
                        val msgs = uids.mapNotNull { uid ->
                            try { imapFolder.getMessageByUID(uid) } catch (_: Throwable) { null }
                        }
                        if (msgs.isNotEmpty()) {
                            folder.setFlags(msgs.toTypedArray(), flags, true)
                        }
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
        downloadAttachmentAt(account, folderName, uid, 0)

    suspend fun downloadAttachmentAt(
        account: MailAccount, folderName: String, uid: Long, index: Int
    ): Result<MailAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uid < 0) throw IllegalStateException("缺少 UID")
                val store = openStore(account)
                try {
                    val folder = resolveTarget(store, folderName, writable = false)
                    try {
                        val imapFolder = folder as? IMAPFolder ?: throw IllegalStateException("非 IMAP 文件夹")
                        val msg = imapFolder.getMessageByUID(uid) ?: throw IllegalStateException("找不到邮件")
                        findAttachmentAt(msg, index) ?: throw IllegalStateException("没有可下载的附件")
                    } finally {
                        folder.close(false)
                    }
                } finally {
                    store.close()
                }
            }
        }

    private fun findAttachmentAt(part: Part, targetIndex: Int): MailAttachment? {
        var count = 0
        fun walk(p: Part): MailAttachment? {
            val disposition = try { p.disposition } catch (_: Throwable) { null }
            val fileName = try { p.fileName } catch (_: Throwable) { null }
            val isAttachment = (disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)) ||
                (fileName != null && !p.isMimeType("text/plain") && !p.isMimeType("text/html") && !p.isMimeType("multipart/*"))
            if (isAttachment) {
                if (count == targetIndex) {
                    val input = p.inputStream ?: return null
                    val bytes = input.use { readAll(it) }
                    return MailAttachment(
                        fileName = fileName ?: "attachment",
                        mimeType = try { p.contentType ?: "application/octet-stream" } catch (_: Throwable) { "application/octet-stream" },
                        bytes = bytes
                    )
                }
                count++
                return null
            }
            try {
                if (p.isMimeType("multipart/*")) {
                    val mp = p.content as? Multipart ?: return null
                    for (i in 0 until mp.count) {
                        walk(mp.getBodyPart(i))?.let { return it }
                    }
                }
            } catch (_: Throwable) {
            }
            return null
        }
        return walk(part)
    }

    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < max) {
            val n = input.read(buf)
            if (n <= 0) break
            val take = minOf(n, max - total)
            out.write(buf, 0, take)
            total += take
        }
        return out.toByteArray()
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
        if (account.authType == "oauth") {
            props["mail.smtp.auth.mechanisms"] = "XOAUTH2"
            props["mail.smtp.auth.login.disable"] = "true"
            props["mail.smtp.auth.plain.disable"] = "true"
        }
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "30000"
        props["mail.smtp.writetimeout"] = "30000"
        return props
    }

    suspend fun sendMail(
        account: MailAccount,
        to: String,
        cc: String,
        subject: String,
        body: String,
        attachments: List<MailAttachment> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val props = smtpProperties(account)
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(account.email, credential(account))
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
            if (attachments.isEmpty()) {
                message.setText(body, "UTF-8")
            } else {
                val multipart = MimeMultipart()
                val textPart = MimeBodyPart()
                textPart.setText(body, "UTF-8")
                multipart.addBodyPart(textPart)
                attachments.forEach { att ->
                    val part = MimeBodyPart()
                    try {
                        part.setFileName(MimeUtility.encodeText(att.fileName, "UTF-8", null))
                    } catch (_: Throwable) {
                        part.fileName = att.fileName
                    }
                    part.setContent(ByteArrayDataSource(att.bytes, att.mimeType))
                    part.disposition = Part.ATTACHMENT
                    multipart.addBodyPart(part)
                }
                message.setContent(multipart)
            }
            Transport.send(message)
        }
    }
}
