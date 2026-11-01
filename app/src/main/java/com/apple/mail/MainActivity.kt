package com.apple.mail

import android.graphics.Color as AndroidColor
import android.os.Bundle
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7))
    ) {
        // 内容整体避让状态栏 / 导航栏，避免控件被系统栏遮挡无法点击
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
                        onReply = { navController.navigate("compose") },
                        onDownloadAttachment = { cb ->
                            viewModel.downloadAttachment(id) { ok, msg ->
                                cb(if (ok) "已保存到：$msg" else "下载失败：$msg")
                            }
                        }
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
                    onOpenEmail = { id -> navController.navigate("email/$id") }
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
