import { common } from '@kit.AbilityKit';
import { Note } from '../model/Note';
import { AttachmentService } from '../services/AttachmentService';
import { NotebookRepository } from '../services/NotebookRepository';

enum ScreenMode {
  LIST,
  EDITOR
}

@Entry
@Component
struct Index {
  @State private notes: Array<Note> = [];
  @State private selectedNote: Note = new Note('', '', '', 0);
  @State private title: string = '';
  @State private content: string = '';
  @State private mode: ScreenMode = ScreenMode.LIST;
  @State private isCapturing: boolean = false;
  @State private lastPhotoUri: string = '';

  private repository?: NotebookRepository;
  private attachmentService?: AttachmentService;

  aboutToAppear(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.repository = new NotebookRepository(context);
    this.attachmentService = new AttachmentService(context, this.repository.attachmentsDir);
    this.refreshNotes();
  }

  private refreshNotes(): void {
    if (this.repository) {
      this.notes = this.repository.list();
    }
  }

  private openNote(note: Note): void {
    this.selectedNote = note;
    this.title = note.title;
    this.content = note.content;
    this.lastPhotoUri = '';
    this.mode = ScreenMode.EDITOR;
  }

  private createNote(): void {
    if (this.repository) {
      this.openNote(this.repository.create());
    }
  }

  private saveNote(showConfirmation: boolean = true): void {
    if (!this.repository) {
      return;
    }
    const draft = new Note(
      this.selectedNote.id,
      this.title,
      this.content,
      this.selectedNote.modifiedAt
    );
    this.selectedNote = this.repository.save(draft);
    this.title = this.selectedNote.title;
    this.content = this.selectedNote.content;
    this.refreshNotes();
    if (showConfirmation) {
      this.getUIContext().getPromptAction().showToast({ message: '已保存到本地' });
    }
  }

  private async capturePhoto(): Promise<void> {
    if (!this.attachmentService || this.isCapturing) {
      return;
    }
    this.isCapturing = true;
    try {
      const attachment = await this.attachmentService.capturePhoto();
      if (attachment) {
        const separator = this.content.length > 0 && !this.content.endsWith('\n') ? '\n\n' : '';
        this.content = `${this.content}${separator}${attachment.markdown}\n`;
        this.lastPhotoUri = attachment.uri;
        this.saveNote(false);
        this.getUIContext().getPromptAction().showToast({ message: '照片已插入并保存' });
      }
    } catch (error) {
      this.getUIContext().getPromptAction().showToast({ message: '拍照未完成，请重试' });
    } finally {
      this.isCapturing = false;
    }
  }

  private formatTime(timestamp: number): string {
    const date = new Date(timestamp);
    const month = `${date.getMonth() + 1}`.padStart(2, '0');
    const day = `${date.getDate()}`.padStart(2, '0');
    const hour = `${date.getHours()}`.padStart(2, '0');
    const minute = `${date.getMinutes()}`.padStart(2, '0');
    return `${month}-${day} ${hour}:${minute}`;
  }

