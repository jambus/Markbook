export enum SyncAction {
  NONE,
  UPLOAD,
  DOWNLOAD,
  KEEP_BOTH
}

export class SyncConflictResolver {
  static decide(localChanged: boolean, remoteChanged: boolean): SyncAction {
    if (localChanged && remoteChanged) {
      return SyncAction.KEEP_BOTH;
    }
    if (localChanged) {
      return SyncAction.UPLOAD;
    }
    if (remoteChanged) {
      return SyncAction.DOWNLOAD;
    }
    return SyncAction.NONE;
  }

  static conflictPath(path: string, deviceName: string, timestamp: number): string {
    const dotIndex = path.lastIndexOf('.');
    const suffix = ` (冲突-${deviceName}-${timestamp})`;
    if (dotIndex <= path.lastIndexOf('/')) {
      return `${path}${suffix}`;
    }
    return `${path.slice(0, dotIndex)}${suffix}${path.slice(dotIndex)}`;
  }
}
