import type { RefreshTokenStore } from './OneDriveSession';

export class MemoryRefreshTokenStore implements RefreshTokenStore {
  private refreshToken?: string;

  async load(): Promise<string | undefined> {
    return this.refreshToken;
  }

  async save(refreshToken: string): Promise<void> {
    this.refreshToken = refreshToken;
  }

  async clear(): Promise<void> {
    this.refreshToken = undefined;
  }
}
