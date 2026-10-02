package com.mobplayer.tv.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(@ApplicationContext private val context: Context) {

    // Lazy: Keystore setup takes ~2s on low-end TVs. AuthManager is injected on the main thread
    // (service onCreate), but prefs are only touched from Ktor handlers, off the main thread.
    private val sharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun generatePin(): String {
        return (1000..9999).random().toString()
    }

    fun verifyPin(expectedPin: String, receivedPin: String): Boolean {
        return expectedPin == receivedPin
    }

    fun generateAndSaveToken(): String {
        val token = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
        return token
    }

    fun isValidToken(token: String): Boolean {
        val savedToken = sharedPreferences.getString(KEY_AUTH_TOKEN, null)
        return savedToken != null && savedToken == token
    }

    fun clearToken() {
        sharedPreferences.edit().remove(KEY_AUTH_TOKEN).apply()
    }

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
    }
}
