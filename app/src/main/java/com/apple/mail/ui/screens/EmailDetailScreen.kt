package com.apple.mail.ui.screens

import android.content.Intent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apple.mail.data.EmailMessage
import com.apple.mail.ui.theme.MailBlue
import com.apple.mail.ui.theme.MailTextSecondary

/**
 * 阅读邮件（iOS 26 Mail 风格）：
 * - 原生导航栏 + 主题/发件信息
 * - 正文用 WebView 渲染 HTML（支持图片/表格/链接），纯文本回退
 * - 附件列表可下载并「打开」预览
 * - 底部操作条为独立一行，正文用 weight 占据剩余空间，不会被底栏遮挡
 */
@Composable
fun EmailDetailScreen(
    email: EmailMessage,
    onBack: () -> Unit,
    onToggleFlag: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onDownloadAttachment: (index: Int, cb: (Boolean, String) -> Unit) -> Unit,
    onOpenAttachment: (String) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF000000) else Color.White
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val divider = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadedPath by remember { mutableStateOf<String?>(null) }

    val attachmentNames = remember(email.id) {
        email.attachmentNames.ifEmpty {
            listOfNotNull(email.attachmentName).ifEmpty { if (email.hasAttachment) listOf("附件") else emptyList() }
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
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MailBlue)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = email.senderName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "发往：${email.recipients.ifBlank { "我" }}",
                    fontSize = 12.sp,
                    color = MailTextSecondary,
                    maxLines = 1
                )
            }
            IconButton(onClick = onToggleFlag) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = "旗标",
                    tint = if (email.isFlagged) Color(0xFFFF9500) else MailTextSecondary
                )
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "更多", tint = MailTextSecondary)
            }
        }
        HorizontalDivider(color = divider, thickness = 0.5.dp)

        // 主题 / 时间
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = email.subject,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${email.senderEmail} · ${email.displayTime}",
                fontSize = 12.sp,
                color = MailTextSecondary,
                maxLines = 1
            )
        }

        // 附件（横向列表）
        if (attachmentNames.isNotEmpty()) {
            HorizontalDivider(color = divider, thickness = 0.5.dp)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(attachmentNames) { index, name ->
                    AttachmentChip(name = name) {
                        downloadStatus = "正在下载…"
                        onDownloadAttachment(index) { ok, msg ->
                            if (ok) {
                                downloadedPath = msg
                                downloadStatus = "已下载"
                            } else {
                                downloadStatus = msg
                            }
                        }
                    }
                }
            }
            if (downloadStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surface)
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = downloadStatus ?: "",
                        fontSize = 12.sp,
                        color = MailTextSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    if (downloadedPath != null) {
                        TextButton(onClick = { onOpenAttachment(downloadedPath!!) }) {
                            Text("打开", color = MailBlue, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        val previewBitmap = remember(downloadedPath) {
            downloadedPath?.let { path ->
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) decodePreview(path) else null
            }
        }
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "附件预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .heightIn(max = 180.dp)
                    .clickable { downloadedPath?.let { onOpenAttachment(it) } }
                    .padding(8.dp)
            )
        }

        HorizontalDivider(color = divider, thickness = 0.5.dp)

        // 正文
        if (email.bodyHtml.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString()
                                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Throwable) {
                                    }
                                }
                                return true
                            }
                        }
                        loadDataWithBaseURL(
                            null,
                            buildEmailHtml(email.bodyHtml, isDark),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(surface),
                onRelease = { wv ->
                    try {
                        wv.stopLoading()
                        wv.destroy()
                    } catch (_: Throwable) {
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(surface)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = email.body,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // 底部操作条（独立一行，不遮挡正文）
        HorizontalDivider(color = divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailAction(Icons.Filled.Archive, "归档", MailBlue, onArchive)
            DetailAction(Icons.Filled.Delete, "删除", Color(0xFFFF3B30), onDelete)
            DetailAction(Icons.Outlined.Flag, "旗标", if (email.isFlagged) Color(0xFFFF9500) else MailBlue, onToggleFlag)
            DetailAction(Icons.AutoMirrored.Filled.Reply, "回复", MailBlue, onReply)
            DetailAction(Icons.Filled.Forward, "转发", MailBlue, onForward)
        }
    }
}

@Composable
private fun AttachmentChip(name: String, onDownload: () -> Unit) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val chipBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(chipBg)
            .clickable { onDownload() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MailBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 180.dp)
        )
    }
}

@Composable
private fun DetailAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
    }
}

/** 采样解码图片，避免大图 OOM。 */
private fun decodePreview(path: String): android.graphics.Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sample > 1280) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    } catch (_: Throwable) {
        null
    }
}

/** 把邮件 HTML 包裹成自适应、支持图片/表格的文档。 */
private fun buildEmailHtml(raw: String, dark: Boolean): String {
    val color = if (dark) "#FFFFFF" else "#000000"
    val bg = if (dark) "#000000" else "#FFFFFF"
    return """
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0">
        <style>
        html, body { margin:0; padding:0; background:$bg; color:$color; }
        body { padding:16px; font-size:16px; line-height:1.45; font-family:-apple-system, Roboto, sans-serif; word-wrap:break-word; overflow-wrap:anywhere; }
        img { max-width:100% !important; height:auto !important; }
        table { max-width:100% !important; border-collapse:collapse; }
        a { color:#007AFF; }
        blockquote { border-left:3px solid #8E8E93; margin:8px 0; padding-left:10px; color:#8E8E93; }
        pre, code { white-space:pre-wrap; word-wrap:break-word; }
        </style></head><body>$raw</body></html>
    """.trimIndent()
}
