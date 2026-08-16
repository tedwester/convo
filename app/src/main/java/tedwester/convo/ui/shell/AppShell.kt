package tedwester.convo.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.security.AppLockManager
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.features.chat.data.ApiPreferencesStore
import tedwester.convo.features.chat.data.QuickSettingsStore
import tedwester.convo.features.chat.data.SearchPreferences
import tedwester.convo.features.chat.data.SearchPreferencesStore
import tedwester.convo.features.chat.data.ComposerPreferences
import tedwester.convo.features.chat.data.ComposerPreferencesStore
import tedwester.convo.features.chat.data.ApiPreferences
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.data.VoicePreferencesStore
import tedwester.convo.ui.chat.ChatScreen
import tedwester.convo.ui.settings.SettingsScreen

@Composable
fun AppShell(
    apiKey: String,
    api: OpenRouterApi,
    keyStorage: KeyStorage,
    appLockManager: AppLockManager,
    biometricLockEnabled: Boolean,
    voicePreferencesStore: VoicePreferencesStore,
    searchPreferencesStore: SearchPreferencesStore,
    composerPreferencesStore: ComposerPreferencesStore,
    apiPreferencesStore: ApiPreferencesStore,
    quickSettingsStore: QuickSettingsStore,
    onApiKeyChanged: (String) -> Unit,
    onBiometricLockChanged: (Boolean) -> Unit,
    onLockApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var voicePrefs by remember { mutableStateOf(voicePreferencesStore.load()) }
    var searchPrefs by remember { mutableStateOf(searchPreferencesStore.load()) }
    var composerPrefs by remember { mutableStateOf(composerPreferencesStore.load()) }
    var apiPrefs by remember { mutableStateOf(apiPreferencesStore.load()) }
    var quickSettingsConfig by remember { mutableStateOf(quickSettingsStore.load()) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun dismissKeyboardAndFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        ChatScreen(
            apiKey = apiKey,
            api = api,
            keyStorage = keyStorage,
            navigationBlocked = showSettings,
            onOpenSettings = {
                dismissKeyboardAndFocus()
                showSettings = true
            },
            voicePreferences = voicePrefs,
            onVoicePreferencesChanged = { next ->
                voicePrefs = next
                voicePreferencesStore.save(next)
            },
            searchPreferences = searchPrefs,
            onSearchPreferencesChanged = { next ->
                searchPrefs = next
                searchPreferencesStore.save(next)
            },
            composerPreferences = composerPrefs,
            onComposerPreferencesChanged = { next ->
                composerPrefs = next
                composerPreferencesStore.save(next)
            },
            onLockApp = onLockApp,
            quickSettingsConfig = quickSettingsConfig,
            onQuickSettingsChanged = { next ->
                quickSettingsConfig = next
                quickSettingsStore.save(next)
            },
        )

        if (showSettings) {
            SettingsScreen(
                apiKey = apiKey,
                api = api,
                appLockManager = appLockManager,
                biometricLockEnabled = biometricLockEnabled,
                voicePreferences = voicePrefs,
                onVoicePreferencesChanged = { next ->
                    voicePrefs = next
                    voicePreferencesStore.save(next)
                },
                searchPreferences = searchPrefs,
                onSearchPreferencesChanged = { next ->
                    searchPrefs = next
                    searchPreferencesStore.save(next)
                },
                composerPreferences = composerPrefs,
                onComposerPreferencesChanged = { next ->
                    composerPrefs = next
                    composerPreferencesStore.save(next)
                },
                quickSettingsConfig = quickSettingsConfig,
                onQuickSettingsChanged = { next ->
                    quickSettingsConfig = next
                    quickSettingsStore.save(next)
                },
                apiPreferences = apiPrefs,
                onApiPreferencesChanged = { next ->
                    apiPrefs = next
                    apiPreferencesStore.save(next)
                },
                onApiTimeoutChanged = { minutes -> api.updateRequestTimeoutMinutes(minutes) },
                onBiometricLockChanged = onBiometricLockChanged,
                onApiKeyChanged = { newKey ->
                    if (newKey.isBlank()) {
                        keyStorage.clearApiKey()
                    } else {
                        keyStorage.saveApiKey(newKey)
                    }
                    onApiKeyChanged(newKey)
                },
                onClose = {
                    dismissKeyboardAndFocus()
                    showSettings = false
                },
            )
        }
    }
}
