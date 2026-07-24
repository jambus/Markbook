import { SyncPath } from '../SyncPath';

export class OneDriveGraphPath {
  static encode(relativePath: string): string {
    return SyncPath.normalize(relativePath)
      .split('/')
      .map((segment: string) => encodeURIComponent(segment))
      .join('/');
  }

  static encodeDirectory(relativePath: string): string {
    return SyncPath.normalizeDirectory(relativePath)
      .split('/')
      .map((segment: string) => encodeURIComponent(segment))
      .join('/');
  }

  static normalizeBaseUrl(baseUrl: string): string {
    if (!baseUrl.startsWith('https://')) {
      throw new Error('Microsoft Graph base URL must use HTTPS');
    }
    return baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
  }

  static validateCursor(cursor: string, baseUrl: string): string {
    const normalizedBase = OneDriveGraphPath.normalizeBaseUrl(baseUrl);
    const pathIndex = normalizedBase.indexOf('/', 'https://'.length);
    const origin = pathIndex >= 0 ?
      normalizedBase.slice(0, pathIndex) :
      normalizedBase;
    if (!cursor.startsWith(`${origin}/`)) {
      throw new Error('OneDrive cursor uses an unexpected origin');
    }
    return cursor;
  }

  static validateUploadUrl(uploadUrl: string): string {
    if (!uploadUrl.startsWith('https://')) {
      throw new Error('OneDrive upload session URL must use HTTPS');
    }
    return uploadUrl;
  }
}
