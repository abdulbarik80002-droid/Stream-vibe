package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.graphics.toColorInt
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.GiftCatalogItem
import com.example.data.ReelPost
import com.example.data.StreamVibeCatalog
import com.example.data.UserProfile
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.DarkStudioBg
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.RoyalGold

@Composable
fun ReelsAndShortsTab(
    userProfile: UserProfile?,
    reelsState: UiState<List<ReelPost>>,
    onVerifyOtpAndTerms: (String, Boolean) -> Unit,
    onLikeReel: (ReelPost) -> Unit,
    onSendGiftToReel: (GiftCatalogItem, ReelPost) -> Unit,
    onSendGiftToReelWithWallet: ((GiftCatalogItem, ReelPost, Boolean) -> Unit)? = null,
    onQuickSeedReel: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToLive: () -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToHub: () -> Unit = {},
    initialSubMode: String = "REELS_FEED"
) {
    var activeSubMode by remember(initialSubMode) { mutableStateOf(initialSubMode) } // HOME_DASHBOARD, REELS_FEED, INBOX_CHAT
    var selectedFilter by remember { mutableStateOf("ALL") }
    var activeGiftTargetReel by remember { mutableStateOf<ReelPost?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Clean Segmented Sub-Navigation Bar (Home Dashboard, Reels & Shorts, Inbox & Chats)
        Surface(
            color = DarkSurfaceCard.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = activeSubMode == "HOME_DASHBOARD",
                        onClick = { activeSubMode = "HOME_DASHBOARD" },
                        label = { Text("🏠 Home Dashboard", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("home_dashboard_mode_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = activeSubMode == "REELS_FEED",
                        onClick = { activeSubMode = "REELS_FEED" },
                        label = { Text("🎬 Reels & Shorts", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("reels_feed_mode_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = activeSubMode == "INBOX_CHAT",
                        onClick = { activeSubMode = "INBOX_CHAT" },
                        label = { Text("💬 Inbox & Chats (3)", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("inbox_chat_mode_chip")
                    )
                }
            }
        }

        when (activeSubMode) {
            "HOME_DASHBOARD" -> {
                HomeDashboardSection(
                    userProfile = userProfile,
                    onOpenReels = { activeSubMode = "REELS_FEED" },
                    onOpenChat = { activeSubMode = "INBOX_CHAT" },
                    onNavigateToUpload = onNavigateToUpload,
                    onNavigateToLive = onNavigateToLive,
                    onNavigateToWallet = onNavigateToWallet,
                    onNavigateToHub = onNavigateToHub
                )
            }
            "INBOX_CHAT" -> {
                InboxAndChatsSection(
                    userProfile = userProfile,
                    onExploreCreators = { activeSubMode = "HOME_DASHBOARD" }
                )
            }
            else -> {
                // Format Filter Bar (All, Reels, Short Videos, Photos + For You / Trending)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to "For You (All)",
                        "REEL" to "Reels (9:16)",
                        "SHORT_VIDEO" to "Trending Shorts",
                        "PHOTO" to "Photo Posts"
                    )
                    items(filters) { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("filter_chip_$key")
                        )
                    }
                }

                when (reelsState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading Reels & Short Videos...", color = ElectricCyan)
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(reelsState.message, color = Color(0xFFFF6B6B))
                        }
                    }
                    is UiState.Success -> {
                        val filtered = reelsState.data.filter {
                            selectedFilter == "ALL" || it.mediaType == selectedFilter
                        }

                        if (filtered.isEmpty()) {
                            EmptyReelsCard(
                                onQuickSeedReel = onQuickSeedReel,
                                onNavigateToUpload = onNavigateToUpload
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filtered, key = { it.id }) { reel ->
                                    ReelStageCard(
                                        reel = reel,
                                        onLikeClick = { onLikeReel(reel) },
                                        onOpenGiftPicker = { activeGiftTargetReel = reel }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(24.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeGiftTargetReel != null) {
        val target = activeGiftTargetReel!!
        GiftPickerBottomCard(
            targetTitle = "@${target.authorName}'s ${target.mediaType}",
            senderName = userProfile?.username ?: "VibeCreator",
            userCoins = userProfile?.coinsBalance ?: 0L,
            adminPromoDiamonds = userProfile?.adminWalletDiamonds ?: 0L,
            onDismiss = { activeGiftTargetReel = null },
            onSelectGift = { gift ->
                if (onSendGiftToReelWithWallet != null) {
                    onSendGiftToReelWithWallet(gift, target, false)
                } else {
                    onSendGiftToReel(gift, target)
                }
                activeGiftTargetReel = null
            },
            onSelectGiftWithWallet = { gift, useAdminPromo ->
                if (onSendGiftToReelWithWallet != null) {
                    onSendGiftToReelWithWallet(gift, target, useAdminPromo)
                } else {
                    onSendGiftToReel(gift, target)
                }
                activeGiftTargetReel = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeDashboardSection(
    userProfile: UserProfile?,
    onOpenReels: () -> Unit,
    onOpenChat: () -> Unit,
    onNavigateToUpload: () -> Unit = {},
    onNavigateToLive: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToHub: () -> Unit
) {
    val tapGuard = rememberSingleTapGuard()
    var followedCreators by remember { mutableStateOf(setOf("Riya Queen")) }
    val displayUsername = userProfile?.username?.ifBlank { "Riya Sharma" } ?: "Riya Sharma"
    val handleText = "@${displayUsername.lowercase().replace(" ", "_")}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Clean Studio Welcome & Live Alerts Strip (no duplicate logo clutter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_dashboard_brand_header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = EmeraldLive.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "● CREATOR DASHBOARD",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLive,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your Vibe • Our Stream",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Welcome, $displayUsername ✨",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Surface(
                color = DarkSurfaceCard,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, NeonMagenta.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .clickable { onOpenChat() }
            ) {
                Text(
                    text = "🔔 Live & Alerts",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonMagenta,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        // 2. Structured 2-Row Creator Identity & 3-Wallet Command Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_dashboard_profile_banner"),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                CyberViolet.copy(alpha = 0.35f),
                                DarkSurfaceCard
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Row: Avatar + Name/Handle + VIP & Verification Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(NeonMagenta, CyberViolet))
                                )
                                .border(2.dp, ElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayUsername.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayUsername,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CyberViolet,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "👑 VIP ${userProfile?.vipLevel ?: 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoyalGold,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$handleText • Verified Creator ✓",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = DarkStudioBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { onNavigateToHub() }
                    ) {
                        Text(
                            text = "Profile >",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Bottom Row: Equal 3-Column Wallet & Earnings Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = DarkStudioBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { onNavigateToWallet() }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "💎 ${userProfile?.coinsBalance ?: 12500L}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Diamonds",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = DarkStudioBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, RoyalGold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { onNavigateToWallet() }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎁 ${userProfile?.adminWalletDiamonds ?: 50000L}",
                                style = MaterialTheme.typography.labelLarge,
                                color = RoyalGold,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Promo Pool",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = DarkStudioBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, NeonMagenta.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable { onNavigateToWallet() }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "₹${userProfile?.withdrawableInr ?: 245L}",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonMagenta,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Wallet Balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 3. Symmetric 2x3 Quick Feature Dashboard Grid (6 Equal Cards)
        val shortcutRow1 = listOf(
            Triple("Live", "🔴", onNavigateToLive),
            Triple("Reels", "🎬", onOpenReels),
            Triple("Chat", "💬", onOpenChat)
        )
        val shortcutRow2 = listOf(
            Triple("Gifts", "🎁", onNavigateToWallet),
            Triple("PK Battle", "🏆", onNavigateToLive),
            Triple("Diamonds", "💎", onNavigateToWallet)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(shortcutRow1, shortcutRow2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { (label, emoji, action) ->
                        val itemTapGuard = rememberSingleTapGuard()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    itemTapGuard.runOnce("home_shortcut_$label") {
                                        action()
                                    }
                                }
                                .testTag("home_shortcut_${label.lowercase().replace(" ", "_")}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 6.dp)
                            ) {
                                Surface(
                                    color = DarkStudioBg,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .border(1.dp, CyberViolet.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Live PK Battle Hero Spotlight Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_pk_battle_hero_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonMagenta.copy(alpha = 0.45f), CyberViolet.copy(alpha = 0.55f), DarkSurfaceElevated)
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = NeonMagenta,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "⚔️ LIVE PK BATTLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "⏱ 03:45 Left • 23.3K Watching",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoyalGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Live PK Battle — Join & Win",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🔥 Riya Queen (12.5K) VS ⚡ Neha Star (10.8K)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoyalGold,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Visual VS Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxSize()
                            .background(NeonMagenta)
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxSize()
                            .background(ElectricCyan)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Send gifts from User or Promo Wallet",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Button(
                        onClick = {
                            tapGuard.runOnce("home_join_pk_button") {
                                onNavigateToLive()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("home_join_pk_button")
                    ) {
                        Text("Join Now >", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Creator Studio & Monthly Auto-Payout Overview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_creator_studio_overview_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Creator Studio & Monetization",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "18th IST Auto-Payout • ₹800 / 1M Views • Level 30+ Eligible",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldLive
                        )
                    }
                    Surface(
                        color = EmeraldLive.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "RazorpayX Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLive,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToUpload() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📤 Upload Reel", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onNavigateToHub() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🛡 Admin & Support", maxLines = 1)
                    }
                }
            }
        }

        // 6. Recommended Live Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recommended Live",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "See All >",
                style = MaterialTheme.typography.labelLarge,
                color = NeonMagenta,
                modifier = Modifier.clickable { onNavigateToLive() }
            )
        }

        val recommendedLives = listOf(
            Triple("Riya Queen", "Beauty & Fashion", "8.5K • 🔥 12.5K"),
            Triple("Gaming Star", "Game Live", "6.2K • 🔥 8.9K"),
            Triple("Anjali", "Dance Challenge", "4.8K • 🔥 6.7K")
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recommendedLives) { (host, topic, stats) ->
                Card(
                    modifier = Modifier
                        .width(195.dp)
                        .clickable { onNavigateToLive() },
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(CyberViolet.copy(alpha = 0.45f), DarkSurfaceCard)
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = NeonMagenta, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "● LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(text = "👁 $stats", style = MaterialTheme.typography.bodySmall, color = RoyalGold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NeonMagenta.copy(alpha = 0.3f))
                                    .border(1.5.dp, NeonMagenta, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = host.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = topic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricCyan,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Trending Creators Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trending Creators",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "See All >",
                style = MaterialTheme.typography.labelLarge,
                color = NeonMagenta,
                modifier = Modifier.clickable { onNavigateToHub() }
            )
        }

        val trendingCreators = listOf(
            "Riya Queen" to "12.5K",
            "Gaming Star" to "8.9K",
            "Anjali" to "6.7K",
            "Rocky" to "5.2K",
            "Cute Girl" to "4.5K"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trendingCreators) { (creator, score) ->
                val isFollowing = followedCreators.contains(creator)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(136.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CyberViolet.copy(alpha = 0.35f))
                                .border(1.5.dp, ElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = creator.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = creator,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "🔥 $score",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoyalGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                followedCreators = if (isFollowing) {
                                    followedCreators - creator
                                } else {
                                    followedCreators + creator
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) EmeraldLive else NeonMagenta,
                                contentColor = if (isFollowing) DarkStudioBg else Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("follow_creator_${creator.lowercase().replace(" ", "_")}")
                        ) {
                            Text(
                                text = if (isFollowing) "Following ✓" else "Follow",
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 8. Get More Diamonds CTA Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(CyberViolet.copy(alpha = 0.6f), DarkSurfaceElevated)
                        )
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💎 Get More Diamonds",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Top up via Razorpay Test Mode & send gifts!",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }
                Button(
                    onClick = { onNavigateToWallet() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("home_buy_diamonds_button")
                ) {
                    Text("Buy Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class DirectChatThread(
    val id: String,
    val name: String,
    val badge: String,
    val category: String,
    val lastMessage: String,
    val timeLabel: String,
    val unreadCount: Int
)

@Composable
private fun InboxAndChatsSection(
    userProfile: UserProfile?,
    onExploreCreators: () -> Unit
) {
    val tapGuard = rememberSingleTapGuard()
    var selectedChatTab by remember { mutableStateOf("CHATS") } // CHATS, FRIENDS, GROUPS, REQUESTS
    var searchQuery by remember { mutableStateOf("") }
    var activeThreadId by remember { mutableStateOf("chat_riya") }
    var messageDraft by remember { mutableStateOf("") }
    var customMessagesByThread by remember {
        mutableStateOf(
            mapOf(
                "chat_riya" to listOf("Riya Sharma: Hello! How are you? 😊", "You: Ready for tonight's PK Battle! 🔥"),
                "chat_gaming" to listOf("Gaming Star: Sure bro! Let's play together 🎮"),
                "chat_family" to listOf("Anjali: Good morning everyone! ☀️")
            )
        )
    }

    val threads = remember {
        listOf(
            DirectChatThread("chat_riya", "Riya Sharma", "👑 VIP", "CHATS", "Hello! How are you? 😊", "09:32 AM", 2),
            DirectChatThread("chat_gaming", "Gaming Star", "✓ Verified", "FRIENDS", "Sure bro! Let's play together 🎮", "08:45 AM", 1),
            DirectChatThread("chat_family", "StreamVibe Family", "👥 Group", "GROUPS", "Anjali: Good morning everyone! ☀️", "08:12 AM", 12),
            DirectChatThread("chat_pooja", "Pooja Verma", "✓ Verified", "CHATS", "You: Nice pic! 😍", "Yesterday", 0),
            DirectChatThread("chat_rahul", "Rahul Singh", "Creator", "REQUESTS", "Can you join my live stream tonight?", "Yesterday", 3),
            DirectChatThread("chat_neha", "Neha Star", "👑 Creator", "FRIENDS", "Thank you for your support! 💖", "Yesterday", 1),
            DirectChatThread("chat_squad", "Reels Squad", "👥 Group", "GROUPS", "Soniya: Check out my new reel! 🎬", "Mon", 7)
        )
    }

    val filteredThreads = threads.filter { thread ->
        val matchesTab = when (selectedChatTab) {
            "CHATS", "ALL" -> true
            "LIVE" -> thread.category == "GROUPS" || thread.badge.contains("VIP")
            "DM" -> thread.category == "CHATS" || thread.category == "FRIENDS"
            else -> thread.category == selectedChatTab
        }
        val matchesSearch = searchQuery.isBlank() ||
            thread.name.contains(searchQuery, ignoreCase = true) ||
            thread.lastMessage.contains(searchQuery, ignoreCase = true)
        matchesTab && matchesSearch
    }

    val activeThread = threads.firstOrNull { it.id == activeThreadId } ?: threads.first()
    val activeMessages = customMessagesByThread[activeThread.id] ?: listOf("${activeThread.name}: ${activeThread.lastMessage}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-Tabs: All, Live, DM + Chats, Friends, Groups, Requests (matching Screen 7 & Image 8)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                listOf(
                    "CHATS" to "All / Chats",
                    "LIVE" to "Live",
                    "DM" to "DM",
                    "FRIENDS" to "Friends",
                    "GROUPS" to "Groups",
                    "REQUESTS" to "Requests (5)"
                )
            ) { (key, label) ->
                FilterChip(
                    selected = selectedChatTab == key,
                    onClick = { selectedChatTab = key },
                    label = { Text(label) },
                    modifier = Modifier.testTag("inbox_tab_$key")
                )
            }
        }

        // Stay Connected Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(CyberViolet.copy(alpha = 0.5f), DarkSurfaceElevated)
                        )
                    )
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("💜 Stay Connected", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Chat, make friends and share your vibe!", style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
                }
                Button(
                    onClick = onExploreCreators,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("inbox_explore_button")
                ) {
                    Text("Explore >")
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search chats, users, groups...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("inbox_search_input")
        )

        // Active Direct Conversation Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_direct_chat_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Active Chat: ${activeThread.name} (${activeThread.badge})",
                    style = MaterialTheme.typography.titleMedium,
                    color = RoyalGold,
                    fontWeight = FontWeight.Bold
                )
                activeMessages.forEach { line ->
                    Surface(
                        color = DarkStudioBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = { messageDraft = it },
                        label = { Text("Message ${activeThread.name}...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("direct_chat_message_input")
                    )
                    Button(
                        onClick = {
                            tapGuard.runOnce("send_direct_chat_btn") {
                                val text = messageDraft.trim()
                                if (text.isNotEmpty()) {
                                    val sender = userProfile?.username ?: "You"
                                    val updated = activeMessages + "$sender: $text"
                                    customMessagesByThread = customMessagesByThread + (activeThread.id to updated)
                                    messageDraft = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                        modifier = Modifier.testTag("send_direct_chat_button")
                    ) {
                        Text("Send")
                    }
                }
            }
        }

        // Thread List (matching Image 8)
        filteredThreads.forEach { thread ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeThreadId = thread.id }
                    .testTag("inbox_thread_${thread.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (thread.id == activeThread.id) DarkSurfaceElevated else DarkSurfaceCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = thread.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CyberViolet.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = thread.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = thread.lastMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = thread.timeLabel, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
                        if (thread.unreadCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = NeonMagenta, shape = CircleShape) {
                                Text(
                                    text = "${thread.unreadCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReelsCard(
    onQuickSeedReel: () -> Unit,
    onNavigateToUpload: () -> Unit
) {
    val tapGuard = rememberSingleTapGuard()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = "Empty Reels",
                tint = NeonMagenta,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Start the StreamVibe Feed",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Upload a Reel, Short Video, or Photo using the Android Photo Picker, or publish a sample Creator Reel to test likes & Diamond gifts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        tapGuard.runOnce("seed_sample_reel_button") {
                            onQuickSeedReel()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    modifier = Modifier.testTag("seed_sample_reel_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish Demo Reel")
                }
                OutlinedButton(
                    onClick = {
                        tapGuard.runOnce("go_to_upload_button") {
                            onNavigateToUpload()
                        }
                    },
                    modifier = Modifier.testTag("go_to_upload_button")
                ) {
                    Text("Upload Studio")
                }
            }
        }
    }
}

@Composable
private fun ReelStageCard(
    reel: ReelPost,
    onLikeClick: () -> Unit,
    onOpenGiftPicker: () -> Unit
) {
    val tapGuard = rememberSingleTapGuard()
    var optimisticTargetLikes by remember(reel.id) { mutableStateOf<Long?>(null) }
    val displayedLikes = maxOf(reel.likesCount, optimisticTargetLikes ?: reel.likesCount)

    val stageColor = remember(reel.themeColorHex) {
        try {
            Color(reel.themeColorHex.toColorInt())
        } catch (e: Exception) {
            NeonMagenta
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reel_card_${reel.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(stageColor.copy(alpha = 0.45f), DarkSurfaceElevated, DarkStudioBg)
                    )
                )
                .padding(18.dp)
        ) {
            // Top Media Badge & Diamond Gifts Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = DarkStudioBg.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${reel.mediaType.replace("_", " ")} • ${reel.category}",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    color = RoyalGold.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "💎 ${reel.giftsCoinsTotal} Gift Diamonds",
                        style = MaterialTheme.typography.labelLarge,
                        color = RoyalGold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Center Play / Media Preview Indicator
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(stageColor.copy(alpha = 0.85f))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (reel.mediaType == "PHOTO") Icons.Default.PhotoLibrary else Icons.Default.PlayArrow,
                        contentDescription = "Media Preview",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (reel.mediaUri.startsWith("content://")) "Local Media Picked" else "HD Stream Stage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Bottom Creator Info + Right Action Rail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "@${reel.authorName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reel.caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio Track",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = reel.musicTrack,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = {
                            tapGuard.runOnce("like_reel_${reel.id}") {
                                optimisticTargetLikes = displayedLikes + 1L
                                onLikeClick()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DarkStudioBg.copy(alpha = 0.8f))
                            .testTag("like_reel_button_${reel.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Like Reel",
                            tint = NeonMagenta
                        )
                    }
                    Text(
                        text = "$displayedLikes",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.testTag("like_count_text_${reel.id}")
                    )

                    Button(
                        onClick = {
                            tapGuard.runOnce("open_gift_reel_${reel.id}") {
                                onOpenGiftPicker()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalGold,
                            contentColor = DarkStudioBg
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("send_gift_reel_button_${reel.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Send Gift",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gift", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftPickerBottomCard(
    targetTitle: String,
    userCoins: Long,
    onDismiss: () -> Unit,
    onSelectGift: (GiftCatalogItem) -> Unit,
    senderName: String = "Creator",
    adminPromoDiamonds: Long = 0L,
    onSelectGiftWithWallet: ((GiftCatalogItem, Boolean) -> Unit)? = null
) {
    val tapGuard = rememberSingleTapGuard()
    var useAdminPromoWallet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.5.dp, RoyalGold, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Send Virtual Diamond Gift",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Sender: @$senderName → Receiver: $targetTitle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = if (useAdminPromoWallet) {
                            "Active Source: Admin Promotional Wallet (💎 $adminPromoDiamonds Diamonds)"
                        } else {
                            "Active Source: User Diamond Wallet (💎 $userCoins Diamonds)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalGold
                    )
                }
                OutlinedButton(
                    onClick = {
                        tapGuard.runOnce("dismiss_gift_picker") {
                            onDismiss()
                        }
                    }
                ) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wallet Source Selector (User Wallet vs Admin Promotional Wallet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !useAdminPromoWallet,
                    onClick = { useAdminPromoWallet = false },
                    label = { Text("User Wallet (💎 $userCoins)") },
                    modifier = Modifier.testTag("gift_source_user_wallet_chip")
                )
                FilterChip(
                    selected = useAdminPromoWallet,
                    onClick = { useAdminPromoWallet = true },
                    label = { Text("Admin Promo (💎 $adminPromoDiamonds)") },
                    modifier = Modifier.testTag("gift_source_admin_wallet_chip")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StreamVibeCatalog.giftItems.forEach { gift ->
                    Surface(
                        color = DarkStudioBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable {
                                tapGuard.runOnce("gift_option_${gift.id}") {
                                    if (onSelectGiftWithWallet != null) {
                                        onSelectGiftWithWallet(gift, useAdminPromoWallet)
                                    } else {
                                        onSelectGift(gift)
                                    }
                                }
                            }
                            .border(1.dp, NeonMagenta.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .testTag("gift_option_${gift.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = gift.emoji, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = gift.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = "💎 ${gift.coinsCost}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoyalGold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadMediaStudioTab(
    onPublishMedia: (
        caption: String,
        mediaType: String,
        category: String,
        musicTrack: String,
        themeColorHex: String,
        mediaUri: String
    ) -> Unit
) {
    val tapGuard = rememberSingleTapGuard()
    var mediaType by remember { mutableStateOf("REEL") }
    var caption by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Trending") }
    var musicTrack by remember { mutableStateOf("Neon Dreams - Original Audio") }
    var themeColorHex by remember { mutableStateOf("#FF2E93") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        pickedUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Creator Upload Studio",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = "Upload Photos, Short Videos, or 9:16 Reels using the zero-permission Android Photo Picker.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Format Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("REEL" to "Reel (9:16)", "SHORT_VIDEO" to "Short Video", "PHOTO" to "Photo Post").forEach { (type, label) ->
                FilterChip(
                    selected = mediaType == type,
                    onClick = { mediaType = type },
                    label = { Text(label) },
                    modifier = Modifier.testTag("upload_type_$type")
                )
            }
        }

        // Android Photo Picker Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (pickedUri != null) "Selected Media: $pickedUri" else "Select Photo or Video from Device Gallery",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            tapGuard.runOnce("pick_photo_video_button") {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberViolet),
                        modifier = Modifier.testTag("pick_photo_video_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Photo / Video")
                    }
                }
            }
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Caption & Hashtags (e.g. #streamvibe #creator)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_caption_input")
        )

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category (Dance, Music, Comedy, Tech, Vlog)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_category_input")
        )

        OutlinedTextField(
            value = musicTrack,
            onValueChange = { musicTrack = it },
            label = { Text("Audio / Music Track") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_music_input")
        )

        // Stage Theme Color Selector
        Text("Stage Accent Theme:", style = MaterialTheme.typography.labelLarge, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("#FF2E93", "#00F0FF", "#9D4EDD", "#00F5D4", "#FFD166").forEach { hex ->
                val col = Color(hex.toColorInt())
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(
                            width = if (themeColorHex == hex) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { themeColorHex = hex }
                )
            }
        }

        Button(
            onClick = {
                tapGuard.runOnce("publish_upload_button") {
                    onPublishMedia(
                        caption.ifBlank { "New $mediaType on StreamVibe! #streamvibe" },
                        mediaType,
                        category,
                        musicTrack,
                        themeColorHex,
                        pickedUri?.toString() ?: "preset://studio_${mediaType.lowercase()}"
                    )
                    caption = ""
                    pickedUri = null
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("publish_upload_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.AddCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Publish $mediaType Now", fontWeight = FontWeight.Bold)
        }
    }
}
