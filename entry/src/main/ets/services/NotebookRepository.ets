import { fileIo as fs } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { MarkdownDocument } from '../model/MarkdownDocument';
import { Note } from '../model/Note';

const NOTE_EXTENSION = '.md';

export class NotebookRepository {
  readonly rootDir: string;
  private readonly notesDir: string;
  readonly attachmentsDir: string;
  readonly metadataDir: string;
  readonly tempDir: string;

  constructor(context: common.UIAbilityContext) {
    this.rootDir = `${context.filesDir}/Markbook`;
    this.notesDir = `${this.rootDir}/notes`;
    this.attachmentsDir = `${this.rootDir}/attachments`;
    this.metadataDir = `${this.rootDir}/.markbook`;
    this.tempDir = `${this.metadataDir}/tmp`;
    this.ensureDirectory(this.rootDir);
    this.ensureDirectory(this.notesDir);
    this.ensureDirectory(this.attachmentsDir);
    this.ensureDirectory(this.metadataDir);
    this.ensureDirectory(this.tempDir);
    this.recoverInterruptedWrites();
  }

  list(): Array<Note> {
    const notes: Array<Note> = [];
    const names = fs.listFileSync(this.notesDir);
    names.forEach((name: string) => {
      if (!name.endsWith(NOTE_EXTENSION)) {
        return;
      }
      const id = name.slice(0, -NOTE_EXTENSION.length);
      const path = this.notePath(id);
      const content = fs.readTextSync(path);
      const stat = fs.statSync(path);
      notes.push(new Note(id, MarkdownDocument.title(content), content, stat.mtime));
    });
    notes.sort((left: Note, right: Note) => right.modifiedAt - left.modifiedAt);
    return notes;
  }

  create(): Note {
    const now = Date.now();
    return new Note(`${now}`, '未命名笔记', '', now);
  }

  save(note: Note): Note {
    const title = note.title.trim().length > 0 ? note.title.trim() : '未命名笔记';
    const body = MarkdownDocument.withTitle(title, note.content);
    this.atomicWrite(this.notePath(note.id), body);
    return new Note(note.id, title, body, Date.now());
  }

  delete(noteId: string): void {
    const path = this.notePath(noteId);
    if (fs.accessSync(path)) {
      fs.unlinkSync(path);
    }
  }

  private notePath(id: string): string {
    return `${this.notesDir}/${id}${NOTE_EXTENSION}`;
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

  private recoverInterruptedWrites(): void {
    const names = fs.listFileSync(this.notesDir);
    names.forEach((name: string) => {
      const path = `${this.notesDir}/${name}`;
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
    if (!fs.accessSync(path)) {
      fs.mkdirSync(path);
    }
  }
}
