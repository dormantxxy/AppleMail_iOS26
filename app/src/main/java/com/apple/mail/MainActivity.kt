package com.apple.mail

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apple.mail.data.EmailMessage
import com.apple.mail.ui.screens.AddAccountScreen
import com.apple.mail.ui.screens.ComposeScreen
import com.apple.mail.ui.screens.DigestScreen
import com.apple.mail.ui.screens.EmailDetailScreen
import com.apple.mail.ui.screens.InboxScreen
import com.apple.mail.ui.theme.AppleMailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            AppleMailTheme {
                MailApp()
            }
        }
    }
}

@Composable
fun MailApp() {
    val navController = rememberNavController()
    val viewModel: MailViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    fun openAttachment(path: String) {
        try {
            val file = java.io.File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            // 无可用应用时忽略
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
    ) {
        // 内容整体避让状态栏 / 导航栏，避免控件被系统栏遮挡
        NavHost(
            navController = navController,
            startDestination = "inbox",
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            enterTransition = { slideInVertically(initialOffsetY = { it / 10 }) + fadeIn(tween(280)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable("inbox") {
                InboxScreen(
                    viewModel = viewModel,
                    onOpenEmail = { id -> navController.navigate("email/$id") },
                    onCompose = { navController.navigate("compose") },
                    onOpenDigest = { sender -> navController.navigate("digest/$sender") },
                    onAddAccount = { navController.navigate("addAccount") }
                )
            }

            composable(
                route = "email/{id}",
                arguments = listOf(
                    androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.LongType }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                val email: EmailMessage? = state.emails.firstOrNull { it.id == id }
                if (email != null) {
                    EmailDetailScreen(
                        email = email,
                        onBack = { navController.popBackStack() },
                        onToggleFlag = { viewModel.toggleFlag(id) },
                        onArchive = {
                            viewModel.archiveEmail(id)
                            navController.popBackStack()
                        },
                        onDelete = {
                            viewModel.deleteEmail(id)
                            navController.popBackStack()
                        },
                        onReply = {
                            viewModel.startReply(email)
                            navController.navigate("compose")
                        },
                        onForward = {
                            viewModel.startForward(email)
                            navController.navigate("compose")
                        },
                        onDownloadAttachment = { index, cb ->
                            viewModel.downloadAttachment(id, index) { ok, msg -> cb(ok, msg) }
                        },
                        onOpenAttachment = { path -> openAttachment(path) }
                    )
                }
            }

            composable(
                route = "digest/{sender}",
                arguments = listOf(
                    androidx.navigation.navArgument("sender") { type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val sender = backStackEntry.arguments?.getString("sender") ?: ""
                val emails = state.emails.filter { it.senderName == sender }
                DigestScreen(
                    senderName = sender,
                    emails = emails,
                    onBack = { navController.popBackStack() },
                    onOpenEmail = { id -> viewModel.markAsRead(id); navController.navigate("email/$id") }
                )
            }

            composable("compose") {
                ComposeScreen(
                    viewModel = viewModel,
                    onCancel = { navController.popBackStack() },
                    onSent = { navController.popBackStack() }
                )
            }

            composable("addAccount") {
                AddAccountScreen(
                    onBack = { navController.popBackStack() },
                    onVerify = { account, cb -> viewModel.verifyAccount(account, cb) },
                    onSave = { account -> viewModel.saveAccount(account) }
                )
            }
        }
    }
}
