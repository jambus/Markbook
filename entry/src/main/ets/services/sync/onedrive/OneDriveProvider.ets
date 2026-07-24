import type { SyncContentSource } from '../SyncContent';
import { ProviderFolderSnapshot } from '../SyncModels';
import type { FileSnapshot } from '../SyncModels';
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
import type { TextCodec } from '../http/TextCodec';
import {
  OneDriveGraphCodec,
  OneDriveItemInfo,
  OneDriveRootInfo
} from './OneDriveGraphCodec';
import { OneDriveGraphPath } from './OneDriveGraphPath';

const SMALL_UPLOAD_LIMIT = 4 * 1024 * 1024;
const UPLOAD_FRAGMENT_SIZE = 5 * 320 * 1024;
const READ_CHUNK_SIZE = 1024 * 1024;

class OneDriveContentSource implements SyncContentSource {
  readonly size: number;
  private readonly transport: HttpTransport;
  private readonly url: string;
  private readonly accessToken: string;
  private readonly textCodec: TextCodec;
  private readonly expectedRevision: string;
  private closed: boolean = false;

  constructor(
    transport: HttpTransport,
    url: string,
    accessToken: string,
    size: number,
    expectedRevision: string
  ) {
    this.transport = transport;
    this.url = url;
    this.accessToken = accessToken;
    this.size = size;
    this.expectedRevision = expectedRevision;
  }

  async read(offset: number, maxBytes: number): Promise<Uint8Array> {
    if (this.closed) {
      throw new Error('Cannot read a closed OneDrive content source');
    }
    if (offset < 0 || maxBytes <= 0 || offset > this.size) {
      throw new Error('Invalid OneDrive content range');
    }
    const length = Math.min(maxBytes, this.size - offset);
    if (length === 0) {
      return new Uint8Array(0);
    }

    const headers = new HttpHeaders();
    headers.authorization = `Bearer ${this.accessToken}`;
    headers.accept = 'application/octet-stream';
    headers.ifMatch = this.expectedRevision;
    headers.range = `bytes=${offset}-${offset + length - 1}`;
    const response = await this.transport.request(new HttpRequestData(
      HttpMethod.GET,
      this.url,
      headers,
      HttpResponseType.BINARY
    ));
    if (response.status !== 206 && !(response.status === 200 && length === this.size)) {
      throw new Error(`OneDrive content download failed with HTTP ${response.status}`);
    }
    if (!response.body || response.body.length !== length) {
      throw new Error('OneDrive content response length does not match the requested range');
    }
    return response.body;
  }

  close(): void {
    this.closed = true;
  }
}

export class OneDriveProvider implements SyncProvider {
  readonly id: string = 'onedrive';
  readonly displayName: string = 'OneDrive';
  private readonly transport: HttpTransport;
  private readonly accessToken: string;
  private readonly baseUrl: string;
  private root?: OneDriveRootInfo;
  private readonly folders: Array<ProviderFolderSnapshot> = [];

  constructor(
    transport: HttpTransport,
    accessToken: string,
    textCodec: TextCodec,
    baseUrl: string = 'https://graph.microsoft.com/v1.0'
  ) {
    if (accessToken.length === 0) {
      throw new Error('OneDrive access token is required');
    }
    this.transport = transport;
    this.accessToken = accessToken;
    this.textCodec = textCodec;
    this.baseUrl = OneDriveGraphPath.normalizeBaseUrl(baseUrl);
  }

  async connect(): Promise<void> {
    const response = await this.graphRequest(
      HttpMethod.GET,
      `${this.baseUrl}/me/drive/special/approot` +
        '?$select=id,name,parentReference,folder'
    );
    this.requireStatus(response, [200], 'connect');
    this.root = OneDriveGraphCodec.parseRoot(this.requireText(response));
    this.folders.length = 0;
    this.folders.push(new ProviderFolderSnapshot('', this.root.id));
  }

