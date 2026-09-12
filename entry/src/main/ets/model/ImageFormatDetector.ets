export class ImageFormatDetector {
  static detect(bytes: Uint8Array, length: number): string | undefined {
    if (length >= 3 && ImageFormatDetector.matches(bytes, 0, [0xFF, 0xD8, 0xFF])) {
      return 'jpg';
    }
    if (length >= 8 && ImageFormatDetector.matches(bytes, 0, [0x89, 0x50, 0x4E, 0x47])) {
      return 'png';
    }
    if (length >= 12 &&
      ImageFormatDetector.matches(bytes, 0, [0x52, 0x49, 0x46, 0x46]) &&
      ImageFormatDetector.matches(bytes, 8, [0x57, 0x45, 0x42, 0x50])) {
      return 'webp';
    }
    if (length >= 12 && ImageFormatDetector.matches(bytes, 4, [0x66, 0x74, 0x79, 0x70])) {
      if (ImageFormatDetector.matches(bytes, 8, [0x68, 0x65, 0x69, 0x63]) ||
        ImageFormatDetector.matches(bytes, 8, [0x68, 0x65, 0x69, 0x78]) ||
        ImageFormatDetector.matches(bytes, 8, [0x68, 0x65, 0x76, 0x63]) ||
        ImageFormatDetector.matches(bytes, 8, [0x68, 0x65, 0x76, 0x78]) ||
        ImageFormatDetector.matches(bytes, 8, [0x6D, 0x69, 0x66, 0x31]) ||
        ImageFormatDetector.matches(bytes, 8, [0x6D, 0x73, 0x66, 0x31])) {
        return 'heic';
      }
    }
    return undefined;
  }

  private static matches(bytes: Uint8Array, offset: number, expected: Array<number>): boolean {
    for (let index = 0; index < expected.length; index += 1) {
      if (bytes[offset + index] !== expected[index]) {
        return false;
      }
    }
    return true;
  }
}
