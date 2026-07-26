import { common, Want } from '@kit.AbilityKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import {
  AttachmentNaming,
  AttachmentRecovery,
  AttachmentRecoveryAction
} from '../model/AttachmentNaming';
import { ImageFormatDetector } from '../model/ImageFormatDetector';

export class AttachmentResult {
  uri: string;
  markdown: string;
  localPath: string;
  mimeType: string;
  transactionPath: string;

  constructor(
    uri: string,
    markdown: string,
    localPath: string,
    mimeType: string,
    transactionPath: string
  ) {
    this.uri = uri;
    this.markdown = markdown;
    this.localPath = localPath;
    this.mimeType = mimeType;
    this.transactionPath = transactionPath;
  }
}

export enum AttachmentCaptureStatus {
  CAPTURED,
  CANCELLED
}

export class AttachmentCaptureOutcome {
  readonly status: AttachmentCaptureStatus;
  readonly attachment?: AttachmentResult;

  constructor(status: AttachmentCaptureStatus, attachment?: AttachmentResult) {
    this.status = status;
    this.attachment = attachment;
  }
}

export class AttachmentService {
  private readonly context: common.UIAbilityContext;
  private readonly attachmentsDir: string;
  private readonly tempDir: string;
  private readonly notesDir: string;

  constructor(
    context: common.UIAbilityContext,
    attachmentsDir: string,
    tempDir: string,
    notesDir: string
  ) {
    this.context = context;
    this.attachmentsDir = attachmentsDir;
    this.tempDir = tempDir;
    this.notesDir = notesDir;
    this.recoverInterruptedAttachments();
  }

  async capturePhoto(): Promise<AttachmentCaptureOutcome> {
    const captureWant = new Want();
    captureWant.action = 'ohos.want.action.imageCapture';
    captureWant.type = 'image/*';
    const result = await this.context.startAbilityForResult(captureWant);
    const resultUri = result.want?.uri;
    if (!resultUri) {
      return new AttachmentCaptureOutcome(AttachmentCaptureStatus.CANCELLED);
    }

    const uuid = AttachmentNaming.uuidV4(
      cryptoFramework.createRandom().generateRandomSync(16).data
    );
    const transactionName = `attachment-${uuid}`;
    const tempPath = `${this.tempDir}/${transactionName}.tmp`;
    const transactionPath = `${this.tempDir}/${transactionName}.json`;
    try {
      fs.copyFileSync(resultUri, tempPath);
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
      const descriptor = AttachmentNaming.photo(uuid, extension);
      const targetPath = `${this.attachmentsDir}/${descriptor.fileName}`;
      if (fs.accessSync(targetPath)) {
        throw new Error('Generated attachment name already exists');
      }
      this.writeTransaction(transactionPath, descriptor.fileName);
      fs.renameSync(tempPath, targetPath);
      const attachment = new AttachmentResult(
        `file://${targetPath}`,
        descriptor.markdown,
        targetPath,
        descriptor.mimeType,
        transactionPath
      );
      return new AttachmentCaptureOutcome(
        AttachmentCaptureStatus.CAPTURED,
        attachment
      );
    } catch (error) {
      this.safeUnlink(tempPath);
      this.safeUnlink(transactionPath);
      throw new Error('Failed to store captured photo');
    }
  }

  confirm(result: AttachmentResult): void {
    try {
      this.safeUnlink(result.transactionPath);
    } catch (error) {
      // Recovery will remove the marker after confirming the Markdown reference.
    }
  }

  rollback(result: AttachmentResult): void {
    let attachmentRemoved: boolean = false;
    try {
      this.safeUnlink(result.localPath);
      attachmentRemoved = true;
    } catch (error) {
      // Keep the transaction marker so startup recovery can retry deletion.
    }
    if (attachmentRemoved) {
      try {
        this.safeUnlink(result.transactionPath);
      } catch (error) {
        // A stale marker is harmless and will be cleared on the next startup.
      }
    }
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

  private writeTransaction(path: string, fileName: string): void {
    const file = fs.openSync(
      path,
      fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC | fs.OpenMode.SYNC
    );
    try {
      fs.writeSync(file.fd, fileName);
      fs.fsyncSync(file.fd);
    } finally {
      fs.closeSync(file);
    }
  }

  private recoverInterruptedAttachments(): void {
    fs.listFileSync(this.tempDir).forEach((name: string) => {
      const path = `${this.tempDir}/${name}`;
      if (/^attachment-[0-9a-f-]+\.tmp$/.test(name)) {
        this.safeUnlink(path);
        return;
      }
      if (!/^attachment-[0-9a-f-]+\.json$/.test(name)) {
        return;
      }
      this.recoverTransaction(path);
    });
  }

  private recoverTransaction(transactionPath: string): void {
    let fileName: string = '';
    try {
      fileName = fs.readTextSync(transactionPath);
    } catch (error) {
      this.safeUnlink(transactionPath);
      return;
    }
    if (!AttachmentNaming.isPhotoFileName(fileName)) {
      this.safeUnlink(transactionPath);
      return;
    }
    const targetPath = `${this.attachmentsDir}/${fileName}`;
    const action = AttachmentRecovery.decide(
      fileName,
      fs.accessSync(targetPath),
      this.noteContents()
    );
    if (action === AttachmentRecoveryAction.DELETE) {
      this.safeUnlink(targetPath);
    }
    this.safeUnlink(transactionPath);
  }

  private noteContents(): Array<string> {
    const contents: Array<string> = [];
    const noteNames = fs.listFileSync(this.notesDir);
    for (let index = 0; index < noteNames.length; index += 1) {
      const name = noteNames[index];
      if (name.endsWith('.md')) {
        contents.push(fs.readTextSync(`${this.notesDir}/${name}`));
      }
    }
    return contents;
  }
}
