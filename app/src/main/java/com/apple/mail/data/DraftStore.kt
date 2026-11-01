package com.apple.mail.data

import android.content.Context
import org.json.JSONObject

/**
 * 撰写草稿本地存储（单份草稿，退出后下次进入撰写页自动恢复）。
 */
object DraftStore {

    private const val PREFS = "apple_mail_draft"
    private const val KEY = "draft_json"

    data class Draft(
        val to: String = "",
        val cc: String = "",
        val subject: String = "",
        val body: String = "",
        val accountId: String = ""
    ) {
        fun isEmpty(): Boolean =
            to.isBlank() && cc.isBlank() && subject.isBlank() && body.isBlank()
    }

    fun load(context: Context): Draft? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(json)
            val d = Draft(
                to = o.optString("to", ""),
                cc = o.optString("cc", ""),
                subject = o.optString("subject", ""),
                body = o.optString("body", ""),
                accountId = o.optString("accountId", "")
            )
            if (d.isEmpty()) null else d
        } catch (_: Throwable) {
            null
        }
    }

    fun save(context: Context, draft: Draft) {
        try {
            if (draft.isEmpty()) {
                clear(context)
                return
            }
            val o = JSONObject()
            o.put("to", draft.to)
            o.put("cc", draft.cc)
            o.put("subject", draft.subject)
            o.put("body", draft.body)
            o.put("accountId", draft.accountId)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, o.toString())
                .apply()
        } catch (_: Throwable) {
        }
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply()
        } catch (_: Throwable) {
        }
    }
}
