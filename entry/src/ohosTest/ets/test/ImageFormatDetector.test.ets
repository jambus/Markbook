import { describe, expect, it } from '@ohos/hypium';
import { ImageFormatDetector } from '../../../main/ets/model/ImageFormatDetector';

export default function imageFormatDetectorTest(): void {
  describe('ImageFormatDetector', () => {
    it('detects JPEG', 0, () => {
      const bytes = new Uint8Array([0xFF, 0xD8, 0xFF, 0xE0]);
      expect(ImageFormatDetector.detect(bytes, bytes.length)).assertEqual('jpg');
    });

    it('detects PNG', 0, () => {
      const bytes = new Uint8Array([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
      expect(ImageFormatDetector.detect(bytes, bytes.length)).assertEqual('png');
    });

    it('detects WebP', 0, () => {
      const bytes = new Uint8Array([
        0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
        0x57, 0x45, 0x42, 0x50
      ]);
      expect(ImageFormatDetector.detect(bytes, bytes.length)).assertEqual('webp');
    });

    it('detects HEIC', 0, () => {
      const bytes = new Uint8Array([
        0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
        0x68, 0x65, 0x69, 0x63
      ]);
      expect(ImageFormatDetector.detect(bytes, bytes.length)).assertEqual('heic');
    });

    it('rejects unknown data', 0, () => {
      const bytes = new Uint8Array([0x00, 0x01, 0x02, 0x03]);
      expect(ImageFormatDetector.detect(bytes, bytes.length)).assertUndefined();
    });
  });
}
