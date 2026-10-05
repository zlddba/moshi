package dev.zlddba.moshiapp.ingest.parse

import dev.zlddba.moshiapp.domain.error.ErrorCode

class IngestException(val kind: Kind) : Exception(kind.errorCode.code) {

    enum class Kind(val errorCode: ErrorCode) {
        UNSUPPORTED(ErrorCode.FILE_FORMAT),
        ENCRYPTED(ErrorCode.FILE_ENCRYPTED),
        TOO_LARGE(ErrorCode.FILE_TOO_LARGE),
        EMPTY(ErrorCode.FILE_CORRUPT),
        PARSE_FAILED(ErrorCode.FILE_CORRUPT),
        IO(ErrorCode.FILE_IO)
    }
}
