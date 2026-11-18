package org.koitharu.kotatsu

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koitharu.kotatsu.ui.KotatsuAppRoot
import org.koitharu.kotatsu.ui.reader.LocalSetVolumeKeyCapture
import org.koitharu.kotatsu.ui.reader.LocalVolumeKeyEvents
import org.koitharu.kotatsu.ui.security.AppLockScreen
import org.koitharu.kotatsu.ui.theme.KotatsuTheme

class MainActivity : ComponentActivity() {

    private val volumeKeyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    @Volatile
    private var captureVolumeKeys: Boolean = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val appContainer = (application as KotatsuApp).container
        val repository = appContainer.mangaRepository

        setContent {
            val prefsState by repository.preferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val windowSizeClass = calculateWindowSizeClass(this)

            val prefs = prefsState
            if (prefs == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141218))
                )
            } else {
                // Toggle FLAG_SECURE dynamically when user enables Recents/Screenshot protection
                LaunchedEffect(prefs.secureScreenEnabled) {
                    if (prefs.secureScreenEnabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                var isLocked by rememberSaveable {
                    mutableStateOf(prefs.appLockEnabled && prefs.appLockPin.length == 4)
                }
                var lastStoppedAt by rememberSaveable { mutableLongStateOf(0L) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, prefs.appLockEnabled, prefs.appLockPin) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (!isChangingConfigurations) {
                                    lastStoppedAt = System.currentTimeMillis()
                                }
                            }
                            Lifecycle.Event.ON_START -> {
                                val elapsed = System.currentTimeMillis() - lastStoppedAt
                                if (lastStoppedAt > 0L &&
                                    elapsed > 30_000L &&
                                    prefs.appLockEnabled &&
                                    prefs.appLockPin.length == 4
                                ) {
                                    isLocked = true
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                CompositionLocalProvider(
                    LocalVolumeKeyEvents provides volumeKeyEvents,
                    LocalSetVolumeKeyCapture provides { capture -> captureVolumeKeys = capture }
                ) {
                    KotatsuTheme(
                        themeMode = prefs.themeMode,
                        amoledDark = prefs.amoledDark,
                        dynamicColors = prefs.dynamicColors
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            KotatsuAppRoot(
                                repository = repository,
                                widthSizeClass = windowSizeClass.widthSizeClass,
                                onTriggerLockNow = {
                                    if (prefs.appLockEnabled && prefs.appLockPin.length == 4) {
                                        isLocked = true
                                    }
                                }
                            )

                            val shouldShowLock = isLocked &&
                                prefs.appLockEnabled &&
                                prefs.appLockPin.length == 4

                            AnimatedVisibility(
                                visible = shouldShowLock,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                BackHandler(enabled = true) {
                                    finish()
                                }
                                AppLockScreen(
                                    expectedPin = prefs.appLockPin,
                                    allowSystemCredential = prefs.appLockSystemCredential,
                                    onUnlocked = { isLocked = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (captureVolumeKeys) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    volumeKeyEvents.tryEmit(-1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    volumeKeyEvents.tryEmit(1)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (captureVolumeKeys &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
