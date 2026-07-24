import { FileSnapshot, ProviderFolderSnapshot } from '../SyncModels';
import { SyncPath } from '../SyncPath';

interface GraphParentReferenceJson {
  id?: string;
  path?: string;
}

interface GraphDriveItemJson {
  id?: string;
  name?: string;
  size?: number;
  eTag?: string;
  lastModifiedDateTime?: string;
  parentReference?: GraphParentReferenceJson;
  folder?: Object;
  deleted?: Object;
}

interface GraphUploadSessionJson {
  uploadUrl?: string;
}

interface GraphDeltaPageJson {
  value?: Array<GraphDriveItemJson>;
  '@odata.nextLink'?: string;
  '@odata.deltaLink'?: string;
}

export class OneDriveRootInfo {
  id: string;
  remotePath: string;

  constructor(id: string, remotePath: string) {
    this.id = id;
    this.remotePath = remotePath;
  }
}

export class OneDriveDeltaPage {
  changes: Array<FileSnapshot>;
  cursor: string;
  hasMore: boolean;
  folders: Array<ProviderFolderSnapshot>;

  constructor(
    changes: Array<FileSnapshot>,
    cursor: string,
    hasMore: boolean,
    folders: Array<ProviderFolderSnapshot>
  ) {
    this.changes = changes;
    this.cursor = cursor;
    this.hasMore = hasMore;
    this.folders = folders;
  }
}

export class OneDriveItemInfo {
  id: string;
  size: number;
  revision: string;
  folder: boolean;

  constructor(id: string, size: number, revision: string, folder: boolean) {
    this.id = id;
    this.size = size;
    this.revision = revision;
    this.folder = folder;
  }
}

export class OneDriveGraphCodec {
  static parseRoot(content: string): OneDriveRootInfo {
    const item = JSON.parse(content) as GraphDriveItemJson;
    if (typeof item.id !== 'string' ||
      typeof item.name !== 'string' ||
      !item.parentReference ||
      typeof item.parentReference.path !== 'string') {
      throw new Error('OneDrive app root response is incomplete');
    }
    return new OneDriveRootInfo(
      item.id,
      `${item.parentReference.path}/${item.name}`
    );
  }

  static parseDelta(
    content: string,
    root: OneDriveRootInfo,
    baselineFiles: Array<FileSnapshot>,
    knownFolders: Array<ProviderFolderSnapshot> = []
  ): OneDriveDeltaPage {
    const page = JSON.parse(content) as GraphDeltaPageJson;
    if (!Array.isArray(page.value)) {
      throw new Error('OneDrive delta response has no value array');
    }

    const items = OneDriveGraphCodec.latestItems(page.value);
    const changes: Array<FileSnapshot> = [];
    const folders = OneDriveGraphCodec.copyFolders(knownFolders);
    OneDriveGraphCodec.putFolder(
      folders,
      new ProviderFolderSnapshot('', root.id)
    );
    const handledFolders = OneDriveGraphCodec.applyFolderChanges(
      items,
      root,
      baselineFiles,
      folders,
      changes
    );

    items.forEach((item: GraphDriveItemJson) => {
      if (item.id === root.id ||
        item.folder !== undefined ||
        (typeof item.id === 'string' && handledFolders.includes(item.id))) {
        return;
      }
      if (typeof item.id !== 'string') {
        throw new Error('OneDrive delta item has no stable ID');
      }

      const deleted = item.deleted !== undefined;
      let relativePath: string | undefined;
      if (deleted) {
        relativePath = OneDriveGraphCodec.pathFromBaseline(item.id, baselineFiles);
      } else {
        relativePath = OneDriveGraphCodec.filePath(item, root, folders);
      }
      if (!relativePath) {
        throw new Error(`OneDrive item ${item.id} cannot be mapped to a local path`);
      }

      if (deleted) {
        changes.push(new FileSnapshot(
          relativePath,
          0,
          undefined,
          Date.now(),
          undefined,
          true,
          item.id
        ));
        return;
      }
      if (typeof item.size !== 'number' || typeof item.eTag !== 'string') {
        throw new Error(`OneDrive file metadata is incomplete for ${relativePath}`);
      }
      changes.push(new FileSnapshot(
        relativePath,
        item.size,
        undefined,
        OneDriveGraphCodec.parseTime(item.lastModifiedDateTime),
        item.eTag,
        false,
        item.id,
        item.parentReference?.id
      ));
    });

    if (typeof page['@odata.nextLink'] === 'string') {
      return new OneDriveDeltaPage(
        changes,
        page['@odata.nextLink'],
        true,
        folders
      );
    }
    if (typeof page['@odata.deltaLink'] !== 'string') {
      throw new Error('OneDrive delta response has no continuation cursor');
    }
    return new OneDriveDeltaPage(
      changes,
      page['@odata.deltaLink'],
      false,
      folders
    );
  }

