export class SyncPath {
  static normalize(relativePath: string): string {
    const normalized = SyncPath.normalizeDirectory(relativePath);
    if (!normalized.includes('/')) {
      throw new Error('Sync path must identify a file below a supported root');
    }
    return normalized;
  }

  static normalizeDirectory(relativePath: string): string {
    if (relativePath.length === 0 || relativePath.startsWith('/') || relativePath.includes('\\')) {
      throw new Error('Sync path must be a non-empty relative path');
    }

    const normalized: Array<string> = [];
    const segments = relativePath.split('/');
    segments.forEach((segment: string) => {
      if (segment.length === 0 || segment === '.') {
        return;
      }
      if (segment === '..') {
        throw new Error('Sync path cannot escape the notebook root');
      }
      normalized.push(segment);
    });

    if (normalized.length === 0) {
      throw new Error('Sync path must identify a file');
    }
    const root = normalized[0];
    if (root !== 'notes' && root !== 'attachments') {
      throw new Error(`Sync path uses an unsupported root: ${root}`);
    }
    return normalized.join('/');
  }
}
