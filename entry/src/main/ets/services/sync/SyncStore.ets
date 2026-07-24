import type { FileSnapshot, SyncBaseline } from './SyncModels';
import type { SyncContentSource } from './SyncContent';

export interface LocalSyncStore {
  listSnapshots(): Promise<Array<FileSnapshot>>;
  openRead(relativePath: string): Promise<SyncContentSource>;
  writeFrom(relativePath: string, content: SyncContentSource): Promise<void>;
  delete(relativePath: string): Promise<void>;
}

export interface SyncBaselineStore {
  load(providerId: string, notebookId: string): Promise<SyncBaseline | undefined>;
  commit(baseline: SyncBaseline): Promise<void>;
}
