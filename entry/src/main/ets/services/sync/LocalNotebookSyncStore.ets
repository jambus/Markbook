import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { FileSnapshot } from './SyncModels';
import { SyncPath } from './SyncPath';
import type { SyncContentSource } from './SyncContent';
import type { LocalSyncStore } from './SyncStore';

const IO_CHUNK_SIZE = 256 * 1024;
const SYNC_ROOTS: Array<string> = ['notes', 'attachments'];

class LocalFileContentSource implements SyncContentSource {
  readonly size: number;
  private readonly fd: number;
  private closed: boolean = false;

  constructor(path: string) {
    const stat = fs.statSync(path);
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY | fs.OpenMode.NOFOLLOW);
    this.fd = file.fd;
    this.size = stat.size;
  }

  async read(offset: number, maxBytes: number): Promise<Uint8Array> {
    if (this.closed) {
      throw new Error('Cannot read a closed sync content source');
    }
    if (offset < 0 || maxBytes <= 0 || offset > this.size) {
      throw new Error('Invalid sync content read range');
    }
    const requested = Math.min(maxBytes, this.size - offset);
    if (requested === 0) {
      return new Uint8Array(0);
    }
    const buffer = new ArrayBuffer(requested);
    const length = fs.readSync(this.fd, buffer, { offset: offset, length: requested });
    return new Uint8Array(buffer, 0, length);
  }

  close(): void {
    if (!this.closed) {
      fs.closeSync(this.fd);
      this.closed = true;
    }
  }
}

export class LocalNotebookSyncStore implements LocalSyncStore {
  private readonly rootDir: string;
  private readonly tempDir: string;
  private sequence: number = 0;

  constructor(rootDir: string, tempDir: string) {
    this.rootDir = rootDir;
    this.tempDir = tempDir;
    if (!fs.accessSync(this.tempDir)) {
      fs.mkdirSync(this.tempDir, true);
    }
  }

  async listSnapshots(): Promise<Array<FileSnapshot>> {
    const snapshots: Array<FileSnapshot> = [];
    SYNC_ROOTS.forEach((root: string) => {
      const path = `${this.rootDir}/${root}`;
      if (fs.accessSync(path)) {
        this.scanDirectory(root, snapshots);
      }
    });
    snapshots.sort((left: FileSnapshot, right: FileSnapshot) =>
      left.relativePath.localeCompare(right.relativePath)
    );
    return snapshots;
  }

  async openRead(relativePath: string): Promise<SyncContentSource> {
    const normalizedPath = SyncPath.normalize(relativePath);
    this.ensureSafeParents(normalizedPath, false);
    const path = `${this.rootDir}/${normalizedPath}`;
    const stat = fs.lstatSync(path);
    if (stat.isSymbolicLink() || stat.isDirectory()) {
      throw new Error(`Sync source is not a regular file: ${relativePath}`);
    }
    return new LocalFileContentSource(path);
  }

  async writeFrom(relativePath: string, source: SyncContentSource): Promise<void> {
    const normalizedPath = SyncPath.normalize(relativePath);
    const targetPath = `${this.rootDir}/${normalizedPath}`;
    this.ensureSafeParents(normalizedPath, true);
    const uniquePart = `${Date.now()}-${this.sequence}`;
    this.sequence += 1;
    const tempPath = `${this.tempDir}/sync-${uniquePart}.tmp`;
    const backupPath = `${targetPath}.sync-bak`;
    this.safeUnlink(tempPath);

    try {
      const file = fs.openSync(
        tempPath,
        fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC | fs.OpenMode.SYNC
      );
      try {
        let offset: number = 0;
        while (offset < source.size) {
          const requested = Math.min(IO_CHUNK_SIZE, source.size - offset);
          const chunk = await source.read(offset, requested);
          if (chunk.length === 0 || chunk.length > requested) {
            throw new Error('Sync content source returned an invalid chunk');
          }
          let chunkOffset: number = 0;
          while (chunkOffset < chunk.length) {
            const remaining = chunk.slice(chunkOffset);
            const written = fs.writeSync(file.fd, remaining.buffer);
            if (written <= 0) {
              throw new Error('Failed to make progress writing sync content');
            }
            chunkOffset += written;
          }
          offset += chunk.length;
        }
        fs.fsyncSync(file.fd);
      } finally {
        fs.closeSync(file);
      }
    } catch (error) {
      this.safeUnlink(tempPath);
      throw new Error('Failed to write synchronized content');
    }

    this.safeUnlink(backupPath);
    if (fs.accessSync(targetPath)) {
      fs.renameSync(targetPath, backupPath);
    }
    try {
      fs.renameSync(tempPath, targetPath);
      this.safeUnlink(backupPath);
    } catch (error) {
      this.safeUnlink(tempPath);
      if (!fs.accessSync(targetPath) && fs.accessSync(backupPath)) {
        fs.renameSync(backupPath, targetPath);
      }
      throw new Error('Failed to replace synchronized content');
    }
  }

  async delete(relativePath: string): Promise<void> {
    const normalizedPath = SyncPath.normalize(relativePath);
    this.ensureSafeParents(normalizedPath, false);
    const path = `${this.rootDir}/${normalizedPath}`;
    if (fs.accessSync(path)) {
      const stat = fs.lstatSync(path);
      if (stat.isDirectory()) {
        throw new Error(`Refusing to delete a directory during sync: ${relativePath}`);
      }
      fs.unlinkSync(path);
    }
  }

  private scanDirectory(relativeDirectory: string, snapshots: Array<FileSnapshot>): void {
    const directory = `${this.rootDir}/${relativeDirectory}`;
    const names = fs.listFileSync(directory);
    names.forEach((name: string) => {
      const relativePath = SyncPath.normalize(`${relativeDirectory}/${name}`);
      const path = `${this.rootDir}/${relativePath}`;
      const stat = fs.lstatSync(path);
      if (stat.isSymbolicLink()) {
        return;
      }
      if (stat.isDirectory()) {
        this.scanDirectory(relativePath, snapshots);
        return;
      }
      snapshots.push(new FileSnapshot(
        relativePath,
        stat.size,
        this.hash(path),
        stat.mtime
      ));
    });
  }

  private hash(path: string): string {
    const md = cryptoFramework.createMd('SHA256');
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY | fs.OpenMode.NOFOLLOW);
    try {
      const buffer = new ArrayBuffer(IO_CHUNK_SIZE);
      let length = fs.readSync(file.fd, buffer);
      while (length > 0) {
        md.updateSync({ data: new Uint8Array(buffer, 0, length) });
        length = fs.readSync(file.fd, buffer);
      }
      const digest = md.digestSync().data;
      let hex = '';
      for (let index = 0; index < digest.length; index += 1) {
        hex += digest[index].toString(16).padStart(2, '0');
      }
      return hex;
    } finally {
      fs.closeSync(file);
    }
  }

  private ensureSafeParents(relativePath: string, create: boolean): void {
    const segments = relativePath.split('/');
    let current = this.rootDir;
    for (let index = 0; index < segments.length - 1; index += 1) {
      current = `${current}/${segments[index]}`;
      if (!fs.accessSync(current)) {
        if (create) {
          fs.mkdirSync(current);
        }
        continue;
      }
      const stat = fs.lstatSync(current);
      if (stat.isSymbolicLink() || !stat.isDirectory()) {
        throw new Error(`Unsafe sync parent directory: ${segments[index]}`);
      }
    }
  }

  private safeUnlink(path: string): void {
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }
}
