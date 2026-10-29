package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import com.example.data.AccountBanAppeal
import com.example.data.AdminAuditLog
import com.example.data.AiCustomerSupportAssistant
import com.example.data.CoinTransaction
import com.example.data.LiveRoom
import com.example.data.ReelPost
import com.example.data.StreamVibeCatalog
import com.example.data.SupportAttachment
import com.example.data.SupportChatMessage
import com.example.data.SupportEvidenceValidator
import com.example.data.SupportRequestCategory
import com.example.data.SupportTicket
import com.example.data.SupportTicketStatus
import com.example.data.UserProfile
import com.example.data.WithdrawalRequest
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.DarkStudioBg
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.RoyalGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportAdminAndPoliciesTab(
    userProfile: UserProfile?,
    currentUserEmail: String,
    reelsState: UiState<List<ReelPost>>,
    liveRoomsState: UiState<List<LiveRoom>>,
    transactionsState: UiState<List<CoinTransaction>>,
    ticketsState: UiState<List<SupportTicket>>,
    onCreateSupportTicket: (subject: String, category: String, description: String, priority: String) -> Unit,
    onUpdateSupportTicket: (ticketId: String, status: String, adminReply: String) -> Unit,
    onSignOut: () -> Unit,
    withdrawalsState: UiState<List<WithdrawalRequest>> = UiState.Success(emptyList()),
    adminAuditLogsState: UiState<List<AdminAuditLog>> = UiState.Success(emptyList()),
    onAllocateAdminDiamonds: (amount: Long, reason: String) -> Unit = { _, _ -> },
    onVerifyWithdrawalPayout: (WithdrawalRequest) -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToReels: () -> Unit = {},
    onNavigateToUpload: () -> Unit = {},
    supportChatHistory: List<SupportChatMessage> = emptyList(),
    stagedAttachments: List<SupportAttachment> = emptyList(),
    banAppeals: List<AccountBanAppeal> = emptyList(),
    onSendAiSupportMessage: ((message: String, category: SupportRequestCategory, customCategoryDesc: String) -> Unit)? = null,
    onUploadEvidenceAttachment: ((fileName: String, mimeType: String, fileSizeBytes: Long) -> Unit)? = null,
    onRemoveStagedAttachment: ((attachmentId: String) -> Unit)? = null,
    onCreateSupportTicketWithEvidence: ((subject: String, category: String, customCategoryDesc: String, description: String, priority: String, consentAccepted: Boolean) -> Unit)? = null,
    onReplyOrReopenTicket: ((ticketId: String, replyMessage: String, reopen: Boolean) -> Unit)? = null,
    onSubmitBanAppeal: ((appealMessage: String) -> Unit)? = null,
    onAdminReviewBanAppeal: ((appealId: String, approveAndRestore: Boolean, adminNote: String) -> Unit)? = null,
    onUpdateContentCategories: ((categories: List<String>) -> Unit)? = null
) {
    val tapGuard = rememberSingleTapGuard()
    // SUPPORT (default), ADMIN, PROFILE, SETTINGS, POLICIES, APK_GUIDE
    var selectedSection by remember { mutableStateOf("SUPPORT") }
    var selectedSupportCategory by remember { mutableStateOf(SupportRequestCategory.DEPOSIT_DIAMOND_PURCHASE) }
    var customCategoryDescription by remember { mutableStateOf("") }
    var ticketSubject by remember { mutableStateOf("") }
    var ticketCategory by remember { mutableStateOf(SupportRequestCategory.DEPOSIT_DIAMOND_PURCHASE.displayName) }
    var ticketPriority by remember { mutableStateOf("HIGH") }
    var ticketDescription by remember { mutableStateOf("") }
    var ticketValidationMessage by remember { mutableStateOf<String?>(null) }

    // AI Support Chat local fallback state (when used standalone in unit tests)
    val localChatMessages = remember { mutableStateListOf<SupportChatMessage>() }
    val effectiveChatMessages = if (supportChatHistory.isNotEmpty()) supportChatHistory else localChatMessages
    var aiChatInput by remember { mutableStateOf("") }
    var pendingSuggestedCategory by remember { mutableStateOf<SupportRequestCategory?>(null) }

    // Evidence Upload local state
    val localStagedAttachments = remember { mutableStateListOf<SupportAttachment>() }
    val effectiveAttachments = if (stagedAttachments.isNotEmpty()) stagedAttachments else localStagedAttachments
    var customEvidenceFileName by remember { mutableStateOf("payment_proof.png") }
    var customEvidenceMimeType by remember { mutableStateOf("image/png") }
    var customEvidenceSizeMb by remember { mutableStateOf("2") }
    var evidenceConsentChecked by remember { mutableStateOf(true) }
    var evidenceUploadFeedback by remember { mutableStateOf<String?>(null) }

    // Ban appeal local state
    var banAppealInput by remember { mutableStateOf("") }
    var banAppealFeedback by remember { mutableStateOf<String?>(null) }

    // Ticket reply state
    var activeReplyTicketId by remember { mutableStateOf<String?>(null) }
    var ticketReplyInput by remember { mutableStateOf("") }

    // Admin ticket filter state
    var adminCategoryFilter by remember { mutableStateOf("ALL") }
    var adminStatusFilter by remember { mutableStateOf("ALL") }

    // Content Categories state in Profile/Settings
    var selectedCreatorCategories by remember(userProfile?.contentCategories) {
        mutableStateOf(
            userProfile?.contentCategories?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf("Short Videos / Reels", "Live Streaming")
        )
    }

    // Profile & Settings interactive states (Images 18 & 19)
    var profileBio by remember(userProfile?.bio) {
        mutableStateOf(userProfile?.bio?.ifBlank { "StreamVibe Live Creator • PK Champion • Daily Reels & Music 🎬✨" } ?: "StreamVibe Live Creator • PK Champion • Daily Reels & Music 🎬✨")
    }
    var isEditingProfile by remember { mutableStateOf(false) }
    var selectedProfileContentTab by remember { mutableStateOf("REELS") } // REELS, LIVE_REPLAYS, POSTS
    var profileStatusToast by remember { mutableStateOf<String?>(null) }

    var darkModeEnabled by remember { mutableStateOf(true) }
    var autoPlayReelsEnabled by remember { mutableStateOf(true) }
    var hdVideoQualityEnabled by remember { mutableStateOf(true) }
    var dataSaverEnabled by remember { mutableStateOf(false) }
    var pushNotificationsEnabled by remember { mutableStateOf(true) }
    var twoFactorEnabled by remember { mutableStateOf(true) }

    val resolvedReports = remember { mutableStateListOf<String>() }

    val reelsList = (reelsState as? UiState.Success)?.data ?: emptyList()
    val reelsCount = reelsList.size
    val liveCount = (liveRoomsState as? UiState.Success)?.data?.size ?: 0
    val txList = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val tickets = (ticketsState as? UiState.Success)?.data ?: emptyList()
    val withdrawals = (withdrawalsState as? UiState.Success)?.data ?: emptyList()
    val auditLogs = (adminAuditLogsState as? UiState.Success)?.data ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Profile & Sign Out Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(CyberViolet.copy(alpha = 0.35f), DarkSurfaceCard)
                        )
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "@${userProfile?.username ?: "VibeCreator"} • VIP Lv.${userProfile?.vipLevel ?: 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$currentUserEmail • OTP: ${if (userProfile?.isVerifiedOtp == true) "Verified ✓" else "Pending"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        tapGuard.runOnce("sign_out_button") {
                            onSignOut()
                        }
                    },
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out", maxLines = 1)
                }
            }
        }

        // Clean 2x3 Section Switcher Dashboard Grid (Support, Admin, Profile, Settings, Terms & Policy, APK)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == "SUPPORT",
                    onClick = { selectedSection = "SUPPORT" },
                    label = { Text("💬 24/7 Support", maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("section_support_chip")
                )
                FilterChip(
                    selected = selectedSection == "ADMIN",
                    onClick = { selectedSection = "ADMIN" },
                    label = { Text("🛡 Admin Desk", maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("section_admin_chip")
                )
                FilterChip(
                    selected = selectedSection == "PROFILE",
                    onClick = { selectedSection = "PROFILE" },
                    label = { Text("👤 My Profile", maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("section_profile_chip")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == "SETTINGS",
                    onClick = { selectedSection = "SETTINGS" },
                    label = { Text("⚙️ Settings", maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("section_settings_chip")
                )
                FilterChip(
                    selected = selectedSection == "POLICIES",
                    onClick = { selectedSection = "POLICIES" },
                    label = { Text("📜 Policies", maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("section_policies_chip")
                )
                FilterChip(
                    selected = selectedSection == "APK_GUIDE",
                    onClick = { selectedSection = "APK_GUIDE" },
                    label = { Text("📦 APK Build", maxLines = 1) },
                    modifier = Modifier.weight(1f).testTag("section_apk_chip")
                )
            }
        }

        when (selectedSection) {
            "SUPPORT" -> {
                // 1. Account Suspension / Ban Notice & Appeal Card (shown when suspended/banned or when Account Ban / Appeal category is selected)
                val acctStatus = userProfile?.accountStatus ?: "ACTIVE"
                if (acctStatus != "ACTIVE" || selectedSupportCategory == SupportRequestCategory.ACCOUNT_BAN_APPEAL) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_ban_notice_and_appeal_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261224)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Account Moderation & Appeal Center (Status: $acctStatus)",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonMagenta,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (userProfile?.suspensionReason?.isNotBlank() == true) {
                                    "Reason: ${userProfile.suspensionReason}"
                                } else {
                                    "Policy Rule: 1st confirmed violation -> Warning + 3-day temporary suspension. Repeated or severe violations -> Permanent ban after moderator review. Single unreviewed user reports never automatically ban your account."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Text(
                                text = "Wallet Protection: Legitimate transaction records, pending payouts, and wallet balances (💎 ${userProfile?.coinsBalance ?: 0L}) are preserved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldLive
                            )
                            OutlinedTextField(
                                value = banAppealInput,
                                onValueChange = { banAppealInput = it },
                                label = { Text("Explain why your suspension/ban should be reviewed") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ban_appeal_message_input")
                            )
                            if (banAppealFeedback != null) {
                                Text(
                                    text = banAppealFeedback!!,
                                    color = ElectricCyan,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("ban_appeal_feedback_text")
                                )
                            }
                            Button(
                                onClick = {
                                    tapGuard.runOnce("submit_ban_appeal_button") {
                                        if (banAppealInput.trim().length < 10) {
                                            banAppealFeedback = "Please provide at least 10 characters explaining your appeal."
                                        } else {
                                            onSubmitBanAppeal?.invoke(banAppealInput.trim())
                                            banAppealFeedback = "Appeal submitted for Trust & Safety human moderator review."
                                            banAppealInput = ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DarkStudioBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("submit_ban_appeal_button")
                            ) {
                                Text("Submit Official Ban / Suspension Appeal", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. Support Request Category Selection ("What do you need help with?") + AI Customer Support Chat
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_support_chat_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "What do you need help with?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Selected Support Category: ${selectedSupportCategory.displayName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldLive,
                                    modifier = Modifier.testTag("selected_support_category_badge")
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SupportRequestCategory.entries.forEach { categoryItem ->
                                FilterChip(
                                    selected = selectedSupportCategory == categoryItem,
                                    onClick = {
                                        selectedSupportCategory = categoryItem
                                        ticketCategory = categoryItem.displayName
                                        pendingSuggestedCategory = null
                                        ticketValidationMessage = null
                                    },
                                    label = { Text(categoryItem.displayName) },
                                    modifier = Modifier.testTag("support_category_chip_${categoryItem.name}")
                                )
                            }
                        }

                        if (selectedSupportCategory == SupportRequestCategory.OTHER_GENERAL) {
                            OutlinedTextField(
                                value = customCategoryDescription,
                                onValueChange = {
                                    customCategoryDescription = it
                                    ticketValidationMessage = null
                                },
                                label = { Text("Describe your custom topic (Required for Other / General Help) *") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("support_other_custom_description_input")
                            )
                        }

                        // Bilingual AI Customer Support Chat Box (Hindi & English)
                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "StreamVibe AI Customer Support (English & हिंदी)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ask about Reels, Live Streaming, OTP, Razorpay Diamond Deposits, Wallet Balances, Withdrawals, or Ban Appeals. Never share your password, OTP, or private keys.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                effectiveChatMessages.takeLast(4).forEach { msg ->
                                    Surface(
                                        color = if (msg.sender == "USER") CyberViolet.copy(alpha = 0.25f) else DarkSurfaceElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = if (msg.sender == "USER") "You (${msg.selectedCategory.displayName})" else "StreamVibe AI Support (${msg.language})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (msg.sender == "USER") NeonMagenta else EmeraldLive
                                            )
                                            Text(
                                                text = msg.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.testTag("ai_chat_message_${msg.sender.lowercase()}")
                                            )
                                            if (msg.requiresHumanEscalation) {
                                                Text(
                                                    text = "⚡ Escalated to Human Support Specialist for backend verification",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = RoyalGold,
                                                    modifier = Modifier.testTag("ai_chat_human_escalation_badge")
                                                )
                                            }
                                        }
                                    }
                                }

                                if (pendingSuggestedCategory != null && pendingSuggestedCategory != selectedSupportCategory) {
                                    val suggested = pendingSuggestedCategory!!
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Suggested Category: ${suggested.displayName}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = RoyalGold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                selectedSupportCategory = suggested
                                                ticketCategory = suggested.displayName
                                                pendingSuggestedCategory = null
                                            },
                                            modifier = Modifier.testTag("apply_suggested_category_button")
                                        ) {
                                            Text("Confirm Category", color = ElectricCyan)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = aiChatInput,
                                        onValueChange = { aiChatInput = it },
                                        label = { Text("Ask in English or हिंदी...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ai_support_chat_input")
                                    )
                                    Button(
                                        onClick = {
                                            tapGuard.runOnce("ai_support_send_button") {
                                                val msgText = aiChatInput.trim()
                                                if (msgText.isNotEmpty()) {
                                                    val userMsg = SupportChatMessage(
                                                        id = "usr_${System.currentTimeMillis()}",
                                                        sender = "USER",
                                                        message = msgText,
                                                        selectedCategory = selectedSupportCategory,
                                                        createdAtEpochMs = System.currentTimeMillis()
                                                    )
                                                    val aiReply = AiCustomerSupportAssistant.generateLocalReply(
                                                        userMessage = msgText,
                                                        selectedCategory = selectedSupportCategory,
                                                        customCategoryDescription = customCategoryDescription
                                                    )
                                                    localChatMessages.add(userMsg)
                                                    localChatMessages.add(aiReply)
                                                    pendingSuggestedCategory = aiReply.suggestedCategory
                                                    onSendAiSupportMessage?.invoke(
                                                        msgText,
                                                        selectedSupportCategory,
                                                        customCategoryDescription
                                                    )
                                                    aiChatInput = ""
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DarkStudioBg),
                                        modifier = Modifier.testTag("ai_support_send_button")
                                    ) {
                                        Text("Ask AI", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 24/7 Help & Support Ticket Submission + Evidence Upload (Images/Videos)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = EmeraldLive)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "24/7 Help & Support Ticket Desk",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Category: ${selectedSupportCategory.displayName} • ${selectedSupportCategory.followUpQuestionEn}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("NORMAL", "HIGH", "URGENT").forEach { prio ->
                                FilterChip(
                                    selected = ticketPriority == prio,
                                    onClick = { ticketPriority = prio },
                                    label = { Text(prio) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = ticketSubject,
                            onValueChange = { ticketSubject = it },
                            label = { Text("Ticket Subject") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ticket_subject_input")
                        )

                        OutlinedTextField(
                            value = ticketDescription,
                            onValueChange = { ticketDescription = it },
                            label = { Text("Describe your issue in detail") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ticket_description_input")
                        )

                        // Evidence Upload Section (Images JPG/PNG/WEBP <= 10MB, Videos MP4/MOV <= 50MB, Max 5 files)
                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("support_evidence_upload_section")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Upload Screenshot or Video Evidence (${effectiveAttachments.size}/${StreamVibeCatalog.MAX_SUPPORT_ATTACHMENTS})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Allowed: Images (JPG, PNG, WEBP up to 10 MB) • Videos (MP4, MOV up to 50 MB) • Max 5 attachments. Recommended: ${selectedSupportCategory.evidencePrompt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            tapGuard.runOnce("attach_screenshot_evidence_button") {
                                                val fileName = "screenshot_${effectiveAttachments.size + 1}.png"
                                                val sizeBytes = 2L * 1024L * 1024L
                                                val v = SupportEvidenceValidator.validateAttachment(
                                                    fileName = fileName,
                                                    mimeType = "image/png",
                                                    fileSizeBytes = sizeBytes,
                                                    currentAttachmentCount = effectiveAttachments.size
                                                )
                                                if (!v.isValid) {
                                                    evidenceUploadFeedback = v.errorMessage
                                                } else {
                                                    val att = SupportAttachment(
                                                        id = "att_${System.currentTimeMillis()}_${effectiveAttachments.size + 1}",
                                                        ownerUserId = userProfile?.id ?: "current_user",
                                                        fileName = fileName,
                                                        mimeType = "image/png",
                                                        mediaType = "IMAGE",
                                                        fileSizeBytes = sizeBytes,
                                                        uploadProgressPercent = 100,
                                                        uploadStatus = com.example.data.AttachmentUploadStatus.UPLOADED_VERIFIED,
                                                        storagePath = "private_support_evidence/$fileName",
                                                        uploadedAtEpochMs = System.currentTimeMillis()
                                                    )
                                                    localStagedAttachments.add(att)
                                                    onUploadEvidenceAttachment?.invoke(fileName, "image/png", sizeBytes)
                                                    evidenceUploadFeedback = "Upload successful: $fileName (${att.formattedSizeLabel})"
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("attach_screenshot_evidence_button")
                                    ) {
                                        Text("+ Screenshot (PNG)")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            tapGuard.runOnce("attach_video_evidence_button") {
                                                val fileName = "screen_recording_${effectiveAttachments.size + 1}.mp4"
                                                val sizeBytes = 12L * 1024L * 1024L
                                                val v = SupportEvidenceValidator.validateAttachment(
                                                    fileName = fileName,
                                                    mimeType = "video/mp4",
                                                    fileSizeBytes = sizeBytes,
                                                    currentAttachmentCount = effectiveAttachments.size
                                                )
                                                if (!v.isValid) {
                                                    evidenceUploadFeedback = v.errorMessage
                                                } else {
                                                    val att = SupportAttachment(
                                                        id = "att_${System.currentTimeMillis()}_${effectiveAttachments.size + 1}",
                                                        ownerUserId = userProfile?.id ?: "current_user",
                                                        fileName = fileName,
                                                        mimeType = "video/mp4",
                                                        mediaType = "VIDEO",
                                                        fileSizeBytes = sizeBytes,
                                                        uploadProgressPercent = 100,
                                                        uploadStatus = com.example.data.AttachmentUploadStatus.UPLOADED_VERIFIED,
                                                        storagePath = "private_support_evidence/$fileName",
                                                        uploadedAtEpochMs = System.currentTimeMillis()
                                                    )
                                                    localStagedAttachments.add(att)
                                                    onUploadEvidenceAttachment?.invoke(fileName, "video/mp4", sizeBytes)
                                                    evidenceUploadFeedback = "Upload successful: $fileName (${att.formattedSizeLabel})"
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("attach_video_evidence_button")
                                    ) {
                                        Text("+ Video (MP4)")
                                    }
                                }

                                if (evidenceUploadFeedback != null) {
                                    Text(
                                        text = evidenceUploadFeedback!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (evidenceUploadFeedback!!.startsWith("Upload successful")) EmeraldLive else Color(0xFFFF5252),
                                        modifier = Modifier.testTag("evidence_upload_feedback_text")
                                    )
                                }

                                // Staged Attachments Preview & Remove List
                                effectiveAttachments.forEach { att ->
                                    Surface(
                                        color = DarkSurfaceElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${if (att.mediaType == "VIDEO") "🎬 Video Preview" else "🖼️ Image Preview"}: ${att.fileName}",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "${att.mimeType} • ${att.formattedSizeLabel} • Progress: ${att.uploadProgressPercent}% • ${if (att.isServerConfirmedSuccess) "Upload successful ✓" else att.uploadStatus.name}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (att.isServerConfirmedSuccess) EmeraldLive else RoyalGold
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        localStagedAttachments.removeAll { it.id == att.id }
                                                        onRemoveStagedAttachment?.invoke(att.id)
                                                    },
                                                    modifier = Modifier.testTag("remove_attachment_button_${att.id}")
                                                ) {
                                                    Text("Remove", color = NeonMagenta)
                                                }
                                            }
                                            LinearProgressIndicator(
                                                progress = { (att.uploadProgressPercent / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp),
                                                color = EmeraldLive
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = evidenceConsentChecked,
                                        onCheckedChange = { evidenceConsentChecked = it },
                                        colors = CheckboxDefaults.colors(checkedColor = EmeraldLive),
                                        modifier = Modifier.testTag("evidence_consent_checkbox")
                                    )
                                    Text(
                                        text = "I consent to share attached screenshots/videos privately with StreamVibe Support.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (ticketValidationMessage != null) {
                            Text(
                                text = ticketValidationMessage!!,
                                color = Color(0xFFFF5252),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("ticket_validation_error_text")
                            )
                        }

                        Button(
                            onClick = {
                                tapGuard.runOnce("submit_ticket_button") {
                                    if (selectedSupportCategory == SupportRequestCategory.OTHER_GENERAL && customCategoryDescription.isBlank()) {
                                        ticketValidationMessage = "Please enter a custom description when 'Other / General Help' is selected."
                                        return@runOnce
                                    }
                                    if (effectiveAttachments.isNotEmpty() && !evidenceConsentChecked) {
                                        ticketValidationMessage = "Please accept the evidence upload consent checkbox before submitting attachments."
                                        return@runOnce
                                    }
                                    ticketValidationMessage = null
                                    val subj = ticketSubject.ifBlank { "Help with $ticketCategory" }
                                    val desc = ticketDescription.ifBlank { "Requesting 24/7 support assistance." }
                                    ticketSubject = ""
                                    ticketDescription = ""
                                    if (onCreateSupportTicketWithEvidence != null) {
                                        onCreateSupportTicketWithEvidence(
                                            subj,
                                            ticketCategory,
                                            customCategoryDescription,
                                            desc,
                                            ticketPriority,
                                            evidenceConsentChecked
                                        )
                                    } else {
                                        onCreateSupportTicket(
                                            subj,
                                            ticketCategory,
                                            desc,
                                            ticketPriority
                                        )
                                    }
                                    localStagedAttachments.clear()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_ticket_button")
                        ) {
                            Text("Submit 24/7 Support Ticket", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Active Tickets List with Reference Number, Status, Attachments, and Reply / Reopen
                Text(
                    text = "Your Support Tickets (${tickets.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                tickets.forEach { tkt ->
                    val refNum = tkt.ticketNumber.ifBlank { "SV-TKT-${tkt.id.takeLast(6).uppercase()}" }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ref: $refNum • ${tkt.subject}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Category: ${tkt.category}${if (tkt.customCategoryDescription.isNotBlank()) " (${tkt.customCategoryDescription})" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricCyan
                                    )
                                }
                                Surface(
                                    color = if (tkt.status.equals("Resolved", ignoreCase = true)) EmeraldLive.copy(alpha = 0.2f) else RoyalGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${tkt.priority} • ${tkt.status}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (tkt.status.equals("Resolved", ignoreCase = true)) EmeraldLive else RoyalGold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = tkt.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tkt.attachments.isNotEmpty()) {
                                Text(
                                    text = "📎 Private Evidence Attached: ${tkt.attachments.size} file(s) (${tkt.attachments.joinToString { it.fileName }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoyalGold
                                )
                            }
                            Text(
                                text = "Support Reply: ${tkt.adminReply}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan
                            )
                            tkt.replies.forEach { rep ->
                                Text(
                                    text = "[${rep.senderRole}] ${rep.senderName}: ${rep.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        activeReplyTicketId = if (activeReplyTicketId == tkt.id) null else tkt.id
                                    },
                                    modifier = Modifier.testTag("toggle_reply_ticket_${tkt.id}")
                                ) {
                                    Text("Reply")
                                }
                                if (tkt.status.equals("Resolved", ignoreCase = true) || tkt.status.equals("Closed", ignoreCase = true)) {
                                    OutlinedButton(
                                        onClick = {
                                            onReplyOrReopenTicket?.invoke(
                                                tkt.id,
                                                "Reopening ticket $refNum — issue still requires assistance.",
                                                true
                                            )
                                        },
                                        modifier = Modifier.testTag("reopen_ticket_button_${tkt.id}")
                                    ) {
                                        Text("Reopen Ticket", color = NeonMagenta)
                                    }
                                }
                            }
                            if (activeReplyTicketId == tkt.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = ticketReplyInput,
                                        onValueChange = { ticketReplyInput = it },
                                        label = { Text("Add reply to $refNum") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ticket_reply_input_${tkt.id}")
                                    )
                                    Button(
                                        onClick = {
                                            if (ticketReplyInput.isNotBlank()) {
                                                onReplyOrReopenTicket?.invoke(tkt.id, ticketReplyInput.trim(), false)
                                                ticketReplyInput = ""
                                                activeReplyTicketId = null
                                            }
                                        },
                                        modifier = Modifier.testTag("send_ticket_reply_button_${tkt.id}")
                                    ) {
                                        Text("Send")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "ADMIN" -> {
                // Admin Dashboard (Image 17)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "StreamVibe Admin Dashboard",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        // 3-Wallet Balances & Platform Metrics Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                label = "Admin Promo Wallet",
                                value = "💎 ${userProfile?.adminWalletDiamonds ?: 0L}",
                                accent = ElectricCyan,
                                modifier = Modifier.weight(1f).testTag("admin_desk_promo_balance")
                            )
                            MetricTile(
                                label = "User Purchased",
                                value = "💎 ${userProfile?.coinsBalance ?: 0L}",
                                accent = RoyalGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile("Public Reels", "$reelsCount", NeonMagenta, Modifier.weight(1f))
                            MetricTile("Live Rooms", "$liveCount", EmeraldLive, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile("Ledger Txs", "${txList.size}", ElectricCyan, Modifier.weight(1f))
                            MetricTile("Open Tickets", "${tickets.count { it.status != "RESOLVED" }}", RoyalGold, Modifier.weight(1f))
                        }

                        // Quick Admin Promotional Diamond Allocation
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Admin Promotional Diamond Pool (Zero Out-of-Pocket Gifting)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "Allocate promotional Diamonds to your Admin Wallet to support creators on Live Streams & Reels without personal spending. Every allocation and gift is recorded in the Admin Audit Trail.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            tapGuard.runOnce("admin_desk_alloc_10k") {
                                                onAllocateAdminDiamonds(10000L, "Admin Desk +10,000 Promotional Pool")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = DarkStudioBg
                                        ),
                                        modifier = Modifier.weight(1f).testTag("admin_desk_alloc_10k_btn")
                                    ) {
                                        Text("+10,000 Promo 💎", fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            tapGuard.runOnce("admin_desk_alloc_25k") {
                                                onAllocateAdminDiamonds(25000L, "Admin Desk +25,000 Promotional Pool")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberViolet),
                                        modifier = Modifier.weight(1f).testTag("admin_desk_alloc_25k_btn")
                                    ) {
                                        Text("+25,000 Promo 💎", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Admin Monthly Payment Management (18th IST Scheduler, Level 30, ₹10,000 Min Payout, RazorpayX)
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_monthly_payout_management_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Automatic Monthly Payout Management (18th IST)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• Schedule: 18th of Every Month (IST) • Next Run: ${com.example.data.MonthlyPayoutPolicy.formatNextPaymentDateIst()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "• Eligibility Rules: Minimum Payout Threshold ₹${com.example.data.MonthlyPayoutPolicy.MIN_PAYOUT_INR} (Approved Balance) • Required Creator Level: ${com.example.data.MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL}+",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldLive
                                )
                                Text(
                                    text = "• View Reward Validation: ₹${com.example.data.MonthlyPayoutPolicy.REWARD_RATE_INR_PER_MILLION_VIEWS} / 1,000,000 Eligible Views • Bot/Fake & Duplicate Claims Filtered",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "• RazorpayX Payout Gateway: Idempotent Payout IDs + HMAC-SHA256 Webhook Verification + Safe Failed Payout Retry • Role-Based Admin (${com.example.data.StreamVibeCatalog.AUTHORIZED_ADMIN_EMAIL})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Recent Moderation Reports Queue (Image 17)
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_recent_reports_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Recent Moderation Reports & Live Activity",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NeonMagenta,
                                    fontWeight = FontWeight.Bold
                                )
                                listOf(
                                    Triple("rep_1", "@SpamBot_99 • Suspicious View Spike", "Flagged by Anti-Bot Filter"),
                                    Triple("rep_2", "@PK_Challenger • Audio Copyright Check", "Cleared for Live Broadcast")
                                ).forEach { (repId, repTitle, repSub) ->
                                    val isResolved = resolvedReports.contains(repId)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(repTitle, style = MaterialTheme.typography.labelLarge, color = Color.White)
                                            Text(
                                                text = if (isResolved) "Status: Reviewed & Resolved ✓" else repSub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isResolved) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isResolved) {
                                            OutlinedButton(
                                                onClick = { resolvedReports.add(repId) },
                                                modifier = Modifier.testTag("admin_resolve_report_$repId")
                                            ) {
                                                Text("Review", color = EmeraldLive)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Creator Withdrawal Payout Queue
                        if (withdrawals.isNotEmpty()) {
                            Text(
                                text = "Creator Withdrawal Payout Queue (${withdrawals.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldLive
                            )
                            withdrawals.take(5).forEach { req ->
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${req.userName} • 💎 ${req.diamondsAmount} → ₹${req.payoutInr}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Bank: ${req.bankName} (${req.bankAccountMasked}) • ${req.status}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (req.status == "PENDING_VERIFICATION") {
                                            Button(
                                                onClick = {
                                                    tapGuard.runOnce("admin_desk_verify_${req.id}") {
                                                        onVerifyWithdrawalPayout(req)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = EmeraldLive,
                                                    contentColor = DarkStudioBg
                                                ),
                                                modifier = Modifier.testTag("admin_desk_verify_payout_${req.id}")
                                            ) {
                                                Text("Verify Paid", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Admin Audit Trail Summary
                        if (auditLogs.isNotEmpty()) {
                            Text(
                                text = "Recent Admin Audit Logs (${auditLogs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = RoyalGold
                            )
                            auditLogs.take(4).forEach { log ->
                                Surface(
                                    color = DarkStudioBg,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "[${log.actionType}] 💎 ${log.diamondsAmount} • ${log.adminEmail} → @${log.targetUser}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ElectricCyan
                                        )
                                        Text(
                                            text = log.note,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Account Ban Appeals Review Queue (Admin Only)
                        if (banAppeals.isNotEmpty()) {
                            Text(
                                text = "Account Suspension & Ban Appeals (${banAppeals.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonMagenta,
                                fontWeight = FontWeight.Bold
                            )
                            banAppeals.forEach { appeal ->
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "[${appeal.status}] @${appeal.userName} (${appeal.accountStatusAtAppeal.name})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Appeal: ${appeal.appealMessage}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ElectricCyan
                                        )
                                        if (appeal.status == "SUBMITTED") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        onAdminReviewBanAppeal?.invoke(
                                                            appeal.id,
                                                            true,
                                                            "Approved after human moderator review. Account restored."
                                                        )
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive, contentColor = DarkStudioBg),
                                                    modifier = Modifier.testTag("admin_approve_appeal_${appeal.id}")
                                                ) {
                                                    Text("Approve & Restore", fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onAdminReviewBanAppeal?.invoke(
                                                            appeal.id,
                                                            false,
                                                            "Appeal reviewed and declined due to confirmed policy violation."
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("admin_reject_appeal_${appeal.id}")
                                                ) {
                                                    Text("Uphold Action", color = NeonMagenta)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Ticket Queue & Resolution Control (Filter by Category & Status)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ALL", "Submitted", "In Progress", "Waiting for User", "Escalated", "Resolved", "Closed").forEach { statusOpt ->
                                FilterChip(
                                    selected = adminStatusFilter == statusOpt,
                                    onClick = { adminStatusFilter = statusOpt },
                                    label = { Text(statusOpt) },
                                    modifier = Modifier.testTag("admin_status_filter_$statusOpt")
                                )
                            }
                        }

                        val filteredAdminTickets = tickets.filter { tkt ->
                            val catOk = adminCategoryFilter == "ALL" || tkt.category.equals(adminCategoryFilter, ignoreCase = true)
                            val statusOk = adminStatusFilter == "ALL" || tkt.status.equals(adminStatusFilter, ignoreCase = true)
                            catOk && statusOk
                        }

                        if (filteredAdminTickets.isEmpty()) {
                            Text(
                                text = "No support tickets match the selected filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            filteredAdminTickets.forEach { tkt ->
                                val refNum = tkt.ticketNumber.ifBlank { "SV-TKT-${tkt.id.takeLast(6).uppercase()}" }
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "[$refNum • ${tkt.status}] ${tkt.subject} (@${tkt.userName})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Category: ${tkt.category} • ${tkt.description}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (tkt.attachments.isNotEmpty()) {
                                            Text(
                                                text = "🔒 Private Attachments (${tkt.attachments.size}): ${tkt.attachments.joinToString { "${it.fileName} (${it.formattedSizeLabel})" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = RoyalGold
                                            )
                                        }
                                        if (!tkt.status.equals("RESOLVED", ignoreCase = true)) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        tapGuard.runOnce("in_progress_ticket_${tkt.id}") {
                                                            onUpdateSupportTicket(
                                                                tkt.id,
                                                                SupportTicketStatus.IN_PROGRESS.displayName,
                                                                "Admin Desk: Reviewing your ${tkt.category} logs now."
                                                            )
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CyberViolet)
                                                ) {
                                                    Text("Mark In-Progress")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        tapGuard.runOnce("escalate_ticket_${tkt.id}") {
                                                            onUpdateSupportTicket(
                                                                tkt.id,
                                                                SupportTicketStatus.ESCALATED.displayName,
                                                                "Admin Desk: Escalated to Payments & Trust Specialist."
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.testTag("escalate_ticket_button_${tkt.id}")
                                                ) {
                                                    Text("Escalate", color = RoyalGold)
                                                }
                                                Button(
                                                    onClick = {
                                                        tapGuard.runOnce("resolve_ticket_${tkt.id}") {
                                                            onUpdateSupportTicket(
                                                                tkt.id,
                                                                "RESOLVED",
                                                                "Admin Desk: Issue resolved and verified."
                                                            )
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = EmeraldLive,
                                                        contentColor = DarkStudioBg
                                                    ),
                                                    modifier = Modifier.testTag("resolve_ticket_button_${tkt.id}")
                                                ) {
                                                    Text("Resolve Ticket", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "PROFILE" -> {
                // Creator Profile Screen (Image 19)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("creator_profile_screen_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CyberViolet.copy(alpha = 0.45f), DarkSurfaceCard)
                                )
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = NeonMagenta,
                                    shape = CircleShape,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = (userProfile?.username ?: "R").take(1).uppercase(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = userProfile?.username ?: "Riya Sharma",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "@${(userProfile?.username ?: "riyasharma").lowercase().replace(" ", "_")} • 👑 VIP ${userProfile?.vipLevel ?: 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoyalGold
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { isEditingProfile = !isEditingProfile },
                                    modifier = Modifier.testTag("edit_profile_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEditingProfile) "Done" else "Edit")
                                }
                                OutlinedButton(
                                    onClick = {
                                        profileStatusToast = "Profile link copied: streamvibe.app/@${userProfile?.username ?: "creator"}"
                                    },
                                    modifier = Modifier.testTag("share_profile_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Profile", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (isEditingProfile) {
                            OutlinedTextField(
                                value = profileBio,
                                onValueChange = { profileBio = it },
                                label = { Text("Creator Bio") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_bio_input")
                            )
                        } else {
                            Text(
                                text = profileBio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        if (profileStatusToast != null) {
                            Text(
                                text = profileStatusToast!!,
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldLive
                            )
                        }

                        // Followers / Following / Views / VIP Stats Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricTile("Followers", "142.8K", NeonMagenta, Modifier.weight(1f))
                            MetricTile("Following", "380", ElectricCyan, Modifier.weight(1f))
                            MetricTile("Total Views", "12.5M", EmeraldLive, Modifier.weight(1f))
                        }

                        // 6 Quick Profile Action Cards (Image 19)
                        Text(
                            text = "Creator Quick Hub",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProfileQuickActionTile(
                                title = "My Wallet",
                                subtitle = "💎 ${userProfile?.coinsBalance ?: 0L}",
                                accent = RoyalGold,
                                tag = "profile_action_my_wallet",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToWallet
                            )
                            ProfileQuickActionTile(
                                title = "My Content",
                                subtitle = "$reelsCount Reels",
                                accent = NeonMagenta,
                                tag = "profile_action_my_content",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToReels
                            )
                            ProfileQuickActionTile(
                                title = "My Earnings",
                                subtitle = "₹${userProfile?.withdrawableInr ?: 0L}",
                                accent = EmeraldLive,
                                tag = "profile_action_my_earnings",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToWallet
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProfileQuickActionTile(
                                title = "My Followers",
                                subtitle = "142.8K Fans",
                                accent = ElectricCyan,
                                tag = "profile_action_my_followers",
                                modifier = Modifier.weight(1f),
                                onClick = { profileStatusToast = "Showing 142.8K verified followers & top gifters" }
                            )
                            ProfileQuickActionTile(
                                title = "My Gifts",
                                subtitle = "💎 ${userProfile?.creatorEarningsDiamonds ?: 0L}",
                                accent = RoyalGold,
                                tag = "profile_action_my_gifts",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToWallet
                            )
                            ProfileQuickActionTile(
                                title = "Transactions",
                                subtitle = "${txList.size} Records",
                                accent = CyberViolet,
                                tag = "profile_action_tx_history",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToWallet
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProfileQuickActionTile(
                                title = "My Live",
                                subtitle = "$liveCount Streams",
                                accent = NeonMagenta,
                                tag = "profile_action_my_live",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToReels
                            )
                            ProfileQuickActionTile(
                                title = "VIP Level",
                                subtitle = "LVL ${userProfile?.vipLevel ?: 25}",
                                accent = RoyalGold,
                                tag = "profile_action_vip_level",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToWallet
                            )
                            ProfileQuickActionTile(
                                title = "Settings",
                                subtitle = "Account & App",
                                accent = ElectricCyan,
                                tag = "profile_action_settings",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedSection = "SETTINGS" }
                            )
                        }

                        // My Content Sub-Tabs (Reels, Live Replays, Posts)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "REELS" to "My Reels ($reelsCount)",
                                "LIVE_REPLAYS" to "Live Replays ($liveCount)",
                                "POSTS" to "Photo Posts"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedProfileContentTab == key,
                                    onClick = { selectedProfileContentTab = key },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("profile_content_tab_$key")
                                )
                            }
                        }

                        reelsList.take(3).forEach { reel ->
                            Surface(
                                color = DarkStudioBg,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reel.caption,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${reel.category} • ❤️ ${reel.likesCount} • 💎 ${reel.giftsCoinsTotal}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ElectricCyan
                                        )
                                    }
                                    OutlinedButton(onClick = onNavigateToUpload) {
                                        Text("Upload New")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "SETTINGS" -> {
                // Settings Screen (Image 18)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_screen_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Account, App & Privacy Settings",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "App Preferences & Playback",
                            style = MaterialTheme.typography.titleMedium,
                            color = RoyalGold
                        )

                        SettingsToggleRow(
                            title = "Dark Neon Studio Theme",
                            subtitle = "Always use high-contrast OLED dark theme",
                            checked = darkModeEnabled,
                            onCheckedChange = { darkModeEnabled = it },
                            tag = "settings_toggle_dark_mode"
                        )
                        SettingsToggleRow(
                            title = "Auto-Play Reels & Live Previews",
                            subtitle = "Automatically play vertical videos in feed",
                            checked = autoPlayReelsEnabled,
                            onCheckedChange = { autoPlayReelsEnabled = it },
                            tag = "settings_toggle_auto_play"
                        )
                        SettingsToggleRow(
                            title = "1080p HD Video Streaming Quality",
                            subtitle = "Stream Live PK Battles and Reels in Full HD",
                            checked = hdVideoQualityEnabled,
                            onCheckedChange = { hdVideoQualityEnabled = it },
                            tag = "settings_toggle_hd_video"
                        )
                        SettingsToggleRow(
                            title = "Data Saver Mode",
                            subtitle = "Reduce cellular data usage during live streams",
                            checked = dataSaverEnabled,
                            onCheckedChange = { dataSaverEnabled = it },
                            tag = "settings_toggle_data_saver"
                        )

                        Text(
                            text = "Privacy, Security & Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )

                        SettingsToggleRow(
                            title = "Push & Live Gift Notifications",
                            subtitle = "Alerts for PK invites, Diamond gifts & 18th IST payouts",
                            checked = pushNotificationsEnabled,
                            onCheckedChange = { pushNotificationsEnabled = it },
                            tag = "settings_toggle_notifications"
                        )
                        SettingsToggleRow(
                            title = "Two-Factor OTP Security",
                            subtitle = "Require OTP verification for bank changes & logins",
                            checked = twoFactorEnabled,
                            onCheckedChange = { twoFactorEnabled = it },
                            tag = "settings_toggle_2fa"
                        )

                        // Editable Content Categories in Settings ("Choose Your Content Categories")
                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_content_categories_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Choose Your Content Categories",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your selected categories personalize your creator profile and Reel recommendations:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StreamVibeCatalog.contentCategories.forEach { catName ->
                                        val isPicked = selectedCreatorCategories.contains(catName)
                                        FilterChip(
                                            selected = isPicked,
                                            onClick = {
                                                val updated = if (isPicked && selectedCreatorCategories.size > 1) {
                                                    selectedCreatorCategories - catName
                                                } else {
                                                    selectedCreatorCategories + catName
                                                }
                                                selectedCreatorCategories = updated
                                                onUpdateContentCategories?.invoke(updated.toList())
                                            },
                                            label = { Text(catName) },
                                            modifier = Modifier.testTag("settings_category_chip_${catName.lowercase().replace(" / ", "_").replace(" ", "_")}")
                                        )
                                    }
                                }
                            }
                        }

                        // Account Violation and Ban Policy Summary inside Profile Settings
                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_ban_policy_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Account Violation & Ban Policy (${StreamVibeCatalog.CURRENT_POLICY_VERSION})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• 1st Confirmed Violation: Warning + 3-Day Temporary Suspension.\n" +
                                        "• Repeated or Severe Violations: Permanent Account Ban following moderator review.\n" +
                                        "• False Report Protection: Single unreviewed reports never automatically ban your account.\n" +
                                        "• Appeal & Wallet Safety: Appeal any action in Support; legitimate wallet balances & payouts remain protected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }

                        // Reference Screen 17 Info Rows: Account Information, Language: English, Help & Support, About StreamVibe
                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Account Information: ${userProfile?.username ?: "Riya Sharma"} (${currentUserEmail})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Language: English (India) • Help & Support: 24/7 Active • About StreamVibe v2.5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToWallet,
                                modifier = Modifier.weight(1f).testTag("settings_linked_bank_button")
                            ) {
                                Text("Linked Bank & KYC")
                            }
                            OutlinedButton(
                                onClick = { selectedSection = "POLICIES" },
                                modifier = Modifier.weight(1f).testTag("settings_privacy_policy_button")
                            ) {
                                Text("Privacy & Terms")
                            }
                        }

                        Button(
                            onClick = {
                                tapGuard.runOnce("settings_logout_button") {
                                    onSignOut()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log Out of StreamVibe", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "POLICIES" -> {
                // Comprehensive Terms & Conditions & Policies
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gavel, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Terms & Conditions, Privacy & Creator Policies",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        PolicyBlock(
                            title = "1. Separate Brand & Platform Identity",
                            body = "StreamVibe (package com.aistudio.streamvibe.xrqpkz) is a completely independent short-video, reels, and live streaming application and is completely separate from Digital Suvidha."
                        )
                        PolicyBlock(
                            title = "2. Three-Wallet Diamond Architecture & Promotional Gifting",
                            body = "StreamVibe maintains three strictly separated Diamond wallets: (A) Regular User Wallet for purchased Diamonds (₹99 = 75 Diamonds up to ₹9,999 = 7,500 Diamonds), (B) Admin Promotional Wallet with controlled allocations and append-only audit logs for promotional creator support, and (C) Creator Earnings Wallet for Diamonds earned from gifts."
                        )
                        PolicyBlock(
                            title = "3. Creator Earnings & Bank Withdrawal Policy",
                            body = "Creators earn Diamonds in their Creator Earnings Wallet when receiving gifts. Withdrawals apply strictly to eligible Creator Earnings Diamonds at the rate of 10,000 Diamonds = ₹4,000 (minimum withdrawal: 10,000 Diamonds) and require a verified Bank Account (Account Holder Name, Account Number, IFSC, Bank Name)."
                        )
                        PolicyBlock(
                            title = "4. Privacy & PII Isolation Policy",
                            body = "User phone numbers, email addresses, and Bank Account payout details are isolated inside restricted Firestore private subcollections (/users/{userId}/private/info) protected by zero-trust security rules."
                        )
                        PolicyBlock(
                            title = "5. 24/7 Support & Grievance Redressal",
                            body = "Users can raise payment, withdrawal, OTP, or moderation tickets 24/7 via the Support tab. Every ticket receives a tracking ID and real-time status updates."
                        )
                        PolicyBlock(
                            title = "6. Account Violation & Ban Policy (${StreamVibeCatalog.CURRENT_POLICY_VERSION})",
                            body = "• First Confirmed Violation: Users receive an official warning and a 3-day temporary suspension.\n" +
                                "• Repeated or Severe Violations: Repeated violations or confirmed severe violations (fraud, illegal content, severe harassment) result in a permanent account ban following moderator review.\n" +
                                "• Protection Against Unreviewed Reports: A single unreviewed user report never automatically suspends or permanently bans an account.\n" +
                                "• Right to Appeal & Wallet Protection: Users receive a clear reason notice, can submit an appeal, and retain legitimate wallet balances and pending payout records."
                        )
                        PolicyBlock(
                            title = "7. Key Features Summary (Screen 20 Reference)",
                            body = "• User Features: Live Streaming, Short Videos (Reels), Real-Time Chat, Virtual Gifting, PK Battles, VIP Levels (1–1000)\n" +
                                "• Creator Features: Earn Diamonds from Gifts, Convert to Cash (10,000 💎 = ₹4,000), Automatic Monthly Payout on 18th IST (Level 30+), Bank Withdrawal, Detailed Analytics\n" +
                                "• Admin Features: Separate Admin Diamond Wallet, User Management, Transaction Monitoring, Full Control\n" +
                                "• Security: Secure Login (+91 Mobile OTP / Email OTP), Encrypted Transactions, Verified Bank Payouts"
                        )
                    }
                }
            }

            "APK_GUIDE" -> {
                // Phased Audit, Build Verification & APK Generation Guide
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APK Build & Export Guide",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        PolicyBlock(
                            title = "How to Download or Build the Android APK",
                            body = "1. In AI Studio: Open the Settings / Export menu in the top-right corner of Google AI Studio to generate and download the signed APK/AAB or export the project ZIP.\n" +
                                "2. Local Android Studio Build: Run './gradlew assembleDebug' to generate app/build/outputs/apk/debug/app-debug.apk.\n" +
                                "3. Why Direct APK File Download Isn't Sent in Chat: The AI Studio chat assistant compiles and verifies the Android project inside a secure cloud build container and streams it to the built-in Android Emulator, while binary APK downloads are served through the AI Studio UI Export menu."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileQuickActionTile(
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = DarkStudioBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = accent)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Surface(
        color = DarkStudioBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkStudioBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PolicyBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkStudioBg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
