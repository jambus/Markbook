import type { SyncContentSource } from '../SyncContent';
import { FileSnapshot } from '../SyncModels';
import type { ProviderFolderSnapshot } from '../SyncModels';
import { SyncPath } from '../SyncPath';
import { ProviderChangePage } from '../SyncProvider';
import type { SyncProvider } from '../SyncProvider';
import {
  HttpHeaders,
  HttpMethod,
  HttpRequestData,
  HttpResponseData,
  HttpResponseType
} from '../http/HttpTransport';
import type { HttpTransport } from '../http/HttpTransport';
import {
  S3Credentials,
  S3QueryParameter,
  S3Signer
} from './S3Signer';
import type { S3Crypto } from './S3Signer';
import { S3ListResult, S3ObjectInfo, S3XmlCodec } from './S3XmlCodec';

const READ_CHUNK_SIZE = 1024 * 1024;
const MAX_SINGLE_UPLOAD = 64 * 1024 * 1024;
const FULL_SCAN_CURSOR = 's3-full-scan-v1';
const PAGE_CURSOR_PREFIX = 's3-page:';

export interface S3Clock {
  now(): Date;
}

export class SystemS3Clock implements S3Clock {
  now(): Date {
    return new Date();
  }
}

export class S3Configuration {
  endpoint: string;
  host: string;
  region: string;
  bucket: string;
  prefix: string;

  constructor(
    endpoint: string,
    region: string,
    bucket: string,
    prefix: string = 'Markbook'
  ) {
    const normalizedEndpoint = endpoint.replace(/\/+$/, '');
    const match = /^https:\/\/([^/]+)$/i.exec(normalizedEndpoint);
    if (!match) {
      throw new Error('S3 endpoint must be an HTTPS origin without a path');
    }
    if (region.length === 0 || bucket.length === 0 || bucket.includes('/')) {
      throw new Error('S3 region and bucket are required');
    }
    const normalizedPrefix = prefix.replace(/^\/+|\/+$/g, '');
    if (normalizedPrefix.length === 0) {
      throw new Error('S3 notebook prefix is required');
    }
    this.endpoint = normalizedEndpoint;
    this.host = match[1];
    this.region = region;
    this.bucket = bucket;
    this.prefix = normalizedPrefix;
  }

  providerId(): string {
    const identity = `${this.endpoint}|${this.region}|${this.bucket}|${this.prefix}`;
    let first: number = 2166136261;
    let second: number = 2246822519;
    for (let index = 0; index < identity.length; index += 1) {
      const value = identity.charCodeAt(index);
      first = Math.imul(first ^ value, 16777619) >>> 0;
      second = Math.imul(second ^ value, 3266489917) >>> 0;
    }
    return `s3-${this.hex(first)}${this.hex(second)}`;
  }

  private hex(value: number): string {
    return value.toString(16).padStart(8, '0');
  }
}

class S3RequestClient {
  private readonly transport: HttpTransport;
  private readonly configuration: S3Configuration;
  private readonly signer: S3Signer;
  private readonly clock: S3Clock;

  constructor(
    transport: HttpTransport,
    configuration: S3Configuration,
    credentials: S3Credentials,
    crypto: S3Crypto,
    clock: S3Clock
  ) {
    this.transport = transport;
    this.configuration = configuration;
    this.signer = new S3Signer(
      configuration.region,
      configuration.host,
      credentials,
      crypto
    );
    this.clock = clock;
  }

  async request(
    method: HttpMethod,
    key: string | undefined,
    query: Array<S3QueryParameter> = [],
    headers: HttpHeaders = new HttpHeaders(),
    responseType: HttpResponseType = HttpResponseType.TEXT,
    body: Uint8Array = new Uint8Array(0)
  ): Promise<HttpResponseData> {
    const path = key === undefined ?
      `/${this.configuration.bucket}` :
      `/${this.configuration.bucket}/${key}`;
    const signed = await this.signer.sign(method, path, query, body, this.clock.now());
    signed.apply(headers);
    const queryText = S3Signer.encodeQuery(query);
    const url = `${this.configuration.endpoint}${S3Signer.encodePath(path)}` +
      (queryText.length > 0 ? `?${queryText}` : '');
    return this.transport.request(new HttpRequestData(
      method,
      url,
      headers,
      responseType,
      body.length > 0 ? body : undefined
    ));
  }
}

class S3ContentSource implements SyncContentSource {
  readonly size: number;
  private readonly client: S3RequestClient;
  private readonly key: string;
  private readonly revision: string;
  private closed: boolean = false;

  constructor(client: S3RequestClient, key: string, size: number, revision: string) {
    this.client = client;
    this.key = key;
    this.size = size;
    this.revision = revision;
  }

