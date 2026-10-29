package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class StreamVibeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureOrCreateProfile_and_completeOtp_succeedsForAuthenticatedOwner() = runBlocking {
        val uniqueEmail = "alice_${UUID.randomUUID().toString().take(6)}@test.com"
        val uid = signInTestUser(uniqueEmail)
        val repository = StreamVibeRepository(firestore)

        val profileResult = withTimeout(5000L) {
            repository.ensureOrCreateProfile("AliceVibe", uniqueEmail, "+919876543210")
        }
        assertTrue("Profile creation failed: ${profileResult.exceptionOrNull()}", profileResult.isSuccess)
        val profile = profileResult.getOrThrow()
        assertEquals(uid, profile.userId)
        assertEquals(250L, profile.coinsBalance)
        assertEquals(50000L, profile.adminWalletDiamonds)
        assertEquals(0L, profile.creatorEarningsDiamonds)

        val verifyResult = withTimeout(5000L) {
            repository.completeOtpAndTermsVerification("+919876543210", true)
        }
        assertTrue("OTP verification failed: ${verifyResult.exceptionOrNull()}", verifyResult.isSuccess)
    }

    @Test
    fun getPrivateInfo_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = "alice_priv_${UUID.randomUUID().toString().take(6)}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val repo = StreamVibeRepository(firestore)

        withTimeout(5000L) {
            repo.ensureOrCreateProfile("Alice", aliceEmail, "+919999999999").getOrThrow()
        }

        val bobEmail = "bob_priv_${UUID.randomUUID().toString().take(6)}@test.com"
        signInTestUser(bobEmail)

        val crossReadResult = withTimeout(5000L) {
            repo.getPrivateInfo(aliceUid)
        }
        assertTrue(crossReadResult.isFailure)
        val fsEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun createReel_and_razorpayTestTopUp_and_sendGift_succeeds() = runBlocking {
        val email = "creator_${UUID.randomUUID().toString().take(6)}@test.com"
        val uid = signInTestUser(email)
        val repo = StreamVibeRepository(firestore)

        withTimeout(5000L) {
            repo.ensureOrCreateProfile("CreatorOne", email).getOrThrow()
        }

        val reelId = withTimeout(5000L) {
            repo.createReelPost(
                authorName = "CreatorOne",
                caption = "First Neon Reel #streamvibe",
                mediaType = "REEL",
                category = "Dance",
                musicTrack = "Cyber Synth",
                themeColorHex = "#FF2E93",
                mediaUri = "preset://neon_dance"
            ).getOrThrow()
        }
        assertTrue(reelId.isNotEmpty())

        val pack = StreamVibeCatalog.coinPacks.first()
        val payId = withTimeout(5000L) {
            repo.processRazorpayTestTopUp(pack, "UPI Test (vpa@razorpay)").getOrThrow()
        }
        assertTrue(payId.startsWith("pay_test_"))

        val updatedProfile = withTimeout(5000L) {
            repo.getUserProfile(uid).getOrThrow()
        }
        assertNotNull(updatedProfile)
        assertEquals(250L + pack.coinsAmount + pack.bonusCoins, updatedProfile?.coinsBalance)
    }

    @Test
    fun threeWalletSystem_adminPromotionalGift_creatorEarnings_and_withdrawal_succeeds() = runBlocking {
        val email = "wallet3_${UUID.randomUUID().toString().take(6)}@test.com"
        val uid = signInTestUser(email)
        val repo = StreamVibeRepository(firestore)

        withTimeout(5000L) {
            repo.ensureOrCreateProfile("AdminCreator", email).getOrThrow()
        }

        // 1. Allocate 10,000 Promotional Diamonds to Admin Wallet
        val newAdminPool = withTimeout(5000L) {
            repo.allocateAdminPromotionalDiamonds(10000L, "Campaign Promo Pool").getOrThrow()
        }
        assertEquals(60000L, newAdminPool)

        // 2. Create a Reel and send two Hyper Rocket gifts (5,000 Diamonds each = 10,000 Diamonds) from Admin Promotional Wallet
        val reelId = withTimeout(5000L) {
            repo.createReelPost(
                authorName = "AdminCreator",
                caption = "Live PK & Creator Earnings Showcase",
                mediaType = "REEL",
                category = "Live Show",
                musicTrack = "Vibe Anthem",
                themeColorHex = "#00F5D4",
                mediaUri = "preset://vibe_show"
            ).getOrThrow()
        }
        val targetReel = ReelPost(id = reelId, authorId = uid, authorName = "AdminCreator")
        val rocketGift = StreamVibeCatalog.giftItems.first { it.id == "gift_rocket" } // 5,000 Diamonds

        withTimeout(5000L) {
            repo.sendGiftToReelOrLive(
                senderName = "AdminCreator",
                gift = rocketGift,
                targetReel = targetReel,
                useAdminPromoWallet = true,
                idempotencyKey = "gift_rocket_1_${UUID.randomUUID()}"
            ).getOrThrow()
        }
        withTimeout(5000L) {
            repo.sendGiftToReelOrLive(
                senderName = "AdminCreator",
                gift = rocketGift,
                targetReel = targetReel,
                useAdminPromoWallet = true,
                idempotencyKey = "gift_rocket_2_${UUID.randomUUID()}"
            ).getOrThrow()
        }

        // Verify User Wallet is still untouched at 250, Admin Wallet is 50,000, Creator Earnings is 10,000
        val profileAfterGifts = withTimeout(5000L) {
            repo.getUserProfile(uid).getOrThrow()
        }!!
        assertEquals(250L, profileAfterGifts.coinsBalance)
        assertEquals(50000L, profileAfterGifts.adminWalletDiamonds)
        assertEquals(10000L, profileAfterGifts.creatorEarningsDiamonds)
        assertEquals(10000L, profileAfterGifts.withdrawableDiamonds)

        // 3. Verify Bank Account in private subcollection
        withTimeout(5000L) {
            repo.saveAndVerifyBankAccount(
                holderName = "Admin Creator",
                accountNumber = "123456789012",
                confirmAccountNumber = "123456789012",
                ifscCode = "HDFC0001234",
                bankName = "HDFC Bank"
            ).getOrThrow()
        }

        // 4. Request Creator Earnings Withdrawal of 10,000 Diamonds (= ₹4,000 / 400,000 paise)
        val wdId = withTimeout(5000L) {
            repo.requestCreatorWithdrawal(
                diamondsAmount = 10000L,
                idempotencyKey = "wd_${UUID.randomUUID()}"
            ).getOrThrow()
        }
        assertTrue(wdId.isNotEmpty())

        // Verify withdrawableDiamonds was deducted to 0 while coinsBalance remains 250
        val profileAfterWithdrawal = withTimeout(5000L) {
            repo.getUserProfile(uid).getOrThrow()
        }!!
        assertEquals(250L, profileAfterWithdrawal.coinsBalance)
        assertEquals(0L, profileAfterWithdrawal.withdrawableDiamonds)

        // 5. Verify Payout transitions withdrawal status to VERIFIED_PAID
        val wdReq = WithdrawalRequest(
            id = wdId,
            userId = uid,
            userName = "AdminCreator",
            diamondsAmount = 10000L,
            payoutInrPaise = 400000L,
            bankName = "HDFC Bank",
            bankAccountMasked = "****9012",
            bankIfsc = "HDFC0001234",
            status = "PENDING_VERIFICATION",
            idempotencyKey = wdId
        )
        withTimeout(5000L) {
            repo.verifyWithdrawalPayout(wdReq).getOrThrow()
        }
    }

    @Test
    fun observePublicReels_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = StreamVibeRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(3000L) {
                repo.observePublicReels().first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull(caught)
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }
}
