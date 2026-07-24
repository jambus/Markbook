import type { FileSnapshot, ProviderFolderSnapshot } from './SyncModels';
import type { SyncContentSource } from './SyncContent';

export enum SyncState {
  IDLE = 'idle',
  SYNCING = 'syncing',
  SUCCESS = 'success',
  ERROR = 'error'
}

export class SyncResult {
  uploaded: number;
  downloaded: number;
  deleted: number;
  conflicts: number;

  constructor(uploaded: number, downloaded: number, deleted: number, conflicts: number) {
    this.uploaded = uploaded;
    this.downloaded = downloaded;
    this.deleted = deleted;
    this.conflicts = conflicts;
  }
}

export class ProviderChangePage {
  changes: Array<FileSnapshot>;
  cursor: string;
  hasMore: boolean;
  folders: Array<ProviderFolderSnapshot>;

  constructor(
    changes: Array<FileSnapshot>,
    cursor: string,
    hasMore: boolean,
    folders: Array<ProviderFolderSnapshot> = []
  ) {
    this.changes = changes;
    this.cursor = cursor;
    this.hasMore = hasMore;
    this.folders = folders;
  }
}

export interface SyncProvider {
  readonly id: string;
  readonly displayName: string;
  connect(): Promise<void>;
  listChanges(
    cursor?: string,
    baselineFiles?: Array<FileSnapshot>,
    baselineFolders?: Array<ProviderFolderSnapshot>
  ): Promise<ProviderChangePage>;
  download(relativePath: string, expectedRevision?: string): Promise<SyncContentSource>;
  upload(
    relativePath: string,
    content: SyncContentSource,
    expectedRevision?: string,
    operationId?: string
  ): Promise<string>;
  delete(relativePath: string, expectedRevision?: string, operationId?: string): Promise<void>;
  cancel(): void;
}
