import { describe, expect, it } from '@ohos/hypium';
import {
  FileSnapshot,
  ProviderFolderSnapshot
} from '../../../main/ets/services/sync/SyncModels';
import {
  OneDriveGraphCodec,
  OneDriveRootInfo
} from '../../../main/ets/services/sync/onedrive/OneDriveGraphCodec';
import { OneDriveGraphPath } from '../../../main/ets/services/sync/onedrive/OneDriveGraphPath';

export default function oneDriveGraphTest(): void {
  describe('OneDriveGraph', () => {
    it('encodes each path segment without encoding separators', 0, () => {
      expect(OneDriveGraphPath.encode('notes/旅行 计划.md'))
        .assertEqual('notes/%E6%97%85%E8%A1%8C%20%E8%AE%A1%E5%88%92.md');
    });

    it('parses files and an opaque delta cursor', 0, () => {
      const root = new OneDriveRootInfo('root-id', '/drive/root:/Apps/Markbook');
      const page = OneDriveGraphCodec.parseDelta(
        '{"value":[{"id":"notes-folder","name":"notes",' +
          '"parentReference":{"id":"root-id"},"folder":{}},' +
          '{"id":"item-1","name":"a.md","size":12,"eTag":"r2",' +
          '"lastModifiedDateTime":"2026-07-24T08:00:00Z",' +
          '"parentReference":{"id":"notes-folder"},"file":{}}],' +
          '"@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?token=opaque"}',
        root,
        []
      );

      expect(page.changes.length).assertEqual(1);
      expect(page.changes[0].relativePath).assertEqual('notes/a.md');
      expect(page.changes[0].providerRevision).assertEqual('r2');
      expect(page.changes[0].providerItemId).assertEqual('item-1');
      expect(page.changes[0].providerParentId).assertEqual('notes-folder');
      expect(page.hasMore).assertFalse();
    });

    it('maps an ID-only deletion through the baseline', 0, () => {
      const root = new OneDriveRootInfo('root-id', '/drive/root:/Apps/Markbook');
      const baseline = [
        new FileSnapshot('attachments/old.jpg', 10, 'hash', 1, 'r1', false, 'item-2')
      ];
      const page = OneDriveGraphCodec.parseDelta(
        '{"value":[{"id":"item-2","deleted":{}}],' +
          '"@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?token=next"}',
        root,
        baseline
      );

      expect(page.changes[0].relativePath).assertEqual('attachments/old.jpg');
      expect(page.changes[0].deleted).assertTrue();
    });

    it('expands a deleted folder into descendant file tombstones', 0, () => {
      const root = new OneDriveRootInfo('root-id', '/drive/root:/Apps/Markbook');
      const baseline = [
        new FileSnapshot(
          'notes/a.md',
          10,
          'a',
          1,
          'r1',
          false,
          'item-1',
          'notes-folder'
        ),
        new FileSnapshot(
          'notes/archive/b.md',
          10,
          'b',
          1,
          'r1',
          false,
          'item-2',
          'archive-folder'
        )
      ];
      const folders = [
        new ProviderFolderSnapshot('', 'root-id'),
        new ProviderFolderSnapshot('notes', 'notes-folder', 'root-id'),
        new ProviderFolderSnapshot('notes/archive', 'archive-folder', 'notes-folder')
      ];
      const page = OneDriveGraphCodec.parseDelta(
        '{"value":[{"id":"notes-folder","folder":{},"deleted":{}}],' +
          '"@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?token=next"}',
        root,
        baseline,
        folders
      );

      expect(page.changes.length).assertEqual(2);
      expect(page.changes[0].deleted).assertTrue();
      expect(page.folders.length).assertEqual(1);
    });

    it('rejects a cursor from another origin', 0, () => {
      let rejected: boolean = false;
      try {
        OneDriveGraphPath.validateCursor(
          'https://example.com/stolen',
          'https://graph.microsoft.com/v1.0'
        );
      } catch (error) {
        rejected = true;
      }
      expect(rejected).assertTrue();
    });
  });
}
