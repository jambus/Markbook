export type OAuthCallbackListener = (uri: string) => void;

export class OAuthCallbackInbox {
  private static readonly instance = new OAuthCallbackInbox();
  private pendingUri?: string;
  private readonly listeners: Array<OAuthCallbackListener> = [];

  static shared(): OAuthCallbackInbox {
    return OAuthCallbackInbox.instance;
  }

  push(uri: string): void {
    if (!uri.startsWith('markbook://oauth/onedrive?')) {
      return;
    }
    if (this.listeners.length > 0) {
      this.listeners.forEach((listener: OAuthCallbackListener) => listener(uri));
      return;
    }
    this.pendingUri = uri;
  }

  take(): string | undefined {
    const result = this.pendingUri;
    this.pendingUri = undefined;
    return result;
  }

  subscribe(listener: OAuthCallbackListener): void {
    if (this.listeners.indexOf(listener) < 0) {
      this.listeners.push(listener);
    }
  }

  unsubscribe(listener: OAuthCallbackListener): void {
    const index = this.listeners.indexOf(listener);
    if (index >= 0) {
      this.listeners.splice(index, 1);
    }
  }
}
