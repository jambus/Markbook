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
    const safeDeviceName = deviceName
      .replace(/[\/\\:*?"<>|]/g, '_')
      .trim();
    const label = safeDeviceName.length > 0 ? safeDeviceName : 'device';
    const dotIndex = path.lastIndexOf('.');
    const suffix = ` (冲突-${label}-${timestamp})`;
    if (dotIndex <= path.lastIndexOf('/')) {
      return `${path}${suffix}`;
    }
    return `${path.slice(0, dotIndex)}${suffix}${path.slice(dotIndex)}`;
  }
}
