@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.koitharu.kotatsu.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.R

@Composable
fun AppLockScreen(
    expectedPin: String,
    allowSystemCredential: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var biometricErrorText by remember { mutableStateOf<String?>(null) }
    var autoPromptedOnce by rememberSaveable { mutableStateOf(false) }

    val fragmentActivity = remember(context) { context.findFragmentActivity() }
    val keyguardManager = remember(context) {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    val capabilityState = remember(context, allowSystemCredential) {
        if (!allowSystemCredential) BiometricHardwareState.UNAVAILABLE
        else BiometricAuthHelper.inspectCapability(context)
    }
    val canUseDeviceCredential = allowSystemCredential && (
        capabilityState == BiometricHardwareState.READY_BIOMETRIC ||
            capabilityState == BiometricHardwareState.READY_CREDENTIAL_ONLY
        )

    val fallbackCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        }
    }

    fun triggerSystemAuth() {
        biometricErrorText = null
        val title = context.getString(R.string.lock_screen_title)
        val subtitle = context.getString(R.string.lock_biometric_prompt_subtitle)
        if (fragmentActivity != null) {
            BiometricAuthHelper.launchBiometricPrompt(
                activity = fragmentActivity,
                title = title,
                subtitle = subtitle,
                onSuccess = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlocked()
                },
                onErrorMessage = { msg ->
                    biometricErrorText = msg
                }
            )
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguardManager?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent != null) {
                fallbackCredentialLauncher.launch(intent)
            }
        }
    }

    // Automatically invoke native BiometricPrompt on lock screen appearance when available
    LaunchedEffect(canUseDeviceCredential, fragmentActivity) {
        if (canUseDeviceCredential && fragmentActivity != null && !autoPromptedOnce) {
            autoPromptedOnce = true
            triggerSystemAuth()
        }
    }

    fun onDigitTap(digit: String) {
        if (enteredPin.length >= 4) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        hasError = false
        biometricErrorText = null
        val updated = enteredPin + digit
        enteredPin = updated
        if (updated.length == 4) {
            if (updated == expectedPin) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUnlocked()
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                hasError = true
                enteredPin = ""
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            enteredPin = enteredPin.dropLast(1)
            hasError = false
        }
    }

    val burstShape = MaterialShapes.SoftBurst.toShape()
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("app_lock_screen")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = (-60).dp)
                    .clip(burstShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-60).dp, y = 60.dp)
                    .clip(cookieShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f))
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                val isTabletWide = maxWidth >= 680.dp

                if (isTabletWide) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            LockHeaderAndIndicators(
                                enteredLength = enteredPin.length,
                                hasError = hasError,
                                biometricErrorText = biometricErrorText,
                                canUseDeviceCredential = canUseDeviceCredential,
                                onTriggerSystemAuth = ::triggerSystemAuth
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            ExpressivePinKeypad(
                                canUseDeviceCredential = canUseDeviceCredential,
                                onDigitTap = ::onDigitTap,
                                onBackspace = ::onBackspace,
                                onSystemAuth = ::triggerSystemAuth
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LockHeaderAndIndicators(
                            enteredLength = enteredPin.length,
                            hasError = hasError,
                            biometricErrorText = biometricErrorText,
                            canUseDeviceCredential = canUseDeviceCredential,
                            onTriggerSystemAuth = ::triggerSystemAuth
                        )

                        ExpressivePinKeypad(
                            canUseDeviceCredential = canUseDeviceCredential,
                            onDigitTap = ::onDigitTap,
                            onBackspace = ::onBackspace,
                            onSystemAuth = ::triggerSystemAuth
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockHeaderAndIndicators(
    enteredLength: Int,
    hasError: Boolean,
    biometricErrorText: String?,
    canUseDeviceCredential: Boolean,
    onTriggerSystemAuth: () -> Unit
) {
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()
    val crestRotation by animateFloatAsState(
        targetValue = enteredLength * 22f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "crestRotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 360.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(92.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = crestRotation }
                    .clip(cookieShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
            )
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.lock_screen_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lock_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(26.dp))

        val shakeOffsetX = remember { Animatable(0f) }
        LaunchedEffect(hasError) {
            if (hasError) {
                listOf(-18f, 18f, -12f, 12f, -6f, 6f, 0f).forEach { target ->
                    shakeOffsetX.animateTo(target, animationSpec = tween(45))
                }
            } else {
                shakeOffsetX.snapTo(0f)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { translationX = shakeOffsetX.value }
        ) {
            repeat(4) { idx ->
                val filled = idx < enteredLength
                val dotWidth by animateDpAsState(
                    targetValue = if (filled) 28.dp else 16.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "pinDotWidth_$idx"
                )
                val dotHeight by animateDpAsState(
                    targetValue = if (filled) 20.dp else 16.dp,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "pinDotHeight_$idx"
                )
                Box(
                    modifier = Modifier
                        .width(dotWidth)
                        .height(dotHeight)
                        .clip(CircleShape)
                        .background(
                            when {
                                hasError -> MaterialTheme.colorScheme.error
                                filled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                )
            }
        }

        AnimatedVisibility(visible = hasError || !biometricErrorText.isNullOrBlank()) {
            Text(
                text = if (hasError) {
                    stringResource(R.string.lock_screen_wrong_pin)
                } else {
                    biometricErrorText.orEmpty()
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (canUseDeviceCredential) {
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onTriggerSystemAuth,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lock_biometric_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.lock_screen_system_auth))
            }
        }
    }
}

@Composable
private fun ExpressivePinKeypad(
    canUseDeviceCredential: Boolean,
    onDigitTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onSystemAuth: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        rows.forEach { rowDigits ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowDigits.forEach { digit ->
                    MorphingKeypadButton(
                        testTagName = "pin_key_$digit",
                        onClick = { onDigitTap(digit) }
                    ) {
                        Text(
                            text = digit,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canUseDeviceCredential) {
                MorphingKeypadButton(
                    testTagName = "pin_key_biometric",
                    isAccent = true,
                    onClick = onSystemAuth
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = stringResource(R.string.lock_screen_system_auth),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(74.dp))
            }

            MorphingKeypadButton(
                testTagName = "pin_key_0",
                onClick = { onDigitTap("0") }
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            MorphingKeypadButton(
                testTagName = "pin_key_backspace",
                isAccent = true,
                onClick = onBackspace
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = stringResource(R.string.lock_backspace_desc),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun MorphingKeypadButton(
    testTagName: String,
    isAccent: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val radius by animateDpAsState(
        targetValue = if (pressed) 18.dp else 37.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "keypadRadius"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "keypadScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(74.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(radius))
            .background(
                if (isAccent) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .testTag(testTagName)
    ) {
        content()
    }
}
