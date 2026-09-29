package dev.alllexey.itmowidgets.core.testing

import api.myitmo.MyItmo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Synthetic responses through the real MyItmoApi Retrofit contract, with no network or credentials. */
fun myItmoStub(code: Int = 200, body: (Request) -> String): MyItmo = myItmoResponses { request -> code to body(request) }

/** Like [myItmoStub], with the HTTP status chosen per request. */
fun myItmoResponses(respond: (Request) -> Pair<Int, String>): MyItmo = MyItmo().apply {
    okHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val (code, body) = respond(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("Synthetic response")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
}
