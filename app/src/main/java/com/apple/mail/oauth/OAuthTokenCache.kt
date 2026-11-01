package com.apple.mail.oauth

import java.util.concurrent.ConcurrentHashMap

/** 运行期 OAuth access token 缓存（避免每次连接都刷新）。 */
object OAuthTokenCache {

    private val cache = ConcurrentHashMap<String, OAuthManager.TokenSet>()

    fun get(accountId: String): OAuthManager.TokenSet? = cache[accountId]

    fun put(accountId: String, token: OAuthManager.TokenSet) {
        cache[accountId] = token
    }

    fun clear(accountId: String) {
        cache.remove(accountId)
    }
}
