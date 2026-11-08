package dev.alllexey.itmowidgets.feature.finances.presentation

/**
 * The tuition page of my.itmo (`/finances/education` of the site's router, where it lists the agreements and starts
 * a payment). `Оплатить` opens it in the system browser: the in-app view does not follow the redirect to the bank, and
 * its navigation policy stays as it is.
 */
object TuitionWeb {
    const val PAY_URL = "https://my.itmo.ru/finances/education"
}
