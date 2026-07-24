export interface TextCodec {
  encode(value: string): Uint8Array;
}