  async listChanges(
    cursor?: string,
    baselineFiles: Array<FileSnapshot> = [],
    baselineFolders: Array<ProviderFolderSnapshot> = []
  ): Promise<ProviderChangePage> {
    const root = this.requireRoot();
    if (cursor && this.folders.length === 1) {
      baselineFolders.forEach((folder: ProviderFolderSnapshot) => {
        if (folder.providerItemId !== root.id) {
          this.folders.push(folder);
        }
      });
    }
    const url = cursor ?
      OneDriveGraphPath.validateCursor(cursor, this.baseUrl) :
      `${this.baseUrl}/me/drive/items/${encodeURIComponent(root.id)}/delta` +
        '?$select=id,name,size,eTag,lastModifiedDateTime,parentReference,file,folder,deleted';
    const response = await this.graphRequest(HttpMethod.GET, url);
    this.requireStatus(response, [200], 'list changes');
    const page = OneDriveGraphCodec.parseDelta(
      this.requireText(response),
      root,
      baselineFiles,
      this.folders
    );
    this.folders.length = 0;
    this.folders.push(...page.folders);
    OneDriveGraphPath.validateCursor(page.cursor, this.baseUrl);
    return new ProviderChangePage(
      page.changes,
      page.cursor,
      page.hasMore,
      page.folders
    );
  }

  async download(
    relativePath: string,
    expectedRevision?: string
  ): Promise<SyncContentSource> {
    const item = await this.getItem(relativePath);
    if (item.folder) {
      throw new Error(`Cannot download OneDrive folder ${relativePath}`);
    }
    if (expectedRevision && item.revision !== expectedRevision) {
      throw new Error(`OneDrive revision changed for ${relativePath}`);
    }
    return new OneDriveContentSource(
      this.transport,
      `${this.itemUrl(relativePath)}:/content`,
      this.accessToken,
      item.size,
      item.revision
    );
  }

  async upload(
    relativePath: string,
    content: SyncContentSource,
    expectedRevision?: string,
    operationId?: string
  ): Promise<string> {
    await this.ensureParentFolders(relativePath);
    if (content.size <= SMALL_UPLOAD_LIMIT) {
      return this.uploadSmall(relativePath, content, expectedRevision);
    }
    return this.uploadLarge(relativePath, content, expectedRevision);
  }

  async delete(
    relativePath: string,
    expectedRevision?: string,
    operationId?: string
  ): Promise<void> {
    const headers = this.authHeaders();
    headers.ifMatch = expectedRevision;
    const response = await this.transport.request(new HttpRequestData(
      HttpMethod.DELETE,
      this.itemUrl(relativePath),
      headers
    ));
    this.requireStatus(response, [204], 'delete');
  }

  cancel(): void {
    this.transport.cancel();
  }

  private async uploadSmall(
    relativePath: string,
    content: SyncContentSource,
    expectedRevision?: string
  ): Promise<string> {
    const body = await this.readRange(content, 0, content.size);
    const headers = this.authHeaders();
    headers.contentType = 'application/octet-stream';
    headers.contentLength = `${body.length}`;
    if (expectedRevision) {
      headers.ifMatch = expectedRevision;
    } else {
      headers.ifNoneMatch = '*';
    }
    const conflictQuery = expectedRevision ?
      '' :
      '?@microsoft.graph.conflictBehavior=fail';
    try {
      const response = await this.transport.request(new HttpRequestData(
        HttpMethod.PUT,
        `${this.itemUrl(relativePath)}:/content${conflictQuery}`,
        headers,
        HttpResponseType.TEXT,
        body
      ));
      this.requireStatus(response, [200, 201], 'small upload');
      return OneDriveGraphCodec.parseItem(this.requireText(response)).revision;
    } catch (error) {
      const recoveredRevision = await this.recoverCompletedUpload(relativePath, content);
      if (recoveredRevision) {
        return recoveredRevision;
      }
      throw error;
    }
  }

