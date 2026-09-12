import { common } from '@kit.AbilityKit';
import { picker } from '@kit.CoreFileKit';
import { promptAction } from '@kit.ArkUI';
import { MarkdownRichText } from '../model/MarkdownRichText';
import { Note } from '../model/Note';
import { NoteSearchIndex } from '../model/NoteSearchIndex';
import { AttachmentNaming } from '../model/AttachmentNaming';
import {
  AttachmentCaptureStatus,
  AttachmentResult,
  AttachmentService
} from '../services/AttachmentService';
import { NotebookRepository } from '../services/NotebookRepository';
import { PhotoPoint, PhotoTransformService } from '../services/PhotoTransformService';

enum ScreenMode {
  LIST,
  EDITOR
}

@Entry
@Component
struct Index {
  @State private notes: Array<Note> = [];
  @State private visibleNotes: Array<Note> = [];
  @State private searchQuery: string = '';
  @State private selectedNote: Note = new Note('', '', '', 0);
  @State private title: string = '';
  @State private saveStatus: string = '已保存';
  @State private mode: ScreenMode = ScreenMode.LIST;
  @State private isCapturing: boolean = false;
  @State private isPickingVault: boolean = false;
  @State private vaultLabel: string = '尚未选择 Vault';
  @State private photoEditorVisible: boolean = false;
  @State private pendingAttachment: AttachmentResult = new AttachmentResult('', '', '', '', '');
  @State private photoPoints: Array<PhotoPoint> = Index.defaultPhotoPoints();
  @State private photoPreviewWidth: number = 360;
  @State private photoPreviewHeight: number = 360;

  private repository?: NotebookRepository;
  private attachmentService?: AttachmentService;
  private readonly richEditorController: RichEditorController = new RichEditorController();
  private autosaveTimer: number = -1;
  private autosaveInterval: number = -1;
  private isDirty: boolean = false;
  private isLoadingEditor: boolean = false;
  private lastSavedContent: string = '';
  private lastSavedTitle: string = '';
  private readonly searchIndex: NoteSearchIndex = new NoteSearchIndex();
  private activeHandle: number = -1;
  private dragStartX: number = 0;
  private dragStartY: number = 0;

  private static defaultPhotoPoints(): Array<PhotoPoint> {
    return [
      new PhotoPoint(0.06, 0.06),
      new PhotoPoint(0.94, 0.06),
      new PhotoPoint(0.94, 0.94),
      new PhotoPoint(0.06, 0.94)
    ];
  }

  aboutToAppear(): void {
    const context = getContext(this) as common.UIAbilityContext;
    this.repository = new NotebookRepository(context);
    this.configureVaultServices(context);
    this.refreshNotes();
  }

  aboutToDisappear(): void {
    this.stopAutosavePolling();
    this.flushAutosave();
  }

  private configureVaultServices(context: common.UIAbilityContext): void {
    if (!this.repository || !this.repository.hasVault()) {
      this.vaultLabel = '尚未选择 Vault';
      this.attachmentService = undefined;
      return;
    }
    this.vaultLabel = this.repository.rootDir;
    this.attachmentService = new AttachmentService(
      context,
      this.repository.attachmentsDir,
      this.repository.tempDir,
      this.repository.rootDir
    );
  }

  private refreshNotes(): void {
    if (!this.repository) {
      return;
    }
    this.notes = this.repository.list();
    this.searchIndex.rebuild(this.notes);
    this.visibleNotes = this.searchIndex.search(this.searchQuery);
  }

  private updateSearch(query: string): void {
    this.searchQuery = query;
    this.visibleNotes = this.searchIndex.search(query);
  }

