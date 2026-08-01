package com.markbook.android

import java.util.regex.Pattern

/**
 * Renders Markdown into the editable HTML document used by the note editor, and defines the
 * contract the in-page JavaScript uses to serialize that document back to Markdown.
 *
 * Block structures the editor cannot edit without losing fidelity are rendered as read-only
 * verbatim blocks. Their original text is carried in `data-markbook-raw` and written back byte for
 * byte, so opening and saving a note never rewrites content the user did not touch.
 *
 * This object has no Android framework dependencies so it can be covered by unit tests.
 */
object MarkdownCodec {
    const val CARET_MARKER = "<!--MARKBOOK_CARET-->"

    private val IMAGE_PATTERN: Pattern = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)")
    private val BOLD_PATTERN = Regex("\\*\\*([^*]+)\\*\\*")
    private val ITALIC_PATTERN = Regex("\\*([^*]+)\\*")
    private val ORDERED_ITEM = Regex("^\\d+[.)]\\s")
    private val TASK_ITEM = Regex("^[-*+]\\s+\\[[ xX]]")
    private val THEMATIC_BREAK = Regex("^(-{3,}|\\*{3,}|_{3,}|={3,})$")
    private val TABLE_DELIMITER = Regex("^[\\s|:-]*-{2,}[\\s|:-]*$")

    fun toHtml(
        markdown: String,
        attachmentUrl: (String) -> String,
        nightMode: Boolean = false
    ): String = document(renderBody(markdown, attachmentUrl), nightMode)

    /** Renders the editable body only. Exposed so round-trip behaviour can be unit tested. */
    fun renderBody(markdown: String, attachmentUrl: (String) -> String): String {
        val source = sourceLines(markdown)
        val body = StringBuilder()
        var listOpen = false
        var index = 0

        fun closeList() {
            if (listOpen) {
                body.append("</ul>")
                listOpen = false
            }
        }

        val frontMatterEnd = frontMatterEnd(source)
        if (frontMatterEnd > 0) {
            body.append(verbatim(source.subList(0, frontMatterEnd).joinToString("\n")))
            index = frontMatterEnd
        }

        while (index < source.size) {
            val line = source[index]

            val fence = fenceMarker(line)
            if (fence != null) {
                closeList()
                var end = index + 1
                while (end < source.size && !source[end].trimStart().startsWith(fence)) end++
                val last = if (end < source.size) end else source.size - 1
                body.append(verbatim(source.subList(index, last + 1).joinToString("\n")))
                index = last + 1
                continue
            }

            if (isTableStart(source, index)) {
                closeList()
                var end = index
                while (end < source.size && source[end].isNotBlank() && source[end].contains('|')) end++
                body.append(verbatim(source.subList(index, end).joinToString("\n")))
                index = end
                continue
            }

            if (isVerbatimLine(line)) {
                closeList()
                var end = index
                while (end < source.size &&
                    isVerbatimLine(source[end]) &&
                    fenceMarker(source[end]) == null
                ) end++
                body.append(verbatim(source.subList(index, end).joinToString("\n")))
                index = end
                continue
            }

            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!listOpen) {
                    body.append("<ul>")
                    listOpen = true
                }
                body.append("<li>").append(inline(line.substring(2), attachmentUrl)).append("</li>")
                index++
                continue
            }
            closeList()

            when {
                line.isEmpty() -> body.append("<p><br></p>")
                line.startsWith("### ") -> body.append("<h3>")
                    .append(inline(line.substring(4), attachmentUrl)).append("</h3>")
                line.startsWith("## ") -> body.append("<h2>")
                    .append(inline(line.substring(3), attachmentUrl)).append("</h2>")
                line.startsWith("# ") -> body.append("<h1>")
                    .append(inline(line.substring(2), attachmentUrl)).append("</h1>")
                else -> body.append("<p>").append(inline(line, attachmentUrl)).append("</p>")
            }
            index++
        }
        closeList()
        return body.toString()
    }

    private fun sourceLines(markdown: String): List<String> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        // A document ending with a newline yields a trailing empty element that is not a blank line.
        return if (lines.size > 1 && lines.last().isEmpty()) lines.subList(0, lines.size - 1) else lines
    }

    private fun frontMatterEnd(source: List<String>): Int {
        if (source.firstOrNull() != "---") return 0
        for (index in 1 until source.size) {
            if (source[index] == "---" || source[index] == "...") return index + 1
        }
        return 0
    }

    private fun fenceMarker(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) return "```"
        if (trimmed.startsWith("~~~")) return "~~~"
        return null
    }

    private fun isTableStart(source: List<String>, index: Int): Boolean {
        val line = source[index]
        if (line.isBlank() || !line.contains('|')) return false
        val delimiter = source.getOrNull(index + 1) ?: return false
        return delimiter.contains('|') && TABLE_DELIMITER.matches(delimiter)
    }

    /**
     * Lines the editable representation cannot reproduce exactly. Anything indented, quoted,
     * ordered, tabular or raw HTML is kept verbatim instead of being flattened into a paragraph.
     */
    private fun isVerbatimLine(line: String): Boolean {
        if (line.isEmpty()) return false
        if (line[0] == ' ' || line[0] == '\t') return true
        if (line[0] == '>' || line[0] == '|' || line[0] == '<') return true
        if (line.startsWith("[^") || line.startsWith("+ ")) return true
        if (ORDERED_ITEM.containsMatchIn(line)) return true
        if (TASK_ITEM.containsMatchIn(line)) return true
        return THEMATIC_BREAK.matches(line)
    }

    private fun verbatim(raw: String): String = buildString {
        append("<pre class=\"markbook-raw\" contenteditable=\"false\" data-markbook-raw=\"")
        append(escapeAttribute(raw))
        append("\">")
        append(escapeHtml(raw))
        append("</pre>")
    }

    private fun document(body: String, nightMode: Boolean): String {
        val background = if (nightMode) "#18231e" else "#f7f7f2"
        val text = if (nightMode) "#eff6f1" else "#18201c"
        val heading = if (nightMode) "#ffffff" else "#18201c"
        val caret = if (nightMode) "#8dcfa8" else "#2f6b4f"
        val rawBackground = if (nightMode) "#22302a" else "#ecefe9"
        val rawBorder = if (nightMode) "#8dcfa8" else "#2f6b4f"
        val rawText = if (nightMode) "#c3d3c9" else "#46514b"
        return """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;background:$background} #editor{box-sizing:border-box;max-width:760px;min-height:100vh;margin:0 auto;padding:18px 20px 56px;font-family:sans-serif;font-size:1.125rem;line-height:1.62;color:$text;caret-color:$caret;outline:none}
            img{max-width:100%;height:auto;border-radius:8px} h1,h2,h3{line-height:1.25;color:$heading} h1{font-size:1.7em;margin-top:.35em} h2{font-size:1.3em;margin-top:1.45em} p{margin:.6em 0} ul{padding-left:1.35em}
            pre.markbook-raw{margin:.6em 0;padding:10px 12px;border-left:3px solid $rawBorder;border-radius:6px;background:$rawBackground;color:$rawText;font-family:monospace;font-size:.9em;line-height:1.5;white-space:pre-wrap;word-break:break-word;user-select:text}</style></head>
            <body><div id="editor" contenteditable="true" spellcheck="true">$body</div></body>
            <script>
            (function() {
              var RAW_OPEN = '\u0000raw', RAW_CLOSE = '\u0000';
              var rawBlocks = [];
              function text(node) { return (node.textContent || '').replace(/\u00a0/g,' '); }
              function md(node) {
                if (node.nodeType === Node.TEXT_NODE) return text(node);
                if (node.nodeType !== Node.ELEMENT_NODE) return '';
                var tag = node.tagName.toLowerCase(), out = '';
                if (tag === 'pre' && node.hasAttribute('data-markbook-raw')) {
                  rawBlocks.push(node.getAttribute('data-markbook-raw'));
                  return RAW_OPEN + (rawBlocks.length - 1) + RAW_CLOSE + '\n';
                }
                if (tag === 'img') return '!['+(node.getAttribute('alt')||'image')+']('+(node.getAttribute('data-markdown')||'')+')';
                if (tag === 'span' && node.getAttribute('data-markbook-caret') === 'true') return '$CARET_MARKER';
                for (var i = 0; i < node.childNodes.length; i++) out += md(node.childNodes[i]);
                if (tag === 'h1') return '# ' + out.trim() + '\n';
                if (tag === 'h2') return '## ' + out.trim() + '\n';
                if (tag === 'h3') return '### ' + out.trim() + '\n';
                if (tag === 'li') return '- ' + out.trim() + '\n';
                if (tag === 'ul' || tag === 'ol') return out;
                if (tag === 'strong' || tag === 'b') return '**' + out + '**';
                if (tag === 'em' || tag === 'i') return '*' + out + '*';
                if (tag === 'a') return '[' + out + '](' + (node.getAttribute('href') || '') + ')';
                if (tag === 'br') return '\n';
                if (tag === 'p' || tag === 'div') return out.trim() + '\n';
                return out;
              }
              var editor = document.getElementById('editor');
              function serialize() {
                rawBlocks = [];
                var value = md(editor).replace(/\n+${'$'}/, '') + '\n';
                return value.replace(/\u0000raw(\d+)\u0000/g, function(match, index) { return rawBlocks[+index]; });
              }
              window.markbook = {
                serialize: serialize,
                serializeWithCaret: function() {
                  var selection = window.getSelection();
                  if (!selection || !selection.rangeCount || !editor.contains(selection.getRangeAt(0).commonAncestorContainer)) return serialize();
                  var range = selection.getRangeAt(0).cloneRange();
                  range.collapse(false);
                  var marker = document.createElement('span');
                  marker.setAttribute('data-markbook-caret','true');
                  range.insertNode(marker);
                  var value = serialize();
                  marker.remove();
                  return value;
                },
                focusAfterImage: function(path) {
                  var images = editor.querySelectorAll('img[data-markdown]');
                  for (var i = 0; i < images.length; i++) {
                    if (images[i].getAttribute('data-markdown') !== path) continue;
                    var range = document.createRange(), selection = window.getSelection();
                    range.setStartAfter(images[i]); range.collapse(true);
                    selection.removeAllRanges(); selection.addRange(range); editor.focus(); return true;
                  }
                  return false;
                }
              };
              editor.addEventListener('paste', function(event) {
                event.preventDefault();
                var clipboard = event.clipboardData || window.clipboardData;
                var plain = clipboard ? clipboard.getData('text/plain') : '';
                if (plain) document.execCommand('insertText', false, plain);
              });
              editor.addEventListener('input', function() { if (window.Android) Android.onChanged(); });
            })();
            </script></html>
        """.trimIndent()
    }

    private fun inline(value: String, attachmentUrl: (String) -> String): String {
        val matcher = IMAGE_PATTERN.matcher(value)
        val out = StringBuilder()
        var last = 0
        while (matcher.find()) {
            out.append(emphasize(escapeHtml(value.substring(last, matcher.start()))))
            val alt = matcher.group(1).orEmpty()
            val path = matcher.group(2).orEmpty()
            out.append("<img alt=\"").append(escapeAttribute(alt))
                .append("\" data-markdown=\"").append(escapeAttribute(path))
                .append("\" src=\"").append(escapeAttribute(attachmentUrl(path))).append("\">")
            last = matcher.end()
        }
        out.append(emphasize(escapeHtml(value.substring(last))))
        return out.toString()
    }

    private fun emphasize(escaped: String): String = escaped
        .replace(BOLD_PATTERN, "<strong>$1</strong>")
        .replace(ITALIC_PATTERN, "<em>$1</em>")

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (character in value) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

    private fun escapeAttribute(value: String): String = escapeHtml(value)
        .replace("\n", "&#10;")
        .replace("\t", "&#9;")
}
