import { common, Want } from '@kit.AbilityKit';

export class SystemBrowser {
  private readonly context: common.UIAbilityContext;

  constructor(context: common.UIAbilityContext) {
    this.context = context;
  }

  async open(url: string): Promise<void> {
    if (!url.startsWith('https://login.microsoftonline.com/')) {
      throw new Error('Refusing to open an unexpected OAuth origin');
    }
    const want: Want = {
      action: 'ohos.want.action.viewData',
      entities: ['entity.system.browsable'],
      uri: url
    };
    await this.context.startAbility(want);
  }
}
