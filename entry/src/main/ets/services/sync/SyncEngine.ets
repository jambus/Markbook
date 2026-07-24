import { SyncConflictResolver } from './SyncConflictResolver';
import type { SyncContentSource } from './SyncContent';
import {
  FileSnapshot,
  ProviderFolderSnapshot,
  SyncBaseline,
  SyncOperation,
  SyncOperationState,
  SyncOperationType
} from './SyncModels';
import { SyncPlanner } from './SyncPlanner';
import { ProviderChangePage, SyncResult } from './SyncProvider';
import type { SyncProvider } from './SyncProvider';
import type { LocalSyncStore, SyncBaselineStore } from './SyncStore';

class RemoteSnapshot {
  files: Array<FileSnapshot>;
  cursor: string;
  folders: Array<ProviderFolderSnapshot>;

  constructor(
    files: Array<FileSnapshot>,
    cursor: string,
    folders: Array<ProviderFolderSnapshot>
  ) {
    this.files = files;
    this.cursor = cursor;
    this.folders = folders;
  }
}

export class SyncEngine {
  private activeProvider?: SyncProvider;
  private cancelled: boolean = false;

  async sync(
    provider: SyncProvider,
    localStore: LocalSyncStore,
    baselineStore: SyncBaselineStore,
    notebookId: string,
    deviceName: string
  ): Promise<SyncResult> {
    if (this.activeProvider) {
      throw new Error('A sync operation is already running');
    }

    this.activeProvider = provider;
    this.cancelled = false;

    try {
      const previous = await baselineStore.load(provider.id, notebookId) ??
        new SyncBaseline(provider.id, notebookId, []);
      await provider.connect();
      this.ensureNotCancelled();
      const localFiles = await localStore.listSnapshots();
      const remote = await this.readRemote(provider, previous);
      const plan = SyncPlanner.createPlan(localFiles, remote.files, previous);
      const result = new SyncResult(0, 0, 0, 0);

      for (let index = 0; index < plan.operations.length; index += 1) {
        this.ensureNotCancelled();
        await this.execute(
          plan.operations[index],
          provider,
          localStore,
          deviceName,
          result
        );
      }

      this.ensureNotCancelled();
      const finalLocal = await localStore.listSnapshots();
      const finalRemote = await this.readAllRemote(provider);
      const committedFiles = this.reconcile(finalLocal, finalRemote.files);
      this.ensureNotCancelled();
      await baselineStore.commit(new SyncBaseline(
        provider.id,
        notebookId,
        committedFiles,
        finalRemote.cursor,
        Date.now(),
        finalRemote.folders
      ));
      return result;
    } finally {
      this.activeProvider = undefined;
    }
  }

  cancel(): void {
    this.cancelled = true;
    if (this.activeProvider) {
      this.activeProvider.cancel();
    }
  }

  private async execute(
    operation: SyncOperation,
    provider: SyncProvider,
    localStore: LocalSyncStore,
    deviceName: string,
    result: SyncResult
  ): Promise<void> {
    operation.state = SyncOperationState.RUNNING;
    try {
      if (operation.type === SyncOperationType.UPLOAD) {
        const content = await localStore.openRead(operation.relativePath);
        try {
          await provider.upload(
            operation.relativePath,
            content,
            operation.expectedRevision,
            this.operationId(operation)
          );
        } finally {
          content.close();
        }
        result.uploaded += 1;
      } else if (operation.type === SyncOperationType.DOWNLOAD) {
        const content = await provider.download(
          operation.relativePath,
          operation.expectedRevision
        );
        try {
          await localStore.writeFrom(operation.relativePath, content);
        } finally {
          content.close();
        }
        result.downloaded += 1;
      } else if (operation.type === SyncOperationType.DELETE_LOCAL) {
        await localStore.delete(operation.relativePath);
        result.deleted += 1;
      } else if (operation.type === SyncOperationType.DELETE_REMOTE) {
        await provider.delete(
          operation.relativePath,
          operation.expectedRevision,
          this.operationId(operation)
        );
        result.deleted += 1;
      } else {
        await this.keepBoth(operation, provider, localStore, deviceName);
        result.uploaded += 1;
        result.downloaded += 1;
        result.conflicts += 1;
      }
      operation.state = SyncOperationState.COMPLETED;
    } catch (error) {
      operation.state = SyncOperationState.FAILED;
      throw error;
    }
  }

