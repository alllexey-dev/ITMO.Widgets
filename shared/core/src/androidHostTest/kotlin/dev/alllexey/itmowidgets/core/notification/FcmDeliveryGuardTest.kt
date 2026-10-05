package dev.alllexey.itmowidgets.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmDeliveryGuardTest {
    @Test fun `only the signed-in recipient with opted-in services may process a push`() {
        assertTrue(FcmDeliveryGuard.canDeliver(recipientIsu = 1, currentIsu = 1, signedIn = true, enabled = true))
        assertFalse(FcmDeliveryGuard.canDeliver(recipientIsu = 1, currentIsu = 2, signedIn = true, enabled = true))
        assertFalse(FcmDeliveryGuard.canDeliver(recipientIsu = 1, currentIsu = null, signedIn = true, enabled = true))
        assertFalse(FcmDeliveryGuard.canDeliver(recipientIsu = 0, currentIsu = 0, signedIn = true, enabled = true))
        assertFalse(FcmDeliveryGuard.canDeliver(recipientIsu = 1, currentIsu = 1, signedIn = false, enabled = true))
        assertFalse(FcmDeliveryGuard.canDeliver(recipientIsu = 1, currentIsu = 1, signedIn = true, enabled = false))
    }
}
