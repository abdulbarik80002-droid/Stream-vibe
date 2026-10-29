package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.BuildConfig
import com.example.data.AdminAuditLog
import com.example.data.CoinPackItem
import com.example.data.CoinTransaction
import com.example.data.DiamondWalletType
import com.example.data.CreatorEarningsLedgerState
import com.example.data.GiftCatalogItem
import com.example.data.LiveChatMessage
import com.example.data.LiveRoom
import com.example.data.MonthlyPayoutEngine
import com.example.data.MonthlyPayoutPolicy
import com.example.data.StreamVibeCatalog
import com.example.data.UserPrivateInfo
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

@Composable
fun LiveVideoAndChatTab(
    currentUserId: String,
    userProfile: UserProfile?,
    liveRoomsState: UiState<List<LiveRoom>>,
    selectedRoomId: String?,
    liveChatState: UiState<List<LiveChatMessage>>,
    onStartLiveRoom: (title: String, topic: String) -> Unit,
    onSelectLiveRoom: (String?) -> Unit,
    onEndLiveRoom: (String) -> Unit,
    onSendChatMessage: (roomId: String, message: String) -> Unit,
    onSendGiftToRoom: (GiftCatalogItem, LiveRoom) -> Unit,
    onStartPkLiveRoom: ((title: String, topic: String, opponentName: String) -> Unit)? = null,
    onInvitePkOpponent: ((roomId: String, opponentName: String) -> Unit)? = null,
    onSendGiftToRoomWithWallet: ((GiftCatalogItem, LiveRoom, Boolean, Boolean) -> Unit)? = null
) {
    val tapGuard = rememberSingleTapGuard()
    var streamTitle by remember { mutableStateOf("Creator Live Q&A + PK Battle") }
    var streamTopic by remember { mutableStateOf("Music & PK") }
    var pkOpponentInput by remember { mutableStateOf("GamingStar_PK") }
    var supportHostInPk by remember { mutableStateOf(true) }
    var chatInput by remember { mutableStateOf("") }
    var showGiftSheetForRoom by remember { mutableStateOf<LiveRoom?>(null) }

    val rooms = (liveRoomsState as? UiState.Success)?.data ?: emptyList()
    val activeRoom = rooms.firstOrNull { it.id == selectedRoomId } ?: rooms.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Go Live / PK Battle Broadcast Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = "Live Video",
                            tint = NeonMagenta
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Video & PK Battle Arena",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    Surface(
                        color = NeonMagenta.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${rooms.size} LIVE NOW",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonMagenta,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        label = { Text("Broadcast Title") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_title_input")
                    )
                    Button(
                        onClick = {
                            tapGuard.runOnce("go_live_button") {
                                onStartLiveRoom(streamTitle, streamTopic)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                        modifier = Modifier.testTag("go_live_button")
                    ) {
                        Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Go Live")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PK Battle Launch / Invite Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pkOpponentInput,
                        onValueChange = { pkOpponentInput = it },
                        label = { Text("PK Opponent Handle") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pk_opponent_input")
                    )
                    Button(
                        onClick = {
                            tapGuard.runOnce("invite_pk_battle_button") {
                                if (activeRoom != null && onInvitePkOpponent != null) {
                                    onInvitePkOpponent(activeRoom.id, pkOpponentInput)
                                } else if (onStartPkLiveRoom != null) {
                                    onStartPkLiveRoom(streamTitle, "PK Battle", pkOpponentInput)
                                } else {
                                    onStartLiveRoom(streamTitle, "PK Battle")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberViolet,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("invite_pk_battle_button")
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Invite for PK")
                    }
                }
            }
        }

        if (activeRoom == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Active Live Broadcast",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Go Live' or 'Invite for PK' above to launch an interactive Live Video & PK Battle Room with real-time chat and Diamond gifts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Active Live Video / PK Battle Stage + Live Chat
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(CyberViolet.copy(alpha = 0.45f), DarkSurfaceElevated, DarkStudioBg)
                            )
                        )
                        .padding(16.dp)
                ) {
                    // Live Stage Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = NeonMagenta,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (activeRoom.isPkBattle) "⚔️ PK BATTLE LIVE" else "● LIVE",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "@${activeRoom.hostName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${activeRoom.title} • 💎 ${activeRoom.totalGiftsCoins} Diamonds",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoyalGold
                            )
                        }

                        if (activeRoom.hostId == currentUserId) {
                            OutlinedButton(
                                onClick = {
                                    tapGuard.runOnce("end_live_button") {
                                        onEndLiveRoom(activeRoom.id)
                                    }
                                },
                                modifier = Modifier.testTag("end_live_button")
                            ) {
                                Text("End Live", color = Color(0xFFFF6B6B))
                            }
                        }
                    }

                    // PK Battle Scoreboard Banner (when PK Battle is active)
                    if (activeRoom.isPkBattle) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val oppName = activeRoom.opponentName.ifBlank { "GamingStar_PK" }
                        val hostScore = activeRoom.hostPkScore
                        val oppScore = activeRoom.opponentPkScore
                        val totalPk = (hostScore + oppScore).coerceAtLeast(1L)
                        val hostFraction = if (hostScore == 0L && oppScore == 0L) 0.5f else (hostScore.toFloat() / totalPk.toFloat()).coerceIn(0.1f, 0.9f)

                        Surface(
                            color = DarkStudioBg.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pk_battle_scoreboard_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "👑 @${activeRoom.hostName}: $hostScore 💎",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NeonMagenta,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "VS • ⏱ 03:45",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = RoyalGold
                                    )
                                    Text(
                                        text = "⚡ @$oppName: $oppScore 💎",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { hostFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = NeonMagenta,
                                    trackColor = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = supportHostInPk,
                                        onClick = { supportHostInPk = true },
                                        label = { Text("Support @${activeRoom.hostName}") }
                                    )
                                    FilterChip(
                                        selected = !supportHostInPk,
                                        onClick = { supportHostInPk = false },
                                        label = { Text("Support @$oppName") }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Room Selector if multiple rooms are live
                    if (rooms.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rooms.take(4).forEach { rm ->
                                FilterChip(
                                    selected = rm.id == activeRoom.id,
                                    onClick = { onSelectLiveRoom(rm.id) },
                                    label = { Text("@${rm.hostName}") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Live Chat Feed
                    val chatMessages = (liveChatState as? UiState.Success)?.data ?: emptyList()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkStudioBg.copy(alpha = 0.65f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (chatMessages.isEmpty()) {
                            item {
                                Text(
                                    text = "Welcome to @${activeRoom.hostName}'s Live Chat! Send Diamond gifts from User Wallet or Admin Promotional Wallet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricCyan
                                )
                            }
                        } else {
                            items(chatMessages, key = { it.id }) { msg ->
                                val isGift = msg.messageType == "GIFT" || msg.messageType == "PK_GIFT"
                                Surface(
                                    color = if (isGift) RoyalGold.copy(alpha = 0.22f) else DarkSurfaceElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "@${msg.senderName}: ",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isGift) RoyalGold else ElectricCyan
                                        )
                                        Text(
                                            text = msg.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chat Input & Gift Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Say something in Live Chat...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_chat_input")
                        )

                        IconButton(
                            onClick = {
                                tapGuard.runOnce("send_live_chat_button") {
                                    if (chatInput.isNotBlank()) {
                                        val msgToSend = chatInput
                                        chatInput = ""
                                        onSendChatMessage(activeRoom.id, msgToSend)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                                .testTag("send_live_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Chat",
                                tint = DarkStudioBg
                            )
                        }

                        Button(
                            onClick = {
                                tapGuard.runOnce("open_live_gift_button") {
                                    showGiftSheetForRoom = activeRoom
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalGold,
                                contentColor = DarkStudioBg
                            ),
                            modifier = Modifier.testTag("open_live_gift_button")
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = "Gift", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gift", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showGiftSheetForRoom != null) {
        val room = showGiftSheetForRoom!!
        GiftPickerBottomCard(
            targetTitle = "@${room.hostName}'s Live Stream",
            senderName = userProfile?.username ?: "Supporter",
            userCoins = userProfile?.coinsBalance ?: 0L,
            adminPromoDiamonds = userProfile?.adminWalletDiamonds ?: 0L,
            onDismiss = { showGiftSheetForRoom = null },
            onSelectGift = { gift ->
                if (onSendGiftToRoomWithWallet != null) {
                    onSendGiftToRoomWithWallet(gift, room, false, supportHostInPk)
                } else {
                    onSendGiftToRoom(gift, room)
                }
                showGiftSheetForRoom = null
            },
            onSelectGiftWithWallet = { gift, useAdminPromo ->
                if (onSendGiftToRoomWithWallet != null) {
                    onSendGiftToRoomWithWallet(gift, room, useAdminPromo, supportHostInPk)
                } else {
                    onSendGiftToRoom(gift, room)
                }
                showGiftSheetForRoom = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletAndRazorpayTab(
    userProfile: UserProfile?,
    transactionsState: UiState<List<CoinTransaction>>,
    onRazorpayTestCheckout: (pack: CoinPackItem, paymentMethodLabel: String) -> Unit,
    privateInfo: UserPrivateInfo? = null,
    withdrawalsState: UiState<List<WithdrawalRequest>> = UiState.Success(emptyList()),
    adminAuditLogsState: UiState<List<AdminAuditLog>> = UiState.Success(emptyList()),
    onAllocateAdminPromoDiamonds: ((amount: Long, note: String) -> Unit)? = null,
    onUpdateAdminLimits: ((maxSingle: Long, dailyLimit: Long) -> Unit)? = null,
    onSaveBankAccount: ((holder: String, acc: String, confirmAcc: String, ifsc: String, bank: String) -> Unit)? = null,
    onRequestWithdrawal: ((diamondsAmount: Long) -> Unit)? = null,
    onVerifyWithdrawalPayout: ((WithdrawalRequest) -> Unit)? = null,
    onUpgradeVipLevel: (() -> Unit)? = null
) {
    val tapGuard = rememberSingleTapGuard()
    var activeWalletSection by remember { mutableStateOf("USER_WALLET") } // USER_WALLET, ADMIN_WALLET, CREATOR_WALLET, VIP_LEVEL
    var selectedPackForCheckout by remember { mutableStateOf<CoinPackItem?>(null) }
    var selectedPaymentMethod by remember { mutableStateOf("UPI Test (success@razorpay)") }
    var showLiveChecklist by remember { mutableStateOf(false) }
    var txFilter by remember { mutableStateOf("ALL") } // ALL, PURCHASE, GIFT, WITHDRAWAL
    var selectedWithdrawalMethod by remember { mutableStateOf("Bank Transfer") } // Bank Transfer, UPI, PayPal, e-Wallet
    var bankUpiIdInput by remember { mutableStateOf("riyasharma@okhdfcbank") }

    // Admin Promotional Allocation State
    var customAllocationInput by remember { mutableStateOf("10000") }
    var allocationNoteInput by remember { mutableStateOf("Promotional Gifting Pool Allocation") }
    var maxAllocLimitInput by remember(userProfile?.adminMaxAllocationLimit) {
        mutableStateOf((userProfile?.adminMaxAllocationLimit ?: 25000L).toString())
    }
    var dailyGiftLimitInput by remember(userProfile?.adminDailyGiftLimit) {
        mutableStateOf((userProfile?.adminDailyGiftLimit ?: 100000L).toString())
    }

    // Creator Bank Account & Withdrawal State
    var bankHolderInput by remember(privateInfo?.bankHolderName) {
        mutableStateOf(privateInfo?.bankHolderName.orEmpty())
    }
    var bankAccInput by remember(privateInfo?.bankAccountNumber) {
        mutableStateOf(privateInfo?.bankAccountNumber.orEmpty())
    }
    var bankConfirmAccInput by remember(privateInfo?.bankAccountNumber) {
        mutableStateOf(privateInfo?.bankAccountNumber.orEmpty())
    }
    var bankIfscInput by remember(privateInfo?.bankIfsc) {
        mutableStateOf(privateInfo?.bankIfsc.orEmpty())
    }
    var bankNameInput by remember(privateInfo?.bankName) {
        mutableStateOf(privateInfo?.bankName.orEmpty())
    }
    var withdrawalDiamondsInput by remember { mutableStateOf("10000") }

    val configuredKey = try {
        BuildConfig.RAZORPAY_KEY_ID.ifBlank { "rzp_test_StreamVibeDemoKey" }
    } catch (e: Exception) {
        "rzp_test_StreamVibeDemoKey"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 3-Wallet Overview Header Card + VIP Badge + Quick Actions (Images 10 & 11)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(CyberViolet.copy(alpha = 0.55f), DarkSurfaceElevated)
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "StreamVibe 3-Wallet Diamond Center",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💎 ${userProfile?.coinsBalance ?: 0L} User Diamonds",
                            style = MaterialTheme.typography.headlineLarge,
                            color = RoyalGold,
                            modifier = Modifier.testTag("user_wallet_balance_text")
                        )
                        Text(
                            text = "Admin Promo: 💎 ${userProfile?.adminWalletDiamonds ?: 50000L} • Creator Earnings: 💎 ${userProfile?.withdrawableDiamonds ?: 0L} (₹${userProfile?.withdrawableInr ?: 0L})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = RoyalGold.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clickable { activeWalletSection = "VIP_LEVEL" }
                            .testTag("vip_badge_header_chip")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "👑 VIP ${userProfile?.vipLevel ?: 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = RoyalGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Levels 1–1000",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3-Wallet + VIP Switcher Tabs (matching Screen 10 & 11 of Reference Design)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeWalletSection == "USER_WALLET",
                        onClick = { activeWalletSection = "USER_WALLET" },
                        label = { Text("User Wallet") },
                        modifier = Modifier.testTag("wallet_tab_user")
                    )
                    FilterChip(
                        selected = activeWalletSection == "ADMIN_WALLET",
                        onClick = { activeWalletSection = "ADMIN_WALLET" },
                        label = { Text("Admin Wallet") },
                        modifier = Modifier.testTag("wallet_tab_admin")
                    )
                    FilterChip(
                        selected = activeWalletSection == "CREATOR_WALLET",
                        onClick = { activeWalletSection = "CREATOR_WALLET" },
                        label = { Text("Creator Wallet") },
                        modifier = Modifier.testTag("wallet_tab_creator")
                    )
                    FilterChip(
                        selected = activeWalletSection == "VIP_LEVEL",
                        onClick = { activeWalletSection = "VIP_LEVEL" },
                        label = { Text("VIP 1–1000") },
                        modifier = Modifier.testTag("wallet_tab_vip")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Wallet Quick Actions Row (Image 11: Top Up, Withdraw, Gift History, Earnings, Bank Account)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_quick_actions_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("Top Up", "USER_WALLET", "wallet_quick_action_topup"),
                        Triple("Withdraw", "CREATOR_WALLET", "wallet_quick_action_withdraw"),
                        Triple("Gift History", "GIFT_HISTORY", "wallet_quick_action_gifts"),
                        Triple("Earnings", "CREATOR_WALLET", "wallet_quick_action_earnings"),
                        Triple("Bank Account", "CREATOR_WALLET", "wallet_quick_action_bank")
                    ).forEach { (label, targetSection, tag) ->
                        Surface(
                            color = DarkStudioBg.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable {
                                    if (targetSection == "GIFT_HISTORY") {
                                        txFilter = "GIFT"
                                    } else {
                                        activeWalletSection = targetSection
                                    }
                                }
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        when (activeWalletSection) {
            "USER_WALLET" -> {
                // WALLET A: REGULAR USER DIAMOND WALLET & 14-TIER DIAMOND STORE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "A. Regular User Diamond Wallet",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "For purchased Diamonds & sending gifts • Verified Payment Credit Only",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricCyan
                                )
                            }
                            Surface(
                                color = EmeraldLive.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "TEST: ${configuredKey.take(12)}...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldLive,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                tapGuard.runOnce("razorpay_live_checklist_button") {
                                    showLiveChecklist = !showLiveChecklist
                                }
                            },
                            modifier = Modifier.testTag("razorpay_live_checklist_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showLiveChecklist) "Hide Live Mode Approval Checklist" else "Razorpay Test vs Live Mode Checklist",
                                color = RoyalGold
                            )
                        }

                        if (showLiveChecklist) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkStudioBg)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Before switching RAZORPAY_MODE from TEST to LIVE:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricCyan
                                )
                                Text("1. Complete Razorpay Merchant KYC, PAN & Bank Settlement verification.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("2. Verify Terms & Conditions, Privacy Policy, and Refund Policy URLs in Razorpay Dashboard.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("3. Generate Live API Keys (rzp_live_*) and store RAZORPAY_KEY_ID in the AI Studio Secrets Panel.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text("4. Diamonds are credited strictly after payment verification with idempotency protection.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        }
                    }
                }

                // Razorpay Test Mode Checkout Sheet (when a pack is clicked)
                if (selectedPackForCheckout != null) {
                    val pack = selectedPackForCheckout!!
                    val previewOrderId = remember(pack.id) {
                        "order_test_${pack.id}_${(pack.priceInr * 100)}"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, EmeraldLive, RoundedCornerShape(24.dp))
                            .testTag("razorpay_test_checkout_sheet"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Razorpay Sandbox Verification (Test Mode)",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldLive
                            )
                            Text(
                                text = "Package: ${pack.coinsAmount} Diamonds • Price: ₹${pack.priceInr}.00 INR (No Duplicate Credit Guard Active)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("razorpay_selected_package_text")
                            )
                            Text(
                                text = "Backend Order: $previewOrderId • Currency: INR (${pack.priceInr * 100} paise) • Key ID: $configuredKey",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("razorpay_order_meta_text")
                            )
                            Text(
                                text = "Server HMAC-SHA256 Signature Verification Enabled • Test transactions are non-withdrawable & excluded from live revenue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Select Test Payment Instrument:", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                            Spacer(modifier = Modifier.height(6.dp))

                            val methods = listOf(
                                "UPI Test (success@razorpay)",
                                "Test Card (4111 **** **** 1111)",
                                "Netbanking Test (HDFC Sandbox)"
                            )
                            methods.forEach { method ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedPaymentMethod = method }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (selectedPaymentMethod == method) EmeraldLive else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = method, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        tapGuard.runOnce("confirm_razorpay_test_pay_button") {
                                            onRazorpayTestCheckout(pack, selectedPaymentMethod)
                                            selectedPackForCheckout = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldLive,
                                        contentColor = DarkStudioBg
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("confirm_razorpay_test_pay_button")
                                ) {
                                    Text("Verify & Credit ${pack.coinsAmount} 💎 (₹${pack.priceInr})", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        tapGuard.runOnce("cancel_razorpay_test_pay_button") {
                                            selectedPackForCheckout = null
                                        }
                                    },
                                    modifier = Modifier.testTag("cancel_razorpay_test_pay_button")
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }


                // Diamond Store Packages (₹99 = 75 Diamonds up to ₹9,999 = 7,500 Diamonds)
                Text(
                    text = "Diamond Store Packages (₹99 to ₹9,999)",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                StreamVibeCatalog.coinPacks.forEach { pack ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coin_pack_${pack.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = NeonMagenta.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = pack.tag,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NeonMagenta,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "₹${pack.priceInr}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = RoyalGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "💎 ${pack.coinsAmount} Diamonds",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }

                            Button(
                                onClick = {
                                    tapGuard.runOnce("buy_pack_button_${pack.id}") {
                                        selectedPackForCheckout = pack
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                                modifier = Modifier.testTag("buy_pack_button_${pack.id}")
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buy ₹${pack.priceInr}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "ADMIN_WALLET" -> {
                // WALLET B: ADMIN PROMOTIONAL DIAMOND WALLET (Separate Balance, Controlled Allocation & Audit Logs)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "B. Admin Promotional Diamond Wallet",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Separate Promotional Diamonds for Admin Gifting — send gifts to creators without spending personal money. Every allocation and gift is recorded in the Audit Log.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = DarkStudioBg,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Available Admin Promotional Balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "💎 ${userProfile?.adminWalletDiamonds ?: 50000L} Promotional Diamonds",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("admin_wallet_balance_text")
                                )
                                Text(
                                    text = "Configured Max Single Allocation: ${userProfile?.adminMaxAllocationLimit ?: 25000L} 💎 • Daily Promo Gift Limit: ${userProfile?.adminDailyGiftLimit ?: 100000L} 💎",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }

                        // Controlled Allocation Presets
                        Text(
                            text = "Controlled Promotional Diamond Allocation",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StreamVibeCatalog.adminPromoAllocationPresets.forEach { preset ->
                                OutlinedButton(
                                    onClick = {
                                        tapGuard.runOnce("promo_preset_$preset") {
                                            customAllocationInput = preset.toString()
                                            onAllocateAdminPromoDiamonds?.invoke(
                                                preset,
                                                "Preset Admin Allocation (+$preset Promo Diamonds)"
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("promo_alloc_preset_$preset")
                                ) {
                                    Text("+$preset 💎", color = RoyalGold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customAllocationInput,
                                onValueChange = { customAllocationInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Allocate Promo Diamonds") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_promo_amount_input")
                            )
                            Button(
                                onClick = {
                                    tapGuard.runOnce("allocate_admin_promo_button") {
                                        val amt = customAllocationInput.toLongOrNull() ?: 0L
                                        onAllocateAdminPromoDiamonds?.invoke(amt, allocationNoteInput)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalGold,
                                    contentColor = DarkStudioBg
                                ),
                                modifier = Modifier.testTag("allocate_admin_promo_button")
                            ) {
                                Text("Allocate 💎", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = allocationNoteInput,
                            onValueChange = { allocationNoteInput = it },
                            label = { Text("Audit Log Reason / Campaign Note") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_promo_note_input")
                        )

                        // Configurable Admin Limits
                        Text(
                            text = "Configurable Admin Wallet Limits",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = maxAllocLimitInput,
                                onValueChange = { maxAllocLimitInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Max Single Alloc (💎)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_max_alloc_limit_input")
                            )
                            OutlinedTextField(
                                value = dailyGiftLimitInput,
                                onValueChange = { dailyGiftLimitInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Daily Gift Limit (💎)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_daily_gift_limit_input")
                            )
                        }
                        Button(
                            onClick = {
                                tapGuard.runOnce("save_admin_limits_button") {
                                    val maxAlloc = maxAllocLimitInput.toLongOrNull() ?: 25000L
                                    val dailyLim = dailyGiftLimitInput.toLongOrNull() ?: 100000L
                                    onUpdateAdminLimits?.invoke(maxAlloc, dailyLim)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberViolet),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_admin_limits_button")
                        ) {
                            Text("Save Configurable Limits & Record Audit")
                        }

                        // Immutable Admin Audit Logs List
                        val auditLogs = (adminAuditLogsState as? UiState.Success)?.data ?: emptyList()
                        Text(
                            text = "Admin Promotional Audit Trail (${auditLogs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        if (auditLogs.isEmpty()) {
                            Text(
                                text = "No admin audit logs recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            auditLogs.take(10).forEach { log ->
                                Surface(
                                    color = DarkStudioBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = log.actionType,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = RoyalGold
                                            )
                                            Text(
                                                text = "${log.diamondsAmount} 💎",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = EmeraldLive,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = log.note,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Target: ${log.targetUser} • Ref: ${log.idempotencyKey}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CREATOR_WALLET" -> {
                // WALLET C: CREATOR EARNINGS WALLET, BANK ACCOUNT VERIFICATION & WITHDRAWAL (10,000 Diamonds = ₹4,000)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "C. Creator Earnings Wallet & Withdrawals",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Conversion Rate: 10,000 Eligible Diamonds = ₹4,000 • Minimum Withdrawal: 10,000 Eligible Diamonds • Only Creator Earnings from gifts are withdrawable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoyalGold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = DarkStudioBg,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                     Text("Total Earnings", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                                     Text(
                                         text = "💎 ${userProfile?.creatorEarningsDiamonds ?: 0L}",
                                         style = MaterialTheme.typography.titleLarge,
                                         color = Color.White,
                                         fontWeight = FontWeight.Bold
                                     )
                                     Text(
                                         text = "≈ ₹${userProfile?.totalCreatorEarningsInr ?: 0L}",
                                         style = MaterialTheme.typography.bodyMedium,
                                         color = RoyalGold
                                     )
                                 }
                            }
                            Surface(
                                color = DarkStudioBg,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Withdrawable Balance", style = MaterialTheme.typography.bodyMedium, color = EmeraldLive)
                                    Text(
                                        text = "💎 ${userProfile?.withdrawableDiamonds ?: 0L}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = EmeraldLive,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("creator_withdrawable_diamonds_text")
                                    )
                                    Text(
                                        text = "= ₹${userProfile?.withdrawableInr ?: 0L}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoyalGold
                                    )
                                }
                            }
                        }

                        // Automatic Monthly Payment (18th IST), Level 30 & ₹10,000 Eligibility Card
                        val payoutEngine = remember { MonthlyPayoutEngine() }
                        val currentCreatorLevel = userProfile?.vipLevel ?: 1L
                        val approvedBalanceInr = userProfile?.withdrawableInr ?: 0L
                        val totalCreatorInr = userProfile?.totalCreatorEarningsInr ?: 0L
                        val pendingInr = (totalCreatorInr - approvedBalanceInr).coerceAtLeast(0L)
                        val isBankVerified = privateInfo?.bankVerified == true
                        val kycStateLabel = privateInfo?.kycStatus?.ifBlank { "pending" } ?: "pending"
                        val maskedAcc = privateInfo?.bankAccountNumber?.let { acc ->
                            if (acc.length >= 4) "****${acc.takeLast(4)}" else acc
                        }.orEmpty()
                        val eligibilityReport = remember(
                            currentCreatorLevel,
                            approvedBalanceInr,
                            isBankVerified,
                            kycStateLabel,
                            maskedAcc,
                            privateInfo?.bankIfsc
                        ) {
                            payoutEngine.evaluateEligibility(
                                CreatorEarningsLedgerState(
                                    creatorId = userProfile?.userId.orEmpty().ifBlank { "creator" },
                                    creatorName = userProfile?.username.orEmpty().ifBlank { "VibeCreator" },
                                    totalEarningsInr = totalCreatorInr,
                                    thisMonthEarningsInr = approvedBalanceInr,
                                    approvedAvailableBalanceInr = approvedBalanceInr,
                                    pendingEarningsInr = pendingInr,
                                    serverVerifiedLevel = currentCreatorLevel,
                                    bankVerified = isBankVerified,
                                    bankName = privateInfo?.bankName.orEmpty(),
                                    bankAccountMasked = maskedAcc,
                                    bankIfsc = privateInfo?.bankIfsc.orEmpty(),
                                    kycStatus = kycStateLabel,
                                    fraudReviewStatus = "CLEAR"
                                )
                            )
                        }

                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("monthly_auto_payout_eligibility_card")
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
                                    Text(
                                        text = "Automatic Monthly Payout (18th IST)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = if (eligibilityReport.isEligible) {
                                            EmeraldLive.copy(alpha = 0.2f)
                                        } else {
                                            NeonMagenta.copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = eligibilityReport.status.displayLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (eligibilityReport.isEligible) EmeraldLive else NeonMagenta,
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("creator_payout_status_badge")
                                        )
                                    }
                                }

                                Text(
                                    text = "• Next Payment Date: ${eligibilityReport.nextPaymentDateLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "• Minimum Payout Threshold: ₹${MonthlyPayoutPolicy.MIN_PAYOUT_INR} • Approved Available: ₹$approvedBalanceInr • Pending: ₹$pendingInr",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoyalGold
                                )
                                Text(
                                    text = "• Level Requirement: Current Level $currentCreatorLevel / Required Level ${MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL} (${if (currentCreatorLevel >= MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL) "Level Eligible ✓" else "Level 1–29 Not Eligible"})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (currentCreatorLevel >= MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL) EmeraldLive else Color.White
                                )
                                Text(
                                    text = "• Bank Status: ${if (isBankVerified) "Verified ✓" else "Unverified"} • KYC Status: ${kycStateLabel.uppercase()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "• Creator View Reward Rate: ₹${MonthlyPayoutPolicy.REWARD_RATE_INR_PER_MILLION_VIEWS} per 1,000,000 Eligible Views (Bot/Fake & Duplicate Views Excluded)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = eligibilityReport.reasonMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("monthly_payout_reason_text")
                                )
                            }
                        }

                        // Verified Bank Account Card (Screen 13 & 14 of Reference Design)
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = ElectricCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Verified Bank Account for Payouts",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                    }
                                    Surface(
                                        color = if (privateInfo?.bankVerified == true) EmeraldLive.copy(alpha = 0.2f) else RoyalGold.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (privateInfo?.bankVerified == true) "VERIFIED ✓" else "REQUIRED",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (privateInfo?.bankVerified == true) EmeraldLive else RoyalGold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = bankHolderInput,
                                    onValueChange = { bankHolderInput = it },
                                    label = { Text("Account Holder Name") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bank_holder_input")
                                )
                                OutlinedTextField(
                                    value = bankAccInput,
                                    onValueChange = { bankAccInput = it },
                                    label = { Text("Account Number") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bank_account_input")
                                )
                                OutlinedTextField(
                                    value = bankConfirmAccInput,
                                    onValueChange = { bankConfirmAccInput = it },
                                    label = { Text("Confirm Account Number") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bank_confirm_account_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = bankIfscInput,
                                        onValueChange = { bankIfscInput = it.uppercase() },
                                        label = { Text("IFSC Code") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("bank_ifsc_input")
                                    )
                                    OutlinedTextField(
                                        value = bankNameInput,
                                        onValueChange = { bankNameInput = it },
                                        label = { Text("Bank Name") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("bank_name_input")
                                    )
                                }

                                OutlinedTextField(
                                    value = bankUpiIdInput,
                                    onValueChange = { bankUpiIdInput = it },
                                    label = { Text("UPI ID (Optional, e.g. name@okaxis)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bank_upi_id_input")
                                )

                                Button(
                                    onClick = {
                                        tapGuard.runOnce("save_bank_account_button") {
                                            onSaveBankAccount?.invoke(
                                                bankHolderInput,
                                                bankAccInput,
                                                bankConfirmAccInput,
                                                bankIfscInput,
                                                bankNameInput
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberViolet),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("save_bank_account_button")
                                ) {
                                    Text("Save & Verify Bank Account", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Withdrawal Request Form + Payment Method Selector (Image 13)
                        Text(
                            text = "Withdrawal Method & Amount",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Bank Transfer", "UPI", "PayPal", "e-Wallet").forEach { method ->
                                FilterChip(
                                    selected = selectedWithdrawalMethod == method,
                                    onClick = { selectedWithdrawalMethod = method },
                                    label = { Text(method) },
                                    modifier = Modifier.testTag("withdrawal_method_${method.lowercase().replace(" ", "_")}")
                                )
                            }
                        }

                        val reqDiamonds = withdrawalDiamondsInput.toLongOrNull() ?: 0L
                        val previewInr = StreamVibeCatalog.diamondsToInr(reqDiamonds)

                        OutlinedTextField(
                            value = withdrawalDiamondsInput,
                            onValueChange = { withdrawalDiamondsInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Withdraw Diamonds (Min 10,000 💎 = ₹4,000)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("withdraw_diamonds_input")
                        )

                        Text(
                            text = "Payout Conversion Preview ($selectedWithdrawalMethod): $reqDiamonds Eligible Diamonds = ₹$previewInr (Processing: 24–72 Hours after Payout Verification)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )

                        Button(
                            onClick = {
                                tapGuard.runOnce("submit_withdrawal_button") {
                                    onRequestWithdrawal?.invoke(reqDiamonds)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldLive,
                                contentColor = DarkStudioBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_withdrawal_button")
                        ) {
                            Text("Withdraw $reqDiamonds 💎 (₹$previewInr)", fontWeight = FontWeight.Bold)
                        }

                        // Creator Earnings 4-Category Breakdown & 7-Day Overview (Image 12)
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("creator_earnings_breakdown_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Creator Earnings Breakdown (4 Revenue Streams)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = DarkStudioBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("🎥 Live Streaming", style = MaterialTheme.typography.labelLarge, color = NeonMagenta)
                                            Text("54% Share", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("PK & Live Gifts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Surface(
                                        color = DarkStudioBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("🎬 Reels & Shorts", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                                            Text("25% Share", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("₹800 / 1M Views", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = DarkStudioBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("💎 Direct Gifts", style = MaterialTheme.typography.labelLarge, color = RoyalGold)
                                            Text("16% Share", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("Profile & Posts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Surface(
                                        color = DarkStudioBg,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("🤝 Referral Bonus", style = MaterialTheme.typography.labelLarge, color = EmeraldLive)
                                            Text("4% Share", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("Creator Invites", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // Withdrawal History & Payout Verification Status
                        val withdrawals = (withdrawalsState as? UiState.Success)?.data ?: emptyList()
                        Text(
                            text = "Creator Withdrawal Requests (${withdrawals.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        if (withdrawals.isEmpty()) {
                            Text(
                                text = "No withdrawal requests yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            withdrawals.forEach { wd ->
                                val isPaid = wd.status == "VERIFIED_PAID"
                                Surface(
                                    color = DarkStudioBg,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "💎 ${wd.diamondsAmount} → ₹${wd.payoutInr}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                color = if (isPaid) EmeraldLive.copy(alpha = 0.2f) else RoyalGold.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (isPaid) "PAID (VERIFIED)" else "PENDING VERIFICATION",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isPaid) EmeraldLive else RoyalGold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Bank: ${wd.bankName} (${wd.bankAccountMasked}) • IFSC: ${wd.bankIfsc}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isPaid && onVerifyWithdrawalPayout != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    tapGuard.runOnce("verify_payout_${wd.id}") {
                                                        onVerifyWithdrawalPayout(wd)
                                                    }
                                                },
                                                modifier = Modifier.testTag("verify_payout_button_${wd.id}")
                                            ) {
                                                Text("Admin: Verify Payout & Mark Paid", color = EmeraldLive)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "VIP_LEVEL" -> {
                // VIP LEVELS (1 - 1000) CARD (Screen 15 of Reference Design)
                val vipLevel = userProfile?.vipLevel ?: 1L
                val vipXp = userProfile?.vipXp ?: 250L
                val progressInLevel = ((vipXp % 500L).toFloat() / 500f).coerceIn(0.05f, 1f)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "StreamVibe VIP Levels (1 – 1000)",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Current Rank: VIP $vipLevel / 1000 • Total VIP XP: $vipXp",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoyalGold
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progressInLevel },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = RoyalGold,
                            trackColor = DarkStudioBg
                        )

                        Text(
                            text = "Next Level Benefits (VIP ${(vipLevel + 1L).coerceAtMost(1000L)}):",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                        Text("• 💎 Bonus VIP Status Badge in Live Streams, PK Battles & Reels", color = Color.White)
                        Text("• 👑 Priority Highlight in Live Chat & Supporter Leaderboard", color = Color.White)
                        Text("• 🚀 Unlock Exclusive Supernova & Hyper Rocket VIP Effects", color = Color.White)
                        Text(
                            text = "• 🏦 Level 30 Creator Payout Eligibility: Level $vipLevel / ${MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL} (${if (vipLevel >= MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL) "Eligible for 18th IST Monthly Payout ✓" else "${MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL - vipLevel} more levels required for Monthly Payout"})",
                            color = if (vipLevel >= MonthlyPayoutPolicy.REQUIRED_CREATOR_LEVEL) EmeraldLive else RoyalGold,
                            modifier = Modifier.testTag("vip_level30_eligibility_text")
                        )

                        Button(
                            onClick = {
                                tapGuard.runOnce("upgrade_vip_level_button") {
                                    onUpgradeVipLevel?.invoke()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalGold,
                                contentColor = DarkStudioBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upgrade_vip_level_button")
                        ) {
                            Text("Upgrade VIP Level (500 💎 from User Wallet)", fontWeight = FontWeight.Bold)
                        }

                        // VIP Tiers Showcase (Image 15)
                        Text(
                            text = "VIP Tier Milestones & Exclusive Perks",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        listOf(
                            Triple("VIP 1–9 • Bronze Star", "Entry Badge + Custom Chat Color", ElectricCyan),
                            Triple("VIP 10–29 • Silver & Gold Elite", "Animated Room Entrance + Priority PK Matching", RoyalGold),
                            Triple("VIP 30+ • Verified Creator Payout Tier", "Unlocks Automatic Monthly Payout on 18th IST (Min ₹10,000)", EmeraldLive),
                            Triple("VIP 100–1000 • Royal Legend", "Global Broadcast Banner + 24/7 Dedicated Concierge", NeonMagenta)
                        ).forEach { (tierTitle, tierPerks, accentColor) ->
                            Surface(
                                color = DarkStudioBg,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(tierTitle, style = MaterialTheme.typography.labelLarge, color = accentColor, fontWeight = FontWeight.Bold)
                                    Text(tierPerks, style = MaterialTheme.typography.bodySmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Transaction History with Summary Bar & Filter Chips (Screen 16 of Reference Design)
        Text(
            text = "3-Wallet Transaction & Gift History",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        val allTxs = (transactionsState as? UiState.Success)?.data ?: emptyList()
        val totalEarnedDiamonds = allTxs.filter { it.coinsDelta > 0 }.sumOf { it.coinsDelta }
        val totalSpentDiamonds = allTxs.filter { it.coinsDelta < 0 && it.txType != "CREATOR_WITHDRAWAL" }.sumOf { -it.coinsDelta }
        val totalWithdrawnDiamonds = allTxs.filter { it.txType == "CREATOR_WITHDRAWAL" }.sumOf { -it.coinsDelta }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_history_summary_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Total Added", style = MaterialTheme.typography.labelSmall, color = EmeraldLive)
                    Text("+${totalEarnedDiamonds} 💎", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Total Gifted", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
                    Text("-${totalSpentDiamonds} 💎", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Withdrawn", style = MaterialTheme.typography.labelSmall, color = RoyalGold)
                    Text("${totalWithdrawnDiamonds} 💎", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "ALL" to "All",
                "PURCHASE" to "Purchase",
                "GIFT" to "Gift",
                "WITHDRAWAL" to "Withdrawal"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = txFilter == key,
                    onClick = { txFilter = key },
                    label = { Text(label) },
                    modifier = Modifier.testTag("tx_filter_$key")
                )
            }
        }

        val txs = allTxs.filter { tx ->
            when (txFilter) {
                "PURCHASE" -> tx.txType == "RAZORPAY_TOPUP" || tx.txType == "ADMIN_PROMO_ALLOCATION" || tx.txType == "WELCOME_BONUS"
                "GIFT" -> tx.txType == "GIFT_SENT" || tx.txType == "ADMIN_PROMO_GIFT" || tx.txType == "GIFT_RECEIVED"
                "WITHDRAWAL" -> tx.txType == "CREATOR_WITHDRAWAL"
                else -> true
            }
        }

        if (txs.isEmpty()) {
            Text(
                text = "No transactions matching filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            txs.take(15).forEach { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.giftLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${tx.txType} • Wallet: ${tx.walletType} • To: ${tx.recipientName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tx.razorpayPaymentId.isNotBlank()) {
                                Text(
                                    text = "Tx ID: ${tx.razorpayPaymentId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricCyan
                                )
                            }
                        }
                        Text(
                            text = if (tx.coinsDelta >= 0) "+${tx.coinsDelta} 💎" else "${tx.coinsDelta} 💎",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (tx.coinsDelta >= 0) EmeraldLive else NeonMagenta,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

