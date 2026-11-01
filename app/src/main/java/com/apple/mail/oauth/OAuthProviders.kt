package com.apple.mail.oauth

import com.apple.mail.BuildConfig

/**
 * OAuth 服务商配置。
 * 需要在 app/build.gradle 的 defaultConfig 填入：
 *   GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
 *   MICROSOFT_CLIENT_ID
 */
object OAuthProviders {

    const val REDIRECT_URI = "com.apple.mail:/oauth2redirect"

    data class Config(
        val authEndpoint: String,
        val tokenEndpoint: String,
        val scope: String,
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
        val extraAuthParams: Map<String, String> = emptyMap()
    )

    fun configFor(providerId: String): Config? = when (providerId) {
        "gmail" -> Config(
            authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            scope = "https://mail.google.com/ openid email",
            clientId = BuildConfig.GOOGLE_CLIENT_ID,
            clientSecret = BuildConfig.GOOGLE_CLIENT_SECRET,
            redirectUri = REDIRECT_URI,
            extraAuthParams = mapOf(
                "access_type" to "offline",
                "prompt" to "consent"
            )
        )
        "outlook", "exchange" -> Config(
            authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            scope = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send",
            clientId = BuildConfig.MICROSOFT_CLIENT_ID,
            clientSecret = "",
            redirectUri = REDIRECT_URI,
            extraAuthParams = mapOf("prompt" to "select_account")
        )
        else -> null
    }

    fun isConfigured(providerId: String): Boolean {
        val cfg = configFor(providerId) ?: return false
        return cfg.clientId.isNotBlank()
    }
}
