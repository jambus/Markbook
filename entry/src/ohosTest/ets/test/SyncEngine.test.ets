import { describe, expect, it } from '@ohos/hypium';
import { FileSnapshot, SyncBaseline } from '../../../main/ets/services/sync/SyncModels';
import type { SyncContentSource } from '../../../main/ets/services/sync/SyncContent';
import {
  ProviderChangePage
} from '../../../main/ets/services/sync/SyncProvider';
import type { SyncProvider } from '../../../main/ets/services/sync/SyncProvider';
import { SyncEngine } from '../../../main/ets/services/sync/SyncEngine';
import type {
  LocalSyncStore,
  SyncBaselineStore
} from '../../../main/ets/services/sync/SyncStore';

class MemoryFile {
  path: string;
  content: Uint8Array;
  revision: string;

  constructor(path: string, content: Uint8Array, revision: string = '') {
    this.path = path;
    this.content = content;
    this.revision = revision;
  }
}

function bytes(value: number): Uint8Array {
  return new Uint8Array([value]);
}

function hash(content: Uint8Array): string {
  let value = '';
  for (let index = 0; index < content.length; index += 1) {
    value += `${content[index]}-`;
  }
  return value;
}

function copy(content: Uint8Array): Uint8Array {
  const result = new Uint8Array(content.length);
  result.set(content);
  return result;
}

class MemoryContentSource implements SyncContentSource {
  readonly size: number;
  private readonly content: Uint8Array;
  closed: boolean = false;

  constructor(content: Uint8Array) {
    this.content = copy(content);
    this.size = content.length;
  }

  async read(offset: number, maxBytes: number): Promise<Uint8Array> {
    return this.content.slice(offset, Math.min(offset + maxBytes, this.size));
  }

  close(): void {
    this.closed = true;
  }
}

async function readAll(source: SyncContentSource): Promise<Uint8Array> {
  const result = new Uint8Array(source.size);
  let offset: number = 0;
  while (offset < source.size) {
    const chunk = await source.read(offset, 64);
    result.set(chunk, offset);
    offset += chunk.length;
  }
  return result;
}

function find(files: Array<MemoryFile>, path: string): number {
  for (let index = 0; index < files.length; index += 1) {
    if (files[index].path === path) {
      return index;
    }
  }
  return -1;
}

class MemoryLocalStore implements LocalSyncStore {
  files: Array<MemoryFile>;
  lastSource?: MemoryContentSource;

  constructor(files: Array<MemoryFile>) {
    this.files = files;
  }

  async listSnapshots(): Promise<Array<FileSnapshot>> {
    return this.files.map((file: MemoryFile) =>
      new FileSnapshot(file.path, file.content.length, hash(file.content), 1)
    );
  }

  async openRead(relativePath: string): Promise<SyncContentSource> {
    const index = find(this.files, relativePath);
    if (index < 0) {
      throw new Error(`Missing local file ${relativePath}`);
    }
    this.lastSource = new MemoryContentSource(this.files[index].content);
    return this.lastSource;
  }

  async writeFrom(relativePath: string, source: SyncContentSource): Promise<void> {
    const content = await readAll(source);
    const index = find(this.files, relativePath);
    const file = new MemoryFile(relativePath, copy(content));
    if (index >= 0) {
      this.files.splice(index, 1, file);
    } else {
      this.files.push(file);
    }
  }

  async delete(relativePath: string): Promise<void> {
    const index = find(this.files, relativePath);
    if (index >= 0) {
      this.files.splice(index, 1);
    }
  }
}

class MemoryProvider implements SyncProvider {
  readonly id: string = 'memory';
  readonly displayName: string = 'Memory';
  files: Array<MemoryFile>;
  failWrites: boolean = false;
  failDownloads: boolean = false;
  private revision: number = 1;

  constructor(files: Array<MemoryFile>) {
    this.files = files;
  }

  async connect(): Promise<void> {
  }

  async listChanges(cursor?: string): Promise<ProviderChangePage> {
    const snapshots = this.files.map((file: MemoryFile) =>
      new FileSnapshot(
        file.path,
        file.content.length,
        hash(file.content),
        1,
        file.revision
      )
    );
    return new ProviderChangePage(snapshots, `cursor-${this.revision}`, false);
  }

  async download(
    relativePath: string,
    expectedRevision?: string
  ): Promise<SyncContentSource> {
    if (this.failDownloads) {
      throw new Error('Injected download failure');
    }
    const index = find(this.files, relativePath);
    if (index < 0) {
      throw new Error(`Missing remote file ${relativePath}`);
    }
    if (expectedRevision && this.files[index].revision !== expectedRevision) {
      throw new Error('Remote revision changed');
    }
    return new MemoryContentSource(this.files[index].content);
  }

