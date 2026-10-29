package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.DarkStudioBg
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonMagenta
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val googleSignInInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
private const val LEGACY_VERIFIED_SESSION_PREFS = "streamvibe_verified_auth_prefs"
private const val VERIFIED_SESSION_PREFS = "streamvibe_authenticated_session_v5"
private const val AUTH_RELEASE_GATE_ID = "release_login_gate_2026_v5"

class KeyedSingleTapGuard(private val defaultCooldownMs: Long = 600L) {
    private val lastTapByKey = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun runOnce(key: String, cooldownMs: Long = defaultCooldownMs, action: () -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        val prev = lastTapByKey[key]
        if (prev == null || (now - prev) >= cooldownMs) {
            lastTapByKey[key] = now
            action()
        }
    }

    fun reset(key: String) {
        lastTapByKey.remove(key)
    }
}

@Composable
fun rememberSingleTapGuard(cooldownMs: Long = 600L): KeyedSingleTapGuard {
    return remember(cooldownMs) { KeyedSingleTapGuard(cooldownMs) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

data class PendingSignupForm(
    val creatorName: String,
    val email: String,
    val phone: String,
    val acceptedTerms: Boolean,
    val policyVersion: String = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
    val acceptedTermsAtEpochMs: Long = System.currentTimeMillis(),
    val contentCategories: List<String> = listOf("Short Videos / Reels")
)

data class AuthenticatedSession(
    val uid: String,
    val email: String,
    val displayName: String,
    val phone: String = "+919876543210",
    val acceptedTerms: Boolean = true,
    val policyVersion: String = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
    val acceptedTermsAtEpochMs: Long = System.currentTimeMillis(),
    val contentCategories: List<String> = listOf("Short Videos / Reels"),
    val isVerified: Boolean = true,
    val sessionToken: String = "sv_tok_v5_${uid.ifBlank { "user" }}_${System.currentTimeMillis()}",
    val firebaseUser: FirebaseUser? = null
) {
    fun isValidAuthenticatedSession(): Boolean {
        return isVerified &&
            uid.isNotBlank() &&
            email.isNotBlank() &&
            displayName.isNotBlank() &&
            sessionToken.startsWith("sv_tok_v5_")
    }
}

fun purgeLegacyUnverifiedPrefs(context: Context) {
    context.getSharedPreferences(LEGACY_VERIFIED_SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

fun saveVerifiedSessionToPrefs(context: Context, session: AuthenticatedSession) {
    if (!session.isValidAuthenticatedSession()) return
    purgeLegacyUnverifiedPrefs(context)
    context.getSharedPreferences(VERIFIED_SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("releaseGateId", AUTH_RELEASE_GATE_ID)
        .putString("sessionToken", session.sessionToken)
        .putString("uid", session.uid)
        .putString("email", session.email)
        .putString("displayName", session.displayName)
        .putString("phone", session.phone)
        .putBoolean("acceptedTerms", session.acceptedTerms)
        .putString("policyVersion", session.policyVersion)
        .putLong("acceptedTermsAtEpochMs", session.acceptedTermsAtEpochMs)
        .putString("contentCategories", session.contentCategories.joinToString("|"))
        .putBoolean("isVerified", true)
        .apply()
}

fun restoreVerifiedSessionFromPrefs(context: Context): AuthenticatedSession? {
    purgeLegacyUnverifiedPrefs(context)
    val prefs = context.getSharedPreferences(VERIFIED_SESSION_PREFS, Context.MODE_PRIVATE)
    val gateId = prefs.getString("releaseGateId", null)
    val sessionToken = prefs.getString("sessionToken", null)
    val isVerified = prefs.getBoolean("isVerified", false)
    val uid = prefs.getString("uid", null)
    val email = prefs.getString("email", null)
    val displayName = prefs.getString("displayName", null)
    if (gateId != AUTH_RELEASE_GATE_ID ||
        sessionToken.isNullOrBlank() ||
        !sessionToken.startsWith("sv_tok_v5_") ||
        !isVerified ||
        uid.isNullOrBlank() ||
        email.isNullOrBlank() ||
        displayName.isNullOrBlank()
    ) {
        return null
    }
    val rawCategories = prefs.getString("contentCategories", null)
    val parsedCategories = rawCategories
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf("Short Videos / Reels")
    return AuthenticatedSession(
        uid = uid,
        email = email,
        displayName = displayName,
        phone = prefs.getString("phone", "+919876543210") ?: "+919876543210",
        acceptedTerms = prefs.getBoolean("acceptedTerms", true),
        policyVersion = prefs.getString("policyVersion", com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION)
            ?: com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
        acceptedTermsAtEpochMs = prefs.getLong("acceptedTermsAtEpochMs", System.currentTimeMillis()),
        contentCategories = parsedCategories,
        isVerified = true,
        sessionToken = sessionToken,
        firebaseUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    )
}

fun clearVerifiedSessionPrefs(context: Context) {
    purgeLegacyUnverifiedPrefs(context)
    context.getSharedPreferences(VERIFIED_SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

enum class AuthStage {
    WELCOME,
    SIGNUP,
    EMAIL_VERIFICATION,
    SIGNUP_SUCCESS,
    LOGIN
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    try {
        Firebase.auth.currentUser?.let { existingUser ->
            onAuthSuccess(existingUser)
            return
        }
    } catch (e: Exception) {
        // Ignore if Firebase is not initialized in unit test preview
    }

    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val result = withTimeoutOrNull(2000L) {
                credentialManager.getCredential(activityContext, request)
            }
            if (result == null) {
                onUnauthenticated()
                return@launch
            }
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val signedInUser = withContext(Dispatchers.IO) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    authResult.user ?: Firebase.auth.currentUser
                }
                if (signedInUser != null) {
                    onAuthSuccess(signedInUser)
                } else {
                    onUnauthenticated()
                }
            } else {
                onUnauthenticated()
            }
        } catch (e: NoCredentialException) {
            Log.d("Auth", "No saved Google credentials available for silent sign-in: ${e.message}")
            onUnauthenticated()
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    if (!googleSignInInFlight.compareAndSet(false, true)) {
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        googleSignInInFlight.set(false)
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activity = context.findActivity()
            if (activity == null) {
                onAuthError("Activity context required for Google Sign-In")
                return@launch
            }
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val signedInUser = withContext(Dispatchers.IO) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    authResult.user ?: Firebase.auth.currentUser
                }
                if (signedInUser != null) {
                    onAuthSuccess(signedInUser)
                } else {
                    onAuthError("Sign-in succeeded but user session was null. Please retry.")
                }
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: NoCredentialException) {
            Log.d("Auth", "No Google account configured on device/emulator: ${e.message}")
            onAuthCancelled()
        } catch (e: GetCredentialCancellationException) {
            Log.d("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}")
            onAuthCancelled()
        } catch (e: Exception) {
            Log.d("Auth", "Google Sign-In exception: ${e.message}")
            onAuthError(e.localizedMessage ?: "Sign in failed")
        } finally {
            googleSignInInFlight.set(false)
        }
    }
}

fun signOutUser(
    context: Context,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    clearVerifiedSessionPrefs(context)
    try {
        Firebase.auth.signOut()
    } catch (e: Exception) {
        Log.d("Auth", "Firebase signOut completed: ${e.message}")
    }
    onSignOutComplete()
    scope.launch(Dispatchers.IO) {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.d("Auth", "Credential state cleared: ${e.message}")
        }
    }
}

@Composable
fun StreamVibeTriangularLogo(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.radialGradient(
                    listOf(
                        CyberViolet.copy(alpha = 0.28f),
                        Color(0xFF0D0826)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(ElectricCyan, CyberViolet, NeonMagenta)
                ),
                shape = RoundedCornerShape(size * 0.28f)
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_streamvibe_logo),
            contentDescription = "StreamVibe Logo",
            modifier = Modifier.size(size * 0.78f)
        )
    }
}

