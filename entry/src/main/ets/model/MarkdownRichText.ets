export class MarkdownRichText {
  static load(controller: RichEditorController, markdown: string, vaultRoot: string): void {
    controller.deleteSpans();
    let body = markdown;
    const firstLineEnd = body.indexOf('\n');
    const firstLine = firstLineEnd >= 0 ? body.substring(0, firstLineEnd) : body;
    if (firstLine.startsWith('# ')) {
      body = firstLineEnd >= 0 ? body.substring(firstLineEnd + 1) : '';
    }

    const imagePattern = /!\[[^\]]*\]\(([^)]+)\)/g;
    let cursor = 0;
    let match: RegExpExecArray | null = imagePattern.exec(body);
    while (match !== null) {
      MarkdownRichText.addText(controller, body.substring(cursor, match.index));
      const relativePath = match[1];
      const imagePath = MarkdownRichText.imagePath(vaultRoot, relativePath);
      if (imagePath.length > 0) {
        controller.addImageSpan(MarkdownRichText.fileUri(imagePath), {
          imageStyle: {
            size: ['100%', 220],
            objectFit: ImageFit.Contain
          }
        });
      } else {
        MarkdownRichText.addText(controller, match[0]);
      }
      cursor = match.index + match[0].length;
      match = imagePattern.exec(body);
    }
    MarkdownRichText.addText(controller, body.substring(cursor));
    controller.setCaretOffset(controller.getSpans().length > 0 ?
      Number.MAX_SAFE_INTEGER : 0);
  }

  static serialize(controller: RichEditorController, vaultRoot: string): string {
    const spans = controller.getSpans();
    spans.sort((left: RichEditorSpan, right: RichEditorSpan) => {
      return left.spanPosition.spanRange[0] - right.spanPosition.spanRange[0];
    });
    let result = '';
    spans.forEach((span: RichEditorSpan) => {
      const imageSpan = span as RichEditorImageSpanResult;
      if (imageSpan.valueResourceStr !== undefined) {
        const resource = imageSpan.valueResourceStr;
        if (typeof resource === 'string') {
          result += `![照片](${MarkdownRichText.relativeAttachmentPath(resource, vaultRoot)})`;
        }
        return;
      }
      result += (span as RichEditorTextSpanResult).value;
    });
    return result.trim();
  }

  private static addText(controller: RichEditorController, text: string): void {
    if (text.length === 0) {
      return;
    }
    const lines = text.split('\n');
    lines.forEach((line: string, index: number) => {
      const heading = /^(#{1,6})\s+/.exec(line);
      const visible = heading ? line.substring(heading[0].length) : line;
      const style: RichEditorTextStyle = heading ? {
        fontSize: Math.max(18, 28 - heading[1].length * 2),
        fontWeight: FontWeight.Bold
      } : {
        fontSize: 16
      };
      controller.addTextSpan(visible, { style });
      if (index < lines.length - 1) {
        controller.addTextSpan('\n', { style: { fontSize: 16 } });
      }
    });
  }

  private static imagePath(vaultRoot: string, relativePath: string): string {
    if (relativePath.startsWith('../attachments/')) {
      return `${vaultRoot}/attachments/${relativePath.substring('../attachments/'.length)}`;
    }
    if (relativePath.startsWith('attachments/')) {
      return `${vaultRoot}/${relativePath}`;
    }
    return '';
  }

  private static relativeAttachmentPath(resource: string, vaultRoot: string): string {
    const prefix = `${MarkdownRichText.fileUri(vaultRoot)}/attachments/`;
    if (resource.startsWith(prefix)) {
      return `../attachments/${resource.substring(prefix.length)}`;
    }
    const slash = resource.lastIndexOf('/');
    return slash >= 0 ? `../attachments/${resource.substring(slash + 1)}` : resource;
  }

  private static fileUri(path: string): string {
    return path.includes('://') ? path : `file://${path}`;
  }
}
