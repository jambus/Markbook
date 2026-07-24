import type { TextCodec } from './TextCodec';

export interface Utf8Codec extends TextCodec {
  decode(value: Uint8Array): string;
}
