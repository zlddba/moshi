package dev.zlddba.moshiapp.ui.doc

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object HtmlRenderer {

    const val KIND_TEXT = "text"
    const val KIND_MARKDOWN = "markdown"
    const val KIND_WORD = "word"
    const val KIND_SHEET = "sheet"

    private const val CSS_LIGHT =
        "body{font-family:sans-serif;font-size:15px;line-height:1.6;padding:16px;margin:0;" +
            "color:#1C1B1F;background:transparent;word-wrap:break-word;}" +
            "h1,h2{border-bottom:1px solid #CAC4D0;padding-bottom:4px;}h1{font-size:1.5em;}" +
            "h2{font-size:1.3em;}h3{font-size:1.15em;}p{margin:8px 0;}" +
            "code{background:#F3EDF7;padding:1px 5px;border-radius:4px;font-size:13px;}" +
            "pre{background:#F3EDF7;padding:12px;border-radius:8px;overflow-x:auto;}" +
            "pre code{background:transparent;padding:0;}" +
            "blockquote{border-left:3px solid #79747E;margin:8px 0;padding-left:12px;color:#49454F;}" +
            "a{color:#6750A4;}" +
            "table{border-collapse:collapse;width:100%;margin:8px 0;}" +
            "th,td{border:1px solid #CAC4D0;padding:6px 8px;}" +
            "th{background:#F3EDF7;}hr{border:none;border-top:1px solid #CAC4D0;margin:12px 0;}" +
            "ul,ol{padding-left:22px;}img{max-width:100%;}" +
            ".sheet-name{font-weight:bold;margin:14px 0 6px;color:#49454F;}" +
            ".empty{color:#79747E;}" +
            "mark{background:#FFE08A;color:#1C1B1F;border-radius:3px;padding:0 2px;}"

    private const val CSS_DARK =
        "body{font-family:sans-serif;font-size:15px;line-height:1.6;padding:16px;margin:0;" +
            "color:#E6E0E9;background:transparent;word-wrap:break-word;}" +
            "h1,h2{border-bottom:1px solid #49454F;padding-bottom:4px;}h1{font-size:1.5em;}" +
            "h2{font-size:1.3em;}h3{font-size:1.15em;}p{margin:8px 0;}" +
            "code{background:#2B2930;padding:1px 5px;border-radius:4px;font-size:13px;}" +
            "pre{background:#2B2930;padding:12px;border-radius:8px;overflow-x:auto;}" +
            "pre code{background:transparent;padding:0;}" +
            "blockquote{border-left:3px solid #938F99;margin:8px 0;padding-left:12px;color:#CAC4D0;}" +
            "a{color:#D0BCFF;}" +
            "table{border-collapse:collapse;width:100%;margin:8px 0;}" +
            "th,td{border:1px solid #49454F;padding:6px 8px;}" +
            "th{background:#2B2930;}hr{border:none;border-top:1px solid #49454F;margin:12px 0;}" +
            "ul,ol{padding-left:22px;}img{max-width:100%;}" +
            ".sheet-name{font-weight:bold;margin:14px 0 6px;color:#CAC4D0;}" +
            ".empty{color:#938F99;}" +
            "mark{background:#7A5900;color:#FFF3C4;border-radius:3px;padding:0 2px;}"

    fun markdownToHtml(markdown: String): String {
        val extensions = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
        )
        val document = Parser.builder().extensions(extensions).build().parse(markdown)
        return HtmlRenderer.builder().extensions(extensions).build().render(document)
    }

    fun wrap(body: String, dark: Boolean): String {
        val css = if (dark) CSS_DARK else CSS_LIGHT
        return "<!DOCTYPE html><html><head>" +
            "<meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>$css</style></head><body>$body" +
            "<script>window.__measure=function(){return document.body.scrollHeight;};</script>" +
            "</body></html>"
    }

    fun page(markdown: String, dark: Boolean): String = wrap(markdownToHtml(markdown), dark)

    fun textPage(text: String, dark: Boolean, highlight: IntRange? = null): String {
        val body = if (highlight == null) {
            escape(text)
        } else {
            val start = highlight.first.coerceIn(0, text.length)
            val endExclusive = (highlight.last + 1).coerceIn(start, text.length)
            if (endExclusive <= start) {
                escape(text)
            } else {
                escape(text.substring(0, start)) +
                    "<mark>" + escape(text.substring(start, endExclusive)) + "</mark>" +
                    escape(text.substring(endExclusive))
            }
        }
        return wrap(
            "<pre style=\"white-space:pre-wrap;word-wrap:break-word;\">" + body + "</pre>",
            dark
        )
    }

    fun escape(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }
}
