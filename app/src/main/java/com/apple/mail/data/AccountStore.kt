package com.apple.mail.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * 账户持久化（SharedPreferences）。
 * 密码字段做 Base64 混淆存储（非强加密，仅避免明文肉眼可见）。
 */
object AccountStore {

    private const val PREFS = "apple_mail_accounts"
    private const val KEY_ACCOUNTS = "accounts_json"

    fun load(context: Context): List<MailAccount> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MailAccount(
                    id = o.optString("id"),
                    displayName = o.optString("displayName"),
                    email = o.optString("email"),
                    password = decode(o.optString("password")),
                    providerId = o.optString("providerId"),
                    imapHost = o.optString("imapHost"),
                    imapPort = o.optInt("imapPort", 993),
                    imapSsl = o.optBoolean("imapSsl", true),
                    smtpHost = o.optString("smtpHost"),
                    smtpPort = o.optInt("smtpPort", 465),
                    smtpSsl = o.optBoolean("smtpSsl", true),
                    smtpStartTls = o.optBoolean("smtpStartTls", false),
                    colorIndex = o.optInt("colorIndex", 0),
                    authType = o.optString("authType", "password"),
                    accessToken = decode(o.optString("accessToken")),
                    refreshToken = decode(o.optString("refreshToken")),
                    expiresAt = o.optLong("expiresAt", 0L),
                    tokenEndpoint = o.optString("tokenEndpoint"),
                    clientId = o.optString("clientId"),
                    clientSecret = decode(o.optString("clientSecret")),
                    scope = o.optString("scope")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, accounts: List<MailAccount>) {
        val arr = JSONArray()
        accounts.forEach { a ->
            val o = JSONObject()
            o.put("id", a.id)
            o.put("displayName", a.displayName)
            o.put("email", a.email)
            o.put("password", encode(a.password))
            o.put("providerId", a.providerId)
            o.put("imapHost", a.imapHost)
            o.put("imapPort", a.imapPort)
            o.put("imapSsl", a.imapSsl)
            o.put("smtpHost", a.smtpHost)
            o.put("smtpPort", a.smtpPort)
            o.put("smtpSsl", a.smtpSsl)
            o.put("smtpStartTls", a.smtpStartTls)
            o.put("colorIndex", a.colorIndex)
            o.put("authType", a.authType)
            o.put("accessToken", encode(a.accessToken))
            o.put("refreshToken", encode(a.refreshToken))
            o.put("expiresAt", a.expiresAt)
            o.put("tokenEndpoint", a.tokenEndpoint)
            o.put("clientId", a.clientId)
            o.put("clientSecret", encode(a.clientSecret))
            o.put("scope", a.scope)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNTS, arr.toString())
            .apply()
    }

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String = try {
        String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }
}