  private async uploadLarge(
    relativePath: string,
    content: SyncContentSource,
    expectedRevision?: string
  ): Promise<string> {
    const headers = this.authHeaders();
    headers.contentType = 'application/json';
    headers.ifMatch = expectedRevision;
    const conflictBehavior = expectedRevision ? 'replace' : 'fail';
    const body = this.textCodec.encode(
      `{"item":{"@microsoft.graph.conflictBehavior":"${conflictBehavior}"}}`
    );
    const sessionResponse = await this.transport.request(new HttpRequestData(
      HttpMethod.POST,
      `${this.itemUrl(relativePath)}:/createUploadSession`,
      headers,
      HttpResponseType.TEXT,
      body
    ));
    this.requireStatus(sessionResponse, [200], 'create upload session');
    const uploadUrl = OneDriveGraphPath.validateUploadUrl(
      OneDriveGraphCodec.parseUploadUrl(this.requireText(sessionResponse))
    );

    try {
      let offset: number = 0;
      let finalResponse: HttpResponseData | undefined = undefined;
      while (offset < content.size) {
        const length = Math.min(UPLOAD_FRAGMENT_SIZE, content.size - offset);
        const fragment = await this.readRange(content, offset, length);
        const fragmentHeaders = new HttpHeaders();
        fragmentHeaders.contentType = 'application/octet-stream';
        fragmentHeaders.contentLength = `${fragment.length}`;
        fragmentHeaders.contentRange =
          `bytes ${offset}-${offset + fragment.length - 1}/${content.size}`;
        finalResponse = await this.transport.request(new HttpRequestData(
          HttpMethod.PUT,
          uploadUrl,
          fragmentHeaders,
          HttpResponseType.TEXT,
          fragment
        ));
        const isLast = offset + fragment.length === content.size;
        this.requireStatus(finalResponse, isLast ? [200, 201] : [202], 'upload fragment');
        offset += fragment.length;
      }
      if (!finalResponse) {
        throw new Error('OneDrive upload session produced no final response');
      }
      return OneDriveGraphCodec.parseItem(this.requireText(finalResponse)).revision;
    } catch (error) {
      const recoveredRevision = await this.recoverCompletedUpload(relativePath, content);
      if (recoveredRevision) {
        return recoveredRevision;
      }
      try {
        await this.transport.request(new HttpRequestData(HttpMethod.DELETE, uploadUrl));
      } catch (cancelError) {
      }
      throw error;
    }
  }

  private async recoverCompletedUpload(
    relativePath: string,
    localContent: SyncContentSource
  ): Promise<string | undefined> {
    try {
      const item = await this.getItem(relativePath);
      if (item.folder || item.size !== localContent.size) {
        return undefined;
      }
      const remoteContent = new OneDriveContentSource(
        this.transport,
        `${this.itemUrl(relativePath)}:/content`,
        this.accessToken,
        item.size,
        item.revision
      );
      try {
        let offset: number = 0;
        while (offset < localContent.size) {
          const length = Math.min(READ_CHUNK_SIZE, localContent.size - offset);
          const local = await this.readRange(localContent, offset, length);
          const remote = await remoteContent.read(offset, length);
          if (!this.equal(local, remote)) {
            return undefined;
          }
          offset += length;
        }
        return item.revision;
      } finally {
        remoteContent.close();
      }
    } catch (recoveryError) {
      return undefined;
    }
  }

