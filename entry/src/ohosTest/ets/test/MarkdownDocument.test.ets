import { describe, expect, it } from '@ohos/hypium';
import { MarkdownDocument } from '../../../main/ets/model/MarkdownDocument';

export default function markdownDocumentTest(): void {
  describe('MarkdownDocument', () => {
    it('adds a heading to a new note', 0, () => {
      expect(MarkdownDocument.withTitle('旅途', '第一天')).assertEqual('# 旅途\n\n第一天');
    });

    it('replaces the existing heading', 0, () => {
      expect(MarkdownDocument.withTitle('新标题', '# 旧标题\n正文')).assertEqual('# 新标题\n正文');
    });

    it('reads the first heading as title', 0, () => {
      expect(MarkdownDocument.title('# 项目\n内容')).assertEqual('项目');
    });

    it('uses a fallback title', 0, () => {
      expect(MarkdownDocument.title('没有标题')).assertEqual('未命名笔记');
    });
  });
}

