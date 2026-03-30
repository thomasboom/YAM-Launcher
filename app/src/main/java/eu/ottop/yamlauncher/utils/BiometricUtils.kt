package eu.ottop.yamlauncher.utils

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import eu.ottop.yamlauncher.R

/**
 * Utility class for biometric authentication.
 * Handles fingerprint, face unlock, and PIN/pattern/password authentication.
 *
 * Supports both strong biometrics (fingerprint, face) and device credentials
 * (PIN, pattern, password) on Android 11+.
 */
class BiometricUtils(private val activity: FragmentActivity) {

    // Callback for authentication results
    private lateinit var callbackSettings: CallbackSettings
    private val logger = Logger.getInstance(activity)

    /**
     * Callback interface for biometric authentication results.
     * Implement to handle success, failure, and error cases.
     */
    interface CallbackSettings {
        /** Called when authentication succeeds */
        fun onAuthenticationSucceeded()

        /** Called when authentication fails (valid biometric rejected) */
        fun onAuthenticationFailed()

        /** Called when authentication is cancelled or encounters an error */
        fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?)
    }

    /**
     * Starts biometric authentication flow.
     * Shows system prompt and handles all authentication callbacks.
     *
     * @param callbackApp Callback for receiving authentication results
     */
    fun startBiometricSettingsAuth(callbackApp: CallbackSettings) {
        this.callbackSettings = callbackApp

        // Create authentication callback to handle results
        val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                logger.i("BiometricUtils", "Biometric authentication succeeded")
                callbackSettings.onAuthenticationSucceeded()
            }

            override fun onAuthenticationFailed() {
                logger.w("BiometricUtils", "Biometric authentication failed")
                callbackSettings.onAuthenticationFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence) {
                logger.e("BiometricUtils", "Biometric authentication error: $errorMessage (code: $errorCode)")
                callbackSettings.onAuthenticationError(errorCode, errorMessage)
            }
        }

        // Get main thread executor for callback delivery
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor, authenticationCallback)

        // Determine allowed authenticators based on API level
        // Android 11+ supports device credentials (PIN/pattern/password)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

        // Check if biometric auth is available on this device
        val canAuthenticate =
            BiometricManager.from(activity).canAuthenticate(authenticators)

        // Build authentication prompt with localized strings
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.text_biometric_login))
            .setSubtitle(activity.getString(R.string.text_biometric_login_sub))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false) // Don't require explicit confirm after auth
            .build()

        // Start authentication if available
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            logger.i("BiometricUtils", "Starting biometric authentication")
            biometricPrompt.authenticate(promptInfo)
        } else {
            logger.w("BiometricUtils", "Biometric authentication not available: $canAuthenticate")
        }
    }
}
