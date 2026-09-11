package com.music.innertube

import com.music.innertube.models.IpVersion
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import java.io.File
import java.net.Proxy

expect fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

expect fun HttpClientConfig<*>.configureInnerTubeOkHttp(
    proxy: Proxy?,
    proxyAuth: String?,
    ipVersion: IpVersion,
)

expect fun HttpClientConfig<*>.configureNetworkOkHttp(
    cacheDir: File?,
    enableCache: Boolean,
    connectTimeoutSeconds: Long,
    readTimeoutSeconds: Long,
    writeTimeoutSeconds: Long,
    cacheSizeBytes: Long,
)
