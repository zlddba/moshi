package dev.zlddba.moshiapp.domain.qa

import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.engine.cloud.CloudConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudRoutingTest {

    private fun hit(sensitive: Boolean = false): RetrieveService.Hit =
        RetrieveService.Hit(
            chunkId = 1,
            noteId = "n1",
            noteTitle = "标题",
            text = "片段内容",
            pageNo = null,
            score = 0.5f,
            isSensitive = sensitive
        )

    private fun config(
        mode: Int = CloudConfig.MODE_HYBRID,
        forceLocal: Boolean = true,
        baseUrl: String = "https://api.example.com/v1",
        apiKey: String = "sk-test",
        modelName: String = "model-a"
    ): CloudConfig = CloudConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        forceLocal = forceLocal,
        mode = mode
    )

    @Test
    fun `local mode never uses cloud`() {
        assertFalse(QaOrchestrator.shouldUseCloud(config(mode = CloudConfig.MODE_LOCAL), listOf(hit())))
    }

    @Test
    fun `incomplete config never uses cloud`() {
        assertFalse(QaOrchestrator.shouldUseCloud(config(apiKey = ""), listOf(hit())))
        assertFalse(QaOrchestrator.shouldUseCloud(config(baseUrl = "ftp://x"), listOf(hit())))
        assertFalse(QaOrchestrator.shouldUseCloud(config(modelName = " "), listOf(hit())))
    }

    @Test
    fun `hybrid mode uses cloud with clean hits`() {
        assertTrue(QaOrchestrator.shouldUseCloud(config(), listOf(hit(), hit())))
    }

    @Test
    fun `sensitive hit forces local when forceLocal on`() {
        assertFalse(
            QaOrchestrator.shouldUseCloud(config(), listOf(hit(), hit(sensitive = true)))
        )
        assertFalse(
            QaOrchestrator.shouldUseCloud(
                config(mode = CloudConfig.MODE_CLOUD),
                listOf(hit(sensitive = true))
            )
        )
    }

    @Test
    fun `sensitive hit allowed when forceLocal off`() {
        assertTrue(
            QaOrchestrator.shouldUseCloud(
                config(forceLocal = false),
                listOf(hit(sensitive = true))
            )
        )
    }

    @Test
    fun `user prompt only carries question and fragments`() {
        val prompt = QaFormat.userPrompt(
            "什么是向量检索",
            listOf(hit())
        )
        assertTrue(prompt.startsWith("【知识片段】"))
        assertTrue(prompt.contains("什么是向量检索"))
        assertTrue(prompt.contains("标题"))
        assertTrue(prompt.contains("片段内容"))
        assertFalse(prompt.contains("[THINKING]"))
        assertFalse(prompt.contains("固定格式"))
    }

    @Test
    fun `cloud config helpers`() {
        assertTrue(config().usesCloud())
        assertTrue(config().isComplete())
        assertFalse(config(mode = CloudConfig.MODE_LOCAL).usesCloud())
        assertFalse(config(apiKey = "").isComplete())
    }
}
