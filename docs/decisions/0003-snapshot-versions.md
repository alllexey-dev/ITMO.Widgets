# 0003 Core and Backend stay 1.2.0-SNAPSHOT until Android 2.1

**Decision (2026-09-15).** Core and Backend keep version `1.2.0-SNAPSHOT`
through the whole v2.1 development. Core is published only to Maven Local.

**Why.** Bumping snapshot numbers for every coordinated change adds churn without
consumers; the three repositories are developed and deployed together.

**Consequence.** A matching version string does not prove an API is present.
`docs/product/releases.md` records the commit hashes each Android revision
needs, and the Android version catalog is not bumped for social or FCM work.
