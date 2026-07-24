import markdownDocumentTest from './MarkdownDocument.test';
import syncConflictResolverTest from './SyncConflictResolver.test';

export default function testsuite(): void {
  markdownDocumentTest();
  syncConflictResolverTest();
}
