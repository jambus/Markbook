import { fileIo as fs } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { MarkdownDocument } from '../model/MarkdownDocument';
import { Note } from '../model/Note';

const NOTE_EXTENSION = '.md';

export class NotebookRepository {
  readonly rootDir: string;
  private readonly notesDir: string;
  readonly attachmentsDir: string;

  constructor(context: common.UIAbilityContext) {
    this.rootDir = `${context.filesDir}/Markbook`;
    this.notesDir = `${this.rootDir}/notes`;
    this.attachmentsDir = `${this.rootDir}/attachments`;
    this.ensureDirectory(this.rootDir);
    this.ensureDirectory(this.notesDir);
    this.ensureDirectory(this.attachmentsDir);
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
    const file = fs.openSync(
      this.notePath(note.id),
      fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC
    );
    try {
      fs.writeSync(file.fd, body);
    } finally {
      fs.closeSync(file);
    }
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

  private ensureDirectory(path: string): void {
    if (!fs.accessSync(path)) {
      fs.mkdirSync(path);
    }
  }
}
