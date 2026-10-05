package dev.zlddba.moshiapp.domain.error

const val STATUS_TIMEOUT = -1

enum class ErrorCode(val code: String) {
    FILE_ENCRYPTED("E_FILE_ENCRYPTED"),
    FILE_CORRUPT("E_FILE_CORRUPT"),
    FILE_FORMAT("E_FILE_FORMAT"),
    FILE_TOO_LARGE("E_FILE_TOO_LARGE"),
    FILE_IO("E_FILE_IO"),
    OCR_FAIL("E_OCR_FAIL"),
    RETRIEVE_EMPTY("E_RETRIEVE_EMPTY"),
    CLOUD_AUTH("E_CLOUD_AUTH"),
    CLOUD_MODEL("E_CLOUD_MODEL"),
    CLOUD_TIMEOUT("E_CLOUD_TIMEOUT"),
    CLOUD_RATE_LIMIT("E_CLOUD_RATE_LIMIT"),
    CLOUD_NETWORK("E_CLOUD_NETWORK"),
    OOM("E_OOM"),
    UNKNOWN("E_UNKNOWN");

    companion object {
        fun forCloudStatus(statusCode: Int): ErrorCode = when {
            statusCode == STATUS_TIMEOUT -> CLOUD_TIMEOUT
            statusCode == 401 || statusCode == 403 -> CLOUD_AUTH
            statusCode == 404 -> CLOUD_MODEL
            statusCode == 429 -> CLOUD_RATE_LIMIT
            statusCode <= 0 -> CLOUD_NETWORK
            statusCode >= 500 -> CLOUD_NETWORK
            else -> UNKNOWN
        }
    }
}
