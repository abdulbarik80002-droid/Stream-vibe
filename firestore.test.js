const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated user cannot read public reels", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("reels").where("visibility", "==", "PUBLIC").get());
});

test("2. Cross-user PII and Bank Account isolation: Bob cannot read Alice's private info", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      phone: "+919876543210",
      kycStatus: "verified",
      bankHolderName: "Alice Sharma",
      bankAccountNumber: "123456789012",
      bankIfsc: "SBIN0001234",
      bankName: "State Bank of India",
      bankVerified: true,
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
});

test("3. Identity spoofing rejected on Reel create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("reels").doc("reel_1").set({
      authorId: BOB_UID, // Spoofed!
      authorName: "Bob",
      caption: "Hello #streamvibe",
      mediaType: "REEL",
      category: "Dance",
      musicTrack: "Neon Beats",
      themeColorHex: "#FF2E93",
      mediaUri: "preset://neon_dance",
      likesCount: 0,
      giftsCoinsTotal: 0,
      visibility: "PUBLIC",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("4. Shadow/Ghost field injection rejected on UserProfile create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "AliceVibe",
      bio: "Creator",
      avatarColor: "#FF2E93",
      coinsBalance: 500,
      giftsReceivedCount: 0,
      followersCount: 0,
      isVerifiedOtp: true,
      acceptedTerms: true,
      isSuperAdmin: true, // Ghost field!
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("5. Value poisoning (negative wallet balance or invalid VIP level) rejected on update", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "AliceVibe",
      bio: "Creator",
      avatarColor: "#FF2E93",
      coinsBalance: 500,
      adminWalletDiamonds: 0,
      creatorEarningsDiamonds: 12000,
      withdrawableDiamonds: 12000,
      vipLevel: 5,
      vipXp: 2500,
      giftsReceivedCount: 0,
      followersCount: 0,
      isVerifiedOtp: true,
      acceptedTerms: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coinsBalance: -100,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      vipLevel: 1005, // Exceeds 1-1000 VIP range!
      updatedAt: new Date(),
    })
  );
});

test("6. Query alignment: transactions list fails without userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("transactions").get());
  await assertSucceeds(aliceDb.collection("transactions").where("userId", "==", ALICE_UID).get());
});

test("7. Terminal state lock: cannot update ENDED LiveRoom", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("live_rooms").doc("room_ended").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      title: "Late Night Music",
      topic: "Music",
      status: "ENDED",
      viewerCount: 10,
      totalGiftsCoins: 100,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("live_rooms").doc("room_ended").update({
      viewerCount: 20,
      updatedAt: new Date(),
    })
  );
});

test("8. Email spoofing test: unverified admin email rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("support_tickets").doc("t_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      subject: "Coin topup help",
      category: "PAYMENT",
      description: "Need test receipt",
      priority: "HIGH",
      status: "OPEN",
      adminReply: "",
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const spoofedAdminDb = testEnv.authenticatedContext(BOB_UID, {
    email: "abdulbarik80000@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(spoofedAdminDb.collection("support_tickets").doc("t_1").get());

  const verifiedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "abdulbarik80000@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(verifiedAdminDb.collection("support_tickets").doc("t_1").get());
});

test("9. Creator Withdrawal enforces minimum 10,000 Diamonds = INR 4,000 and PENDING_VERIFICATION status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Below 10,000 Diamonds must fail
  await assertFails(
    aliceDb.collection("withdrawal_requests").doc("wd_small").set({
      userId: ALICE_UID,
      userName: "AliceVibe",
      diamondsAmount: 8500,
      payoutInrPaise: 340000,
      bankName: "HDFC Bank",
      bankAccountMasked: "****4321",
      bankIfsc: "HDFC0001234",
      status: "PENDING_VERIFICATION",
      idempotencyKey: "idem_wd_small",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Direct creation as VERIFIED_PAID (fake payout claim) must fail
  await assertFails(
    aliceDb.collection("withdrawal_requests").doc("wd_fake_paid").set({
      userId: ALICE_UID,
      userName: "AliceVibe",
      diamondsAmount: 10000,
      payoutInrPaise: 400000,
      bankName: "HDFC Bank",
      bankAccountMasked: "****4321",
      bankIfsc: "HDFC0001234",
      status: "VERIFIED_PAID",
      idempotencyKey: "idem_wd_fake",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Valid 10,000 Diamonds = 400,000 paise (INR 4,000) in PENDING_VERIFICATION succeeds
  await assertSucceeds(
    aliceDb.collection("withdrawal_requests").doc("wd_valid").set({
      userId: ALICE_UID,
      userName: "AliceVibe",
      diamondsAmount: 10000,
      payoutInrPaise: 400000,
      bankName: "HDFC Bank",
      bankAccountMasked: "****4321",
      bankIfsc: "HDFC0001234",
      status: "PENDING_VERIFICATION",
      idempotencyKey: "idem_wd_valid",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("10. Admin Audit Log is append-only and cannot be modified or deleted", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("admin_audit_logs").doc("audit_1").set({
      adminId: ADMIN_UID,
      adminEmail: "abdulbarik80000@gmail.com",
      actionType: "PROMO_ALLOCATION",
      diamondsAmount: 10000,
      targetUser: "AdminPromoPool",
      note: "Allocated 10,000 Promotional Diamonds for Live gifting",
      idempotencyKey: "idem_alloc_1",
      createdAt: new Date(),
    })
  );

  // Attempting to tamper with or delete audit log fails
  await assertFails(
    adminDb.collection("admin_audit_logs").doc("audit_1").update({
      diamondsAmount: 999999,
    })
  );
  await assertFails(adminDb.collection("admin_audit_logs").doc("audit_1").delete());
});
