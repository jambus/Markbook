import {
  OneDriveAuthorizationTransaction,
  OneDriveOAuth,
  OneDriveOAuthConfig
} from './OneDriveOAuth';
import {
  OneDriveTokenClient,
  OneDriveTokenError,
  OneDriveTokenSet
} from './OneDriveTokenClient';

export interface RefreshTokenStore {
  load(): Promise<string | undefined>;
  save(refreshToken: string): Promise<void>;
  clear(): Promise<void>;
}

export class OneDriveSession {
  private readonly config: OneDriveOAuthConfig;
  private readonly tokenClient: OneDriveTokenClient;
  private readonly refreshTokens: RefreshTokenStore;
  private readonly now: () => number;
  private tokens?: OneDriveTokenSet;

  constructor(
    config: OneDriveOAuthConfig,
    tokenClient: OneDriveTokenClient,
    refreshTokens: RefreshTokenStore,
    now: () => number = () => Date.now()
  ) {
    this.config = config;
    this.tokenClient = tokenClient;
    this.refreshTokens = refreshTokens;
    this.now = now;
  }

  async completeAuthorization(
    callbackUri: string,
    transaction: OneDriveAuthorizationTransaction
  ): Promise<string> {
    const result = OneDriveOAuth.parseCallback(
      callbackUri,
      this.config.redirectUri,
      transaction.state
    );
    const tokens = await this.tokenClient.exchangeCode(
      this.config,
      result.code,
      transaction
    );
    if (!tokens.refreshToken) {
      throw new Error('Microsoft did not return a refresh token');
    }
    await this.refreshTokens.save(tokens.refreshToken);
    this.tokens = tokens;
    return tokens.accessToken;
  }

  async accessToken(): Promise<string> {
    if (this.tokens && this.tokens.isUsable(this.now())) {
      return this.tokens.accessToken;
    }
    const storedRefreshToken = await this.refreshTokens.load();
    if (!storedRefreshToken) {
      throw new Error('OneDrive authorization is required');
    }
    let refreshed: OneDriveTokenSet;
    try {
      refreshed = await this.tokenClient.refresh(this.config, storedRefreshToken);
    } catch (error) {
      if (
        error instanceof OneDriveTokenError &&
        error.requiresAuthorization()
      ) {
        this.tokens = undefined;
        await this.refreshTokens.clear();
        throw new Error('OneDrive authorization is required');
      }
      throw new Error('OneDrive token refresh failed');
    }
    const nextRefreshToken = refreshed.refreshToken ?? storedRefreshToken;
    if (nextRefreshToken !== storedRefreshToken) {
      await this.refreshTokens.save(nextRefreshToken);
    }
    this.tokens = new OneDriveTokenSet(
      refreshed.accessToken,
      nextRefreshToken,
      refreshed.expiresAt,
      refreshed.scopes
    );
    return refreshed.accessToken;
  }

  async disconnect(): Promise<void> {
    this.tokens = undefined;
    await this.refreshTokens.clear();
  }

  cancel(): void {
    this.tokenClient.cancel();
  }
}
