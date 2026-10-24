package com.singam.lionlibrary.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Identifies the app to providers that ask clients to describe themselves. */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}
