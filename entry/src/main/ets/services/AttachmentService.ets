import { common } from '@kit.AbilityKit';
import { camera, cameraPicker } from '@kit.CameraKit';
import { fileIo as fs } from '@kit.CoreFileKit';

export class AttachmentResult {
  uri: string;
  markdown: string;

  constructor(uri: string, markdown: string) {
    this.uri = uri;
    this.markdown = markdown;
  }
}

export class AttachmentService {
  private readonly context: common.UIAbilityContext;
  private readonly attachmentsDir: string;

  constructor(context: common.UIAbilityContext, attachmentsDir: string) {
    this.context = context;
    this.attachmentsDir = attachmentsDir;
  }

  async capturePhoto(): Promise<AttachmentResult | undefined> {
    const profile: cameraPicker.PickerProfile = {
      cameraPosition: camera.CameraPosition.CAMERA_POSITION_BACK
    };
    const result: cameraPicker.PickerResult = await cameraPicker.pick(
      this.context,
      [cameraPicker.PickerMediaType.PHOTO],
      profile
    );
    if (!result.resultUri) {
      return undefined;
    }

    const fileName = `photo-${Date.now()}.jpg`;
    const targetPath = `${this.attachmentsDir}/${fileName}`;
    fs.copyFileSync(result.resultUri, targetPath);
    return new AttachmentResult(
      `file://${targetPath}`,
      `![照片](../attachments/${fileName})`
    );
  }
}
