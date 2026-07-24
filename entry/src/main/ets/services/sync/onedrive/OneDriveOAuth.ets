export interface OAuthCrypto {
  randomBytes(length: number): Uint8Array;
  sha256(value: Uint8Array): Uint8Array;
  encodeUtf8(value: string): Uint8Array;
}

export class OneDriveOAuthConfig {
  readonly clientId: string;
  readonly redirectUri: string;
  readonly tenant: string;
  readonly scopes: Array<string>;

  constructor(
    clientId: string,
    redirectUri: string = 'markbook://oauth/onedrive',
    tenant: string = 'common',
    scopes: Array<string> = [
      'Files.ReadWrite.AppFolder',
      'offline_access'
    ]
  ) {
    if (clientId.trim().length === 0) {
      throw new Error('OneDrive client ID is required');
    }
    if (!redirectUri.startsWith('markbook://')) {
      throw new Error('OneDrive redirect URI must use the Markbook scheme');
    }
    if (!/^[A-Za-z0-9.-]+$/.test(tenant)) {
      throw new Error('Invalid Microsoft tenant');
    }
    if (scopes.length === 0) {
      throw new Error('At least one OneDrive scope is required');
    }
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.tenant = tenant;
    this.scopes = scopes.slice();
  }

  authorizationEndpoint(): string {
    return `https://login.microsoftonline.com/${this.tenant}/oauth2/v2.0/authorize`;
  }

  tokenEndpoint(): string {
    return `https://login.microsoftonline.com/${this.tenant}/oauth2/v2.0/token`;
  }
}

export class OneDriveAuthorizationTransaction {
  readonly authorizationUrl: string;
  readonly state: string;
  readonly codeVerifier: string;

  constructor(authorizationUrl: string, state: string, codeVerifier: string) {
    this.authorizationUrl = authorizationUrl;
    this.state = state;
    this.codeVerifier = codeVerifier;
  }
}

export class OneDriveAuthorizationCode {
  readonly code: string;

  constructor(code: string) {
    this.code = code;
  }
}

export class OneDriveOAuth {
  static begin(config: OneDriveOAuthConfig, crypto: OAuthCrypto): OneDriveAuthorizationTransaction {
    const state = OneDriveOAuth.base64Url(crypto.randomBytes(32));
    const verifier = OneDriveOAuth.base64Url(crypto.randomBytes(64));
    const challenge = OneDriveOAuth.base64Url(
      crypto.sha256(crypto.encodeUtf8(verifier))
    );
    const query = [
      OneDriveOAuth.field('client_id', config.clientId),
      OneDriveOAuth.field('response_type', 'code'),
      OneDriveOAuth.field('redirect_uri', config.redirectUri),
      OneDriveOAuth.field('response_mode', 'query'),
      OneDriveOAuth.field('scope', config.scopes.join(' ')),
      OneDriveOAuth.field('state', state),
      OneDriveOAuth.field('code_challenge', challenge),
      OneDriveOAuth.field('code_challenge_method', 'S256')
    ].join('&');
    return new OneDriveAuthorizationTransaction(
      `${config.authorizationEndpoint()}?${query}`,
      state,
      verifier
    );
  }

  static parseCallback(
    callbackUri: string,
    redirectUri: string,
    expectedState: string
  ): OneDriveAuthorizationCode {
    const prefix = `${redirectUri}?`;
    if (!callbackUri.startsWith(prefix)) {
      throw new Error('Unexpected OneDrive OAuth redirect URI');
    }
    const values = OneDriveOAuth.parseQuery(callbackUri.substring(prefix.length));
    const actualState = OneDriveOAuth.find(values, 'state');
    if (!actualState || actualState !== expectedState) {
      throw new Error('OneDrive OAuth state does not match');
    }
    const error = OneDriveOAuth.find(values, 'error');
    if (error) {
      const description = OneDriveOAuth.find(values, 'error_description');
      throw new Error(description ? `${error}: ${description}` : error);
    }
    const code = OneDriveOAuth.find(values, 'code');
    if (!code) {
      throw new Error('OneDrive OAuth callback does not contain a code');
    }
    return new OneDriveAuthorizationCode(code);
  }

  static base64Url(value: Uint8Array): string {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
    let result = '';
    for (let index = 0; index < value.length; index += 3) {
      const first = value[index];
      const hasSecond = index + 1 < value.length;
      const hasThird = index + 2 < value.length;
      const second = hasSecond ? value[index + 1] : 0;
      const third = hasThird ? value[index + 2] : 0;
      result += alphabet.charAt(first >> 2);
      result += alphabet.charAt(((first & 0x03) << 4) | (second >> 4));
      if (hasSecond) {
        result += alphabet.charAt(((second & 0x0f) << 2) | (third >> 6));
      }
      if (hasThird) {
        result += alphabet.charAt(third & 0x3f);
      }
    }
    return result;
  }

  static form(fields: Array<Array<string>>): string {
    return fields.map((entry: Array<string>) => {
      return OneDriveOAuth.field(entry[0], entry[1]);
    }).join('&');
  }

  private static field(name: string, value: string): string {
    return `${encodeURIComponent(name)}=${encodeURIComponent(value)}`;
  }

  private static parseQuery(query: string): Array<Array<string>> {
    const result: Array<Array<string>> = [];
    if (query.length === 0) {
      return result;
    }
    query.split('&').forEach((entry: string) => {
      const separator = entry.indexOf('=');
      const rawName = separator >= 0 ? entry.substring(0, separator) : entry;
      const rawValue = separator >= 0 ? entry.substring(separator + 1) : '';
      const name = decodeURIComponent(rawName.replace(/\+/g, ' '));
      if (OneDriveOAuth.find(result, name) !== undefined) {
        throw new Error(`Duplicate OneDrive OAuth parameter: ${name}`);
      }
      result.push([
        name,
        decodeURIComponent(rawValue.replace(/\+/g, ' '))
      ]);
    });
    return result;
  }

  private static find(values: Array<Array<string>>, name: string): string | undefined {
    for (let index = 0; index < values.length; index++) {
      if (values[index][0] === name) {
        return values[index][1];
      }
    }
    return undefined;
  }
}
