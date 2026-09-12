import { fileIo as fs } from '@kit.CoreFileKit';
import { SyncBaselineCodec } from './SyncBaselineCodec';
import type { SyncBaseline } from './SyncModels';
import type { SyncBaselineStore } from './SyncStore';

export class FileSyncBaselineStore implements SyncBaselineStore {
  private readonly directory: string;

  constructor(metadataDirectory: string) {
    this.directory = `${metadataDirectory}/sync`;
    if (!fs.accessSync(this.directory)) {
      fs.mkdirSync(this.directory, true);
    }
    this.recoverInterruptedWrites();
  }

  async load(providerId: string, notebookId: string): Promise<SyncBaseline | undefined> {
    const path = this.path(providerId, notebookId);
    if (!fs.accessSync(path)) {
      return undefined;
    }
    const baseline = SyncBaselineCodec.decode(fs.readTextSync(path));
    if (baseline.providerId !== providerId || baseline.notebookId !== notebookId) {
      throw new Error('Sync baseline identity does not match its file name');
    }
    return baseline;
  }

  async commit(baseline: SyncBaseline): Promise<void> {
    const path = this.path(baseline.providerId, baseline.notebookId);
    const tempPath = `${path}.tmp`;
    const backupPath = `${path}.bak`;
    this.safeUnlink(tempPath);

    try {
      const file = fs.openSync(
        tempPath,
        fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC | fs.OpenMode.SYNC
      );
      try {
        fs.writeSync(file.fd, SyncBaselineCodec.encode(baseline));
        fs.fsyncSync(file.fd);
      } finally {
        fs.closeSync(file);
      }
    } catch (error) {
      this.safeUnlink(tempPath);
      throw new Error('Failed to write the sync baseline');
    }

    this.safeUnlink(backupPath);
    if (fs.accessSync(path)) {
      fs.renameSync(path, backupPath);
    }
    try {
      fs.renameSync(tempPath, path);
      this.safeUnlink(backupPath);
    } catch (error) {
      this.safeUnlink(tempPath);
      if (!fs.accessSync(path) && fs.accessSync(backupPath)) {
        fs.renameSync(backupPath, path);
      }
      throw new Error('Failed to replace the sync baseline');
    }
  }

  private path(providerId: string, notebookId: string): string {
    this.validateId(providerId);
    this.validateId(notebookId);
    return `${this.directory}/${providerId}-${notebookId}.json`;
  }

  private validateId(value: string): void {
    if (!/^[A-Za-z0-9._-]+$/.test(value)) {
      throw new Error('Sync baseline identifiers may contain only safe filename characters');
    }
  }

  private recoverInterruptedWrites(): void {
    const names = fs.listFileSync(this.directory);
    names.forEach((name: string) => {
      const path = `${this.directory}/${name}`;
      if (name.endsWith('.json.tmp')) {
        this.safeUnlink(path);
        return;
      }
      if (name.endsWith('.json.bak')) {
        const targetPath = path.slice(0, -4);
        if (fs.accessSync(targetPath)) {
          this.safeUnlink(path);
        } else {
          fs.renameSync(path, targetPath);
        }
      }
    });
  }

  private safeUnlink(path: string): void {
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }
}
