import { describe, expect, it } from '@ohos/hypium';
import { SyncAction, SyncConflictResolver } from '../../../main/ets/services/sync/SyncConflictResolver';

export default function syncConflictResolverTest(): void {
  describe('SyncConflictResolver', () => {
    it('uploads a local-only change', 0, () => {
      expect(SyncConflictResolver.decide(true, false)).assertEqual(SyncAction.UPLOAD);
    });

    it('downloads a remote-only change', 0, () => {
      expect(SyncConflictResolver.decide(false, true)).assertEqual(SyncAction.DOWNLOAD);
    });

    it('keeps both concurrent changes', 0, () => {
      expect(SyncConflictResolver.decide(true, true)).assertEqual(SyncAction.KEEP_BOTH);
    });

    it('adds the conflict marker before the extension', 0, () => {
      const path = SyncConflictResolver.conflictPath('notes/trip.md', 'Mate60', 42);
      expect(path).assertEqual('notes/trip (冲突-Mate60-42).md');
    });
  });
}
