import { fileIo as fs } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { MarkdownDocument } from '../model/MarkdownDocument';
import { Note } from '../model/Note';

const NOTE_EXTENSION = '.md';
const APP_STATE_DIR = 'Markbook';
const VAULT_URI_FILE = 'vault-uri.txt';
const DAILY_NOTES_DIR = 'Daily Notes';

export class NotebookRepository {
  rootDir: string;
  notesDir: string;
  attachmentsDir: string;
  readonly metadataDir: string;
  readonly tempDir: string;
  dailyNotesDir: string;
  private readonly stateDir: string;
  private readonly vaultUriPath: string;

  constructor(context: common.UIAbilityContext) {
    this.stateDir = `${context.filesDir}/${APP_STATE_DIR}`;
    this.vaultUriPath = `${this.stateDir}/${VAULT_URI_FILE}`;
    this.ensureDirectory(this.stateDir);

    this.rootDir = this.readStoredVaultUri();
    this.notesDir = this.rootDir;
    this.attachmentsDir = this.rootDir.length > 0 ?
      this.join(this.rootDir, 'attachments') : '';
    this.dailyNotesDir = this.rootDir.length > 0 ?
      this.join(this.rootDir, DAILY_NOTES_DIR) : '';
    this.metadataDir = `${this.stateDir}/.markbook`;
    this.tempDir = `${this.metadataDir}/tmp`;
    this.ensureDirectory(this.metadataDir);
    this.ensureDirectory(this.tempDir);
    if (this.hasVault()) {
      this.ensureVaultDirectories();
    }
    this.recoverInterruptedWrites();
  }

  static parentDirectory(uri: string): string {
    const normalized = uri.endsWith('/') ? uri.substring(0, uri.length - 1) : uri;
    const slash = normalized.lastIndexOf('/');
    return slash > 'file://'.length ? normalized.substring(0, slash) : '';
  }

  hasVault(): boolean {
    return this.rootDir.length > 0 && fs.accessSync(this.rootDir);
  }

  setVault(uri: string): void {
    const normalized = uri.trim();
    if (normalized.length === 0 || !this.isDirectory(normalized)) {
      throw new Error('Selected Vault is not a readable directory');
    }
    this.writeText(this.vaultUriPath, normalized);
    this.rootDir = normalized;
    this.notesDir = normalized;
    this.attachmentsDir = this.join(normalized, 'attachments');
    this.dailyNotesDir = this.join(normalized, DAILY_NOTES_DIR);
    this.ensureVaultDirectories();
    this.recoverInterruptedWrites();
  }

  list(): Array<Note> {
    const notes: Array<Note> = [];
    if (!this.hasVault()) {
      return notes;
    }
    const names = this.listFilesRecursively(this.rootDir);
    names.forEach((name: string) => {
      const relativePath = name;
      if (!relativePath.endsWith(NOTE_EXTENSION) ||
        this.isIgnoredPath(relativePath)) {
        return;
      }
      const path = this.notePath(relativePath);
      const content = fs.readTextSync(path);
      const stat = fs.statSync(path);
      notes.push(new Note(relativePath, MarkdownDocument.title(content), content, stat.mtime));
    });
    notes.sort((left: Note, right: Note) => right.modifiedAt - left.modifiedAt);
    return notes;
  }

  create(): Note {
    if (!this.hasVault()) {
      throw new Error('Select an Obsidian Vault first');
    }
    this.ensureVaultDirectories();
    const now = Date.now();
    const date = new Date(now);
    const year = date.getFullYear();
    const month = `${date.getMonth() + 1}`.padStart(2, '0');
    const day = `${date.getDate()}`.padStart(2, '0');
    const fileName = `${year}-${month}-${day}${NOTE_EXTENSION}`;
    const relativePath = `${DAILY_NOTES_DIR}/${fileName}`;
    if (fs.accessSync(this.notePath(relativePath))) {
      const content = fs.readTextSync(this.notePath(relativePath));
      return new Note(relativePath, MarkdownDocument.title(content), content,
        fs.statSync(this.notePath(relativePath)).mtime);
    }
    return new Note(relativePath, `${year}-${month}-${day}`, '', now);
  }

  save(note: Note): Note {
    const title = note.title.trim().length > 0 ? note.title.trim() : '未命名笔记';
    const body = MarkdownDocument.withTitle(title, note.content);
    const path = this.notePath(note.id);
    this.ensureParentDirectory(path);
    this.atomicWrite(path, body);
    return new Note(note.id, title, body, Date.now());
  }

