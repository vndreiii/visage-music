package com.music.innertube

import com.music.innertube.models.IpVersion
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Dns
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp, block)

@Suppress("UNCHECKED_CAST")
private fun HttpClientConfig<*>.asOkHttpConfig(): HttpClientConfig<OkHttpConfig> =
    this as HttpClientConfig<OkHttpConfig>

actual fun HttpClientConfig<*>.configureInnerTubeOkHttp(
    proxy: Proxy?,
    proxyAuth: String?,
    ipVersion: IpVersion,
) {
    asOkHttpConfig().engine {
        config {
            connectionPool(
                okhttp3.ConnectionPool(
                    10,
                    5,
                    TimeUnit.MINUTES,
                )
            )

            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)

            protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            retryOnConnectionFailure(true)

            cache(
                okhttp3.Cache(
                    directory = File(System.getProperty("java.io.tmpdir"), "http_cache"),
                    maxSize = 50L * 1024L * 1024L,
                )
            )

            dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    return when (ipVersion) {
                        IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> {
                            val ipv4 = addresses.filterIsInstance<Inet4Address>()
                            val ipv6 = addresses.filterIsInstance<Inet6Address>()
                            ipv4 + ipv6
                        }
                    }
                }
            })

            proxy?.let { proxyConfig ->
                proxy(proxyConfig)
            }

            proxyAuth?.let { auth ->
                proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                }
            }
        }
    }
}

actual fun HttpClientConfig<*>.configureNetworkOkHttp(
    cacheDir: File?,
    enableCache: Boolean,
    connectTimeoutSeconds: Long,
    readTimeoutSeconds: Long,
    writeTimeoutSeconds: Long,
    cacheSizeBytes: Long,
) {
    asOkHttpConfig().engine {
        config {
            connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            retryOnConnectionFailure(true)

            if (enableCache) {
                val cacheDirectory = cacheDir
                    ?: File(System.getProperty("java.io.tmpdir"), "vivimusic_http_cache")
                cache(okhttp3.Cache(cacheDirectory, cacheSizeBytes))
            }
        }
    }
}
