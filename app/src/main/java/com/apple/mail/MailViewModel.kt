package com.apple.mail

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apple.mail.data.AccountStore
import com.apple.mail.data.EmailMessage
import com.apple.mail.data.MailAccount
import com.apple.mail.data.MailAttachment
import com.apple.mail.data.MailCacheStore
import com.apple.mail.data.MailCategory
import com.apple.mail.data.MailRepository
import com.apple.mail.data.ServerFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class MailUiState(
    val emails: List<EmailMessage> = emptyList(),
    val accounts: List<MailAccount> = emptyList(),
    val selectedCategory: MailCategory = MailCategory.PRIMARY,
    val query: String = "",
    val searchActive: Boolean = false,
    val selectedEmails: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val showSidebar: Boolean = false,
    val currentFolder: String = "收件箱",
    val showListMode: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncTime: Long = 0L,
    val toast: String? = null
)

data class ComposeDraft(
    val to: String = "",
    val cc: String = "",
    val subject: String = "",
    val body: String = ""
)

class MailViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Application = getApplication()

    private val _state = MutableStateFlow(MailUiState())
    val state: StateFlow<MailUiState> = _state.asStateFlow()

    val emails: List<EmailMessage> get() = _state.value.emails

    /** 撰写页草稿（回复/转发时预填） */
    var pendingCompose: ComposeDraft? = null

    init {
        val accounts = AccountStore.load(appContext)
        _state.value = _state.value.copy(accounts = accounts)
        if (accounts.isNotEmpty()) refreshAll()
        // 邮件变化后自动落盘（防抖），保证下次打开有内容
        viewModelScope.launch {
            var lastHash = Int.MIN_VALUE
            while (true) {
                delay(1500)
                val st = _state.value
                if (!st.isLoading && st.emails.isNotEmpty()) {
                    val h = st.emails.hashCode()
                    if (h != lastHash) {
                        lastHash = h
                        persistCurrentFolder()
                    }
                }
            }
        }
    }

    // ---------------- 账户 ----------------

    fun verifyAccount(account: MailAccount, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = MailRepository.verifyAccount(account)
            _state.value = _state.value.copy(isLoading = false)
            if (result.isSuccess) onResult(true, null)
            else {
                val msg = friendlyError(result.exceptionOrNull())
                _state.value = _state.value.copy(errorMessage = msg)
                onResult(false, msg)
            }
        }
    }

    fun saveAccount(account: MailAccount) {
        viewModelScope.launch {
            val accounts = _state.value.accounts.filterNot { it.id == account.id } + account
            withContext(Dispatchers.IO) { AccountStore.save(appContext, accounts) }
            _state.value = _state.value.copy(accounts = accounts, currentFolder = "收件箱")
            refreshAll()
        }
    }

    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            val accounts = _state.value.accounts.filterNot { it.id == accountId }
            withContext(Dispatchers.IO) { AccountStore.save(appContext, accounts) }
            withContext(Dispatchers.IO) { MailCacheStore.clearAccount(appContext, accountId) }
            val mails = _state.value.emails.filterNot { it.accountId == accountId }
            _state.value = _state.value.copy(accounts = accounts, emails = mails)
        }
    }

    // ---------------- 同步 ----------------

    fun refreshAll() = refreshCurrentFolder()

    fun refreshCurrentFolder() {
        val accounts = _state.value.accounts
        if (accounts.isEmpty()) return
        val folderName = serverFolderName(_state.value.currentFolder)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            // 1) 先展示本地缓存，避免白屏
            val cachedMap = withContext(Dispatchers.IO) {
                accounts.associate { it.id to MailCacheStore.load(appContext, it.id, folderName) }
            }
            val seeded = cachedMap.values.filterNotNull().flatMap { it.emails }
                .sortedByDescending { it.timestamp }
            if (seeded.isNotEmpty()) {
                _state.value = _state.value.copy(emails = seeded)
            }

            // 2) 增量同步（只拉新邮件）
            val finalList = mutableListOf<EmailMessage>()
            var lastError: String? = null
            accounts.forEach { acc ->
                val cached = cachedMap[acc.id]
                val res = MailRepository.fetchFolderIncremental(
                    acc,
                    folderName,
                    cached?.maxUid ?: 0L,
                    cached?.uidValidity ?: -1L
                )
                if (res.isSuccess) {
                    val fr = res.getOrThrow()
                    val validityChanged = fr.uidValidity > 0L &&
                        (cached?.uidValidity ?: -1L) > 0L &&
                        fr.uidValidity != cached?.uidValidity
                    val byKey = LinkedHashMap<String, EmailMessage>()
                    if (!validityChanged) {
                        cached?.emails?.forEach { byKey[emailKey(it)] = it }
                    }
                    fr.emails.forEach { byKey[emailKey(it)] = it }
                    val list = byKey.values.sortedByDescending { it.timestamp }
                        .take(MailCacheStore.MAX_CACHE)
                    finalList.addAll(list)
                    withContext(Dispatchers.IO) {
                        MailCacheStore.save(
                            appContext,
                            acc.id,
                            folderName,
                            fr.uidValidity,
                            fr.maxUid,
                            list
                        )
                    }
                } else {
                    cached?.emails?.let { finalList.addAll(it) }
                    lastError = "${acc.email}: ${friendlyError(res.exceptionOrNull())}"
                }
            }
            _state.value = _state.value.copy(
                emails = finalList.sortedByDescending { it.timestamp },
                isLoading = false,
                errorMessage = lastError,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    private fun emailKey(e: EmailMessage): String =
        if (e.uid >= 0) "${e.accountId}#${e.uid}" else "id#${e.id}"

    /** 把当前文件夹的邮件写入本地缓存（保留已有 UID 水位）。 */
    private fun persistCurrentFolder() {
        val st = _state.value
        if (st.accounts.isEmpty() || st.emails.isEmpty()) return
        val folderName = serverFolderName(st.currentFolder)
        val grouped = st.emails.groupBy { it.accountId }
        val accounts = st.accounts
        viewModelScope.launch(Dispatchers.IO) {
            accounts.forEach { acc ->
                val list = grouped[acc.id] ?: return@forEach
                val existing = MailCacheStore.load(appContext, acc.id, folderName)
                val maxUid = maxOf(existing?.maxUid ?: 0L, list.maxOfOrNull { it.uid } ?: 0L)
                MailCacheStore.save(
                    appContext,
                    acc.id,
                    folderName,
                    existing?.uidValidity ?: -1L,
                    maxUid,
                    list.sortedByDescending { it.timestamp }.take(MailCacheStore.MAX_CACHE)
                )
            }
        }
    }

    /** 本地文件夹名 → 服务器文件夹名 */
    private fun serverFolderName(local: String): String = when (local) {
        "已发送" -> "Sent"
        "草稿" -> "Drafts"
        "垃圾" -> "Junk"
        "废纸篓" -> "Trash"
        else -> "INBOX"
    }

    private fun accountOf(accountId: String): MailAccount? =
        _state.value.accounts.firstOrNull { it.id == accountId }

    /** 加载更早的一页邮件。 */
    fun loadMore() {
        val st = _state.value
        if (st.isLoading || st.isLoadingMore || st.accounts.isEmpty()) return
        val folder = serverFolderName(st.currentFolder)
        val mins = st.accounts.associate { acc ->
            val uid = st.emails.filter { it.accountId == acc.id && it.uid > 0 }
                .map { it.uid }
                .minOrNull() ?: 0L
            acc.id to uid
        }
        if (mins.values.none { it > 1L }) {
            _state.value = st.copy(toast = "没有更多邮件")
            return
        }
        _state.value = st.copy(isLoadingMore = true)
        viewModelScope.launch {
            val collected = mutableListOf<EmailMessage>()
            st.accounts.forEach { acc ->
                val before = mins[acc.id] ?: 0L
                if (before > 1L) {
                    val res = MailRepository.fetchOlder(acc, folder, before, 30)
                    res.getOrNull()?.let { collected.addAll(it) }
                }
            }
            val byKey = LinkedHashMap<String, EmailMessage>()
            _state.value.emails.forEach { byKey[emailKey(it)] = it }
            collected.forEach { byKey[emailKey(it)] = it }
            _state.value = _state.value.copy(
                emails = byKey.values.sortedByDescending { it.timestamp },
                isLoadingMore = false,
                toast = if (collected.isEmpty()) "没有更多邮件" else null
            )
        }
    }

    // ---------------- 分类 / 搜索 / 选择 ----------------

    fun selectCategory(category: MailCategory) {
        _state.value = _state.value.copy(selectedCategory = category)
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun toggleSearch() {
        val active = !_state.value.searchActive
        _state.value = _state.value.copy(searchActive = active, query = if (active) _state.value.query else "")
    }

    fun toggleSelectionMode() {
        _state.value = _state.value.copy(
            isSelectionMode = !_state.value.isSelectionMode,
            selectedEmails = emptySet()
        )
    }

    fun toggleSelectEmail(id: Long) {
        val selected = _state.value.selectedEmails.toMutableSet()
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        _state.value = _state.value.copy(selectedEmails = selected)
    }

    fun toggleSidebar() {
        _state.value = _state.value.copy(showSidebar = !_state.value.showSidebar)
    }

    fun closeSidebar() {
        _state.value = _state.value.copy(showSidebar = false)
    }

    fun setFolder(name: String) {
        _state.value = _state.value.copy(currentFolder = name, showSidebar = false)
        // 真实文件夹需要重新从服务器拉取
        when (name) {
            "已发送", "草稿", "垃圾", "废纸篓", "收件箱" -> refreshCurrentFolder()
            else -> { /* VIP / 有附件 / 未读 / 今天 仍基于已加载的收件箱做本地过滤 */ }
        }
    }

    fun toggleListMode() {
        _state.value = _state.value.copy(showListMode = !_state.value.showListMode)
    }

    fun consumeToast() {
        if (_state.value.toast != null) _state.value = _state.value.copy(toast = null)
    }

    // ---------------- 服务器回写：已读 / 旗标 ----------------

    private fun folderForEmail(e: EmailMessage): String =
        serverFolderName(_state.value.currentFolder)

    fun markAsRead(id: Long) {
        val email = _state.value.emails.firstOrNull { it.id == id } ?: return
        if (email.isRead) return
        _state.value = _state.value.copy(
            emails = _state.value.emails.map { if (it.id == id) it.copy(isRead = true) else it }
        )
        pushFlag(email, ServerFlag.SEEN, true)
    }

    fun markAsUnread(id: Long) {
        val email = _state.value.emails.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(
            emails = _state.value.emails.map { if (it.id == id) it.copy(isRead = false) else it }
        )
        pushFlag(email, ServerFlag.SEEN, false)
    }

    fun toggleFlag(id: Long) {
        val email = _state.value.emails.firstOrNull { it.id == id } ?: return
        val newValue = !email.isFlagged
        _state.value = _state.value.copy(
            emails = _state.value.emails.map { if (it.id == id) it.copy(isFlagged = newValue) else it }
        )
        pushFlag(email, ServerFlag.FLAGGED, newValue)
    }

    private fun pushFlag(email: EmailMessage, flag: ServerFlag, value: Boolean) {
        val account = accountOf(email.accountId) ?: return
        if (email.uid < 0) return
        val folder = folderForEmail(email)
        viewModelScope.launch {
            val res = MailRepository.setFlag(account, folder, email.uid, flag, value)
            if (res.isFailure) {
                _state.value = _state.value.copy(toast = "同步标记失败：${friendlyError(res.exceptionOrNull())}")
            }
        }
    }

    fun markAllRead() {
        val targets = _state.value.emails.filter { !it.isRead }
        if (targets.isEmpty()) return
        _state.value = _state.value.copy(emails = _state.value.emails.map { it.copy(isRead = true) })
        val folder = serverFolderName(_state.value.currentFolder)
        targets.groupBy { it.accountId }.forEach { (accId, list) ->
            val account = accountOf(accId) ?: return@forEach
            val uids = list.map { it.uid }.filter { it >= 0 }
            if (uids.isNotEmpty()) {
                viewModelScope.launch {
                    val res = MailRepository.markAllSeen(account, folder, uids)
                    if (res.isFailure) {
                        _state.value = _state.value.copy(toast = "全部已读同步失败：${friendlyError(res.exceptionOrNull())}")
                    }
                }
            }
        }
    }

    // ---------------- 服务器回写：删除 / 归档 ----------------

    fun archiveEmail(id: Long) {
        val email = _state.value.emails.firstOrNull { it.id == id } ?: return
        removeLocally(id)
        val account = accountOf(email.accountId) ?: return
        if (email.uid < 0) return
        val folder = folderForEmail(email)
        viewModelScope.launch {
            val res = MailRepository.archiveMessage(account, folder, email.uid)
            _state.value = _state.value.copy(
                toast = if (res.isSuccess) "已归档" else "归档失败：${friendlyError(res.exceptionOrNull())}"
            )
        }
    }

    fun deleteEmail(id: Long) {
        val email = _state.value.emails.firstOrNull { it.id == id } ?: return
        removeLocally(id)
        val account = accountOf(email.accountId) ?: return
        if (email.uid < 0) return
        val folder = folderForEmail(email)
        viewModelScope.launch {
            val res = MailRepository.deleteMessage(account, folder, email.uid)
            _state.value = _state.value.copy(
                toast = if (res.isSuccess) "已删除" else "删除失败：${friendlyError(res.exceptionOrNull())}"
            )
        }
    }

    private fun removeLocally(id: Long) {
        _state.value = _state.value.copy(emails = _state.value.emails.filterNot { it.id == id })
    }

    // ---------------- 批量（本地 + 回写） ----------------

    fun markSelectedRead() {
        val targets = _state.value.emails.filter { _state.value.selectedEmails.contains(it.id) && !it.isRead }
        _state.value = _state.value.copy(
            emails = _state.value.emails.map { if (_state.value.selectedEmails.contains(it.id)) it.copy(isRead = true) else it },
            isSelectionMode = false, selectedEmails = emptySet()
        )
        targets.forEach { pushFlag(it, ServerFlag.SEEN, true) }
    }

    fun markSelectedUnread() {
        val targets = _state.value.emails.filter { _state.value.selectedEmails.contains(it.id) && it.isRead }
        _state.value = _state.value.copy(
            emails = _state.value.emails.map { if (_state.value.selectedEmails.contains(it.id)) it.copy(isRead = false) else it },
            isSelectionMode = false, selectedEmails = emptySet()
        )
        targets.forEach { pushFlag(it, ServerFlag.SEEN, false) }
    }

    fun archiveSelected() {
        val targets = _state.value.emails.filter { _state.value.selectedEmails.contains(it.id) }
        _state.value = _state.value.copy(
            emails = _state.value.emails.filterNot { _state.value.selectedEmails.contains(it.id) },
            isSelectionMode = false, selectedEmails = emptySet()
        )
        targets.forEach { e ->
            val acc = accountOf(e.accountId) ?: return@forEach
            if (e.uid >= 0) viewModelScope.launch { MailRepository.archiveMessage(acc, folderForEmail(e), e.uid) }
        }
    }

    fun deleteSelected() {
        val targets = _state.value.emails.filter { _state.value.selectedEmails.contains(it.id) }
        _state.value = _state.value.copy(
            emails = _state.value.emails.filterNot { _state.value.selectedEmails.contains(it.id) },
            isSelectionMode = false, selectedEmails = emptySet()
        )
        targets.forEach { e ->
            val acc = accountOf(e.accountId) ?: return@forEach
            if (e.uid >= 0) viewModelScope.launch { MailRepository.deleteMessage(acc, folderForEmail(e), e.uid) }
        }
    }

    // ---------------- 附件下载 ----------------

    fun downloadAttachment(id: Long, index: Int = 0, onResult: (Boolean, String) -> Unit) {
        val email = _state.value.emails.firstOrNull { it.id == id }
        if (email == null) { onResult(false, "找不到邮件"); return }
        val account = accountOf(email.accountId)
        if (account == null) { onResult(false, "账户不存在"); return }
        if (email.uid < 0) { onResult(false, "缺少 UID，无法下载"); return }
        val folder = folderForEmail(email)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val res = MailRepository.downloadAttachmentAt(account, folder, email.uid, index)
            _state.value = _state.value.copy(isLoading = false)
            if (res.isSuccess) {
                val att = res.getOrNull()!!
                val dir = File(appContext.getExternalFilesDir(null), "attachments").apply { mkdirs() }
                val safeName = att.fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val file = File(dir, "${System.currentTimeMillis()}_$safeName")
                val saved = withContext(Dispatchers.IO) {
                    runCatching { file.writeBytes(att.bytes); true }.getOrDefault(false)
                }
                if (saved) onResult(true, file.absolutePath)
                else onResult(false, "写入文件失败")
            } else {
                onResult(false, friendlyError(res.exceptionOrNull()))
            }
        }
    }

    // ---------------- 发送 ----------------

    fun startReply(email: EmailMessage) {
        val subj = if (email.subject.startsWith("Re:", true)) email.subject else "Re: ${email.subject}"
        pendingCompose = ComposeDraft(
            to = email.senderEmail,
            subject = subj,
            body = "\n\n—— 原始邮件 ——\n发件人：${email.senderName} <${email.senderEmail}>\n时间：${email.displayTime}\n\n${email.body}"
        )
    }

    fun startForward(email: EmailMessage) {
        val subj = if (email.subject.startsWith("Fwd:", true)) email.subject else "Fwd: ${email.subject}"
        pendingCompose = ComposeDraft(
            to = "",
            subject = subj,
            body = "\n\n—— 转发邮件 ——\n发件人：${email.senderName} <${email.senderEmail}>\n主题：${email.subject}\n\n${email.body}"
        )
    }

    fun clearPendingCompose() {
        pendingCompose = null
    }

    fun sendMail(
        accountId: String,
        to: String,
        cc: String,
        subject: String,
        body: String,
        attachmentUris: List<Uri> = emptyList(),
        onResult: (Boolean, String?) -> Unit
    ) {
        val account = _state.value.accounts.firstOrNull { it.id == accountId }
            ?: _state.value.accounts.firstOrNull()
        if (account == null) { onResult(false, "没有可用的发件账户，请先添加邮箱"); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val atts = if (attachmentUris.isEmpty()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) { readAttachments(attachmentUris) }
            }
            val result = MailRepository.sendMail(account, to, cc, subject, body, atts)
            _state.value = _state.value.copy(isLoading = false)
            if (result.isSuccess) onResult(true, null)
            else onResult(false, friendlyError(result.exceptionOrNull()))
        }
    }

    /** 读取用户选择的内容 Uri 为附件（总大小上限 20MB）。 */
    private fun readAttachments(uris: List<Uri>): List<MailAttachment> {
        val out = mutableListOf<MailAttachment>()
        var total = 0L
        val maxTotal = 20L * 1024 * 1024
        uris.forEach { uri ->
            try {
                val resolver = appContext.contentResolver
                var name = uri.lastPathSegment ?: "attachment"
                var sizeHint = 0L
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (ni >= 0) c.getString(ni)?.let { name = it }
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (si >= 0 && !c.isNull(si)) sizeHint = c.getLong(si)
                    }
                }
                if (sizeHint > 0 && total + sizeHint > maxTotal) return@forEach
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
                if (total + bytes.size > maxTotal) return@forEach
                total += bytes.size
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                out.add(MailAttachment(name, mime, bytes))
            } catch (_: Throwable) {
            }
        }
        return out
    }

    // ---------------- 过滤视图 ----------------

    fun filteredEmails(): List<EmailMessage> {
        val s = _state.value
        if (s.searchActive && s.query.isNotBlank()) {
            return s.emails.filter { e ->
                e.subject.contains(s.query, ignoreCase = true) ||
                    e.senderName.contains(s.query, ignoreCase = true) ||
                    e.senderEmail.contains(s.query, ignoreCase = true) ||
                    e.preview.contains(s.query, ignoreCase = true) ||
                    e.body.contains(s.query, ignoreCase = true)
            }
        }
        // 这些文件夹已从服务器真实拉取，直接展示
        when (s.currentFolder) {
            "已发送", "草稿", "垃圾", "废纸篓" -> return s.emails
        }
        when (s.currentFolder) {
            "VIP" -> return s.emails.filter { it.isFlagged }
            "有附件" -> return s.emails.filter { it.hasAttachment }
            "未读" -> return s.emails.filter { !it.isRead }
        }
        if (s.showListMode) return s.emails
        return s.emails.filter { it.category == s.selectedCategory }
    }

    fun unreadCount(category: MailCategory): Int =
        _state.value.emails.count { it.category == category && !it.isRead }

    /** 侧栏智能邮箱未读数。 */
    fun folderUnread(folder: String): Int {
        val list = _state.value.emails
        return when (folder) {
            "收件箱" -> list.count { !it.isRead }
            "VIP" -> list.count { it.isFlagged && !it.isRead }
            "有附件" -> list.count { it.hasAttachment && !it.isRead }
            "未读" -> list.count { !it.isRead }
            "今天" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                list.count { !it.isRead && it.timestamp >= start }
            }
            else -> 0
        }
    }

    fun unreadTotal(): Int = _state.value.emails.count { !it.isRead }

    private fun friendlyError(e: Throwable?): String {
        if (e == null) return "未知错误"
        val m = e.message.orEmpty()
        return when {
            m.contains("535") || m.contains("authentication", true) || m.contains("LOGIN", true) ||
                m.contains("login", true) || m.contains("password", true) || m.contains("Unsafe", true) ->
                "账号或密码/授权码错误。QQ/163/126 请使用「授权码」；Gmail/iCloud/Yahoo 请使用「应用专用密码」，并确认已在网页端开启 IMAP/SMTP 服务。"
            m.contains("timeout", true) || m.contains("timed out", true) -> "连接超时，请检查网络或服务器地址/端口。"
            m.contains("UnknownHost", true) || m.contains("unresolved", true) -> "无法解析服务器地址，请检查 IMAP/SMTP 主机名。"
            m.contains("SSL", true) || m.contains("handshake", true) -> "SSL/加密握手失败，请检查端口与加密方式。"
            m.contains("AUTHENTICATE", true) || m.contains("mechanism", true) || m.contains("BadCommand", true) ->
                "服务器认证方式不兼容。请确认已开启 IMAP/SMTP 并使用授权码/应用专用密码。详情：$m"
            else -> m.ifBlank { e.javaClass.simpleName }
        }
    }
}
