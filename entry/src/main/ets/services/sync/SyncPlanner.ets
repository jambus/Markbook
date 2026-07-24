import {
  FileSnapshot,
  SyncBaseline,
  SyncOperation,
  SyncOperationType,
  SyncPlan
} from './SyncModels';
import { SyncPath } from './SyncPath';

export class SyncPlanner {
  static createPlan(
    localFiles: Array<FileSnapshot>,
    remoteFiles: Array<FileSnapshot>,
    baseline: SyncBaseline
  ): SyncPlan {
    const operations: Array<SyncOperation> = [];
    const paths = SyncPlanner.collectPaths(localFiles, remoteFiles, baseline.files);

    paths.forEach((path: string) => {
      const local = SyncPlanner.find(localFiles, path);
      const remote = SyncPlanner.find(remoteFiles, path);
      const previous = SyncPlanner.find(baseline.files, path);
      const localChanged = SyncPlanner.localChanged(local, previous);
      const remoteChanged = SyncPlanner.remoteChanged(remote, previous);

      if (localChanged && remoteChanged) {
        if (!SyncPlanner.sameContent(local, remote)) {
          operations.push(new SyncOperation(
            SyncOperationType.KEEP_BOTH,
            path,
            remote ? remote.providerRevision : undefined
          ));
        }
        return;
      }
      if (localChanged) {
        operations.push(new SyncOperation(
          SyncPlanner.exists(local) ? SyncOperationType.UPLOAD : SyncOperationType.DELETE_REMOTE,
          path,
          remote ? remote.providerRevision : undefined
        ));
        return;
      }
      if (remoteChanged) {
        operations.push(new SyncOperation(
          SyncPlanner.exists(remote) ? SyncOperationType.DOWNLOAD : SyncOperationType.DELETE_LOCAL,
          path,
          remote ? remote.providerRevision : undefined
        ));
      }
    });

    return new SyncPlan(operations);
  }

  private static collectPaths(
    localFiles: Array<FileSnapshot>,
    remoteFiles: Array<FileSnapshot>,
    baselineFiles: Array<FileSnapshot>
  ): Array<string> {
    const paths: Array<string> = [];
    SyncPlanner.appendPaths(paths, localFiles);
    SyncPlanner.appendPaths(paths, remoteFiles);
    SyncPlanner.appendPaths(paths, baselineFiles);
    paths.sort();
    return paths;
  }

  private static appendPaths(paths: Array<string>, files: Array<FileSnapshot>): void {
    files.forEach((file: FileSnapshot) => {
      const path = SyncPath.normalize(file.relativePath);
      if (path !== file.relativePath) {
        throw new Error(`Sync path is not normalized: ${file.relativePath}`);
      }
      if (!paths.includes(path)) {
        paths.push(path);
      }
    });
  }

  private static find(files: Array<FileSnapshot>, path: string): FileSnapshot | undefined {
    for (let index = 0; index < files.length; index += 1) {
      if (files[index].relativePath === path) {
        return files[index];
      }
    }
    return undefined;
  }

  private static localChanged(current?: FileSnapshot, previous?: FileSnapshot): boolean {
    if (!previous) {
      return SyncPlanner.exists(current);
    }
    if (!current) {
      return !previous.deleted;
    }
    if (current.deleted !== previous.deleted) {
      return true;
    }
    if (current.deleted) {
      return false;
    }
    return current.contentHash !== previous.contentHash;
  }

  private static remoteChanged(current?: FileSnapshot, previous?: FileSnapshot): boolean {
    if (!previous) {
      return SyncPlanner.exists(current);
    }
    if (!current) {
      return !previous.deleted;
    }
    if (current.deleted !== previous.deleted) {
      return true;
    }
    if (current.deleted) {
      return false;
    }
    if (current.providerRevision !== undefined || previous.providerRevision !== undefined) {
      return current.providerRevision !== previous.providerRevision;
    }
    return current.contentHash !== previous.contentHash;
  }

  private static sameContent(left?: FileSnapshot, right?: FileSnapshot): boolean {
    if (!SyncPlanner.exists(left) || !SyncPlanner.exists(right)) {
      return !SyncPlanner.exists(left) && !SyncPlanner.exists(right);
    }
    return left !== undefined && right !== undefined &&
      left.contentHash !== undefined && right.contentHash !== undefined &&
      left.contentHash === right.contentHash;
  }

  private static exists(snapshot?: FileSnapshot): boolean {
    return snapshot !== undefined && !snapshot.deleted;
  }
}
