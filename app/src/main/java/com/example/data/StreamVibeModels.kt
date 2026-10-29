package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class DiamondWalletType(val rawValue: String, val displayName: String) {
    USER_WALLET("USER_WALLET", "User Diamond Wallet"),
    ADMIN_WALLET("ADMIN_WALLET", "Admin Promotional Wallet"),
    CREATOR_WALLET("CREATOR_WALLET", "Creator Earnings Wallet")
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "VibeCreator",
    val bio: String = "Creating Reels, Short Videos & Live Streams on StreamVibe",
    val avatarColor: String = "#FF2E93",
    // Wallet A: Regular User Diamond Wallet (for purchased Diamonds & gifting)
    val coinsBalance: Long = 250L,
    // Wallet B: Admin Promotional Diamond Wallet (separate promotional pool with audit logs)
    val adminWalletDiamonds: Long = 50000L,
    // Wallet C: Creator Earnings Wallet (earned from gifts; 10,000 Diamonds = ₹4,000)
    val creatorEarningsDiamonds: Long = 0L,
    val withdrawableDiamonds: Long = 0L,
    // VIP Levels (1 - 1000)
    val vipLevel: Long = 1L,
    val vipXp: Long = 250L,
    // Configurable Admin Limits
    val adminMaxAllocationLimit: Long = 25000L,
    val adminDailyGiftLimit: Long = 100000L,
    val giftsReceivedCount: Long = 0L,
    val followersCount: Long = 12L,
    val isVerifiedOtp: Boolean = false,
    val acceptedTerms: Boolean = false,
    val acceptedPolicyVersion: String = "",
    val acceptedTermsEpochMs: Long = 0L,
    val contentCategories: List<String> = emptyList(),
    val accountStatus: String = "ACTIVE", // ACTIVE, WARNED, TEMP_SUSPENDED_3_DAYS, PERMANENTLY_BANNED
    val violationCount: Int = 0,
    val suspensionReason: String = "",
    val suspensionEndsAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val userWalletDiamonds: Long
        get() = coinsBalance

    val withdrawableInr: Long
        get() = StreamVibeCatalog.diamondsToInr(withdrawableDiamonds)

    val totalCreatorEarningsInr: Long
        get() = StreamVibeCatalog.diamondsToInr(creatorEarningsDiamonds)

    val isTemporarilySuspended: Boolean
        get() = accountStatus == AccountModerationStatus.TEMP_SUSPENDED_3_DAYS.name &&
            (suspensionEndsAtEpochMs == 0L || System.currentTimeMillis() < suspensionEndsAtEpochMs)

    val isPermanentlyBanned: Boolean
        get() = accountStatus == AccountModerationStatus.PERMANENTLY_BANNED.name
}

