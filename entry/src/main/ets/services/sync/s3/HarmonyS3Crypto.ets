import { buffer } from '@kit.ArkTS';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import type { S3Crypto } from './S3Signer';

export class HarmonyS3Crypto implements S3Crypto {
  encodeUtf8(value: string): Uint8Array {
    return new Uint8Array(buffer.from(value, 'utf-8').buffer);
  }

  async sha256(value: Uint8Array): Promise<Uint8Array> {
    const digest = cryptoFramework.createMd('SHA256');
    await digest.update({ data: value });
    return (await digest.digest()).data;
  }

  async hmacSha256(key: Uint8Array, value: Uint8Array): Promise<Uint8Array> {
    const keyGenerator = cryptoFramework.createSymKeyGenerator('HMAC|SHA256');
    const symmetricKey = await keyGenerator.convertKey({ data: key });
    const mac = cryptoFramework.createMac('HMAC|SHA256');
    await mac.init(symmetricKey);
    await mac.update({ data: value });
    return (await mac.doFinal()).data;
  }
}
