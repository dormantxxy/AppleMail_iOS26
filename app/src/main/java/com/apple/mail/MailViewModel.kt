package com.apple.mail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apple.mail.data.AccountStore
import com.apple.mail.data.EmailMessage
import com.apple.mail.data.MailAccount
import com.apple.mail.data.MailCategory
import com.apple.mail.data.MailRepository
import com.apple.mail.data.ServerFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val errorMessage: String? = null,
    val lastSyncTime: Long = 0L,
    val toast: String? = null
)

class MailViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Application = getApplication()

    private val _state = MutableStateFlow(MailUiState())
    val state: StateFlow<MailUiState> = _state.asStateFlow()

    val emails: List<EmailMessage> get() = _state.value.emails

    init {
        val accounts = AccountStore.load(appContext)
        _state.value = _state.value.copy(accounts = accounts)
        if (accounts.isNotEmpty()) refreshAll()
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
            val all = mutableListOf<EmailMessage>()
            var lastError: String? = null
            accounts.forEach { acc ->
                val res = MailRepository.fetchFolder(acc, folderName)
                if (res.isSuccess) all.addAll(res.getOrNull().orEmpty())
                else lastError = "${acc.email}: ${friendlyError(res.exceptionOrNull())}"
            }
            all.sortByDescending { it.timestamp }
            _state.value = _state.value.copy(
                emails = all,
                isLoading = false,
                errorMessage = lastError,
                lastSyncTime = System.currentTimeMillis()
            )
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

    fun downloadAttachment(id: Long, onResult: (Boolean, String) -> Unit) {
        val email = _state.value.emails.firstOrNull { it.id == id }
        if (email == null) { onResult(false, "找不到邮件"); return }
        val account = accountOf(email.accountId)
        if (account == null) { onResult(false, "账户不存在"); return }
        if (email.uid < 0) { onResult(false, "缺少 UID，无法下载"); return }
        val folder = folderForEmail(email)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val res = MailRepository.downloadFirstAttachment(account, folder, email.uid)
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

    fun sendMail(
        accountId: String, to: String, cc: String, subject: String, body: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val account = _state.value.accounts.firstOrNull { it.id == accountId }
            ?: _state.value.accounts.firstOrNull()
        if (account == null) { onResult(false, "没有可用的发件账户，请先添加邮箱"); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = MailRepository.sendMail(account, to, cc, subject, body)
            _state.value = _state.value.copy(isLoading = false)
            if (result.isSuccess) onResult(true, null)
            else onResult(false, friendlyError(result.exceptionOrNull()))
        }
    }

    // ---------------- 过滤视图 ----------------

    fun filteredEmails(): List<EmailMessage> {
        val s = _state.value
        if (s.searchActive && s.query.isNotBlank()) {
            return s.emails.filter { e ->
                e.subject.contains(s.query, ignoreCase = true) ||
                    e.senderName.contains(s.query, ignoreCase = true) ||
                    e.senderEmail.contains(s.query, ignoreCase = true) ||
                    e.preview.contains(s.query, ignoreCase = true)
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