@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phone: String = "",
    val kycStatus: String = "pending",
    val bankHolderName: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val bankName: String = "",
    val bankVerified: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReelPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    val mediaType: String = "REEL", // REEL, SHORT_VIDEO, PHOTO
    val category: String = "Trending",
    val musicTrack: String = "StreamVibe Original Audio",
    val themeColorHex: String = "#FF2E93",
    val mediaUri: String = "preset://neon_pulse",
    val likesCount: Long = 0L,
    val giftsCoinsTotal: Long = 0L,
    val visibility: String = "PUBLIC", // PUBLIC, MODERATION
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveRoom(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val topic: String = "Live Jam",
    val status: String = "LIVE", // LIVE, ENDED
    val viewerCount: Long = 1L,
    val totalGiftsCoins: Long = 0L,
    val isPkBattle: Boolean = false,
    val opponentName: String = "",
    val hostPkScore: Long = 0L,
    val opponentPkScore: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val messageType: String = "CHAT", // CHAT, GIFT, PK_GIFT
    val giftName: String = "",
    val giftCoins: Long = 0L,
    val walletSource: String = "USER_WALLET", // USER_WALLET, ADMIN_PROMO_WALLET
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CoinTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = "RAZORPAY_TOPUP", // RAZORPAY_TOPUP, GIFT_SENT, GIFT_RECEIVED, ADMIN_PROMO_ALLOCATION, ADMIN_PROMO_GIFT, CREATOR_WITHDRAWAL, VIP_UPGRADE, WELCOME_BONUS
    val coinsDelta: Long = 0L,
    val amountInrPaise: Long = 0L,
    val currency: String = "INR",
    val razorpayOrderId: String = "",
    val razorpayPaymentId: String = "",
    val paymentMode: String = "TEST", // TEST, LIVE
    val paymentStatus: String = "VERIFIED", // CREATED, VERIFIED, FAILED, CANCELLED, INVALID_SIGNATURE, NETWORK_ERROR
    val razorpaySignatureVerified: Boolean = true,
    val isTestTransaction: Boolean = true,
    val countsAsRealRevenue: Boolean = false,
    val withdrawableAsRealMoney: Boolean = false,
    val failureReason: String = "",
    val recipientName: String = "Self",
    val giftLabel: String = "",
    val walletType: String = "USER_WALLET", // USER_WALLET, ADMIN_WALLET, CREATOR_WALLET
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class RazorpayPaymentStatus {
    CREATED,
    VERIFIED,
    FAILED,
    CANCELLED,
    INVALID_SIGNATURE,
    NETWORK_ERROR
}

data class RazorpayTestOrder(
    val orderId: String,
    val userId: String,
    val packId: String,
    val packTitle: String,
    val diamondsAmount: Long,
    val bonusDiamonds: Long,
    val amountInr: Int,
    val amountInrPaise: Long,
    val currency: String = "INR",
    val paymentMode: String = "TEST",
    val checkoutKeyId: String,
    val status: RazorpayPaymentStatus = RazorpayPaymentStatus.CREATED,
    val idempotencyKey: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val totalDiamonds: Long
        get() = diamondsAmount + bonusDiamonds
}

data class RazorpayVerificationResult(
    val orderId: String,
    val paymentId: String,
    val status: RazorpayPaymentStatus,
    val diamondsCredited: Long,
    val newWalletBalance: Long,
    val currency: String = "INR",
    val amountInrPaise: Long,
    val isTestTransaction: Boolean = true,
    val countsAsRealRevenue: Boolean = false,
    val withdrawableAsRealMoney: Boolean = false,
    val message: String
)


@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val diamondsAmount: Long = 10000L,
    val payoutInrPaise: Long = 400000L,
    val bankName: String = "",
    val bankAccountMasked: String = "",
    val bankIfsc: String = "",
    val status: String = "PENDING_VERIFICATION", // PENDING_VERIFICATION, VERIFIED_PAID, REJECTED
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val payoutInr: Long
        get() = payoutInrPaise / 100L
}

@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val actionType: String = "PROMO_ALLOCATION", // PROMO_ALLOCATION, PROMO_GIFT_SENT, LIMIT_CONFIG_UPDATED, WITHDRAWAL_VERIFIED_PAID
    val diamondsAmount: Long = 0L,
    val targetUser: String = "",
    val note: String = "",
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val ticketNumber: String = "",
    val userId: String = "",
    val userName: String = "",
    val subject: String = "",
    val category: String = "GENERAL", // Can be legacy or SupportRequestCategory.displayTitle
    val customCategoryDescription: String = "",
    val description: String = "",
    val priority: String = "NORMAL", // NORMAL, HIGH, URGENT
    val status: String = "OPEN", // SUBMITTED, OPEN, IN_PROGRESS, WAITING_FOR_USER, ESCALATED, RESOLVED, CLOSED
    val adminReply: String = "",
    val attachments: List<SupportAttachment> = emptyList(),
    val replies: List<SupportTicketReply> = emptyList(),
    val evidenceConsentAccepted: Boolean = false,
    val escalatedToTeam: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class AccountModerationStatus(val displayLabel: String) {
    ACTIVE("Active"),
    WARNED("Warning Issued"),
    TEMP_SUSPENDED_3_DAYS("Temporary Suspension (3 Days)"),
    PERMANENTLY_BANNED("Permanent Account Ban")
}

enum class ViolationSeverity {
    ORDINARY,
    REPEATED,
    REPEATED_ORDINARY,
    SEVERE
}

data class PolicyAcceptanceRecord(
    val userId: String,
    val policyVersion: String,
    val acceptedAtEpochMs: Long,
    val checkboxLabel: String = StreamVibeCatalog.MANDATORY_TERMS_CHECKBOX_LABEL
)

