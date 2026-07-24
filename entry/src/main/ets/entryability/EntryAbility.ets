import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { OAuthCallbackInbox } from '../services/sync/onedrive/OAuthCallbackInbox';

const DOMAIN = 0x0000;

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, 'Markbook', 'EntryAbility created');
    this.captureOAuthCallback(want);
  }

  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.captureOAuthCallback(want);
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index', (error) => {
      if (error.code) {
        hilog.error(DOMAIN, 'Markbook', 'Failed to load page: %{public}s', JSON.stringify(error));
      }
    });
  }

  private captureOAuthCallback(want: Want): void {
    if (want.uri) {
      OAuthCallbackInbox.shared().push(want.uri);
    }
  }
}
