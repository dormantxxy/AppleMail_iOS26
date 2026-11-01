package com.apple.mail.oauth

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.0（Authorization Code + PKCE）最小实现，用于 Gmail / Outlook 的 XOAUTH2。
 */
object OAuthManager {

    data class TokenSet(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )

    private val random = SecureRandom()

    private fun b64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return b64Url(bytes)
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return b64Url(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return b64Url(digest.digest(verifier.toByteArray(Charsets.US_ASCII)))
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun buildAuthUrl(config: OAuthProviders.Config, verifier: String, state: String): String {
        val params = mutableListOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to config.scope,
            "code_challenge" to challenge(verifier),
            "code_challenge_method" to "S256",
            "state" to state
        )
        config.extraAuthParams.forEach { (k, v) -> params.add(k to v) }
        return config.authEndpoint + "?" + params.joinToString("&") { enc(it.first) + "=" + enc(it.second) }
    }

    fun extractCode(redirectUrl: String, expectedState: String): String? {
        return try {
            val uri = Uri.parse(redirectUrl)
            val error = uri.getQueryParameter("error")
            if (!error.isNullOrBlank()) return null
            val state = uri.getQueryParameter("state")
            if (!expectedState.isNullOrBlank() && state != null && state != expectedState) return null
            uri.getQueryParameter("code")
        } catch (_: Throwable) {
            null
        }
    }

    fun exchangeCode(
        config: OAuthProviders.Config,
        code: String,
        verifier: String
    ): TokenSet? {
        val params = mutableMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
            "client_id" to config.clientId,
            "code_verifier" to verifier
        )
        if (config.clientSecret.isNotBlank()) params["client_secret"] = config.clientSecret
        return postToken(config.tokenEndpoint, params)
    }

    fun refresh(config: OAuthProviders.Config, refreshToken: String): TokenSet? {
        if (refreshToken.isBlank() || config.tokenEndpoint.isBlank() || config.clientId.isBlank()) return null
        val params = mutableMapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to config.clientId
        )
        if (config.clientSecret.isNotBlank()) params["client_secret"] = config.clientSecret
        return postToken(config.tokenEndpoint, params)
    }

    private fun postToken(url: String, params: Map<String, String>): TokenSet? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20000
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }
            val body = params.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(text) } catch (_: Throwable) { null } ?: return null
            val access = json.optString("access_token")
            if (access.isBlank()) return null
            val refresh = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            TokenSet(
                accessToken = access,
                refreshToken = refresh,
                expiresAt = System.currentTimeMillis() + expiresIn * 1000L
            )
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }
}
