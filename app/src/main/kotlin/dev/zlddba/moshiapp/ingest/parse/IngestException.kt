package dev.zlddba.moshiapp.ingest.parse

class IngestException(val kind: Kind) : Exception() {

    enum class Kind {
        UNSUPPORTED,
        TOO_LARGE,
        EMPTY,
        PARSE_FAILED,
        IO
    }
}
