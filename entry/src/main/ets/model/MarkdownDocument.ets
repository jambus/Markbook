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
}
