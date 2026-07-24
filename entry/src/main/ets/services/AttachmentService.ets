import { common } from '@kit.AbilityKit';
import { camera, cameraPicker } from '@kit.CameraKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { ImageFormatDetector } from '../model/ImageFormatDetector';

export class AttachmentResult {
  uri: string;
  markdown: string;
  localPath: string;

  constructor(uri: string, markdown: string, localPath: string) {
    this.uri = uri;
    this.markdown = markdown;
    this.localPath = localPath;
  }
}

export class AttachmentService {
  private readonly context: common.UIAbilityContext;
  private readonly attachmentsDir: string;
  private readonly tempDir: string;
  private sequence: number = 0;

  constructor(context: common.UIAbilityContext, attachmentsDir: string, tempDir: string) {
    this.context = context;
    this.attachmentsDir = attachmentsDir;
    this.tempDir = tempDir;
  }

  async capturePhoto(): Promise<AttachmentResult | undefined> {
    const profile: cameraPicker.PickerProfile = {
      cameraPosition: camera.CameraPosition.CAMERA_POSITION_BACK
    };
    const result: cameraPicker.PickerResult = await cameraPicker.pick(
      this.context,
      [cameraPicker.PickerMediaType.PHOTO],
      profile
    );
    if (!result.resultUri) {
      return undefined;
    }

    const uniquePart = `${Date.now()}-${this.sequence}`;
    this.sequence += 1;
    const tempPath = `${this.tempDir}/photo-${uniquePart}.tmp`;
    try {
      fs.copyFileSync(result.resultUri, tempPath);
      const stat = fs.statSync(tempPath);
      if (stat.size <= 0) {
        throw new Error('Captured photo is empty');
      }

      const extension = this.detectExtension(tempPath);
      const tempFile = fs.openSync(tempPath, fs.OpenMode.READ_WRITE);
      try {
        fs.fsyncSync(tempFile.fd);
      } finally {
        fs.closeSync(tempFile);
      }
      const fileName = `photo-${uniquePart}.${extension}`;
      const targetPath = `${this.attachmentsDir}/${fileName}`;
      fs.renameSync(tempPath, targetPath);
      return new AttachmentResult(
        `file://${targetPath}`,
        `![照片](../attachments/${fileName})`,
        targetPath
      );
    } catch (error) {
      this.safeUnlink(tempPath);
      throw new Error(`Failed to store captured photo: ${JSON.stringify(error)}`);
    }
  }

  rollback(result: AttachmentResult): void {
    this.safeUnlink(result.localPath);
  }

  private detectExtension(path: string): string {
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    const header = new ArrayBuffer(16);
    let length: number = 0;
    try {
      length = fs.readSync(file.fd, header);
    } finally {
      fs.closeSync(file);
    }
    const bytes = new Uint8Array(header);
    const extension = ImageFormatDetector.detect(bytes, length);
    if (!extension) {
      throw new Error('Unsupported captured image format');
    }
    return extension;
  }

  private safeUnlink(path: string): void {
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }
}
