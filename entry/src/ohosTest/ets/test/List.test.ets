import imageFormatDetectorTest from './ImageFormatDetector.test';
import attachmentNamingTest from './AttachmentNaming.test';
import markdownDocumentTest from './MarkdownDocument.test';
import noteSearchIndexTest from './NoteSearchIndex.test';
import oneDriveGraphTest from './OneDriveGraph.test';
import oneDriveOAuthTest from './OneDriveOAuth.test';
import oneDriveProviderTest from './OneDriveProvider.test';
import s3ProviderTest from './S3Provider.test';
import syncBaselineCodecTest from './SyncBaselineCodec.test';
import syncConflictResolverTest from './SyncConflictResolver.test';
import syncEngineTest from './SyncEngine.test';
import syncPlannerTest from './SyncPlanner.test';

export default function testsuite(): void {
  attachmentNamingTest();
  imageFormatDetectorTest();
  markdownDocumentTest();
  noteSearchIndexTest();
  oneDriveGraphTest();
  oneDriveOAuthTest();
  oneDriveProviderTest();
  s3ProviderTest();
  syncBaselineCodecTest();
  syncConflictResolverTest();
  syncEngineTest();
  syncPlannerTest();
}
