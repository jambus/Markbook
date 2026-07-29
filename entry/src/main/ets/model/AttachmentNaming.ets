export class AttachmentDescriptor {
  readonly attachmentId: string;
  readonly fileName: string;
  readonly relativePath: string;
  readonly markdown: string;
  readonly mimeType: string;

  constructor(attachmentId: string, variant: string, extension: string, mimeType: string) {
    this.attachmentId = attachmentId;
    this.fileName = `${attachmentId}-${variant}.${extension}`;
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

  static photo(attachmentId: string, extension: string, variant: string = 'original'): AttachmentDescriptor {
    if (!AttachmentNaming.isAttachmentId(attachmentId)) {
      throw new Error('Invalid attachment ID');
    }
    if (variant !== 'original' && variant !== 'corrected') {
      throw new Error('Unsupported attachment variant');
    }
    const mimeType = AttachmentNaming.mimeType(extension);
    return new AttachmentDescriptor(attachmentId, variant, extension, mimeType);
  }

  static shortPhotoId(date: Date, randomBytes: Uint8Array): string {
    if (randomBytes.length < 3) {
      throw new Error('Short attachment ID requires at least three random bytes');
    }
    const timestamp = `${date.getFullYear().toString().padStart(4, '0')}` +
      `${(date.getMonth() + 1).toString().padStart(2, '0')}` +
      `${date.getDate().toString().padStart(2, '0')}-` +
      `${date.getHours().toString().padStart(2, '0')}` +
      `${date.getMinutes().toString().padStart(2, '0')}` +
      `${date.getSeconds().toString().padStart(2, '0')}`;
    const randomValue = ((randomBytes[0] << 16) | (randomBytes[1] << 8) | randomBytes[2]) % 1679616;
    return `${timestamp}-${randomValue.toString(36).padStart(4, '0')}`;
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
    return /^(photo-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|[0-9]{8}-[0-9]{6}-[0-9a-z]{4})-(original|corrected))\.(jpg|png|webp|heic)$/
      .test(fileName);
  }

  private static isAttachmentId(attachmentId: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(attachmentId) ||
      /^[0-9]{8}-[0-9]{6}-[0-9a-z]{4}$/.test(attachmentId);
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