  private async ensureParentFolders(relativePath: string): Promise<void> {
    const segments = relativePath.split('/');
    let currentPath = '';
    let parentId = this.requireRoot().id;
    for (let index = 0; index < segments.length - 1; index += 1) {
      currentPath = currentPath.length > 0 ?
        `${currentPath}/${segments[index]}` :
        segments[index];
      const cached = this.findFolder(currentPath);
      if (cached) {
        parentId = cached.providerItemId;
        continue;
      }

      const existing = await this.graphRequest(
        HttpMethod.GET,
        `${this.itemUrl(currentPath, true)}?$select=id,folder`
      );
      if (existing.status === 200) {
        const item = OneDriveGraphCodec.parseItem(this.requireText(existing));
        if (!item.folder) {
          throw new Error(`OneDrive path component is not a folder: ${currentPath}`);
        }
        parentId = item.id;
        this.folders.push(new ProviderFolderSnapshot(currentPath, parentId));
        continue;
      }
      if (existing.status !== 404) {
        this.requireStatus(existing, [200, 404], 'find parent folder');
      }

      const createHeaders = this.authHeaders();
      createHeaders.contentType = 'application/json';
      const createBody = this.textCodec.encode(JSON.stringify({
        name: segments[index],
        folder: {},
        '@microsoft.graph.conflictBehavior': 'fail'
      }));
      const created = await this.transport.request(new HttpRequestData(
        HttpMethod.POST,
        `${this.baseUrl}/me/drive/items/${encodeURIComponent(parentId)}/children`,
        createHeaders,
        HttpResponseType.TEXT,
        createBody
      ));
      if (created.status === 409) {
        const raced = await this.graphRequest(
          HttpMethod.GET,
          `${this.itemUrl(currentPath, true)}?$select=id,folder`
        );
        this.requireStatus(raced, [200], 'resolve parent folder race');
        const item = OneDriveGraphCodec.parseItem(this.requireText(raced));
        if (!item.folder) {
          throw new Error(`OneDrive path component is not a folder: ${currentPath}`);
        }
        parentId = item.id;
      } else {
        this.requireStatus(created, [201], 'create parent folder');
        const item = OneDriveGraphCodec.parseItem(this.requireText(created));
        if (!item.folder) {
          throw new Error(`OneDrive created item is not a folder: ${currentPath}`);
        }
        parentId = item.id;
      }
      this.folders.push(new ProviderFolderSnapshot(currentPath, parentId));
    }
  }

  private async getItem(relativePath: string): Promise<OneDriveItemInfo> {
    const response = await this.graphRequest(
      HttpMethod.GET,
      `${this.itemUrl(relativePath)}?$select=id,size,eTag,file,folder`
    );
    this.requireStatus(response, [200], 'get item');
    return OneDriveGraphCodec.parseItem(this.requireText(response));
  }

  private async graphRequest(method: HttpMethod, url: string): Promise<HttpResponseData> {
    return this.transport.request(new HttpRequestData(method, url, this.authHeaders()));
  }

  private authHeaders(): HttpHeaders {
    const headers = new HttpHeaders();
    headers.authorization = `Bearer ${this.accessToken}`;
    headers.accept = 'application/json';
    return headers;
  }

  private itemUrl(relativePath: string, directory: boolean = false): string {
    const root = this.requireRoot();
    const encodedPath = directory ?
      OneDriveGraphPath.encodeDirectory(relativePath) :
      OneDriveGraphPath.encode(relativePath);
    return `${this.baseUrl}/me/drive/items/${encodeURIComponent(root.id)}:/` +
      encodedPath;
  }

  private findFolder(path: string): ProviderFolderSnapshot | undefined {
    for (let index = 0; index < this.folders.length; index += 1) {
      if (this.folders[index].relativePath === path) {
        return this.folders[index];
      }
    }
    return undefined;
  }

  private requireRoot(): OneDriveRootInfo {
    if (!this.root) {
      throw new Error('OneDrive provider is not connected');
    }
    return this.root;
  }

  private requireStatus(
    response: HttpResponseData,
    expected: Array<number>,
    operation: string
  ): void {
    if (!expected.includes(response.status)) {
      throw new Error(`OneDrive ${operation} failed with HTTP ${response.status}`);
    }
  }

  private requireText(response: HttpResponseData): string {
    if (response.text === undefined) {
      throw new Error('OneDrive response body is missing');
    }
    return response.text;
  }

  private async readRange(
    content: SyncContentSource,
    offset: number,
    length: number
  ): Promise<Uint8Array> {
    const result = new Uint8Array(length);
    let position: number = 0;
    while (position < length) {
      const chunk = await content.read(
        offset + position,
        Math.min(READ_CHUNK_SIZE, length - position)
      );
      if (chunk.length === 0 || chunk.length > length - position) {
        throw new Error('Sync content source returned an invalid upload chunk');
      }
      result.set(chunk, position);
      position += chunk.length;
    }
    return result;
  }

  private equal(left: Uint8Array, right: Uint8Array): boolean {
    if (left.length !== right.length) {
      return false;
    }
    for (let index = 0; index < left.length; index += 1) {
      if (left[index] !== right[index]) {
        return false;
      }
    }
    return true;
  }

}
