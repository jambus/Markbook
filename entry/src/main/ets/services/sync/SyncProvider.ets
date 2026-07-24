export enum SyncState {
  IDLE = 'idle',
  SYNCING = 'syncing',
  SUCCESS = 'success',
  ERROR = 'error'
}

export class SyncResult {
  uploaded: number;
  downloaded: number;
  conflicts: number;

  constructor(uploaded: number, downloaded: number, conflicts: number) {
    this.uploaded = uploaded;
    this.downloaded = downloaded;
    this.conflicts = conflicts;
  }
}

export interface SyncProvider {
  readonly id: string;
  readonly displayName: string;
  sync(notebookPath: string): Promise<SyncResult>;
}

