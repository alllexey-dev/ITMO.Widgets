package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.core.ResultResponse

/**
 * The result of a successful envelope, null where my.itmo answers `result: null` for "none" (no dormitory
 * application, no agreements); an envelope with an error code throws as `requireResult` does.
 */
fun <T : Any> ResultResponse<T>.optionalResult(): T? {
    if (errorCode != 0) throw MyItmoException.Api(200, errorCode, errorMessage)
    return result
}
