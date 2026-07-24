import { common } from '@kit.AbilityKit';
import { Note } from '../model/Note';
import { AttachmentResult, AttachmentService } from '../services/AttachmentService';
import { NotebookRepository } from '../services/NotebookRepository';
import {
  OAuthCallbackInbox
} from '../services/sync/onedrive/OAuthCallbackInbox';
import type {
  OAuthCallbackListener
} from '../services/sync/onedrive/OAuthCallbackInbox';
import {
  OneDriveSyncCoordinator
} from '../services/sync/onedrive/OneDriveSyncCoordinator';

enum ScreenMode {
  LIST,
  EDITOR,
  SYNC
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
  @State private isSyncBusy: boolean = false;
  @State private isOneDriveConnected: boolean = false;
  @State private syncStatus: string = 'OneDrive 未连接';
  @State private hasConfirmedRemoteSync: boolean = false;
  @State private isCancellingSync: boolean = false;
  @State private isSyncOperationActive: boolean = false;

  private repository?: NotebookRepository;
  private attachmentService?: AttachmentService;
  private oneDrive?: OneDriveSyncCoordinator;
  private readonly oauthListener: OAuthCallbackListener = (uri: string) => {
    this.handleOAuthCallback(uri);
  };

  aboutToAppear(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.repository = new NotebookRepository(context);
    this.attachmentService = new AttachmentService(
      context,
      this.repository.attachmentsDir,
      this.repository.tempDir
    );
    const clientId = context.resourceManager.getStringByNameSync('onedrive_client_id');
    this.oneDrive = new OneDriveSyncCoordinator(
      context,
      this.repository,
      clientId
    );
    OAuthCallbackInbox.shared().subscribe(this.oauthListener);
    const pendingCallback = OAuthCallbackInbox.shared().take();
    if (pendingCallback) {
      this.handleOAuthCallback(pendingCallback);
    } else {
      this.restoreOneDriveSession();
    }
    this.refreshNotes();
  }

