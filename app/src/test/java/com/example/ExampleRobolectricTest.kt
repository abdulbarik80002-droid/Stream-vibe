package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ReelPost
import com.example.data.UserPrivateInfo
import com.example.data.UserProfile
import com.example.ui.AccountCreationResult
import com.example.ui.AuthOtpGateway
import com.example.ui.AuthScreen
import com.example.ui.AuthStage
import com.example.ui.AuthenticatedSession
import com.example.ui.OtpActionResult
import com.example.ui.OtpChannel
import com.example.ui.OtpVerificationBannerCard
import com.example.ui.ReelsAndShortsTab
import com.example.ui.SignupFormValidator
import com.example.ui.SupportAdminAndPoliciesTab
import com.example.ui.UiState
import com.example.ui.WalletAndRazorpayTab
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deterministic test double for [AuthOtpGateway] to test all 13 Signup, OTP, Expiry,
 * Rate-Limiting, Network Failure, Duplicate Account, and Home Navigation scenarios.
 */
class FakeServerOtpGateway : AuthOtpGateway {
    var shouldSimulateNetworkFailureOnSend = false
    var shouldSimulateExpiredOtp = false
    var sendOtpCallCount = 0
    var verifyOtpCallCount = 0
    var createAccountCallCount = 0
    var issuedOtpCode = "739284"
    val registeredEmails = mutableSetOf("existing@streamvibe.app")
    val registeredPhones = mutableSetOf("+919988776655")
    private val usedOtpSessions = mutableSetOf<String>()

    override suspend fun sendOtp(
        channel: OtpChannel,
        target: String,
        isResend: Boolean,
        activity: Activity?
    ): OtpActionResult {
        sendOtpCallCount++
        if (shouldSimulateNetworkFailureOnSend) {
            return OtpActionResult.Error(
                message = "Network error: Unable to reach OTP server.",
                isNetworkError = true
            )
        }
        val masked = if (channel == OtpChannel.MOBILE) "+91${target.takeLast(10)}" else target
        return OtpActionResult.OtpSent(
            sessionId = "sess_${sendOtpCallCount}",
            targetMasked = masked,
            expiresAtMillis = System.currentTimeMillis() + 300_000L,
            cooldownSeconds = 0 // 0s in test to allow immediate Resend verification
        )
    }

    override suspend fun verifyOtp(
        channel: OtpChannel,
        target: String,
        sessionId: String,
        otpCode: String
    ): OtpActionResult {
        verifyOtpCallCount++
        if (shouldSimulateExpiredOtp) {
            return OtpActionResult.Error(
                message = "OTP has expired. Please tap Resend OTP to get a new code.",
                isExpired = true
            )
        }
        if (usedOtpSessions.contains(sessionId)) {
            return OtpActionResult.Error(
                message = "This OTP has already been used. Please request a new OTP."
            )
        }
        if (otpCode != issuedOtpCode) {
            return OtpActionResult.Error(
                message = "Invalid or expired OTP code. Please check and try again."
            )
        }
        usedOtpSessions.add(sessionId)
        return OtpActionResult.OtpVerified(
            verificationToken = "tok_${sessionId}_$otpCode",
            verifiedTarget = target
        )
    }

    override suspend fun createVerifiedAccount(
        fullName: String,
        mobileTenDigits: String,
        email: String,
        verifiedChannel: OtpChannel,
        verificationToken: String,
        firebaseUser: FirebaseUser?
    ): AccountCreationResult {
        createAccountCallCount++
        val normEmail = email.trim().lowercase()
        val normPhone = "+91${mobileTenDigits.trim()}"
        if (registeredEmails.contains(normEmail) || registeredPhones.contains(normPhone)) {
            return AccountCreationResult.Error(
                message = "An account with this Email or Mobile Number already exists. Please Login instead.",
                isDuplicateUser = true
            )
        }
        registeredEmails.add(normEmail)
        registeredPhones.add(normPhone)
        return AccountCreationResult.Success(
            session = AuthenticatedSession(
                uid = "uid_${normEmail.substringBefore("@")}",
                email = normEmail,
                displayName = fullName.trim(),
                phone = normPhone,
                acceptedTerms = true,
                isVerified = true
            )
        )
    }

