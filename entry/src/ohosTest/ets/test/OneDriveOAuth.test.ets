import { describe, expect, it } from '@ohos/hypium';
import {
  HttpRequestData,
  HttpResponseData
} from '../../../main/ets/services/sync/http/HttpTransport';
import type { HttpTransport } from '../../../main/ets/services/sync/http/HttpTransport';
import type { TextCodec } from '../../../main/ets/services/sync/http/TextCodec';
import {
  OneDriveAuthorizationTransaction,
  OneDriveOAuth,
  OneDriveOAuthConfig
} from '../../../main/ets/services/sync/onedrive/OneDriveOAuth';
import type {
  OAuthCrypto
} from '../../../main/ets/services/sync/onedrive/OneDriveOAuth';
import {
  OAuthCallbackInbox
} from '../../../main/ets/services/sync/onedrive/OAuthCallbackInbox';
import {
  OneDriveTokenClient
} from '../../../main/ets/services/sync/onedrive/OneDriveTokenClient';
import {
  OneDriveSession
} from '../../../main/ets/services/sync/onedrive/OneDriveSession';
import type {
  RefreshTokenStore
} from '../../../main/ets/services/sync/onedrive/OneDriveSession';

class TestOAuthCrypto implements OAuthCrypto {
  private seed: number = 0;

  randomBytes(length: number): Uint8Array {
    const result = new Uint8Array(length);
    for (let index = 0; index < length; index++) {
      result[index] = (this.seed + index) & 0xff;
    }
    this.seed += length;
    return result;
  }

  sha256(value: Uint8Array): Uint8Array {
    const result = new Uint8Array(32);
    result.fill(value.length);
    return result;
  }

  encodeUtf8(value: string): Uint8Array {
    return asciiEncode(value);
  }
}

class TestTextCodec implements TextCodec {
  encode(value: string): Uint8Array {
    return asciiEncode(value);
  }
}

class MockHttpTransport implements HttpTransport {
  readonly requests: Array<HttpRequestData> = [];
  readonly responses: Array<HttpResponseData> = [];
  cancelled: boolean = false;

  async request(request: HttpRequestData): Promise<HttpResponseData> {
    this.requests.push(request);
    return this.responses.shift() as HttpResponseData;
  }

  cancel(): void {
    this.cancelled = true;
  }
}

class MemoryRefreshTokenStore implements RefreshTokenStore {
  value?: string;
  saves: number = 0;

  async load(): Promise<string | undefined> {
    return this.value;
  }

  async save(refreshToken: string): Promise<void> {
    this.value = refreshToken;
    this.saves += 1;
  }

  async clear(): Promise<void> {
    this.value = undefined;
  }
}

function asciiEncode(value: string): Uint8Array {
  const result = new Uint8Array(value.length);
  for (let index = 0; index < value.length; index++) {
    result[index] = value.charCodeAt(index);
  }
  return result;
}

function asciiDecode(value?: Uint8Array): string {
  if (!value) {
    return '';
  }
  let result = '';
  for (let index = 0; index < value.length; index++) {
    result += String.fromCharCode(value[index]);
  }
  return result;
}

function transaction(): OneDriveAuthorizationTransaction {
  return new OneDriveAuthorizationTransaction(
    'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
    'expected-state',
    'code-verifier'
  );
}

