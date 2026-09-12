export class MarkdownInsertion {
  readonly content: string;
  readonly caretOffset: number;

  constructor(content: string, caretOffset: number) {
    this.content = content;
    this.caretOffset = caretOffset;
  }
}

export class MarkdownDocument {
  static withTitle(title: string, content: string): string {
    const normalizedTitle = title.trim().length > 0 ? title.trim() : '未命名笔记';
    const lines = content.split('\n');
    if (lines.length > 0 && lines[0].startsWith('# ')) {
      lines[0] = `# ${normalizedTitle}`;
      return lines.join('\n');
    }
    const separator = content.length > 0 ? '\n\n' : '\n';
    return `# ${normalizedTitle}${separator}${content}`;
  }

  static title(content: string): string {
    const firstLine = content.split('\n')[0];
    if (firstLine.startsWith('# ')) {
      const title = firstLine.slice(2).trim();
      return title.length > 0 ? title : '未命名笔记';
    }
    return '未命名笔记';
  }

  static insertBlock(
    content: string,
    block: string,
    selectionStart: number,
    selectionEnd: number
  ): MarkdownInsertion {
    const start = Math.max(0, Math.min(selectionStart, content.length));
    const end = Math.max(start, Math.min(selectionEnd, content.length));
    const before = content.substring(0, start);
    const after = content.substring(end);
    const prefix = MarkdownDocument.blockPrefix(before);
    const suffix = MarkdownDocument.blockSuffix(after);
    const inserted = `${prefix}${block}${suffix}`;
    return new MarkdownInsertion(
      `${before}${inserted}${after}`,
      before.length + inserted.length
    );
  }

  private static blockPrefix(before: string): string {
    if (before.length === 0 || before.endsWith('\n\n')) {
      return '';
    }
    return before.endsWith('\n') ? '\n' : '\n\n';
  }

  private static blockSuffix(after: string): string {
    if (after.length === 0) {
      return '\n';
    }
    if (after.startsWith('\n\n')) {
      return '';
    }
    return after.startsWith('\n') ? '\n' : '\n\n';
  }
}
