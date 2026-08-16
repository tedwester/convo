package tedwester.convo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.security.AppLockManager
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.ui.applock.AppLockGate
import tedwester.convo.features.chat.data.ChatNotifications
import tedwester.convo.ui.onboarding.OnboardingScreen
import tedwester.convo.ui.shell.AppShell
import tedwester.convo.ui.theme.ConvoTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureOpenChatIntent(intent)
        enableEdgeToEdge()
        setContent {
            ConvoTheme {
                val context = LocalContext.current
                val activity = context as FragmentActivity
                val app = context.applicationContext as ConvoApp
                val keyStorage = remember { KeyStorage(context) }
                val appLockManager = remember { AppLockManager(activity) }
                val api: OpenRouterApi = remember { app.api }

                var apiKey by remember { mutableStateOf(keyStorage.getApiKey()) }
                var biometricLockEnabled by remember {
                    mutableStateOf(keyStorage.isBiometricLockEnabled())
                }
                var isUnlocked by remember { mutableStateOf(!biometricLockEnabled) }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* ongoing FGS still works; reply alerts need grant */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val lifecycleOwner = ProcessLifecycleOwner.get()
                DisposableEffect(lifecycleOwner, biometricLockEnabled) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP &&
                            biometricLockEnabled &&
                            !activity.isChangingConfigurations
                        ) {
                            isUnlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val key = apiKey
                when {
                    key == null -> {
                        OnboardingScreen(
                            api = api,
                            onComplete = { newKey ->
                                keyStorage.saveApiKey(newKey)
                                apiKey = newKey
                            },
                        )
                    }
                    else -> {
                        AppLockGate(
                            locked = !isUnlocked,
                            appLockManager = appLockManager,
                            onUnlocked = { isUnlocked = true },
                        ) {
                            AppShell(
                                apiKey = key,
                                api = api,
                                keyStorage = keyStorage,
                                appLockManager = appLockManager,
                                biometricLockEnabled = biometricLockEnabled,
                                voicePreferencesStore = app.voicePreferencesStore,
                                searchPreferencesStore = app.searchPreferencesStore,
                                composerPreferencesStore = app.composerPreferencesStore,
                                apiPreferencesStore = app.apiPreferencesStore,
                                quickSettingsStore = app.quickSettingsStore,
                                onApiKeyChanged = { newKey -> apiKey = newKey },
                                onBiometricLockChanged = { enabled ->
                                    keyStorage.setBiometricLockEnabled(enabled)
                                    biometricLockEnabled = enabled
                                    if (enabled) {
                                        isUnlocked = true
                                    }
                                },
                                onLockApp = { isUnlocked = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureOpenChatIntent(intent)
    }

    private fun captureOpenChatIntent(intent: Intent?) {
        val chatId = intent?.getStringExtra(ChatNotifications.EXTRA_OPEN_CHAT_ID) ?: return
        (application as ConvoApp).offerOpenChat(chatId)
    }
}
