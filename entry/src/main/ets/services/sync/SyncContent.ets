export interface SyncContentSource {
  readonly size: number;
  read(offset: number, maxBytes: number): Promise<Uint8Array>;
  close(): void;
}
