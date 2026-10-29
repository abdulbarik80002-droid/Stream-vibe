# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Identity Isolation**: No unauthenticated caller may read or write any collection (`request.auth != null`).
2. **PII Split Isolation**: User PII (`email`, `phone`, `kycStatus`) lives strictly in `/users/{userId}/private/{docId}` and is readable/writable ONLY by `request.auth.uid == userId` or `isAdmin()`.
3. **Immutable Ownership & Timestamps**: `userId`, `authorId`, `hostId`, `senderId`, and `createdAt` can never be mutated on `update`.
4. **Strict Key Allowlisting**: Every `create` and `update` must validate `hasAll` / `hasOnly` and `affectedKeys().hasOnly(...)` along with `isValid[Entity](incoming())`.
5. **Query Enforcer on Lists**: Every `allow list` rule evaluates `resource.data` (e.g., `resource.data.visibility == 'PUBLIC'`, `resource.data.userId == request.auth.uid`, etc.).
6. **Terminal State Locking**: Once a `LiveRoom` reaches `status == 'ENDED'`, non-admin users cannot update it. Once a `SupportTicket` reaches `status == 'RESOLVED'`, non-admin users cannot mutate it.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("reels").where("visibility", "==", "PUBLIC").get()` -> Reject (`PERMISSION_DENIED`).
2. **Cross-User PII Read**: `bobDb.collection("users/alice/private").doc("info").get()` -> Reject (`PERMISSION_DENIED`).
3. **Identity Spoofing on Create**: `aliceDb.collection("reels").doc("r1").set({ authorId: "bob", ... })` -> Reject.
4. **Shadow/Ghost Field Injection**: `aliceDb.collection("users").doc("alice").update({ isSuperAdmin: true })` -> Reject.
5. **Value Poisoning (Negative Coin Balance)**: `aliceDb.collection("users").doc("alice").update({ coinsBalance: -500 })` -> Reject.
6. **Oversized String DoS (Resource Poisoning)**: `aliceDb.collection("reels").doc("r1").set({ caption: "A".repeat(5000), ... })` -> Reject.
7. **Unfiltered List Query on Private Collection**: `aliceDb.collection("transactions").get()` without `.where("userId", "==", "alice")` -> Reject.
8. **Cross-User Transaction Read**: `bobDb.collection("transactions").doc("alice_tx").get()` -> Reject.
9. **Terminal State Bypass on LiveRoom**: Updating a `LiveRoom` whose existing `status` is `'ENDED'` -> Reject.
10. **Immutable `createdAt` Mutation**: Updating `createdAt` on a user profile or reel -> Reject.
11. **Unauthorized Ticket Status Escalation**: Non-admin user trying to set `adminReply` on someone else's `SupportTicket` -> Reject.
12. **Chat Message Deletion/Tampering**: Attempting to update or delete an existing `LiveChatMessage` -> Reject (append-only ledger).
