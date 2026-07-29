package com.markbook.android

import android.text.TextUtils
import java.util.regex.Matcher
import java.util.regex.Pattern

object MarkdownCodec {
    fun toHtml(markdown: String, repository: VaultRepository): String {
        val body = StringBuilder()
        val lines = markdown.replace("\r\n", "\n").split('\n')
        var inList = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    body.append("<ul>")
                    inList = true
                }
                body.append("<li>").append(inline(trimmed.substring(2), repository)).append("</li>")
                continue
            }
            if (inList) {
                body.append("</ul>")
                inList = false
            }
            when {
                trimmed.isEmpty() -> body.append("<p><br></p>")
                trimmed.startsWith("### ") -> body.append("<h3>")
                    .append(inline(trimmed.substring(4), repository)).append("</h3>")
                trimmed.startsWith("## ") -> body.append("<h2>")
                    .append(inline(trimmed.substring(3), repository)).append("</h2>")
                trimmed.startsWith("# ") -> body.append("<h1>")
                    .append(inline(trimmed.substring(2), repository)).append("</h1>")
                else -> body.append("<p>").append(inline(trimmed, repository)).append("</p>")
            }
        }
        if (inList) body.append("</ul>")
        return """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font-family:sans-serif;font-size:18px;line-height:1.55;margin:12px;color:#202124}
            img{max-width:100%;height:auto} h1,h2,h3{line-height:1.25}</style></head>
            <body contenteditable="true" spellcheck="true">$body</body>
            <script>
            (function() {
              function esc(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
              function text(node) { return esc(node.textContent || '').replace(/\u00a0/g,' '); }
              function md(node) {
                if (node.nodeType === Node.TEXT_NODE) return text(node);
                if (node.nodeType !== Node.ELEMENT_NODE) return '';
                var tag=node.tagName.toLowerCase(), out='';
                if (tag==='img') return '!['+(node.alt||'image')+']('+(node.getAttribute('data-markdown')||'')+')\n';
                for (var i=0;i<node.childNodes.length;i++) out += md(node.childNodes[i]);
                if (tag==='h1') return '# '+out.trim()+'\n\n';
                if (tag==='h2') return '## '+out.trim()+'\n\n';
                if (tag==='h3') return '### '+out.trim()+'\n\n';
                if (tag==='li') return '- '+out.trim()+'\n';
                if (tag==='ul' || tag==='ol') return out+'\n';
                if (tag==='strong' || tag==='b') return '**'+out+'**';
                if (tag==='em' || tag==='i') return '*'+out+'*';
                if (tag==='a') return '['+out+']('+(node.href||'')+')';
                if (tag==='br') return '\n';
                if (tag==='p' || tag==='div') return out.trim()+'\n\n';
                return out;
              }
              window.markbook={serialize:function(){return md(document.body).replace(/\n{3,}/g,'\n\n').trim()+'\n'} };
              document.body.addEventListener('input',function(){ if(window.Android) Android.onChanged(); });
            })();
            </script></html>
        """.trimIndent()
    }

    private fun inline(value: String, repository: VaultRepository): String {
        var text = TextUtils.htmlEncode(value)
        val imagePattern = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)")
        val matcher = imagePattern.matcher(text)
        val result = StringBuffer()
        while (matcher.find()) {
            val alt = matcher.group(1) ?: "image"
            val path = matcher.group(2) ?: ""
            val tag = "<img alt=\"${TextUtils.htmlEncode(alt)}\" data-markdown=\"${TextUtils.htmlEncode(path)}\" src=\"${repository.htmlAttachmentUrl(path)}\">"
            matcher.appendReplacement(result, Matcher.quoteReplacement(tag))
        }
        matcher.appendTail(result)
        text = result.toString()
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        text = text.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        return text
    }
}
