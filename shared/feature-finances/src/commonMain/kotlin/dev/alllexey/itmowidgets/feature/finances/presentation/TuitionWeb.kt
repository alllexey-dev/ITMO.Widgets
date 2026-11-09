package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoutes

/**
 * The tuition page of my.itmo (`/finances/education` of the site's router, where it lists the agreements and starts
 * a payment). `Оплатить` opens it in the system browser: the in-app view does not follow the redirect to the bank, and
 * its navigation policy stays as it is.
 */
object TuitionWeb {
    const val PAY_URL = "${AppRoutes.MyItmoWeb.ORIGIN}/finances/education"
}
