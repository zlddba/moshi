package dev.zlddba.moshiapp.domain.chunk

object Chunker {

    const val MAX_CHARS = 800

    data class TextChunk(
        val seq: Int,
        val text: String,
        val offset: Int
    )

    private data class Block(
        val offset: Int,
        val text: String
    )

    fun chunk(text: String, maxChars: Int = MAX_CHARS): List<TextChunk> {
        val blocks = splitToBlocks(text, maxChars)
        return mergeBlocks(blocks, maxChars).mapIndexed { index, block ->
            TextChunk(seq = index, text = block.text, offset = block.offset)
        }
    }

    private fun splitToBlocks(text: String, maxChars: Int): List<Block> {
        val blocks = mutableListOf<Block>()
        var blockStart = 0
        var lineStart = 0
        while (lineStart < text.length) {
            val newlineIndex = text.indexOf('\n', lineStart)
            val lineEnd = if (newlineIndex == -1) text.length else newlineIndex
            val nextStart = if (newlineIndex == -1) text.length else newlineIndex + 1
            val line = text.substring(lineStart, lineEnd)
            if (line.isBlank()) {
                if (lineStart > blockStart) {
                    appendBlock(blocks, text, blockStart, lineStart, maxChars)
                }
                blockStart = nextStart
            } else if (isHeading(line) && lineStart > blockStart) {
                appendBlock(blocks, text, blockStart, lineStart, maxChars)
                blockStart = lineStart
            }
            lineStart = nextStart
        }
        appendBlock(blocks, text, blockStart, text.length, maxChars)
        return blocks
    }

    private fun appendBlock(
        out: MutableList<Block>,
        text: String,
        from: Int,
        to: Int,
        maxChars: Int
    ) {
        if (to <= from) return
        val segment = text.substring(from, to)
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return
        val leading = segment.length - segment.trimStart().length
        val offset = from + leading
        if (trimmed.length <= maxChars) {
            out.add(Block(offset, trimmed))
        } else {
            splitOversized(out, Block(offset, trimmed), maxChars)
        }
    }

    private fun splitOversized(out: MutableList<Block>, block: Block, maxChars: Int) {
        val content = block.text
        var cursor = 0
        while (cursor < content.length) {
            val remaining = content.length - cursor
            if (remaining <= maxChars) {
                out.add(Block(block.offset + cursor, content.substring(cursor)))
                return
            }
            var cut = findSentenceCut(content, cursor, cursor + maxChars)
            if (cut <= cursor) cut = cursor + maxChars
            out.add(Block(block.offset + cursor, content.substring(cursor, cut)))
            cursor = cut
        }
    }

    private fun findSentenceCut(content: String, from: Int, windowEnd: Int): Int {
        var index = windowEnd - 1
        while (index > from) {
            val char = content[index]
            val sentenceEnd = char == '。' || char == '！' || char == '？' ||
                char == '!' || char == '?' || char == '；' || char == ';' || char == '\n'
            val latinEnd = char == '.' && (
                index + 1 >= content.length ||
                    !content[index + 1].isDigit()
                )
            if (sentenceEnd || latinEnd) return index + 1
            index--
        }
        return -1
    }

    private fun mergeBlocks(blocks: List<Block>, maxChars: Int): List<Block> {
        val merged = mutableListOf<Block>()
        var current: Block? = null
        for (block in blocks) {
            val running = current
            if (running == null) {
                current = block
                continue
            }
            if (running.text.length + 1 + block.text.length <= maxChars) {
                current = Block(running.offset, running.text + "\n" + block.text)
            } else {
                merged.add(running)
                current = block
            }
        }
        current?.let { merged.add(it) }
        return merged
    }

    private fun isHeading(line: String): Boolean {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("#")) return false
        val level = trimmed.takeWhile { it == '#' }.length
        if (level !in 1..6) return false
        return trimmed.length > level && trimmed[level].isWhitespace()
    }
}
