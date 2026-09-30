package dev.zlddba.moshiapp.ingest.models

object ModelCatalog {

    const val GEMMA = "gemma4_e2b"
    const val QWEN = "qwen3_06b"
    const val GECKO = "gecko_110m"
    const val SENSE_VOICE = "sense_voice"
    const val STREAM_ASR = "stream_asr"

    val LLM_IDS = listOf(GEMMA, QWEN)

    data class ModelFile(
        val name: String,
        val bytes: Long,
        val sha256: String?
    )

    data class Descriptor(
        val id: String,
        val files: List<ModelFile>,
        val urlPrefixes: List<String>
    )

    private val descriptors = listOf(
        Descriptor(
            id = GEMMA,
            files = listOf(
                ModelFile(
                    name = "gemma-4-E2B-it.litertlm",
                    bytes = 2_588_147_712L,
                    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
                )
            ),
            urlPrefixes = listOf(
                "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/master",
                "https://hf-mirror.com/litert-community/gemma-4-E2B-it-litert-lm/resolve/main"
            )
        ),
        Descriptor(
            id = QWEN,
            files = listOf(
                ModelFile(
                    name = "qwen3_0_6b_mixed_int4.litertlm",
                    bytes = 497_516_544L,
                    sha256 = "7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1"
                )
            ),
            urlPrefixes = listOf(
                "https://modelscope.cn/models/litert-community/Qwen3-0.6B/resolve/master",
                "https://hf-mirror.com/litert-community/Qwen3-0.6B/resolve/main"
            )
        ),
        Descriptor(
            id = GECKO,
            files = listOf(
                ModelFile(
                    name = "Gecko_256_quant.tflite",
                    bytes = 114_141_184L,
                    sha256 = "81505c2a296878467fba5bb066fe3646f75e04d19220fbf219c0c968f5ea2ce4"
                ),
                ModelFile(
                    name = "sentencepiece.model",
                    bytes = 794_346L,
                    sha256 = "839ffa4b9afae8d77834a88b87781849aa021975d6063dec6085633fcaf7171c"
                )
            ),
            urlPrefixes = listOf(
                "https://modelscope.cn/models/litert-community/Gecko-110m-en/resolve/master",
                "https://hf-mirror.com/litert-community/Gecko-110m-en/resolve/main"
            )
        ),
        Descriptor(
            id = SENSE_VOICE,
            files = listOf(
                ModelFile(
                    name = "model.int8.onnx",
                    bytes = 239_233_841L,
                    sha256 = null
                ),
                ModelFile(
                    name = "tokens.txt",
                    bytes = 315_894L,
                    sha256 = null
                )
            ),
            urlPrefixes = listOf(
                "https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main",
                "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"
            )
        ),
        Descriptor(
            id = STREAM_ASR,
            files = listOf(
                ModelFile(
                    name = "encoder-epoch-99-avg-1.int8.onnx",
                    bytes = 21_621_684L,
                    sha256 = "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1"
                ),
                ModelFile(
                    name = "decoder-epoch-99-avg-1.int8.onnx",
                    bytes = 1_888_682L,
                    sha256 = "22f123bb8cba9b38974b3df18a3f45e7081f4985ebb2e075d9f21f618c468bbf"
                ),
                ModelFile(
                    name = "joiner-epoch-99-avg-1.int8.onnx",
                    bytes = 1_795_562L,
                    sha256 = "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169"
                ),
                ModelFile(
                    name = "tokens.txt",
                    bytes = 48_697L,
                    sha256 = null
                )
            ),
            urlPrefixes = listOf(
                "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main",
                "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main"
            )
        )
    )

    fun descriptor(id: String): Descriptor = descriptors.first { it.id == id }

    fun totalBytes(id: String): Long = descriptor(id).files.sumOf { it.bytes }

    fun isSmallModel(id: String): Boolean = id == QWEN
}
