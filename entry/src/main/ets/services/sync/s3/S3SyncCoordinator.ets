import { NotebookRepository } from '../../NotebookRepository';
import { FileSyncBaselineStore } from '../FileSyncBaselineStore';
import { LocalNotebookSyncStore } from '../LocalNotebookSyncStore';
import { SyncEngine } from '../SyncEngine';
import type { SyncProgressListener } from '../SyncModels';
import type { SyncResult } from '../SyncProvider';
import { NetworkHttpTransport } from '../http/NetworkHttpTransport';
import { HarmonyS3Crypto } from './HarmonyS3Crypto';
import {
  S3Configuration,
  S3Provider
} from './S3Provider';
import { S3Credentials } from './S3Signer';

export class S3SyncCoordinator {
  private readonly repository: NotebookRepository;
  private readonly configuration: S3Configuration;
  private readonly credentials: S3Credentials;
  private readonly engine: SyncEngine = new SyncEngine();
  private provider?: S3Provider;

  constructor(
    repository: NotebookRepository,
    configuration: S3Configuration,
    credentials: S3Credentials
  ) {
    this.repository = repository;
    this.configuration = configuration;
    this.credentials = credentials;
  }

  async connect(): Promise<void> {
    const provider = this.createProvider();
    await provider.connect();
    this.provider = provider;
  }

  async sync(progressListener?: SyncProgressListener): Promise<SyncResult> {
    const provider = this.createProvider();
    this.provider = provider;
    try {
      return await this.engine.sync(
        provider,
        new LocalNotebookSyncStore(
          this.repository.rootDir,
          this.repository.tempDir
        ),
        new FileSyncBaselineStore(this.repository.metadataDir),
        'default',
        'HarmonyOS',
        progressListener
      );
    } finally {
      this.provider = undefined;
    }
  }

  cancel(): void {
    this.engine.cancel();
    if (this.provider) {
      this.provider.cancel();
    }
  }

  private createProvider(): S3Provider {
    return new S3Provider(
      new NetworkHttpTransport(),
      this.configuration,
      this.credentials,
      new HarmonyS3Crypto()
    );
  }
}