  aboutToDisappear(): void {
    OAuthCallbackInbox.shared().unsubscribe(this.oauthListener);
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
    const attachmentService = this.attachmentService;
    this.isCapturing = true;
    let attachment: AttachmentResult | undefined = undefined;
    const previousContent = this.content;
    const previousPhotoUri = this.lastPhotoUri;
    let didSave: boolean = false;
    try {
      attachment = await attachmentService.capturePhoto();
      if (attachment) {
        const separator = this.content.length > 0 && !this.content.endsWith('\n') ? '\n\n' : '';
        this.content = `${this.content}${separator}${attachment.markdown}\n`;
        this.lastPhotoUri = attachment.uri;
        this.saveNote(false);
        didSave = true;
      }
    } catch (error) {
      if (attachment && !didSave) {
        attachmentService.rollback(attachment);
      }
      if (!didSave) {
        this.content = previousContent;
        this.lastPhotoUri = previousPhotoUri;
      }
      this.getUIContext().getPromptAction().showToast({ message: '拍照未完成，请重试' });
    } finally {
      this.isCapturing = false;
    }
    if (didSave) {
      this.getUIContext().getPromptAction().showToast({ message: '照片已插入并保存' });
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

  private async restoreOneDriveSession(): Promise<void> {
    if (!this.oneDrive || !this.oneDrive.isConfigured()) {
      this.syncStatus = '需要配置 Microsoft Entra 客户端 ID';
      return;
    }
    this.isSyncBusy = true;
    this.syncStatus = '正在检查 OneDrive 会话';
    const connected = await this.oneDrive.restoreSession();
    this.isOneDriveConnected = connected;
    this.syncStatus = connected ? 'OneDrive 已连接' : 'OneDrive 未连接';
    this.isSyncBusy = false;
  }

  private async connectOneDrive(): Promise<void> {
    if (!this.oneDrive || !this.oneDrive.isConfigured() || this.isSyncBusy) {
      this.getUIContext().getPromptAction().showToast({
        message: '请先配置 Microsoft Entra 客户端 ID'
      });
      return;
    }
    this.isSyncBusy = true;
    this.syncStatus = '等待浏览器授权';
    try {
      await this.oneDrive.beginAuthorization();
    } catch (error) {
      this.syncStatus = '无法打开 Microsoft 登录页';
      this.isSyncBusy = false;
    }
  }

  private async handleOAuthCallback(uri: string): Promise<void> {
    if (!this.oneDrive) {
      return;
    }
    this.isSyncBusy = true;
    this.syncStatus = '正在完成 OneDrive 授权';
    try {
      await this.oneDrive.completeAuthorization(uri);
      this.isOneDriveConnected = true;
      this.syncStatus = 'OneDrive 已连接';
    } catch (error) {
      this.isOneDriveConnected = false;
      this.syncStatus = '授权未完成，请重新连接';
    } finally {
      this.isSyncBusy = false;
    }
  }

  private async syncNow(): Promise<void> {
    if (!this.oneDrive || this.isSyncBusy || !this.isOneDriveConnected) {
      return;
    }
    this.isSyncBusy = true;
    this.isSyncOperationActive = true;
    this.syncStatus = '正在同步';
    try {
      const result = await this.oneDrive.sync();
      this.syncStatus =
        `同步完成：上传 ${result.uploaded}，下载 ${result.downloaded}，冲突 ${result.conflicts}`;
      this.refreshNotes();
    } catch (error) {
      this.syncStatus = this.isCancellingSync ?
        '同步已取消' :
        '同步失败，本地文件未丢失';
    } finally {
      this.isSyncBusy = false;
      this.isCancellingSync = false;
      this.isSyncOperationActive = false;
    }
  }

  private cancelSync(): void {
    if (this.oneDrive && this.isSyncBusy) {
      this.isCancellingSync = true;
      this.syncStatus = '正在取消';
      this.oneDrive.cancel();
      if (!this.isSyncOperationActive) {
        this.isSyncBusy = false;
        this.isCancellingSync = false;
        this.syncStatus = this.isOneDriveConnected ?
          'OneDrive 已连接' :
          'OneDrive 未连接';
      }
    }
  }

  private async disconnectOneDrive(): Promise<void> {
    if (!this.oneDrive || this.isSyncBusy) {
      return;
    }
    this.isSyncBusy = true;
    try {
      await this.oneDrive.disconnect();
      this.isOneDriveConnected = false;
      this.hasConfirmedRemoteSync = false;
      this.syncStatus = 'OneDrive 未连接';
    } finally {
      this.isSyncBusy = false;
    }
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

        Row({ space: 8 }) {
          Button('同步')
            .fontSize(14)
            .fontColor($r('app.color.ink'))
            .backgroundColor($r('app.color.surface'))
            .borderRadius(6)
            .height(40)
            .onClick(() => this.mode = ScreenMode.SYNC)
          Button('新建')
            .fontSize(15)
            .fontColor(Color.White)
            .backgroundColor($r('app.color.brand'))
            .borderRadius(6)
            .height(40)
            .onClick(() => this.createNote())
        }
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

  @Builder
  private syncPage() {
    Column() {
      Row({ space: 8 }) {
        Button('返回')
          .fontSize(14)
          .fontColor($r('app.color.ink'))
          .backgroundColor(Color.Transparent)
          .height(40)
          .padding({ left: 8, right: 8 })
          .onClick(() => this.mode = ScreenMode.LIST)
        Text('同步')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.ink'))
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 10, right: 18, top: 12, bottom: 10 })
      .border({ width: { bottom: 1 }, color: $r('app.color.line') })

      Column({ space: 12 }) {
        Text('OneDrive')
          .fontSize(19)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.ink'))
          .width('100%')
        Text(this.syncStatus)
          .fontSize(14)
          .fontColor($r('app.color.muted'))
          .width('100%')

        if (this.isOneDriveConnected) {
          Row({ space: 10 }) {
            Checkbox({ name: 'confirm-onedrive-scope' })
              .select(this.hasConfirmedRemoteSync)
              .onChange((selected: boolean) => {
                this.hasConfirmedRemoteSync = selected;
              })
            Text('允许将笔记和附件同步到 OneDrive 应用专属目录')
              .fontSize(13)
              .fontColor($r('app.color.ink'))
              .layoutWeight(1)
          }
          .width('100%')

          Button(this.isSyncBusy ? '同步中' : '立即同步')
            .width('100%')
            .height(44)
            .fontColor(Color.White)
            .backgroundColor($r('app.color.brand'))
            .borderRadius(6)
            .enabled(!this.isSyncBusy && this.hasConfirmedRemoteSync)
            .onClick(() => this.syncNow())
          Button('断开连接')
            .width('100%')
            .height(42)
            .fontColor($r('app.color.ink'))
            .backgroundColor($r('app.color.surface'))
            .borderRadius(6)
            .enabled(!this.isSyncBusy)
            .onClick(() => this.disconnectOneDrive())
        } else {
          Button(this.isSyncBusy ? '处理中' : '连接 OneDrive')
            .width('100%')
            .height(44)
            .fontColor(Color.White)
            .backgroundColor($r('app.color.brand'))
            .borderRadius(6)
            .enabled(!this.isSyncBusy)
            .onClick(() => this.connectOneDrive())
        }

        if (this.isSyncBusy) {
          Button('取消')
            .width('100%')
            .height(40)
            .fontColor($r('app.color.ink'))
            .backgroundColor(Color.Transparent)
            .onClick(() => this.cancelSync())
        }
      }
      .alignItems(HorizontalAlign.Start)
      .width('100%')
      .padding({ left: 20, right: 20, top: 24 })

      Blank()

      Text('同步范围：笔记和附件；凭据与本机同步状态不会上传')
        .fontSize(12)
        .fontColor($r('app.color.muted'))
        .width('100%')
        .padding({ left: 20, right: 20, bottom: 18 })
    }
    .width('100%')
    .height('100%')
  }

  build() {
    Stack() {
      if (this.mode === ScreenMode.LIST) {
        this.listPage()
      } else if (this.mode === ScreenMode.EDITOR) {
        this.editorPage()
      } else {
        this.syncPage()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.page_background'))
  }
}
