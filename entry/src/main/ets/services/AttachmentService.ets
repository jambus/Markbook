import { common, Want } from '@kit.AbilityKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import {
  AttachmentDescriptor,
  AttachmentNaming,
  AttachmentRecovery,
  AttachmentRecoveryAction
} from '../model/AttachmentNaming';
import { ImageFormatDetector } from '../model/ImageFormatDetector';

export class AttachmentResult {
  attachmentId: string;
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
    transactionPath: string,
    attachmentId: string = ''
  ) {
    this.attachmentId = attachmentId;
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

    const operationId = AttachmentNaming.uuidV4(
      cryptoFramework.createRandom().generateRandomSync(16).data
    );
    const tempPath = `${this.tempDir}/attachment-${operationId}.tmp`;
    let transactionPath = '';
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
      const descriptor = this.createAvailableDescriptor(extension);
      const targetPath = `${this.attachmentsDir}/${descriptor.fileName}`;
      transactionPath = `${this.tempDir}/attachment-${descriptor.attachmentId}.json`;
      this.writeTransaction(transactionPath, descriptor.fileName);
      fs.renameSync(tempPath, targetPath);
      const attachment = new AttachmentResult(
        this.fileUri(targetPath),
        descriptor.markdown,
        targetPath,
        descriptor.mimeType,
        transactionPath,
        descriptor.attachmentId
      );
      return new AttachmentCaptureOutcome(
        AttachmentCaptureStatus.CAPTURED,
        attachment
      );
    } catch (error) {
      this.safeUnlink(tempPath);
      if (transactionPath.length > 0) {
        this.safeUnlink(transactionPath);
      }
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

  recordVariant(result: AttachmentResult, fileName: string): void {
    if (!AttachmentNaming.isPhotoFileName(fileName) || result.transactionPath.length === 0) {
      throw new Error('Invalid attachment transaction variant');
    }
    let existing = '';
    try {
      existing = fs.readTextSync(result.transactionPath).trim();
    } catch (error) {
      throw new Error('Attachment transaction is unavailable');
    }
    if (existing.split('\n').indexOf(fileName) >= 0) {
      return;
    }
    this.writeTransaction(result.transactionPath, `${existing}\n${fileName}`);
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

  private createAvailableDescriptor(extension: string): AttachmentDescriptor {
    for (let attempt = 0; attempt < 32; attempt += 1) {
      const attachmentId = AttachmentNaming.shortPhotoId(
        new Date(),
        cryptoFramework.createRandom().generateRandomSync(3).data
      );
      const descriptor = AttachmentNaming.photo(attachmentId, extension);
      const originalPath = `${this.attachmentsDir}/${descriptor.fileName}`;
      const corrected = AttachmentNaming.photo(attachmentId, extension, 'corrected');
      const correctedPath = `${this.attachmentsDir}/${corrected.fileName}`;
      if (!fs.accessSync(originalPath) && !fs.accessSync(correctedPath)) {
        return descriptor;
      }
    }
    throw new Error('Unable to allocate an attachment file name');
  }

  private safeUnlink(path: string): void {
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }

  private fileUri(path: string): string {
    return path.includes('://') ? path : `file://${path}`;
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
      if (/^attachment-[0-9a-z-]+\.tmp$/.test(name)) {
        this.safeUnlink(path);
        return;
      }
      if (!/^attachment-[0-9a-z-]+\.json$/.test(name)) {
        return;
      }
      this.recoverTransaction(path);
    });
  }

  private recoverTransaction(transactionPath: string): void {
    let fileNames: Array<string> = [];
    try {
      fileNames = fs.readTextSync(transactionPath)
        .split('\n')
        .map((name: string): string => name.trim())
        .filter((name: string): boolean => name.length > 0);
    } catch (error) {
      this.safeUnlink(transactionPath);
      return;
    }
    let invalidName: boolean = fileNames.length === 0;
    for (let index = 0; index < fileNames.length; index += 1) {
      if (!AttachmentNaming.isPhotoFileName(fileNames[index])) {
        invalidName = true;
        break;
      }
    }
    if (invalidName) {
      this.safeUnlink(transactionPath);
      return;
    }
    const notes = this.noteContents();
    let committed: boolean = false;
    for (let index = 0; index < fileNames.length; index += 1) {
      const fileName = fileNames[index];
      const targetPath = `${this.attachmentsDir}/${fileName}`;
      if (AttachmentRecovery.decide(fileName, fs.accessSync(targetPath), notes) ===
        AttachmentRecoveryAction.KEEP) {
        committed = true;
        break;
      }
    }
    if (!committed) {
      fileNames.forEach((fileName: string): void => {
        this.safeUnlink(`${this.attachmentsDir}/${fileName}`);
      });
    }
    this.safeUnlink(transactionPath);
  }

  private noteContents(): Array<string> {
    const contents: Array<string> = [];
    const noteNames = this.listFilesRecursively(this.notesDir);
    for (let index = 0; index < noteNames.length; index += 1) {
      const relativeName = noteNames[index];
      if (relativeName.endsWith('.md') && !relativeName.startsWith('.obsidian/')) {
        contents.push(fs.readTextSync(`${this.notesDir}/${relativeName}`));
      }
    }
    return contents;
  }

  private listFilesRecursively(directory: string, prefix: string = ''): Array<string> {
    const files: Array<string> = [];
    fs.listFileSync(directory).forEach((name: string) => {
      const relativeName = prefix.length > 0 ? `${prefix}/${name}` : name;
      const path = `${directory}/${name}`;
      try {
        fs.listFileSync(path);
        files.push(...this.listFilesRecursively(path, relativeName));
      } catch (error) {
        files.push(relativeName);
      }
    });
    return files;
  }
}
