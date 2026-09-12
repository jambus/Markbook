import { asset } from '@kit.AssetStoreKit';
import type { BusinessError } from '@kit.BasicServicesKit';
import type { Utf8Codec } from '../http/Utf8Codec';
import type { RefreshTokenStore } from './OneDriveSession';

const SECRET_LIMIT = 1024;
const MAX_CHUNKS = 64;
const MANIFEST_ALIAS = 'heji.notes.onedrive.refresh.manifest';
const CHUNK_ALIAS_PREFIX = 'heji.notes.onedrive.refresh.chunk';
const NOT_FOUND = 24000002;

class RefreshTokenManifest {
  readonly generation: string;
  readonly chunkCount: number;

  constructor(generation: string, chunkCount: number) {
    this.generation = generation;
    this.chunkCount = chunkCount;
  }
}

export class AssetRefreshTokenStore implements RefreshTokenStore {
  private readonly codec: Utf8Codec;
  private sequence: number = 0;

  constructor(codec: Utf8Codec) {
    this.codec = codec;
  }

  async load(): Promise<string | undefined> {
    const manifest = await this.loadManifest();
    if (!manifest) {
      return undefined;
    }
    const chunks: Array<Uint8Array> = [];
    let totalLength = 0;
    for (let index = 0; index < manifest.chunkCount; index++) {
      const chunk = await this.querySecret(this.chunkAlias(manifest.generation, index));
      if (!chunk) {
        throw new Error('Stored OneDrive refresh token is incomplete');
      }
      chunks.push(chunk);
      totalLength += chunk.length;
    }
    const value = new Uint8Array(totalLength);
    let offset = 0;
    chunks.forEach((chunk: Uint8Array) => {
      value.set(chunk, offset);
      offset += chunk.length;
    });
    return this.codec.decode(value);
  }

  async save(refreshToken: string): Promise<void> {
    if (refreshToken.length === 0) {
      throw new Error('Cannot store an empty OneDrive refresh token');
    }
    const previous = await this.loadManifest();
    const bytes = this.codec.encode(refreshToken);
    if (bytes.length > SECRET_LIMIT * MAX_CHUNKS) {
      throw new Error('OneDrive refresh token exceeds the secure storage limit');
    }
    const chunkCount = Math.ceil(bytes.length / SECRET_LIMIT);
    const generation = `${Date.now()}-${this.sequence++}`;

    for (let index = 0; index < chunkCount; index++) {
      const start = index * SECRET_LIMIT;
      const end = Math.min(start + SECRET_LIMIT, bytes.length);
      await this.putSecret(
        this.chunkAlias(generation, index),
        bytes.slice(start, end)
      );
    }
    await this.putSecret(
      MANIFEST_ALIAS,
      this.codec.encode(`${generation}\n${chunkCount}`)
    );
    if (previous) {
      await this.removeChunks(previous);
    }
  }

  async clear(): Promise<void> {
    const manifest = await this.loadManifest();
    await this.remove(MANIFEST_ALIAS);
    if (manifest) {
      await this.removeChunks(manifest);
    }
  }

  private async loadManifest(): Promise<RefreshTokenManifest | undefined> {
    const bytes = await this.querySecret(MANIFEST_ALIAS);
    if (!bytes) {
      return undefined;
    }
    const fields = this.codec.decode(bytes).split('\n');
    const chunkCount = fields.length === 2 ? Number(fields[1]) : 0;
    if (
      fields.length !== 2 ||
      !/^[0-9]+-[0-9]+$/.test(fields[0]) ||
      !Number.isInteger(chunkCount) ||
      chunkCount <= 0 ||
      chunkCount > MAX_CHUNKS
    ) {
      throw new Error('Stored OneDrive refresh token manifest is invalid');
    }
    return new RefreshTokenManifest(fields[0], chunkCount);
  }

  private async querySecret(alias: string): Promise<Uint8Array | undefined> {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, this.codec.encode(alias));
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL);
    try {
      const results = await asset.query(query);
      if (results.length !== 1) {
        throw new Error(`Expected one Asset Store entry for ${alias}`);
      }
      return results[0].get(asset.Tag.SECRET) as Uint8Array;
    } catch (error) {
      if ((error as BusinessError).code === NOT_FOUND) {
        return undefined;
      }
      throw new Error('Asset Store query failed');
    }
  }

  private async putSecret(alias: string, secret: Uint8Array): Promise<void> {
    if (secret.length === 0 || secret.length > SECRET_LIMIT) {
      throw new Error('Asset Store secret chunk must be between 1 and 1024 bytes');
    }
    const attributes: asset.AssetMap = new Map();
    attributes.set(asset.Tag.ALIAS, this.codec.encode(alias));
    attributes.set(asset.Tag.SECRET, secret);
    attributes.set(
      asset.Tag.ACCESSIBILITY,
      asset.Accessibility.DEVICE_FIRST_UNLOCKED
    );
    attributes.set(
      asset.Tag.CONFLICT_RESOLUTION,
      asset.ConflictResolution.OVERWRITE
    );
    await asset.add(attributes);
  }

  private async removeChunks(manifest: RefreshTokenManifest): Promise<void> {
    for (let index = 0; index < manifest.chunkCount; index++) {
      await this.remove(this.chunkAlias(manifest.generation, index));
    }
  }

  private async remove(alias: string): Promise<void> {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, this.codec.encode(alias));
    try {
      await asset.remove(query);
    } catch (error) {
      if ((error as BusinessError).code !== NOT_FOUND) {
        throw new Error('Asset Store removal failed');
      }
    }
  }

  private chunkAlias(generation: string, index: number): string {
    return `${CHUNK_ALIAS_PREFIX}.${generation}.${index}`;
  }
}