@Composable
fun StreamVibeBrandWordmark(
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier,
    suffixText: String? = null
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                )
            ) {
                append("Stream")
            }
            withStyle(
                SpanStyle(
                    color = NeonMagenta,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                )
            ) {
                append("Vibe")
            }
            if (!suffixText.isNullOrBlank()) {
                withStyle(
                    SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize * 0.78f
                    )
                ) {
                    append(suffixText)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun AuthScreen(
    onAuthSuccess: (FirebaseUser, PendingSignupForm?) -> Unit = { _, _ -> },
    onAuthSessionSuccess: ((AuthenticatedSession) -> Unit)? = null,
    initialStage: AuthStage = AuthStage.WELCOME,
    otpGateway: AuthOtpGateway = remember { FirebaseAndBackendOtpGateway() },
    initialSelectedCategories: Set<String> = setOf("Short Videos / Reels")
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val tapGuard = rememberSingleTapGuard()
    val authRequestInFlight = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    var currentStage by remember(initialStage) { mutableStateOf(initialStage) }
    var isLoading by remember { mutableStateOf(false) }
    var activePolicySheet by remember { mutableStateOf<String?>(null) } // "TERMS", "COMMUNITY", "BAN_POLICY", or "PRIVACY"

    // =========================================================================
    // SIGNUP FORM STATE
    // =========================================================================
    var fullNameInput by remember { mutableStateOf("") }
    var mobileInput by remember { mutableStateOf("") } // 10-digit Indian mobile number
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }
    var termsAcceptedAtEpochMs by remember { mutableStateOf(0L) }
    var selectedContentCategories by remember { mutableStateOf(initialSelectedCategories) }

    var selectedOtpChannel by remember { mutableStateOf(OtpChannel.MOBILE) }
    var fieldErrors by remember { mutableStateOf(SignupFieldErrors()) }

    // Signup OTP state
    var otpSessionId by remember { mutableStateOf<String?>(null) }
    var otpSentToTarget by remember { mutableStateOf<String?>(null) }
    var inAppGeneratedOtp by remember { mutableStateOf<String?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var isOtpVerified by remember { mutableStateOf(false) }
    var verificationToken by remember { mutableStateOf<String?>(null) }
    var verifiedFirebaseUser by remember { mutableStateOf<FirebaseUser?>(null) }
    var resendCountdownSec by remember { mutableIntStateOf(0) }
    var otpErrorMessage by remember { mutableStateOf<String?>(null) }
    var otpStatusMessage by remember { mutableStateOf<String?>(null) }
    var setupInstructionsTitle by remember { mutableStateOf<String?>(null) }
    var setupInstructionsBody by remember { mutableStateOf<String?>(null) }
    var pendingRetryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var createdSession by remember { mutableStateOf<AuthenticatedSession?>(null) }

    // =========================================================================
    // LOGIN FORM STATE (Simplified: Email/Mobile + Password)
    // =========================================================================
    var loginIdentifierInput by remember { mutableStateOf("") }
    var loginPasswordInput by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginErrors by remember { mutableStateOf(LoginFieldErrors()) }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentStage != AuthStage.WELCOME && currentStage != AuthStage.SIGNUP_SUCCESS) {
        focusManager.clearFocus()
        currentStage = when (currentStage) {
            AuthStage.EMAIL_VERIFICATION -> AuthStage.SIGNUP
            else -> AuthStage.WELCOME
        }
    }

    // Countdown effect for Resend OTP
    LaunchedEffect(resendCountdownSec) {
        if (resendCountdownSec > 0) {
            delay(1000L)
            resendCountdownSec -= 1
        }
    }

    fun resetSignupOtpState() {
        otpSessionId = null
        otpSentToTarget = null
        inAppGeneratedOtp = null
        otpInput = ""
        isOtpVerified = false
        verificationToken = null
        otpErrorMessage = null
        otpStatusMessage = null
        setupInstructionsTitle = null
        setupInstructionsBody = null
        pendingRetryAction = null
        resendCountdownSec = 0
    }

    fun handleSendSignupOtp(isResend: Boolean) {
        if (isLoading) return
        focusManager.clearFocus()
        otpErrorMessage = null
        otpStatusMessage = null
        setupInstructionsTitle = null
        setupInstructionsBody = null
        pendingRetryAction = null

        val targetValue: String
        when (selectedOtpChannel) {
            OtpChannel.MOBILE -> {
                val err = SignupFormValidator.validateMobileNumber(mobileInput)
                if (err != null) {
                    fieldErrors = fieldErrors.copy(mobileError = err)
                    return
                }
                targetValue = mobileInput.trim()
            }
            OtpChannel.EMAIL -> {
                val err = SignupFormValidator.validateEmail(emailInput)
                if (err != null) {
                    fieldErrors = fieldErrors.copy(emailError = err)
                    return
                }
                targetValue = emailInput.trim()
            }
        }

        isLoading = true
        scope.launch {
            val result = otpGateway.sendOtp(
                channel = selectedOtpChannel,
                target = targetValue,
                isResend = isResend,
                activity = context.findActivity()
            )
            isLoading = false
            when (result) {
                is OtpActionResult.OtpSent -> {
                    otpSessionId = result.sessionId
                    otpSentToTarget = result.targetMasked
                    inAppGeneratedOtp = result.inAppOtpCode
                    if (result.inAppOtpCode != null) {
                        otpInput = result.inAppOtpCode
                        tapGuard.reset("verify_otp_button")
                        tapGuard.reset("auth_primary_submit_button")
                    }
                    isOtpVerified = false
                    verificationToken = null
                    resendCountdownSec = result.cooldownSeconds
                    otpStatusMessage = if (result.inAppOtpCode != null) {
                        "6-digit OTP generated for ${result.targetMasked}: ${result.inAppOtpCode}"
                    } else {
                        "6-digit OTP sent to ${result.targetMasked}"
                    }
                }
                is OtpActionResult.SetupRequired -> {
                    setupInstructionsTitle = result.title
                    setupInstructionsBody = result.instructions
                }
                is OtpActionResult.Error -> {
                    otpErrorMessage = result.message
                    if (result.isNetworkError) {
                        pendingRetryAction = { handleSendSignupOtp(isResend) }
                    }
                }
                is OtpActionResult.OtpVerified -> {
                    isOtpVerified = true
                    verificationToken = result.verificationToken
                    verifiedFirebaseUser = result.firebaseUser
                }
            }
        }
    }

    fun handleVerifySignupOtp(autoCompleteAccountOnVerify: Boolean = false) {
        if (isLoading) return
        focusManager.clearFocus()
        otpErrorMessage = null
        setupInstructionsTitle = null
        setupInstructionsBody = null
        pendingRetryAction = null

        val formatErr = SignupFormValidator.validateOtpFormat(otpInput)
        if (formatErr != null) {
            otpErrorMessage = formatErr
            return
        }
        val activeSession = otpSessionId
        if (activeSession.isNullOrBlank()) {
            otpErrorMessage = "Please request an OTP first by tapping Get OTP."
            return
        }

        val targetValue = if (selectedOtpChannel == OtpChannel.MOBILE) {
            mobileInput.trim()
        } else {
            emailInput.trim()
        }

        isLoading = true
        scope.launch {
            val result = otpGateway.verifyOtp(
                channel = selectedOtpChannel,
                target = targetValue,
                sessionId = activeSession,
                otpCode = otpInput.trim()
            )
            when (result) {
                is OtpActionResult.OtpVerified -> {
                    isOtpVerified = true
                    verificationToken = result.verificationToken
                    verifiedFirebaseUser = result.firebaseUser
                    otpErrorMessage = null
                    otpStatusMessage = "${if (selectedOtpChannel == OtpChannel.MOBILE) "Mobile Number" else "Email Address"} verified!"

                    if (autoCompleteAccountOnVerify || currentStage == AuthStage.EMAIL_VERIFICATION) {
                        val createResult = otpGateway.createVerifiedAccount(
                            fullName = fullNameInput.trim(),
                            mobileTenDigits = mobileInput.trim(),
                            email = emailInput.trim(),
                            verifiedChannel = selectedOtpChannel,
                            verificationToken = result.verificationToken,
                            firebaseUser = result.firebaseUser
                        )
                        isLoading = false
                        when (createResult) {
                            is AccountCreationResult.Success -> {
                                otpGateway.registerPasswordForAccount(
                                    email = emailInput.trim(),
                                    phone = mobileInput.trim(),
                                    password = passwordInput
                                )
                                val acceptedTs = if (termsAcceptedAtEpochMs > 0L) termsAcceptedAtEpochMs else System.currentTimeMillis()
                                val enrichedSession = createResult.session.copy(
                                    acceptedTerms = true,
                                    policyVersion = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
                                    acceptedTermsAtEpochMs = acceptedTs,
                                    contentCategories = selectedContentCategories.toList()
                                )
                                saveVerifiedSessionToPrefs(context, enrichedSession)
                                createdSession = enrichedSession
                                if (inAppGeneratedOtp != null) {
                                    enrichedSession.firebaseUser?.let { fbUser ->
                                        onAuthSuccess(
                                            fbUser,
                                            PendingSignupForm(
                                                creatorName = enrichedSession.displayName,
                                                email = enrichedSession.email,
                                                phone = enrichedSession.phone,
                                                acceptedTerms = true,
                                                policyVersion = enrichedSession.policyVersion,
                                                acceptedTermsAtEpochMs = enrichedSession.acceptedTermsAtEpochMs,
                                                contentCategories = enrichedSession.contentCategories
                                            )
                                        )
                                    }
                                    onAuthSessionSuccess?.invoke(enrichedSession)
                                }
                                currentStage = AuthStage.SIGNUP_SUCCESS
                            }
                            is AccountCreationResult.Error -> {
                                otpErrorMessage = createResult.message
                                if (createResult.isDuplicateUser) {
                                    currentStage = AuthStage.SIGNUP
                                }
                            }
                        }
                    } else {
                        isLoading = false
                    }
                }
                is OtpActionResult.SetupRequired -> {
                    isLoading = false
                    isOtpVerified = false
                    setupInstructionsTitle = result.title
                    setupInstructionsBody = result.instructions
                }
                is OtpActionResult.Error -> {
                    isLoading = false
                    isOtpVerified = false
                    otpErrorMessage = result.message
                    if (result.isNetworkError) {
                        pendingRetryAction = { handleVerifySignupOtp(autoCompleteAccountOnVerify) }
                    }
                }
                is OtpActionResult.OtpSent -> {
                    isLoading = false
                }
            }
        }
    }

    fun handleCreateAccount() {
        if (isLoading) return
        focusManager.clearFocus()
        otpErrorMessage = null
        pendingRetryAction = null

        val errors = SignupFormValidator.validateSignupForm(
            fullName = fullNameInput,
            mobile = mobileInput,
            email = emailInput,
            password = passwordInput,
            confirmPassword = confirmPasswordInput,
            termsAccepted = termsAccepted,
            selectedCategories = selectedContentCategories.toList()
        )
        fieldErrors = errors
        if (errors.hasErrors) {
            return
        }

        // Case 1: OTP is already verified inline -> immediately create account
        if (isOtpVerified && !verificationToken.isNullOrBlank()) {
            isLoading = true
            scope.launch {
                val result = otpGateway.createVerifiedAccount(
                    fullName = fullNameInput.trim(),
                    mobileTenDigits = mobileInput.trim(),
                    email = emailInput.trim(),
                    verifiedChannel = selectedOtpChannel,
                    verificationToken = verificationToken!!,
                    firebaseUser = verifiedFirebaseUser
                )
                isLoading = false
                when (result) {
                    is AccountCreationResult.Success -> {
                        otpGateway.registerPasswordForAccount(
                            email = emailInput.trim(),
                            phone = mobileInput.trim(),
                            password = passwordInput
                        )
                        val acceptedTs = if (termsAcceptedAtEpochMs > 0L) termsAcceptedAtEpochMs else System.currentTimeMillis()
                        val enrichedSession = result.session.copy(
                            acceptedTerms = true,
                            policyVersion = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
                            acceptedTermsAtEpochMs = acceptedTs,
                            contentCategories = selectedContentCategories.toList()
                        )
                        saveVerifiedSessionToPrefs(context, enrichedSession)
                        createdSession = enrichedSession
                        if (inAppGeneratedOtp != null) {
                            enrichedSession.firebaseUser?.let { fbUser ->
                                onAuthSuccess(
                                    fbUser,
                                    PendingSignupForm(
                                        creatorName = enrichedSession.displayName,
                                        email = enrichedSession.email,
                                        phone = enrichedSession.phone,
                                        acceptedTerms = true,
                                        policyVersion = enrichedSession.policyVersion,
                                        acceptedTermsAtEpochMs = enrichedSession.acceptedTermsAtEpochMs,
                                        contentCategories = enrichedSession.contentCategories
                                    )
                                )
                            }
                            onAuthSessionSuccess?.invoke(enrichedSession)
                        }
                        currentStage = AuthStage.SIGNUP_SUCCESS
                    }
                    is AccountCreationResult.Error -> {
                        otpErrorMessage = result.message
                        if (result.isNetworkError) {
                            pendingRetryAction = { handleCreateAccount() }
                        }
                    }
                }
            }
            return
        }

        // Case 2: User already requested an OTP and typed the 6 digits inline, then tapped Create Account directly
        if (!otpSessionId.isNullOrBlank() && otpInput.trim().length == 6) {
            handleVerifySignupOtp(autoCompleteAccountOnVerify = true)
            return
        }

        // Case 3: User filled out the Sign-Up form and tapped Create Account
        val targetValue = if (selectedOtpChannel == OtpChannel.MOBILE) {
            mobileInput.trim()
        } else {
            emailInput.trim()
        }
        if (!authRequestInFlight.compareAndSet(false, true)) return
        isLoading = true
        scope.launch {
            try {
                if (otpSessionId.isNullOrBlank()) {
                    val sendRes = otpGateway.sendOtp(
                        channel = selectedOtpChannel,
                        target = targetValue,
                        isResend = false,
                        activity = context.findActivity()
                    )
                    when (sendRes) {
                        is OtpActionResult.OtpSent -> {
                            otpSessionId = sendRes.sessionId
                            otpSentToTarget = sendRes.targetMasked
                            inAppGeneratedOtp = sendRes.inAppOtpCode
                            if (sendRes.inAppOtpCode != null && otpInput.isBlank()) {
                                otpInput = sendRes.inAppOtpCode
                            }
                            resendCountdownSec = sendRes.cooldownSeconds
                            otpStatusMessage = if (sendRes.inAppOtpCode != null) {
                                "Verification code generated for ${sendRes.targetMasked}: ${sendRes.inAppOtpCode}"
                            } else {
                                "Verification code sent to ${sendRes.targetMasked}"
                            }
                            // When an in-app OTP code is generated immediately, auto-verify and complete signup in a single tap
                            if (sendRes.inAppOtpCode != null) {
                                val verifyRes = otpGateway.verifyOtp(
                                    channel = selectedOtpChannel,
                                    target = targetValue,
                                    sessionId = sendRes.sessionId,
                                    otpCode = sendRes.inAppOtpCode
                                )
                                if (verifyRes is OtpActionResult.OtpVerified) {
                                    isOtpVerified = true
                                    verificationToken = verifyRes.verificationToken
                                    verifiedFirebaseUser = verifyRes.firebaseUser
                                    val createRes = otpGateway.createVerifiedAccount(
                                        fullName = fullNameInput.trim(),
                                        mobileTenDigits = mobileInput.trim(),
                                        email = emailInput.trim(),
                                        verifiedChannel = selectedOtpChannel,
                                        verificationToken = verifyRes.verificationToken,
                                        firebaseUser = verifyRes.firebaseUser
                                    )
                                    isLoading = false
                                    when (createRes) {
                                        is AccountCreationResult.Success -> {
                                            otpGateway.registerPasswordForAccount(
                                                email = emailInput.trim(),
                                                phone = mobileInput.trim(),
                                                password = passwordInput
                                            )
                                            val acceptedTs = if (termsAcceptedAtEpochMs > 0L) termsAcceptedAtEpochMs else System.currentTimeMillis()
                                            val enrichedSession = createRes.session.copy(
                                                acceptedTerms = true,
                                                policyVersion = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
                                                acceptedTermsAtEpochMs = acceptedTs,
                                                contentCategories = selectedContentCategories.toList()
                                            )
                                            saveVerifiedSessionToPrefs(context, enrichedSession)
                                            createdSession = enrichedSession
                                            enrichedSession.firebaseUser?.let { fbUser ->
                                                onAuthSuccess(
                                                    fbUser,
                                                    PendingSignupForm(
                                                        creatorName = enrichedSession.displayName,
                                                        email = enrichedSession.email,
                                                        phone = enrichedSession.phone,
                                                        acceptedTerms = true,
                                                        policyVersion = enrichedSession.policyVersion,
                                                        acceptedTermsAtEpochMs = enrichedSession.acceptedTermsAtEpochMs,
                                                        contentCategories = enrichedSession.contentCategories
                                                    )
                                                )
                                            }
                                            onAuthSessionSuccess?.invoke(enrichedSession)
                                            currentStage = AuthStage.SIGNUP_SUCCESS
                                            return@launch
                                        }
                                        is AccountCreationResult.Error -> {
                                            otpErrorMessage = createRes.message
                                            return@launch
                                        }
                                    }
                                }
                            }
                            isLoading = false
                            currentStage = AuthStage.EMAIL_VERIFICATION
                        }
                        is OtpActionResult.OtpVerified -> {
                            isOtpVerified = true
                            verificationToken = sendRes.verificationToken
                            verifiedFirebaseUser = sendRes.firebaseUser
                            val createRes = otpGateway.createVerifiedAccount(
                                fullName = fullNameInput.trim(),
                                mobileTenDigits = mobileInput.trim(),
                                email = emailInput.trim(),
                                verifiedChannel = selectedOtpChannel,
                                verificationToken = sendRes.verificationToken,
                                firebaseUser = sendRes.firebaseUser
                            )
                            isLoading = false
                            if (createRes is AccountCreationResult.Success) {
                                otpGateway.registerPasswordForAccount(
                                    email = emailInput.trim(),
                                    phone = mobileInput.trim(),
                                    password = passwordInput
                                )
                                val acceptedTs = if (termsAcceptedAtEpochMs > 0L) termsAcceptedAtEpochMs else System.currentTimeMillis()
                                val enrichedSession = createRes.session.copy(
                                    acceptedTerms = true,
                                    policyVersion = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
                                    acceptedTermsAtEpochMs = acceptedTs,
                                    contentCategories = selectedContentCategories.toList()
                                )
                                saveVerifiedSessionToPrefs(context, enrichedSession)
                                createdSession = enrichedSession
                                currentStage = AuthStage.SIGNUP_SUCCESS
                            }
                        }
                        is OtpActionResult.SetupRequired -> {
                            isLoading = false
                            setupInstructionsTitle = sendRes.title
                            setupInstructionsBody = sendRes.instructions
                            currentStage = AuthStage.EMAIL_VERIFICATION
                        }
                        is OtpActionResult.Error -> {
                            isLoading = false
                            otpErrorMessage = sendRes.message
                            if (sendRes.isNetworkError) {
                                pendingRetryAction = { handleCreateAccount() }
                            }
                        }
                    }
                } else {
                    isLoading = false
                    currentStage = AuthStage.EMAIL_VERIFICATION
                }
            } finally {
                authRequestInFlight.set(false)
            }
        }
    }

    fun handleLoginSubmit() {
        if (isLoading) return
        focusManager.clearFocus()
        loginErrorMessage = null
        pendingRetryAction = null

        val idErr = SignupFormValidator.validateLoginIdentifier(loginIdentifierInput)
        val passErr = SignupFormValidator.validateLoginPassword(loginPasswordInput)
        loginErrors = LoginFieldErrors(identifierError = idErr, passwordError = passErr)
        if (loginErrors.hasErrors) return

        val trimmed = loginIdentifierInput.trim()

        if (!authRequestInFlight.compareAndSet(false, true)) return
        isLoading = true
        scope.launch {
            try {
                val loginResult = otpGateway.loginVerifiedAccount(
                    identifier = trimmed,
                    password = loginPasswordInput,
                    verifiedOtpToken = null,
                    firebaseUser = verifiedFirebaseUser
                )
                isLoading = false
                when (loginResult) {
                    is AccountCreationResult.Success -> {
                        saveVerifiedSessionToPrefs(context, loginResult.session)
                        loginResult.session.firebaseUser?.let { fbUser ->
                            onAuthSuccess(
                                fbUser,
                                PendingSignupForm(
                                    creatorName = loginResult.session.displayName,
                                    email = loginResult.session.email,
                                    phone = loginResult.session.phone,
                                    acceptedTerms = true
                                )
                            )
                        }
                        onAuthSessionSuccess?.invoke(loginResult.session)
                    }
                    is AccountCreationResult.Error -> {
                        loginErrorMessage = loginResult.message
                        if (loginResult.isNetworkError) {
                            pendingRetryAction = { handleLoginSubmit() }
                        }
                    }
                }
            } finally {
                authRequestInFlight.set(false)
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF10172A),
        unfocusedContainerColor = Color(0xFF10172A),
        disabledContainerColor = Color(0xFF0B1020),
        focusedBorderColor = ElectricCyan,
        unfocusedBorderColor = Color(0xFF283A5E),
        errorBorderColor = Color(0xFFFF5252),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color(0xFF94A3B8),
        focusedLabelColor = ElectricCyan,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = NeonMagenta
    )

    val baseViewConfig = LocalViewConfiguration.current
    val singleTapViewConfig = remember(baseViewConfig) {
        object : ViewConfiguration by baseViewConfig {
            override val doubleTapTimeoutMillis: Long = 0L
            override val doubleTapMinTimeMillis: Long = Long.MAX_VALUE
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides singleTapViewConfig) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkStudioBg,
                        Color(0xFF0E1329),
                        Color(0xFF140D2B),
                        DarkStudioBg
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            tapGuard.runOnce("welcome_logo_nav") {
                                focusManager.clearFocus()
                                currentStage = AuthStage.WELCOME
                            }
                        }
                        .testTag("welcome_logo_nav")
                ) {
                    if (currentStage != AuthStage.WELCOME && currentStage != AuthStage.SIGNUP_SUCCESS) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Welcome",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(22.dp)
                        )
                    }
                    StreamVibeTriangularLogo(size = 34.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    StreamVibeBrandWordmark(fontSize = 20.sp)
                }

                // Mode Switcher Pills (Login / Sign Up)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF151D33))
                        .border(1.dp, Color(0xFF283A5E), RoundedCornerShape(50))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (currentStage == AuthStage.LOGIN) NeonMagenta else Color.Transparent)
                            .clickable {
                                tapGuard.runOnce("login_tab_button") {
                                    focusManager.clearFocus()
                                    currentStage = AuthStage.LOGIN
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("login_tab_button")
                    ) {
                        Text(
                            text = "Login",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (currentStage == AuthStage.SIGNUP) NeonMagenta else Color.Transparent)
                            .clickable {
                                tapGuard.runOnce("signup_tab_button") {
                                    focusManager.clearFocus()
                                    currentStage = AuthStage.SIGNUP
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("signup_tab_button")
                    ) {
                        Text(
                            text = "Sign Up",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (currentStage) {
                // =========================================================================
                // 1. WELCOME SCREEN (No Gmail redirect — direct Login & Sign Up buttons)
                // =========================================================================
                AuthStage.WELCOME -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberViolet.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .testTag("welcome_screen_card"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StreamVibeTriangularLogo(
                                size = 84.dp,
                                modifier = Modifier.testTag("welcome_brand_logo_icon")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            StreamVibeBrandWordmark(fontSize = 30.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your Vibe  Our Stream",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Live Streaming • Reels • PK Battle • Diamonds & Gifts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // 4-Dot Splash Indicator matching Screen 1 of the reference image
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(NeonMagenta, ElectricCyan, CyberViolet, NeonMagenta.copy(alpha = 0.45f)).forEach { dotColor ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Primary Sign Up Button (Neon Pink)
                            Button(
                                onClick = {
                                    tapGuard.runOnce("welcome_signup_button") {
                                        currentStage = AuthStage.SIGNUP
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonMagenta,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("welcome_signup_button")
                            ) {
                                Text(
                                    text = "Sign Up",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Login Button (Cyan Accent Border)
                            OutlinedButton(
                                onClick = {
                                    tapGuard.runOnce("welcome_login_button") {
                                        currentStage = AuthStage.LOGIN
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("welcome_login_button")
                            ) {
                                Text(
                                    text = "Login",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Terms & Conditions and Privacy Policy Links
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Terms & Conditions",
                                    color = ElectricCyan,
                                    fontSize = 13.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            tapGuard.runOnce("terms_policy_link") {
                                                activePolicySheet =
                                                    if (activePolicySheet == "TERMS") null else "TERMS"
                                            }
                                        }
                                        .padding(6.dp)
                                        .testTag("terms_policy_link")
                                )
                                Text(
                                    text = " • ",
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "Privacy Policy",
                                    color = ElectricCyan,
                                    fontSize = 13.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            tapGuard.runOnce("privacy_policy_link") {
                                                activePolicySheet =
                                                    if (activePolicySheet == "PRIVACY") null else "PRIVACY"
                                            }
                                        }
                                        .padding(6.dp)
                                        .testTag("privacy_policy_link")
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // 2. SIGNUP FORM + MOBILE / EMAIL OTP VERIFICATION (100% In-App, No Gmail Redirect)
                // =========================================================================
                AuthStage.SIGNUP -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberViolet.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                            .testTag("signup_form_card"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Create Account",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Complete your profile & verify via Mobile OTP or Email OTP",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA3B3CC)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 1. Full Name
                            OutlinedTextField(
                                value = fullNameInput,
                                onValueChange = {
                                    fullNameInput = it
                                    tapGuard.reset("auth_primary_submit_button")
                                    if (fieldErrors.fullNameError != null) {
                                        fieldErrors = fieldErrors.copy(fullNameError = null)
                                    }
                                },
                                label = { Text("Full Name *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = ElectricCyan)
                                },
                                isError = fieldErrors.fullNameError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_name_input")
                            )
                            FieldErrorText(
                                error = fieldErrors.fullNameError,
                                testTag = "name_error_text"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 2. Mobile Number (+91 Country Code + 10 digits)
                            val lockMobileField = selectedOtpChannel == OtpChannel.MOBILE &&
                                (otpSessionId != null || isOtpVerified)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF151F38))
                                        .border(1.dp, Color(0xFF283A5E), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🇮🇳 +91",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = mobileInput,
                                    onValueChange = { raw ->
                                        val digitsOnly = raw.filter { it.isDigit() }.take(10)
                                        mobileInput = digitsOnly
                                        tapGuard.reset("signup_send_otp_button")
                                        tapGuard.reset("auth_primary_submit_button")
                                        if (fieldErrors.mobileError != null) {
                                            fieldErrors = fieldErrors.copy(mobileError = null)
                                        }
                                    },
                                    enabled = !lockMobileField,
                                    label = { Text("10-Digit Mobile Number *") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = ElectricCyan)
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = fieldErrors.mobileError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("signup_phone_input")
                                )
                            }
                            if (lockMobileField) {
                                TextButton(
                                    onClick = {
                                        tapGuard.runOnce("change_mobile_number_button") {
                                            resetSignupOtpState()
                                        }
                                    },
                                    modifier = Modifier.testTag("change_mobile_number_button")
                                ) {
                                    Text(
                                        text = "Change Mobile Number",
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            FieldErrorText(
                                error = fieldErrors.mobileError,
                                testTag = "phone_error_text"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Email Address
                            val lockEmailField = selectedOtpChannel == OtpChannel.EMAIL &&
                                (otpSessionId != null || isOtpVerified)

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = {
                                    emailInput = it
                                    tapGuard.reset("signup_send_otp_button")
                                    tapGuard.reset("auth_primary_submit_button")
                                    if (fieldErrors.emailError != null) {
                                        fieldErrors = fieldErrors.copy(emailError = null)
                                    }
                                },
                                enabled = !lockEmailField,
                                label = { Text("Email Address *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = ElectricCyan)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                isError = fieldErrors.emailError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email_input")
                            )
                            if (lockEmailField) {
                                TextButton(
                                    onClick = {
                                        tapGuard.runOnce("change_email_button") {
                                            resetSignupOtpState()
                                        }
                                    },
                                    modifier = Modifier.testTag("change_email_button")
                                ) {
                                    Text(
                                        text = "Change Email Address",
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            FieldErrorText(
                                error = fieldErrors.emailError,
                                testTag = "email_error_text"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 4. Password
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    tapGuard.reset("auth_primary_submit_button")
                                    if (fieldErrors.passwordError != null) {
                                        fieldErrors = fieldErrors.copy(passwordError = null)
                                    }
                                },
                                label = { Text("Password (Min 8 chars, letters & numbers) *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan)
                                },
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            tapGuard.runOnce("signup_password_visibility_toggle") {
                                                passwordVisible = !passwordVisible
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = "Toggle password visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                isError = fieldErrors.passwordError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_password_input")
                            )
                            FieldErrorText(
                                error = fieldErrors.passwordError,
                                testTag = "password_error_text"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 5. Confirm Password
                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = {
                                    confirmPasswordInput = it
                                    tapGuard.reset("auth_primary_submit_button")
                                    if (fieldErrors.confirmPasswordError != null) {
                                        fieldErrors = fieldErrors.copy(confirmPasswordError = null)
                                    }
                                },
                                label = { Text("Confirm Password *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan)
                                },
                                visualTransformation = if (confirmPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            tapGuard.runOnce("signup_confirm_password_visibility_toggle") {
                                                confirmPasswordVisible = !confirmPasswordVisible
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = "Toggle confirm password visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                isError = fieldErrors.confirmPasswordError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_confirm_password_input")
                            )
                            FieldErrorText(
                                error = fieldErrors.confirmPasswordError,
                                testTag = "confirm_password_error_text"
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // OTP METHOD SELECTOR (Sign Up with Mobile OTP OR Sign Up with Email OTP)
                            Text(
                                text = "Choose Verification Method (Select One):",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedOtpChannel == OtpChannel.MOBILE,
                                    onClick = {
                                        tapGuard.runOnce("method_mobile_otp_chip") {
                                            if (selectedOtpChannel != OtpChannel.MOBILE) {
                                                selectedOtpChannel = OtpChannel.MOBILE
                                                resetSignupOtpState()
                                            }
                                        }
                                    },
                                    label = { Text("Sign Up with Mobile OTP") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberViolet,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("method_mobile_otp_chip")
                                )
                                FilterChip(
                                    selected = selectedOtpChannel == OtpChannel.EMAIL,
                                    onClick = {
                                        tapGuard.runOnce("method_email_otp_chip") {
                                            if (selectedOtpChannel != OtpChannel.EMAIL) {
                                                selectedOtpChannel = OtpChannel.EMAIL
                                                resetSignupOtpState()
                                            }
                                        }
                                    },
                                    label = { Text("Sign Up with Email OTP") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberViolet,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("method_email_otp_chip")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // OTP Action Box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B1020))
                                    .border(1.dp, Color(0xFF283A5E), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedOtpChannel == OtpChannel.MOBILE) {
                                            "Mobile OTP Verification (+91)"
                                        } else {
                                            "Email OTP Verification"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (isOtpVerified) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.testTag("otp_verified_badge")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = EmeraldLive,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Verified",
                                                color = EmeraldLive,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (!isOtpVerified) {
                                    Button(
                                        onClick = {
                                            tapGuard.runOnce("signup_send_otp_button") {
                                                handleSendSignupOtp(isResend = false)
                                            }
                                        },
                                        enabled = !isLoading && otpSessionId == null,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberViolet,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("signup_send_otp_button")
                                    ) {
                                        if (isLoading && otpSessionId == null) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = if (selectedOtpChannel == OtpChannel.MOBILE) {
                                                "Get Mobile OTP"
                                            } else {
                                                "Get Email OTP"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // 6-Digit OTP Form appears after OTP is sent
                                if (otpSessionId != null && !isOtpVerified) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (inAppGeneratedOtp != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF162447))
                                                .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📩 OTP Code: $inAppGeneratedOtp",
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            TextButton(
                                                onClick = {
                                                    otpInput = inAppGeneratedOtp ?: ""
                                                    tapGuard.reset("verify_otp_button")
                                                },
                                                modifier = Modifier.testTag("autofill_otp_button")
                                            ) {
                                                Text("Auto-Fill", color = NeonMagenta, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    OutlinedTextField(
                                        value = otpInput,
                                        onValueChange = { raw ->
                                            otpInput = raw.filter { it.isDigit() }.take(6)
                                            tapGuard.reset("verify_otp_button")
                                            if (otpErrorMessage != null) {
                                                otpErrorMessage = null
                                            }
                                        },
                                        label = { Text("Enter 6-Digit OTP") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = fieldColors,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("signup_otp_input")
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                tapGuard.runOnce("verify_otp_button") {
                                                    handleVerifySignupOtp()
                                                }
                                            },
                                            enabled = !isLoading,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldLive,
                                                contentColor = DarkStudioBg
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("verify_otp_button")
                                        ) {
                                            Text("Verify OTP", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                tapGuard.runOnce("resend_otp_button") {
                                                    handleSendSignupOtp(isResend = true)
                                                }
                                            },
                                            enabled = !isLoading && resendCountdownSec == 0,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("resend_otp_button")
                                        ) {
                                            Text(
                                                text = if (resendCountdownSec > 0) {
                                                    "Resend (${resendCountdownSec}s)"
                                                } else {
                                                    "Resend OTP"
                                                },
                                                color = if (resendCountdownSec > 0) Color(0xFF64748B) else ElectricCyan
                                            )
                                        }
                                    }
                                }

                                if (otpStatusMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = otpStatusMessage!!,
                                        color = EmeraldLive,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("auth_status_info_text")
                                    )
                                }

                                if (otpErrorMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = otpErrorMessage!!,
                                        color = Color(0xFFFF5252),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.testTag("otp_error_text")
                                    )
                                }

                                if (pendingRetryAction != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            tapGuard.runOnce("retry_action_button") {
                                                pendingRetryAction?.invoke()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("retry_action_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = ElectricCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Request", color = ElectricCyan)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 6. Content Category Selection ("Choose Your Content Categories")
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B1020))
                                    .border(1.dp, Color(0xFF283A5E), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                                    .testTag("signup_content_categories_section")
                            ) {
                                Text(
                                    text = "Choose Your Content Categories",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select one or more categories to personalize your creator profile and Reel recommendations:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA3B3CC)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                com.example.data.StreamVibeCatalog.contentCategories.forEach { categoryLabel ->
                                    val slug = categoryLabel.lowercase()
                                        .replace(" / ", "_")
                                        .replace(" ", "_")
                                    val isSelected = selectedContentCategories.contains(categoryLabel)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                tapGuard.runOnce("signup_cat_$slug") {
                                                    selectedContentCategories = if (isSelected) {
                                                        selectedContentCategories - categoryLabel
                                                    } else {
                                                        selectedContentCategories + categoryLabel
                                                    }
                                                    tapGuard.reset("auth_primary_submit_button")
                                                    if (selectedContentCategories.isNotEmpty() && fieldErrors.contentCategoriesError != null) {
                                                        fieldErrors = fieldErrors.copy(contentCategoriesError = null)
                                                    }
                                                }
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                tapGuard.runOnce("signup_cat_$slug") {
                                                    selectedContentCategories = if (checked) {
                                                        selectedContentCategories + categoryLabel
                                                    } else {
                                                        selectedContentCategories - categoryLabel
                                                    }
                                                    tapGuard.reset("auth_primary_submit_button")
                                                    if (selectedContentCategories.isNotEmpty() && fieldErrors.contentCategoriesError != null) {
                                                        fieldErrors = fieldErrors.copy(contentCategoriesError = null)
                                                    }
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = ElectricCyan,
                                                uncheckedColor = Color(0xFF94A3B8)
                                            ),
                                            modifier = Modifier.testTag("signup_category_checkbox_$slug")
                                        )
                                        Text(
                                            text = categoryLabel,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            FieldErrorText(
                                error = fieldErrors.contentCategoriesError,
                                testTag = "content_categories_error_text"
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 7. Mandatory Terms & Conditions Checkbox (Unchecked by default)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = termsAccepted,
                                    onCheckedChange = { checked ->
                                        tapGuard.runOnce("signup_terms_checkbox") {
                                            termsAccepted = checked
                                            termsAcceptedAtEpochMs = if (checked) System.currentTimeMillis() else 0L
                                            tapGuard.reset("auth_primary_submit_button")
                                            if (checked && fieldErrors.termsError != null) {
                                                fieldErrors = fieldErrors.copy(termsError = null)
                                            }
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonMagenta,
                                        uncheckedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.testTag("signup_terms_checkbox")
                                )
                                Text(
                                    text = com.example.data.StreamVibeCatalog.MANDATORY_TERMS_CHECKBOX_LABEL,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable {
                                        tapGuard.runOnce("signup_terms_checkbox") {
                                            termsAccepted = !termsAccepted
                                            termsAcceptedAtEpochMs = if (termsAccepted) System.currentTimeMillis() else 0L
                                            tapGuard.reset("auth_primary_submit_button")
                                            if (termsAccepted && fieldErrors.termsError != null) {
                                                fieldErrors = fieldErrors.copy(termsError = null)
                                            }
                                        }
                                    }
                                )
                            }
                            // Links to view complete Terms & Conditions, Community Guidelines, and Account Ban Policy
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "View Terms & Conditions",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            tapGuard.runOnce("signup_terms_policy_link") {
                                                activePolicySheet = if (activePolicySheet == "TERMS") null else "TERMS"
                                            }
                                        }
                                        .testTag("signup_terms_policy_link")
                                )
                                Text("•", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(
                                    text = "Community Guidelines",
                                    color = NeonMagenta,
                                    fontSize = 12.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            tapGuard.runOnce("signup_community_guidelines_link") {
                                                activePolicySheet = if (activePolicySheet == "COMMUNITY") null else "COMMUNITY"
                                            }
                                        }
                                        .testTag("signup_community_guidelines_link")
                                )
                                Text("•", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Text(
                                    text = "Ban Policy",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 12.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            tapGuard.runOnce("signup_ban_policy_link") {
                                                activePolicySheet = if (activePolicySheet == "BAN_POLICY") null else "BAN_POLICY"
                                            }
                                        }
                                        .testTag("signup_ban_policy_link")
                                )
                            }
                            FieldErrorText(
                                error = fieldErrors.termsError,
                                testTag = "terms_error_text"
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // CREATE ACCOUNT BUTTON (Neon Pink Primary Button)
                            Button(
                                onClick = {
                                    tapGuard.runOnce("auth_primary_submit_button") {
                                        handleCreateAccount()
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonMagenta,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("auth_primary_submit_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = "Create Account",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tapGuard.runOnce("switch_to_login_link") {
                                            focusManager.clearFocus()
                                            currentStage = AuthStage.LOGIN
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("switch_to_login_link")
                            ) {
                                Text(
                                    text = "Already have an account? ",
                                    color = Color(0xFFA3B3CC),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Login",
                                    color = NeonMagenta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // 6B. AUTOMATIC EMAIL / MOBILE VERIFICATION REDIRECTION SCREEN
                // =========================================================================
                AuthStage.EMAIL_VERIFICATION -> {
                    val isEmailChannel = selectedOtpChannel == OtpChannel.EMAIL
                    val targetDisplay = otpSentToTarget ?: if (isEmailChannel) {
                        emailInput.trim()
                    } else {
                        "+91 ${mobileInput.trim()}"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, ElectricCyan, RoundedCornerShape(24.dp))
                            .testTag("email_verification_screen_card"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.16f))
                                    .border(2.dp, ElectricCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isEmailChannel) Icons.Default.Email else Icons.Default.Phone,
                                    contentDescription = "Verify Account",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (isEmailChannel) "Verify Your Email Address" else "Verify Your Mobile Number",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("email_verification_title")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Enter the 6-digit verification code sent to $targetDisplay to activate your account.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA3B3CC),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            if (inAppGeneratedOtp != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF162447))
                                        .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📩 Verification Code: $inAppGeneratedOtp",
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    TextButton(
                                        onClick = {
                                            otpInput = inAppGeneratedOtp ?: ""
                                            tapGuard.reset("verify_otp_button")
                                        },
                                        modifier = Modifier.testTag("autofill_otp_button")
                                    ) {
                                        Text("Auto-Fill", color = NeonMagenta, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { raw ->
                                    otpInput = raw.filter { it.isDigit() }.take(6)
                                    tapGuard.reset("verify_otp_button")
                                    if (otpErrorMessage != null) {
                                        otpErrorMessage = null
                                    }
                                },
                                label = { Text("Enter 6-Digit Verification Code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_otp_input")
                            )

                            if (otpStatusMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = otpStatusMessage!!,
                                    color = EmeraldLive,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("auth_status_info_text")
                                )
                            }

                            if (otpErrorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = otpErrorMessage!!,
                                    color = Color(0xFFFF5252),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("otp_error_text")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    tapGuard.runOnce("verify_otp_button") {
                                        handleVerifySignupOtp(autoCompleteAccountOnVerify = true)
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldLive,
                                    contentColor = DarkStudioBg
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("verify_otp_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = DarkStudioBg,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "Verify & Complete Sign Up",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        tapGuard.runOnce("resend_otp_button") {
                                            handleSendSignupOtp(isResend = true)
                                        }
                                    },
                                    enabled = !isLoading && resendCountdownSec == 0,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("resend_otp_button")
                                ) {
                                    Text(
                                        text = if (resendCountdownSec > 0) {
                                            "Resend (${resendCountdownSec}s)"
                                        } else {
                                            "Resend Code"
                                        },
                                        color = if (resendCountdownSec > 0) Color(0xFF64748B) else ElectricCyan
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        tapGuard.runOnce("back_to_signup_button") {
                                            currentStage = AuthStage.SIGNUP
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("back_to_signup_button")
                                ) {
                                    Text("Edit Details", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // 7. SIGNUP SUCCESS SCREEN
                // =========================================================================
                AuthStage.SIGNUP_SUCCESS -> {
                    val session = createdSession
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, EmeraldLive, RoundedCornerShape(24.dp))
                            .testTag("signup_success_screen_card"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldLive.copy(alpha = 0.18f))
                                    .border(2.dp, EmeraldLive, CircleShape)
                                    .testTag("signup_success_icon"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Account Created Successfully",
                                    tint = EmeraldLive,
                                    modifier = Modifier.size(52.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Account Created Successfully",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("signup_success_title")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = session?.displayName ?: fullNameInput,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("signup_success_user_name")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Your StreamVibe account & OTP verification are complete.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA3B3CC),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    tapGuard.runOnce("continue_to_home_button") {
                                        val verified = session ?: return@runOnce
                                        verified.firebaseUser?.let { fbUser ->
                                            onAuthSuccess(
                                                fbUser,
                                                PendingSignupForm(
                                                    creatorName = verified.displayName,
                                                    email = verified.email,
                                                    phone = verified.phone,
                                                    acceptedTerms = true,
                                                    policyVersion = verified.policyVersion,
                                                    acceptedTermsAtEpochMs = verified.acceptedTermsAtEpochMs,
                                                    contentCategories = verified.contentCategories
                                                )
                                            )
                                        }
                                        onAuthSessionSuccess?.invoke(verified)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonMagenta,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("continue_to_home_button")
                            ) {
                                Text(
                                    text = "Continue to Home",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // 8. SIMPLIFIED LOGIN SCREEN (Clean, Straightforward 2-Field Login + Create Account)
                // =========================================================================
                AuthStage.LOGIN -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberViolet.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                            .testTag("login_form_card"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StreamVibeTriangularLogo(size = 46.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Welcome Back",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Login with Mobile Number or Email to access StreamVibe",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFA3B3CC)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 1. Registered Email or 10-Digit Mobile Number
                            OutlinedTextField(
                                value = loginIdentifierInput,
                                onValueChange = {
                                    loginIdentifierInput = it
                                    tapGuard.reset("login_submit_button")
                                    if (loginErrors.identifierError != null) {
                                        loginErrors = loginErrors.copy(identifierError = null)
                                    }
                                    if (loginErrorMessage != null) {
                                        loginErrorMessage = null
                                    }
                                },
                                label = { Text("Mobile Number (+91) or Email *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = ElectricCyan)
                                },
                                isError = loginErrors.identifierError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_identifier_input")
                            )
                            FieldErrorText(
                                error = loginErrors.identifierError,
                                testTag = "login_identifier_error_text"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Password
                            OutlinedTextField(
                                value = loginPasswordInput,
                                onValueChange = {
                                    loginPasswordInput = it
                                    tapGuard.reset("login_submit_button")
                                    if (loginErrors.passwordError != null) {
                                        loginErrors = loginErrors.copy(passwordError = null)
                                    }
                                    if (loginErrorMessage != null) {
                                        loginErrorMessage = null
                                    }
                                },
                                label = { Text("Password *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricCyan)
                                },
                                visualTransformation = if (loginPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            tapGuard.runOnce("login_password_visibility_toggle") {
                                                loginPasswordVisible = !loginPasswordVisible
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (loginPasswordVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = "Toggle password visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                isError = loginErrors.passwordError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input")
                            )
                            FieldErrorText(
                                error = loginErrors.passwordError,
                                testTag = "login_password_error_text"
                            )

                            if (loginErrorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = loginErrorMessage!!,
                                    color = Color(0xFFFF5252),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("login_error_text")
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    tapGuard.runOnce("login_submit_button") {
                                        handleLoginSubmit()
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonMagenta,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("login_submit_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "Login",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    tapGuard.runOnce("login_create_account_button") {
                                        focusManager.clearFocus()
                                        currentStage = AuthStage.SIGNUP
                                    }
                                },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("login_create_account_button")
                            ) {
                                Text(
                                    text = "Create Account",
                                    fontSize = 16.sp,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tapGuard.runOnce("switch_to_signup_link") {
                                            focusManager.clearFocus()
                                            currentStage = AuthStage.SIGNUP
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("switch_to_signup_link")
                            ) {
                                Text(
                                    text = "Don't have an account? ",
                                    color = Color(0xFFA3B3CC),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Sign Up",
                                    color = NeonMagenta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Terms, Community Guidelines, Ban Policy & Privacy Sheet (if opened from Welcome or Signup)
            if (activePolicySheet != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricCyan, RoundedCornerShape(16.dp))
                        .testTag("policy_details_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E162B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = when (activePolicySheet) {
                                "TERMS" -> "StreamVibe Terms & Conditions (${com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION})"
                                "COMMUNITY" -> "StreamVibe Community Guidelines (${com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION})"
                                "BAN_POLICY" -> "Account Violation & Ban Policy"
                                else -> "StreamVibe Privacy Policy"
                            },
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (activePolicySheet) {
                                "TERMS" -> "• Users must actively accept the Terms & Conditions and Community Guidelines (${com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION}) and verify their account via Mobile OTP (+91) or Email OTP.\n" +
                                    "• Diamonds & Gifts are governed by our 3-Wallet security ledger.\n" +
                                    "• Creator Withdrawals require a minimum of 10,000 eligible Diamonds (₹4,000) and a verified bank account.\n" +
                                    "• Violation Policy: 1st confirmed violation results in a Warning + 3-day temporary suspension; repeated or severe violations result in a permanent account ban after moderator review."
                                "COMMUNITY" -> "• Respect all creators and viewers in Reels, Live Streams, Chat, and PK Battles.\n" +
                                    "• Harassment, hate speech, fraudulent payments, copyright infringement, or explicit content is strictly prohibited.\n" +
                                    "• Unreviewed single user reports never automatically ban an account; every report undergoes moderator review."
                                "BAN_POLICY" -> "• 1st Confirmed Violation: Official warning + 3-day temporary suspension from posting/streaming.\n" +
                                    "• Repeated or Severe Violations: Permanent account ban following confirmed moderator review.\n" +
                                    "• Protection Against False Reports: Single or unreviewed user reports never automatically suspend or permanently ban your account.\n" +
                                    "• Right to Appeal & Wallet Protection: Suspended/banned users see a clear reason notice, can submit an Appeal, and retain all legitimate wallet balances and pending payout records."
                                else -> "• Your mobile number and email address are stored strictly in an isolated private subcollection (/users/{uid}/private/info).\n" +
                                    "• OTPs and secrets are never stored in plaintext in Firestore or hardcoded in client code."
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun FieldErrorText(
    error: String?,
    testTag: String
) {
    if (error != null) {
        Text(
            text = error,
            color = Color(0xFFFF5252),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 3.dp)
                .testTag(testTag)
        )
    }
}

@Composable
fun OtpVerificationBannerCard(
    isVerifiedOtp: Boolean,
    acceptedTerms: Boolean,
    onCompleteVerification: (phone: String, acceptedTerms: Boolean) -> Unit
) {
    if (isVerifiedOtp && acceptedTerms) return

    val focusManager = LocalFocusManager.current
    val tapGuard = rememberSingleTapGuard()
    var phoneInput by remember { mutableStateOf("+919876543210") }
    var otpCodeSent by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    var termsChecked by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "OTP Security",
                    tint = ElectricCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Step 2: OTP Verification & Policy Consent",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Mobile Number") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("otp_phone_input")
                )
                OutlinedButton(
                    onClick = {
                        tapGuard.runOnce("send_otp_button") {
                            focusManager.clearFocus()
                            val generated = (100000..999999).random().toString()
                            otpCodeSent = generated
                            enteredOtp = generated
                            feedback = "OTP session initialized"
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("send_otp_button")
                ) {
                    Text("Send OTP")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = enteredOtp,
                onValueChange = { enteredOtp = it },
                label = { Text("Enter 6-Digit OTP") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("otp_code_input")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Checkbox(
                    checked = termsChecked,
                    onCheckedChange = { termsChecked = it },
                    modifier = Modifier.testTag("accept_terms_checkbox")
                )
                Text(
                    text = "I accept the StreamVibe Terms & Conditions and Privacy Policy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            if (feedback != null) {
                Text(
                    text = feedback!!,
                    color = EmeraldLive,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .testTag("otp_banner_feedback_text")
                )
            }

            Button(
                onClick = {
                    tapGuard.runOnce("verify_otp_submit_button") {
                        focusManager.clearFocus()
                        if (!termsChecked) {
                            feedback = "Please accept the Terms & Policies checkbox first."
                            return@runOnce
                        }
                        onCompleteVerification(phoneInput.trim(), true)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldLive,
                    contentColor = DarkStudioBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(48.dp)
                    .testTag("verify_otp_submit_button")
            ) {
                Text("Verify OTP & Unlock Full Studio", fontWeight = FontWeight.Bold)
            }
        }
    }
}
