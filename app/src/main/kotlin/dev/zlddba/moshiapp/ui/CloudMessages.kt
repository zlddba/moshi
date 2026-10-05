package dev.zlddba.moshiapp.ui

import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.domain.error.STATUS_TIMEOUT
import dev.zlddba.moshiapp.engine.cloud.CloudNetworkProbe

object CloudMessages {

    fun errorOf(statusCode: Int): Int = when (statusCode) {
        STATUS_TIMEOUT -> R.string.cloud_err_timeout
        CloudNetworkProbe.STATUS_DNS -> R.string.cloud_err_dns
        CloudNetworkProbe.STATUS_CONNECT -> R.string.cloud_err_connect
        CloudNetworkProbe.STATUS_TLS -> R.string.cloud_err_tls
        0 -> R.string.cloud_err_network
        else -> httpErrorOf(statusCode)
    }

    fun httpErrorOf(statusCode: Int): Int = when (statusCode) {
        400 -> R.string.cloud_http_400
        401 -> R.string.cloud_http_401
        403 -> R.string.cloud_http_403
        404 -> R.string.cloud_http_404
        429 -> R.string.cloud_http_429
        in 500..599 -> R.string.cloud_http_5xx
        else -> R.string.cloud_http_other
    }
}