  delete(noteId: string): void {
    const path = this.notePath(noteId);
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }

  private notePath(relativePath: string): string {
    if (relativePath.includes('..') || relativePath.startsWith('/')) {
      throw new Error('Invalid Vault relative path');
    }
    return this.join(this.rootDir, relativePath);
  }

  private atomicWrite(path: string, content: string): void {
    const tempPath = `${path}.tmp`;
    const backupPath = `${path}.bak`;
    this.safeUnlink(tempPath);

    try {
      const file = fs.openSync(
        tempPath,
        fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC
      );
      try {
        fs.writeSync(file.fd, content);
        fs.fsyncSync(file.fd);
      } finally {
        fs.closeSync(file);
      }
    } catch (error) {
      this.safeUnlink(tempPath);
      throw new Error(`Failed to write note draft: ${JSON.stringify(error)}`);
    }

    this.safeUnlink(backupPath);
    if (fs.accessSync(path)) {
      fs.renameSync(path, backupPath);
    }

    try {
      fs.renameSync(tempPath, path);
      this.safeUnlink(backupPath);
    } catch (error) {
      this.safeUnlink(tempPath);
      if (!fs.accessSync(path) && fs.accessSync(backupPath)) {
        fs.renameSync(backupPath, path);
      }
      throw new Error(`Failed to replace note file: ${JSON.stringify(error)}`);
    }
  }

  private writeText(path: string, content: string): void {
    const file = fs.openSync(
      path,
      fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC
    );
    try {
      fs.writeSync(file.fd, content);
      fs.fsyncSync(file.fd);
    } finally {
      fs.closeSync(file);
    }
  }

  private recoverInterruptedWrites(): void {
    if (!this.hasVault()) {
      return;
    }
    const names = this.listFilesRecursively(this.rootDir);
    names.forEach((name: string) => {
      const relativePath = name;
      const path = this.join(this.rootDir, relativePath);
      if (name.endsWith(`${NOTE_EXTENSION}.tmp`)) {
        this.safeUnlink(path);
        return;
      }
      if (name.endsWith(`${NOTE_EXTENSION}.bak`)) {
        const targetPath = path.slice(0, -4);
        if (fs.accessSync(targetPath)) {
          this.safeUnlink(path);
        } else {
          fs.renameSync(path, targetPath);
        }
      }
    });
  }

  private safeUnlink(path: string): void {
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }

  private ensureDirectory(path: string): void {
    if (path.length > 0 && !fs.accessSync(path)) {
      fs.mkdirSync(path, true);
    }
  }

  private ensureVaultDirectories(): void {
    this.ensureDirectory(this.attachmentsDir);
    this.ensureDirectory(this.dailyNotesDir);
  }

  private ensureParentDirectory(path: string): void {
    const lastSlash = path.lastIndexOf('/');
    if (lastSlash > 0) {
      this.ensureDirectory(path.slice(0, lastSlash));
    }
  }

  private readStoredVaultUri(): string {
    if (!fs.accessSync(this.vaultUriPath)) {
      return '';
    }
    try {
      const uri = fs.readTextSync(this.vaultUriPath).trim();
      if (uri.length > 0 && this.isDirectory(uri)) {
        return uri;
      }
    } catch (error) {
      return '';
    }
    return '';
  }

  private listFilesRecursively(directory: string, prefix: string = ''): Array<string> {
    const files: Array<string> = [];
    fs.listFileSync(directory).forEach((name: string) => {
      const relativeName = prefix.length > 0 ? `${prefix}/${name}` : name;
      const path = this.join(directory, name);
      try {
        fs.listFileSync(path);
        files.push(...this.listFilesRecursively(path, relativeName));
      } catch (error) {
        files.push(relativeName);
      }
    });
    return files;
  }

  private isDirectory(path: string): boolean {
    try {
      fs.listFileSync(path);
      return true;
    } catch (error) {
      return false;
    }
  }

  private isIgnoredPath(relativePath: string): boolean {
    return relativePath === '.obsidian' || relativePath.startsWith('.obsidian/') ||
      relativePath === '.markbook' || relativePath.startsWith('.markbook/') ||
      relativePath.startsWith('attachments/');
  }

  private join(base: string, child: string): string {
    return base.endsWith('/') ? `${base}${child}` : `${base}/${child}`;
  }
}
