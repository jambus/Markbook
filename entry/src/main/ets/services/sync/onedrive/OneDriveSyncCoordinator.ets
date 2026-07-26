import { common } from '@kit.AbilityKit';
import { NotebookRepository } from '../../NotebookRepository';
import { FileSyncBaselineStore } from '../FileSyncBaselineStore';
import { LocalNotebookSyncStore } from '../LocalNotebookSyncStore';
import { SyncEngine } from '../SyncEngine';
import type { SyncProgressListener } from '../SyncModels';
import type { SyncResult } from '../SyncProvider';
import { ArkTextCodec } from '../http/ArkTextCodec';
import { NetworkHttpTransport } from '../http/NetworkHttpTransport';
import { HarmonyOAuthCrypto } from './HarmonyOAuthCrypto';
import { MemoryRefreshTokenStore } from './MemoryRefreshTokenStore';
import {
  OneDriveAuthorizationTransaction,
  OneDriveOAuth,
  OneDriveOAuthConfig
} from './OneDriveOAuth';
import { OneDriveProvider } from './OneDriveProvider';
import { OneDriveSession } from './OneDriveSession';
import { OneDriveTokenClient } from './OneDriveTokenClient';
import { SystemBrowser } from './SystemBrowser';

export class OneDriveSyncCoordinator {
  private readonly repository: NotebookRepository;
  private readonly config?: OneDriveOAuthConfig;
  private readonly session?: OneDriveSession;
  private readonly browser: SystemBrowser;
  private readonly crypto: HarmonyOAuthCrypto;
  private readonly codec: ArkTextCodec;
  private readonly engine: SyncEngine;
  private transaction?: OneDriveAuthorizationTransaction;

  constructor(
    context: common.UIAbilityContext,
    repository: NotebookRepository,
    clientId: string
  ) {
    this.repository = repository;
    this.browser = new SystemBrowser(context);
    this.crypto = new HarmonyOAuthCrypto();
    this.codec = new ArkTextCodec();
    this.engine = new SyncEngine();
    if (
      clientId.length > 0 &&
      clientId !== 'REPLACE_WITH_ENTRA_CLIENT_ID'
    ) {
      this.config = new OneDriveOAuthConfig(clientId);
      this.session = new OneDriveSession(
        this.config,
        new OneDriveTokenClient(new NetworkHttpTransport(), this.codec),
        new MemoryRefreshTokenStore()
      );
    }
  }

  isConfigured(): boolean {
    return this.config !== undefined;
  }

  async beginAuthorization(): Promise<void> {
    const config = this.requireConfig();
    this.transaction = OneDriveOAuth.begin(config, this.crypto);
    await this.browser.open(this.transaction.authorizationUrl);
  }

  async completeAuthorization(callbackUri: string): Promise<void> {
    const session = this.requireSession();
    const transaction = this.transaction;
    if (!transaction) {
      throw new Error('OneDrive authorization request has expired');
    }
    try {
      const accessToken = await session.completeAuthorization(
        callbackUri,
        transaction
      );
      await this.verify(accessToken);
    } finally {
      this.transaction = undefined;
    }
  }

  async restoreSession(): Promise<boolean> {
    if (!this.session) {
      return false;
    }
    try {
      const accessToken = await this.session.accessToken();
      await this.verify(accessToken);
      return true;
    } catch (error) {
      return false;
    }
  }

  async sync(progressListener?: SyncProgressListener): Promise<SyncResult> {
    const accessToken = await this.requireSession().accessToken();
    const provider = new OneDriveProvider(
      new NetworkHttpTransport(),
      accessToken,
      this.codec
    );
    return this.engine.sync(
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
  }

  async disconnect(): Promise<void> {
    this.engine.cancel();
    if (this.session) {
      await this.session.disconnect();
    }
  }

  cancel(): void {
    this.transaction = undefined;
    this.engine.cancel();
    if (this.session) {
      this.session.cancel();
    }
  }

  private async verify(accessToken: string): Promise<void> {
    const provider = new OneDriveProvider(
      new NetworkHttpTransport(),
      accessToken,
      this.codec
    );
    await provider.connect();
  }

  private requireConfig(): OneDriveOAuthConfig {
    if (!this.config) {
      throw new Error('OneDrive client ID is not configured');
    }
    return this.config;
  }

  private requireSession(): OneDriveSession {
    if (!this.session) {
      throw new Error('OneDrive client ID is not configured');
    }
    return this.session;
  }
}
