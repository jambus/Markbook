import { Note } from './Note';

class NoteSearchEntry {
  readonly note: Note;
  readonly searchableText: string;

  constructor(note: Note) {
    this.note = note;
    this.searchableText = `${note.title}\n${note.content}`.toLocaleLowerCase();
  }
}

export class NoteSearchIndex {
  private entries: Array<NoteSearchEntry> = [];

  rebuild(notes: Array<Note>): void {
    this.entries = notes.map((note: Note): NoteSearchEntry => {
      return new NoteSearchEntry(note);
    });
  }

  search(query: string): Array<Note> {
    const normalized = query.trim().toLocaleLowerCase();
    if (normalized.length === 0) {
      return this.entries.map((entry: NoteSearchEntry): Note => entry.note);
    }
    const results: Array<Note> = [];
    this.entries.forEach((entry: NoteSearchEntry) => {
      if (entry.searchableText.includes(normalized)) {
        results.push(entry.note);
      }
    });
    return results;
  }
}
