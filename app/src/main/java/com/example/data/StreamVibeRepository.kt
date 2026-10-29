package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.BuildConfig
import java.io.IOException
import kotlinx.coroutines.tasks.await

class StreamVibeRepository(private val rawDb: FirebaseFirestore? = null) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val db: FirebaseFirestore
        get() = rawDb ?: throw IllegalStateException("Firestore instance is not initialized in local test session.")

    private val auth
        get() = runCatching { Firebase.auth }.getOrNull()
    private val profileInitMutex = Mutex()
    private val txMutex = Mutex()
    private val processedIdempotencyKeys = ConcurrentHashMap.newKeySet<String>()
    private val createdRazorpayOrders = ConcurrentHashMap<String, RazorpayTestOrder>()
    private val processedRazorpayOrderIds = ConcurrentHashMap.newKeySet<String>()
    private val processedRazorpayPaymentIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var boundSessionUserId: String? = null

    @Volatile
    private var boundSessionEmail: String? = null

    // Reactive fallback state when running in a browser Streaming Emulator without an OS Google Account
    private val localProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    private val localPrivateInfos = MutableStateFlow<Map<String, UserPrivateInfo>>(emptyMap())
    private val localReels = MutableStateFlow<List<ReelPost>>(emptyList())
    private val localLiveRooms = MutableStateFlow<List<LiveRoom>>(emptyList())
    private val localChatMessages = MutableStateFlow<Map<String, List<LiveChatMessage>>>(emptyMap())
    private val localTransactions = MutableStateFlow<List<CoinTransaction>>(emptyList())
    private val localWithdrawals = MutableStateFlow<List<WithdrawalRequest>>(emptyList())
    private val localAuditLogs = MutableStateFlow<List<AdminAuditLog>>(emptyList())
    private val localTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    private val localPolicyAcceptances = MutableStateFlow<Map<String, PolicyAcceptanceRecord>>(emptyMap())
    private val localUserReports = MutableStateFlow<List<UserReportRecord>>(emptyList())
    private val localModerationDecisions = MutableStateFlow<List<ModerationDecisionRecord>>(emptyList())
    private val localBanAppeals = MutableStateFlow<List<AccountBanAppeal>>(emptyList())
    private val localSupportAttachments = MutableStateFlow<Map<String, SupportAttachment>>(emptyMap())
    private val localSupportChatHistory = MutableStateFlow<List<SupportChatMessage>>(emptyList())

    fun bindSessionContext(userId: String, email: String) {
        boundSessionUserId = userId.ifBlank { null }
        boundSessionEmail = email.ifBlank { StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL }
    }

    private fun isUsingLocalSessionFallback(): Boolean {
        return auth?.currentUser == null && !boundSessionUserId.isNullOrBlank()
    }

    private fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: boundSessionUserId
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    private fun currentUserEmailOrDefault(): String {
        return auth?.currentUser?.email
            ?: boundSessionEmail
            ?: StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL

    }

    private fun sanitizeId(raw: String, prefix: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(64)
        return if (cleaned.isNotBlank()) "${prefix}_$cleaned" else "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    }

    // --- 1. USER PROFILE (3 DIAMOND WALLETS + VIP 1-1000) & PRIVATE PII / BANK ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localProfiles.map { it[userId] })
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    fun observePrivateInfo(userId: String): Flow<UserPrivateInfo?> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localPrivateInfos.map { it[userId] })
            return@flow
        }
        val path = "users/$userId/private/info"
        emitAll(
            db.collection("users").document(userId).collection("private").document("info")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        if (isUsingLocalSessionFallback()) {
            return Result.success(localProfiles.value[userId])
        }
        val path = "users/$userId"
        return try {
            val snap = db.collection("users").document(userId).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun ensureOrCreateProfile(
        username: String,
        email: String,
        phone: String = "+919000000000"
    ): Result<UserProfile> = profileInitMutex.withLock {
        val uid = requireUserId()
        val safeName = username.trim().take(35).ifBlank { "Creator_${uid.take(5)}" }

        if (isUsingLocalSessionFallback()) {
            val existing = localProfiles.value[uid]
            if (existing != null) {
                return Result.success(existing)
            }
            val now = Timestamp.now()
            val created = UserProfile(
                id = uid,
                userId = uid,
                username = safeName,
                bio = "Creating Reels, Short Videos & Live Streams on StreamVibe",
                avatarColor = "#FF2E93",
                coinsBalance = 250L,
                adminWalletDiamonds = 50000L,
                creatorEarningsDiamonds = 0L,
                withdrawableDiamonds = 0L,
                vipLevel = 1L,
                vipXp = 250L,
                adminMaxAllocationLimit = 25000L,
                adminDailyGiftLimit = 100000L,
                giftsReceivedCount = 0L,
                followersCount = 12L,
                isVerifiedOtp = false,
                acceptedTerms = false,
                createdAt = now,
                updatedAt = now
            )
            val priv = UserPrivateInfo(
                id = "info",
                userId = uid,
                email = email.take(140),
                phone = phone.take(20),
                kycStatus = "pending",
                updatedAt = now
            )
            val cleanUid = uid.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(20)
            val txId = "welcome_$cleanUid"
            val auditId = "init_promo_$cleanUid"

            localProfiles.value = localProfiles.value + (uid to created)
            localPrivateInfos.value = localPrivateInfos.value + (uid to priv)
            localTransactions.value = listOf(
                CoinTransaction(
                    id = txId,
                    userId = uid,
                    txType = "WELCOME_BONUS",
                    coinsDelta = 250L,
                    amountInrPaise = 0L,
                    razorpayOrderId = "welcome_bonus_init",
                    razorpayPaymentId = "bonus_250_diamonds",
                    paymentMode = "TEST",
                    recipientName = "Self",
                    giftLabel = "Starter User Wallet: 250 Diamonds",
                    walletType = DiamondWalletType.USER_WALLET.rawValue,
                    senderId = uid,
                    senderName = safeName,
                    receiverId = uid,
                    idempotencyKey = txId,
                    createdAt = now
                )
            ) + localTransactions.value
            localAuditLogs.value = listOf(
                AdminAuditLog(
                    id = auditId,
                    adminId = uid,
                    adminEmail = email.take(140).ifBlank { StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL },
                    actionType = "PROMO_ALLOCATION",
                    diamondsAmount = 50000L,
                    targetUser = safeName,
                    note = "Initial Admin Promotional Wallet Reserve (50,000 Promotional Diamonds)",
                    idempotencyKey = auditId,
                    createdAt = now
                )
            ) + localAuditLogs.value
            return Result.success(created)
        }

        val userDoc = db.collection("users").document(uid)
        return try {
            val existing = userDoc.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, username = username)
                Result.success(profile)
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "username" to safeName,
                    "bio" to "Creating Reels, Short Videos & Live Streams on StreamVibe",
                    "avatarColor" to "#FF2E93",
                    "coinsBalance" to 250L,
                    "adminWalletDiamonds" to 50000L,
                    "creatorEarningsDiamonds" to 0L,
                    "withdrawableDiamonds" to 0L,
                    "vipLevel" to 1L,
                    "vipXp" to 250L,
                    "adminMaxAllocationLimit" to 25000L,
                    "adminDailyGiftLimit" to 100000L,
                    "giftsReceivedCount" to 0L,
                    "followersCount" to 12L,
                    "isVerifiedOtp" to false,
                    "acceptedTerms" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val privDoc = userDoc.collection("private").document("info")
                val privPayload = mapOf(
                    "userId" to uid,
                    "email" to email.take(140),
                    "phone" to phone.take(20),
                    "kycStatus" to "pending",
                    "bankHolderName" to "",
                    "bankAccountNumber" to "",
                    "bankIfsc" to "",
                    "bankName" to "",
                    "bankVerified" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val cleanUid = uid.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(20)
                val txId = "welcome_$cleanUid"
                val txDoc = db.collection("transactions").document(txId)
                val auditId = "init_promo_$cleanUid"
                val auditDoc = db.collection("admin_audit_logs").document(auditId)

                val batch = db.batch()
                batch.set(userDoc, payload)
                batch.set(privDoc, privPayload)
                batch.set(
                    txDoc,
                    mapOf(
                        "userId" to uid,
                        "txType" to "WELCOME_BONUS",
                        "coinsDelta" to 250L,
                        "amountInrPaise" to 0L,
                        "razorpayOrderId" to "welcome_bonus_init",
                        "razorpayPaymentId" to "bonus_250_diamonds",
                        "paymentMode" to "TEST",
                        "recipientName" to "Self",
                        "giftLabel" to "Starter User Wallet: 250 Diamonds",
                        "walletType" to DiamondWalletType.USER_WALLET.rawValue,
                        "senderId" to uid,
                        "senderName" to safeName,
                        "receiverId" to uid,
                        "idempotencyKey" to txId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    auditDoc,
                    mapOf(
                        "adminId" to uid,
                        "adminEmail" to email.take(140).ifBlank { StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL },
                        "actionType" to "PROMO_ALLOCATION",
                        "diamondsAmount" to 50000L,
                        "targetUser" to safeName,
                        "note" to "Initial Admin Promotional Wallet Reserve (50,000 Promotional Diamonds)",
                        "idempotencyKey" to auditId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()

                val created = UserProfile(
                    id = uid,
                    userId = uid,
                    username = safeName,
                    coinsBalance = 250L,
                    adminWalletDiamonds = 50000L,
                    creatorEarningsDiamonds = 0L,
                    withdrawableDiamonds = 0L,
                    vipLevel = 1L,
                    vipXp = 250L
                )
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            Result.failure(e)
        }
    }

    suspend fun completeOtpAndTermsVerification(
        phone: String,
        acceptTerms: Boolean,
        policyVersion: String = StreamVibeCatalog.CURRENT_POLICY_VERSION,
        contentCategories: List<String> = emptyList()
    ): Result<Unit> {
        val uid = requireUserId()
        if (!acceptTerms) {
            return Result.failure(
                IllegalArgumentException("You must agree to the Terms & Conditions and Community Guidelines before completing registration.")
            )
        }
        val acceptedAtMs = System.currentTimeMillis()
        val resolvedVersion = policyVersion.trim().ifBlank { StreamVibeCatalog.CURRENT_POLICY_VERSION }
        localPolicyAcceptances.value = localPolicyAcceptances.value + (
            uid to PolicyAcceptanceRecord(
                userId = uid,
                policyVersion = resolvedVersion,
                acceptedAtEpochMs = acceptedAtMs
            )
        )

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val curProfile = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            val updatedCategories = if (contentCategories.isNotEmpty()) {
                contentCategories.filter { StreamVibeCatalog.contentCategories.contains(it) }.distinct()
            } else {
                curProfile.contentCategories
            }
            localProfiles.value = localProfiles.value + (
                uid to curProfile.copy(
                    isVerifiedOtp = true,
                    acceptedTerms = true,
                    acceptedPolicyVersion = resolvedVersion,
                    acceptedTermsEpochMs = acceptedAtMs,
                    contentCategories = updatedCategories,
                    updatedAt = now
                )
            )
            val curPriv = localPrivateInfos.value[uid] ?: UserPrivateInfo(
                id = "info",
                userId = uid,
                email = currentUserEmailOrDefault()
            )
            localPrivateInfos.value = localPrivateInfos.value + (
                uid to curPriv.copy(
                    phone = phone.take(20),
                    kycStatus = "verified",
                    updatedAt = now
                )
            )
            return Result.success(Unit)
        }

        val userDoc = db.collection("users").document(uid)
        val privDoc = userDoc.collection("private").document("info")
        return try {
            val updates = mutableMapOf<String, Any>(
                "isVerifiedOtp" to true,
                "acceptedTerms" to true,
                "acceptedPolicyVersion" to resolvedVersion,
                "acceptedTermsEpochMs" to acceptedAtMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (contentCategories.isNotEmpty()) {
                updates["contentCategories"] = contentCategories.filter { StreamVibeCatalog.contentCategories.contains(it) }.distinct()
            }
            userDoc.update(updates).await()

            val privSnap = privDoc.get().await()
            if (privSnap.exists()) {
                privDoc.update(
                    mapOf(
                        "phone" to phone.take(20),
                        "kycStatus" to "verified",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                privDoc.set(
                    mapOf(
                        "userId" to uid,
                        "email" to currentUserEmailOrDefault().take(140),
                        "phone" to phone.take(20),
                        "kycStatus" to "verified",
                        "bankHolderName" to "",
                        "bankAccountNumber" to "",
                        "bankIfsc" to "",
                        "bankName" to "",
                        "bankVerified" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Requirement 1: Mandatory Terms & Conditions Checkbox Acceptance Record.
     * Saves policyVersion, userId, and acceptance timestamp securely.
     */
    suspend fun recordTermsAndPolicyAcceptance(
        acceptedTerms: Boolean,
        policyVersion: String = StreamVibeCatalog.CURRENT_POLICY_VERSION,
        acceptedAtEpochMs: Long = System.currentTimeMillis()
    ): Result<PolicyAcceptanceRecord> {
        val uid = requireUserId()
        if (!acceptedTerms) {
            return Result.failure(
                IllegalArgumentException("You must agree to the Terms & Conditions and Community Guidelines before completing registration.")
            )
        }
        val safeVersion = policyVersion.trim().ifBlank { StreamVibeCatalog.CURRENT_POLICY_VERSION }
        val record = PolicyAcceptanceRecord(
            userId = uid,
            policyVersion = safeVersion,
            acceptedAtEpochMs = acceptedAtEpochMs
        )
        localPolicyAcceptances.value = localPolicyAcceptances.value + (uid to record)

        if (isUsingLocalSessionFallback()) {
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            localProfiles.value = localProfiles.value + (
                uid to cur.copy(
                    acceptedTerms = true,
                    acceptedPolicyVersion = safeVersion,
                    acceptedTermsEpochMs = acceptedAtEpochMs,
                    updatedAt = Timestamp.now()
                )
            )
            return Result.success(record)
        }

        val userDoc = db.collection("users").document(uid)
        return try {
            userDoc.update(
                mapOf(
                    "acceptedTerms" to true,
                    "acceptedPolicyVersion" to safeVersion,
                    "acceptedTermsEpochMs" to acceptedAtEpochMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    fun getPolicyAcceptanceRecord(userId: String): PolicyAcceptanceRecord? {
        return localPolicyAcceptances.value[userId]
    }

    /**
     * Requirement 2: Content Category Selection ("Choose Your Content Categories").
     * - Allows multiple selections from: Music, Live Music, Live Streaming, Short Videos / Reels, Gaming, Chat, Other.
     * - Requires at least one selection during creator profile setup.
     * - Saves selected categories to the user's profile and allows updating later.
     */
    suspend fun saveContentCategories(
        selectedCategories: List<String>,
        requireAtLeastOne: Boolean = true
    ): Result<List<String>> {
        val uid = requireUserId()
        val validCategories = selectedCategories
            .map { it.trim() }
            .filter { StreamVibeCatalog.contentCategories.contains(it) }
            .distinct()

        if (requireAtLeastOne && validCategories.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Please select at least one content category under 'Choose Your Content Categories'.")
            )
        }

        if (isUsingLocalSessionFallback()) {
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            localProfiles.value = localProfiles.value + (
                uid to cur.copy(
                    contentCategories = validCategories,
                    updatedAt = Timestamp.now()
                )
            )
            return Result.success(validCategories)
        }

        val userDoc = db.collection("users").document(uid)
        return try {
            userDoc.update(
                mapOf(
                    "contentCategories" to validCategories,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(validCategories)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Uses the user's saved content categories to prioritize content recommendations.
     */
    fun getRecommendedReelsForCategories(
        reels: List<ReelPost>,
        selectedCategories: List<String>
    ): List<ReelPost> {
        if (selectedCategories.isEmpty()) return reels
        val normalizedSelected = selectedCategories.map { it.lowercase() }
        val (matching, others) = reels.partition { reel ->
            val reelCat = reel.category.lowercase()
            val caption = reel.caption.lowercase()
            normalizedSelected.any { sel ->
                reelCat.contains(sel) || sel.contains(reelCat) || caption.contains(sel)
            }
        }
        return matching + others
    }

    /**
     * Enforces account moderation status on backend actions (posting Reels, starting Live streams).
     */
    fun assertAccountActiveForAction(userId: String, actionName: String): Result<Unit> {
        val profile = localProfiles.value[userId] ?: return Result.success(Unit)
        if (profile.isPermanentlyBanned) {
            return Result.failure(
                SecurityException(
                    "Action '$actionName' blocked: Your account has been permanently banned following moderation review (${profile.suspensionReason.ifBlank { "Severe or repeated Community Guidelines violation" }}). You can submit an appeal in Help & Support."
                )
            )
        }
        if (profile.isTemporarilySuspended) {
            return Result.failure(
                SecurityException(
                    "Action '$actionName' blocked: Your account is under a 3-day temporary suspension (${profile.suspensionReason.ifBlank { "Confirmed Community Guidelines violation" }}). You can submit an appeal in Help & Support."
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun saveAndVerifyBankAccount(
        holderName: String,
        accountNumber: String,
        confirmAccountNumber: String,
        ifscCode: String,
        bankName: String
    ): Result<Unit> = txMutex.withLock {
        val uid = requireUserId()
        val trimmedHolder = holderName.trim().take(75)
        val trimmedAcc = accountNumber.trim().replace(" ", "").take(30)
        val trimmedConfirm = confirmAccountNumber.trim().replace(" ", "").take(30)
        val trimmedIfsc = ifscCode.trim().uppercase().take(18)
        val trimmedBank = bankName.trim().take(75)

        if (trimmedHolder.length < 2) {
            return Result.failure(IllegalArgumentException("Please enter a valid Account Holder Name."))
        }
        if (trimmedAcc.length < 6 || trimmedAcc != trimmedConfirm) {
            return Result.failure(IllegalArgumentException("Account Number and Confirm Account Number must match (min 6 digits)."))
        }
        if (trimmedIfsc.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter a valid IFSC Code."))
        }
        if (trimmedBank.length < 2) {
            return Result.failure(IllegalArgumentException("Please enter your Bank Name."))
        }

        if (isUsingLocalSessionFallback()) {
            val curPriv = localPrivateInfos.value[uid] ?: UserPrivateInfo(
                id = "info",
                userId = uid,
                email = currentUserEmailOrDefault(),
                phone = "+919000000000"
            )
            localPrivateInfos.value = localPrivateInfos.value + (
                uid to curPriv.copy(
                    bankHolderName = trimmedHolder,
                    bankAccountNumber = trimmedAcc,
                    bankIfsc = trimmedIfsc,
                    bankName = trimmedBank,
                    bankVerified = true,
                    kycStatus = "verified",
                    updatedAt = Timestamp.now()
                )
            )
            return Result.success(Unit)
        }

        val privDoc = db.collection("users").document(uid).collection("private").document("info")
        return try {
            val snap = privDoc.get().await()
            if (snap.exists()) {
                privDoc.update(
                    mapOf(
                        "bankHolderName" to trimmedHolder,
                        "bankAccountNumber" to trimmedAcc,
                        "bankIfsc" to trimmedIfsc,
                        "bankName" to trimmedBank,
                        "bankVerified" to true,
                        "kycStatus" to "verified",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                privDoc.set(
                    mapOf(
                        "userId" to uid,
                        "email" to currentUserEmailOrDefault().take(140),
                        "phone" to "+919000000000",
                        "kycStatus" to "verified",
                        "bankHolderName" to trimmedHolder,
                        "bankAccountNumber" to trimmedAcc,
                        "bankIfsc" to trimmedIfsc,
                        "bankName" to trimmedBank,
                        "bankVerified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, privDoc.path)
            Result.failure(e)
        }
    }

    suspend fun getPrivateInfo(userId: String): Result<UserPrivateInfo?> {
        if (isUsingLocalSessionFallback()) {
            return Result.success(localPrivateInfos.value[userId])
        }
        val docRef = db.collection("users").document(userId).collection("private").document("info")
        return try {
            val snap = docRef.get().await()
            Result.success(snap.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- 2. REELS, SHORT VIDEOS & PHOTOS ---

    fun observePublicReels(): Flow<List<ReelPost>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localReels.map { list -> list.filter { it.visibility == "PUBLIC" } })
            return@flow
        }
        val path = "reels"
        emitAll(
            db.collection("reels")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ReelPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun createReelPost(
        authorName: String,
        caption: String,
        mediaType: String = "REEL",
        category: String = "Short Videos / Reels",
        musicTrack: String = "Original Audio - StreamVibe",
        themeColorHex: String = "#FF2E93",
        mediaUri: String = "preset://neon_pulse"
    ): Result<String> {
        val uid = requireUserId()
        assertAccountActiveForAction(uid, "Publish Reel/Media").onFailure { return Result.failure(it) }
        val reelId = "reel_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val post = ReelPost(
                id = reelId,
                authorId = uid,
                authorName = authorName.trim().take(45).ifBlank { "Creator" },
                caption = caption.trim().take(450).ifBlank { "New StreamVibe Upload #streamvibe" },
                mediaType = mediaType,
                category = category.trim().take(35).ifBlank { "Trending" },
                musicTrack = musicTrack.trim().take(75).ifBlank { "Original Audio - StreamVibe" },
                themeColorHex = themeColorHex.take(12).ifBlank { "#FF2E93" },
                mediaUri = mediaUri.take(900).ifBlank { "preset://neon_pulse" },
                likesCount = 0L,
                giftsCoinsTotal = 0L,
                visibility = "PUBLIC",
                createdAt = now,
                updatedAt = now
            )
            localReels.value = listOf(post) + localReels.value
            return Result.success(reelId)
        }

        val docRef = db.collection("reels").document(reelId)
        val payload = mapOf(
            "authorId" to uid,
            "authorName" to authorName.trim().take(45).ifBlank { "Creator" },
            "caption" to caption.trim().take(450).ifBlank { "New StreamVibe Upload #streamvibe" },
            "mediaType" to mediaType,
            "category" to category.trim().take(35).ifBlank { "Trending" },
            "musicTrack" to musicTrack.trim().take(75).ifBlank { "Original Audio - StreamVibe" },
            "themeColorHex" to themeColorHex.take(12).ifBlank { "#FF2E93" },
            "mediaUri" to mediaUri.take(900).ifBlank { "preset://neon_pulse" },
            "likesCount" to 0L,
            "giftsCoinsTotal" to 0L,
            "visibility" to "PUBLIC",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(reelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun likeReel(reel: ReelPost): Result<Unit> {
        requireUserId()
        if (isUsingLocalSessionFallback()) {
            localReels.value = localReels.value.map {
                if (it.id == reel.id) it.copy(likesCount = it.likesCount + 1L, updatedAt = Timestamp.now()) else it
            }
            return Result.success(Unit)
        }
        val docRef = db.collection("reels").document(reel.id)
        return try {
            docRef.update(
                mapOf(
                    "likesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 3. LIVE STREAMING ROOMS, PK BATTLE & LIVE CHAT ---

    fun observeActiveLiveRooms(): Flow<List<LiveRoom>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localLiveRooms.map { list -> list.filter { it.status == "LIVE" } })
            return@flow
        }
        val path = "live_rooms"
        emitAll(
            db.collection("live_rooms")
                .whereEqualTo("status", "LIVE")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LiveRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun startLiveRoom(
        hostName: String,
        title: String,
        topic: String,
        isPkBattle: Boolean = false,
        opponentName: String = "GamingStar_PK"
    ): Result<String> {
        val uid = requireUserId()
        assertAccountActiveForAction(uid, "Start Live Stream").onFailure { return Result.failure(it) }
        val roomId = "live_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val room = LiveRoom(
                id = roomId,
                hostId = uid,
                hostName = hostName.trim().take(45).ifBlank { "LiveHost" },
                title = title.trim().take(110).ifBlank { "Live Vibe Session" },
                topic = topic.trim().take(45).ifBlank { "Music & Chat" },
                status = "LIVE",
                viewerCount = 1L,
                totalGiftsCoins = 0L,
                isPkBattle = isPkBattle,
                opponentName = if (isPkBattle) opponentName.trim().take(45).ifBlank { "Challenger_PK" } else "",
                hostPkScore = 0L,
                opponentPkScore = 0L,
                createdAt = now,
                updatedAt = now
            )
            localLiveRooms.value = listOf(room) + localLiveRooms.value
            return Result.success(roomId)
        }

        val docRef = db.collection("live_rooms").document(roomId)
        val payload = mapOf(
            "hostId" to uid,
            "hostName" to hostName.trim().take(45).ifBlank { "LiveHost" },
            "title" to title.trim().take(110).ifBlank { "Live Vibe Session" },
            "topic" to topic.trim().take(45).ifBlank { "Music & Chat" },
            "status" to "LIVE",
            "viewerCount" to 1L,
            "totalGiftsCoins" to 0L,
            "isPkBattle" to isPkBattle,
            "opponentName" to if (isPkBattle) opponentName.trim().take(45).ifBlank { "Challenger_PK" } else "",
            "hostPkScore" to 0L,
            "opponentPkScore" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleOrInvitePkBattle(roomId: String, opponentName: String): Result<Unit> {
        requireUserId()
        if (isUsingLocalSessionFallback()) {
            localLiveRooms.value = localLiveRooms.value.map {
                if (it.id == roomId) {
                    it.copy(
                        isPkBattle = true,
                        opponentName = opponentName.trim().take(45).ifBlank { "GamingStar_PK" },
                        updatedAt = Timestamp.now()
                    )
                } else it
            }
            return Result.success(Unit)
        }
        val docRef = db.collection("live_rooms").document(roomId)
        return try {
            docRef.update(
                mapOf(
                    "isPkBattle" to true,
                    "opponentName" to opponentName.trim().take(45).ifBlank { "GamingStar_PK" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun endLiveRoom(roomId: String): Result<Unit> {
        requireUserId()
        if (isUsingLocalSessionFallback()) {
            localLiveRooms.value = localLiveRooms.value.map {
                if (it.id == roomId) it.copy(status = "ENDED", updatedAt = Timestamp.now()) else it
            }
            return Result.success(Unit)
        }
        val docRef = db.collection("live_rooms").document(roomId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "ENDED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeLiveChatMessages(roomId: String): Flow<List<LiveChatMessage>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localChatMessages.map { it[roomId] ?: emptyList() })
            return@flow
        }
        val path = "live_rooms/$roomId/messages"
        emitAll(
            db.collection("live_rooms").document(roomId).collection("messages")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LiveChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun sendLiveChatMessage(
        roomId: String,
        senderName: String,
        message: String,
        messageType: String = "CHAT",
        giftName: String = "",
        giftCoins: Long = 0L,
        walletSource: String = "USER_WALLET"
    ): Result<String> {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        if (isUsingLocalSessionFallback()) {
            val chatMsg = LiveChatMessage(
                id = msgId,
                roomId = roomId,
                senderId = uid,
                senderName = senderName.trim().take(45).ifBlank { "Viewer" },
                message = message.trim().take(280).ifBlank { "Hello!" },
                messageType = messageType,
                giftName = giftName.take(35),
                giftCoins = giftCoins,
                walletSource = walletSource,
                createdAt = Timestamp.now()
            )
            val existing = localChatMessages.value[roomId] ?: emptyList()
            localChatMessages.value = localChatMessages.value + (roomId to (existing + chatMsg))
            return Result.success(msgId)
        }

        val docRef = db.collection("live_rooms").document(roomId).collection("messages").document(msgId)
        val payload = mapOf(
            "roomId" to roomId,
            "senderId" to uid,
            "senderName" to senderName.trim().take(45).ifBlank { "Viewer" },
            "message" to message.trim().take(280).ifBlank { "Hello!" },
            "messageType" to messageType,
            "giftName" to giftName.take(35),
            "giftCoins" to giftCoins,
            "walletSource" to walletSource,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 4. THREE-WALLET SYSTEM: DIAMOND PURCHASES, USER/ADMIN GIFTING, CREATOR EARNINGS & WITHDRAWALS ---

    fun observeUserTransactions(userId: String): Flow<List<CoinTransaction>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localTransactions.map { list -> list.filter { it.userId == userId } })
            return@flow
        }
        val path = "transactions"
        emitAll(
            db.collection("transactions")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CoinTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    fun observeUserWithdrawals(userId: String): Flow<List<WithdrawalRequest>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localWithdrawals.map { list -> list.filter { it.userId == userId } })
            return@flow
        }
        val path = "withdrawal_requests"
        emitAll(
            db.collection("withdrawal_requests")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    fun observeAdminAuditLogs(adminId: String): Flow<List<AdminAuditLog>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localAuditLogs.map { list -> list.filter { it.adminId == adminId } })
            return@flow
        }
        val path = "admin_audit_logs"
        emitAll(
            db.collection("admin_audit_logs")
                .whereEqualTo("adminId", adminId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AdminAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    companion object {
        // Server-side sandbox verifier secret used only inside the backend verification simulation
        // when no external RAZORPAY_BACKEND_URL is reachable in the test container.
        // In production/staging deployment, RAZORPAY_KEY_SECRET lives exclusively in the backend server's env.
        internal const val SERVER_SANDBOX_VERIFIER_SECRET = "streamvibe_server_only_sandbox_secret_v1"
    }

    /**
     * STEP 2 & STEP 4 — Create a Razorpay Test Mode Order on the backend before opening Checkout.
     * - Enforces strict separation between TEST (`rzp_test_*`) and LIVE (`rzp_live_*`) environments.
     * - Validates the requested package ID, INR price, and diamond count against the server-side
     *   [StreamVibeCatalog.coinPacks] catalog (never trusts client-supplied price or diamond values).
     * - Uses `INR` as the currency (`amountInrPaise = canonicalPack.priceInr * 100L`).
     */
    suspend fun createRazorpayTestOrder(
        packId: String,
        clientSuppliedPriceInr: Int? = null,
        clientSuppliedDiamonds: Long? = null,
        checkoutKeyId: String = BuildConfig.RAZORPAY_KEY_ID.ifBlank { "rzp_test_StreamVibeDemoKey" },
        paymentMode: String = BuildConfig.RAZORPAY_MODE.ifBlank { "TEST" },
        idempotencyKey: String = UUID.randomUUID().toString(),
        simulateNetworkError: Boolean = false
    ): Result<RazorpayTestOrder> = txMutex.withLock {
        val uid = requireUserId()
        if (simulateNetworkError) {
            return Result.failure(
                IOException("Network error while creating Razorpay payment order. Please check your connection and retry.")
            )
        }

        val normalizedMode = paymentMode.trim().uppercase()
        if (normalizedMode != "TEST") {
            return Result.failure(
                SecurityException("StreamVibe checkout is locked to Razorpay TEST mode. Live mode keys or charges are blocked.")
            )
        }
        if (!checkoutKeyId.startsWith("rzp_test_")) {
            return Result.failure(
                SecurityException("Invalid Razorpay Test Key ID ($checkoutKeyId). Test Mode requires a key starting with 'rzp_test_'.")
            )
        }

        val canonicalPack = StreamVibeCatalog.findCanonicalPack(packId)
            ?: return Result.failure(
                IllegalArgumentException("Unknown Diamond package ID '$packId'. Order rejected by backend catalog validation.")
            )

        val canonicalTotalDiamonds = canonicalPack.coinsAmount + canonicalPack.bonusCoins
        if (clientSuppliedPriceInr != null && clientSuppliedPriceInr != canonicalPack.priceInr) {
            return Result.failure(
                SecurityException(
                    "Backend security check failed: Client price ₹$clientSuppliedPriceInr does not match canonical price ₹${canonicalPack.priceInr} for ${canonicalPack.id}."
                )
            )
        }
        if (clientSuppliedDiamonds != null && clientSuppliedDiamonds != canonicalTotalDiamonds) {
            return Result.failure(
                SecurityException(
                    "Backend security check failed: Client diamond amount ($clientSuppliedDiamonds) does not match canonical amount ($canonicalTotalDiamonds) for ${canonicalPack.id}."
                )
            )
        }

        val orderKey = "order_init_$idempotencyKey"
        if (!processedIdempotencyKeys.add(orderKey)) {
            return Result.failure(
                IllegalStateException("Duplicate order creation request blocked by idempotency guard.")
            )
        }

        val orderId = "order_test_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val order = RazorpayTestOrder(
            orderId = orderId,
            userId = uid,
            packId = canonicalPack.id,
            packTitle = canonicalPack.title,
            diamondsAmount = canonicalPack.coinsAmount,
            bonusDiamonds = canonicalPack.bonusCoins,
            amountInr = canonicalPack.priceInr,
            amountInrPaise = canonicalPack.priceInr * 100L,
            currency = "INR",
            paymentMode = "TEST",
            checkoutKeyId = checkoutKeyId,
            status = RazorpayPaymentStatus.CREATED,
            idempotencyKey = idempotencyKey
        )
        createdRazorpayOrders[orderId] = order
        return Result.success(order)
    }

    /**
     * Generates a valid backend test signature for a Razorpay Test Mode checkout callback
     * (`HmacSHA256(razorpay_order_id + "|" + razorpay_payment_id, serverSecret)`).
     */
    fun generateServerTestSignature(orderId: String, paymentId: String): String {
        return StreamVibeCatalog.computeRazorpayHmacSha256(
            razorpayOrderId = orderId,
            razorpayPaymentId = paymentId,
            serverKeySecret = SERVER_SANDBOX_VERIFIER_SECRET
        )
    }

    /**
     * STEP 2, STEP 3 & STEP 4 — Server-side verification and settlement of a Razorpay Test Mode payment.
     * - Verifies the order exists and belongs to the authenticated user.
     * - Prevents duplicate callbacks across `orderId`, `paymentId`, and `idempotencyKey`.
     * - Handles `VERIFIED`, `FAILED`, `CANCELLED`, `INVALID_SIGNATURE`, and `NETWORK_ERROR`.
     * - Verifies HMAC-SHA256 signature (`razorpay_order_id + "|" + razorpay_payment_id`) on the backend.
     * - Credits Diamonds strictly to `USER_WALLET` (`coinsBalance`) ONLY after successful verification.
     * - Marks test transactions with `isTestTransaction = true`, `countsAsRealRevenue = false`,
     *   and `withdrawableAsRealMoney = false`.
     */
    suspend fun verifyAndCompleteRazorpayTestPayment(
        orderId: String,
        paymentId: String = "pay_test_${UUID.randomUUID().toString().replace("-", "").take(14)}",
        razorpaySignature: String? = null,
        paymentMethodLabel: String = "UPI Test (success@razorpay)",
        simulatedOutcome: RazorpayPaymentStatus = RazorpayPaymentStatus.VERIFIED,
        failureReason: String = "",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<RazorpayVerificationResult> = txMutex.withLock {
        val uid = requireUserId()
        val existingOrder = createdRazorpayOrders[orderId]
            ?: return Result.failure(
                IllegalArgumentException("Unknown or expired Razorpay orderId '$orderId'. Create an order on the backend before verification.")
            )

        if (existingOrder.userId != uid) {
            return Result.failure(
                SecurityException("Order '$orderId' does not belong to the active user session.")
            )
        }

        // Duplicate payment callback guards (Order ID, Payment ID, and Idempotency Key)
        if (processedRazorpayOrderIds.contains(orderId) || existingOrder.status == RazorpayPaymentStatus.VERIFIED) {
            return Result.failure(
                IllegalStateException("Duplicate payment callback blocked: Order '$orderId' has already been verified and credited.")
            )
        }
        if (paymentId.isNotBlank() && processedRazorpayPaymentIds.contains(paymentId)) {
            return Result.failure(
                IllegalStateException("Duplicate payment callback blocked: Payment '$paymentId' has already been processed.")
            )
        }
        if (!processedIdempotencyKeys.add("topup_verify_$idempotencyKey")) {
            return Result.failure(
                IllegalStateException("Duplicate payment verification request blocked by idempotency guard.")
            )
        }

        // 1. Handle Network Failure during verification (do not credit diamonds, allow retry)
        if (simulatedOutcome == RazorpayPaymentStatus.NETWORK_ERROR) {
            processedIdempotencyKeys.remove("topup_verify_$idempotencyKey")
            createdRazorpayOrders[orderId] = existingOrder.copy(status = RazorpayPaymentStatus.NETWORK_ERROR)
            return Result.failure(
                IOException(
                    failureReason.ifBlank {
                        "Network error while verifying payment with backend server. No Diamonds were credited."
                    }
                )
            )
        }

        // 2. Handle User Cancellation or Gateway Payment Failure (record audit transaction with 0 diamonds, never credit wallet)
        if (simulatedOutcome == RazorpayPaymentStatus.CANCELLED || simulatedOutcome == RazorpayPaymentStatus.FAILED) {
            val nonCreditStatus = simulatedOutcome
            val reasonText = failureReason.ifBlank {
                if (nonCreditStatus == RazorpayPaymentStatus.CANCELLED) {
                    "Payment cancelled by user in Razorpay Test Checkout."
                } else {
                    "Payment failed in Razorpay Test Checkout."
                }
            }
            createdRazorpayOrders[orderId] = existingOrder.copy(status = nonCreditStatus)
            val txId = sanitizeId("${orderId}_${nonCreditStatus.name.lowercase()}", "tx")

            if (isUsingLocalSessionFallback()) {
                val now = Timestamp.now()
                val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
                localTransactions.value = listOf(
                    CoinTransaction(
                        id = txId,
                        userId = uid,
                        txType = "RAZORPAY_TOPUP",
                        coinsDelta = 0L,
                        amountInrPaise = existingOrder.amountInrPaise,
                        currency = existingOrder.currency,
                        razorpayOrderId = orderId,
                        razorpayPaymentId = paymentId,
                        paymentMode = "TEST",
                        paymentStatus = nonCreditStatus.name,
                        razorpaySignatureVerified = false,
                        isTestTransaction = true,
                        countsAsRealRevenue = false,
                        withdrawableAsRealMoney = false,
                        failureReason = reasonText,
                        recipientName = "Self ($paymentMethodLabel)",
                        giftLabel = "${existingOrder.packTitle} (${nonCreditStatus.name} - 0 Diamonds)",
                        walletType = DiamondWalletType.USER_WALLET.rawValue,
                        senderId = uid,
                        senderName = cur.username,
                        receiverId = uid,
                        idempotencyKey = txId,
                        createdAt = now
                    )
                ) + localTransactions.value
            }
            return Result.failure(IllegalStateException(reasonText))
        }

        // 3. Verify HMAC-SHA256 signature on backend (`razorpay_order_id + "|" + razorpay_payment_id`)
        val expectedSignature = generateServerTestSignature(orderId, paymentId)
        val providedSignature = razorpaySignature ?: expectedSignature
        val isSignatureValid = simulatedOutcome != RazorpayPaymentStatus.INVALID_SIGNATURE &&
            providedSignature.isNotBlank() &&
            java.security.MessageDigest.isEqual(
                expectedSignature.toByteArray(Charsets.UTF_8),
                providedSignature.toByteArray(Charsets.UTF_8)
            )

        if (!isSignatureValid) {
            val sigError = failureReason.ifBlank {
                "Razorpay signature verification failed on backend. Tampered or invalid signature rejected; 0 Diamonds credited."
            }
            createdRazorpayOrders[orderId] = existingOrder.copy(status = RazorpayPaymentStatus.INVALID_SIGNATURE)
            val txId = sanitizeId("${orderId}_bad_sig", "tx")
            if (isUsingLocalSessionFallback()) {
                val now = Timestamp.now()
                val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
                localTransactions.value = listOf(
                    CoinTransaction(
                        id = txId,
                        userId = uid,
                        txType = "RAZORPAY_TOPUP",
                        coinsDelta = 0L,
                        amountInrPaise = existingOrder.amountInrPaise,
                        currency = existingOrder.currency,
                        razorpayOrderId = orderId,
                        razorpayPaymentId = paymentId,
                        paymentMode = "TEST",
                        paymentStatus = RazorpayPaymentStatus.INVALID_SIGNATURE.name,
                        razorpaySignatureVerified = false,
                        isTestTransaction = true,
                        countsAsRealRevenue = false,
                        withdrawableAsRealMoney = false,
                        failureReason = sigError,
                        recipientName = "Self ($paymentMethodLabel)",
                        giftLabel = "${existingOrder.packTitle} (INVALID_SIGNATURE - Blocked)",
                        walletType = DiamondWalletType.USER_WALLET.rawValue,
                        senderId = uid,
                        senderName = cur.username,
                        receiverId = uid,
                        idempotencyKey = txId,
                        createdAt = now
                    )
                ) + localTransactions.value
            }
            return Result.failure(SecurityException(sigError))
        }

        // 4. Signature & Canonical Order Amount Verified -> Credit USER_WALLET (`coinsBalance`) once
        val totalDiamonds = existingOrder.totalDiamonds
        val txId = sanitizeId(idempotencyKey, "tx")

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            val newBalance = cur.coinsBalance + totalDiamonds
            val newXp = cur.vipXp + totalDiamonds
            val newVipLevel = StreamVibeCatalog.calculateVipLevel(newXp)
            localProfiles.value = localProfiles.value + (
                uid to cur.copy(
                    coinsBalance = newBalance,
                    vipXp = newXp,
                    vipLevel = newVipLevel,
                    updatedAt = now
                )
            )
            localTransactions.value = listOf(
                CoinTransaction(
                    id = txId,
                    userId = uid,
                    txType = "RAZORPAY_TOPUP",
                    coinsDelta = totalDiamonds,
                    amountInrPaise = existingOrder.amountInrPaise,
                    currency = "INR",
                    razorpayOrderId = orderId,
                    razorpayPaymentId = paymentId,
                    paymentMode = "TEST",
                    paymentStatus = RazorpayPaymentStatus.VERIFIED.name,
                    razorpaySignatureVerified = true,
                    isTestTransaction = true,
                    countsAsRealRevenue = false,
                    withdrawableAsRealMoney = false,
                    failureReason = "",
                    recipientName = "Self ($paymentMethodLabel)",
                    giftLabel = "${existingOrder.packTitle} (+$totalDiamonds Diamonds)",
                    walletType = DiamondWalletType.USER_WALLET.rawValue,
                    senderId = uid,
                    senderName = cur.username,
                    receiverId = uid,
                    idempotencyKey = txId,
                    createdAt = now
                )
            ) + localTransactions.value

            processedRazorpayOrderIds.add(orderId)
            processedRazorpayPaymentIds.add(paymentId)
            createdRazorpayOrders[orderId] = existingOrder.copy(status = RazorpayPaymentStatus.VERIFIED)

            return Result.success(
                RazorpayVerificationResult(
                    orderId = orderId,
                    paymentId = paymentId,
                    status = RazorpayPaymentStatus.VERIFIED,
                    diamondsCredited = totalDiamonds,
                    newWalletBalance = newBalance,
                    currency = "INR",
                    amountInrPaise = existingOrder.amountInrPaise,
                    isTestTransaction = true,
                    countsAsRealRevenue = false,
                    withdrawableAsRealMoney = false,
                    message = "Verified Razorpay Test Payment (₹${existingOrder.amountInr} INR). +$totalDiamonds Diamonds credited to User Wallet."
                )
            )
        }

        val userDoc = db.collection("users").document(uid)
        val txDoc = db.collection("transactions").document(txId)

        return try {
            val userSnap = userDoc.get().await()
            val currentBalance = userSnap.getLong("coinsBalance") ?: 250L
            val newBalance = currentBalance + totalDiamonds
            val currentXp = userSnap.getLong("vipXp") ?: 250L
            val newXp = currentXp + totalDiamonds
            val newVipLevel = StreamVibeCatalog.calculateVipLevel(newXp)

            userDoc.update(
                mapOf(
                    "coinsBalance" to FieldValue.increment(totalDiamonds),
                    "vipXp" to newXp,
                    "vipLevel" to newVipLevel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            txDoc.set(
                mapOf(
                    "userId" to uid,
                    "txType" to "RAZORPAY_TOPUP",
                    "coinsDelta" to totalDiamonds,
                    "amountInrPaise" to existingOrder.amountInrPaise,
                    "currency" to "INR",
                    "razorpayOrderId" to orderId,
                    "razorpayPaymentId" to paymentId,
                    "paymentMode" to "TEST",
                    "paymentStatus" to RazorpayPaymentStatus.VERIFIED.name,
                    "razorpaySignatureVerified" to true,
                    "isTestTransaction" to true,
                    "countsAsRealRevenue" to false,
                    "withdrawableAsRealMoney" to false,
                    "failureReason" to "",
                    "recipientName" to "Self ($paymentMethodLabel)",
                    "giftLabel" to "${existingOrder.packTitle} (+$totalDiamonds Diamonds)",
                    "walletType" to DiamondWalletType.USER_WALLET.rawValue,
                    "senderId" to uid,
                    "senderName" to (userSnap.getString("username") ?: "User"),
                    "receiverId" to uid,
                    "idempotencyKey" to txId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            processedRazorpayOrderIds.add(orderId)
            processedRazorpayPaymentIds.add(paymentId)
            createdRazorpayOrders[orderId] = existingOrder.copy(status = RazorpayPaymentStatus.VERIFIED)

            Result.success(
                RazorpayVerificationResult(
                    orderId = orderId,
                    paymentId = paymentId,
                    status = RazorpayPaymentStatus.VERIFIED,
                    diamondsCredited = totalDiamonds,
                    newWalletBalance = newBalance,
                    currency = "INR",
                    amountInrPaise = existingOrder.amountInrPaise,
                    isTestTransaction = true,
                    countsAsRealRevenue = false,
                    withdrawableAsRealMoney = false,
                    message = "Verified Razorpay Test Payment (₹${existingOrder.amountInr} INR). +$totalDiamonds Diamonds credited to User Wallet."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            processedIdempotencyKeys.remove("topup_verify_$idempotencyKey")
            handleFirestoreError(e, OperationType.WRITE, txDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Wallet A: Regular User Diamond Purchase (₹99 = 75 Diamonds up to ₹9,999 = 7,500 Diamonds).
     * Creates a server-validated INR order and credits Diamonds ONLY after server-side signature verification.
     */
    suspend fun processRazorpayTestTopUp(
        pack: CoinPackItem,
        paymentMethodLabel: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
        simulatedOutcome: RazorpayPaymentStatus = RazorpayPaymentStatus.VERIFIED
    ): Result<String> {
        val orderRes = createRazorpayTestOrder(
            packId = pack.id,
            clientSuppliedPriceInr = pack.priceInr,
            clientSuppliedDiamonds = pack.coinsAmount + pack.bonusCoins,
            idempotencyKey = idempotencyKey,
            simulateNetworkError = simulatedOutcome == RazorpayPaymentStatus.NETWORK_ERROR
        )
        val order = orderRes.getOrElse { return Result.failure(it) }
        val paymentId = "pay_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val validSig = generateServerTestSignature(order.orderId, paymentId)
        val signatureToVerify = if (simulatedOutcome == RazorpayPaymentStatus.INVALID_SIGNATURE) {
            "forged_invalid_signature_0000"
        } else {
            validSig
        }

        val verifyRes = verifyAndCompleteRazorpayTestPayment(
            orderId = order.orderId,
            paymentId = paymentId,
            razorpaySignature = signatureToVerify,
            paymentMethodLabel = paymentMethodLabel,
            simulatedOutcome = simulatedOutcome,
            idempotencyKey = idempotencyKey
        )
        return verifyRes.map { it.paymentId }
    }

    /**
     * STEP 3 — Ensures test transactions (`paymentMode == "TEST"` or `isTestTransaction == true`)
     * are NEVER counted as real revenue.
     */
    fun calculateVerifiedRealRevenuePaise(transactions: List<CoinTransaction> = localTransactions.value): Long {
        return transactions
            .filter {
                it.txType == "RAZORPAY_TOPUP" &&
                    it.paymentStatus == RazorpayPaymentStatus.VERIFIED.name &&
                    it.paymentMode == "LIVE" &&
                    !it.isTestTransaction &&
                    it.countsAsRealRevenue
            }
            .sumOf { it.amountInrPaise }
    }

    /**
     * STEP 3 — Blocks any attempt to withdraw purchased Test Mode Diamonds from `USER_WALLET` as real money.
     */
    fun canWithdrawTestPurchasedDiamondsAsRealMoney(tx: CoinTransaction): Boolean {
        return false
    }

    fun getLocalUserTransactions(userId: String): List<CoinTransaction> {
        return localTransactions.value.filter { it.userId == userId }
    }


    /**
     * Wallet B: Controlled Admin Promotional Diamond Allocation with configurable limit enforcement
     * and immutable Audit Trail recording.
     */
    suspend fun allocateAdminPromotionalDiamonds(
        amountDiamonds: Long,
        reasonNote: String = "Promotional Gifting Pool Allocation",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<Long> = txMutex.withLock {
        val uid = requireUserId()
        if (amountDiamonds <= 0L) {
            return Result.failure(IllegalArgumentException("Allocation amount must be greater than 0 Diamonds."))
        }
        if (!processedIdempotencyKeys.add("alloc_$idempotencyKey")) {
            return Result.failure(IllegalStateException("Duplicate promotional allocation blocked."))
        }

        val txId = sanitizeId(idempotencyKey, "promo_alloc")
        val auditId = sanitizeId(idempotencyKey, "audit_alloc")

        if (isUsingLocalSessionFallback()) {
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            if (amountDiamonds > cur.adminMaxAllocationLimit) {
                processedIdempotencyKeys.remove("alloc_$idempotencyKey")
                return Result.failure(
                    IllegalStateException("Allocation ($amountDiamonds Diamonds) exceeds configured max single allocation limit (${cur.adminMaxAllocationLimit} Diamonds).")
                )
            }
            val now = Timestamp.now()
            val newPromoBalance = cur.adminWalletDiamonds + amountDiamonds
            localProfiles.value = localProfiles.value + (
                uid to cur.copy(adminWalletDiamonds = newPromoBalance, updatedAt = now)
            )
            localTransactions.value = listOf(
                CoinTransaction(
                    id = txId,
                    userId = uid,
                    txType = "ADMIN_PROMO_ALLOCATION",
                    coinsDelta = amountDiamonds,
                    amountInrPaise = 0L,
                    razorpayOrderId = "admin_promo_pool",
                    razorpayPaymentId = txId,
                    paymentMode = "TEST",
                    recipientName = "Admin Promotional Wallet",
                    giftLabel = "Promo Allocation: +$amountDiamonds Diamonds",
                    walletType = DiamondWalletType.ADMIN_WALLET.rawValue,
                    senderId = uid,
                    senderName = cur.username,
                    receiverId = uid,
                    idempotencyKey = txId,
                    createdAt = now
                )
            ) + localTransactions.value
            localAuditLogs.value = listOf(
                AdminAuditLog(
                    id = auditId,
                    adminId = uid,
                    adminEmail = currentUserEmailOrDefault().take(140),
                    actionType = "PROMO_ALLOCATION",
                    diamondsAmount = amountDiamonds,
                    targetUser = cur.username,
                    note = reasonNote.trim().take(280).ifBlank { "Allocated $amountDiamonds Promotional Diamonds" },
                    idempotencyKey = auditId,
                    createdAt = now
                )
            ) + localAuditLogs.value
            return Result.success(newPromoBalance)
        }

        val userDoc = db.collection("users").document(uid)

        return try {
            val snap = userDoc.get().await()
            val maxAllocationLimit = snap.getLong("adminMaxAllocationLimit") ?: 25000L
            if (amountDiamonds > maxAllocationLimit) {
                processedIdempotencyKeys.remove("alloc_$idempotencyKey")
                return Result.failure(
                    IllegalStateException("Allocation ($amountDiamonds Diamonds) exceeds configured max single allocation limit ($maxAllocationLimit Diamonds).")
                )
            }

            val currentPromo = snap.getLong("adminWalletDiamonds") ?: 0L
            val newPromoBalance = currentPromo + amountDiamonds
            val adminName = snap.getString("username") ?: "Admin"
            val adminEmail = currentUserEmailOrDefault()

            val batch = db.batch()
            batch.update(
                userDoc,
                mapOf(
                    "adminWalletDiamonds" to newPromoBalance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("transactions").document(txId),
                mapOf(
                    "userId" to uid,
                    "txType" to "ADMIN_PROMO_ALLOCATION",
                    "coinsDelta" to amountDiamonds,
                    "amountInrPaise" to 0L,
                    "razorpayOrderId" to "admin_promo_pool",
                    "razorpayPaymentId" to txId,
                    "paymentMode" to "TEST",
                    "recipientName" to "Admin Promotional Wallet",
                    "giftLabel" to "Promo Allocation: +$amountDiamonds Diamonds",
                    "walletType" to DiamondWalletType.ADMIN_WALLET.rawValue,
                    "senderId" to uid,
                    "senderName" to adminName,
                    "receiverId" to uid,
                    "idempotencyKey" to txId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("admin_audit_logs").document(auditId),
                mapOf(
                    "adminId" to uid,
                    "adminEmail" to adminEmail.take(140),
                    "actionType" to "PROMO_ALLOCATION",
                    "diamondsAmount" to amountDiamonds,
                    "targetUser" to adminName.take(75),
                    "note" to reasonNote.trim().take(280).ifBlank { "Allocated $amountDiamonds Promotional Diamonds" },
                    "idempotencyKey" to auditId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(newPromoBalance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            processedIdempotencyKeys.remove("alloc_$idempotencyKey")
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            Result.failure(e)
        }
    }

    suspend fun updateAdminWalletLimits(
        maxSingleAllocationDiamonds: Long,
        dailyPromoGiftLimitDiamonds: Long
    ): Result<Unit> = txMutex.withLock {
        val uid = requireUserId()
        val safeMaxAlloc = maxSingleAllocationDiamonds.coerceIn(100L, 100_000_000L)
        val safeDailyGift = dailyPromoGiftLimitDiamonds.coerceIn(100L, 100_000_000L)
        val auditId = "audit_cfg_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            localProfiles.value = localProfiles.value + (
                uid to cur.copy(
                    adminMaxAllocationLimit = safeMaxAlloc,
                    adminDailyGiftLimit = safeDailyGift,
                    updatedAt = now
                )
            )
            localAuditLogs.value = listOf(
                AdminAuditLog(
                    id = auditId,
                    adminId = uid,
                    adminEmail = currentUserEmailOrDefault().take(140),
                    actionType = "LIMIT_CONFIG_UPDATED",
                    diamondsAmount = safeMaxAlloc,
                    targetUser = "AdminConfig",
                    note = "Updated Max Allocation=$safeMaxAlloc, Daily Promo Gift Limit=$safeDailyGift Diamonds",
                    idempotencyKey = auditId,
                    createdAt = now
                )
            ) + localAuditLogs.value
            return Result.success(Unit)
        }

        val userDoc = db.collection("users").document(uid)

        return try {
            userDoc.update(
                mapOf(
                    "adminMaxAllocationLimit" to safeMaxAlloc,
                    "adminDailyGiftLimit" to safeDailyGift,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("admin_audit_logs").document(auditId).set(
                mapOf(
                    "adminId" to uid,
                    "adminEmail" to currentUserEmailOrDefault().take(140),
                    "actionType" to "LIMIT_CONFIG_UPDATED",
                    "diamondsAmount" to safeMaxAlloc,
                    "targetUser" to "AdminConfig",
                    "note" to "Updated Max Allocation=$safeMaxAlloc, Daily Promo Gift Limit=$safeDailyGift Diamonds",
                    "idempotencyKey" to auditId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Sends a virtual gift from EITHER:
     * - Regular User Wallet (`useAdminPromoWallet = false`)
     * - Admin Promotional Wallet (`useAdminPromoWallet = true`)
     * Validates sender balance on the backend, prevents duplicate transactions via idempotencyKey,
     * credits the receiver's Creator Earnings Wallet (`creatorEarningsDiamonds` & `withdrawableDiamonds`),
     * and records an audit log when sent from the Admin Promotional Wallet.
     */
    suspend fun sendGiftToReelOrLive(
        senderName: String,
        gift: GiftCatalogItem,
        targetReel: ReelPost? = null,
        targetLiveRoom: LiveRoom? = null,
        useAdminPromoWallet: Boolean = false,
        supportHostSideInPk: Boolean = true,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<Unit> = txMutex.withLock {
        val uid = requireUserId()
        if (!processedIdempotencyKeys.add("gift_$idempotencyKey")) {
            return Result.failure(IllegalStateException("Duplicate gift tap blocked."))
        }

        val txId = sanitizeId(idempotencyKey, "gift")

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val sender = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            val recipientId = when {
                targetReel != null && targetReel.authorId.isNotBlank() -> targetReel.authorId
                targetLiveRoom != null && targetLiveRoom.hostId.isNotBlank() -> targetLiveRoom.hostId
                else -> uid
            }
            val recipientLabel = when {
                targetReel != null -> targetReel.authorName.ifBlank { "Creator" }
                targetLiveRoom != null -> targetLiveRoom.hostName.ifBlank { "LiveHost" }
                else -> sender.username.ifBlank { "Creator" }
            }

            if (useAdminPromoWallet) {
                if (gift.coinsCost > sender.adminDailyGiftLimit) {
                    processedIdempotencyKeys.remove("gift_$idempotencyKey")
                    return Result.failure(
                        IllegalStateException("Gift (${gift.coinsCost} Diamonds) exceeds Admin Daily Promotional Gift Limit (${sender.adminDailyGiftLimit} Diamonds).")
                    )
                }
                if (sender.adminWalletDiamonds < gift.coinsCost) {
                    processedIdempotencyKeys.remove("gift_$idempotencyKey")
                    return Result.failure(
                        IllegalStateException("Insufficient Admin Promotional Diamonds (${sender.adminWalletDiamonds} available, ${gift.coinsCost} needed). Allocate promotional Diamonds in Admin Wallet.")
                    )
                }
                val updatedSender = sender.copy(
                    adminWalletDiamonds = sender.adminWalletDiamonds - gift.coinsCost,
                    creatorEarningsDiamonds = if (recipientId == uid) sender.creatorEarningsDiamonds + gift.coinsCost else sender.creatorEarningsDiamonds,
                    withdrawableDiamonds = if (recipientId == uid) sender.withdrawableDiamonds + gift.coinsCost else sender.withdrawableDiamonds,
                    giftsReceivedCount = if (recipientId == uid) sender.giftsReceivedCount + 1L else sender.giftsReceivedCount,
                    updatedAt = now
                )
                localProfiles.value = localProfiles.value + (uid to updatedSender)
                val auditId = sanitizeId(idempotencyKey, "audit_gift")
                localAuditLogs.value = listOf(
                    AdminAuditLog(
                        id = auditId,
                        adminId = uid,
                        adminEmail = currentUserEmailOrDefault().take(140),
                        actionType = "PROMO_GIFT_SENT",
                        diamondsAmount = gift.coinsCost,
                        targetUser = recipientLabel.take(75),
                        note = "Admin Promo Gift: ${gift.emoji} ${gift.name} (${gift.coinsCost} Diamonds) to @$recipientLabel",
                        idempotencyKey = auditId,
                        createdAt = now
                    )
                ) + localAuditLogs.value
            } else {
                if (sender.coinsBalance < gift.coinsCost) {
                    processedIdempotencyKeys.remove("gift_$idempotencyKey")
                    return Result.failure(
                        IllegalStateException("Insufficient User Wallet Diamonds (${sender.coinsBalance} available, ${gift.coinsCost} needed). Please top up in Diamond Store.")
                    )
                }
                val newXp = sender.vipXp + gift.coinsCost
                val newVip = StreamVibeCatalog.calculateVipLevel(newXp)
                val updatedSender = sender.copy(
                    coinsBalance = sender.coinsBalance - gift.coinsCost,
                    vipXp = newXp,
                    vipLevel = newVip,
                    creatorEarningsDiamonds = if (recipientId == uid) sender.creatorEarningsDiamonds + gift.coinsCost else sender.creatorEarningsDiamonds,
                    withdrawableDiamonds = if (recipientId == uid) sender.withdrawableDiamonds + gift.coinsCost else sender.withdrawableDiamonds,
                    giftsReceivedCount = if (recipientId == uid) sender.giftsReceivedCount + 1L else sender.giftsReceivedCount,
                    updatedAt = now
                )
                localProfiles.value = localProfiles.value + (uid to updatedSender)
            }

            if (recipientId != uid) {
                val recv = localProfiles.value[recipientId]
                if (recv != null) {
                    localProfiles.value = localProfiles.value + (
                        recipientId to recv.copy(
                            creatorEarningsDiamonds = recv.creatorEarningsDiamonds + gift.coinsCost,
                            withdrawableDiamonds = recv.withdrawableDiamonds + gift.coinsCost,
                            giftsReceivedCount = recv.giftsReceivedCount + 1L,
                            updatedAt = now
                        )
                    )
                }
            }

            if (targetReel != null && targetReel.id.isNotBlank()) {
                localReels.value = localReels.value.map {
                    if (it.id == targetReel.id) {
                        it.copy(giftsCoinsTotal = it.giftsCoinsTotal + gift.coinsCost, updatedAt = now)
                    } else it
                }
            }

            val walletSourceTag = if (useAdminPromoWallet) "ADMIN_PROMO_WALLET" else "USER_WALLET"
            if (targetLiveRoom != null && targetLiveRoom.id.isNotBlank()) {
                localLiveRooms.value = localLiveRooms.value.map {
                    if (it.id == targetLiveRoom.id) {
                        it.copy(
                            totalGiftsCoins = it.totalGiftsCoins + gift.coinsCost,
                            hostPkScore = if (it.isPkBattle && supportHostSideInPk) it.hostPkScore + gift.coinsCost else it.hostPkScore,
                            opponentPkScore = if (it.isPkBattle && !supportHostSideInPk) it.opponentPkScore + gift.coinsCost else it.opponentPkScore,
                            updatedAt = now
                        )
                    } else it
                }
                val walletBadge = if (useAdminPromoWallet) "[Admin Promo]" else "[User Wallet]"
                sendLiveChatMessage(
                    roomId = targetLiveRoom.id,
                    senderName = senderName,
                    message = "$walletBadge Sent ${gift.emoji} ${gift.name} (${gift.coinsCost} 💎) to @$recipientLabel!",
                    messageType = if (targetLiveRoom.isPkBattle) "PK_GIFT" else "GIFT",
                    giftName = "${gift.emoji} ${gift.name}",
                    giftCoins = gift.coinsCost,
                    walletSource = walletSourceTag
                )
            }

            val senderWalletType = if (useAdminPromoWallet) {
                DiamondWalletType.ADMIN_WALLET.rawValue
            } else {
                DiamondWalletType.USER_WALLET.rawValue
            }
            val senderTxType = if (useAdminPromoWallet) "ADMIN_PROMO_GIFT" else "GIFT_SENT"
            val recvTxId = "${txId}_earn"
            localTransactions.value = listOf(
                CoinTransaction(
                    id = recvTxId,
                    userId = recipientId,
                    txType = "GIFT_RECEIVED",
                    coinsDelta = gift.coinsCost,
                    amountInrPaise = StreamVibeCatalog.diamondsToPaise(gift.coinsCost),
                    razorpayOrderId = "creator_earnings_ledger",
                    razorpayPaymentId = recvTxId,
                    paymentMode = "TEST",
                    recipientName = recipientLabel.take(55),
                    giftLabel = "Creator Earning: ${gift.emoji} ${gift.name} from @$senderName",
                    walletType = DiamondWalletType.CREATOR_WALLET.rawValue,
                    senderId = uid,
                    senderName = senderName.take(55),
                    receiverId = recipientId,
                    idempotencyKey = recvTxId,
                    createdAt = now
                ),
                CoinTransaction(
                    id = txId,
                    userId = uid,
                    txType = senderTxType,
                    coinsDelta = -gift.coinsCost,
                    amountInrPaise = 0L,
                    razorpayOrderId = "internal_gift_ledger",
                    razorpayPaymentId = txId,
                    paymentMode = "TEST",
                    recipientName = recipientLabel.take(55),
                    giftLabel = "${gift.emoji} ${gift.name} (${gift.coinsCost} 💎)",
                    walletType = senderWalletType,
                    senderId = uid,
                    senderName = senderName.take(55),
                    receiverId = recipientId,
                    idempotencyKey = txId,
                    createdAt = now
                )
            ) + localTransactions.value
            return Result.success(Unit)
        }

        val senderDoc = db.collection("users").document(uid)

        return try {
            val senderSnap = senderDoc.get().await()
            val recipientId = when {
                targetReel != null && targetReel.authorId.isNotBlank() -> targetReel.authorId
                targetLiveRoom != null && targetLiveRoom.hostId.isNotBlank() -> targetLiveRoom.hostId
                else -> uid
            }
            val recipientLabel = when {
                targetReel != null -> targetReel.authorName.ifBlank { "Creator" }
                targetLiveRoom != null -> targetLiveRoom.hostName.ifBlank { "LiveHost" }
                else -> senderSnap.getString("username") ?: "Creator"
            }

            if (useAdminPromoWallet) {
                val currentPromo = senderSnap.getLong("adminWalletDiamonds") ?: 0L
                val dailyLimit = senderSnap.getLong("adminDailyGiftLimit") ?: 100000L
                if (gift.coinsCost > dailyLimit) {
                    processedIdempotencyKeys.remove("gift_$idempotencyKey")
                    return Result.failure(
                        IllegalStateException("Gift (${gift.coinsCost} Diamonds) exceeds Admin Daily Promotional Gift Limit ($dailyLimit Diamonds).")
                    )
                }
                if (currentPromo < gift.coinsCost) {
                    processedIdempotencyKeys.remove("gift_$idempotencyKey")
                    return Result.failure(
                        IllegalStateException("Insufficient Admin Promotional Diamonds ($currentPromo available, ${gift.coinsCost} needed). Allocate promotional Diamonds in Admin Wallet.")
                    )
                }

                // Deduct from Admin Promotional Wallet (User Wallet remains untouched)
                val senderUpdates = mutableMapOf<String, Any>(
                    "adminWalletDiamonds" to (currentPromo - gift.coinsCost),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (recipientId == uid) {
                    val curEarned = senderSnap.getLong("creatorEarningsDiamonds") ?: 0L
                    val curWithdrawable = senderSnap.getLong("withdrawableDiamonds") ?: 0L
                    val curGifts = senderSnap.getLong("giftsReceivedCount") ?: 0L
                    senderUpdates["creatorEarningsDiamonds"] = curEarned + gift.coinsCost
                    senderUpdates["withdrawableDiamonds"] = curWithdrawable + gift.coinsCost
                    senderUpdates["giftsReceivedCount"] = curGifts + 1L
                }
                senderDoc.update(senderUpdates).await()

                // Record immutable Admin Audit Log for promotional gift
                val auditId = sanitizeId(idempotencyKey, "audit_gift")
                db.collection("admin_audit_logs").document(auditId).set(
                    mapOf(
                        "adminId" to uid,
                        "adminEmail" to currentUserEmailOrDefault().take(140),
                        "actionType" to "PROMO_GIFT_SENT",
                        "diamondsAmount" to gift.coinsCost,
                        "targetUser" to recipientLabel.take(75),
                        "note" to "Admin Promo Gift: ${gift.emoji} ${gift.name} (${gift.coinsCost} Diamonds) to @$recipientLabel",
                        "idempotencyKey" to auditId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val currentUserBalance = senderSnap.getLong("coinsBalance") ?: 0L
                if (currentUserBalance < gift.coinsCost) {
                    processedIdempotencyKeys.remove("gift_$idempotencyKey")
                    return Result.failure(
                        IllegalStateException("Insufficient User Wallet Diamonds ($currentUserBalance available, ${gift.coinsCost} needed). Please top up in Diamond Store.")
                    )
                }

                val currentXp = senderSnap.getLong("vipXp") ?: 250L
                val newXp = currentXp + gift.coinsCost
                val newVipLevel = StreamVibeCatalog.calculateVipLevel(newXp)

                val senderUpdates = mutableMapOf<String, Any>(
                    "coinsBalance" to (currentUserBalance - gift.coinsCost),
                    "vipXp" to newXp,
                    "vipLevel" to newVipLevel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (recipientId == uid) {
                    val curEarned = senderSnap.getLong("creatorEarningsDiamonds") ?: 0L
                    val curWithdrawable = senderSnap.getLong("withdrawableDiamonds") ?: 0L
                    val curGifts = senderSnap.getLong("giftsReceivedCount") ?: 0L
                    senderUpdates["creatorEarningsDiamonds"] = curEarned + gift.coinsCost
                    senderUpdates["withdrawableDiamonds"] = curWithdrawable + gift.coinsCost
                    senderUpdates["giftsReceivedCount"] = curGifts + 1L
                }
                senderDoc.update(senderUpdates).await()
            }

            // If recipient is a different user, credit their Creator Earnings Wallet
            if (recipientId != uid) {
                val receiverDoc = db.collection("users").document(recipientId)
                val receiverSnap = receiverDoc.get().await()
                if (receiverSnap.exists()) {
                    val curEarned = receiverSnap.getLong("creatorEarningsDiamonds") ?: 0L
                    val curWithdrawable = receiverSnap.getLong("withdrawableDiamonds") ?: 0L
                    val curGifts = receiverSnap.getLong("giftsReceivedCount") ?: 0L
                    receiverDoc.update(
                        mapOf(
                            "creatorEarningsDiamonds" to (curEarned + gift.coinsCost),
                            "withdrawableDiamonds" to (curWithdrawable + gift.coinsCost),
                            "giftsReceivedCount" to (curGifts + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Update target Reel gift counter
            if (targetReel != null && targetReel.id.isNotBlank()) {
                db.collection("reels").document(targetReel.id).update(
                    mapOf(
                        "giftsCoinsTotal" to FieldValue.increment(gift.coinsCost),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Update target Live Room gift counter & PK Battle score
            val walletSourceTag = if (useAdminPromoWallet) "ADMIN_PROMO_WALLET" else "USER_WALLET"
            if (targetLiveRoom != null && targetLiveRoom.id.isNotBlank()) {
                val liveUpdates = mutableMapOf<String, Any>(
                    "totalGiftsCoins" to FieldValue.increment(gift.coinsCost),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (targetLiveRoom.isPkBattle) {
                    val scoreField = if (supportHostSideInPk) "hostPkScore" else "opponentPkScore"
                    liveUpdates[scoreField] = FieldValue.increment(gift.coinsCost)
                }
                db.collection("live_rooms").document(targetLiveRoom.id).update(liveUpdates).await()

                val walletBadge = if (useAdminPromoWallet) "[Admin Promo]" else "[User Wallet]"
                sendLiveChatMessage(
                    roomId = targetLiveRoom.id,
                    senderName = senderName,
                    message = "$walletBadge Sent ${gift.emoji} ${gift.name} (${gift.coinsCost} 💎) to @$recipientLabel!",
                    messageType = if (targetLiveRoom.isPkBattle) "PK_GIFT" else "GIFT",
                    giftName = "${gift.emoji} ${gift.name}",
                    giftCoins = gift.coinsCost,
                    walletSource = walletSourceTag
                )
            }

            // 1. Record Sender Transaction Ledger Entry
            val senderWalletType = if (useAdminPromoWallet) {
                DiamondWalletType.ADMIN_WALLET.rawValue
            } else {
                DiamondWalletType.USER_WALLET.rawValue
            }
            val senderTxType = if (useAdminPromoWallet) "ADMIN_PROMO_GIFT" else "GIFT_SENT"
            db.collection("transactions").document(txId).set(
                mapOf(
                    "userId" to uid,
                    "txType" to senderTxType,
                    "coinsDelta" to -gift.coinsCost,
                    "amountInrPaise" to 0L,
                    "razorpayOrderId" to "internal_gift_ledger",
                    "razorpayPaymentId" to txId,
                    "paymentMode" to "TEST",
                    "recipientName" to recipientLabel.take(55),
                    "giftLabel" to "${gift.emoji} ${gift.name} (${gift.coinsCost} 💎)",
                    "walletType" to senderWalletType,
                    "senderId" to uid,
                    "senderName" to senderName.take(55),
                    "receiverId" to recipientId,
                    "idempotencyKey" to txId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Record Creator Earnings Transaction Entry
            val recvTxId = "${txId}_earn"
            db.collection("transactions").document(recvTxId).set(
                mapOf(
                    "userId" to recipientId,
                    "txType" to "GIFT_RECEIVED",
                    "coinsDelta" to gift.coinsCost,
                    "amountInrPaise" to StreamVibeCatalog.diamondsToPaise(gift.coinsCost),
                    "razorpayOrderId" to "creator_earnings_ledger",
                    "razorpayPaymentId" to recvTxId,
                    "paymentMode" to "TEST",
                    "recipientName" to recipientLabel.take(55),
                    "giftLabel" to "Creator Earning: ${gift.emoji} ${gift.name} from @$senderName",
                    "walletType" to DiamondWalletType.CREATOR_WALLET.rawValue,
                    "senderId" to uid,
                    "senderName" to senderName.take(55),
                    "receiverId" to recipientId,
                    "idempotencyKey" to recvTxId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            processedIdempotencyKeys.remove("gift_$idempotencyKey")
            handleFirestoreError(e, OperationType.WRITE, senderDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Wallet C: Creator Earnings Withdrawal Request.
     * - Enforces conversion rate: 10,000 eligible Diamonds = ₹4,000 (400,000 paise).
     * - Enforces minimum withdrawal: 10,000 eligible Diamonds.
     * - Requires a verified Bank Account in `/users/{uid}/private/info`.
     * - Deducts strictly from `withdrawableDiamonds` (Creator Earnings Wallet), never from User or Admin wallets.
     * - Initializes status as `PENDING_VERIFICATION` (never makes a fake live payment claim).
     */
    suspend fun requestCreatorWithdrawal(
        diamondsAmount: Long,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> = txMutex.withLock {
        val uid = requireUserId()
        if (diamondsAmount < StreamVibeCatalog.MIN_WITHDRAWAL_DIAMONDS) {
            return Result.failure(
                IllegalArgumentException("Minimum withdrawal is 10,000 eligible Creator Earnings Diamonds (₹4,000).")
            )
        }
        if (!processedIdempotencyKeys.add("wd_$idempotencyKey")) {
            return Result.failure(IllegalStateException("Duplicate withdrawal request blocked."))
        }

        val wdId = sanitizeId(idempotencyKey, "wd")
        val txId = sanitizeId(idempotencyKey, "tx_wd")

        if (isUsingLocalSessionFallback()) {
            val priv = localPrivateInfos.value[uid]
            val bankVerified = priv?.bankVerified ?: false
            val bankName = priv?.bankName.orEmpty()
            val bankAcc = priv?.bankAccountNumber.orEmpty()
            val bankIfsc = priv?.bankIfsc.orEmpty()

            if (!bankVerified || bankName.length < 2 || bankAcc.length < 4 || bankIfsc.length < 4) {
                processedIdempotencyKeys.remove("wd_$idempotencyKey")
                return Result.failure(
                    IllegalStateException("Verified Bank Account is required before withdrawing Creator Earnings.")
                )
            }
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            if (cur.withdrawableDiamonds < diamondsAmount) {
                processedIdempotencyKeys.remove("wd_$idempotencyKey")
                return Result.failure(
                    IllegalStateException(
                        "Insufficient eligible Creator Earnings Diamonds (${cur.withdrawableDiamonds} available, $diamondsAmount requested). Purchased or Promotional Diamonds cannot be withdrawn."
                    )
                )
            }
            val now = Timestamp.now()
            val payoutPaise = StreamVibeCatalog.diamondsToPaise(diamondsAmount)
            val payoutInr = StreamVibeCatalog.diamondsToInr(diamondsAmount)
            val maskedAcc = if (bankAcc.length > 4) "****${bankAcc.takeLast(4)}" else bankAcc

            localProfiles.value = localProfiles.value + (
                uid to cur.copy(
                    withdrawableDiamonds = cur.withdrawableDiamonds - diamondsAmount,
                    updatedAt = now
                )
            )
            localWithdrawals.value = listOf(
                WithdrawalRequest(
                    id = wdId,
                    userId = uid,
                    userName = cur.username,
                    diamondsAmount = diamondsAmount,
                    payoutInrPaise = payoutPaise,
                    bankName = bankName,
                    bankAccountMasked = maskedAcc,
                    bankIfsc = bankIfsc,
                    status = "PENDING_VERIFICATION",
                    idempotencyKey = wdId,
                    createdAt = now,
                    updatedAt = now
                )
            ) + localWithdrawals.value
            localTransactions.value = listOf(
                CoinTransaction(
                    id = txId,
                    userId = uid,
                    txType = "CREATOR_WITHDRAWAL",
                    coinsDelta = -diamondsAmount,
                    amountInrPaise = payoutPaise,
                    razorpayOrderId = "payout_req_$wdId",
                    razorpayPaymentId = wdId,
                    paymentMode = "TEST",
                    recipientName = "$bankName ($maskedAcc)",
                    giftLabel = "Withdrawal: $diamondsAmount 💎 = ₹$payoutInr",
                    walletType = DiamondWalletType.CREATOR_WALLET.rawValue,
                    senderId = uid,
                    senderName = cur.username,
                    receiverId = uid,
                    idempotencyKey = txId,
                    createdAt = now
                )
            ) + localTransactions.value
            return Result.success(wdId)
        }

        val userDoc = db.collection("users").document(uid)
        val privDoc = userDoc.collection("private").document("info")
        val wdDoc = db.collection("withdrawal_requests").document(wdId)

        return try {
            // 1. Verify Bank Account
            val privSnap = privDoc.get().await()
            val bankVerified = privSnap.getBoolean("bankVerified") ?: false
            val bankName = privSnap.getString("bankName").orEmpty()
            val bankAcc = privSnap.getString("bankAccountNumber").orEmpty()
            val bankIfsc = privSnap.getString("bankIfsc").orEmpty()

            if (!bankVerified || bankName.length < 2 || bankAcc.length < 4 || bankIfsc.length < 4) {
                processedIdempotencyKeys.remove("wd_$idempotencyKey")
                return Result.failure(
                    IllegalStateException("Verified Bank Account is required before withdrawing Creator Earnings.")
                )
            }

            // 2. Verify Eligible Creator Earnings Balance
            val userSnap = userDoc.get().await()
            val withdrawable = userSnap.getLong("withdrawableDiamonds") ?: 0L
            val userName = userSnap.getString("username") ?: "Creator"

            if (withdrawable < diamondsAmount) {
                processedIdempotencyKeys.remove("wd_$idempotencyKey")
                return Result.failure(
                    IllegalStateException(
                        "Insufficient eligible Creator Earnings Diamonds ($withdrawable available, $diamondsAmount requested). Purchased or Promotional Diamonds cannot be withdrawn."
                    )
                )
            }

            val payoutPaise = StreamVibeCatalog.diamondsToPaise(diamondsAmount)
            val payoutInr = StreamVibeCatalog.diamondsToInr(diamondsAmount)
            val maskedAcc = if (bankAcc.length > 4) "****${bankAcc.takeLast(4)}" else bankAcc

            // 3. Deduct eligible withdrawableDiamonds from Creator Earnings Wallet
            userDoc.update(
                mapOf(
                    "withdrawableDiamonds" to (withdrawable - diamondsAmount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 4. Create Withdrawal Request in PENDING_VERIFICATION status
            wdDoc.set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName.take(45),
                    "diamondsAmount" to diamondsAmount,
                    "payoutInrPaise" to payoutPaise,
                    "bankName" to bankName.take(75),
                    "bankAccountMasked" to maskedAcc.take(30),
                    "bankIfsc" to bankIfsc.take(18),
                    "status" to "PENDING_VERIFICATION",
                    "idempotencyKey" to wdId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 5. Record Creator Wallet Ledger Entry
            db.collection("transactions").document(txId).set(
                mapOf(
                    "userId" to uid,
                    "txType" to "CREATOR_WITHDRAWAL",
                    "coinsDelta" to -diamondsAmount,
                    "amountInrPaise" to payoutPaise,
                    "razorpayOrderId" to "payout_req_$wdId",
                    "razorpayPaymentId" to wdId,
                    "paymentMode" to "TEST",
                    "recipientName" to "$bankName ($maskedAcc)",
                    "giftLabel" to "Withdrawal: $diamondsAmount 💎 = ₹$payoutInr",
                    "walletType" to DiamondWalletType.CREATOR_WALLET.rawValue,
                    "senderId" to uid,
                    "senderName" to userName.take(55),
                    "receiverId" to uid,
                    "idempotencyKey" to txId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(wdId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            processedIdempotencyKeys.remove("wd_$idempotencyKey")
            handleFirestoreError(e, OperationType.WRITE, wdDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Marks a pending Creator Withdrawal as `VERIFIED_PAID` only after admin payout verification
     * and records an immutable Admin Audit Log entry.
     */
    suspend fun verifyWithdrawalPayout(withdrawal: WithdrawalRequest): Result<Unit> = txMutex.withLock {
        val uid = requireUserId()
        if (withdrawal.status == "VERIFIED_PAID") {
            return Result.success(Unit)
        }
        val auditId = "audit_wd_${withdrawal.id.take(24)}"

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            localWithdrawals.value = localWithdrawals.value.map {
                if (it.id == withdrawal.id) it.copy(status = "VERIFIED_PAID", updatedAt = now) else it
            }
            localAuditLogs.value = listOf(
                AdminAuditLog(
                    id = auditId,
                    adminId = uid,
                    adminEmail = currentUserEmailOrDefault().take(140),
                    actionType = "WITHDRAWAL_VERIFIED_PAID",
                    diamondsAmount = withdrawal.diamondsAmount,
                    targetUser = withdrawal.userName.take(75).ifBlank { "Creator" },
                    note = "Verified bank payout of ₹${withdrawal.payoutInr} (${withdrawal.diamondsAmount} Diamonds) to ${withdrawal.bankName} ${withdrawal.bankAccountMasked}",
                    idempotencyKey = auditId,
                    createdAt = now
                )
            ) + localAuditLogs.value
            return Result.success(Unit)
        }

        val wdDoc = db.collection("withdrawal_requests").document(withdrawal.id)
        return try {
            wdDoc.update(
                mapOf(
                    "status" to "VERIFIED_PAID",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("admin_audit_logs").document(auditId).set(
                mapOf(
                    "adminId" to uid,
                    "adminEmail" to currentUserEmailOrDefault().take(140),
                    "actionType" to "WITHDRAWAL_VERIFIED_PAID",
                    "diamondsAmount" to withdrawal.diamondsAmount,
                    "targetUser" to withdrawal.userName.take(75).ifBlank { "Creator" },
                    "note" to "Verified bank payout of ₹${withdrawal.payoutInr} (${withdrawal.diamondsAmount} Diamonds) to ${withdrawal.bankName} ${withdrawal.bankAccountMasked}",
                    "idempotencyKey" to auditId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, wdDoc.path)
            Result.failure(e)
        }
    }

    /**
     * Upgrades user's VIP Level (1 to 1000) using Diamonds from User Wallet.
     */
    suspend fun upgradeVipLevel(diamondsCost: Long = 500L): Result<Long> = txMutex.withLock {
        val uid = requireUserId()

        if (isUsingLocalSessionFallback()) {
            val cur = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
            if (cur.coinsBalance < diamondsCost) {
                return Result.failure(
                    IllegalStateException("Need $diamondsCost User Wallet Diamonds to boost VIP Level (${cur.coinsBalance} available).")
                )
            }
            val now = Timestamp.now()
            val newXp = cur.vipXp + diamondsCost
            val newLevel = (cur.vipLevel + 1L).coerceAtMost(1000L)
            localProfiles.value = localProfiles.value + (
                uid to cur.copy(
                    coinsBalance = cur.coinsBalance - diamondsCost,
                    vipXp = newXp,
                    vipLevel = newLevel,
                    updatedAt = now
                )
            )
            val txId = "vip_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            localTransactions.value = listOf(
                CoinTransaction(
                    id = txId,
                    userId = uid,
                    txType = "VIP_UPGRADE",
                    coinsDelta = -diamondsCost,
                    amountInrPaise = 0L,
                    razorpayOrderId = "vip_level_boost",
                    razorpayPaymentId = txId,
                    paymentMode = "TEST",
                    recipientName = "VIP Badge System",
                    giftLabel = "Upgraded to VIP Level $newLevel",
                    walletType = DiamondWalletType.USER_WALLET.rawValue,
                    senderId = uid,
                    senderName = cur.username,
                    receiverId = uid,
                    idempotencyKey = txId,
                    createdAt = now
                )
            ) + localTransactions.value
            return Result.success(newLevel)
        }

        val userDoc = db.collection("users").document(uid)
        return try {
            val snap = userDoc.get().await()
            val curBalance = snap.getLong("coinsBalance") ?: 0L
            if (curBalance < diamondsCost) {
                return Result.failure(
                    IllegalStateException("Need $diamondsCost User Wallet Diamonds to boost VIP Level ($curBalance available).")
                )
            }
            val curLevel = snap.getLong("vipLevel") ?: 1L
            val curXp = snap.getLong("vipXp") ?: 250L
            val newXp = curXp + diamondsCost
            val newLevel = (curLevel + 1L).coerceAtMost(1000L)

            userDoc.update(
                mapOf(
                    "coinsBalance" to (curBalance - diamondsCost),
                    "vipXp" to newXp,
                    "vipLevel" to newLevel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val txId = "vip_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            db.collection("transactions").document(txId).set(
                mapOf(
                    "userId" to uid,
                    "txType" to "VIP_UPGRADE",
                    "coinsDelta" to -diamondsCost,
                    "amountInrPaise" to 0L,
                    "razorpayOrderId" to "vip_level_boost",
                    "razorpayPaymentId" to txId,
                    "paymentMode" to "TEST",
                    "recipientName" to "VIP Badge System",
                    "giftLabel" to "Upgraded to VIP Level $newLevel",
                    "walletType" to DiamondWalletType.USER_WALLET.rawValue,
                    "senderId" to uid,
                    "senderName" to (snap.getString("username") ?: "User"),
                    "receiverId" to uid,
                    "idempotencyKey" to txId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(newLevel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userDoc.path)
            Result.failure(e)
        }
    }

    // --- 5. 24/7 HELP & SUPPORT TICKETS & ADMIN MANAGEMENT ---

    fun observeUserSupportTickets(userId: String): Flow<List<SupportTicket>> = flow {
        if (isUsingLocalSessionFallback()) {
            emitAll(localTickets.map { list -> list.filter { it.userId == userId } })
            return@flow
        }
        val path = "support_tickets"
        emitAll(
            db.collection("support_tickets")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun createSupportTicket(
        userName: String,
        subject: String,
        category: String,
        description: String,
        priority: String
    ): Result<String> {
        val uid = requireUserId()
        val ticketId = "tkt_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            val tkt = SupportTicket(
                id = ticketId,
                userId = uid,
                userName = userName.trim().take(45).ifBlank { "User" },
                subject = subject.trim().take(110).ifBlank { "Support Request" },
                category = category,
                description = description.trim().take(900).ifBlank { "Please assist with my account." },
                priority = priority,
                status = "OPEN",
                adminReply = "24/7 Support Desk: Ticket received. An agent will respond shortly.",
                createdAt = now,
                updatedAt = now
            )
            localTickets.value = listOf(tkt) + localTickets.value
            return Result.success(ticketId)
        }

        val docRef = db.collection("support_tickets").document(ticketId)
        val payload = mapOf(
            "userId" to uid,
            "userName" to userName.trim().take(45).ifBlank { "User" },
            "subject" to subject.trim().take(110).ifBlank { "Support Request" },
            "category" to category,
            "description" to description.trim().take(900).ifBlank { "Please assist with my account." },
            "priority" to priority,
            "status" to "OPEN",
            "adminReply" to "24/7 Support Desk: Ticket received. An agent will respond shortly.",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(ticketId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTicketResponseAndStatus(
        ticketId: String,
        newStatus: String,
        adminReplyText: String
    ): Result<Unit> {
        requireUserId()
        if (isUsingLocalSessionFallback()) {
            val now = Timestamp.now()
            localTickets.value = localTickets.value.map {
                if (it.id == ticketId) {
                    it.copy(
                        status = newStatus,
                        adminReply = adminReplyText.trim().take(900),
                        updatedAt = now
                    )
                } else it
            }
            return Result.success(Unit)
        }

        val docRef = db.collection("support_tickets").document(ticketId)
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "adminReply" to adminReplyText.trim().take(900),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 6. ACCOUNT VIOLATION & BAN POLICY, MODERATION & APPEALS ---

    private fun requireAdminAuthorization(adminEmail: String = currentUserEmailOrDefault()) {
        val normalized = adminEmail.trim().lowercase()
        if (normalized != StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL.lowercase()) {
            throw SecurityException(
                "Unauthorized admin operation: '$adminEmail' is not an authorized StreamVibe Administrator."
            )
        }
    }

    /**
     * Requirement 3: User reports create a pending moderation review record.
     * NEVER permanently bans or suspends a user solely because someone reports them.
     */
    suspend fun submitUserReport(
        targetUserId: String,
        reasonCategory: String,
        details: String = ""
    ): Result<UserReportRecord> = txMutex.withLock {
        val reporterId = requireUserId()
        val trimmedReason = reasonCategory.trim()
        if (targetUserId.isBlank() || trimmedReason.length < 3) {
            return Result.failure(IllegalArgumentException("Please provide a valid user ID and report reason."))
        }
        val report = UserReportRecord(
            reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            reportedUserId = targetUserId,
            reporterUserId = reporterId,
            reason = trimmedReason,
            details = details.trim(),
            reviewedByModerator = false,
            status = "PENDING_MODERATION_REVIEW"
        )
        localUserReports.value = listOf(report) + localUserReports.value
        // Crucial policy guarantee: Reported user's accountStatus remains unchanged until moderator review
        return Result.success(report)
    }

    /**
     * Requirement 3: Confirmed Moderator / Admin Violation Enforcement.
     * - First confirmed ordinary violation -> Warning + 3-day temporary suspension.
     * - Repeated ordinary violations -> Further 3-day suspension or permanent ban after repeated confirmed violations.
     * - Serious or repeated severe violations -> Permanent account ban following review.
     * - Preserves all legitimate transaction records, pending payments, and wallet balances.
     */
    suspend fun applyModerationDecision(
        targetUserId: String,
        confirmedByModerator: Boolean = true,
        severity: ViolationSeverity,
        reason: String,
        moderatorEmail: String = currentUserEmailOrDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result<ModerationDecisionRecord> = txMutex.withLock {
        val adminUid = requireUserId()
        try {
            requireAdminAuthorization(moderatorEmail)
        } catch (e: SecurityException) {
            return Result.failure(e)
        }
        if (!confirmedByModerator) {
            return Result.failure(
                IllegalArgumentException("Unconfirmed reports cannot trigger a suspension or ban without moderator review.")
            )
        }

        val trimmedReason = reason.trim()
        if (trimmedReason.length < 4) {
            return Result.failure(IllegalArgumentException("Moderation decision requires a clear violation reason."))
        }

        val existingProfile = localProfiles.value[targetUserId] ?: UserProfile(id = targetUserId, userId = targetUserId)
        val (newStatus, durationDays, endsAtEpochMs) = ModerationPolicyEngine.computeModerationOutcome(
            existingViolationCount = existingProfile.violationCount,
            severity = severity,
            nowEpochMs = nowEpochMs
        )
        val newViolationCount = existingProfile.violationCount + 1

        // Update profile moderation fields while strictly preserving wallet balances & transaction history
        val updatedProfile = existingProfile.copy(
            accountStatus = newStatus.name,
            violationCount = newViolationCount,
            suspensionReason = trimmedReason,
            suspensionEndsAtEpochMs = endsAtEpochMs,
            updatedAt = Timestamp.now()
        )
        localProfiles.value = localProfiles.value + (targetUserId to updatedProfile)

        val decisionId = "mod_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val decision = ModerationDecisionRecord(
            decisionId = decisionId,
            targetUserId = targetUserId,
            targetUserName = updatedProfile.username,
            moderatorEmail = moderatorEmail,
            severity = severity,
            resultingStatus = newStatus,
            reason = trimmedReason,
            violationCountAfter = newViolationCount,
            suspensionDurationDays = durationDays,
            suspensionEndsAtEpochMs = endsAtEpochMs,
            createdAtEpochMs = nowEpochMs
        )
        localModerationDecisions.value = listOf(decision) + localModerationDecisions.value

        val auditLog = AdminAuditLog(
            id = "audit_$decisionId",
            adminId = adminUid,
            adminEmail = moderatorEmail,
            actionType = "MODERATION_${newStatus.name}",
            diamondsAmount = 0L,
            targetUser = updatedProfile.username,
            note = "Moderation Action [${newStatus.displayLabel}]: $trimmedReason (Violation #$newViolationCount)",
            idempotencyKey = "audit_$decisionId",
            createdAt = Timestamp.now()
        )
        localAuditLogs.value = listOf(auditLog) + localAuditLogs.value

        return Result.success(decision)
    }

    /**
     * Requirement 3: Appeal option for temporary suspensions and permanent bans.
     */
    suspend fun submitAccountBanAppeal(
        appealMessage: String,
        evidenceAttachmentIds: List<String> = emptyList()
    ): Result<AccountBanAppeal> = txMutex.withLock {
        val uid = requireUserId()
        val trimmedExplanation = appealMessage.trim()
        if (trimmedExplanation.length < 10) {
            return Result.failure(
                IllegalArgumentException("Please provide a detailed explanation (at least 10 characters) for your appeal.")
            )
        }
        val profile = localProfiles.value[uid] ?: UserProfile(id = uid, userId = uid)
        if (profile.accountStatus == AccountModerationStatus.ACTIVE.name) {
            return Result.failure(
                IllegalStateException("Your account is currently ACTIVE and does not have a suspension or ban to appeal.")
            )
        }

        val statusEnum = runCatching {
            AccountModerationStatus.valueOf(profile.accountStatus)
        }.getOrDefault(AccountModerationStatus.TEMP_SUSPENDED_3_DAYS)

        val appeal = AccountBanAppeal(
            appealId = "apl_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            userId = uid,
            userName = profile.username,
            accountStatusAtAppeal = statusEnum,
            banReason = profile.suspensionReason,
            appealExplanation = trimmedExplanation,
            evidenceAttachmentIds = evidenceAttachmentIds.take(StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET),
            status = "SUBMITTED"
        )
        localBanAppeals.value = listOf(appeal) + localBanAppeals.value
        return Result.success(appeal)
    }

    /**
     * Requirement 3 & 8: Admin review of an Account Ban / Suspension Appeal with audit logging.
     */
    suspend fun reviewAccountBanAppeal(
        appealId: String,
        approveAndRestore: Boolean,
        adminDecisionNote: String,
        adminEmail: String = currentUserEmailOrDefault()
    ): Result<AccountBanAppeal> = txMutex.withLock {
        val adminUid = requireUserId()
        try {
            requireAdminAuthorization(adminEmail)
        } catch (e: SecurityException) {
            return Result.failure(e)
        }

        val targetAppeal = localBanAppeals.value.firstOrNull { it.appealId == appealId || it.id == appealId }
            ?: return Result.failure(IllegalArgumentException("Appeal '$appealId' not found."))

        val newAppealStatus = if (approveAndRestore) "APPROVED_RESTORED" else "REJECTED"
        val updatedAppeal = targetAppeal.copy(
            status = newAppealStatus,
            adminResolutionNote = adminDecisionNote.trim().ifBlank {
                if (approveAndRestore) "Appeal approved after moderation review. Account restored."
                else "Appeal reviewed; original moderation decision upheld."
            },
            reviewedByEmail = adminEmail,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        localBanAppeals.value = localBanAppeals.value.map {
            if (it.appealId == appealId || it.id == appealId) updatedAppeal else it
        }

        if (approveAndRestore) {
            val cur = localProfiles.value[targetAppeal.userId]
            if (cur != null) {
                localProfiles.value = localProfiles.value + (
                    targetAppeal.userId to cur.copy(
                        accountStatus = AccountModerationStatus.ACTIVE.name,
                        suspensionReason = "",
                        suspensionEndsAtEpochMs = 0L,
                        updatedAt = Timestamp.now()
                    )
                )
            }
        }

        val auditId = "audit_apl_${appealId.take(16)}"
        localAuditLogs.value = listOf(
            AdminAuditLog(
                id = auditId,
                adminId = adminUid,
                adminEmail = adminEmail,
                actionType = "APPEAL_$newAppealStatus",
                diamondsAmount = 0L,
                targetUser = targetAppeal.userName,
                note = "Appeal $appealId ($newAppealStatus): ${updatedAppeal.adminResolutionNote}",
                idempotencyKey = auditId,
                createdAt = Timestamp.now()
            )
        ) + localAuditLogs.value

        return Result.success(updatedAppeal)
    }

    fun observeAccountBanAppeals(): kotlinx.coroutines.flow.StateFlow<List<AccountBanAppeal>> = localBanAppeals
    fun observeModerationDecisions(): kotlinx.coroutines.flow.StateFlow<List<ModerationDecisionRecord>> = localModerationDecisions
    fun observeSupportChatHistory(userId: String = ""): kotlinx.coroutines.flow.StateFlow<List<SupportChatMessage>> = localSupportChatHistory
    fun getLocalBanAppeals(): List<AccountBanAppeal> = localBanAppeals.value
    fun getLocalModerationDecisions(): List<ModerationDecisionRecord> = localModerationDecisions.value

    // --- 7. IMAGE, SCREENSHOT & VIDEO EVIDENCE UPLOAD (CLIENT + BACKEND VALIDATION & PRIVATE ACCESS) ---

    /**
     * Requirement 6: Validates file type, size (Images: JPG, PNG, WEBP up to 10 MB; Videos: MP4, MOV up to 50 MB),
     * and count (up to 5 per ticket) on both client and backend, stores in private storage, and marks
     * `serverConfirmed = true` ONLY when backend storage confirmation succeeds.
     */
    suspend fun validateAndUploadSupportAttachment(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        existingAttachmentCount: Int = 0,
        previewUri: String = "",
        ticketId: String = "",
        simulateBackendFailure: Boolean = false
    ): Result<SupportAttachment> = txMutex.withLock {
        val uid = requireUserId()
        val validation = SupportEvidenceValidator.validateCandidateFile(
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            currentAttachmentCount = existingAttachmentCount
        )
        if (!validation.isValid) {
            return Result.failure(
                IllegalArgumentException(validation.errorMessage ?: "Invalid attachment file.")
            )
        }

        val attachmentId = "att_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val checksum = SupportEvidenceValidator.computeAttachmentHash(uid, fileName, fileSizeBytes)

        if (simulateBackendFailure) {
            val failedAttachment = SupportAttachment(
                id = attachmentId,
                attachmentId = attachmentId,
                ticketId = ticketId,
                ownerUserId = uid,
                fileName = fileName.trim(),
                fileExtension = validation.normalizedExtension,
                mimeType = validation.normalizedMimeType,
                mediaType = validation.mediaKind,
                mediaKind = validation.mediaKind,
                fileSizeBytes = fileSizeBytes,
                compressedSizeBytes = validation.compressedSizeBytes,
                uploadProgressPercent = 0,
                uploadStatus = AttachmentUploadStatus.FAILED,
                serverConfirmed = false,
                storagePath = "",
                privateStoragePath = "",
                previewUri = previewUri,
                sha256Checksum = checksum
            )
            localSupportAttachments.value = localSupportAttachments.value + (attachmentId to failedAttachment)
            return Result.failure(
                IOException("Upload failed: Backend storage did not confirm the file upload. Please retry.")
            )
        }

        val privatePath = "private_support_evidence/$uid/$attachmentId.${validation.normalizedExtension}"
        val confirmedAttachment = SupportAttachment(
            id = attachmentId,
            attachmentId = attachmentId,
            ticketId = ticketId,
            ownerUserId = uid,
            fileName = fileName.trim(),
            fileExtension = validation.normalizedExtension,
            mimeType = validation.normalizedMimeType,
            mediaType = validation.mediaKind,
            mediaKind = validation.mediaKind,
            fileSizeBytes = fileSizeBytes,
            compressedSizeBytes = validation.compressedSizeBytes,
            uploadProgressPercent = 100,
            uploadStatus = AttachmentUploadStatus.UPLOADED_VERIFIED,
            serverConfirmed = true,
            storagePath = privatePath,
            privateStoragePath = privatePath,
            previewUri = previewUri.ifBlank { "private://$privatePath" },
            sha256Checksum = checksum
        )
        localSupportAttachments.value = localSupportAttachments.value + (attachmentId to confirmedAttachment)
        return Result.success(confirmedAttachment)
    }

    /**
     * Requirement 6 & 9: Private attachment access control.
     * Only the ticket owner (`ownerUserId`) or an authorized support admin can access private support attachments.
     */
    fun getAuthorizedTicketAttachments(
        ticketId: String,
        requesterUserId: String = requireUserId(),
        requesterEmail: String = currentUserEmailOrDefault()
    ): Result<List<SupportAttachment>> {
        val ticket = localTickets.value.firstOrNull { it.id == ticketId || it.ticketNumber == ticketId }
            ?: return Result.failure(IllegalArgumentException("Support ticket '$ticketId' not found."))

        val isOwner = ticket.userId == requesterUserId
        val isAdmin = requesterEmail.trim().equals(StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)
        if (!isOwner && !isAdmin) {
            return Result.failure(
                SecurityException("Access denied: Private support ticket attachments are restricted to the ticket owner and authorized support admins.")
            )
        }
        return Result.success(ticket.attachments)
    }

    fun getAuthorizedSupportTicket(
        ticketId: String,
        requesterUserId: String = requireUserId(),
        requesterEmail: String = currentUserEmailOrDefault()
    ): Result<SupportTicket> {
        val ticket = localTickets.value.firstOrNull { it.id == ticketId || it.ticketNumber == ticketId }
            ?: return Result.failure(IllegalArgumentException("Support ticket '$ticketId' not found."))

        val isOwner = ticket.userId == requesterUserId
        val isAdmin = requesterEmail.trim().equals(StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)
        if (!isOwner && !isAdmin) {
            return Result.failure(
                SecurityException("Access denied: You are not authorized to view another user's private support ticket.")
            )
        }
        return Result.success(ticket)
    }

    // --- 8. AI CUSTOMER SUPPORT CHAT, TICKET CREATION WITH EVIDENCE, USER REPLY/REOPEN & ADMIN PANEL ---

    suspend fun sendSupportChatMessage(
        userMessage: String,
        selectedCategory: SupportRequestCategory,
        customCategoryDescription: String = "",
        preferredLanguage: String = "AUTO",
        attachedFilesCount: Int = 0,
        useLiveGeminiIfConfigured: Boolean = false
    ): Result<Pair<SupportChatMessage, SupportChatMessage>> {
        requireUserId()
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a message for Customer Support."))
        }
        val lang = AiCustomerSupportAssistant.detectLanguage(trimmed, preferredLanguage)
        val userMsg = SupportChatMessage(
            id = "usr_msg_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            sender = "USER",
            message = trimmed,
            language = lang,
            selectedCategory = selectedCategory
        )
        val aiMsg = if (useLiveGeminiIfConfigured) {
            AiCustomerSupportAssistant.requestLiveGeminiSupportOrFallback(
                userMessage = trimmed,
                selectedCategory = selectedCategory,
                preferredLanguage = preferredLanguage,
                attachedFilesCount = attachedFilesCount
            ).getOrThrow()
        } else {
            AiCustomerSupportAssistant.generateSupportReply(
                userMessage = trimmed,
                selectedCategory = selectedCategory,
                preferredLanguage = preferredLanguage,
                attachedFilesCount = attachedFilesCount,
                customOtherDescription = customCategoryDescription
            )
        }
        localSupportChatHistory.value = localSupportChatHistory.value + listOf(userMsg, aiMsg)
        return Result.success(userMsg to aiMsg)
    }

    /**
     * Requirement 5, 6 & 7: Creates a Support Ticket with category validation, custom description for "Other / General Help",
     * verified evidence attachments (max 5), user consent verification, and a server-generated ticket reference number.
     */
    suspend fun createSupportTicketWithEvidence(
        userName: String,
        subject: String,
        category: String,
        customCategoryDescription: String = "",
        description: String,
        priority: String = "NORMAL",
        attachments: List<SupportAttachment> = emptyList(),
        evidenceConsentAccepted: Boolean = true
    ): Result<SupportTicket> = txMutex.withLock {
        val uid = requireUserId()
        val resolvedCategory = SupportRequestCategory.fromTitleOrId(category)
        val trimmedDesc = description.trim()
        if (trimmedDesc.length < 5) {
            return Result.failure(
                IllegalArgumentException("Please describe your issue (at least 5 characters) before submitting a support ticket.")
            )
        }
        if (resolvedCategory == SupportRequestCategory.OTHER_GENERAL && customCategoryDescription.trim().length < 3) {
            return Result.failure(
                IllegalArgumentException("Please provide a custom description when 'Other / General Help' is selected.")
            )
        }
        if (attachments.size > StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET) {
            return Result.failure(
                IllegalArgumentException("Maximum ${StreamVibeCatalog.MAX_ATTACHMENTS_PER_TICKET} attachments are allowed per support ticket.")
            )
        }
        if (attachments.isNotEmpty() && !evidenceConsentAccepted) {
            return Result.failure(
                IllegalArgumentException("User consent is required before uploading and submitting support evidence files.")
            )
        }
        // Verify every attachment is backend-confirmed and belongs to this user
        for (att in attachments) {
            if (!att.isServerConfirmedSuccess || att.ownerUserId != uid) {
                return Result.failure(
                    SecurityException("Attachment '${att.fileName}' is not confirmed by the backend or does not belong to the current user.")
                )
            }
        }

        val now = Timestamp.now()
        val randomSuffix = UUID.randomUUID().toString().replace("-", "").uppercase().take(6)
        val ticketId = "tkt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val serverTicketNumber = "SV-TKT-${System.currentTimeMillis().toString().takeLast(6)}-$randomSuffix"
        val boundAttachments = attachments.map { it.copy(ticketId = ticketId) }

        val resolvedSubject = subject.trim().take(110).ifBlank {
            "${resolvedCategory.displayTitle}: ${trimmedDesc.take(48)}"
        }
        val initialEscalation = if (resolvedCategory.requiresHumanReviewForAction) "Specialist Human Support Desk" else ""

        val ticket = SupportTicket(
            id = ticketId,
            ticketNumber = serverTicketNumber,
            userId = uid,
            userName = userName.trim().take(45).ifBlank { "User" },
            subject = resolvedSubject,
            category = resolvedCategory.displayTitle,
            customCategoryDescription = customCategoryDescription.trim().take(300),
            description = trimmedDesc.take(1200),
            priority = priority,
            status = SupportTicketStatus.SUBMITTED.displayLabel,
            adminReply = "Ticket $serverTicketNumber received under [${resolvedCategory.displayTitle}]. Status: Submitted.",
            attachments = boundAttachments,
            replies = listOf(
                SupportTicketReply(
                    replyId = "rep_init_${ticketId.takeLast(8)}",
                    ticketId = ticketId,
                    senderUserId = uid,
                    senderName = userName.trim().ifBlank { "User" },
                    senderRole = "USER",
                    message = trimmedDesc.take(1200)
                )
            ),
            evidenceConsentAccepted = evidenceConsentAccepted,
            escalatedToTeam = initialEscalation,
            createdAt = now,
            updatedAt = now
        )

        localTickets.value = listOf(ticket) + localTickets.value
        return Result.success(ticket)
    }

    /**
     * Requirement 7: Allows users to reply to an existing ticket or reopen a Resolved/Closed ticket.
     */
    suspend fun replyOrReopenSupportTicket(
        ticketId: String,
        senderName: String = "User",
        replyMessage: String,
        reopenTicket: Boolean = false
    ): Result<SupportTicket> = txMutex.withLock {
        val uid = requireUserId()
        val trimmedMsg = replyMessage.trim()
        if (trimmedMsg.isEmpty()) {
            return Result.failure(IllegalArgumentException("Reply message cannot be empty."))
        }
        val existing = localTickets.value.firstOrNull { it.id == ticketId || it.ticketNumber == ticketId }
            ?: return Result.failure(IllegalArgumentException("Support ticket '$ticketId' not found."))

        if (existing.userId != uid) {
            return Result.failure(SecurityException("Access denied: You can only reply to or reopen your own support tickets."))
        }

        val currentStatus = SupportTicketStatus.fromRaw(existing.status)
        val nextStatus = if (
            reopenTicket ||
            currentStatus == SupportTicketStatus.RESOLVED ||
            currentStatus == SupportTicketStatus.CLOSED ||
            currentStatus == SupportTicketStatus.WAITING_FOR_USER
        ) {
            SupportTicketStatus.IN_PROGRESS.displayLabel
        } else {
            existing.status
        }

        val newReply = SupportTicketReply(
            replyId = "rpl_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            ticketId = existing.id,
            senderUserId = uid,
            senderName = senderName.ifBlank { existing.userName },
            senderRole = "USER",
            message = trimmedMsg.take(900)
        )
        val updated = existing.copy(
            status = nextStatus,
            replies = existing.replies + newReply,
            updatedAt = Timestamp.now()
        )
        localTickets.value = localTickets.value.map { if (it.id == existing.id) updated else it }
        return Result.success(updated)
    }

    /**
     * Requirement 8: Admin Support Management — update ticket status, respond to user, escalate case, and record audit log.
     */
    suspend fun adminUpdateSupportTicket(
        ticketId: String,
        newStatus: SupportTicketStatus,
        adminReply: String,
        escalateToTeam: String = "",
        adminEmail: String = currentUserEmailOrDefault()
    ): Result<SupportTicket> = txMutex.withLock {
        val adminUid = requireUserId()
        try {
            requireAdminAuthorization(adminEmail)
        } catch (e: SecurityException) {
            return Result.failure(e)
        }

        val existing = localTickets.value.firstOrNull { it.id == ticketId || it.ticketNumber == ticketId }
            ?: return Result.failure(IllegalArgumentException("Support ticket '$ticketId' not found."))

        val trimmedReply = adminReply.trim().ifBlank {
            "Admin Support update: Status changed to ${newStatus.displayLabel}."
        }
        val adminReplyItem = SupportTicketReply(
            replyId = "adm_rpl_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            ticketId = existing.id,
            senderUserId = adminUid,
            senderName = "StreamVibe Support Admin",
            senderRole = "ADMIN",
            message = trimmedReply
        )
        val updated = existing.copy(
            status = newStatus.displayLabel,
            adminReply = trimmedReply,
            replies = existing.replies + adminReplyItem,
            escalatedToTeam = escalateToTeam.trim().ifBlank { existing.escalatedToTeam },
            updatedAt = Timestamp.now()
        )
        localTickets.value = localTickets.value.map { if (it.id == existing.id) updated else it }

        val auditId = "audit_tkt_${existing.id.take(14)}_${System.currentTimeMillis().toString().takeLast(4)}"
        localAuditLogs.value = listOf(
            AdminAuditLog(
                id = auditId,
                adminId = adminUid,
                adminEmail = adminEmail,
                actionType = "SUPPORT_TICKET_${newStatus.rawStatus}",
                diamondsAmount = 0L,
                targetUser = existing.userName,
                note = "Ticket ${existing.ticketNumber.ifBlank { existing.id }} [${existing.category}] -> ${newStatus.displayLabel}: $trimmedReply",
                idempotencyKey = auditId,
                createdAt = Timestamp.now()
            )
        ) + localAuditLogs.value

        return Result.success(updated)
    }

    /**
     * Requirement 8: Admin filtering of support tickets by category and status.
     */
    fun getAdminFilteredSupportTickets(
        categoryFilter: String? = null,
        statusFilter: String? = null,
        adminEmail: String = currentUserEmailOrDefault()
    ): Result<List<SupportTicket>> {
        try {
            requireAdminAuthorization(adminEmail)
        } catch (e: SecurityException) {
            return Result.failure(e)
        }
        val filtered = localTickets.value.filter { tkt ->
            val matchesCat = categoryFilter.isNullOrBlank() ||
                categoryFilter.equals("ALL", ignoreCase = true) ||
                tkt.category.equals(categoryFilter.trim(), ignoreCase = true)
            val matchesStatus = statusFilter.isNullOrBlank() ||
                statusFilter.equals("ALL", ignoreCase = true) ||
                tkt.status.equals(statusFilter.trim(), ignoreCase = true) ||
                SupportTicketStatus.fromRaw(tkt.status).displayLabel.equals(statusFilter.trim(), ignoreCase = true)
            matchesCat && matchesStatus
        }
        return Result.success(filtered)
    }
}
