package tedwester.convo.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeyStorage(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    val hasApiKey: Boolean
        get() = !getApiKey().isNullOrBlank()

    fun isBiometricLockEnabled(): Boolean =
        prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    fun saveModel(model: StoredModel) {
        prefs.edit()
            .putString(KEY_MODEL_ID, model.id)
            .putString(KEY_MODEL_NAME, model.name)
            .apply()
    }

    fun getModel(): StoredModel? {
        val id = prefs.getString(KEY_MODEL_ID, null) ?: return null
        val name = prefs.getString(KEY_MODEL_NAME, null) ?: id
        return StoredModel(id = id, name = name)
    }

    private companion object {
        const val FILE_NAME = "convo_secure_prefs"
        const val KEY_API_KEY = "openrouter_api_key"
        const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        const val KEY_MODEL_ID = "selected_model_id"
        const val KEY_MODEL_NAME = "selected_model_name"
    }
}
