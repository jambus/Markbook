import imageFormatDetectorTest from './ImageFormatDetector.test';
import markdownDocumentTest from './MarkdownDocument.test';
import oneDriveGraphTest from './OneDriveGraph.test';
import oneDriveOAuthTest from './OneDriveOAuth.test';
import oneDriveProviderTest from './OneDriveProvider.test';
import syncBaselineCodecTest from './SyncBaselineCodec.test';
import syncConflictResolverTest from './SyncConflictResolver.test';
import syncEngineTest from './SyncEngine.test';
import syncPlannerTest from './SyncPlanner.test';

export default function testsuite(): void {
  imageFormatDetectorTest();
  markdownDocumentTest();
  oneDriveGraphTest();
  oneDriveOAuthTest();
  oneDriveProviderTest();
  syncBaselineCodecTest();
  syncConflictResolverTest();
  syncEngineTest();
  syncPlannerTest();
}
