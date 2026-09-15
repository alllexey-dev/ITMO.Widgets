# 0001 Single Gradle module

**Decision.** The Android application stays one Gradle module (`:app`).

**Why.** The only architectural benefit of multi-module here, compile-time layer
boundaries, is achieved by the Konsist suite at a fraction of the cost. Reuse
across applications is already served by the separate Core and MyItmoApi
repositories.

**Revisit when.** Incremental build times hurt, a second developer joins, or a
second in-repo reuse site appears.