  async read(offset: number, maxBytes: number): Promise<Uint8Array> {
    if (this.closed) {
      throw new Error('Cannot read a closed S3 content source');
    }
    if (offset < 0 || maxBytes <= 0 || offset > this.size) {
      throw new Error('Invalid S3 content range');
    }
    const length = Math.min(maxBytes, this.size - offset);
    if (length === 0) {
      return new Uint8Array(0);
    }
    const headers = new HttpHeaders();
    headers.accept = 'application/octet-stream';
    headers.ifMatch = this.revision;
    headers.range = `bytes=${offset}-${offset + length - 1}`;
    const response = await this.client.request(
      HttpMethod.GET,
      this.key,
      [],
      headers,
      HttpResponseType.BINARY
    );
    if (response.status !== 206 && !(response.status === 200 && length === this.size)) {
      throw new Error(`S3 content download failed with HTTP ${response.status}`);
    }
    if (!response.body || response.body.length !== length) {
      throw new Error('S3 content response length does not match the requested range');
    }
    return response.body;
  }

  close(): void {
    this.closed = true;
  }
}

export class S3Provider implements SyncProvider {
  readonly id: string;
  readonly displayName: string = 'S3-compatible NAS';
  private readonly transport: HttpTransport;
  private readonly configuration: S3Configuration;
  private readonly client: S3RequestClient;
  private readonly snapshots: Array<FileSnapshot> = [];
  private readonly seenPaths: Array<string> = [];

  constructor(
    transport: HttpTransport,
    configuration: S3Configuration,
    credentials: S3Credentials,
    crypto: S3Crypto,
    clock: S3Clock = new SystemS3Clock()
  ) {
    this.id = configuration.providerId();
    this.transport = transport;
    this.configuration = configuration;
    this.client = new S3RequestClient(
      transport,
      configuration,
      credentials,
      crypto,
      clock
    );
  }

  async connect(): Promise<void> {
    const response = await this.listRequest(undefined, 1);
    this.requireStatus(response, [200], 'connect');
  }

  async listChanges(
    cursor?: string,
    baselineFiles: Array<FileSnapshot> = [],
    baselineFolders: Array<ProviderFolderSnapshot> = []
  ): Promise<ProviderChangePage> {
    const continuationToken = this.readContinuationToken(cursor);
    if (!continuationToken) {
      this.snapshots.length = 0;
      this.seenPaths.length = 0;
    }
    const response = await this.listRequest(continuationToken);
    this.requireStatus(response, [200], 'list objects');
    const list = S3XmlCodec.parseList(this.requireText(response));
    const changes = this.toSnapshots(list);
    this.snapshots.push(...changes);
    changes.forEach((file: FileSnapshot) => this.seenPaths.push(file.relativePath));

    if (!list.truncated) {
      baselineFiles.forEach((file: FileSnapshot) => {
        if (this.seenPaths.indexOf(file.relativePath) < 0) {
          changes.push(new FileSnapshot(
            file.relativePath,
            0,
            undefined,
            Date.now(),
            file.providerRevision,
            true
          ));
        }
      });
    }
    const nextCursor = list.truncated ?
      `${PAGE_CURSOR_PREFIX}${list.continuationToken}` :
      FULL_SCAN_CURSOR;
    return new ProviderChangePage(changes, nextCursor, list.truncated);
  }

  async download(
    relativePath: string,
    expectedRevision?: string
  ): Promise<SyncContentSource> {
    const normalized = SyncPath.normalize(relativePath);
    const snapshot = this.findSnapshot(normalized);
    const revision = expectedRevision ?? snapshot.providerRevision;
    if (!revision) {
      throw new Error(`S3 revision is unavailable for ${normalized}`);
    }
    if (expectedRevision && snapshot.providerRevision !== expectedRevision) {
      throw new Error(`S3 revision changed for ${normalized}`);
    }
    return new S3ContentSource(
      this.client,
      this.objectKey(normalized),
      snapshot.size,
      revision
    );
  }

  async upload(
    relativePath: string,
    content: SyncContentSource,
    expectedRevision?: string,
    operationId?: string
  ): Promise<string> {
    const normalized = SyncPath.normalize(relativePath);
    if (content.size > MAX_SINGLE_UPLOAD) {
      throw new Error('S3 object exceeds the first-version 64 MiB upload limit');
    }
    const body = await this.readAll(content);
    const headers = new HttpHeaders();
    headers.contentType = 'application/octet-stream';
    headers.contentLength = `${body.length}`;
    if (expectedRevision) {
      headers.ifMatch = expectedRevision;
    } else {
      headers.ifNoneMatch = '*';
    }
    const response = await this.client.request(
      HttpMethod.PUT,
      this.objectKey(normalized),
      [],
      headers,
      HttpResponseType.TEXT,
      body
    );
    this.requireStatus(response, [200], 'upload');
    const revision = response.etag ?? await this.readRevision(normalized);
    this.replaceSnapshot(new FileSnapshot(
      normalized,
      content.size,
      undefined,
      Date.now(),
      revision
    ));
    return revision;
  }

