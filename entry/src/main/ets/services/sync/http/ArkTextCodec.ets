import { buffer } from '@kit.ArkTS';
import type { TextCodec } from './TextCodec';
import type { Utf8Codec } from './Utf8Codec';

export class ArkTextCodec implements TextCodec, Utf8Codec {
  encode(value: string): Uint8Array {
    return new Uint8Array(buffer.from(value, 'utf-8').buffer);
  }

  decode(value: Uint8Array): string {
    return buffer.from(value).toString('utf-8');
  }
}
