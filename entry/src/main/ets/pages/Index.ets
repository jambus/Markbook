import { common } from '@kit.AbilityKit';
import { promptAction } from '@kit.ArkUI';
import { MarkdownDocument } from '../model/MarkdownDocument';
import { Note } from '../model/Note';
import { NoteSearchIndex } from '../model/NoteSearchIndex';
import {
  AttachmentCaptureStatus,
  AttachmentResult,
  AttachmentService
} from '../services/AttachmentService';
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
import {
  S3Configuration
} from '../services/sync/s3/S3Provider';
import {
  S3SyncCoordinator
} from '../services/sync/s3/S3SyncCoordinator';
import {
  S3Credentials
} from '../services/sync/s3/S3Signer';
import { SyncPhase, SyncProgress } from '../services/sync/SyncModels';

enum ScreenMode {
  LIST,
  EDITOR,
  SYNC
}

enum SyncTarget {
  ONEDRIVE,
  NAS
}

@Entry
@Component
struct Index {
  @State private notes: Array<Note> = [];
  @State private visibleNotes: Array<Note> = [];
  @State private searchQuery: string = '';
  @State private selectedNote: Note = new Note('', '', '', 0);
  @State private title: string = '';
  @State private content: string = '';
  @State private saveStatus: string = '已保存';
  @State private mode: ScreenMode = ScreenMode.LIST;
  @State private isCapturing: boolean = false;
  @State private lastPhotoUri: string = '';
  @State private isSyncBusy: boolean = false;
  @State private isOneDriveConnected: boolean = false;
  @State private syncStatus: string = 'OneDrive 未连接';
  @State private hasConfirmedRemoteSync: boolean = false;
  @State private isCancellingSync: boolean = false;
  @State private isSyncOperationActive: boolean = false;
  @State private syncDetail: string = '';
  @State private syncTarget: SyncTarget = SyncTarget.ONEDRIVE;
  @State private isNasConnected: boolean = false;
  @State private nasEndpoint: string = '';
  @State private nasRegion: string = 'us-east-1';
  @State private nasBucket: string = '';
  @State private nasPrefix: string = 'Markbook';
  @State private nasAccessKey: string = '';
  @State private nasSecretKey: string = '';
  @State private hasConfirmedNasSync: boolean = false;

  private repository?: NotebookRepository;
  private attachmentService?: AttachmentService;
  private oneDrive?: OneDriveSyncCoordinator;
  private nas?: S3SyncCoordinator;
  private readonly editorController: TextAreaController = new TextAreaController();
  private editorSelectionStart: number = 0;
  private editorSelectionEnd: number = 0;
  private autosaveTimer: number = -1;
  private isDirty: boolean = false;
  private readonly searchIndex: NoteSearchIndex = new NoteSearchIndex();
  private readonly oauthListener: OAuthCallbackListener = (uri: string) => {
    this.handleOAuthCallback(uri);
  };

