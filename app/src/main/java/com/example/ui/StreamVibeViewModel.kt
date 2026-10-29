package com.example.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountBanAppeal
import com.example.data.AdminAuditLog
import com.example.data.CoinPackItem
import com.example.data.CoinTransaction
import com.example.data.GiftCatalogItem
import com.example.data.LiveChatMessage
import com.example.data.LiveRoom
import com.example.data.RazorpayPaymentStatus
import com.example.data.ReelPost
import com.example.data.StreamVibeCatalog
import com.example.data.StreamVibeRepository
import com.example.data.SupportAttachment
import com.example.data.SupportChatMessage
import com.example.data.SupportRequestCategory
import com.example.data.SupportTicket
import com.example.data.UserPrivateInfo
import com.example.data.UserProfile
import com.example.data.WithdrawalRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class StreamVibeViewModel(
    private val repository: StreamVibeRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String,
    private val initialPhone: String? = null,
    private val initialAcceptedTerms: Boolean = false,
    private val initialPolicyVersion: String = StreamVibeCatalog.CURRENT_POLICY_VERSION,
    private val initialAcceptedTermsEpochMs: Long = System.currentTimeMillis(),
    private val initialContentCategories: List<String> = emptyList()
) : ViewModel() {

    private val actionLockMonitor = Any()
    private val inFlightActions = ConcurrentHashMap<String, Boolean>()
    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()
    private val isInitialized = AtomicBoolean(false)

    /**
     * Prevents any user action from executing twice on a single tap (either from
     * synthesized touch+mouse events in the streaming emulator or rapid double-dispatch).
     */
    private fun tryAcquireActionLock(actionKey: String, cooldownMs: Long = 650L): Boolean {
        synchronized(actionLockMonitor) {
            val now = SystemClock.elapsedRealtime()
            if (inFlightActions[actionKey] == true) {
                return false
            }
            val lastTime = lastActionTimestamps[actionKey]
            if (lastTime != null && (now - lastTime) in 0 until cooldownMs) {
                return false
            }
            inFlightActions[actionKey] = true
            lastActionTimestamps[actionKey] = now
            return true
        }
    }

    private fun releaseActionLock(actionKey: String) {
        synchronized(actionLockMonitor) {
            inFlightActions[actionKey] = false
        }
    }

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _selectedLiveRoomId = MutableStateFlow<String?>(null)
    val selectedLiveRoomId: StateFlow<String?> = _selectedLiveRoomId.asStateFlow()

    init {
        repository.bindSessionContext(currentUserId, currentUserEmail)
    }

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load profile")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val privateInfoState: StateFlow<UiState<UserPrivateInfo?>> = repository.observePrivateInfo(currentUserId)
        .map<UserPrivateInfo?, UiState<UserPrivateInfo?>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load private account info")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val reelsState: StateFlow<UiState<List<ReelPost>>> = repository.observePublicReels()
        .map<List<ReelPost>, UiState<List<ReelPost>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load reels")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val liveRoomsState: StateFlow<UiState<List<LiveRoom>>> = repository.observeActiveLiveRooms()
        .map<List<LiveRoom>, UiState<List<LiveRoom>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load live rooms")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val liveChatState: StateFlow<UiState<List<LiveChatMessage>>> = _selectedLiveRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeLiveChatMessages(roomId)
                    .map<List<LiveChatMessage>, UiState<List<LiveChatMessage>>> { UiState.Success(it) }
                    .catch { emit(UiState.Error(it.message ?: "Failed to load live chat")) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(emptyList())
        )

    val transactionsState: StateFlow<UiState<List<CoinTransaction>>> = repository.observeUserTransactions(currentUserId)
        .map<List<CoinTransaction>, UiState<List<CoinTransaction>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load transactions")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val withdrawalsState: StateFlow<UiState<List<WithdrawalRequest>>> = repository.observeUserWithdrawals(currentUserId)
        .map<List<WithdrawalRequest>, UiState<List<WithdrawalRequest>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load withdrawals")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val adminAuditLogsState: StateFlow<UiState<List<AdminAuditLog>>> = repository.observeAdminAuditLogs(currentUserId)
        .map<List<AdminAuditLog>, UiState<List<AdminAuditLog>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load admin audit logs")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val ticketsState: StateFlow<UiState<List<SupportTicket>>> = repository.observeUserSupportTickets(currentUserId)
        .map<List<SupportTicket>, UiState<List<SupportTicket>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load support tickets")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val supportChatHistoryState: StateFlow<List<SupportChatMessage>> = repository.observeSupportChatHistory(currentUserId)
    val banAppealsState: StateFlow<List<AccountBanAppeal>> = repository.observeAccountBanAppeals()

    private val _stagedEvidenceAttachments = MutableStateFlow<List<SupportAttachment>>(emptyList())
    val stagedEvidenceAttachments: StateFlow<List<SupportAttachment>> = _stagedEvidenceAttachments.asStateFlow()

    init {
        initializeUserAndStarterContent()
    }

    private fun initializeUserAndStarterContent() {
        if (!isInitialized.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            val defaultName = currentUserDisplayName.ifBlank {
                currentUserEmail.substringBefore("@").ifBlank { "VibeCreator" }
            }
            repository.ensureOrCreateProfile(
                username = defaultName,
                email = currentUserEmail,
                phone = initialPhone ?: "+919000000000"
            )
            if (!initialPhone.isNullOrBlank() && initialAcceptedTerms) {
                repository.completeOtpAndTermsVerification(initialPhone, true)
                repository.recordTermsAndPolicyAcceptance(
                    acceptedTerms = true,
                    policyVersion = initialPolicyVersion,
                    acceptedAtEpochMs = initialAcceptedTermsEpochMs
                )
            }
            if (initialContentCategories.isNotEmpty()) {
                repository.saveContentCategories(initialContentCategories)
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun completeOtpVerification(phone: String, acceptedTerms: Boolean) {
        val lockKey = "otp_verify"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "OTP Verified & Terms Accepted! Creator features unlocked."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.completeOtpAndTermsVerification(phone, acceptedTerms)
                if (result.isFailure) {
                    _statusBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Verification failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun publishReelOrMedia(
        caption: String,
        mediaType: String,
        category: String,
        musicTrack: String,
        themeColorHex: String,
        mediaUri: String
    ) {
        val lockKey = "publish_media"
        if (!tryAcquireActionLock(lockKey)) return
        val authorName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "VibeCreator" }
        _statusBanner.value = "$mediaType published to StreamVibe feed!"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.createReelPost(
                    authorName = authorName,
                    caption = caption,
                    mediaType = mediaType,
                    category = category,
                    musicTrack = musicTrack,
                    themeColorHex = themeColorHex,
                    mediaUri = mediaUri
                )
                if (result.isFailure) {
                    _statusBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Upload failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun likeReelPost(reel: ReelPost) {
        val lockKey = "like_reel_${reel.id}"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "Liked @${reel.authorName}'s ${reel.mediaType}!"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.likeReel(reel)
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun startNewLiveStream(
        title: String,
        topic: String,
        isPkBattle: Boolean = false,
        opponentName: String = "GamingStar_PK"
    ) {
        val lockKey = "start_live_stream"
        if (!tryAcquireActionLock(lockKey)) return
        val hostName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "LiveHost" }
        _statusBanner.value = if (isPkBattle) {
            "Live PK Battle started vs @$opponentName!"
        } else {
            "You are now LIVE on StreamVibe!"
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.startLiveRoom(hostName, title, topic, isPkBattle, opponentName)
                if (result.isSuccess) {
                    _selectedLiveRoomId.value = result.getOrNull()
                } else {
                    _statusBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Could not start live stream"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun invitePkOpponent(roomId: String, opponentName: String) {
        val lockKey = "invite_pk_$roomId"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "PK Battle started vs @$opponentName!"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.toggleOrInvitePkBattle(roomId, opponentName)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not start PK Battle"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun selectLiveRoom(roomId: String?) {
        _selectedLiveRoomId.value = roomId
    }

    fun endLiveStream(roomId: String) {
        val lockKey = "end_live_$roomId"
        if (!tryAcquireActionLock(lockKey)) return
        if (_selectedLiveRoomId.value == roomId) {
            _selectedLiveRoomId.value = null
        }
        _statusBanner.value = "Live broadcast ended."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.endLiveRoom(roomId)
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun sendChatMessage(roomId: String, message: String) {
        if (message.isBlank()) return
        val lockKey = "chat_${roomId}_${message.trim()}"
        if (!tryAcquireActionLock(lockKey)) return
        val senderName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "Viewer" }
        _statusBanner.value = "Message sent to Live Chat!"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.sendLiveChatMessage(
                    roomId = roomId,
                    senderName = senderName,
                    message = message
                )
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun topUpCoinsWithRazorpayTest(
        pack: CoinPackItem,
        paymentMethodLabel: String,
        simulatedOutcome: RazorpayPaymentStatus = RazorpayPaymentStatus.VERIFIED
    ) {
        val lockKey = "razorpay_topup_${pack.id}"
        if (!tryAcquireActionLock(lockKey)) return
        val totalDiamonds = pack.coinsAmount + pack.bonusCoins
        if (simulatedOutcome == RazorpayPaymentStatus.VERIFIED) {
            _statusBanner.value = "Verified Test Payment ₹${pack.priceInr} INR! +$totalDiamonds Diamonds credited to User Wallet."
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.processRazorpayTestTopUp(
                    pack = pack,
                    paymentMethodLabel = paymentMethodLabel,
                    simulatedOutcome = simulatedOutcome
                )
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Razorpay test payment failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun allocateAdminPromotionalDiamonds(amountDiamonds: Long, reasonNote: String) {
        val lockKey = "admin_promo_alloc_$amountDiamonds"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "+$amountDiamonds Promotional Diamonds allocated to Admin Wallet & logged in Audit Trail."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.allocateAdminPromotionalDiamonds(amountDiamonds, reasonNote)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Promotional allocation failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun updateAdminWalletLimits(maxSingleAllocation: Long, dailyGiftLimit: Long) {
        val lockKey = "admin_limits_update"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "Admin Promotional Wallet limits saved & audited."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.updateAdminWalletLimits(maxSingleAllocation, dailyGiftLimit)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update admin limits"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun sendGift(
        gift: GiftCatalogItem,
        targetReel: ReelPost? = null,
        targetLiveRoom: LiveRoom? = null,
        useAdminPromoWallet: Boolean = false,
        supportHostSideInPk: Boolean = true
    ) {
        val lockKey = "send_gift_${gift.id}_${targetReel?.id ?: targetLiveRoom?.id ?: ""}_$useAdminPromoWallet"
        if (!tryAcquireActionLock(lockKey)) return
        val senderName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "Supporter" }
        val walletLabel = if (useAdminPromoWallet) "Admin Promotional Wallet" else "User Wallet"
        _statusBanner.value = "Sent ${gift.emoji} ${gift.name} (${gift.coinsCost} Diamonds) from $walletLabel!"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.sendGiftToReelOrLive(
                    senderName = senderName,
                    gift = gift,
                    targetReel = targetReel,
                    targetLiveRoom = targetLiveRoom,
                    useAdminPromoWallet = useAdminPromoWallet,
                    supportHostSideInPk = supportHostSideInPk
                )
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to send gift"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun saveAndVerifyBankAccount(
        holderName: String,
        accountNumber: String,
        confirmAccountNumber: String,
        ifscCode: String,
        bankName: String
    ) {
        val lockKey = "save_bank_account"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "Bank account verified for Creator Earnings withdrawals!"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.saveAndVerifyBankAccount(
                    holderName = holderName,
                    accountNumber = accountNumber,
                    confirmAccountNumber = confirmAccountNumber,
                    ifscCode = ifscCode,
                    bankName = bankName
                )
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Bank verification failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun requestCreatorWithdrawal(diamondsAmount: Long) {
        val lockKey = "request_withdrawal_$diamondsAmount"
        if (!tryAcquireActionLock(lockKey)) return
        val inrAmount = StreamVibeCatalog.diamondsToInr(diamondsAmount)
        _statusBanner.value = "Withdrawal request for $diamondsAmount Diamonds (₹$inrAmount) submitted for payout verification."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.requestCreatorWithdrawal(diamondsAmount)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Withdrawal request failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun verifyWithdrawalPayout(withdrawal: WithdrawalRequest) {
        val lockKey = "verify_payout_${withdrawal.id}"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "Payout verified! Status updated to VERIFIED_PAID (₹${withdrawal.payoutInr})."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.verifyWithdrawalPayout(withdrawal)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Payout verification failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun upgradeVipLevel(diamondsCost: Long = 500L) {
        val lockKey = "upgrade_vip"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "VIP Level Upgraded! Exclusive badge & perks unlocked."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.upgradeVipLevel(diamondsCost)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "VIP Upgrade failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun submitSupportTicket(
        subject: String,
        category: String,
        description: String,
        priority: String
    ) {
        val lockKey = "submit_support_ticket"
        if (!tryAcquireActionLock(lockKey)) return
        val userName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "User" }
        _statusBanner.value = "24/7 Support Ticket submitted! Track status below."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.createSupportTicket(
                    userName = userName,
                    subject = subject,
                    category = category,
                    description = description,
                    priority = priority
                )
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to create ticket"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun updateSupportTicket(ticketId: String, newStatus: String, adminReply: String) {
        val lockKey = "update_ticket_${ticketId}_$newStatus"
        if (!tryAcquireActionLock(lockKey)) return
        _statusBanner.value = "Ticket updated to $newStatus."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.updateTicketResponseAndStatus(ticketId, newStatus, adminReply)
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update ticket"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun updateContentCategories(categories: List<String>) {
        val lockKey = "update_content_categories"
        if (!tryAcquireActionLock(lockKey)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.saveContentCategories(categories)
                if (res.isSuccess) {
                    _statusBanner.value = "Content categories updated: ${res.getOrNull()?.joinToString(", ")}"
                } else {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update categories"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun sendAiSupportMessage(
        messageText: String,
        selectedCategory: SupportRequestCategory,
        customCategoryDescription: String = ""
    ) {
        if (messageText.isBlank()) return
        val lockKey = "ai_support_chat_${messageText.trim().hashCode()}"
        if (!tryAcquireActionLock(lockKey)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.sendSupportChatMessage(
                    userMessage = messageText,
                    selectedCategory = selectedCategory,
                    customCategoryDescription = customCategoryDescription
                )
                if (res.isFailure) {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not send support message"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun uploadEvidenceAttachment(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long
    ) {
        val lockKey = "upload_evidence_${fileName.trim()}"
        if (!tryAcquireActionLock(lockKey)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentCount = _stagedEvidenceAttachments.value.size
                val res = repository.validateAndUploadSupportAttachment(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = fileSizeBytes,
                    existingAttachmentCount = currentCount
                )
                if (res.isSuccess) {
                    val uploaded = res.getOrThrow()
                    _stagedEvidenceAttachments.value = _stagedEvidenceAttachments.value + uploaded
                    _statusBanner.value = "Upload successful: ${uploaded.fileName} (${uploaded.formattedSizeLabel}) verified by server."
                } else {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Attachment validation failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun removeStagedAttachment(attachmentId: String) {
        _stagedEvidenceAttachments.value = _stagedEvidenceAttachments.value.filterNot { it.id == attachmentId }
    }

    fun submitSupportTicketWithEvidence(
        subject: String,
        category: String,
        customCategoryDescription: String,
        description: String,
        priority: String,
        evidenceConsentAccepted: Boolean
    ) {
        val lockKey = "submit_support_ticket_with_evidence"
        if (!tryAcquireActionLock(lockKey)) return
        val userName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "User" }
        val attachments = _stagedEvidenceAttachments.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.createSupportTicketWithEvidence(
                    userName = userName,
                    subject = subject,
                    category = category,
                    customCategoryDescription = customCategoryDescription,
                    description = description,
                    priority = priority,
                    attachments = attachments,
                    evidenceConsentAccepted = evidenceConsentAccepted
                )
                if (res.isSuccess) {
                    val created = res.getOrThrow()
                    _stagedEvidenceAttachments.value = emptyList()
                    _statusBanner.value = "Support Ticket ${created.ticketNumber} submitted! Status: ${created.status}."
                } else {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to create support ticket"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun replyOrReopenTicket(ticketId: String, message: String, reopenTicket: Boolean) {
        val lockKey = "reply_reopen_${ticketId}_$reopenTicket"
        if (!tryAcquireActionLock(lockKey)) return
        val senderName = (userProfileState.value as? UiState.Success)?.data?.username
            ?: currentUserDisplayName.ifBlank { "User" }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.replyOrReopenSupportTicket(
                    ticketId = ticketId,
                    senderName = senderName,
                    replyMessage = message,
                    reopenTicket = reopenTicket
                )
                if (res.isSuccess) {
                    val updated = res.getOrThrow()
                    _statusBanner.value = "Ticket ${updated.ticketNumber} updated (${updated.status})."
                } else {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not update ticket"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun submitBanAppeal(appealMessage: String, evidenceAttachmentIds: List<String> = emptyList()) {
        val lockKey = "submit_ban_appeal"
        if (!tryAcquireActionLock(lockKey)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.submitAccountBanAppeal(
                    appealMessage = appealMessage,
                    evidenceAttachmentIds = evidenceAttachmentIds
                )
                if (res.isSuccess) {
                    _statusBanner.value = "Account appeal (${res.getOrThrow().id}) submitted for Trust & Safety review."
                } else {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Appeal submission failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }

    fun adminReviewBanAppeal(appealId: String, approveAndRestore: Boolean, adminNote: String) {
        val lockKey = "admin_review_appeal_$appealId"
        if (!tryAcquireActionLock(lockKey)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.reviewAccountBanAppeal(
                    appealId = appealId,
                    approveAndRestore = approveAndRestore,
                    adminDecisionNote = adminNote
                )
                if (res.isSuccess) {
                    _statusBanner.value = "Appeal $appealId reviewed: ${res.getOrThrow().status}."
                } else {
                    _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Appeal review failed"
                }
            } finally {
                releaseActionLock(lockKey)
            }
        }
    }
}