data class UserReportRecord(
    val reportId: String,
    val reportedUserId: String,
    val reporterUserId: String,
    val reason: String,
    val details: String = "",
    val reviewedByModerator: Boolean = false,
    val status: String = "PENDING_MODERATION_REVIEW", // PENDING_MODERATION_REVIEW, CONFIRMED_VIOLATION, DISMISSED
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class ModerationDecisionRecord(
    val decisionId: String,
    val targetUserId: String,
    val targetUserName: String = "",
    val moderatorEmail: String,
    val severity: ViolationSeverity,
    val resultingStatus: AccountModerationStatus,
    val reason: String,
    val violationCountAfter: Int = 1,
    val suspensionDurationDays: Int = 0,
    val suspensionEndsAtEpochMs: Long = 0L,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val newStatus: AccountModerationStatus
        get() = resultingStatus
}

data class AccountBanAppeal(
    val appealId: String,
    val userId: String,
    val userName: String,
    val accountStatusAtAppeal: AccountModerationStatus = AccountModerationStatus.TEMP_SUSPENDED_3_DAYS,
    val banReason: String,
    val appealExplanation: String,
    val evidenceAttachmentIds: List<String> = emptyList(),
    val status: String = "SUBMITTED", // SUBMITTED, UNDER_REVIEW, APPROVED_RESTORED, REJECTED
    val adminResolutionNote: String = "",
    val reviewedByEmail: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val id: String
        get() = appealId

    val appealMessage: String
        get() = appealExplanation
}

enum class SupportTicketStatus(val rawStatus: String, val displayLabel: String) {
    SUBMITTED("SUBMITTED", "Submitted"),
    IN_PROGRESS("IN_PROGRESS", "In Progress"),
    WAITING_FOR_USER("WAITING_FOR_USER", "Waiting for User"),
    ESCALATED("ESCALATED", "Escalated"),
    RESOLVED("RESOLVED", "Resolved"),
    CLOSED("CLOSED", "Closed");

    val displayName: String
        get() = displayLabel

    companion object {
        fun fromRaw(raw: String): SupportTicketStatus {
            return entries.firstOrNull {
                it.rawStatus.equals(raw.trim(), ignoreCase = true) ||
                    it.displayLabel.equals(raw.trim(), ignoreCase = true)
            } ?: if (raw.equals("OPEN", ignoreCase = true)) SUBMITTED else SUBMITTED
        }
    }
}

enum class SupportRequestCategory(
    val id: String,
    val displayTitle: String,
    val hindiTitle: String,
    val followUpEnglish: List<String>,
    val followUpHindi: List<String>,
    val evidenceHintEnglish: String,
    val evidenceHintHindi: String,
    val requiresHumanReviewForAction: Boolean = false
) {
    VIDEO_REELS(
        id = "CAT_VIDEO_REELS",
        displayTitle = "Video / Reels Problem",
        hindiTitle = "वीडियो / रील्स समस्या",
        followUpEnglish = listOf(
            "Which Reel or video ID is affected?",
            "Are you facing an upload error, audio sync issue, or playback buffering?"
        ),
        followUpHindi = listOf(
            "कौन सा रील या वीडियो प्रभावित है?",
            "क्या आपको अपलोड एरर, ऑडियो सिंक, या प्लेबैक बफरिंग की समस्या आ रही है?"
        ),
        evidenceHintEnglish = "Attach a screenshot or short screen recording (MP4/MOV up to 50 MB) showing the playback or upload error.",
        evidenceHintHindi = "प्लेबैक या अपलोड एरर दिखाने वाला स्क्रीनशॉट या स्क्रीन रिकॉर्डिंग (MP4/MOV, 50 MB तक) अपलोड करें।"
    ),
    LIVE_STREAMING(
        id = "CAT_LIVE_STREAMING",
        displayTitle = "Live Streaming Problem",
        hindiTitle = "लाइव स्ट्रीमिंग समस्या",
        followUpEnglish = listOf(
            "Did the issue happen when starting a Live room or during an active broadcast?",
            "Please share the approximate time and any error banner shown on screen."
        ),
        followUpHindi = listOf(
            "क्या यह समस्या लाइव रूम शुरू करते समय हुई या ब्रॉडकास्ट के दौरान?",
            "कृपया समय और स्क्रीन पर दिखा एरर मैसेज साझा करें।"
        ),
        evidenceHintEnglish = "Attach a screenshot or short video clip of the Live Studio screen.",
        evidenceHintHindi = "लाइव स्टूडियो स्क्रीन का स्क्रीनशॉट या छोटा वीडियो क्लिप जोड़ें।"
    ),
    DEPOSIT_DIAMOND(
        id = "CAT_DEPOSIT_DIAMOND",
        displayTitle = "Deposit / Diamond Purchase",
        hindiTitle = "डिपॉज़िट / डायमंड खरीद",
        followUpEnglish = listOf(
            "Which Diamond package (₹99 to ₹9,999) did you select?",
            "Please share your Razorpay Order ID / Payment ID or UTR reference."
        ),
        followUpHindi = listOf(
            "आपने कौन सा डायमंड पैकेज (₹99 से ₹9,999) चुना था?",
            "कृपया अपना Razorpay Order ID / Payment ID या UTR नंबर बताएं।"
        ),
        evidenceHintEnglish = "Upload your payment receipt or UPI/bank debit screenshot (JPG/PNG/WEBP up to 10 MB).",
        evidenceHintHindi = "अपनी पेमेंट रसीद या UPI/बैंक डेबिट का स्क्रीनशॉट (JPG/PNG/WEBP, 10 MB तक) अपलोड करें।",
        requiresHumanReviewForAction = true
    ),
    WALLET_BALANCE(
        id = "CAT_WALLET_BALANCE",
        displayTitle = "Wallet Balance / Transaction",
        hindiTitle = "वॉलेट बैलेंस / लेन-देन",
        followUpEnglish = listOf(
            "Which wallet is affected (User Wallet, Admin Promotional Wallet, or Creator Earnings Wallet)?",
            "What transaction ID or date appears in your ledger?"
        ),
        followUpHindi = listOf(
            "कौन सा वॉलेट प्रभावित है (User Wallet, Admin Promotional Wallet, या Creator Earnings Wallet)?",
            "लेजर में कौन सी ट्रांज़ैक्शन आईडी या तारीख दिख रही है?"
        ),
        evidenceHintEnglish = "Attach a screenshot of your Wallet balance and Transaction History.",
        evidenceHintHindi = "अपने वॉलेट बैलेंस और ट्रांज़ैक्शन हिस्ट्री का स्क्रीनशॉट अपलोड करें।",
        requiresHumanReviewForAction = true
    ),
    WITHDRAWAL_BANK(
        id = "CAT_WITHDRAWAL_BANK",
        displayTitle = "Withdrawal / Bank Account",
        hindiTitle = "विड्रॉल / बैंक खाता",
        followUpEnglish = listOf(
            "Is this about Bank Account / IFSC verification, the 10,000 Diamonds (₹4,000) withdrawal, or the 18th IST Monthly Payout (Level 30+ & ₹10,000+)?",
            "Please share the Withdrawal Request ID if already submitted."
        ),
        followUpHindi = listOf(
            "क्या यह बैंक खाता / IFSC वेरिफिकेशन, 10,000 Diamonds (₹4,000) विड्रॉल, या 18 तारीख के मासिक पेआउट (Level 30+ और ₹10,000+) से जुड़ा है?",
            "यदि विड्रॉल रिक्वेस्ट सबमिट की है तो उसकी ID साझा करें।"
        ),
        evidenceHintEnglish = "Attach a screenshot of your Creator Earnings withdrawal status or bank verification message.",
        evidenceHintHindi = "क्रिएटर अर्निंग्स विड्रॉल स्टेटस या बैंक वेरिफिकेशन मैसेज का स्क्रीनशॉट जोड़ें।",
        requiresHumanReviewForAction = true
    ),
    LOGIN_SIGNUP_OTP(
        id = "CAT_LOGIN_SIGNUP_OTP",
        displayTitle = "Login / Signup / OTP",
        hindiTitle = "लॉगिन / साइनअप / ओटीपी",
        followUpEnglish = listOf(
            "Are you trying to verify via Mobile OTP (+91) or Email OTP?",
            "Please do NOT share your password or OTP code in chat—only share the error message shown."
        ),
        followUpHindi = listOf(
            "क्या आप मोबाइल ओटीपी (+91) या ईमेल ओटीपी से वेरिफाई कर रहे हैं?",
            "कृपया चैट में अपना पासवर्ड या ओटीपी कभी साझा न करें—केवल स्क्रीन पर दिखा एरर बताएं।"
        ),
        evidenceHintEnglish = "Attach a screenshot of the login/signup error message (hide any sensitive code).",
        evidenceHintHindi = "लॉगिन/साइनअप एरर मैसेज का स्क्रीनशॉट अपलोड करें।"
    ),
    ACCOUNT_BAN_APPEAL(
        id = "CAT_ACCOUNT_BAN_APPEAL",
        displayTitle = "Account Ban / Appeal",
        hindiTitle = "अकाउंट बैन / अपील",
        followUpEnglish = listOf(
            "Is your account showing a Warning, 3-Day Temporary Suspension, or Permanent Ban?",
            "Please explain why you believe the moderation decision should be reviewed."
        ),
        followUpHindi = listOf(
            "क्या आपके अकाउंट पर चेतावनी (Warning), 3-दिन का अस्थायी सस्पेंशन, या स्थायी बैन दिख रहा है?",
            "कृपया बताएं कि आपके अनुसार इस निर्णय की समीक्षा क्यों की जानी चाहिए।"
        ),
        evidenceHintEnglish = "Attach a screenshot of the suspension/ban notice and any supporting proof for your appeal.",
        evidenceHintHindi = "सस्पेंशन/बैन नोटिस का स्क्रीनशॉट और अपनी अपील के समर्थन में प्रमाण संलग्न करें।",
        requiresHumanReviewForAction = true
    ),
    CHAT_GIFTS_PK(
        id = "CAT_CHAT_GIFTS_PK",
        displayTitle = "Chat / Gifts / PK Battle",
        hindiTitle = "चैट / गिफ्ट्स / पीके बैटल",
        followUpEnglish = listOf(
            "Did the issue occur while sending a Virtual Gift, chatting in Live Room, or during a PK Battle score update?",
            "Which gift item and room/reel were involved?"
        ),
        followUpHindi = listOf(
            "क्या यह समस्या वर्चुअल गिफ्ट भेजते समय, लाइव चैट में, या पीके बैटल स्कोर अपडेट के दौरान हुई?",
            "कौन सा गिफ्ट और रूम/रील इसमें शामिल था?"
        ),
        evidenceHintEnglish = "Attach a screenshot or short video of the PK Battle or Gift ledger.",
        evidenceHintHindi = "पीके बैटल या गिफ्ट लेजर का स्क्रीनशॉट या छोटा वीडियो जोड़ें।"
    ),
    PROFILE_SETTINGS(
        id = "CAT_PROFILE_SETTINGS",
        displayTitle = "Profile / Account Settings",
        hindiTitle = "प्रोफ़ाइल / अकाउंट सेटिंग्स",
        followUpEnglish = listOf(
            "Do you need help updating your Creator Bio, Content Categories, VIP badge, or privacy settings?",
            "Tell us which profile change is not saving."
        ),
        followUpHindi = listOf(
            "क्या आपको क्रिएटर बायो, कंटेंट कैटेगरी, VIP बैज, या प्राइवेसी सेटिंग्स अपडेट करने में सहायता चाहिए?",
            "बताएं कि कौन सा बदलाव सेव नहीं हो रहा है।"
        ),
        evidenceHintEnglish = "Attach a screenshot of your Profile or Settings screen.",
        evidenceHintHindi = "अपनी प्रोफ़ाइल या सेटिंग्स स्क्रीन का स्क्रीनशॉट अपलोड करें।"
    ),
    PAYMENT_FAILED_REFUND(
        id = "CAT_PAYMENT_FAILED_REFUND",
        displayTitle = "Payment Failed / Refund Request",
        hindiTitle = "पेमेंट फेल / रिफंड अनुरोध",
        followUpEnglish = listOf(
            "Was the amount debited from your bank/UPI while the Diamond order status showed FAILED?",
            "Please provide the Razorpay Order ID, Payment ID, and transaction date."
        ),
        followUpHindi = listOf(
            "क्या बैंक/UPI से पैसे कट गए जबकि डायमंड ऑर्डर स्टेटस FAILED दिखा रहा है?",
            "कृपया Razorpay Order ID, Payment ID और लेन-देन की तारीख बताएं।"
        ),
        evidenceHintEnglish = "Attach your bank/UPI debit statement screenshot and Razorpay checkout screen.",
        evidenceHintHindi = "बैंक/UPI डेबिट स्टेटमेंट और Razorpay चेकआउट स्क्रीन का स्क्रीनशॉट जोड़ें।",
        requiresHumanReviewForAction = true
    ),
    OTHER_GENERAL(
        id = "CAT_OTHER_GENERAL",
        displayTitle = "Other / General Help",
        hindiTitle = "अन्य / सामान्य सहायता",
        followUpEnglish = listOf(
            "Please enter a custom description of your issue in the 'Other / General Help Description' field.",
            "Include any screen name or steps so our support team can assist you."
        ),
        followUpHindi = listOf(
            "कृपया 'अन्य / सामान्य सहायता विवरण' फ़ील्ड में अपनी समस्या का विवरण लिखें।",
            "स्क्रीन का नाम या स्टेप्स बताएं ताकि हमारी टीम आपकी मदद कर सके।"
        ),
        evidenceHintEnglish = "Attach up to 5 screenshots (max 10 MB each) or videos (max 50 MB each) if helpful.",
        evidenceHintHindi = "यदि आवश्यक हो तो अधिकतम 5 स्क्रीनशॉट (10 MB तक) या वीडियो (50 MB तक) अपलोड करें।"
    );

    val displayName: String
        get() = displayTitle

    val followUpQuestionEn: String
        get() = followUpEnglish.firstOrNull() ?: ""

    val evidencePrompt: String
        get() = evidenceHintEnglish

    companion object {
        val DEPOSIT_DIAMOND_PURCHASE: SupportRequestCategory
            get() = DEPOSIT_DIAMOND

        val WITHDRAWAL_BANK_ACCOUNT: SupportRequestCategory
            get() = WITHDRAWAL_BANK

        val LIVE_STREAMING_PROBLEM: SupportRequestCategory
            get() = LIVE_STREAMING

        val VIDEO_REELS_PROBLEM: SupportRequestCategory
            get() = VIDEO_REELS

        val WALLET_BALANCE_TRANSACTION: SupportRequestCategory
            get() = WALLET_BALANCE

        fun fromTitleOrId(value: String): SupportRequestCategory {
            val trimmed = value.trim()
            return entries.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) ||
                    it.id.equals(trimmed, ignoreCase = true) ||
                    it.displayTitle.equals(trimmed, ignoreCase = true) ||
                    it.hindiTitle == trimmed
            } ?: OTHER_GENERAL
        }

        /**
         * Suggests the most relevant [SupportRequestCategory] based on user query keywords
         * without ever mixing unrelated categories (e.g., never mixes Deposit/Payment with Video/Reels).
         */
        fun suggestCategoryForQuery(query: String): SupportRequestCategory {
            val q = query.lowercase()
            return when {
                q.contains("refund") || q.contains("रिफंड") ||
                    (q.contains("payment") && q.contains("fail")) ||
                    q.contains("debited") || q.contains("पैसे कट") -> PAYMENT_FAILED_REFUND
                q.contains("deposit") || q.contains("diamond purchase") || q.contains("buy diamond") ||
                    q.contains("topup") || q.contains("top-up") || q.contains("डिपॉज़िट") ||
                    q.contains("डायमंड खरीद") -> DEPOSIT_DIAMOND
                q.contains("withdraw") || q.contains("ifsc") || q.contains("bank") ||
                    q.contains("payout") || q.contains("18th") || q.contains("विड्रॉल") ||
                    q.contains("बैंक") -> WITHDRAWAL_BANK
                q.contains("ban") || q.contains("suspend") || q.contains("appeal") ||
                    q.contains("violation") || q.contains("बैन") || q.contains("सस्पेंड") ||
                    q.contains("अपील") -> ACCOUNT_BAN_APPEAL
                q.contains("otp") || q.contains("login") || q.contains("signup") ||
                    q.contains("sign up") || q.contains("password") || q.contains("लॉगिन") ||
                    q.contains("ओटीपी") -> LOGIN_SIGNUP_OTP
                q.contains("wallet") || q.contains("balance") || q.contains("ledger") ||
                    q.contains("वॉलेट") || q.contains("बैलेंस") -> WALLET_BALANCE
                q.contains("live stream") || q.contains("broadcast") || q.contains("लाइव") ||
                    (q.contains("stream") && (q.contains("camera") || q.contains("audio") || q.contains("froze"))) -> LIVE_STREAMING
                q.contains("pk battle") || q.contains("gift") || q.contains("गिफ्ट") ||
                    q.contains("पीके") -> CHAT_GIFTS_PK
                q.contains("reel") || q.contains("video") || q.contains("playback") ||
                    q.contains("upload") || q.contains("रील") || q.contains("वीडियो") -> VIDEO_REELS
                q.contains("profile") || q.contains("category") || q.contains("bio") ||
                    q.contains("setting") || q.contains("प्रोफ़ाइल") -> PROFILE_SETTINGS
                else -> OTHER_GENERAL
            }
        }
    }
}

