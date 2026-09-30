package dev.zlddba.moshiapp.domain.summary

import kotlin.math.ln

object NoteSummarizer {

    private const val MAX_SENTENCES = 3
    private const val MAX_CHARS = 160
    private const val MIN_SENTENCE_CHARS = 8
    private const val CANDIDATE_LIMIT = 120

    /**
     * 抽取式摘要：按词频给句子打分后取前几句，不改写、不生成新内容，
     * 因此不会引入检索片段之外的表述。
     */
    fun summarize(content: String, limitChars: Int = MAX_CHARS): String {
        val text = content.trim()
        if (text.isEmpty()) return ""
        val sentences = splitSentences(text).take(CANDIDATE_LIMIT)
        if (sentences.isEmpty()) return text.take(limitChars)
        if (sentences.size <= MAX_SENTENCES) return joinWithin(sentences, limitChars)

        val frequencies = wordFrequencies(text)
        val scored = sentences.mapIndexed { index, sentence ->
            Triple(index, sentence, score(sentence, frequencies))
        }
        val picked = scored
            .sortedWith(compareByDescending<Triple<Int, String, Double>> { it.third }.thenBy { it.first })
            .take(MAX_SENTENCES)
            .sortedBy { it.first }
            .map { it.second }
        return joinWithin(picked, limitChars)
    }

    private fun score(sentence: String, frequencies: Map<String, Int>): Double {
        val tokens = tokenize(sentence)
        if (tokens.isEmpty()) return 0.0
        var sum = 0.0
        for (token in tokens) {
            val count = frequencies[token] ?: 0
            if (count > 0) sum += 1.0 + ln(count.toDouble())
        }
        val density = sum / tokens.size
        val lengthBonus = if (sentence.length in 20..80) 1.15 else 1.0
        return density * lengthBonus
    }

    private fun wordFrequencies(text: String): Map<String, Int> {
        val frequencies = HashMap<String, Int>()
        for (token in tokenize(text)) {
            frequencies[token] = (frequencies[token] ?: 0) + 1
        }
        return frequencies
    }

    private fun tokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        val buffer = StringBuilder()
        for (ch in text) {
            when {
                ch.isLetterOrDigit() -> buffer.append(ch.lowercaseChar())
                ch.code in CJK_RANGE -> {
                    if (buffer.isNotEmpty()) {
                        tokens.add(buffer.toString())
                        buffer.setLength(0)
                    }
                    tokens.add(ch.toString())
                }

                else -> {
                    if (buffer.isNotEmpty()) {
                        tokens.add(buffer.toString())
                        buffer.setLength(0)
                    }
                }
            }
        }
        if (buffer.isNotEmpty()) tokens.add(buffer.toString())
        return tokens
    }

    private fun splitSentences(text: String): List<String> {
        val sentences = ArrayList<String>()
        val buffer = StringBuilder()
        for (ch in text) {
            if (ch == '\n') {
                flush(sentences, buffer)
                continue
            }
            buffer.append(ch)
            if (ch in SENTENCE_ENDS) flush(sentences, buffer)
        }
        flush(sentences, buffer)
        return sentences
    }

    private fun flush(sentences: MutableList<String>, buffer: StringBuilder) {
        val value = buffer.toString().trim().trimStart('#', '-', '*', '>', ' ')
        buffer.setLength(0)
        if (value.length >= MIN_SENTENCE_CHARS) sentences.add(value)
    }

    private fun joinWithin(sentences: List<String>, limitChars: Int): String {
        val builder = StringBuilder()
        for (sentence in sentences) {
            if (builder.isEmpty()) {
                builder.append(sentence)
                continue
            }
            if (builder.length + sentence.length > limitChars) break
            builder.append(' ').append(sentence)
        }
        return builder.toString().take(limitChars)
    }

    private val SENTENCE_ENDS = charArrayOf('。', '！', '？', '；', '.', '!', '?', ';')
    private val CJK_RANGE = 0x4E00..0x9FFF
}
