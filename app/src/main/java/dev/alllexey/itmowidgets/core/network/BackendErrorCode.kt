package dev.alllexey.itmowidgets.core.network

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.model.ApiResponse
import retrofit2.HttpException
import java.util.WeakHashMap

private val codes = WeakHashMap<HttpException, String?>()
private val errorGson = Gson()

/** ResponseBody is single-use; cache the parsed code without retaining errors or exposing server messages. */
internal fun HttpException.backendErrorCode(gson: Gson = errorGson): String? = synchronized(codes) {
    if (codes.containsKey(this)) return@synchronized codes[this]
    val code = runCatching { response()?.errorBody()?.string()?.let { gson.fromJson(it, ApiResponse::class.java)?.error?.code } }.getOrNull()
    codes[this] = code
    code
}