  private async keepBoth(
    operation: SyncOperation,
    provider: SyncProvider,
    localStore: LocalSyncStore,
    deviceName: string
  ): Promise<void> {
    const localContent = await localStore.openRead(operation.relativePath);
    try {
      const remoteContent = await provider.download(
        operation.relativePath,
        operation.expectedRevision
      );
      try {
        const conflictPath = SyncConflictResolver.conflictPath(
          operation.relativePath,
          deviceName,
          Date.now()
        );
        await localStore.writeFrom(conflictPath, localContent);
        await localStore.writeFrom(operation.relativePath, remoteContent);
        await provider.upload(
          conflictPath,
          localContent,
          undefined,
          this.operationId(operation)
        );
      } finally {
        remoteContent.close();
      }
    } finally {
      localContent.close();
    }
  }

  private async readRemote(
    provider: SyncProvider,
    baseline: SyncBaseline
  ): Promise<RemoteSnapshot> {
    if (!baseline.cursor) {
      return this.readAllRemote(provider);
    }
    const delta = await this.readPages(
      provider,
      baseline.cursor,
      baseline.files,
      baseline.providerFolders
    );
    return new RemoteSnapshot(
      this.applyChanges(baseline.files, delta.files),
      delta.cursor,
      delta.folders
    );
  }

  private async readAllRemote(provider: SyncProvider): Promise<RemoteSnapshot> {
    const remote = await this.readPages(provider, undefined);
    return new RemoteSnapshot(
      remote.files.filter((file: FileSnapshot) => !file.deleted),
      remote.cursor,
      remote.folders
    );
  }

  private async readPages(
    provider: SyncProvider,
    cursor?: string,
    baselineFiles?: Array<FileSnapshot>,
    baselineFolders?: Array<ProviderFolderSnapshot>
  ): Promise<RemoteSnapshot> {
    const changes: Array<FileSnapshot> = [];
    let page: ProviderChangePage = await provider.listChanges(
      cursor,
      baselineFiles,
      baselineFolders
    );
    changes.push(...page.changes);
    while (page.hasMore) {
      this.ensureNotCancelled();
      page = await provider.listChanges(page.cursor, baselineFiles, baselineFolders);
      changes.push(...page.changes);
    }
    return new RemoteSnapshot(changes, page.cursor, page.folders);
  }

  private applyChanges(
    baselineFiles: Array<FileSnapshot>,
    changes: Array<FileSnapshot>
  ): Array<FileSnapshot> {
    const files = baselineFiles.slice();
    changes.forEach((change: FileSnapshot) => {
      const index = this.findIndex(files, change.relativePath);
      if (index >= 0) {
        files.splice(index, 1);
      }
      if (!change.deleted) {
        files.push(change);
      }
    });
    return files;
  }

  private reconcile(
    localFiles: Array<FileSnapshot>,
    remoteFiles: Array<FileSnapshot>
  ): Array<FileSnapshot> {
    if (localFiles.length !== remoteFiles.length) {
      throw new Error('Sync completed with different local and remote file counts');
    }

    const files: Array<FileSnapshot> = [];
    localFiles.forEach((local: FileSnapshot) => {
      const remoteIndex = this.findIndex(remoteFiles, local.relativePath);
      if (remoteIndex < 0 || local.contentHash === undefined) {
        throw new Error(`Sync verification failed for ${local.relativePath}`);
      }
      const remote = remoteFiles[remoteIndex];
      if (remote.contentHash !== undefined && remote.contentHash !== local.contentHash) {
        throw new Error(`Sync content verification failed for ${local.relativePath}`);
      }
      files.push(new FileSnapshot(
        local.relativePath,
        local.size,
        local.contentHash,
        local.modifiedAt,
        remote.providerRevision,
        false,
        remote.providerItemId,
        remote.providerParentId
      ));
    });
    return files;
  }

  private findIndex(files: Array<FileSnapshot>, relativePath: string): number {
    for (let index = 0; index < files.length; index += 1) {
      if (files[index].relativePath === relativePath) {
        return index;
      }
    }
    return -1;
  }

  private operationId(operation: SyncOperation): string {
    return `${operation.type}:${operation.relativePath}:${Date.now()}`;
  }

  private ensureNotCancelled(): void {
    if (this.cancelled) {
      throw new Error('Sync cancelled');
    }
  }
}
