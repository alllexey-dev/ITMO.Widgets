package dev.alllexey.itmowidgets.core.testing

import api.myitmo.MyItmo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Synthetic responses through the real MyItmoApi Retrofit contract, with no network or credentials. */
fun myItmoStub(body: (Request) -> String): MyItmo = MyItmo().apply {
    okHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK")
            .body(body(chain.request()).toResponseBody("application/json".toMediaType())).build()
    }.build()
}