enum class AttachmentUploadStatus(val displayLabel: String) {
    PENDING("Pending"),
    UPLOADING("Uploading"),
    UPLOADED("Upload successful"),
    UPLOADED_VERIFIED("Upload successful"),
    FAILED("Upload failed")
}

@IgnoreExtraProperties
data class SupportAttachment(
    val id: String = "",
    val attachmentId: String = id,
    val ticketId: String = "",
    val ownerUserId: String = "",
    val fileName: String = "",
    val fileExtension: String = "",
    val mimeType: String = "",
    val mediaType: String = "IMAGE", // IMAGE or VIDEO
    val mediaKind: String = mediaType,
    val fileSizeBytes: Long = 0L,
    val compressedSizeBytes: Long = 0L,
    val uploadProgressPercent: Int = 0,
    val uploadStatus: AttachmentUploadStatus = AttachmentUploadStatus.PENDING,
    val serverConfirmed: Boolean = true,
    val storagePath: String = "",
    val privateStoragePath: String = storagePath,
    val previewUri: String = "",
    val sha256Checksum: String = "",
    val uploadedAtEpochMs: Long = System.currentTimeMillis(),
    val createdAtEpochMs: Long = uploadedAtEpochMs
) {
    /**
     * Never display "Upload successful" unless the backend confirms the upload.
     */
    val isServerConfirmedSuccess: Boolean
        get() = (uploadStatus == AttachmentUploadStatus.UPLOADED || uploadStatus == AttachmentUploadStatus.UPLOADED_VERIFIED) &&
            serverConfirmed &&
            (privateStoragePath.isNotBlank() || storagePath.isNotBlank()) &&
            uploadProgressPercent == 100

    val formattedFileSize: String
        get() {
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.2f MB", mb)
            } else {
                String.format(java.util.Locale.US, "%.1f KB", kb.coerceAtLeast(0.1))
            }
        }

    val formattedSizeLabel: String
        get() = formattedFileSize
}

