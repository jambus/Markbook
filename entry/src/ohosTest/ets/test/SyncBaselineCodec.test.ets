import { describe, expect, it } from '@ohos/hypium';
import { SyncBaselineCodec } from '../../../main/ets/services/sync/SyncBaselineCodec';
import {
  FileSnapshot,
  ProviderFolderSnapshot,
  SyncBaseline
} from '../../../main/ets/services/sync/SyncModels';

export default function syncBaselineCodecTest(): void {
  describe('SyncBaselineCodec', () => {
    it('round trips revisions, cursor, and deletion markers', 0, () => {
      const original = new SyncBaseline(
        'memory',
        'notebook',
        [
          new FileSnapshot(
            'notes/a.md',
            12,
            'abc',
            42,
            'r1',
            false,
            'item-1',
            'notes-folder'
          ),
          new FileSnapshot('attachments/old.jpg', 0, '', 43, undefined, true)
        ],
        'opaque-cursor',
        44,
        [new ProviderFolderSnapshot('notes', 'notes-folder', 'root-id')]
      );

      const decoded = SyncBaselineCodec.decode(SyncBaselineCodec.encode(original));

      expect(decoded.providerId).assertEqual('memory');
      expect(decoded.cursor).assertEqual('opaque-cursor');
      expect(decoded.files.length).assertEqual(2);
      expect(decoded.files[0].providerRevision).assertEqual('r1');
      expect(decoded.files[0].providerItemId).assertEqual('item-1');
      expect(decoded.files[0].providerParentId).assertEqual('notes-folder');
      expect(decoded.files[1].deleted).assertTrue();
      expect(decoded.providerFolders[0].relativePath).assertEqual('notes');
    });

    it('rejects unsupported baseline versions', 0, () => {
      let rejected: boolean = false;
      try {
        SyncBaselineCodec.decode(
          '{"version":2,"providerId":"p","notebookId":"n","files":[],"completedAt":1}'
        );
      } catch (error) {
        rejected = true;
      }
      expect(rejected).assertTrue();
    });
  });
}
