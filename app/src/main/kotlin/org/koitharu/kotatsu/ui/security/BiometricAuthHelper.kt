package org.koitharu.kotatsu.ui.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricHardwareState {
    READY_BIOMETRIC,
    READY_CREDENTIAL_ONLY,
    NONE_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

object BiometricAuthHelper {

    fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Authenticators.BIOMETRIC_STRONG or
                Authenticators.BIOMETRIC_WEAK or
                Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun inspectCapability(context: Context): BiometricHardwareState {
        val manager = runCatching { BiometricManager.from(context) }.getOrNull()
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (manager == null) {
            return if (keyguard?.isDeviceSecure == true) {
                BiometricHardwareState.READY_CREDENTIAL_ONLY
            } else {
                BiometricHardwareState.UNAVAILABLE
            }
        }

        val bioResult = runCatching {
            manager.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)
        }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)

        if (bioResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricHardwareState.READY_BIOMETRIC
        }

        val combinedResult = runCatching {
            manager.canAuthenticate(allowedAuthenticators())
        }.getOrDefault(bioResult)

        if (combinedResult == BiometricManager.BIOMETRIC_SUCCESS || keyguard?.isDeviceSecure == true) {
            return BiometricHardwareState.READY_CREDENTIAL_ONLY
        }

        return when (bioResult) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricHardwareState.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricHardwareState.NO_HARDWARE
            else -> BiometricHardwareState.UNAVAILABLE
        }
    }

    fun canAuthenticateAny(context: Context): Boolean {
        val state = inspectCapability(context)
        return state == BiometricHardwareState.READY_BIOMETRIC ||
            state == BiometricHardwareState.READY_CREDENTIAL_ONLY
    }

    fun launchBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onErrorMessage(errString.toString())
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        runCatching {
            prompt.authenticate(promptInfo)
        }.onFailure { err ->
            onErrorMessage(err.localizedMessage ?: "Falha ao abrir autenticação biométrica.")
        }
    }
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
