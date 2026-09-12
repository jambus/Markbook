import {
  HttpHeaders,
  HttpMethod,
  HttpRequestData,
  HttpResponseType
} from '../http/HttpTransport';
import type { HttpTransport } from '../http/HttpTransport';
import type { TextCodec } from '../http/TextCodec';
import {
  OneDriveAuthorizationTransaction,
  OneDriveOAuth,
  OneDriveOAuthConfig
} from './OneDriveOAuth';

class TokenJson {
  token_type?: string;
  scope?: string;
  expires_in?: number;
  access_token?: string;
  refresh_token?: string;
  error?: string;
  error_description?: string;
}

export class OneDriveTokenSet {
  readonly accessToken: string;
  readonly refreshToken?: string;
  readonly expiresAt: number;
  readonly scopes: Array<string>;

  constructor(
    accessToken: string,
    refreshToken: string | undefined,
    expiresAt: number,
    scopes: Array<string>
  ) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.expiresAt = expiresAt;
    this.scopes = scopes;
  }

  isUsable(now: number, minimumLifetimeMs: number = 60000): boolean {
    return this.expiresAt - now > minimumLifetimeMs;
  }
}

export class OneDriveTokenError extends Error {
  readonly oauthError?: string;

  constructor(message: string, oauthError?: string) {
    super(message);
    this.oauthError = oauthError;
  }

  requiresAuthorization(): boolean {
    return this.oauthError === 'invalid_grant' ||
      this.oauthError === 'interaction_required';
  }
}

export class OneDriveTokenClient {
  private readonly transport: HttpTransport;
  private readonly codec: TextCodec;
  private readonly now: () => number;

  constructor(
    transport: HttpTransport,
    codec: TextCodec,
    now: () => number = () => Date.now()
  ) {
    this.transport = transport;
    this.codec = codec;
    this.now = now;
  }

  exchangeCode(
    config: OneDriveOAuthConfig,
    code: string,
    transaction: OneDriveAuthorizationTransaction
  ): Promise<OneDriveTokenSet> {
    return this.request(config, [
      ['client_id', config.clientId],
      ['scope', config.scopes.join(' ')],
      ['code', code],
      ['redirect_uri', config.redirectUri],
      ['grant_type', 'authorization_code'],
      ['code_verifier', transaction.codeVerifier]
    ]);
  }

  refresh(config: OneDriveOAuthConfig, refreshToken: string): Promise<OneDriveTokenSet> {
    return this.request(config, [
      ['client_id', config.clientId],
      ['scope', config.scopes.join(' ')],
      ['refresh_token', refreshToken],
      ['grant_type', 'refresh_token']
    ]);
  }

  cancel(): void {
    this.transport.cancel();
  }

  private async request(
    config: OneDriveOAuthConfig,
    fields: Array<Array<string>>
  ): Promise<OneDriveTokenSet> {
    const headers = new HttpHeaders();
    headers.accept = 'application/json';
    headers.contentType = 'application/x-www-form-urlencoded';
    const body = this.codec.encode(OneDriveOAuth.form(fields));
    headers.contentLength = `${body.length}`;
    const response = await this.transport.request(new HttpRequestData(
      HttpMethod.POST,
      config.tokenEndpoint(),
      headers,
      HttpResponseType.TEXT,
      body
    ));
    const parsed = JSON.parse(response.text ?? '{}') as TokenJson;
    if (response.status < 200 || response.status >= 300) {
      const summary = parsed.error_description ?? parsed.error ??
        `HTTP ${response.status}`;
      throw new OneDriveTokenError(
        `OneDrive token request failed: ${summary}`,
        parsed.error
      );
    }
    if (!parsed.access_token || !parsed.expires_in || parsed.expires_in <= 0) {
      throw new Error('OneDrive token response is incomplete');
    }
    return new OneDriveTokenSet(
      parsed.access_token,
      parsed.refresh_token,
      this.now() + parsed.expires_in * 1000,
      parsed.scope ? parsed.scope.split(' ') : config.scopes.slice()
    );
  }
}
