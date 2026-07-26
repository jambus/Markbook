import { FileSnapshot, ProviderFolderSnapshot, SyncBaseline } from './SyncModels';

interface FileSnapshotJson {
  relativePath: string;
  size: number;
  contentHash: string;
  modifiedAt: number;
  providerRevision?: string;
  deleted: boolean;
  providerItemId?: string;
  providerParentId?: string;
}

interface SyncBaselineJson {
  version: number;
  providerId: string;
  notebookId: string;
  files: Array<FileSnapshotJson>;
  cursor?: string;
  completedAt: number;
  providerFolders?: Array<ProviderFolderJson>;
}

interface ProviderFolderJson {
  relativePath: string;
  providerItemId: string;
  providerParentId?: string;
  deleted: boolean;
}

export class SyncBaselineCodec {
  static encode(baseline: SyncBaseline): string {
    const value: SyncBaselineJson = {
      version: 1,
      providerId: baseline.providerId,
      notebookId: baseline.notebookId,
      files: baseline.files.map((file: FileSnapshot): FileSnapshotJson => {
        if (file.contentHash === undefined) {
          throw new Error('Cannot persist a sync baseline without a content hash');
        }
        return {
          relativePath: file.relativePath,
          size: file.size,
          contentHash: file.contentHash,
          modifiedAt: file.modifiedAt,
          providerRevision: file.providerRevision,
          deleted: file.deleted,
          providerItemId: file.providerItemId,
          providerParentId: file.providerParentId
        };
      }),
      cursor: baseline.cursor,
      completedAt: baseline.completedAt,
      providerFolders: baseline.providerFolders.map(
        (folder: ProviderFolderSnapshot): ProviderFolderJson => {
          return {
            relativePath: folder.relativePath,
            providerItemId: folder.providerItemId,
            providerParentId: folder.providerParentId,
            deleted: folder.deleted
          };
        }
      )
    };
    return JSON.stringify(value);
  }

  static decode(content: string): SyncBaseline {
    const value = JSON.parse(content) as SyncBaselineJson;
    if (value.version !== 1 ||
      typeof value.providerId !== 'string' ||
      typeof value.notebookId !== 'string' ||
      !Array.isArray(value.files) ||
      typeof value.completedAt !== 'number') {
      throw new Error('Invalid sync baseline');
    }

    const files: Array<FileSnapshot> = value.files.map((file: FileSnapshotJson) => {
      if (typeof file.relativePath !== 'string' ||
        typeof file.size !== 'number' ||
        typeof file.contentHash !== 'string' ||
        typeof file.modifiedAt !== 'number' ||
        typeof file.deleted !== 'boolean') {
        throw new Error('Invalid file snapshot in sync baseline');
      }
      return new FileSnapshot(
        file.relativePath,
        file.size,
        file.contentHash,
        file.modifiedAt,
        file.providerRevision,
        file.deleted,
        file.providerItemId,
        file.providerParentId
      );
    });
    const folderValues = Array.isArray(value.providerFolders) ? value.providerFolders : [];
    const folders = folderValues.map((folder: ProviderFolderJson) => {
      if (typeof folder.relativePath !== 'string' ||
        typeof folder.providerItemId !== 'string' ||
        typeof folder.deleted !== 'boolean') {
        throw new Error('Invalid folder snapshot in sync baseline');
      }
      return new ProviderFolderSnapshot(
        folder.relativePath,
        folder.providerItemId,
        folder.providerParentId,
        folder.deleted
      );
    });
    return new SyncBaseline(
      value.providerId,
      value.notebookId,
      files,
      value.cursor,
      value.completedAt,
      folders
    );
  }
}
