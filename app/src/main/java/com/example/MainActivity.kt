package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.StreamVibeRepository
import com.example.ui.AuthScreen
import com.example.ui.LiveVideoAndChatTab
import com.example.ui.ReelsAndShortsTab
import com.example.ui.StreamVibeViewModel
import com.example.ui.SupportAdminAndPoliciesTab
import com.example.ui.UiState
import com.example.ui.UploadMediaStudioTab
import com.example.ui.WalletAndRazorpayTab
import com.example.ui.signOutUser
import com.example.ui.theme.DarkStudioBg
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.RoyalGold
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    private var lastTapUpTimeMs: Long = 0L
    private var lastTapUpX: Float = 0f
    private var lastTapUpY: Float = 0f
    private var currentDownX: Float = 0f
    private var currentDownY: Float = 0f
    private var suppressingBounceTap: Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)
        val density = resources.displayMetrics.density
        val sameSpotDistancePx = 56f * density
        val touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val sameSpotBounceWindowMs = 700L
        val globalRapidBounceWindowMs = 380L

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = SystemClock.uptimeMillis()
                val dt = now - lastTapUpTimeMs
                val distFromLastUp = hypot(
                    (ev.rawX - lastTapUpX).toDouble(),
                    (ev.rawY - lastTapUpY).toDouble()
                ).toFloat()
                currentDownX = ev.rawX
                currentDownY = ev.rawY
                val isSameSpotDoubleTap =
                    lastTapUpTimeMs > 0L && dt in 0L..sameSpotBounceWindowMs && distFromLastUp <= sameSpotDistancePx
                val isGlobalRapidDoubleTap =
                    lastTapUpTimeMs > 0L && dt in 0L..globalRapidBounceWindowMs
                if (isSameSpotDoubleTap || isGlobalRapidDoubleTap) {
                    suppressingBounceTap = true
                    return true
                }
                suppressingBounceTap = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (suppressingBounceTap) {
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (suppressingBounceTap) {
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (suppressingBounceTap) {
                    suppressingBounceTap = false
                    return true
                }
                val moveDist = hypot(
                    (ev.rawX - currentDownX).toDouble(),
                    (ev.rawY - currentDownY).toDouble()
                ).toFloat()
                if (moveDist <= touchSlopPx * 1.5f) {
                    lastTapUpTimeMs = SystemClock.uptimeMillis()
                    lastTapUpX = ev.rawX
                    lastTapUpY = ev.rawY
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (suppressingBounceTap) {
                    suppressingBounceTap = false
                    return true
                }
                suppressingBounceTap = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkStudioBg
                ) {
                    StreamVibeRootApp()
                }
            }
        }
    }
}

