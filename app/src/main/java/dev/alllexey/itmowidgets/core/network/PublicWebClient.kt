package dev.alllexey.itmowidgets.core.network

import javax.inject.Qualifier

/** The `OkHttpClient` for public pages: no cookies and no credentials of any account. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicWebClient
