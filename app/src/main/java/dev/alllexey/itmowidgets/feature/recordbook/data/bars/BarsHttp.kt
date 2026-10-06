package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import javax.inject.Qualifier

/**
 * The Ktor engine of BARS and its ITMO.ID login: OkHttp with the BARS timeouts and no cookie jar, cache or redirects.
 * It is never the MyITMO engine, so no cookie or connection state is shared with MyITMO.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BarsHttp
