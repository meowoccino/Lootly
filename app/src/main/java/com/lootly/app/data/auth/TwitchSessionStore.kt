package com.lootly.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lootly.app.data.model.TwitchSession
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TwitchSessionStore(private val context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<TwitchSession?> = _session.asStateFlow()

    private val _autoPauseEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PAUSE, true))
    val autoPauseEnabled: StateFlow<Boolean> = _autoPauseEnabled.asStateFlow()

    private val _usageCardDismissed = MutableStateFlow(prefs.getBoolean(KEY_USAGE_DISMISSED, false))
    val usageCardDismissed: StateFlow<Boolean> = _usageCardDismissed.asStateFlow()

    private val _badgesEnabled = MutableStateFlow(prefs.getBoolean(KEY_BADGES_ENABLED, true))
    val badgesEnabled: StateFlow<Boolean> = _badgesEnabled.asStateFlow()

    private val _dropsEnabled = MutableStateFlow(prefs.getBoolean(KEY_DROPS_ENABLED, true))
    val dropsEnabled: StateFlow<Boolean> = _dropsEnabled.asStateFlow()

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun readSession(): TwitchSession? {
        val authToken = prefs.getString(KEY_AUTH_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val login = prefs.getString(KEY_LOGIN, null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        val displayName = prefs.getString(KEY_DISPLAY_NAME, login) ?: login
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, null)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: generateFallbackDeviceId()
        val twilightUser = prefs.getString(KEY_TWILIGHT_USER, null)
        val rawCookies = prefs.getString(KEY_RAW_COOKIES, "") ?: ""
        val capturedAt = prefs.getLong(KEY_CAPTURED_AT, System.currentTimeMillis())

        return TwitchSession(
            authToken = authToken,
            userId = userId,
            login = login,
            displayName = displayName,
            avatarUrl = avatarUrl,
            deviceId = deviceId,
            twilightUserJson = twilightUser,
            rawCookies = rawCookies,
            capturedAtEpochMs = capturedAt
        )
    }

    fun saveSession(session: TwitchSession) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, session.authToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_LOGIN, session.login)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_AVATAR_URL, session.avatarUrl)
            .putString(KEY_DEVICE_ID, session.deviceId)
            .putString(KEY_TWILIGHT_USER, session.twilightUserJson)
            .putString(KEY_RAW_COOKIES, session.rawCookies)
            .putLong(KEY_CAPTURED_AT, session.capturedAtEpochMs)
            .apply()
        _session.value = session
    }

    fun updateProfile(displayName: String, avatarUrl: String?) {
        val current = _session.value ?: return
        val effectiveName = displayName.ifBlank { current.displayName }
        saveSession(
            current.copy(
                displayName = effectiveName,
                avatarUrl = avatarUrl ?: current.avatarUrl
            )
        )
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PAUSE, enabled).apply()
        _autoPauseEnabled.value = enabled
    }

    fun setUsageCardDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_USAGE_DISMISSED, dismissed).apply()
        _usageCardDismissed.value = dismissed
    }

    fun setBadgesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BADGES_ENABLED, enabled).apply()
        _badgesEnabled.value = enabled
    }

    fun setDropsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DROPS_ENABLED, enabled).apply()
        _dropsEnabled.value = enabled
    }

    fun clearSessionAndCookies() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_LOGIN)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_TWILIGHT_USER)
            .remove(KEY_RAW_COOKIES)
            .remove(KEY_CAPTURED_AT)
            .apply()
        _session.value = null

        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.removeSessionCookies(null)
            cm.flush()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching { File(context.filesDir, "config.yaml").delete() }
        runCatching { File(context.filesDir, "configs").deleteRecursively() }
        runCatching { File(context.filesDir, "miner_data/cookies").deleteRecursively() }
    }

    companion object {
        private const val PREFS_FILE_NAME = "twitch_miner_secure_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LOGIN = "login"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TWILIGHT_USER = "twilight_user"
        private const val KEY_RAW_COOKIES = "raw_cookies"
        private const val KEY_CAPTURED_AT = "captured_at"
        private const val KEY_AUTO_PAUSE = "auto_pause_enabled"
        private const val KEY_USAGE_DISMISSED = "usage_onboarding_dismissed"
        private const val KEY_BADGES_ENABLED = "badges_enabled"
        private const val KEY_DROPS_ENABLED = "drops_enabled"

        fun generateFallbackDeviceId(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val random = SecureRandom()
            return buildString(32) {
                repeat(32) { append(chars[random.nextInt(chars.length)]) }
            }
        }
    }
}
