# 0007 Push delivery is guarded by the recipient ISU, not a token fingerprint

**Decision (2026-09-16).** Backend adds `recipient_isu` to every FCM message.
The Android worker processes a queued message only when services are enabled,
the user is signed in and that ISU equals the current user's ISU.

**Why.** A first implementation also compared a SHA-256 of the ID token taken at
receipt with the current one. MyITMO rotates the ID token on every refresh, so a
push waiting for network would be dropped as soon as the app refreshed tokens.
The ISU check already covers account changes.

**Consequence.** Sign-out cancels queued work and clears notifications; a token
refresh inside the same account never loses a push.
