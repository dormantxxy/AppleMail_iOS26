package com.apple.mail.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.apple.mail.oauth.OAuthManager
import com.apple.mail.oauth.OAuthProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OAuth 授权弹窗：WebView 打开授权页，拦截 redirect_uri 提取 code 并换取 token。
 */
@Composable
fun OAuthDialog(
    config: OAuthProviders.Config,
    onCancel: () -> Unit,
    onResult: (OAuthManager.TokenSet?) -> Unit
) {
    val verifier = remember { OAuthManager.generateVerifier() }
    val state = remember { OAuthManager.generateState() }
    val authUrl = remember { OAuthManager.buildAuthUrl(config, verifier, state) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("登录授权", fontSize = 16.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        if (url.startsWith(config.redirectUri)) {
                                            val code = OAuthManager.extractCode(url, state)
                                            if (code == null) {
                                                onResult(null)
                                            } else {
                                                scope.launch {
                                                    val token = withContext(Dispatchers.IO) {
                                                        OAuthManager.exchangeCode(config, code, verifier)
                                                    }
                                                    onResult(token)
                                                }
                                            }
                                            return true
                                        }
                                        return false
                                    }
                                }
                                loadUrl(authUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(560.dp)
                    )
                }
            }
        }
    }
}
