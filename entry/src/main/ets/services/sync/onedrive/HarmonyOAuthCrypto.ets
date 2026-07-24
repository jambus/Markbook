import { buffer } from '@kit.ArkTS';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import type { OAuthCrypto } from './OneDriveOAuth';

export class HarmonyOAuthCrypto implements OAuthCrypto {
  randomBytes(length: number): Uint8Array {
    return cryptoFramework.createRandom().generateRandomSync(length).data;
  }

  sha256(value: Uint8Array): Uint8Array {
    const digest = cryptoFramework.createMd('SHA256');
    digest.updateSync({ data: value });
    return digest.digestSync().data;
  }

  encodeUtf8(value: string): Uint8Array {
    return new Uint8Array(buffer.from(value, 'utf-8').buffer);
  }
}
