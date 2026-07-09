package com.cloudflare.manager.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "cloudflare_manager_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(accountId: String, token: String) {
        prefs.edit().putString(accountId, token).apply()
    }

    fun getToken(accountId: String): String? {
        return prefs.getString(accountId, null)
    }

    fun deleteToken(accountId: String) {
        prefs.edit().remove(accountId).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