  aboutToAppear(): void {
    const context = getContext(this) as common.UIAbilityContext;
    this.repository = new NotebookRepository(context);
    this.attachmentService = new AttachmentService(
      context,
      this.repository.attachmentsDir,
      this.repository.tempDir,
      this.repository.notesDir
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
    this.flushAutosave();
    OAuthCallbackInbox.shared().unsubscribe(this.oauthListener);
  }

  private refreshNotes(): void {
    if (this.repository) {
      this.notes = this.repository.list();
      this.searchIndex.rebuild(this.notes);
      this.updateVisibleNotes();
    }
  }

  private updateVisibleNotes(): void {
    this.visibleNotes = this.searchIndex.search(this.searchQuery);
  }

  private openNote(note: Note): void {
    this.cancelAutosave();
    this.selectedNote = note;
    this.title = note.title;
    this.content = note.content;
    this.editorSelectionStart = note.content.length;
    this.editorSelectionEnd = note.content.length;
    this.isDirty = false;
    this.saveStatus = '已保存';
    this.lastPhotoUri = '';
    this.mode = ScreenMode.EDITOR;
  }

  private createNote(): void {
    if (this.repository) {
      this.openNote(this.repository.create());
    }
  }

  private saveNote(showConfirmation: boolean = true): void {
    if (!this.repository || this.selectedNote.id.length === 0) {
      return;
    }
    this.cancelAutosave();
    const draft = new Note(
      this.selectedNote.id,
      this.title,
      this.content,
      this.selectedNote.modifiedAt
    );
    this.selectedNote = this.repository.save(draft);
    this.title = this.selectedNote.title;
    this.content = this.selectedNote.content;
    this.isDirty = false;
    this.saveStatus = '已保存';
    this.refreshNotes();
    if (showConfirmation) {
      promptAction.showToast({ message: '已保存到本地' });
    }
  }

  private saveNoteSafely(showConfirmation: boolean = true): boolean {
    try {
      this.saveNote(showConfirmation);
      return true;
    } catch (error) {
      this.saveStatus = '保存失败';
      promptAction.showToast({ message: '保存失败，请检查存储空间' });
      return false;
    }
  }

  private scheduleAutosave(): void {
    this.isDirty = true;
    this.saveStatus = '未保存';
    this.cancelAutosave();
    this.autosaveTimer = setTimeout(() => {
      this.autosaveTimer = -1;
      this.flushAutosave();
    }, 600);
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

  private async capturePhoto(): Promise<void> {
    if (!this.attachmentService || this.isCapturing) {
      return;
    }
    const attachmentService = this.attachmentService;
    this.isCapturing = true;
    let attachment: AttachmentResult | undefined = undefined;
    const previousContent = this.content;
    const previousPhotoUri = this.lastPhotoUri;
    const insertionStart = this.editorSelectionStart;
    const insertionEnd = this.editorSelectionEnd;
    let didSave: boolean = false;
    try {
      const outcome = await attachmentService.capturePhoto();
      if (outcome.status === AttachmentCaptureStatus.CANCELLED) {
        return;
      }
      attachment = outcome.attachment;
      if (attachment) {
        const insertion = MarkdownDocument.insertBlock(
          this.content,
          attachment.markdown,
          insertionStart,
          insertionEnd
        );
        this.content = insertion.content;
        this.editorSelectionStart = insertion.caretOffset;
        this.editorSelectionEnd = insertion.caretOffset;
        this.editorController.caretPosition(insertion.caretOffset);
        this.lastPhotoUri = attachment.uri;
        this.saveNote(false);
        didSave = true;
        attachmentService.confirm(attachment);
      }
    } catch (error) {
      if (attachment && !didSave) {
        attachmentService.rollback(attachment);
      }
      if (!didSave) {
        this.content = previousContent;
        this.lastPhotoUri = previousPhotoUri;
      }
      promptAction.showToast({ message: '拍照未完成，请重试' });
    } finally {
      this.isCapturing = false;
    }
    if (didSave) {
      promptAction.showToast({ message: '照片已插入并保存' });
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
      promptAction.showToast({
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
    this.syncDetail = '';
    try {
      const result = await this.oneDrive.sync((progress: SyncProgress) => {
        this.updateSyncProgress(progress);
      });
      this.syncStatus =
        `同步完成：上传 ${result.uploaded}，下载 ${result.downloaded}，冲突 ${result.conflicts}`;
      this.syncDetail = '';
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

  private async connectNas(): Promise<void> {
    if (!this.repository || this.isSyncBusy) {
      return;
    }
    this.isSyncBusy = true;
    this.syncStatus = '正在连接 NAS';
    try {
      const configuration = new S3Configuration(
        this.nasEndpoint,
        this.nasRegion,
        this.nasBucket,
        this.nasPrefix
      );
      const credentials = new S3Credentials(
        this.nasAccessKey,
        this.nasSecretKey
      );
      const coordinator = new S3SyncCoordinator(
        this.repository,
        configuration,
        credentials
      );
      this.nas = coordinator;
      await coordinator.connect();
      this.isNasConnected = true;
      this.syncStatus = 'NAS 已连接';
    } catch (error) {
      this.nas = undefined;
      this.isNasConnected = false;
      this.syncStatus = 'NAS 连接失败，请检查 HTTPS 地址和凭据';
    } finally {
      this.isSyncBusy = false;
    }
  }

  private async syncNasNow(): Promise<void> {
    if (!this.nas || this.isSyncBusy || !this.isNasConnected) {
      return;
    }
    this.isSyncBusy = true;
    this.isSyncOperationActive = true;
    this.syncStatus = '正在同步';
    this.syncDetail = '';
    try {
      const result = await this.nas.sync((progress: SyncProgress) => {
        this.updateSyncProgress(progress);
      });
      this.syncStatus =
        `同步完成：上传 ${result.uploaded}，下载 ${result.downloaded}，冲突 ${result.conflicts}`;
      this.syncDetail = '';
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

  private updateSyncProgress(progress: SyncProgress): void {
    if (progress.phase === SyncPhase.SCANNING_LOCAL) {
      this.syncStatus = '正在扫描本地文件';
    } else if (progress.phase === SyncPhase.READING_REMOTE) {
      this.syncStatus = this.syncTarget === SyncTarget.ONEDRIVE ?
        '正在读取 OneDrive 变更' :
        '正在读取 NAS 对象';
    } else if (progress.phase === SyncPhase.APPLYING) {
      this.syncStatus = progress.totalOperations === 0 ?
        '文件已一致' :
        `正在同步 ${progress.completedOperations}/${progress.totalOperations}`;
    } else if (progress.phase === SyncPhase.VERIFYING) {
      this.syncStatus = '正在验证同步结果';
    }
    this.syncDetail = progress.currentPath ?? '';
  }

  private cancelSync(): void {
    if (this.isSyncBusy) {
      this.isCancellingSync = true;
      this.syncStatus = '正在取消';
      if (this.syncTarget === SyncTarget.ONEDRIVE && this.oneDrive) {
        this.oneDrive.cancel();
      } else if (this.syncTarget === SyncTarget.NAS && this.nas) {
        this.nas.cancel();
      }
      if (!this.isSyncOperationActive) {
        this.isSyncBusy = false;
        this.isCancellingSync = false;
        this.syncStatus = this.syncTarget === SyncTarget.ONEDRIVE ?
          (this.isOneDriveConnected ? 'OneDrive 已连接' : 'OneDrive 未连接') :
          (this.isNasConnected ? 'NAS 已连接' : 'NAS 未连接');
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
      this.syncDetail = '';
    } finally {
      this.isSyncBusy = false;
    }
  }

  private disconnectNas(): void {
    if (this.isSyncBusy) {
      return;
    }
    if (this.nas) {
      this.nas.cancel();
    }
    this.nas = undefined;
    this.isNasConnected = false;
    this.hasConfirmedNasSync = false;
    this.nasSecretKey = '';
    this.syncStatus = 'NAS 未连接';
    this.syncDetail = '';
  }

  private selectSyncTarget(target: SyncTarget): void {
    if (this.isSyncBusy) {
      return;
    }
    this.syncTarget = target;
    this.syncDetail = '';
    this.syncStatus = target === SyncTarget.ONEDRIVE ?
      (this.isOneDriveConnected ? 'OneDrive 已连接' : 'OneDrive 未连接') :
      (this.isNasConnected ? 'NAS 已连接' : 'NAS 未连接');
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

      Search({
        value: this.searchQuery,
        placeholder: '搜索标题或正文'
      })
        .height(42)
        .margin({ left: 16, right: 16, bottom: 12 })
        .backgroundColor($r('app.color.surface'))
        .onChange((value: string) => {
          this.searchQuery = value;
          this.updateVisibleNotes();
        })

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
            if (this.saveNoteSafely(false)) {
              this.mode = ScreenMode.LIST;
            }
          })
        TextInput({ text: this.title, placeholder: '笔记标题' })
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

      TextArea({
        text: this.content,
        placeholder: '用 Markdown 开始记录...\n\n# 标题\n- 清单\n**重点**',
        controller: this.editorController
      })
        .fontSize(16)
        .fontColor($r('app.color.ink'))
        .placeholderColor('#9AA39E')
        .backgroundColor(Color.Transparent)
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 18, right: 18, top: 16, bottom: 16 })
        .onChange((value: string) => {
          this.content = value;
          this.scheduleAutosave();
        })
        .onTextSelectionChange((selectionStart: number, selectionEnd: number) => {
          this.editorSelectionStart = selectionStart;
          this.editorSelectionEnd = selectionEnd;
        })

      if (this.lastPhotoUri.length > 0) {
        Image(this.lastPhotoUri)
          .width('100%')
          .height(112)
          .objectFit(ImageFit.Cover)
          .padding({ left: 18, right: 18, bottom: 10 })
      }

      Row() {
        Text(`Markdown · ${this.saveStatus}`)
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

      Scroll() {
        Column({ space: 12 }) {
          Row() {
            Button('OneDrive')
              .layoutWeight(1)
              .height(40)
              .fontColor(this.syncTarget === SyncTarget.ONEDRIVE ?
                Color.White : $r('app.color.ink'))
              .backgroundColor(this.syncTarget === SyncTarget.ONEDRIVE ?
                $r('app.color.brand') : $r('app.color.surface'))
              .borderRadius(6)
              .onClick(() => this.selectSyncTarget(SyncTarget.ONEDRIVE))
            Button('NAS / S3')
              .layoutWeight(1)
              .height(40)
              .fontColor(this.syncTarget === SyncTarget.NAS ?
                Color.White : $r('app.color.ink'))
              .backgroundColor(this.syncTarget === SyncTarget.NAS ?
                $r('app.color.brand') : $r('app.color.surface'))
              .borderRadius(6)
              .onClick(() => this.selectSyncTarget(SyncTarget.NAS))
          }
          .width('100%')

          Text(this.syncTarget === SyncTarget.ONEDRIVE ? 'OneDrive' : 'S3 兼容 NAS')
            .fontSize(19)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.ink'))
            .width('100%')
          Text(this.syncStatus)
            .fontSize(14)
            .fontColor($r('app.color.muted'))
            .width('100%')
          if (this.syncDetail.length > 0) {
            Text(this.syncDetail)
              .fontSize(12)
              .fontColor($r('app.color.muted'))
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .width('100%')
          }

          if (this.syncTarget === SyncTarget.ONEDRIVE) {
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
          } else {
            if (!this.isNasConnected) {
              TextInput({ text: this.nasEndpoint, placeholder: 'https://nas.example.com:9000' })
                .width('100%')
                .height(44)
                .fontSize(14)
                .backgroundColor($r('app.color.surface'))
                .onChange((value: string) => this.nasEndpoint = value)
              Row({ space: 8 }) {
                TextInput({ text: this.nasBucket, placeholder: 'Bucket' })
                  .layoutWeight(1)
                  .height(44)
                  .fontSize(14)
                  .backgroundColor($r('app.color.surface'))
                  .onChange((value: string) => this.nasBucket = value)
                TextInput({ text: this.nasRegion, placeholder: 'Region' })
                  .layoutWeight(1)
                  .height(44)
                  .fontSize(14)
                  .backgroundColor($r('app.color.surface'))
                  .onChange((value: string) => this.nasRegion = value)
              }
              .width('100%')
              TextInput({ text: this.nasPrefix, placeholder: '远端目录前缀' })
                .width('100%')
                .height(44)
                .fontSize(14)
                .backgroundColor($r('app.color.surface'))
                .onChange((value: string) => this.nasPrefix = value)
              TextInput({ text: this.nasAccessKey, placeholder: 'Access Key' })
                .width('100%')
                .height(44)
                .fontSize(14)
                .backgroundColor($r('app.color.surface'))
                .onChange((value: string) => this.nasAccessKey = value)
              TextInput({ text: this.nasSecretKey, placeholder: 'Secret Key' })
                .type(InputType.Password)
                .width('100%')
                .height(44)
                .fontSize(14)
                .backgroundColor($r('app.color.surface'))
                .onChange((value: string) => this.nasSecretKey = value)
              Button(this.isSyncBusy ? '连接中' : '连接 NAS')
                .width('100%')
                .height(44)
                .fontColor(Color.White)
                .backgroundColor($r('app.color.brand'))
                .borderRadius(6)
                .enabled(!this.isSyncBusy)
                .onClick(() => this.connectNas())
            } else {
              Row({ space: 10 }) {
                Checkbox({ name: 'confirm-nas-scope' })
                  .select(this.hasConfirmedNasSync)
                  .onChange((selected: boolean) => {
                    this.hasConfirmedNasSync = selected;
                  })
                Text(`允许同步到 ${this.nasBucket}/${this.nasPrefix}`)
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
                .enabled(!this.isSyncBusy && this.hasConfirmedNasSync)
                .onClick(() => this.syncNasNow())
              Button('断开连接')
                .width('100%')
                .height(42)
                .fontColor($r('app.color.ink'))
                .backgroundColor($r('app.color.surface'))
                .borderRadius(6)
                .enabled(!this.isSyncBusy)
                .onClick(() => this.disconnectNas())
            }
          }

          if (this.isSyncBusy) {
            Button('取消')
              .width('100%')
              .height(40)
              .fontColor($r('app.color.ink'))
              .backgroundColor(Color.Transparent)
              .onClick(() => this.cancelSync())
          }

          Text('同步范围：笔记和附件；兼容鸿蒙 4，凭据仅在本次运行保留且不会上传')
            .fontSize(12)
            .fontColor($r('app.color.muted'))
            .width('100%')
            .margin({ top: 14, bottom: 18 })
        }
        .alignItems(HorizontalAlign.Start)
        .width('100%')
        .padding({ left: 20, right: 20, top: 20 })
      }
      .layoutWeight(1)
      .width('100%')
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