  async upload(
    relativePath: string,
    source: SyncContentSource,
    expectedRevision?: string,
    operationId?: string
  ): Promise<string> {
    if (this.failWrites) {
      throw new Error('Injected upload failure');
    }
    const index = find(this.files, relativePath);
    if (expectedRevision && (index < 0 || this.files[index].revision !== expectedRevision)) {
      throw new Error('Remote revision changed');
    }
    const content = await readAll(source);
    this.revision += 1;
    const revision = `r${this.revision}`;
    const file = new MemoryFile(relativePath, copy(content), revision);
    if (index >= 0) {
      this.files.splice(index, 1, file);
    } else {
      this.files.push(file);
    }
    return revision;
  }

  async delete(
    relativePath: string,
    expectedRevision?: string,
    operationId?: string
  ): Promise<void> {
    if (this.failWrites) {
      throw new Error('Injected delete failure');
    }
    const index = find(this.files, relativePath);
    if (expectedRevision && (index < 0 || this.files[index].revision !== expectedRevision)) {
      throw new Error('Remote revision changed');
    }
    if (index >= 0) {
      this.files.splice(index, 1);
    }
    this.revision += 1;
  }

  cancel(): void {
  }
}

class MemoryBaselineStore implements SyncBaselineStore {
  baseline?: SyncBaseline;
  commits: number = 0;

  constructor(baseline?: SyncBaseline) {
    this.baseline = baseline;
  }

  async load(providerId: string, notebookId: string): Promise<SyncBaseline | undefined> {
    return this.baseline;
  }

  async commit(baseline: SyncBaseline): Promise<void> {
    this.baseline = baseline;
    this.commits += 1;
  }
}

function oldBaseline(): SyncBaseline {
  return new SyncBaseline(
    'memory',
    'notebook',
    [new FileSnapshot('notes/a.md', 1, hash(bytes(1)), 1, 'r1')],
    'cursor-1',
    1
  );
}

export default function syncEngineTest(): void {
  describe('SyncEngine', () => {
    it('uploads a local change and commits the completed baseline', 0, async () => {
      const local = new MemoryLocalStore([new MemoryFile('notes/a.md', bytes(2))]);
      const provider = new MemoryProvider([new MemoryFile('notes/a.md', bytes(1), 'r1')]);
      const baselines = new MemoryBaselineStore(oldBaseline());

      const result = await new SyncEngine().sync(
        provider,
        local,
        baselines,
        'notebook',
        'phone'
      );

      expect(result.uploaded).assertEqual(1);
      expect(provider.files[0].content[0]).assertEqual(2);
      expect(baselines.commits).assertEqual(1);
    });

    it('keeps local and remote content after a conflict', 0, async () => {
      const local = new MemoryLocalStore([new MemoryFile('notes/a.md', bytes(2))]);
      const provider = new MemoryProvider([new MemoryFile('notes/a.md', bytes(3), 'r2')]);
      const baselines = new MemoryBaselineStore(oldBaseline());

      const result = await new SyncEngine().sync(
        provider,
        local,
        baselines,
        'notebook',
        'phone'
      );

      expect(result.conflicts).assertEqual(1);
      expect(local.files.length).assertEqual(2);
      expect(provider.files.length).assertEqual(2);
      expect(baselines.commits).assertEqual(1);
    });

    it('does not commit a baseline after an injected failure', 0, async () => {
      const local = new MemoryLocalStore([new MemoryFile('notes/a.md', bytes(2))]);
      const provider = new MemoryProvider([new MemoryFile('notes/a.md', bytes(1), 'r1')]);
      provider.failWrites = true;
      const baselines = new MemoryBaselineStore(oldBaseline());
      let failed: boolean = false;

      try {
        await new SyncEngine().sync(provider, local, baselines, 'notebook', 'phone');
      } catch (error) {
        failed = true;
      }

      expect(failed).assertTrue();
      expect(baselines.commits).assertEqual(0);
    });

    it('closes local content when conflict download fails', 0, async () => {
      const local = new MemoryLocalStore([new MemoryFile('notes/a.md', bytes(2))]);
      const provider = new MemoryProvider([new MemoryFile('notes/a.md', bytes(3), 'r2')]);
      provider.failDownloads = true;
      const baselines = new MemoryBaselineStore(oldBaseline());
      let failed: boolean = false;

      try {
        await new SyncEngine().sync(provider, local, baselines, 'notebook', 'phone');
      } catch (error) {
        failed = true;
      }

      expect(failed).assertTrue();
      expect(local.lastSource !== undefined && local.lastSource.closed).assertTrue();
      expect(baselines.commits).assertEqual(0);
    });
  });
}
