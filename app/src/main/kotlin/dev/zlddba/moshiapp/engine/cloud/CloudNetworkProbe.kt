package dev.zlddba.moshiapp.engine.cloud

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

object CloudNetworkProbe {

    private const val TAG = "MoshiCloud"
    private const val DNS_TIMEOUT_MS = 5_000L
    private const val TCP_TIMEOUT_MS = 6_000
    private const val TLS_TIMEOUT_MS = 6_000

    const val STATUS_DNS = -2
    const val STATUS_CONNECT = -3
    const val STATUS_TLS = -4

    data class Result(
        val ok: Boolean,
        val statusCode: Int = 0,
        val detail: String = ""
    )

    suspend fun probe(baseUrl: String): Result {
        val uri = try {
            URI(baseUrl)
        } catch (e: Throwable) {
            Log.e(TAG, "probe url invalid: $baseUrl", e)
            return Result(false, STATUS_CONNECT, "服务地址格式不正确")
        }
        val host = uri.host
        if (host.isNullOrBlank()) {
            return Result(false, STATUS_CONNECT, "服务地址缺少主机名")
        }
        val port = if (uri.port > 0) uri.port else if (uri.scheme == "http") 80 else 443

        val dnsStart = System.currentTimeMillis()
        val addresses = withTimeoutOrNull(DNS_TIMEOUT_MS.milliseconds) {
            try {
                withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
            } catch (e: Throwable) {
                Log.e(
                    TAG,
                    "probe dns host=$host failed ms=${System.currentTimeMillis() - dnsStart}",
                    e
                )
                null
            }
        }
        val dnsMs = System.currentTimeMillis() - dnsStart
        if (addresses == null) {
            Log.e(TAG, "probe dns host=$host timeout ms=$dnsMs")
            return Result(false, STATUS_DNS, "域名解析超时（$host）")
        }
        if (addresses.isEmpty()) {
            Log.e(TAG, "probe dns host=$host no address ms=$dnsMs")
            return Result(false, STATUS_DNS, "域名没有解析到地址（$host）")
        }
        Log.i(
            TAG,
            "probe dns host=$host ok ms=$dnsMs addrs=${addresses.map { it.hostAddress }}"
        )

        var failure: Result? = null
        for (address in addresses) {
            val tcpStart = System.currentTimeMillis()
            val socket = Socket()
            try {
                withContext(Dispatchers.IO) {
                    socket.connect(InetSocketAddress(address, port), TCP_TIMEOUT_MS)
                }
                Log.i(
                    TAG,
                    "probe tcp host=$host port=$port ok addr=${address.hostAddress} " +
                        "ms=${System.currentTimeMillis() - tcpStart}"
                )
            } catch (e: Throwable) {
                Log.w(
                    TAG,
                    "probe tcp host=$host port=$port failed addr=${address.hostAddress} " +
                        "ms=${System.currentTimeMillis() - tcpStart} reason=${e.message}"
                )
                try {
                    socket.close()
                } catch (ignored: Throwable) {
                }
                if (failure == null) {
                    failure = Result(false, STATUS_CONNECT, "无法连接 $host:$port")
                }
                continue
            }
            try {
                socket.close()
            } catch (ignored: Throwable) {
            }
            if (uri.scheme != "https") return Result(true)
            val tls = probeTls(host, address, port)
            if (tls.ok) return tls
            if (failure == null) failure = tls
        }
        return failure ?: Result(false, STATUS_CONNECT, "无法连接 $host:$port")
    }

    private suspend fun probeTls(host: String, address: InetAddress, port: Int): Result {
        val start = System.currentTimeMillis()
        return try {
            val protocol = withContext(Dispatchers.IO) {
                val plain = Socket()
                plain.connect(InetSocketAddress(address, port), TCP_TIMEOUT_MS)
                val factory =
                    javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                val ssl = factory.createSocket(plain, host, port, true)
                    as javax.net.ssl.SSLSocket
                ssl.soTimeout = TLS_TIMEOUT_MS
                val params = ssl.sslParameters
                params.endpointIdentificationAlgorithm = "HTTPS"
                ssl.sslParameters = params
                ssl.startHandshake()
                val negotiated: String = ssl.session.protocol
                try {
                    ssl.close()
                } catch (ignored: Throwable) {
                }
                negotiated
            }
            Log.i(
                TAG,
                "probe tls host=$host ok protocol=$protocol " +
                    "ms=${System.currentTimeMillis() - start}"
            )
            Result(true)
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "probe tls host=$host failed ms=${System.currentTimeMillis() - start} " +
                    "reason=${e.message}",
                e
            )
            Result(false, STATUS_TLS, "TLS 握手失败：${e.message ?: e::class.simpleName}")
        }
    }
}