@Composable
fun StreamVibeRootApp(
    otpGateway: com.example.ui.AuthOtpGateway = remember { com.example.ui.FirebaseAndBackendOtpGateway() },
    initialAuthStage: com.example.ui.AuthStage = com.example.ui.AuthStage.LOGIN,
    customRepository: StreamVibeRepository? = null
) {
    val context = LocalContext.current
    var activeSession by remember {
        com.example.ui.purgeLegacyUnverifiedPrefs(context)
        mutableStateOf(com.example.ui.restoreVerifiedSessionFromPrefs(context))
    }
    var authInitialStage by remember(initialAuthStage) { mutableStateOf(initialAuthStage) }
    var authInitialized by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        com.example.ui.purgeLegacyUnverifiedPrefs(context)
        val restored = com.example.ui.restoreVerifiedSessionFromPrefs(context)
        if (restored != null && restored.isValidAuthenticatedSession()) {
            activeSession = restored
        } else {
            activeSession = null
            runCatching { Firebase.auth.signOut() }
        }
        authInitialized = true

        val authInstance = runCatching { Firebase.auth }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val updated = firebaseAuth.currentUser
            val currentRestored = com.example.ui.restoreVerifiedSessionFromPrefs(context)
            if (updated != null && currentRestored != null && currentRestored.isValidAuthenticatedSession()) {
                if (activeSession?.uid != updated.uid) {
                    val verifiedSession = currentRestored.copy(
                        uid = updated.uid,
                        email = updated.email ?: currentRestored.email,
                        displayName = updated.displayName ?: currentRestored.displayName,
                        firebaseUser = updated
                    )
                    com.example.ui.saveVerifiedSessionToPrefs(context, verifiedSession)
                    activeSession = verifiedSession
                }
            }
        }
        runCatching { authInstance?.addAuthStateListener(listener) }
        onDispose {
            runCatching { authInstance?.removeAuthStateListener(listener) }
        }
    }

    val session = activeSession
    if (!authInitialized || session == null || !session.isValidAuthenticatedSession()) {
        AuthScreen(
            initialStage = authInitialStage,
            otpGateway = otpGateway,
            onAuthSuccess = { signedInUser, signupForm ->
                val verifiedSession = com.example.ui.AuthenticatedSession(
                    uid = signedInUser.uid,
                    email = signupForm?.email?.ifBlank { null } ?: signedInUser.email ?: "abdulbarik80000@gmail.com",
                    displayName = signupForm?.creatorName?.ifBlank { null } ?: signedInUser.displayName ?: "VibeCreator",
                    phone = signupForm?.phone ?: "+919876543210",
                    acceptedTerms = signupForm?.acceptedTerms ?: true,
                    policyVersion = signupForm?.policyVersion ?: com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
                    acceptedTermsAtEpochMs = signupForm?.acceptedTermsAtEpochMs ?: System.currentTimeMillis(),
                    contentCategories = signupForm?.contentCategories ?: listOf("Short Videos / Reels"),
                    isVerified = true,
                    firebaseUser = signedInUser
                )
                com.example.ui.saveVerifiedSessionToPrefs(context, verifiedSession)
                activeSession = verifiedSession
            },
            onAuthSessionSuccess = { newSession ->
                if (newSession.isValidAuthenticatedSession()) {
                    com.example.ui.saveVerifiedSessionToPrefs(context, newSession)
                    activeSession = newSession
                }
            }
        )
    } else {
        StreamVibeAuthenticatedHome(
            currentUserId = session.uid,
            currentUserEmail = session.email,
            currentUserDisplayName = session.displayName,
            initialPhone = session.phone,
            initialAcceptedTerms = session.acceptedTerms,
            initialPolicyVersion = session.policyVersion,
            initialAcceptedTermsEpochMs = session.acceptedTermsAtEpochMs,
            initialContentCategories = session.contentCategories,
            customRepository = customRepository,
            onUserSignedOut = {
                com.example.ui.clearVerifiedSessionPrefs(context)
                authInitialStage = com.example.ui.AuthStage.LOGIN
                activeSession = null
            }
        )
    }
}

enum class MainNavTab {
    REELS,
    UPLOAD,
    LIVE,
    WALLET,
    HUB
}