  private async chooseVault(): Promise<void> {
    if (!this.repository || this.isPickingVault) {
      return;
    }
    this.isPickingVault = true;
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const documentPicker = new picker.DocumentViewPicker(context);
      let result: Array<string> = [];
      try {
        result = await documentPicker.select({
          selectMode: picker.DocumentSelectMode.FOLDER,
          maxSelectNumber: 1
        });
      } catch (folderError) {
        promptAction.showToast({ message: '手机端请选择 Vault 内任意 Markdown 文件' });
        result = await documentPicker.select({
          fileSuffixFilters: ['Markdown|md,markdown'],
          maxSelectNumber: 1
        });
        if (result.length > 0) {
          result[0] = NotebookRepository.parentDirectory(result[0]);
        }
      }
      if (result.length === 0 || result[0].length === 0) {
        return;
      }
      this.repository.setVault(result[0]);
      this.configureVaultServices(context);
      this.refreshNotes();
      promptAction.showToast({ message: 'Vault 已连接' });
    } catch (error) {
      promptAction.showToast({ message: '无法打开这个 Vault，请选择可读写目录' });
    } finally {
      this.isPickingVault = false;
    }
  }

  private openNote(note: Note): void {
    this.cancelAutosave();
    this.stopAutosavePolling();
    this.selectedNote = note;
    this.title = note.title;
    this.isDirty = false;
    this.saveStatus = '已保存';
    this.mode = ScreenMode.EDITOR;
    this.isLoadingEditor = true;
  }

  private createNote(): void {
    if (!this.repository || !this.repository.hasVault()) {
      promptAction.showToast({ message: '请先选择 Obsidian Vault' });
      return;
    }
    try {
      this.openNote(this.repository.create());
    } catch (error) {
      promptAction.showToast({ message: '无法创建每日笔记' });
    }
  }

  private loadEditor(): void {
    if (!this.isLoadingEditor || !this.repository || !this.repository.hasVault()) {
      return;
    }
    MarkdownRichText.load(
      this.richEditorController,
      this.selectedNote.content,
      this.repository.rootDir
    );
    this.lastSavedContent = MarkdownRichText.serialize(
      this.richEditorController,
      this.repository.rootDir
    );
    this.lastSavedTitle = this.title;
    this.isLoadingEditor = false;
    this.startAutosavePolling();
  }

  private saveNote(showConfirmation: boolean = true): void {
    if (!this.repository || this.selectedNote.id.length === 0 || this.isLoadingEditor) {
      return;
    }
    this.cancelAutosave();
    const content = MarkdownRichText.serialize(
      this.richEditorController,
      this.repository.rootDir
    );
    const draft = new Note(
      this.selectedNote.id,
      this.title,
      content,
      this.selectedNote.modifiedAt
    );
    this.selectedNote = this.repository.save(draft);
    this.title = this.selectedNote.title;
    this.lastSavedContent = content;
    this.lastSavedTitle = this.title;
    this.isDirty = false;
    this.saveStatus = '已保存';
    this.refreshNotes();
    if (showConfirmation) {
      promptAction.showToast({ message: '已保存到 Vault' });
    }
  }

  private saveNoteSafely(showConfirmation: boolean = true): boolean {
    try {
      this.saveNote(showConfirmation);
      return true;
    } catch (error) {
      this.saveStatus = '保存失败';
      promptAction.showToast({ message: '保存失败，请检查 Vault 权限和存储空间' });
      return false;
    }
  }

  private scheduleAutosave(): void {
    if (this.isLoadingEditor) {
      return;
    }
    this.isDirty = true;
    this.saveStatus = '未保存';
    this.cancelAutosave();
    this.autosaveTimer = setTimeout(() => {
      this.autosaveTimer = -1;
      this.flushAutosave();
    }, 700);
  }

  private flushAutosave(): void {
    if (!this.isDirty || this.mode !== ScreenMode.EDITOR) {
      return;
    }
    this.saveStatus = '正在保存';
    try {
      this.saveNote(false);
    } catch (error) {
      this.saveStatus = '保存失败';
    }
  }

  private cancelAutosave(): void {
    if (this.autosaveTimer >= 0) {
      clearTimeout(this.autosaveTimer);
      this.autosaveTimer = -1;
    }
  }

  private startAutosavePolling(): void {
    this.stopAutosavePolling();
    this.autosaveInterval = setInterval(() => this.checkEditorSnapshot(), 1000);
  }

  private stopAutosavePolling(): void {
    if (this.autosaveInterval >= 0) {
      clearInterval(this.autosaveInterval);
      this.autosaveInterval = -1;
    }
  }

  private checkEditorSnapshot(): void {
    if (this.mode !== ScreenMode.EDITOR || this.isLoadingEditor || !this.repository) {
      return;
    }
    const content = MarkdownRichText.serialize(
      this.richEditorController,
      this.repository.rootDir
    );
    if (content !== this.lastSavedContent || this.title !== this.lastSavedTitle) {
      this.scheduleAutosave();
    }
  }

  private async capturePhoto(): Promise<void> {
    if (!this.attachmentService || this.isCapturing || this.isLoadingEditor) {
      return;
    }
    this.isCapturing = true;
    let attachment: AttachmentResult | undefined = undefined;
    try {
      const outcome = await this.attachmentService.capturePhoto();
      if (outcome.status === AttachmentCaptureStatus.CANCELLED || !outcome.attachment) {
        return;
      }
      attachment = outcome.attachment;
      this.pendingAttachment = attachment;
      this.photoPoints = Index.defaultPhotoPoints();
      this.photoEditorVisible = true;
    } catch (error) {
      if (attachment) {
        this.attachmentService.rollback(attachment);
      }
      promptAction.showToast({ message: '拍照未完成，请重试' });
    } finally {
      this.isCapturing = false;
    }
  }

  private cancelPhotoEdit(): void {
    if (this.pendingAttachment.localPath.length > 0 && this.attachmentService) {
      this.attachmentService.rollback(this.pendingAttachment);
    }
    this.pendingAttachment = new AttachmentResult('', '', '', '', '');
    this.photoEditorVisible = false;
  }

  private async insertOriginalPhoto(): Promise<void> {
    await this.finishPhotoInsert(this.pendingAttachment, false);
  }

  private async insertCorrectedPhoto(): Promise<void> {
    if (!this.repository || !this.attachmentService || this.pendingAttachment.localPath.length === 0) {
      return;
    }
    try {
      const descriptor = AttachmentNaming.photo(this.pendingAttachment.attachmentId, 'jpg', 'corrected');
      const targetPath = `${this.repository.attachmentsDir}/${descriptor.fileName}`;
      this.attachmentService.recordVariant(this.pendingAttachment, descriptor.fileName);
      await PhotoTransformService.writePerspectiveJpeg(
        this.pendingAttachment.localPath,
        targetPath,
        this.photoPoints
      );
      const corrected = new AttachmentResult(
        this.fileUri(targetPath),
        descriptor.markdown,
        targetPath,
        descriptor.mimeType,
        '',
        this.pendingAttachment.attachmentId
      );
      await this.finishPhotoInsert(corrected, true);
    } catch (error) {
      this.attachmentService.rollback(this.pendingAttachment);
      this.photoEditorVisible = false;
      promptAction.showToast({ message: '图片校正失败，请重新拍摄' });
    }
  }

  private async finishPhotoInsert(attachment: AttachmentResult, corrected: boolean): Promise<void> {
    if (!this.attachmentService) {
      return;
    }
    try {
      const offset = this.richEditorController.getCaretOffset();
      this.richEditorController.addTextSpan('\n', { offset });
      this.richEditorController.addImageSpan(attachment.uri, {
        offset: offset + 1,
        imageStyle: { size: ['100%', 220], objectFit: ImageFit.Contain }
      });
      this.richEditorController.addTextSpan('\n', { offset: offset + 2 });
      this.saveNote(false);
      this.attachmentService.confirm(this.pendingAttachment);
      this.pendingAttachment = new AttachmentResult('', '', '', '', '');
      this.photoEditorVisible = false;
      promptAction.showToast({ message: corrected ? '原图与校正图已保存' : '照片已插入并保存' });
    } catch (error) {
      if (corrected) {
        this.attachmentService.rollback(attachment);
      }
      this.attachmentService.rollback(this.pendingAttachment);
      throw new Error('Photo insertion failed');
    }
  }

  private fileUri(path: string): string {
    return path.includes('://') ? path : `file://${path}`;
  }

  private beginHandleDrag(index: number): void {
    this.activeHandle = index;
    this.dragStartX = this.photoPoints[index].x;
    this.dragStartY = this.photoPoints[index].y;
  }

  private moveHandle(index: number, event: GestureEvent): void {
    if (this.activeHandle !== index) {
      return;
    }
    const point = this.photoPoints[index];
    point.x = Math.max(0.02, Math.min(0.98,
      this.dragStartX + event.offsetX / this.photoPreviewWidth));
    point.y = Math.max(0.02, Math.min(0.98,
      this.dragStartY + event.offsetY / this.photoPreviewHeight));
    this.photoPoints = [...this.photoPoints];
  }

  @Builder
  private listPage(): void {
    Column() {
      Row() {
        Column() {
          Text($r('app.string.EntryAbility_label'))
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.ink'))
          Text('本地 Obsidian Vault')
            .fontSize(13)
            .fontColor($r('app.color.muted'))
            .margin({ top: 2 })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        Button(this.isPickingVault ? '打开中' : '选择 Vault')
          .fontSize(14)
          .fontColor($r('app.color.ink'))
          .backgroundColor($r('app.color.surface'))
          .borderRadius(6)
          .height(40)
          .enabled(!this.isPickingVault)
          .onClick(() => this.chooseVault())
      }
      .width('100%')
      .padding({ left: 20, right: 20, top: 20, bottom: 8 })

      Text(this.vaultLabel)
        .fontSize(12)
        .fontColor($r('app.color.muted'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
        .padding({ left: 20, right: 20, bottom: 12 })

      Search({ value: this.searchQuery, placeholder: '搜索标题或正文' })
        .height(42)
        .margin({ left: 16, right: 16, bottom: 12 })
        .backgroundColor($r('app.color.surface'))
        .onChange((value: string) => this.updateSearch(value))

      if (!this.repository || !this.repository.hasVault()) {
        Column({ space: 12 }) {
          Text('先连接一个 Obsidian Vault')
            .fontSize(20)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.ink'))
          Text('笔记会直接保存为 Markdown 文件，电脑端可用 Obsidian 编辑。')
            .fontSize(14)
            .fontColor($r('app.color.muted'))
            .textAlign(TextAlign.Center)
          Button('选择 Vault')
            .fontColor(Color.White)
            .backgroundColor($r('app.color.brand'))
            .borderRadius(6)
            .onClick(() => this.chooseVault())
        }
        .layoutWeight(1)
        .width('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.notes.length === 0) {
        Column({ space: 12 }) {
          Text('还没有笔记')
            .fontSize(20)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.ink'))
          Text('创建今天的笔记，拍下眼前的内容。')
            .fontSize(14)
            .fontColor($r('app.color.muted'))
          Button('创建每日笔记')
            .fontColor(Color.White)
            .backgroundColor($r('app.color.brand'))
            .borderRadius(6)
            .onClick(() => this.createNote())
        }
        .layoutWeight(1)
        .width('100%')
        .justifyContent(FlexAlign.Center)
      } else if (this.visibleNotes.length === 0) {
        Column({ space: 8 }) {
          Text('没有匹配的笔记')
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.ink'))
          Text('换一个标题或正文关键词')
            .fontSize(13)
            .fontColor($r('app.color.muted'))
        }
        .layoutWeight(1)
        .width('100%')
        .justifyContent(FlexAlign.Center)
      } else {
        List({ space: 8 }) {
          ForEach(this.visibleNotes, (note: Note) => {
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
                  Text(note.id)
                    .fontSize(11)
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

      if (this.repository && this.repository.hasVault()) {
        Button('新建每日笔记')
          .fontSize(15)
          .fontColor(Color.White)
          .backgroundColor($r('app.color.brand'))
          .borderRadius(6)
          .height(44)
          .margin({ left: 16, right: 16, bottom: 14 })
          .onClick(() => this.createNote())
      }
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private editorPage(): void {
    Column() {
      Row({ space: 8 }) {
        Button('返回')
          .fontSize(14)
          .fontColor($r('app.color.ink'))
          .backgroundColor(Color.Transparent)
          .height(40)
          .onClick(() => {
            if (this.saveNoteSafely(false)) {
              this.mode = ScreenMode.LIST;
            }
          })
        TextInput({ text: this.title, placeholder: '每日笔记标题' })
          .fontSize(19)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.ink'))
          .backgroundColor(Color.Transparent)
          .layoutWeight(1)
          .height(44)
          .onChange((value: string) => {
            this.title = value;
            this.scheduleAutosave();
          })
        Button('保存')
          .fontSize(14)
          .fontColor(Color.White)
          .backgroundColor($r('app.color.brand'))
          .borderRadius(6)
          .height(38)
          .onClick(() => this.saveNoteSafely())
      }
      .width('100%')
      .padding({ left: 10, right: 14, top: 10, bottom: 8 })
      .border({ width: { bottom: 1 }, color: $r('app.color.line') })

      RichEditor({ controller: this.richEditorController })
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 16, right: 16, top: 12, bottom: 12 })
        .onReady(() => this.loadEditor())

      Row() {
        Text(`富文本 · ${this.saveStatus}`)
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

  @Builder
  private photoEditor(): void {
    Column() {
      Text('调整照片')
        .fontSize(20)
        .fontWeight(FontWeight.Bold)
        .fontColor(Color.White)
        .margin({ bottom: 12 })
      Stack() {
        Image(this.pendingAttachment.uri)
          .width('100%')
          .height(360)
          .objectFit(ImageFit.Fill)
        ForEach(this.photoPoints, (point: PhotoPoint, index: number) => {
          Circle()
            .width(30)
            .height(30)
            .fill($r('app.color.accent'))
            .border({ width: 2, color: Color.White })
            .position({ x: `${point.x * 100}%`, y: `${point.y * 100}%` })
            .gesture(
              PanGesture()
                .onActionStart(() => this.beginHandleDrag(index))
                .onActionUpdate((event: GestureEvent) => this.moveHandle(index, event))
            )
        }, (point: PhotoPoint, index: number) => `${index}`)
      }
      .width('100%')
      .height(360)
      .backgroundColor(Color.Black)
      .onAreaChange((oldValue: Area, newValue: Area) => {
        this.photoPreviewWidth = Number(newValue.width);
        this.photoPreviewHeight = Number(newValue.height);
      })
      Row({ space: 8 }) {
        Button('恢复矩形')
          .layoutWeight(1)
          .onClick(() => this.photoPoints = Index.defaultPhotoPoints())
        Button('直接插入原图')
          .layoutWeight(1)
          .onClick(() => this.insertOriginalPhoto())
      }
      .width('100%')
      .margin({ top: 14 })
      Row({ space: 8 }) {
        Button('取消')
          .layoutWeight(1)
          .backgroundColor($r('app.color.surface'))
          .fontColor($r('app.color.ink'))
          .onClick(() => this.cancelPhotoEdit())
        Button('裁剪并校正')
          .layoutWeight(1)
          .backgroundColor($r('app.color.accent'))
          .fontColor(Color.White)
          .onClick(() => this.insertCorrectedPhoto())
      }
      .width('100%')
      .margin({ top: 8 })
    }
    .width('100%')
    .padding(18)
    .backgroundColor($r('app.color.ink'))
    .borderRadius(8)
    .position({ x: '0%', y: '50%' })
  }

  build(): void {
    Stack() {
      if (this.mode === ScreenMode.EDITOR) {
        this.editorPage();
      } else {
        this.listPage();
      }
      if (this.photoEditorVisible) {
        Column() {
          this.photoEditor();
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
        .backgroundColor('#99000000')
      }
    }
    .width('100%')
    .height('100%')
  }
}