export default function oneDriveOAuthTest(): void {
  describe('OneDriveOAuth', () => {
    it('creates a PKCE authorization request without a client secret', 0, () => {
      const config = new OneDriveOAuthConfig('client-id');
      const result = OneDriveOAuth.begin(config, new TestOAuthCrypto());

      expect(result.codeVerifier.length).assertEqual(86);
      expect(result.authorizationUrl.includes('code_challenge_method=S256')).assertTrue();
      expect(result.authorizationUrl.includes('Files.ReadWrite.AppFolder')).assertTrue();
      expect(result.authorizationUrl.includes('offline_access')).assertTrue();
      expect(result.authorizationUrl.includes('client_secret')).assertFalse();
    });

    it('encodes PKCE bytes without padding', 0, () => {
      expect(OneDriveOAuth.base64Url(new Uint8Array([102, 111, 111])))
        .assertEqual('Zm9v');
      expect(OneDriveOAuth.base64Url(new Uint8Array([255, 254])))
        .assertEqual('__4');
    });

    it('parses only the expected redirect and state', 0, () => {
      const result = OneDriveOAuth.parseCallback(
        'markbook://oauth/onedrive?code=abc%20123&state=expected-state',
        'markbook://oauth/onedrive',
        'expected-state'
      );

      expect(result.code).assertEqual('abc 123');
    });

    it('rejects a forged state and duplicate parameters', 0, () => {
      let forgedRejected = false;
      let duplicateRejected = false;
      try {
        OneDriveOAuth.parseCallback(
          'markbook://oauth/onedrive?code=abc&state=forged',
          'markbook://oauth/onedrive',
          'expected-state'
        );
      } catch (error) {
        forgedRejected = true;
      }
      try {
        OneDriveOAuth.parseCallback(
          'markbook://oauth/onedrive?code=abc&code=other&state=expected-state',
          'markbook://oauth/onedrive',
          'expected-state'
        );
      } catch (error) {
        duplicateRejected = true;
      }

      expect(forgedRejected).assertTrue();
      expect(duplicateRejected).assertTrue();
    });

    it('delivers an active callback once without retaining it', 0, () => {
      const inbox = OAuthCallbackInbox.shared();
      inbox.take();
      let received = '';
      const listener = (uri: string): void => {
        received = uri;
      };
      inbox.subscribe(listener);

      inbox.push('markbook://oauth/onedrive?code=abc&state=state');
      inbox.unsubscribe(listener);

      expect(received.includes('code=abc')).assertTrue();
      expect(inbox.take()).assertUndefined();
    });

    it('exchanges an authorization code with form encoding', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(new HttpResponseData(200, JSON.stringify({
        token_type: 'Bearer',
        scope: 'Files.ReadWrite.AppFolder offline_access',
        expires_in: 3600,
        access_token: 'access-1',
        refresh_token: 'refresh-1'
      })));
      const client = new OneDriveTokenClient(
        transport,
        new TestTextCodec(),
        () => 1000
      );

      const tokens = await client.exchangeCode(
        new OneDriveOAuthConfig('client-id'),
        'code value',
        transaction()
      );
      const body = asciiDecode(transport.requests[0].body);

      expect(tokens.accessToken).assertEqual('access-1');
      expect(tokens.expiresAt).assertEqual(3600000 + 1000);
      expect(body.includes('grant_type=authorization_code')).assertTrue();
      expect(body.includes('code=code%20value')).assertTrue();
      expect(body.includes('code_verifier=code-verifier')).assertTrue();
    });

    it('rotates refresh tokens and reuses an unexpired access token', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(new HttpResponseData(200, JSON.stringify({
        expires_in: 3600,
        access_token: 'access-2',
        refresh_token: 'refresh-2'
      })));
      const store = new MemoryRefreshTokenStore();
      store.value = 'refresh-1';
      const client = new OneDriveTokenClient(
        transport,
        new TestTextCodec(),
        () => 1000
      );
      const session = new OneDriveSession(
        new OneDriveOAuthConfig('client-id'),
        client,
        store,
        () => 1000
      );

      const first = await session.accessToken();
      const second = await session.accessToken();

      expect(first).assertEqual('access-2');
      expect(second).assertEqual('access-2');
      expect(store.value).assertEqual('refresh-2');
      expect(store.saves).assertEqual(1);
      expect(transport.requests.length).assertEqual(1);
    });

    it('removes a refresh token rejected by Microsoft', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(new HttpResponseData(400, JSON.stringify({
        error: 'invalid_grant',
        error_description: 'The refresh token is no longer valid.'
      })));
      const store = new MemoryRefreshTokenStore();
      store.value = 'expired-refresh-token';
      const session = new OneDriveSession(
        new OneDriveOAuthConfig('client-id'),
        new OneDriveTokenClient(transport, new TestTextCodec()),
        store
      );
      let rejected = false;

      try {
        await session.accessToken();
      } catch (error) {
        rejected = true;
      }

      expect(rejected).assertTrue();
      expect(store.value).assertUndefined();
    });
  });
}
