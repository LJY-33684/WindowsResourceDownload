package link.mczihan.androidResourceDownload.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight Markdown renderer for Compose Desktop.
 * Supports headings (#/##/###), bullet lists (- / *), inline **bold** and *italic*.
 * Announcements and update notes are published as Markdown, so plain Text() would
 * show raw syntax; this renders them properly.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val annotated = remember(markdown, style) { parseMarkdown(markdown, style) }
    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = style,
    )
}

private fun parseMarkdown(markdown: String, base: TextStyle): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        lines.forEachIndexed { idx, raw ->
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = base.fontSize * 1.1f)) {
                        appendInline(trimmed.removePrefix("### "))
                    }
                }
                trimmed.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = base.fontSize * 1.2f)) {
                        appendInline(trimmed.removePrefix("## "))
                    }
                }
                trimmed.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = base.fontSize * 1.3f)) {
                        appendInline(trimmed.removePrefix("# "))
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("• ")
                    appendInline(trimmed.drop(2))
                }
                trimmed.isBlank() -> { /* keep blank line as paragraph spacing below */ }
                else -> appendInline(trimmed)
            }
            if (idx < lines.size - 1) append("\n")
        }
    }
}

/** Inline parsing for **bold** segments. */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var remaining = text
    while (true) {
        val s = remaining.indexOf("**")
        if (s == -1) break
        val e = remaining.indexOf("**", s + 2)
        if (e == -1) break
        appendItalic(remaining.substring(0, s))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendItalic(remaining.substring(s + 2, e))
        }
        remaining = remaining.substring(e + 2)
    }
    appendItalic(remaining)
}

/** Inline parsing for *italic* segments. */
private fun AnnotatedString.Builder.appendItalic(text: String) {
    var remaining = text
    while (true) {
        val s = remaining.indexOf("*")
        if (s == -1) break
        val e = remaining.indexOf("*", s + 1)
        if (e == -1) break
        append(remaining.substring(0, s))
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(remaining.substring(s + 1, e))
        }
        remaining = remaining.substring(e + 1)
    }
    append(remaining)
}
