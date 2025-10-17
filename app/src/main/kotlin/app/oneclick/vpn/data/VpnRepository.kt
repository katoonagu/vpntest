package app.oneclick.vpn.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.oneclick.vpn.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VpnRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val PREF_FILE = "oneclick-vpn"
        private const val KEY_SELECTED_PROFILE = "selected_profile"
    }

    private val appContext = context.applicationContext
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        PREF_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _selectedProfile = MutableStateFlow(
        sharedPreferences.getString(KEY_SELECTED_PROFILE, BuildConfig.DEFAULT_WG_ASSET)
            ?: BuildConfig.DEFAULT_WG_ASSET
    )

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_SELECTED_PROFILE) {
            _selectedProfile.value =
                sharedPreferences.getString(KEY_SELECTED_PROFILE, BuildConfig.DEFAULT_WG_ASSET)
                    ?: BuildConfig.DEFAULT_WG_ASSET
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun selectedProfileFlow(): Flow<String> = _selectedProfile.asStateFlow()

    suspend fun setSelectedProfile(assetPath: String) {
        withContext(ioDispatcher) {
            sharedPreferences.edit()
                .putString(KEY_SELECTED_PROFILE, assetPath)
                .apply()
        }
    }

    fun currentProfile(): String = _selectedProfile.value

    suspend fun readAsset(context: Context, assetPath: String): String =
        withContext(ioDispatcher) {
            context.assets.open(assetPath).use { input ->
                input.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        }

    fun close() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
