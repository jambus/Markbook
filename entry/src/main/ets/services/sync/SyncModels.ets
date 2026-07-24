export class FileSnapshot {
  relativePath: string;
  size: number;
  contentHash?: string;
  modifiedAt: number;
  providerRevision?: string;
  deleted: boolean;
  providerItemId?: string;
  providerParentId?: string;

  constructor(
    relativePath: string,
    size: number,
    contentHash: string | undefined,
    modifiedAt: number,
    providerRevision?: string,
    deleted: boolean = false,
    providerItemId?: string,
    providerParentId?: string
  ) {
    this.relativePath = relativePath;
    this.size = size;
    this.contentHash = contentHash;
    this.modifiedAt = modifiedAt;
    this.providerRevision = providerRevision;
    this.deleted = deleted;
    this.providerItemId = providerItemId;
    this.providerParentId = providerParentId;
  }
}

export class ProviderFolderSnapshot {
  relativePath: string;
  providerItemId: string;
  providerParentId?: string;
  deleted: boolean;

  constructor(
    relativePath: string,
    providerItemId: string,
    providerParentId?: string,
    deleted: boolean = false
  ) {
    this.relativePath = relativePath;
    this.providerItemId = providerItemId;
    this.providerParentId = providerParentId;
    this.deleted = deleted;
  }
}

export class SyncBaseline {
  providerId: string;
  notebookId: string;
  files: Array<FileSnapshot>;
  cursor?: string;
  completedAt: number;
  providerFolders: Array<ProviderFolderSnapshot>;

  constructor(
    providerId: string,
    notebookId: string,
    files: Array<FileSnapshot>,
    cursor?: string,
    completedAt: number = 0,
    providerFolders: Array<ProviderFolderSnapshot> = []
  ) {
    this.providerId = providerId;
    this.notebookId = notebookId;
    this.files = files;
    this.cursor = cursor;
    this.completedAt = completedAt;
    this.providerFolders = providerFolders;
  }
}

export enum SyncOperationType {
  UPLOAD = 'upload',
  DOWNLOAD = 'download',
  DELETE_LOCAL = 'delete-local',
  DELETE_REMOTE = 'delete-remote',
  KEEP_BOTH = 'keep-both'
}

export enum SyncOperationState {
  PENDING = 'pending',
  RUNNING = 'running',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled'
}

export class SyncOperation {
  type: SyncOperationType;
  relativePath: string;
  expectedRevision?: string;
  state: SyncOperationState;

  constructor(type: SyncOperationType, relativePath: string, expectedRevision?: string) {
    this.type = type;
    this.relativePath = relativePath;
    this.expectedRevision = expectedRevision;
    this.state = SyncOperationState.PENDING;
  }
}

export class SyncPlan {
  operations: Array<SyncOperation>;

  constructor(operations: Array<SyncOperation>) {
    this.operations = operations;
  }
}