  async delete(
    relativePath: string,
    expectedRevision?: string,
    operationId?: string
  ): Promise<void> {
    const normalized = SyncPath.normalize(relativePath);
    const headers = new HttpHeaders();
    headers.ifMatch = expectedRevision;
    const response = await this.client.request(
      HttpMethod.DELETE,
      this.objectKey(normalized),
      [],
      headers
    );
    this.requireStatus(response, [200, 204], 'delete');
    const index = this.snapshotIndex(normalized);
    if (index >= 0) {
      this.snapshots.splice(index, 1);
    }
  }

  cancel(): void {
    this.transport.cancel();
  }

  private async listRequest(
    continuationToken?: string,
    maxKeys: number = 1000
  ): Promise<HttpResponseData> {
    const query: Array<S3QueryParameter> = [
      new S3QueryParameter('list-type', '2'),
      new S3QueryParameter('max-keys', `${maxKeys}`),
      new S3QueryParameter('prefix', `${this.configuration.prefix}/`)
    ];
    if (continuationToken) {
      query.push(new S3QueryParameter('continuation-token', continuationToken));
    }
    return this.client.request(HttpMethod.GET, undefined, query);
  }

  private toSnapshots(list: S3ListResult): Array<FileSnapshot> {
    const snapshots: Array<FileSnapshot> = [];
    list.objects.forEach((item: S3ObjectInfo) => {
      const relativePath = item.key.substring(this.configuration.prefix.length + 1);
      if (relativePath.length === 0) {
        return;
      }
      if (
        !relativePath.startsWith('notes/') &&
        !relativePath.startsWith('attachments/')
      ) {
        return;
      }
      const normalized = SyncPath.normalize(relativePath);
      snapshots.push(new FileSnapshot(
        normalized,
        item.size,
        undefined,
        item.modifiedAt,
        item.etag
      ));
    });
    return snapshots;
  }

  private readContinuationToken(cursor?: string): string | undefined {
    if (!cursor || cursor === FULL_SCAN_CURSOR) {
      return undefined;
    }
    if (!cursor.startsWith(PAGE_CURSOR_PREFIX)) {
      throw new Error('Invalid S3 synchronization cursor');
    }
    const token = cursor.substring(PAGE_CURSOR_PREFIX.length);
    if (token.length === 0) {
      throw new Error('Invalid S3 continuation token');
    }
    return token;
  }

  private objectKey(relativePath: string): string {
    return `${this.configuration.prefix}/${relativePath}`;
  }

  private findSnapshot(relativePath: string): FileSnapshot {
    const index = this.snapshotIndex(relativePath);
    if (index < 0) {
      throw new Error(`S3 object metadata is unavailable for ${relativePath}`);
    }
    return this.snapshots[index];
  }

  private snapshotIndex(relativePath: string): number {
    return this.snapshots.findIndex((file: FileSnapshot) =>
      file.relativePath === relativePath
    );
  }

  private replaceSnapshot(snapshot: FileSnapshot): void {
    const index = this.snapshotIndex(snapshot.relativePath);
    if (index >= 0) {
      this.snapshots.splice(index, 1);
    }
    this.snapshots.push(snapshot);
  }

  private async readRevision(relativePath: string): Promise<string> {
    const response = await this.client.request(
      HttpMethod.HEAD,
      this.objectKey(relativePath)
    );
    this.requireStatus(response, [200], 'read uploaded revision');
    if (!response.etag) {
      throw new Error('S3 response omitted the object ETag');
    }
    return response.etag;
  }

  private async readAll(content: SyncContentSource): Promise<Uint8Array> {
    const result = new Uint8Array(content.size);
    let offset: number = 0;
    while (offset < content.size) {
      const chunk = await content.read(
        offset,
        Math.min(READ_CHUNK_SIZE, content.size - offset)
      );
      if (chunk.length === 0 || offset + chunk.length > content.size) {
        throw new Error('S3 upload source returned an invalid chunk');
      }
      result.set(chunk, offset);
      offset += chunk.length;
    }
    return result;
  }

  private requireStatus(
    response: HttpResponseData,
    expected: Array<number>,
    action: string
  ): void {
    if (expected.indexOf(response.status) < 0) {
      if (response.status === 409 || response.status === 412) {
        throw new Error(`S3 ${action} detected a concurrent change`);
      }
      throw new Error(`S3 ${action} failed with HTTP ${response.status}`);
    }
  }

  private requireText(response: HttpResponseData): string {
    if (response.text === undefined) {
      throw new Error('S3 response body is missing');
    }
    return response.text;
  }
}
