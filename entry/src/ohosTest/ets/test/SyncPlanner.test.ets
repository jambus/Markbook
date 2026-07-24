import { describe, expect, it } from '@ohos/hypium';
import {
  FileSnapshot,
  SyncBaseline,
  SyncOperationType
} from '../../../main/ets/services/sync/SyncModels';
import { SyncPath } from '../../../main/ets/services/sync/SyncPath';
import { SyncPlanner } from '../../../main/ets/services/sync/SyncPlanner';

function snapshot(path: string, hash: string, revision?: string): FileSnapshot {
  return new FileSnapshot(path, 10, hash, 1, revision);
}

function baseline(files: Array<FileSnapshot>): SyncBaseline {
  return new SyncBaseline('memory', 'notebook', files, 'cursor-1', 1);
}

export default function syncPlannerTest(): void {
  describe('SyncPlanner', () => {
    it('uploads a local-only change', 0, () => {
      const previous = snapshot('notes/a.md', 'old', 'r1');
      const plan = SyncPlanner.createPlan(
        [snapshot('notes/a.md', 'local')],
        [snapshot('notes/a.md', 'old', 'r1')],
        baseline([previous])
      );
      expect(plan.operations.length).assertEqual(1);
      expect(plan.operations[0].type).assertEqual(SyncOperationType.UPLOAD);
      expect(plan.operations[0].expectedRevision).assertEqual('r1');
    });

    it('downloads a remote-only change', 0, () => {
      const previous = snapshot('notes/a.md', 'old', 'r1');
      const plan = SyncPlanner.createPlan(
        [snapshot('notes/a.md', 'old')],
        [snapshot('notes/a.md', 'remote', 'r2')],
        baseline([previous])
      );
      expect(plan.operations.length).assertEqual(1);
      expect(plan.operations[0].type).assertEqual(SyncOperationType.DOWNLOAD);
    });

    it('uses an opaque remote revision when no remote hash is available', 0, () => {
      const previous = snapshot('notes/a.md', 'old', 'r1');
      const plan = SyncPlanner.createPlan(
        [snapshot('notes/a.md', 'old')],
        [new FileSnapshot('notes/a.md', 12, undefined, 2, 'r2')],
        baseline([previous])
      );
      expect(plan.operations.length).assertEqual(1);
      expect(plan.operations[0].type).assertEqual(SyncOperationType.DOWNLOAD);
    });

    it('keeps both independently changed versions', 0, () => {
      const previous = snapshot('notes/a.md', 'old', 'r1');
      const plan = SyncPlanner.createPlan(
        [snapshot('notes/a.md', 'local')],
        [snapshot('notes/a.md', 'remote', 'r2')],
        baseline([previous])
      );
      expect(plan.operations.length).assertEqual(1);
      expect(plan.operations[0].type).assertEqual(SyncOperationType.KEEP_BOTH);
    });

    it('propagates a local deletion', 0, () => {
      const previous = snapshot('attachments/photo.jpg', 'photo', 'r1');
      const plan = SyncPlanner.createPlan(
        [],
        [snapshot('attachments/photo.jpg', 'photo', 'r1')],
        baseline([previous])
      );
      expect(plan.operations.length).assertEqual(1);
      expect(plan.operations[0].type).assertEqual(SyncOperationType.DELETE_REMOTE);
    });

    it('does nothing when both sides independently reach the same content', 0, () => {
      const previous = snapshot('notes/a.md', 'old', 'r1');
      const plan = SyncPlanner.createPlan(
        [snapshot('notes/a.md', 'same')],
        [snapshot('notes/a.md', 'same', 'r2')],
        baseline([previous])
      );
      expect(plan.operations.length).assertEqual(0);
    });
  });

  describe('SyncPath', () => {
    it('normalizes harmless duplicate separators', 0, () => {
      expect(SyncPath.normalize('notes//daily.md')).assertEqual('notes/daily.md');
    });

    it('rejects traversal outside the notebook', 0, () => {
      let rejected: boolean = false;
      try {
        SyncPath.normalize('../secret.md');
      } catch (error) {
        rejected = true;
      }
      expect(rejected).assertTrue();
    });

    it('rejects internal sync metadata', 0, () => {
      let internalRejected: boolean = false;
      let portableMetadataRejected: boolean = false;
      try {
        SyncPath.normalize('.markbook/baseline.json');
      } catch (error) {
        internalRejected = true;
      }
      try {
        SyncPath.normalize('metadata/settings.json');
      } catch (error) {
        portableMetadataRejected = true;
      }
      expect(internalRejected).assertTrue();
      expect(portableMetadataRejected).assertTrue();
    });
  });
}
