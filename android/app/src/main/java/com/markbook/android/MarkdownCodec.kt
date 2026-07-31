package com.markbook.android

import android.text.TextUtils
import java.util.regex.Matcher
import java.util.regex.Pattern

object MarkdownCodec {
    const val CARET_MARKER = "<!--MARKBOOK_CARET-->"

    fun toHtml(markdown: String, repository: VaultRepository, nightMode: Boolean = false): String {
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
        val background = if (nightMode) "#18231e" else "#f7f7f2"
        val text = if (nightMode) "#eff6f1" else "#18201c"
        val heading = if (nightMode) "#ffffff" else "#18201c"
        val caret = if (nightMode) "#8dcfa8" else "#2f6b4f"
        return """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;background:$background} #editor{box-sizing:border-box;max-width:760px;min-height:100vh;margin:0 auto;padding:18px 20px 56px;font-family:sans-serif;font-size:18px;line-height:1.62;color:$text;caret-color:$caret;outline:none}
            img{max-width:100%;height:auto;border-radius:8px} h1,h2,h3{line-height:1.25;color:$heading} h1{font-size:1.7em;margin-top:.35em} h2{font-size:1.3em;margin-top:1.45em} p{margin:.6em 0} ul{padding-left:1.35em}</style></head>
            <body><div id="editor" contenteditable="true" spellcheck="true">$body</div></body>
            <script>
            (function() {
              function esc(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
              function text(node) { return esc(node.textContent || '').replace(/\u00a0/g,' '); }
              function md(node) {
                if (node.nodeType === Node.TEXT_NODE) return text(node);
                if (node.nodeType !== Node.ELEMENT_NODE) return '';
                var tag=node.tagName.toLowerCase(), out='';
                if (tag==='img') return '!['+(node.alt||'image')+']('+(node.getAttribute('data-markdown')||'')+')\n';
                if (tag==='span' && node.getAttribute('data-markbook-caret') === 'true') return '$CARET_MARKER';
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
              var editor=document.getElementById('editor');
              function serialize() { return md(editor).replace(/\n{3,}/g,'\n\n').trim()+'\n'; }
              window.markbook={
                serialize:serialize,
                serializeWithCaret:function(){
                  var selection=window.getSelection();
                  if(!selection || !selection.rangeCount || !editor.contains(selection.getRangeAt(0).commonAncestorContainer)) return serialize();
                  var range=selection.getRangeAt(0).cloneRange();
                  range.collapse(false);
                  var marker=document.createElement('span');
                  marker.setAttribute('data-markbook-caret','true');
                  range.insertNode(marker);
                  var value=serialize();
                  marker.remove();
                  return value;
                },
                focusAfterImage:function(path){
                  var images=editor.querySelectorAll('img[data-markdown]');
                  for(var i=0;i<images.length;i++){
                    if(images[i].getAttribute('data-markdown')!==path) continue;
                    var range=document.createRange(), selection=window.getSelection();
                    range.setStartAfter(images[i]); range.collapse(true);
                    selection.removeAllRanges(); selection.addRange(range); editor.focus(); return true;
                  }
                  return false;
                }
              };
              editor.addEventListener('input',function(){ if(window.Android) Android.onChanged(); });
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
