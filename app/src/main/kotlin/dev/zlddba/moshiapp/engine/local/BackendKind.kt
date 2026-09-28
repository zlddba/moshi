package dev.zlddba.moshiapp.engine.local

enum class BackendKind {
    CPU,
    GPU,
    NPU;

    companion object {

        fun from(value: String): BackendKind =
            entries.firstOrNull { it.name == value } ?: CPU

        fun available(): List<BackendKind> {
            val kinds = mutableListOf(CPU, GPU)
            if (hasNpu()) kinds.add(NPU)
            return kinds
        }

        private fun hasNpu(): Boolean = NPU_LIB_DIRS.any { dirPath ->
            try {
                java.io.File(dirPath)
                    .listFiles()
                    ?.any { file -> matchesNpu(file.name) } == true
            } catch (e: SecurityException) {
                false
            }
        }

        private fun matchesNpu(fileName: String): Boolean {
            val name = fileName.lowercase()
            return name.contains("qnnhtp") ||
                name.contains("neuropilot") ||
                name.contains("libnpu") ||
                name.contains("edgetpu")
        }

        private val NPU_LIB_DIRS = listOf(
            "/vendor/lib64",
            "/system/vendor/lib64",
            "/odm/lib64"
        )
    }
}
