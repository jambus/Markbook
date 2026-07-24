export class Note {
  id: string;
  title: string;
  content: string;
  modifiedAt: number;

  constructor(id: string, title: string, content: string, modifiedAt: number) {
    this.id = id;
    this.title = title;
    this.content = content;
    this.modifiedAt = modifiedAt;
  }
}