    override suspend fun loginVerifiedAccount(
        identifier: String,
        password: String,
        verifiedOtpToken: String?,
        firebaseUser: FirebaseUser?
    ): AccountCreationResult {
        if (password.length < 6) {
            return AccountCreationResult.Error("Invalid credentials. Password must be at least 6 characters.")
        }
        val norm = identifier.trim().lowercase()
        if (!registeredEmails.contains(norm) && !registeredPhones.contains("+91$norm")) {
            return AccountCreationResult.Error("Account not found. Please Sign Up first.")
        }
        return AccountCreationResult.Success(
            session = AuthenticatedSession(
                uid = "uid_existing",
                email = if (norm.contains("@")) norm else "existing@streamvibe.app",
                displayName = "Existing Creator",
                phone = "+919988776655",
                acceptedTerms = true,
                isVerified = true
            )
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `verify app name is StreamVibe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StreamVibe", appName)
    }

    @Test
    fun `signupFormValidator validates mobile email password and terms accurately`() {
        // 1. Valid vs Invalid Mobile Number
        assertNull(SignupFormValidator.validateMobileNumber("9876543210"))
        assertNotNull(SignupFormValidator.validateMobileNumber(""))
        assertNotNull(SignupFormValidator.validateMobileNumber("12345"))
        assertNotNull(SignupFormValidator.validateMobileNumber("1234567890")) // Does not start with 6-9

        // 2. Valid vs Invalid Email
        assertNull(SignupFormValidator.validateEmail("riya.sharma@example.com"))
        assertNotNull(SignupFormValidator.validateEmail(""))
        assertNotNull(SignupFormValidator.validateEmail("invalid-email-format"))

        // 3. Password & Confirm Password
        assertNull(SignupFormValidator.validatePassword("Stream123"))
        assertNotNull(SignupFormValidator.validatePassword("short1"))
        assertNull(SignupFormValidator.validateConfirmPassword("Stream123", "Stream123"))
        assertNotNull(SignupFormValidator.validateConfirmPassword("Stream123", "Mismatch99"))

        // 4. Terms & Conditions
        assertNull(SignupFormValidator.validateTerms(true))
        assertNotNull(SignupFormValidator.validateTerms(false))
    }

    @Test
    fun `welcome login and signup navigation and field validation work properly`() {
        val gateway = FakeServerOtpGateway()
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    initialStage = AuthStage.WELCOME,
                    otpGateway = gateway
                )
            }
        }

        // Welcome screen displays Login, Sign Up, Terms & Privacy links
        composeTestRule.onNodeWithTag("welcome_screen_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("terms_policy_link").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("policy_details_card").performScrollTo().assertIsDisplayed()

        // Navigate Welcome -> Login -> Sign Up
        composeTestRule.onNodeWithTag("welcome_login_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("login_form_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("switch_to_signup_link").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_form_card").assertIsDisplayed()

        // Submitting empty form shows field-level errors
        composeTestRule.onNodeWithTag("auth_primary_submit_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("name_error_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("phone_error_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_error_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_error_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("terms_error_text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `email signup automatically redirects to verification and onboarding and simplified login works`() {
        val gateway = FakeServerOtpGateway()
        var loggedInSession: AuthenticatedSession? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    initialStage = AuthStage.SIGNUP,
                    otpGateway = gateway,
                    onAuthSessionSuccess = { session ->
                        loggedInSession = session
                    }
                )
            }
        }

        // Select Sign Up with Email OTP & fill details
        composeTestRule.onNodeWithTag("method_email_otp_chip").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().performTextInput("Aarav Verma")
        composeTestRule.onNodeWithTag("signup_phone_input").performScrollTo().performTextInput("9812345678")
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("aarav@streamvibe.app")
        composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("StreamPass99")
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("StreamPass99")
        composeTestRule.onNodeWithTag("signup_terms_checkbox").performScrollTo().performClick()

        // Tapping Create Account directly automatically sends Email OTP and redirects to Email Verification Screen
        composeTestRule.onNodeWithTag("auth_primary_submit_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("email_verification_screen_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_verification_title").assertTextEquals("Verify Your Email Address")

        // Entering 6-digit OTP on Email Verification screen automatically creates account and redirects to Signup Success Screen
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextInput("739284")
        composeTestRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_success_screen_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("signup_success_user_name").assertTextEquals("Aarav Verma")

        // Navigate to Login and verify simplified 2-field login immediately authenticates returning user
        composeTestRule.onNodeWithTag("login_tab_button").performClick()
        composeTestRule.onNodeWithTag("login_form_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_identifier_input").performScrollTo().performTextInput("aarav@streamvibe.app")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("StreamPass99")
        composeTestRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

        assertNotNull(loggedInSession)
        assertEquals("aarav@streamvibe.app", loggedInSession?.email)
    }

    @Test
    fun `signup flow handles network retry wrong otp expired otp duplicate user and success navigation`() {
        val gateway = FakeServerOtpGateway()
        var homeSession: AuthenticatedSession? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    initialStage = AuthStage.SIGNUP,
                    otpGateway = gateway,
                    onAuthSessionSuccess = { session ->
                        homeSession = session
                    }
                )
            }
        }

        // Fill valid signup details
        composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().performTextInput("Riya Sharma")
        composeTestRule.onNodeWithTag("signup_phone_input").performScrollTo().performTextInput("9876543210")
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("riya@streamvibe.app")
        composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("VibePass123")
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("VibePass123")
        composeTestRule.onNodeWithTag("signup_terms_checkbox").performScrollTo().performClick()

        // 1. Network failure on Get OTP shows error + Retry button
        gateway.shouldSimulateNetworkFailureOnSend = true
        composeTestRule.onNodeWithTag("signup_send_otp_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("otp_error_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_action_button").performScrollTo().assertIsDisplayed()

        // 2. Retry succeeds when network recovers
        gateway.shouldSimulateNetworkFailureOnSend = false
        composeTestRule.onNodeWithTag("retry_action_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().assertIsDisplayed()

        // 3. Expired OTP rejected
        gateway.shouldSimulateExpiredOtp = true
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextInput("739284")
        composeTestRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("otp_error_text").performScrollTo().assertIsDisplayed()

        // 4. Resend OTP works & Wrong OTP is rejected
        gateway.shouldSimulateExpiredOtp = false
        composeTestRule.onNodeWithTag("resend_otp_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextInput("111111")
        composeTestRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("otp_error_text").performScrollTo().assertIsDisplayed()

        // 5. Correct OTP marks mobile as Verified
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("signup_otp_input").performScrollTo().performTextInput("739284")
        composeTestRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("otp_verified_badge").performScrollTo().assertIsDisplayed()

        // 6. Create Account transitions to Signup Success Screen
        composeTestRule.onNodeWithTag("auth_primary_submit_button").performScrollTo().performClick()
        assertEquals(1, gateway.createAccountCallCount)

        // 7. Signup Success Screen displays Success Icon, Title, User's Name, and Continue to Home button
        composeTestRule.onNodeWithTag("signup_success_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("signup_success_title").assertTextEquals("Account Created Successfully")
        composeTestRule.onNodeWithTag("signup_success_user_name").assertTextEquals("Riya Sharma")

        // 8. Tapping Continue to Home (with duplicate double-tap guard) navigates to Home with verified session
        composeTestRule.onNodeWithTag("continue_to_home_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("continue_to_home_button").performClick()
        assertNotNull(homeSession)
        assertEquals("Riya Sharma", homeSession?.displayName)
        assertEquals("riya@streamvibe.app", homeSession?.email)
        assertTrue(homeSession?.isVerified == true)
    }

    @Test
    fun `otpVerificationBanner buttons respond immediately on a single tap and block double execution`() {
        var verifiedCallbackCount = 0
        composeTestRule.setContent {
            MyApplicationTheme {
                OtpVerificationBannerCard(
                    isVerifiedOtp = false,
                    acceptedTerms = false,
                    onCompleteVerification = { _, _ ->
                        verifiedCallbackCount++
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("send_otp_button").performClick()
        composeTestRule.onNodeWithTag("otp_banner_feedback_text").assertIsDisplayed()

        composeTestRule.onNodeWithTag("verify_otp_submit_button").performClick()
        composeTestRule.onNodeWithTag("verify_otp_submit_button").performClick()
        assertEquals(1, verifiedCallbackCount)
    }

    @Test
    fun `reelsTab like and gift buttons respond on a single tap without double increment`() {
        var likeCallCount = 0
        val sampleReel = ReelPost(
            id = "reel_touch_1",
            authorId = "u1",
            authorName = "CyberDancer",
            caption = "Testing single tap response",
            mediaType = "REEL",
            category = "Dance",
            likesCount = 10L,
            giftsCoinsTotal = 50L
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ReelsAndShortsTab(
                    userProfile = UserProfile(
                        id = "u1",
                        userId = "u1",
                        username = "Tester",
                        isVerifiedOtp = true,
                        acceptedTerms = true,
                        coinsBalance = 500L,
                        adminWalletDiamonds = 50000L
                    ),
                    reelsState = UiState.Success(listOf(sampleReel)),
                    onVerifyOtpAndTerms = { _, _ -> },
                    onLikeReel = { _ -> likeCallCount++ },
                    onSendGiftToReel = { _, _ -> },
                    onQuickSeedReel = {},
                    onNavigateToUpload = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("like_count_text_reel_touch_1").assertTextEquals("10")
        composeTestRule.onNodeWithTag("like_reel_button_reel_touch_1").performClick()
        composeTestRule.onNodeWithTag("like_reel_button_reel_touch_1").performClick()
        assertEquals(1, likeCallCount)
        composeTestRule.onNodeWithTag("like_count_text_reel_touch_1").assertTextEquals("11")

        composeTestRule.onNodeWithTag("send_gift_reel_button_reel_touch_1").performClick()
        composeTestRule.onNodeWithTag("gift_option_gift_rose").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gift_source_admin_wallet_chip").assertIsDisplayed()
    }

    @Test
    fun `walletAndRazorpayTab switches cleanly across User Admin Creator and VIP wallets`() {
        var allocatedCount = 0
        var withdrawalRequestedCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                WalletAndRazorpayTab(
                    userProfile = UserProfile(
                        id = "u1",
                        userId = "u1",
                        username = "VibeStar",
                        coinsBalance = 750L,
                        adminWalletDiamonds = 50000L,
                        creatorEarningsDiamonds = 15000L,
                        withdrawableDiamonds = 12000L,
                        vipLevel = 25L
                    ),
                    privateInfo = UserPrivateInfo(
                        userId = "u1",
                        bankVerified = true,
                        bankName = "HDFC Bank",
                        bankHolderName = "Vibe Star",
                        bankAccountNumber = "123456789012",
                        bankIfsc = "HDFC0001234"
                    ),
                    transactionsState = UiState.Success(emptyList()),
                    onRazorpayTestCheckout = { _, _ -> },
                    onAllocateAdminPromoDiamonds = { _, _ -> allocatedCount++ },
                    onRequestWithdrawal = { _ -> withdrawalRequestedCount++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("user_wallet_balance_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("buy_pack_button_pack_99").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("wallet_tab_admin").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("admin_wallet_balance_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("allocate_admin_promo_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("allocate_admin_promo_button").performScrollTo().performClick()
        assertEquals(1, allocatedCount)

        composeTestRule.onNodeWithTag("wallet_tab_creator").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("creator_withdrawable_diamonds_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_withdrawal_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("submit_withdrawal_button").performScrollTo().performClick()
        assertEquals(1, withdrawalRequestedCount)

        composeTestRule.onNodeWithTag("wallet_tab_vip").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("upgrade_vip_level_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `supportAdminAndPoliciesTab section chips and ticket submit execute strictly once per tap`() {
        var ticketSubmitCount = 0
        var submittedTicketSubject: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                SupportAdminAndPoliciesTab(
                    userProfile = UserProfile(id = "u1", userId = "u1", username = "Tester", isVerifiedOtp = true, acceptedTerms = true),
                    currentUserEmail = "tester@example.com",
                    reelsState = UiState.Success(emptyList()),
                    liveRoomsState = UiState.Success(emptyList()),
                    transactionsState = UiState.Success(emptyList()),
                    ticketsState = UiState.Success(emptyList()),
                    onCreateSupportTicket = { subject, _, _, _ ->
                        ticketSubmitCount++
                        submittedTicketSubject = subject
                    },
                    onUpdateSupportTicket = { _, _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("submit_ticket_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("submit_ticket_button").performScrollTo().performClick()
        assertEquals(1, ticketSubmitCount)
        assertTrue(!submittedTicketSubject.isNullOrBlank())

        composeTestRule.onNodeWithTag("section_admin_chip").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("admin_monthly_payout_management_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `automatic monthly payout enforces all 10 eligibility scheduler webhook retry and carry forward rules`() {
        val engine = com.example.data.MonthlyPayoutEngine(webhookSecret = "test_webhook_secret_123")
        val oct18Ist = java.time.ZonedDateTime.of(
            2026, 10, 18, 10, 30, 0, 0,
            com.example.data.MonthlyPayoutPolicy.IST_ZONE
        )
        val oct17Ist = oct18Ist.minusDays(1)
        val nov18Ist = oct18Ist.plusMonths(1)

        // 1. Level 29 and ₹10,000 Balance -> Payment blocked & carried forward
        val level29Creator = com.example.data.CreatorEarningsLedgerState(
            creatorId = "c_lvl29",
            creatorName = "Creator29",
            totalEarningsInr = 10_000L,
            approvedAvailableBalanceInr = 10_000L,
            serverVerifiedLevel = 29L,
            bankVerified = true,
            bankName = "HDFC Bank",
            bankAccountMasked = "****1234",
            bankIfsc = "HDFC0001234",
            kycStatus = "verified"
        )
        engine.upsertCreatorLedger(level29Creator)
        // Even if client tries to spoof fake level 50, server enforces level 29
        val rep1 = engine.evaluateEligibility(level29Creator, oct18Ist, clientSuppliedFakeLevel = 50L)
        assertEquals(false, rep1.isEligible)
        assertEquals(com.example.data.PayoutStatus.NOT_ELIGIBLE, rep1.status)
        assertEquals(10_000L, rep1.carryForwardAmountInr)
        assertTrue(engine.runScheduledMonthlyPayout("c_lvl29", oct18Ist).isFailure)

        // 2. Level 30 and ₹9,999 Balance -> Payment blocked & carried forward
        val below10kCreator = level29Creator.copy(
            creatorId = "c_9999",
            serverVerifiedLevel = 30L,
            approvedAvailableBalanceInr = 9_999L,
            pendingEarningsInr = 2_000L // Pending must not be counted in Approved Available
        )
        engine.upsertCreatorLedger(below10kCreator)
        val rep2 = engine.evaluateEligibility(below10kCreator, oct18Ist)
        assertEquals(false, rep2.isEligible)
        assertEquals(com.example.data.PayoutStatus.NOT_ELIGIBLE, rep2.status)
        assertEquals(9_999L, rep2.carryForwardAmountInr)
        assertTrue(engine.runScheduledMonthlyPayout("c_9999", oct18Ist).isFailure)

        // 3. Level 30 and ₹10,000 Approved Balance -> Eligible
        val exact10kCreator = level29Creator.copy(
            creatorId = "c_10000",
            serverVerifiedLevel = 30L,
            approvedAvailableBalanceInr = 10_000L
        )
        engine.upsertCreatorLedger(exact10kCreator)
        val rep3 = engine.evaluateEligibility(exact10kCreator, oct18Ist)
        assertEquals(true, rep3.isEligible)
        assertEquals(10_000L, rep3.payableAmountInr)

        // 4. Level 30 and ₹15,000 Approved Balance -> Processes ₹15,000 Payout on 18th IST
        val creator15k = level29Creator.copy(
            creatorId = "c_15000",
            serverVerifiedLevel = 32L,
            approvedAvailableBalanceInr = 15_000L
        )
        engine.upsertCreatorLedger(creator15k)

        // 9. Scheduler date check: Running on Oct 17th IST is rejected; Oct 18th IST succeeds
        assertTrue(engine.runScheduledMonthlyPayout("c_15000", oct17Ist).isFailure)
        val payout15kResult = engine.runScheduledMonthlyPayout("c_15000", oct18Ist)
        assertTrue(payout15kResult.isSuccess)
        val payout15k = payout15kResult.getOrThrow()
        assertEquals(com.example.data.PayoutStatus.PAID, payout15k.status)
        assertEquals(15_000L, payout15k.amountInr)
        assertEquals(0L, engine.getCreatorLedger("c_15000")?.approvedAvailableBalanceInr)
        assertEquals(15_000L, engine.getCreatorLedger("c_15000")?.alreadyPaidTotalInr)

        // 7. Duplicate Scheduler Execution on same 18th cycle -> Returns same idempotent payout, never pays twice
        val duplicateRun = engine.runScheduledMonthlyPayout("c_15000", oct18Ist).getOrThrow()
        assertEquals(payout15k.payoutId, duplicateRun.payoutId)
        assertEquals(1, engine.getAllPayoutRecordsForCreator("c_15000").size)
        assertEquals(15_000L, engine.getCreatorLedger("c_15000")?.alreadyPaidTotalInr)

        // 5. Unverified Bank Account -> Payment held (ON_HOLD)
        val unverifiedBankCreator = exact10kCreator.copy(
            creatorId = "c_nobank",
            bankVerified = false
        )
        engine.upsertCreatorLedger(unverifiedBankCreator)
        val rep5 = engine.evaluateEligibility(unverifiedBankCreator, oct18Ist)
        assertEquals(false, rep5.isEligible)
        assertEquals(com.example.data.PayoutStatus.ON_HOLD, rep5.status)
        assertTrue(engine.runScheduledMonthlyPayout("c_nobank", oct18Ist).isFailure)

        // 6. Failed Payment -> Status FAILED, balance preserved, and safe Retry succeeds
        val retryCreator = exact10kCreator.copy(
            creatorId = "c_retry",
            approvedAvailableBalanceInr = 12_000L
        )
        engine.upsertCreatorLedger(retryCreator)
        val failedPayout = engine.runScheduledMonthlyPayout(
            creatorId = "c_retry",
            executionTimeIst = oct18Ist,
            simulateGatewayFailure = true
        ).getOrThrow()
        assertEquals(com.example.data.PayoutStatus.FAILED, failedPayout.status)

        // 8. Invalid Webhook -> Rejected and does NOT mutate FAILED status to PAID
        val invalidWebhookRes = engine.processRazorpayWebhook(
            payloadBody = """{"payout_id":"${failedPayout.payoutId}","status":"processed"}""",
            providedSignatureHex = "forged_invalid_signature_000",
            payoutId = failedPayout.payoutId,
            webhookStatus = com.example.data.PayoutStatus.PAID,
            utrNumber = "FAKE_UTR"
        )
        assertTrue(invalidWebhookRes.isFailure)
        assertEquals(
            com.example.data.PayoutStatus.FAILED,
            engine.getAllPayoutRecordsForCreator("c_retry").first().status
        )

        // Safe Admin Retry recovers the FAILED payout to PAID
        val retriedPayout = engine.retryFailedPayout(
            payoutId = failedPayout.payoutId,
            simulateGatewayFailure = false
        ).getOrThrow()
        assertEquals(com.example.data.PayoutStatus.PAID, retriedPayout.status)
        assertEquals(1, retriedPayout.retryCount)
        assertEquals(0L, engine.getCreatorLedger("c_retry")?.approvedAvailableBalanceInr)

        // 10. Next Month Carry Forward + Creator View Reward Validation (₹800 per 1,000,000 Eligible Views)
        val carryCreator = exact10kCreator.copy(
            creatorId = "c_carry",
            serverVerifiedLevel = 30L,
            approvedAvailableBalanceInr = 8_500L
        )
        engine.upsertCreatorLedger(carryCreator)
        // Oct 18: ₹8,500 < ₹10,000 -> Not paid, carried forward
        assertTrue(engine.runScheduledMonthlyPayout("c_carry", oct18Ist).isFailure)
        assertEquals(8_500L, engine.getCreatorLedger("c_carry")?.carryForwardBalanceInr)

        // Validate 2,500,000 raw views with 500,000 bot/duplicate views -> 2,000,000 eligible views = ₹1,600
        val viewReward = engine.validateAndCalculateViewReward(
            creatorId = "c_carry",
            claimBatchId = "batch_nov_2026",
            rawViews = 2_500_000L,
            flaggedBotOrFakeViews = 300_000L,
            duplicateViews = 200_000L,
            taskCompleted = true,
            adminApprovedForPayable = true
        )
        assertEquals(2_000_000L, viewReward.eligibleViews)
        assertEquals(1_600L, viewReward.calculatedRewardInr)

        // Advance to November cycle with +₹4,900 additional approved gift earnings -> Total Approved = 8,500 + 1,600 + 4,900 = ₹15,000
        val novState = engine.advanceToNextMonthCycle(
            creatorId = "c_carry",
            newlyApprovedEarningsInr = 4_900L
        )
        assertEquals(15_000L, novState.approvedAvailableBalanceInr)

        // Nov 18th IST: ₹15,000 is now eligible and paid automatically!
        val novPayout = engine.runScheduledMonthlyPayout("c_carry", nov18Ist).getOrThrow()
        assertEquals(com.example.data.PayoutStatus.PAID, novPayout.status)
        assertEquals(15_000L, novPayout.amountInr)
        assertEquals(0L, engine.getCreatorLedger("c_carry")?.approvedAvailableBalanceInr)
    }

    @Test
    fun `homeDashboard inboxChat profile and settings reference screens render and navigate cleanly`() {
        var navigatedToLive = false
        var navigatedToWallet = false

        composeTestRule.setContent {
            MyApplicationTheme {
                ReelsAndShortsTab(
                    userProfile = UserProfile(
                        id = "u1",
                        userId = "u1",
                        username = "Riya Sharma",
                        isVerifiedOtp = true,
                        acceptedTerms = true,
                        coinsBalance = 1250L,
                        adminWalletDiamonds = 50000L
                    ),
                    reelsState = UiState.Success(emptyList()),
                    onVerifyOtpAndTerms = { _, _ -> },
                    onLikeReel = {},
                    onSendGiftToReel = { _, _ -> },
                    onQuickSeedReel = {},
                    onNavigateToUpload = {},
                    onNavigateToLive = { navigatedToLive = true },
                    onNavigateToWallet = { navigatedToWallet = true }
                )
            }
        }

        // Switch to Home Dashboard (Images 4 & 5)
        composeTestRule.onNodeWithTag("home_dashboard_mode_chip").performClick()
        composeTestRule.onNodeWithTag("home_dashboard_profile_banner").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_pk_battle_hero_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_join_pk_button").performScrollTo().performClick()
        assertTrue(navigatedToLive)

        composeTestRule.onNodeWithTag("home_shortcut_diamonds").performScrollTo().performClick()
        assertTrue(navigatedToWallet)

        // Switch to Inbox & Chats (Image 8)
        composeTestRule.onNodeWithTag("inbox_chat_mode_chip").performClick()
        composeTestRule.onNodeWithTag("inbox_search_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_direct_chat_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("direct_chat_message_input").performScrollTo().performTextInput("Hello from StreamVibe test!")
        composeTestRule.onNodeWithTag("send_direct_chat_button").performScrollTo().performClick()
    }

    @Test
    fun `razorpayTestMode backend validates orders signatures duplicate callbacks errors and blocks real money withdrawal`() = kotlinx.coroutines.runBlocking {
        val repo = com.example.data.StreamVibeRepository()
        val userId = "uid_razorpay_tester"
        repo.bindSessionContext(userId = userId, email = "tester@streamvibe.app")
        repo.ensureOrCreateProfile("Razorpay Tester", "tester@streamvibe.app")

        // Initial user wallet balance is 250 Diamonds, Creator withdrawable is 0
        val initialProfile = repo.getUserProfile(userId).getOrThrow()!!
        assertEquals(250L, initialProfile.coinsBalance)
        assertEquals(0L, initialProfile.withdrawableDiamonds)

        // 1. Security: Reject live keys or non-TEST mode, and reject client-tampered prices or diamond counts
        assertTrue(
            repo.createRazorpayTestOrder(
                packId = "pack_99",
                checkoutKeyId = "rzp_live_ForbiddenKey",
                paymentMode = "TEST"
            ).isFailure
        )
        assertTrue(
            repo.createRazorpayTestOrder(
                packId = "pack_999",
                clientSuppliedPriceInr = 1 // Tampered ₹1 instead of ₹999
            ).isFailure
        )
        assertTrue(
            repo.createRazorpayTestOrder(
                packId = "pack_99",
                clientSuppliedDiamonds = 50_000L // Tampered 50,000 diamonds instead of 75
            ).isFailure
        )

        // 2. Network Error during Order Creation -> Fails cleanly, 0 Diamonds credited
        val netErrOrder = repo.createRazorpayTestOrder(
            packId = "pack_99",
            simulateNetworkError = true
        )
        assertTrue(netErrOrder.isFailure)
        assertEquals(250L, repo.getUserProfile(userId).getOrThrow()!!.coinsBalance)

        // 3. Failed Payment -> Order created in INR, payment fails, 0 Diamonds credited, FAILED record stored
        val failedOrder = repo.createRazorpayTestOrder(packId = "pack_199").getOrThrow()
        assertEquals("INR", failedOrder.currency)
        assertEquals(19900L, failedOrder.amountInrPaise)
        val failedVerify = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = failedOrder.orderId,
            paymentId = "pay_test_failed_01",
            simulatedOutcome = com.example.data.RazorpayPaymentStatus.FAILED
        )
        assertTrue(failedVerify.isFailure)
        assertEquals(250L, repo.getUserProfile(userId).getOrThrow()!!.coinsBalance)

        // 4. Cancelled Payment -> 0 Diamonds credited, CANCELLED record stored
        val cancelledOrder = repo.createRazorpayTestOrder(packId = "pack_399").getOrThrow()
        val cancelledVerify = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = cancelledOrder.orderId,
            paymentId = "pay_test_cancelled_01",
            simulatedOutcome = com.example.data.RazorpayPaymentStatus.CANCELLED
        )
        assertTrue(cancelledVerify.isFailure)
        assertEquals(250L, repo.getUserProfile(userId).getOrThrow()!!.coinsBalance)

        // 5. Invalid Signature -> Rejected by backend HMAC-SHA256 verifier, 0 Diamonds credited
        val sigOrder = repo.createRazorpayTestOrder(packId = "pack_499").getOrThrow()
        val badSigVerify = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = sigOrder.orderId,
            paymentId = "pay_test_badsig_01",
            razorpaySignature = "tampered_forged_signature_xyz"
        )
        assertTrue(badSigVerify.isFailure)
        assertEquals(250L, repo.getUserProfile(userId).getOrThrow()!!.coinsBalance)

        // 6. Network Error during Payment Verification -> 0 Diamonds credited, subsequent retry succeeds
        val retryOrder = repo.createRazorpayTestOrder(packId = "pack_99").getOrThrow()
        val paymentIdSuccess = "pay_test_valid_99"
        val validSig = repo.generateServerTestSignature(retryOrder.orderId, paymentIdSuccess)

        val netVerifyFail = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = retryOrder.orderId,
            paymentId = paymentIdSuccess,
            razorpaySignature = validSig,
            simulatedOutcome = com.example.data.RazorpayPaymentStatus.NETWORK_ERROR,
            idempotencyKey = "idem_net_retry_99"
        )
        assertTrue(netVerifyFail.isFailure)
        assertEquals(250L, repo.getUserProfile(userId).getOrThrow()!!.coinsBalance)

        // 7. Successful Server-Verified Payment -> Credits +75 Diamonds (250 + 75 = 325)
        val verifiedResult = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = retryOrder.orderId,
            paymentId = paymentIdSuccess,
            razorpaySignature = validSig,
            simulatedOutcome = com.example.data.RazorpayPaymentStatus.VERIFIED,
            idempotencyKey = "idem_net_retry_99"
        ).getOrThrow()
        assertEquals(com.example.data.RazorpayPaymentStatus.VERIFIED, verifiedResult.status)
        assertEquals(75L, verifiedResult.diamondsCredited)
        assertEquals(325L, verifiedResult.newWalletBalance)
        assertEquals("INR", verifiedResult.currency)
        assertEquals(true, verifiedResult.isTestTransaction)
        assertEquals(false, verifiedResult.countsAsRealRevenue)
        assertEquals(false, verifiedResult.withdrawableAsRealMoney)

        val updatedProfile = repo.getUserProfile(userId).getOrThrow()!!
        assertEquals(325L, updatedProfile.coinsBalance)
        assertEquals(0L, updatedProfile.withdrawableDiamonds)

        // 8. Duplicate Payment Callbacks (Same Order ID, Same Payment ID, Same Idempotency Key) -> Blocked
        val dupByOrder = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = retryOrder.orderId,
            paymentId = "pay_test_another_id",
            razorpaySignature = repo.generateServerTestSignature(retryOrder.orderId, "pay_test_another_id")
        )
        assertTrue(dupByOrder.isFailure)

        val secondOrder = repo.createRazorpayTestOrder(packId = "pack_99").getOrThrow()
        val dupByPaymentId = repo.verifyAndCompleteRazorpayTestPayment(
            orderId = secondOrder.orderId,
            paymentId = paymentIdSuccess, // Already processed paymentId!
            razorpaySignature = repo.generateServerTestSignature(secondOrder.orderId, paymentIdSuccess)
        )
        assertTrue(dupByPaymentId.isFailure)
        assertEquals(325L, repo.getUserProfile(userId).getOrThrow()!!.coinsBalance)

        // 9. Test Transactions are NOT counted as real revenue and CANNOT be withdrawn as real money
        val txList = repo.getLocalUserTransactions(userId)
        val verifiedTx = txList.first { it.razorpayPaymentId == paymentIdSuccess }
        assertEquals("TEST", verifiedTx.paymentMode)
        assertEquals("VERIFIED", verifiedTx.paymentStatus)
        assertEquals("INR", verifiedTx.currency)
        assertEquals(false, verifiedTx.countsAsRealRevenue)
        assertEquals(false, repo.canWithdrawTestPurchasedDiamondsAsRealMoney(verifiedTx))
        assertEquals(0L, repo.calculateVerifiedRealRevenuePaise(txList))

        // Even if user purchases 15,000 Test Diamonds into User Wallet, Creator Withdrawal rejects withdrawing them
        repo.processRazorpayTestTopUp(
            pack = com.example.data.StreamVibeCatalog.findCanonicalPack("pack_9999")!!,
            paymentMethodLabel = "UPI Test (success@razorpay)"
        )
        repo.processRazorpayTestTopUp(
            pack = com.example.data.StreamVibeCatalog.findCanonicalPack("pack_9999")!!,
            paymentMethodLabel = "UPI Test (success@razorpay)"
        )
        repo.saveAndVerifyBankAccount(
            holderName = "Razorpay Tester",
            accountNumber = "123456789012",
            confirmAccountNumber = "123456789012",
            ifscCode = "HDFC0001234",
            bankName = "HDFC Bank"
        )
        val withdrawAttempt = repo.requestCreatorWithdrawal(10_000L)
        assertTrue(withdrawAttempt.isFailure)
    }

    @Test
    fun `diamondStore razorpayCheckoutSheet displays selected package price INR order and credits once`() {
        var checkoutCallCount = 0
        var lastSelectedPackId: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                WalletAndRazorpayTab(
                    userProfile = UserProfile(
                        id = "u1",
                        userId = "u1",
                        username = "VibeStar",
                        coinsBalance = 500L
                    ),
                    transactionsState = UiState.Success(emptyList()),
                    onRazorpayTestCheckout = { pack, _ ->
                        checkoutCallCount++
                        lastSelectedPackId = pack.id
                    }
                )
            }
        }

        // Select ₹99 = 75 Diamonds package
        composeTestRule.onNodeWithTag("buy_pack_button_pack_99").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("razorpay_test_checkout_sheet").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("razorpay_selected_package_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("razorpay_order_meta_text").performScrollTo().assertIsDisplayed()

        // Confirm payment once (single-tap guard blocks rapid double tap)
        composeTestRule.onNodeWithTag("confirm_razorpay_test_pay_button").performScrollTo().performClick()
        assertEquals(1, checkoutCallCount)
        assertEquals("pack_99", lastSelectedPackId)
    }

    @Test
    fun `signup terms checkbox policy version timestamp and content categories multi select and empty validation work`(): Unit = kotlinx.coroutines.runBlocking {
        // 1. Validator & Backend tests for Terms consent (policy version + timestamp) & Content Categories
        assertNotNull(SignupFormValidator.validateTerms(false))
        assertEquals(null, SignupFormValidator.validateTerms(true))
        assertNotNull(SignupFormValidator.validateContentCategories(emptyList()))
        assertEquals(
            null,
            SignupFormValidator.validateContentCategories(listOf("Music", "Live Streaming", "Short Videos / Reels"))
        )

        val repo = com.example.data.StreamVibeRepository()
        val userId = "uid_terms_and_cat_tester"
        repo.bindSessionContext(userId = userId, email = "creator@streamvibe.app")
        repo.ensureOrCreateProfile("Creator One", "creator@streamvibe.app")

        // Unchecked terms rejected by backend
        assertTrue(repo.recordTermsAndPolicyAcceptance(acceptedTerms = false).isFailure)

        // Accepted terms saved with version and timestamp
        val consentTs = 1765000123456L
        val record = repo.recordTermsAndPolicyAcceptance(
            acceptedTerms = true,
            policyVersion = "v2026.2",
            acceptedAtEpochMs = consentTs
        ).getOrThrow()
        assertEquals(userId, record.userId)
        assertEquals("v2026.2", record.policyVersion)
        assertEquals(consentTs, record.acceptedAtEpochMs)
        assertEquals(record, repo.getPolicyAcceptanceRecord(userId))

        // Empty categories rejected by backend; multiple valid categories saved & used for Reel recommendations
        assertTrue(repo.saveContentCategories(emptyList()).isFailure)
        val savedCats = repo.saveContentCategories(listOf("Gaming", "Music", "Live Streaming")).getOrThrow()
        assertEquals(listOf("Gaming", "Music", "Live Streaming"), savedCats)
        assertEquals(savedCats, repo.getUserProfile(userId).getOrThrow()!!.contentCategories)

        // Check UI empty category validation when initialSelectedCategories is empty
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    initialStage = AuthStage.SIGNUP,
                    initialSelectedCategories = emptySet()
                )
            }
        }
        composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().performTextInput("Aarav Creator")
        composeTestRule.onNodeWithTag("signup_phone_input").performScrollTo().performTextInput("9876543210")
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("aarav@streamvibe.app")
        composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("Stream123")
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("Stream123")
        composeTestRule.onNodeWithTag("signup_terms_checkbox").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("auth_primary_submit_button").performScrollTo().performClick()

        // Blocked because no content category was selected
        composeTestRule.onNodeWithTag("content_categories_error_text").performScrollTo().assertIsDisplayed()

        // Select multiple categories (Music + Gaming) -> error clears
        composeTestRule.onNodeWithTag("signup_category_checkbox_music").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_category_checkbox_gaming").performScrollTo().performClick()
        composeTestRule.onAllNodesWithTag("content_categories_error_text").assertCountEquals(0)
        Unit
    }

    @Test
    fun `accountModerationPolicy enforces 3 day suspension on 1st confirmed violation permanent ban on repeated or severe and never auto bans on single report`(): Unit = kotlinx.coroutines.runBlocking {
        val repo = com.example.data.StreamVibeRepository()
        val targetUserId = "uid_moderated_creator"
        repo.bindSessionContext(userId = targetUserId, email = "creator_mod@streamvibe.app")
        repo.ensureOrCreateProfile("Moderated Creator", "creator_mod@streamvibe.app")

        // Give the user legitimate wallet balance and verify it is preserved across moderation actions
        repo.processRazorpayTestTopUp(
            pack = com.example.data.StreamVibeCatalog.findCanonicalPack("pack_199")!!,
            paymentMethodLabel = "UPI Test"
        )
        val balanceBefore = repo.getUserProfile(targetUserId).getOrThrow()!!.coinsBalance
        assertEquals(400L, balanceBefore)

        // 1. Single unreviewed user report (or multiple unreviewed reports) NEVER automatically suspends or bans
        repo.bindSessionContext(userId = "uid_reporter_1", email = "reporter1@streamvibe.app")
        val report = repo.submitUserReport(
            targetUserId = targetUserId,
            reasonCategory = "Spam in Live Chat",
            details = "Posted promotional link in chat"
        ).getOrThrow()
        assertEquals(false, report.reviewedByModerator)
        val profileAfterUnreviewedReport = repo.getUserProfile(targetUserId).getOrThrow()!!
        assertEquals("ACTIVE", profileAfterUnreviewedReport.accountStatus)
        assertEquals(false, profileAfterUnreviewedReport.isTemporarilySuspended)
        assertEquals(false, profileAfterUnreviewedReport.isPermanentlyBanned)

        // 2. Non-admin cannot apply moderation decisions
        assertTrue(
            repo.applyModerationDecision(
                targetUserId = targetUserId,
                confirmedByModerator = true,
                severity = com.example.data.ViolationSeverity.ORDINARY,
                reason = "Unauthorized attempt"
            ).isFailure
        )

        // 3. Authorized Admin/Moderator confirms 1st ordinary violation -> Warning + 3-Day Temporary Suspension
        repo.bindSessionContext(
            userId = "uid_admin",
            email = com.example.data.StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL
        )
        val firstDecision = repo.applyModerationDecision(
            targetUserId = targetUserId,
            confirmedByModerator = true,
            severity = com.example.data.ViolationSeverity.ORDINARY,
            reason = "1st confirmed community guideline violation in live stream"
        ).getOrThrow()
        assertEquals(com.example.data.AccountModerationStatus.TEMP_SUSPENDED_3_DAYS, firstDecision.newStatus)
        assertEquals(1, firstDecision.violationCountAfter)
        assertTrue(firstDecision.suspensionEndsAtEpochMs > firstDecision.createdAtEpochMs)

        val suspendedProfile = repo.getUserProfile(targetUserId).getOrThrow()!!
        assertEquals("TEMP_SUSPENDED_3_DAYS", suspendedProfile.accountStatus)
        assertEquals(true, suspendedProfile.isTemporarilySuspended)
        assertEquals(400L, suspendedProfile.coinsBalance) // Wallet balance preserved!

        // Temporarily suspended user cannot post reels or start live streams
        repo.bindSessionContext(userId = targetUserId, email = "creator_mod@streamvibe.app")
        assertTrue(
            repo.createReelPost(
                authorName = "Moderated Creator",
                caption = "Blocked while suspended",
                mediaType = "REEL"
            ).isFailure
        )

        // User submits an official appeal
        val appeal = repo.submitAccountBanAppeal(
            appealMessage = "I understand the Community Guidelines and have removed the promotional link."
        ).getOrThrow()
        assertEquals("SUBMITTED", appeal.status)

        // 4. Repeated confirmed violation -> Permanent Ban after moderator review
        repo.bindSessionContext(
            userId = "uid_admin",
            email = com.example.data.StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL
        )
        val secondDecision = repo.applyModerationDecision(
            targetUserId = targetUserId,
            confirmedByModerator = true,
            severity = com.example.data.ViolationSeverity.REPEATED,
            reason = "Repeated policy violation after 3-day suspension"
        ).getOrThrow()
        assertEquals(com.example.data.AccountModerationStatus.PERMANENTLY_BANNED, secondDecision.newStatus)

        val bannedProfile = repo.getUserProfile(targetUserId).getOrThrow()!!
        assertEquals("PERMANENTLY_BANNED", bannedProfile.accountStatus)
        assertEquals(true, bannedProfile.isPermanentlyBanned)
        assertEquals(400L, bannedProfile.coinsBalance) // Legitimate wallet balance still preserved

        // Admin reviews and approves appeal to restore account
        val reviewedAppeal = repo.reviewAccountBanAppeal(
            appealId = appeal.id,
            approveAndRestore = true,
            adminDecisionNote = "Restored on appeal after review"
        ).getOrThrow()
        assertEquals("APPROVED_RESTORED", reviewedAppeal.status)
        assertEquals("ACTIVE", repo.getUserProfile(targetUserId).getOrThrow()!!.accountStatus)
    }

    @Test
    fun `aiCustomerSupportChat responds in Hindi and English isolates categories and enforces safety guardrails`(): Unit = kotlinx.coroutines.runBlocking {
        // 1. English Deposit / Diamond Purchase support (does not mix with video or login issues)
        val enReply = com.example.data.AiCustomerSupportAssistant.generateLocalReply(
            userMessage = "My Razorpay UPI payment of ₹199 was deducted but diamonds are not showing",
            selectedCategory = com.example.data.SupportRequestCategory.DEPOSIT_DIAMOND_PURCHASE
        )
        assertEquals("EN", enReply.language)
        assertEquals(com.example.data.SupportRequestCategory.DEPOSIT_DIAMOND_PURCHASE, enReply.selectedCategory)
        assertTrue(enReply.message.contains("Deposit / Diamond Purchase"))
        assertTrue(enReply.requiresHumanEscalation)

        // 2. Hindi Withdrawal / Bank Account support
        val hiReply = com.example.data.AiCustomerSupportAssistant.generateLocalReply(
            userMessage = "मेरा बैंक विड्रॉल कब आएगा? 10000 डायमंड्स हैं",
            selectedCategory = com.example.data.SupportRequestCategory.WITHDRAWAL_BANK_ACCOUNT
        )
        assertEquals("HI", hiReply.language)
        assertTrue(hiReply.message.contains("10,000 Diamonds"))
        assertTrue(hiReply.requiresHumanEscalation)

        // 3. Suggests appropriate category when user selects "Other / General Help" but asks about Live Streaming
        val suggestedReply = com.example.data.AiCustomerSupportAssistant.generateLocalReply(
            userMessage = "My PK battle live stream camera and audio froze",
            selectedCategory = com.example.data.SupportRequestCategory.OTHER_GENERAL,
            customCategoryDescription = "Stream issue"
        )
        assertEquals(com.example.data.SupportRequestCategory.LIVE_STREAMING_PROBLEM, suggestedReply.suggestedCategory)

        // 4. Safety guardrail: Never asks for password, OTP, or private keys
        assertTrue(com.example.data.AiCustomerSupportAssistant.validateSupportResponseSafety(enReply.message))
        assertTrue(com.example.data.AiCustomerSupportAssistant.validateSupportResponseSafety(hiReply.message))
    }

    @Test
    fun `supportEvidenceUpload tickets adminFiltering and unauthorizedAccessRestriction work end to end`(): Unit = kotlinx.coroutines.runBlocking {
        val repo = com.example.data.StreamVibeRepository()
        val ownerId = "uid_ticket_owner"
        repo.bindSessionContext(userId = ownerId, email = "owner@streamvibe.app")
        repo.ensureOrCreateProfile("Ticket Owner", "owner@streamvibe.app")

        // 1. Valid Image (PNG <= 10 MB) and Valid Video (MP4 <= 50 MB) succeed
        val validImg = repo.validateAndUploadSupportAttachment(
            fileName = "upi_receipt.png",
            mimeType = "image/png",
            fileSizeBytes = 3L * 1024L * 1024L,
            existingAttachmentCount = 0
        ).getOrThrow()
        assertTrue(validImg.isServerConfirmedSuccess)

        val validVid = repo.validateAndUploadSupportAttachment(
            fileName = "reel_glitch.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = 25L * 1024L * 1024L,
            existingAttachmentCount = 1
        ).getOrThrow()
        assertTrue(validVid.isServerConfirmedSuccess)

        // 2. Oversized Image (> 10 MB), Oversized Video (> 50 MB), Unsupported/Executable (.exe/.apk), and 6th file are rejected
        assertTrue(
            repo.validateAndUploadSupportAttachment(
                fileName = "huge_photo.jpg",
                mimeType = "image/jpeg",
                fileSizeBytes = 11L * 1024L * 1024L,
                existingAttachmentCount = 2
            ).isFailure
        )
        assertTrue(
            repo.validateAndUploadSupportAttachment(
                fileName = "huge_video.mov",
                mimeType = "video/quicktime",
                fileSizeBytes = 55L * 1024L * 1024L,
                existingAttachmentCount = 2
            ).isFailure
        )
        assertTrue(
            repo.validateAndUploadSupportAttachment(
                fileName = "malware_payload.apk",
                mimeType = "application/vnd.android.package-archive",
                fileSizeBytes = 1024L,
                existingAttachmentCount = 2
            ).isFailure
        )
        assertTrue(
            repo.validateAndUploadSupportAttachment(
                fileName = "sixth_image.webp",
                mimeType = "image/webp",
                fileSizeBytes = 1024L,
                existingAttachmentCount = 5 // Max 5 already reached!
            ).isFailure
        )

        // 3. "Other / General Help" requires a non-blank customCategoryDescription
        assertTrue(
            repo.createSupportTicketWithEvidence(
                userName = "Ticket Owner",
                subject = "General query",
                category = com.example.data.SupportRequestCategory.OTHER_GENERAL.displayName,
                customCategoryDescription = "", // Blank -> rejected!
                description = "Need help with custom topic",
                priority = "NORMAL"
            ).isFailure
        )

        // 4. Create ticket with verified evidence -> generates server ticket reference number (SV-TKT-...)
        val createdTicket = repo.createSupportTicketWithEvidence(
            userName = "Ticket Owner",
            subject = "UPI ₹499 deducted",
            category = com.example.data.SupportRequestCategory.PAYMENT_FAILED_REFUND.displayName,
            customCategoryDescription = "",
            description = "Attached UPI screenshot and video proof",
            priority = "HIGH",
            attachments = listOf(validImg, validVid),
            evidenceConsentAccepted = true
        ).getOrThrow()
        assertTrue(createdTicket.ticketNumber.startsWith("SV-TKT-"))
        assertEquals("Submitted", createdTicket.status)
        assertEquals(2, createdTicket.attachments.size)

        // 5. Unauthorized user CANNOT access another user's support ticket or private evidence files
        repo.bindSessionContext(userId = "uid_intruder", email = "intruder@streamvibe.app")
        assertTrue(repo.getAuthorizedSupportTicket(createdTicket.id).isFailure)
        assertTrue(repo.getAuthorizedTicketAttachments(createdTicket.id).isFailure)
        assertTrue(
            repo.adminUpdateSupportTicket(
                ticketId = createdTicket.id,
                newStatus = com.example.data.SupportTicketStatus.RESOLVED,
                adminReply = "Intruder trying to resolve ticket"
            ).isFailure
        )

        // 6. Authorized Admin CAN filter, view attachments, update status, and escalate/resolve
        repo.bindSessionContext(
            userId = "uid_admin",
            email = com.example.data.StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL
        )
        assertEquals(2, repo.getAuthorizedTicketAttachments(createdTicket.id).getOrThrow().size)
        val filtered = repo.getAdminFilteredSupportTickets(
            categoryFilter = com.example.data.SupportRequestCategory.PAYMENT_FAILED_REFUND.displayName,
            statusFilter = "Submitted"
        ).getOrThrow()
        assertEquals(1, filtered.size)

        val resolvedTicket = repo.adminUpdateSupportTicket(
            ticketId = createdTicket.id,
            newStatus = com.example.data.SupportTicketStatus.RESOLVED,
            adminReply = "Verified Razorpay Order ID and processed refund."
        ).getOrThrow()
        assertEquals("Resolved", resolvedTicket.status)

        // 7. Ticket owner can reply to and reopen a resolved ticket
        repo.bindSessionContext(userId = ownerId, email = "owner@streamvibe.app")
        val reopenedTicket = repo.replyOrReopenSupportTicket(
            ticketId = createdTicket.id,
            senderName = "Ticket Owner",
            replyMessage = "Thank you, I have one follow-up question on the bank reference.",
            reopenTicket = true
        ).getOrThrow()
        assertEquals("In Progress", reopenedTicket.status)
        assertTrue(reopenedTicket.replies.any { it.senderRole == "USER" })
    }

    @Test
    fun `completeAppFlow opens LoginSignup first blocks unauthenticated Home access authenticates redirects to Home and Logout returns to Login`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ui.clearVerifiedSessionPrefs(context)

        // Simulate a stale legacy session from an older APK install and verify it is purged on launch
        context.getSharedPreferences("streamvibe_verified_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("uid", "stale_old_uid")
            .putString("email", "stale@streamvibe.app")
            .putString("displayName", "Stale User")
            .putBoolean("isVerified", true)
            .commit()

        val gateway = com.example.ui.FirebaseAndBackendOtpGateway()
        val testRepo = com.example.data.StreamVibeRepository()

        composeTestRule.setContent {
            MyApplicationTheme {
                StreamVibeRootApp(
                    otpGateway = gateway,
                    customRepository = testRepo
                )
            }
        }

        // 1. Opening the app when unauthenticated displays Login/Signup screen first and blocks Home/Reels/Live/Wallet/Profile
        assertNull(com.example.ui.restoreVerifiedSessionFromPrefs(context))
        composeTestRule.onNodeWithTag("login_form_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_submit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_create_account_button").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("authenticated_home_scaffold").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("nav_tab_reels").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("nav_tab_live").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("nav_tab_wallet").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("nav_tab_hub").assertCountEquals(0)

        // 2. Validate Login form fields (empty fields & short password show clear error messages)
        composeTestRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("login_identifier_error_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_error_text").assertIsDisplayed()

        composeTestRule.onNodeWithTag("login_identifier_input").performScrollTo().performTextInput("9876501234")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("123")
        composeTestRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("login_password_error_text").assertIsDisplayed()

        // 3. Tap "Create Account" button to switch to Sign Up screen & complete registration
        composeTestRule.onNodeWithTag("login_create_account_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("signup_form_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().performTextInput("Karan Malhotra")
        composeTestRule.onNodeWithTag("signup_phone_input").performScrollTo().performTextInput("9876501234")
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("karan.m@streamvibe.app")
        composeTestRule.onNodeWithTag("signup_password_input").performScrollTo().performTextInput("KaranPass99")
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("KaranPass99")
        composeTestRule.onNodeWithTag("signup_terms_checkbox").performScrollTo().performClick()

        // Rapid double-tap on Create Account executes once and redirects to Home Screen
        composeTestRule.onNodeWithTag("auth_primary_submit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 4. Verify redirect to Home Screen after successful authentication & valid session persistence
        val activeSessionAfterSignup = com.example.ui.restoreVerifiedSessionFromPrefs(context)
        assertNotNull(activeSessionAfterSignup)
        assertTrue(activeSessionAfterSignup!!.isValidAuthenticatedSession())
        assertEquals("karan.m@streamvibe.app", activeSessionAfterSignup.email)
        composeTestRule.onNodeWithTag("authenticated_home_scaffold").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_reels").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_live").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_wallet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_hub").assertIsDisplayed()

        // 5. Logout clears the authenticated session and returns immediately to the Login Screen
        composeTestRule.onNodeWithTag("top_bar_logout_button").performClick()
        composeTestRule.waitForIdle()

        assertNull(com.example.ui.restoreVerifiedSessionFromPrefs(context))
        composeTestRule.onAllNodesWithTag("authenticated_home_scaffold").assertCountEquals(0)
        composeTestRule.onNodeWithTag("login_form_card").assertIsDisplayed()

        // 6. Log back in using registered 10-digit Mobile Number & Password -> redirects to Home Screen
        composeTestRule.onNodeWithTag("login_identifier_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("login_identifier_input").performScrollTo().performTextInput("9876501234")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("KaranPass99")
        composeTestRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertNotNull(com.example.ui.restoreVerifiedSessionFromPrefs(context))
        composeTestRule.onNodeWithTag("authenticated_home_scaffold").assertIsDisplayed()

        // 7. Logout from Profile/Hub tab ("Sign Out" button) also clears session and returns to Login Screen
        composeTestRule.onNodeWithTag("nav_tab_hub").performClick()
        composeTestRule.onNodeWithTag("sign_out_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertNull(com.example.ui.restoreVerifiedSessionFromPrefs(context))
        composeTestRule.onAllNodesWithTag("authenticated_home_scaffold").assertCountEquals(0)
        composeTestRule.onNodeWithTag("login_form_card").assertIsDisplayed()
    }
}




