export class AttachmentDescriptor {
  readonly uuid: string;
  readonly fileName: string;
  readonly relativePath: string;
  readonly markdown: string;
  readonly mimeType: string;

  constructor(uuid: string, variant: string, extension: string, mimeType: string) {
    this.uuid = uuid;
    this.fileName = `${uuid}-${variant}.${extension}`;
    this.relativePath = `../attachments/${this.fileName}`;
    this.markdown = `![照片](${this.relativePath})`;
    this.mimeType = mimeType;
  }
}

export class AttachmentNaming {
  static uuidV4(randomBytes: Uint8Array): string {
    if (randomBytes.length !== 16) {
      throw new Error('UUID generation requires exactly 16 random bytes');
    }
    const bytes = randomBytes.slice();
    bytes[6] = (bytes[6] & 0x0F) | 0x40;
    bytes[8] = (bytes[8] & 0x3F) | 0x80;
    const value = Array.from(bytes)
      .map((byte: number): string => byte.toString(16).padStart(2, '0'))
      .join('');
    return `${value.substring(0, 8)}-${value.substring(8, 12)}-` +
      `${value.substring(12, 16)}-${value.substring(16, 20)}-${value.substring(20)}`;
  }

  static photo(uuid: string, extension: string, variant: string = 'original'): AttachmentDescriptor {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(uuid)) {
      throw new Error('Invalid attachment UUID');
    }
    if (variant !== 'original' && variant !== 'corrected') {
      throw new Error('Unsupported attachment variant');
    }
    const mimeType = AttachmentNaming.mimeType(extension);
    return new AttachmentDescriptor(uuid, variant, extension, mimeType);
  }

  static mimeType(extension: string): string {
    if (extension === 'jpg') {
      return 'image/jpeg';
    }
    if (extension === 'png') {
      return 'image/png';
    }
    if (extension === 'webp') {
      return 'image/webp';
    }
    if (extension === 'heic') {
      return 'image/heic';
    }
    throw new Error('Unsupported attachment image format');
  }

  static isPhotoFileName(fileName: string): boolean {
    return /^(photo-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}-(original|corrected))\.(jpg|png|webp|heic)$/
      .test(fileName);
  }
}

export enum AttachmentRecoveryAction {
  KEEP,
  DELETE,
  IGNORE
}

export class AttachmentRecovery {
  static decide(
    fileName: string,
    targetExists: boolean,
    noteContents: Array<string>
  ): AttachmentRecoveryAction {
    if (!AttachmentNaming.isPhotoFileName(fileName) || !targetExists) {
      return AttachmentRecoveryAction.IGNORE;
    }
    const markdownPath = `../attachments/${fileName}`;
    for (let index = 0; index < noteContents.length; index += 1) {
      if (noteContents[index].includes(markdownPath)) {
        return AttachmentRecoveryAction.KEEP;
      }
    }
    return AttachmentRecoveryAction.DELETE;
  }
}