@IgnoreExtraProperties
data class SupportTicketReply(
    val replyId: String = "",
    val ticketId: String = "",
    val senderUserId: String = "",
    val senderName: String = "",
    val senderRole: String = "USER", // USER, ADMIN, AI_ASSISTANT
    val message: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SupportChatMessage(
    val id: String = "",
    val sender: String = "AI_ASSISTANT", // USER, AI_ASSISTANT, HUMAN_AGENT
    val message: String = "",
    val language: String = "EN", // EN or HI
    val selectedCategory: SupportRequestCategory = SupportRequestCategory.OTHER_GENERAL,
    val suggestedCategory: SupportRequestCategory? = null,
    val requiresHumanEscalation: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    val messageId: String
        get() = id

    val senderRole: String
        get() = sender

    val text: String
        get() = message

    val activeCategory: SupportRequestCategory
        get() = selectedCategory

    val escalatedToHuman: Boolean
        get() = requiresHumanEscalation
}

data class GiftCatalogItem(
    val id: String,
    val name: String,
    val emoji: String,
    val coinsCost: Long,
    val accentHex: String
)

data class CoinPackItem(
    val id: String,
    val title: String,
    val coinsAmount: Long,
    val bonusCoins: Long,
    val priceInr: Int,
    val tag: String
)

object StreamVibeCatalog {
    const val MIN_WITHDRAWAL_DIAMONDS = 10_000L
    const val INR_PER_10000_DIAMONDS = 4_000L
    const val AUTHORIZED_ADMIN_EMAIL = "abdulbarik80000@gmail.com"

    // 1. Mandatory Terms & Conditions Policy Constants
    const val CURRENT_POLICY_VERSION = "v2026.2"
    const val MANDATORY_TERMS_CHECKBOX_LABEL = "I agree to the Terms & Conditions and Community Guidelines."

    // 2. Content Category Selection Constants
    val contentCategories = listOf(
        "Music",
        "Live Music",
        "Live Streaming",
        "Short Videos / Reels",
        "Gaming",
        "Chat",
        "Other"
    )

    // 6. Support Evidence Upload Validation Constants
    const val MAX_IMAGE_ATTACHMENT_BYTES = 10L * 1024L * 1024L // 10 MB
    const val MAX_VIDEO_ATTACHMENT_BYTES = 50L * 1024L * 1024L // 50 MB
    const val MAX_ATTACHMENTS_PER_TICKET = 5
    const val MAX_SUPPORT_ATTACHMENTS = MAX_ATTACHMENTS_PER_TICKET
    val allowedImageExtensions = setOf("jpg", "jpeg", "png", "webp")
    val allowedVideoExtensions = setOf("mp4", "mov")
    val allowedImageMimeTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
    val allowedVideoMimeTypes = setOf("video/mp4", "video/quicktime")

    // 3. Account Violation & Ban Policy Constants
    const val TEMP_SUSPENSION_DAYS = 3
    const val TEMP_SUSPENSION_DURATION_MS = 3L * 24L * 60L * 60L * 1000L // 3 days in ms

    /**
     * Converts eligible Creator Earnings Diamonds to INR.
     * Rate: 10,000 Diamonds = ₹4,000 (₹0.40 per Diamond).
     */
    fun diamondsToInr(diamonds: Long): Long = (diamonds * 40L) / 100L

    /**
     * Converts eligible Creator Earnings Diamonds to INR paise.
     * Rate: 10,000 Diamonds = 400,000 paise (₹4,000.00).
     */
    fun diamondsToPaise(diamonds: Long): Long = diamonds * 40L

    /**
     * Computes VIP Level (1 to 1000) from cumulative VIP XP.
     */
    fun calculateVipLevel(vipXp: Long): Long {
        val rawLevel = 1L + (vipXp / 500L)
        return rawLevel.coerceIn(1L, 1000L)
    }

    val giftItems = listOf(
        GiftCatalogItem("gift_rose", "Neon Rose", "🌹", 10L, "#FF2E93"),
        GiftCatalogItem("gift_fire", "Cyber Flame", "🔥", 25L, "#FF7A00"),
        GiftCatalogItem("gift_heart", "Diamond Heart", "💎", 50L, "#00F0FF"),
        GiftCatalogItem("gift_crown", "Royal Crown", "👑", 100L, "#FFD700"),
        GiftCatalogItem("gift_car", "Super Car", "🏎️", 250L, "#00F5D4"),
        GiftCatalogItem("gift_castle", "Royal Castle", "🏰", 500L, "#FF9F1C"),
        GiftCatalogItem("gift_galaxy", "Supernova Galaxy", "🌌", 1000L, "#9D4EDD"),
        GiftCatalogItem("gift_rocket", "Hyper Rocket", "🚀", 5000L, "#FF2E93")
    )

    // Complete 14-tier Diamond Store Catalog: ₹99 = 75 Diamonds up to ₹9,999 = 7,500 Diamonds
    val coinPacks = listOf(
        CoinPackItem("pack_99", "75 Diamonds Pack", 75L, 0L, 99, "STARTER"),
        CoinPackItem("pack_199", "150 Diamonds Pack", 150L, 0L, 199, "POPULAR"),
        CoinPackItem("pack_399", "300 Diamonds Pack", 300L, 0L, 399, "CREATOR"),
        CoinPackItem("pack_499", "375 Diamonds Pack", 375L, 0L, 499, "VALUE"),
        CoinPackItem("pack_999", "750 Diamonds Pack", 750L, 0L, 999, "HOT PACK"),
        CoinPackItem("pack_1999", "1,500 Diamonds Pack", 1500L, 0L, 1999, "PRO PACK"),
        CoinPackItem("pack_2999", "2,250 Diamonds Pack", 2250L, 0L, 2999, "STREAMER"),
        CoinPackItem("pack_3999", "3,000 Diamonds Pack", 3000L, 0L, 3999, "ELITE"),
        CoinPackItem("pack_4999", "3,750 Diamonds Pack", 3750L, 0L, 4999, "ROYAL"),
        CoinPackItem("pack_5999", "4,500 Diamonds Pack", 4500L, 0L, 5999, "VIP BOOST"),
        CoinPackItem("pack_6999", "5,250 Diamonds Pack", 5250L, 0L, 6999, "MEGA CHEST"),
        CoinPackItem("pack_7999", "6,000 Diamonds Pack", 6000L, 0L, 7999, "ULTRA VIP"),
        CoinPackItem("pack_8999", "6,750 Diamonds Pack", 6750L, 0L, 8999, "SUPREME"),
        CoinPackItem("pack_9999", "7,500 Diamonds Pack", 7500L, 0L, 9999, "MAX VAULT")
    )

    val adminPromoAllocationPresets = listOf(1000L, 5000L, 10000L, 25000L)

    /**
     * Server-side authoritative lookup of a Diamond package by ID.
     * Never trusts client-supplied price or diamond amounts.
     */
    fun findCanonicalPack(packId: String): CoinPackItem? {
        return coinPacks.firstOrNull { it.id == packId }
    }

    /**
     * Computes the official Razorpay HMAC-SHA256 signature for `razorpay_order_id + "|" + razorpay_payment_id`
     * on the backend verifier using the server-side secret.
     */
    fun computeRazorpayHmacSha256(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        serverKeySecret: String
    ): String {
        val payload = "$razorpayOrderId|$razorpayPaymentId"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(
            serverKeySecret.toByteArray(Charsets.UTF_8),
            "HmacSHA256"
        )
        mac.init(secretKeySpec)
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}