@Composable
fun StreamVibeAuthenticatedHome(
    currentUserId: String,
    currentUserEmail: String,
    currentUserDisplayName: String,
    initialPhone: String? = null,
    initialAcceptedTerms: Boolean = false,
    initialPolicyVersion: String = com.example.data.StreamVibeCatalog.CURRENT_POLICY_VERSION,
    initialAcceptedTermsEpochMs: Long = System.currentTimeMillis(),
    initialContentCategories: List<String> = emptyList(),
    customRepository: StreamVibeRepository? = null,
    onUserSignedOut: () -> Unit = {}
) {
    if (currentUserId.isBlank()) {
        onUserSignedOut()
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapGuard = com.example.ui.rememberSingleTapGuard()
    var activeTab by remember { mutableStateOf(MainNavTab.REELS) }

    val viewModel: StreamVibeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val repo = customRepository ?: run {
                    val app = this[APPLICATION_KEY]
                    val db = runCatching {
                        val databaseId = app?.getString(R.string.firestore_database_id) ?: "(default)"
                        FirebaseFirestore.getInstance(databaseId)
                    }.getOrNull()
                    StreamVibeRepository(db)
                }
                StreamVibeViewModel(
                    repository = repo,
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName,
                    initialPhone = initialPhone,
                    initialAcceptedTerms = initialAcceptedTerms,
                    initialPolicyVersion = initialPolicyVersion,
                    initialAcceptedTermsEpochMs = initialAcceptedTermsEpochMs,
                    initialContentCategories = initialContentCategories
                )
            }
        }
    )

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val privateInfoState by viewModel.privateInfoState.collectAsStateWithLifecycle()
    val reelsState by viewModel.reelsState.collectAsStateWithLifecycle()
    val liveRoomsState by viewModel.liveRoomsState.collectAsStateWithLifecycle()
    val selectedRoomId by viewModel.selectedLiveRoomId.collectAsStateWithLifecycle()
    val liveChatState by viewModel.liveChatState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val withdrawalsState by viewModel.withdrawalsState.collectAsStateWithLifecycle()
    val adminAuditLogsState by viewModel.adminAuditLogsState.collectAsStateWithLifecycle()
    val ticketsState by viewModel.ticketsState.collectAsStateWithLifecycle()
    val supportChatHistory by viewModel.supportChatHistoryState.collectAsStateWithLifecycle()
    val stagedAttachments by viewModel.stagedEvidenceAttachments.collectAsStateWithLifecycle()
    val banAppeals by viewModel.banAppealsState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val privateInfo = (privateInfoState as? UiState.Success)?.data

    if (activeTab != MainNavTab.REELS) {
        BackHandler {
            activeTab = MainNavTab.REELS
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("authenticated_home_scaffold"),
        containerColor = DarkStudioBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(
                color = DarkSurfaceCard,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        com.example.ui.StreamVibeTriangularLogo(size = 34.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            com.example.ui.StreamVibeBrandWordmark(
                                fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
                                suffixText = ""
                            )
                            Text(
                                text = "VIP Lv.${userProfile?.vipLevel ?: 1} • Live & Reels Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clickable { activeTab = MainNavTab.WALLET }
                                .testTag("top_wallet_balance_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💎 ${userProfile?.coinsBalance ?: 0L}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Wallet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonMagenta,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clickable {
                                    tapGuard.runOnce("top_bar_logout_button") {
                                        signOutUser(
                                            context = context,
                                            onSignOutComplete = { onUserSignedOut() },
                                            scope = scope
                                        )
                                    }
                                }
                                .testTag("top_bar_logout_button")
                        ) {
                            Text(
                                text = "Logout",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceCard
            ) {
                NavigationBarItem(
                    selected = activeTab == MainNavTab.REELS,
                    onClick = { activeTab = MainNavTab.REELS },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Home & Reels") },
                    label = { Text("Home", maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_reels")
                )
                NavigationBarItem(
                    selected = activeTab == MainNavTab.UPLOAD,
                    onClick = { activeTab = MainNavTab.UPLOAD },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Upload") },
                    label = { Text("Upload", maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_upload")
                )
                NavigationBarItem(
                    selected = activeTab == MainNavTab.LIVE,
                    onClick = { activeTab = MainNavTab.LIVE },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = "Live") },
                    label = { Text("Live", maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_live")
                )
                NavigationBarItem(
                    selected = activeTab == MainNavTab.WALLET,
                    onClick = { activeTab = MainNavTab.WALLET },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
                    label = { Text("Wallet", maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_wallet")
                )
                NavigationBarItem(
                    selected = activeTab == MainNavTab.HUB,
                    onClick = { activeTab = MainNavTab.HUB },
                    icon = { Icon(Icons.Default.SupportAgent, contentDescription = "Support & Admin") },
                    label = { Text("Hub", maxLines = 1) },
                    modifier = Modifier.testTag("nav_tab_hub")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBanner != null) {
                statusBanner?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldLive.copy(alpha = 0.2f))
                            .clickable { viewModel.dismissBanner() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("status_banner_box")
                    ) {
                        Text(
                            text = "$msg (Tap to dismiss)",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldLive
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (activeTab) {
                    MainNavTab.REELS -> {
                        ReelsAndShortsTab(
                            userProfile = userProfile,
                            reelsState = reelsState,
                            onVerifyOtpAndTerms = { phone, accepted ->
                                viewModel.completeOtpVerification(phone, accepted)
                            },
                            onLikeReel = { reel -> viewModel.likeReelPost(reel) },
                            onSendGiftToReel = { gift, reel ->
                                viewModel.sendGift(
                                    gift = gift,
                                    targetReel = reel,
                                    useAdminPromoWallet = false
                                )
                            },
                            onSendGiftToReelWithWallet = { gift, reel, useAdminPromo ->
                                viewModel.sendGift(
                                    gift = gift,
                                    targetReel = reel,
                                    useAdminPromoWallet = useAdminPromo
                                )
                            },
                            onQuickSeedReel = {
                                viewModel.publishReelOrMedia(
                                    caption = "Neon Cyber Dance Challenge! Drop a gift if you vibe #streamvibe #reels",
                                    mediaType = "REEL",
                                    category = "Dance & Music",
                                    musicTrack = "Electric Pulse - StreamVibe Studio",
                                    themeColorHex = "#FF2E93",
                                    mediaUri = "preset://neon_dance_reel"
                                )
                            },
                            onNavigateToUpload = { activeTab = MainNavTab.UPLOAD },
                            onNavigateToLive = { activeTab = MainNavTab.LIVE },
                            onNavigateToWallet = { activeTab = MainNavTab.WALLET },
                            onNavigateToHub = { activeTab = MainNavTab.HUB },
                            initialSubMode = "HOME_DASHBOARD"
                        )
                    }
                    MainNavTab.UPLOAD -> {
                        UploadMediaStudioTab(
                            onPublishMedia = { caption, mediaType, category, musicTrack, themeHex, uri ->
                                viewModel.publishReelOrMedia(
                                    caption = caption,
                                    mediaType = mediaType,
                                    category = category,
                                    musicTrack = musicTrack,
                                    themeColorHex = themeHex,
                                    mediaUri = uri
                                )
                                activeTab = MainNavTab.REELS
                            }
                        )
                    }
                    MainNavTab.LIVE -> {
                        LiveVideoAndChatTab(
                            currentUserId = currentUserId,
                            userProfile = userProfile,
                            liveRoomsState = liveRoomsState,
                            selectedRoomId = selectedRoomId,
                            liveChatState = liveChatState,
                            onStartLiveRoom = { title, topic ->
                                viewModel.startNewLiveStream(title, topic)
                            },
                            onSelectLiveRoom = { roomId ->
                                viewModel.selectLiveRoom(roomId)
                            },
                            onEndLiveRoom = { roomId ->
                                viewModel.endLiveStream(roomId)
                            },
                            onSendChatMessage = { roomId, msg ->
                                viewModel.sendChatMessage(roomId, msg)
                            },
                            onSendGiftToRoom = { gift, room ->
                                viewModel.sendGift(gift = gift, targetLiveRoom = room)
                            },
                            onStartPkLiveRoom = { title, topic, opponentName ->
                                viewModel.startNewLiveStream(
                                    title = title,
                                    topic = topic,
                                    isPkBattle = true,
                                    opponentName = opponentName
                                )
                            },
                            onInvitePkOpponent = { roomId, opponentName ->
                                viewModel.invitePkOpponent(roomId, opponentName)
                            },
                            onSendGiftToRoomWithWallet = { gift, room, useAdminPromo, supportHost ->
                                viewModel.sendGift(
                                    gift = gift,
                                    targetLiveRoom = room,
                                    useAdminPromoWallet = useAdminPromo,
                                    supportHostSideInPk = supportHost
                                )
                            }
                        )
                    }
                    MainNavTab.WALLET -> {
                        WalletAndRazorpayTab(
                            userProfile = userProfile,
                            transactionsState = transactionsState,
                            onRazorpayTestCheckout = { pack, method ->
                                viewModel.topUpCoinsWithRazorpayTest(pack, method)
                            },
                            privateInfo = privateInfo,
                            withdrawalsState = withdrawalsState,
                            adminAuditLogsState = adminAuditLogsState,
                            onAllocateAdminPromoDiamonds = { amount, note ->
                                viewModel.allocateAdminPromotionalDiamonds(amount, note)
                            },
                            onUpdateAdminLimits = { maxAlloc, dailyLimit ->
                                viewModel.updateAdminWalletLimits(maxAlloc, dailyLimit)
                            },
                            onSaveBankAccount = { holder, acc, confirmAcc, ifsc, bank ->
                                viewModel.saveAndVerifyBankAccount(holder, acc, confirmAcc, ifsc, bank)
                            },
                            onRequestWithdrawal = { diamonds ->
                                viewModel.requestCreatorWithdrawal(diamonds)
                            },
                            onVerifyWithdrawalPayout = { withdrawal ->
                                viewModel.verifyWithdrawalPayout(withdrawal)
                            },
                            onUpgradeVipLevel = {
                                viewModel.upgradeVipLevel()
                            }
                        )
                    }
                    MainNavTab.HUB -> {
                        SupportAdminAndPoliciesTab(
                            userProfile = userProfile,
                            currentUserEmail = currentUserEmail,
                            reelsState = reelsState,
                            liveRoomsState = liveRoomsState,
                            transactionsState = transactionsState,
                            ticketsState = ticketsState,
                            onCreateSupportTicket = { subj, cat, desc, prio ->
                                viewModel.submitSupportTicket(subj, cat, desc, prio)
                            },
                            onUpdateSupportTicket = { id, status, reply ->
                                viewModel.updateSupportTicket(id, status, reply)
                            },
                            onSignOut = {
                                signOutUser(
                                    context = context,
                                    onSignOutComplete = { onUserSignedOut() },
                                    scope = scope
                                )
                            },
                            withdrawalsState = withdrawalsState,
                            adminAuditLogsState = adminAuditLogsState,
                            onAllocateAdminDiamonds = { amount, reason ->
                                viewModel.allocateAdminPromotionalDiamonds(amount, reason)
                            },
                            onVerifyWithdrawalPayout = { withdrawal ->
                                viewModel.verifyWithdrawalPayout(withdrawal)
                            },
                            onNavigateToWallet = { activeTab = MainNavTab.WALLET },
                            onNavigateToReels = { activeTab = MainNavTab.REELS },
                            onNavigateToUpload = { activeTab = MainNavTab.UPLOAD },
                            supportChatHistory = supportChatHistory,
                            stagedAttachments = stagedAttachments,
                            banAppeals = banAppeals,
                            onSendAiSupportMessage = { msg, cat, customDesc ->
                                viewModel.sendAiSupportMessage(msg, cat, customDesc)
                            },
                            onUploadEvidenceAttachment = { name, mime, size ->
                                viewModel.uploadEvidenceAttachment(name, mime, size)
                            },
                            onRemoveStagedAttachment = { attId ->
                                viewModel.removeStagedAttachment(attId)
                            },
                            onCreateSupportTicketWithEvidence = { subj, cat, customDesc, desc, prio, consent ->
                                viewModel.submitSupportTicketWithEvidence(subj, cat, customDesc, desc, prio, consent)
                            },
                            onReplyOrReopenTicket = { id, replyMsg, reopen ->
                                viewModel.replyOrReopenTicket(id, replyMsg, reopen)
                            },
                            onSubmitBanAppeal = { appealMsg ->
                                viewModel.submitBanAppeal(appealMsg)
                            },
                            onAdminReviewBanAppeal = { appealId, approve, note ->
                                viewModel.adminReviewBanAppeal(appealId, approve, note)
                            },
                            onUpdateContentCategories = { cats ->
                                viewModel.updateContentCategories(cats)
                            }
                        )
                    }
                }
            }
        }
    }
}