  static parseItem(content: string): OneDriveItemInfo {
    const item = JSON.parse(content) as GraphDriveItemJson;
    if (typeof item.id !== 'string') {
      throw new Error('OneDrive item response has no stable ID');
    }
    const isFolder = item.folder !== undefined;
    if (isFolder) {
      return new OneDriveItemInfo(item.id, 0, item.eTag ?? '', true);
    }
    if (typeof item.size !== 'number' || typeof item.eTag !== 'string') {
      throw new Error('OneDrive file response is incomplete');
    }
    return new OneDriveItemInfo(item.id, item.size, item.eTag, false);
  }

  static parseUploadUrl(content: string): string {
    const session = JSON.parse(content) as GraphUploadSessionJson;
    if (typeof session.uploadUrl !== 'string') {
      throw new Error('OneDrive upload session response has no upload URL');
    }
    return session.uploadUrl;
  }

  private static applyFolderChanges(
    items: Array<GraphDriveItemJson>,
    root: OneDriveRootInfo,
    baselineFiles: Array<FileSnapshot>,
    folders: Array<ProviderFolderSnapshot>,
    changes: Array<FileSnapshot>
  ): Array<string> {
    const pending: Array<GraphDriveItemJson> = [];
    const handled: Array<string> = [];
    items.forEach((item: GraphDriveItemJson) => {
      const knownFolder = typeof item.id === 'string' ?
        OneDriveGraphCodec.findFolder(folders, item.id) :
        undefined;
      if (item.id === root.id || (item.folder === undefined && !knownFolder)) {
        return;
      }
      if (typeof item.id !== 'string') {
        throw new Error('OneDrive folder delta has no stable ID');
      }
      if (item.deleted !== undefined) {
        OneDriveGraphCodec.deleteFolder(item.id, baselineFiles, folders, changes);
        handled.push(item.id);
      } else {
        pending.push(item);
      }
    });

    while (pending.length > 0) {
      let progressed: boolean = false;
      for (let index = pending.length - 1; index >= 0; index -= 1) {
        const item = pending[index];
        const folder = OneDriveGraphCodec.resolveFolder(item, root, folders);
        if (!folder) {
          continue;
        }
        const previous = OneDriveGraphCodec.findFolder(folders, folder.providerItemId);
        if (previous && previous.relativePath !== folder.relativePath) {
          OneDriveGraphCodec.moveFolder(
            previous.relativePath,
            folder.relativePath,
            baselineFiles,
            folders,
            changes
          );
        }
        OneDriveGraphCodec.putFolder(folders, folder);
        handled.push(folder.providerItemId);
        pending.splice(index, 1);
        progressed = true;
      }
      if (!progressed) {
        throw new Error('OneDrive folder hierarchy cannot be resolved from delta data');
      }
    }
    return handled;
  }

  private static latestItems(
    items: Array<GraphDriveItemJson>
  ): Array<GraphDriveItemJson> {
    const latest: Array<GraphDriveItemJson> = [];
    items.forEach((item: GraphDriveItemJson) => {
      if (typeof item.id !== 'string') {
        latest.push(item);
        return;
      }
      for (let index = latest.length - 1; index >= 0; index -= 1) {
        if (latest[index].id === item.id) {
          latest.splice(index, 1);
          break;
        }
      }
      latest.push(item);
    });
    return latest;
  }

  private static resolveFolder(
    item: GraphDriveItemJson,
    root: OneDriveRootInfo,
    folders: Array<ProviderFolderSnapshot>
  ): ProviderFolderSnapshot | undefined {
    if (typeof item.id !== 'string' ||
      typeof item.name !== 'string' ||
      !item.parentReference ||
      typeof item.parentReference.id !== 'string') {
      return undefined;
    }
    const parentId = item.parentReference.id;
    let candidate: string;
    if (parentId === root.id) {
      candidate = item.name;
    } else {
      const parent = OneDriveGraphCodec.findFolder(folders, parentId);
      if (!parent) {
        return undefined;
      }
      candidate = `${parent.relativePath}/${item.name}`;
    }
    return new ProviderFolderSnapshot(
      SyncPath.normalizeDirectory(candidate),
      item.id,
      parentId
    );
  }