  @Builder
  private listPage() {
    Column() {
      Row() {
        Column() {
          Text('Markbook')
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.ink'))
          Text('本地 Markdown 笔记')
            .fontSize(13)
            .fontColor($r('app.color.muted'))
            .margin({ top: 2 })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        Button('新建')
          .fontSize(15)
          .fontColor(Color.White)
          .backgroundColor($r('app.color.brand'))
          .borderRadius(6)
          .height(40)
          .onClick(() => this.createNote())
      }
      .width('100%')
      .padding({ left: 20, right: 20, top: 20, bottom: 14 })

      if (this.notes.length === 0) {
        Column({ space: 12 }) {
          Text('还没有笔记')
            .fontSize(20)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.ink'))
          Text('创建第一篇笔记，拍下眼前的内容。')
            .fontSize(14)
            .fontColor($r('app.color.muted'))
          Button('创建笔记')
            .fontColor($r('app.color.brand'))
            .backgroundColor('#E8F0EB')
            .borderRadius(6)
            .margin({ top: 8 })
            .onClick(() => this.createNote())
        }
        .layoutWeight(1)
        .width('100%')
        .justifyContent(FlexAlign.Center)
      } else {
        List({ space: 8 }) {
          ForEach(this.notes, (note: Note) => {
            ListItem() {
              Column() {
                Row() {
                  Text(note.title)
                    .fontSize(17)
                    .fontWeight(FontWeight.Medium)
                    .fontColor($r('app.color.ink'))
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .layoutWeight(1)
                  Text(this.formatTime(note.modifiedAt))
                    .fontSize(12)
                    .fontColor($r('app.color.muted'))
                }
                .width('100%')

                Text(note.content.split('\n').join(' '))
                  .fontSize(13)
                  .fontColor($r('app.color.muted'))
                  .maxLines(2)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .width('100%')
                  .margin({ top: 8 })
              }
              .alignItems(HorizontalAlign.Start)
              .padding(16)
              .width('100%')
              .backgroundColor($r('app.color.surface'))
              .borderRadius(6)
              .border({ width: 1, color: $r('app.color.line') })
              .onClick(() => this.openNote(note))
            }
          }, (note: Note) => note.id)
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 16 })
      }
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private editorPage() {
    Column() {
      Row({ space: 10 }) {
        Button('返回')
          .fontSize(14)
          .fontColor($r('app.color.ink'))
          .backgroundColor(Color.Transparent)
          .height(40)
          .padding({ left: 8, right: 8 })
          .onClick(() => {
            this.saveNote(false);
            this.mode = ScreenMode.LIST;
          })
        TextInput({ text: this.title, placeholder: '笔记标题' })
          .fontSize(19)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.ink'))
          .backgroundColor(Color.Transparent)
          .layoutWeight(1)
          .height(44)
          .onChange((value: string) => this.title = value)
        Button('保存')
          .fontSize(14)
          .fontColor(Color.White)
          .backgroundColor($r('app.color.brand'))
          .borderRadius(6)
          .height(38)
          .onClick(() => this.saveNote())
      }
      .width('100%')
      .padding({ left: 10, right: 14, top: 10, bottom: 8 })
      .border({ width: { bottom: 1 }, color: $r('app.color.line') })

      TextArea({
        text: this.content,
        placeholder: '用 Markdown 开始记录...\n\n# 标题\n- 清单\n**重点**'
      })
        .fontSize(16)
        .fontColor($r('app.color.ink'))
        .placeholderColor('#9AA39E')
        .backgroundColor(Color.Transparent)
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 18, right: 18, top: 16, bottom: 16 })
        .onChange((value: string) => this.content = value)

      if (this.lastPhotoUri.length > 0) {
        Image(this.lastPhotoUri)
          .width('100%')
          .height(112)
          .objectFit(ImageFit.Cover)
          .padding({ left: 18, right: 18, bottom: 10 })
      }

      Row() {
        Text('Markdown')
          .fontSize(12)
          .fontColor($r('app.color.muted'))
          .layoutWeight(1)
        Button(this.isCapturing ? '处理中' : '拍照插入')
          .fontSize(15)
          .fontColor(Color.White)
          .backgroundColor($r('app.color.accent'))
          .borderRadius(6)
          .height(42)
          .enabled(!this.isCapturing)
          .onClick(() => this.capturePhoto())
      }
      .width('100%')
      .padding({ left: 18, right: 18, top: 10, bottom: 12 })
      .backgroundColor($r('app.color.surface'))
      .border({ width: { top: 1 }, color: $r('app.color.line') })
    }
    .width('100%')
    .height('100%')
  }

  build() {
    Stack() {
      if (this.mode === ScreenMode.LIST) {
        this.listPage()
      } else {
        this.editorPage()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.page_background'))
  }
}
