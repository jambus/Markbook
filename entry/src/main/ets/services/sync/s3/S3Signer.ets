import { HttpHeaders, HttpMethod } from '../http/HttpTransport';

export interface S3Crypto {
  encodeUtf8(value: string): Uint8Array;
  sha256(value: Uint8Array): Promise<Uint8Array>;
  hmacSha256(key: Uint8Array, value: Uint8Array): Promise<Uint8Array>;
}

export class S3Credentials {
  accessKeyId: string;
  secretAccessKey: string;
  sessionToken?: string;

  constructor(accessKeyId: string, secretAccessKey: string, sessionToken?: string) {
    if (accessKeyId.length === 0 || secretAccessKey.length === 0) {
      throw new Error('S3 access key and secret key are required');
    }
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.sessionToken = sessionToken;
  }
}

export class S3QueryParameter {
  name: string;
  value: string;

  constructor(name: string, value: string = '') {
    this.name = name;
    this.value = value;
  }
}

export class S3SignedRequest {
  authorization: string;
  amzDate: string;
  payloadHash: string;
  securityToken?: string;

  constructor(
    authorization: string,
    amzDate: string,
    payloadHash: string,
    securityToken?: string
  ) {
    this.authorization = authorization;
    this.amzDate = amzDate;
    this.payloadHash = payloadHash;
    this.securityToken = securityToken;
  }

  apply(headers: HttpHeaders): void {
    headers.authorization = this.authorization;
    headers.xAmzDate = this.amzDate;
    headers.xAmzContentSha256 = this.payloadHash;
    headers.xAmzSecurityToken = this.securityToken;
  }
}

export class S3Signer {
  private readonly region: string;
  private readonly host: string;
  private readonly credentials: S3Credentials;
  private readonly crypto: S3Crypto;

  constructor(
    region: string,
    host: string,
    credentials: S3Credentials,
    crypto: S3Crypto
  ) {
    if (region.length === 0 || host.length === 0) {
      throw new Error('S3 region and endpoint host are required');
    }
    this.region = region;
    this.host = host.toLowerCase();
    this.credentials = credentials;
    this.crypto = crypto;
  }

  async sign(
    method: HttpMethod,
    path: string,
    query: Array<S3QueryParameter>,
    body: Uint8Array,
    now: Date
  ): Promise<S3SignedRequest> {
    const amzDate = now.toISOString()
      .replace(/[:-]/g, '')
      .replace(/\.\d{3}Z$/, 'Z');
    const dateStamp = amzDate.substring(0, 8);
    const payloadHash = this.hex(await this.crypto.sha256(body));
    const signedHeaders = this.credentials.sessionToken ?
      'host;x-amz-content-sha256;x-amz-date;x-amz-security-token' :
      'host;x-amz-content-sha256;x-amz-date';
    let canonicalHeaders =
      `host:${this.host}\n` +
      `x-amz-content-sha256:${payloadHash}\n` +
      `x-amz-date:${amzDate}\n`;
    if (this.credentials.sessionToken) {
      canonicalHeaders += `x-amz-security-token:${this.credentials.sessionToken.trim()}\n`;
    }
    const canonicalRequest =
      `${method}\n` +
      `${S3Signer.encodePath(path)}\n` +
      `${S3Signer.encodeQuery(query)}\n` +
      `${canonicalHeaders}` +
      `${signedHeaders}\n` +
      `${payloadHash}`;
    const scope = `${dateStamp}/${this.region}/s3/aws4_request`;
    const stringToSign =
      `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n` +
      this.hex(await this.crypto.sha256(this.crypto.encodeUtf8(canonicalRequest)));
    const dateKey = await this.crypto.hmacSha256(
      this.crypto.encodeUtf8(`AWS4${this.credentials.secretAccessKey}`),
      this.crypto.encodeUtf8(dateStamp)
    );
    const regionKey = await this.crypto.hmacSha256(
      dateKey,
      this.crypto.encodeUtf8(this.region)
    );
    const serviceKey = await this.crypto.hmacSha256(
      regionKey,
      this.crypto.encodeUtf8('s3')
    );
    const signingKey = await this.crypto.hmacSha256(
      serviceKey,
      this.crypto.encodeUtf8('aws4_request')
    );
    const signature = this.hex(await this.crypto.hmacSha256(
      signingKey,
      this.crypto.encodeUtf8(stringToSign)
    ));
    return new S3SignedRequest(
      `AWS4-HMAC-SHA256 Credential=${this.credentials.accessKeyId}/${scope}, ` +
        `SignedHeaders=${signedHeaders}, Signature=${signature}`,
      amzDate,
      payloadHash,
      this.credentials.sessionToken
    );
  }

  static encodePath(path: string): string {
    if (!path.startsWith('/')) {
      throw new Error('S3 canonical path must be absolute');
    }
    return path.split('/')
      .map((segment: string) => S3Signer.encodeComponent(segment))
      .join('/');
  }

  static encodeQuery(query: Array<S3QueryParameter>): string {
    const encoded = query.map((item: S3QueryParameter) =>
      `${S3Signer.encodeComponent(item.name)}=${S3Signer.encodeComponent(item.value)}`
    );
    encoded.sort();
    return encoded.join('&');
  }

  static encodeComponent(value: string): string {
    return encodeURIComponent(value).replace(/[!'()*]/g, (character: string) =>
      `%${character.charCodeAt(0).toString(16).toUpperCase()}`
    );
  }

  private hex(value: Uint8Array): string {
    let result = '';
    for (let index = 0; index < value.length; index += 1) {
      result += value[index].toString(16).padStart(2, '0');
    }
    return result;
  }
}
