package com.megasmsrouter

import android.app.Application
import com.megasmsrouter.background.WorkManagerScheduler
import com.megasmsrouter.data.AppDatabase
import com.megasmsrouter.data.AppPrefs
import com.megasmsrouter.data.repository.ConfigRepository
import com.megasmsrouter.network.ApiClient
import com.megasmsrouter.security.KeystoreCipher
import com.megasmsrouter.security.SecretCipher
import com.megasmsrouter.sms.SmsMessageProcessor

/** Minimal manual dependency container. */
class MsrApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.get(this) }
    val cipher: SecretCipher by lazy { KeystoreCipher() }
    val prefs: AppPrefs by lazy { AppPrefs(this) }
    val repo: ConfigRepository by lazy { ConfigRepository(db, cipher) }
    val apiClient: ApiClient by lazy { ApiClient() }
    val processor: SmsMessageProcessor by lazy {
        SmsMessageProcessor(db, cipher, { prefs.isForwardingEnabled() }, WorkManagerScheduler(this))
    }
}