  private static filePath(
    item: GraphDriveItemJson,
    root: OneDriveRootInfo,
    folders: Array<ProviderFolderSnapshot>
  ): string | undefined {
    if (typeof item.name !== 'string' || !item.parentReference) {
      return undefined;
    }
    if (typeof item.parentReference.id === 'string') {
      const parent = OneDriveGraphCodec.findFolder(folders, item.parentReference.id);
      if (parent) {
        return SyncPath.normalize(`${parent.relativePath}/${item.name}`);
      }
    }
    if (typeof item.parentReference.path !== 'string') {
      return undefined;
    }
    const parentPath = item.parentReference.path;
    if (!parentPath.startsWith(`${root.remotePath}/`)) {
      return undefined;
    }
    const relativeParent = parentPath.slice(root.remotePath.length + 1);
    return SyncPath.normalize(`${relativeParent}/${item.name}`);
  }

  private static deleteFolder(
    folderId: string,
    baselineFiles: Array<FileSnapshot>,
    folders: Array<ProviderFolderSnapshot>,
    changes: Array<FileSnapshot>
  ): void {
    const folder = OneDriveGraphCodec.findFolder(folders, folderId);
    if (!folder) {
      return;
    }
    const prefix = `${folder.relativePath}/`;
    baselineFiles.forEach((file: FileSnapshot) => {
      if (file.relativePath.startsWith(prefix)) {
        changes.push(new FileSnapshot(
          file.relativePath,
          0,
          undefined,
          Date.now(),
          undefined,
          true,
          file.providerItemId,
          file.providerParentId
        ));
      }
    });
    for (let index = folders.length - 1; index >= 0; index -= 1) {
      if (folders[index].providerItemId === folderId ||
        folders[index].relativePath.startsWith(prefix)) {
        folders.splice(index, 1);
      }
    }
  }

  private static moveFolder(
    oldPath: string,
    newPath: string,
    baselineFiles: Array<FileSnapshot>,
    folders: Array<ProviderFolderSnapshot>,
    changes: Array<FileSnapshot>
  ): void {
    const oldPrefix = `${oldPath}/`;
    const newPrefix = `${newPath}/`;
    baselineFiles.forEach((file: FileSnapshot) => {
      if (!file.relativePath.startsWith(oldPrefix)) {
        return;
      }
      const newFilePath = `${newPrefix}${file.relativePath.slice(oldPrefix.length)}`;
      changes.push(new FileSnapshot(
        file.relativePath,
        0,
        undefined,
        Date.now(),
        undefined,
        true,
        file.providerItemId,
        file.providerParentId
      ));
      changes.push(new FileSnapshot(
        SyncPath.normalize(newFilePath),
        file.size,
        undefined,
        file.modifiedAt,
        file.providerRevision,
        false,
        file.providerItemId,
        file.providerParentId
      ));
    });
    folders.forEach((folder: ProviderFolderSnapshot) => {
      if (folder.relativePath.startsWith(oldPrefix)) {
        folder.relativePath =
          `${newPrefix}${folder.relativePath.slice(oldPrefix.length)}`;
      }
    });
  }

  private static copyFolders(
    folders: Array<ProviderFolderSnapshot>
  ): Array<ProviderFolderSnapshot> {
    return folders.map((folder: ProviderFolderSnapshot) =>
      new ProviderFolderSnapshot(
        folder.relativePath,
        folder.providerItemId,
        folder.providerParentId,
        folder.deleted
      )
    );
  }

  private static findFolder(
    folders: Array<ProviderFolderSnapshot>,
    itemId: string
  ): ProviderFolderSnapshot | undefined {
    for (let index = 0; index < folders.length; index += 1) {
      if (folders[index].providerItemId === itemId) {
        return folders[index];
      }
    }
    return undefined;
  }

  private static putFolder(
    folders: Array<ProviderFolderSnapshot>,
    folder: ProviderFolderSnapshot
  ): void {
    for (let index = 0; index < folders.length; index += 1) {
      if (folders[index].providerItemId === folder.providerItemId) {
        folders.splice(index, 1, folder);
        return;
      }
    }
    folders.push(folder);
  }

  private static pathFromBaseline(
    itemId: string,
    baselineFiles: Array<FileSnapshot>
  ): string | undefined {
    for (let index = 0; index < baselineFiles.length; index += 1) {
      if (baselineFiles[index].providerItemId === itemId) {
        return baselineFiles[index].relativePath;
      }
    }
    return undefined;
  }

  private static parseTime(value?: string): number {
    if (!value) {
      return 0;
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? 0 : timestamp;
  }
}
