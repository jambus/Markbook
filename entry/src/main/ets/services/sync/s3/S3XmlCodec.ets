import { buffer, xml } from '@kit.ArkTS';

export class S3ObjectInfo {
  key: string = '';
  etag: string = '';
  size: number = 0;
  modifiedAt: number = 0;
}

export class S3ListResult {
  objects: Array<S3ObjectInfo> = [];
  truncated: boolean = false;
  continuationToken?: string;
}

export class S3XmlCodec {
  static parseList(value: string): S3ListResult {
    const result = new S3ListResult();
    let current: S3ObjectInfo | undefined = undefined;
    let textValue = '';
    const parser = new xml.XmlPullParser(buffer.from(value, 'utf-8').buffer);
    parser.parse({
      supportDoctype: false,
      ignoreNameSpace: true,
      tokenValueCallbackFunction: (
        eventType: xml.EventType,
        info: xml.ParseInfo
      ): boolean => {
        if (eventType === xml.EventType.START_TAG) {
          textValue = '';
          if (info.getName() === 'Contents') {
            current = new S3ObjectInfo();
          }
        } else if (eventType === xml.EventType.TEXT) {
          textValue += info.getText();
        } else if (eventType === xml.EventType.END_TAG) {
          const name = info.getName();
          const trimmed = textValue.trim();
          if (current) {
            if (name === 'Key') {
              current.key = trimmed;
            } else if (name === 'ETag') {
              current.etag = trimmed;
            } else if (name === 'Size') {
              current.size = Number(trimmed);
            } else if (name === 'LastModified') {
              current.modifiedAt = Date.parse(trimmed);
            } else if (name === 'Contents') {
              S3XmlCodec.validateObject(current);
              result.objects.push(current);
              current = undefined;
            }
          } else if (name === 'IsTruncated') {
            result.truncated = trimmed === 'true';
          } else if (name === 'NextContinuationToken' && trimmed.length > 0) {
            result.continuationToken = trimmed;
          }
          textValue = '';
        }
        return true;
      }
    });
    if (result.truncated && !result.continuationToken) {
      throw new Error('S3 list response omitted its continuation token');
    }
    return result;
  }

  private static validateObject(value: S3ObjectInfo): void {
    if (
      value.key.length === 0 ||
      value.etag.length === 0 ||
      !Number.isFinite(value.size) ||
      value.size < 0 ||
      !Number.isFinite(value.modifiedAt)
    ) {
      throw new Error('S3 list response contains invalid object metadata');
    }
  }
}
