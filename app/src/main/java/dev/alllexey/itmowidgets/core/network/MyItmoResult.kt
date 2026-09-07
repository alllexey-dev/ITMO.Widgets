package dev.alllexey.itmowidgets.core.network

import api.myitmo.model.ResultResponse
import api.myitmo.utils.ApiException

/** MyITMO can return an API error inside HTTP 200; don't turn authorization failures into empty data. */
internal fun <T> ResultResponse<T>?.requireResult(): T {
    checkNotNull(this) { "MyITMO returned an empty response" }
    if (errorCode != 0) throw ApiException(errorCode, "MyITMO request failed")
    return checkNotNull(result) { "MyITMO returned no result" }
}
