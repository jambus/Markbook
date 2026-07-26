import { describe, expect, it } from '@ohos/hypium';
import type { SyncContentSource } from '../../../main/ets/services/sync/SyncContent';
import { FileSnapshot } from '../../../main/ets/services/sync/SyncModels';
import {
  HttpMethod,
  HttpRequestData,
  HttpResponseData
} from '../../../main/ets/services/sync/http/HttpTransport';
import type { HttpTransport } from '../../../main/ets/services/sync/http/HttpTransport';
import { HarmonyS3Crypto } from '../../../main/ets/services/sync/s3/HarmonyS3Crypto';
import {
  S3Configuration,
  S3Provider
} from '../../../main/ets/services/sync/s3/S3Provider';
import type { S3Clock } from '../../../main/ets/services/sync/s3/S3Provider';
import {
  S3Credentials,
  S3Signer
} from '../../../main/ets/services/sync/s3/S3Signer';

class FixedClock implements S3Clock {
  now(): Date {
    return new Date('2013-05-24T00:00:00.000Z');
  }
}

class TestContentSource implements SyncContentSource {
  readonly size: number;
  private readonly value: Uint8Array;

  constructor(value: Uint8Array) {
    this.value = value;
    this.size = value.length;
  }

  async read(offset: number, maxBytes: number): Promise<Uint8Array> {
    return this.value.slice(offset, offset + maxBytes);
  }

  close(): void {
  }
}

class MockHttpTransport implements HttpTransport {
  requests: Array<HttpRequestData> = [];
  responses: Array<HttpResponseData> = [];
  cancelled: boolean = false;

  async request(request: HttpRequestData): Promise<HttpResponseData> {
    this.requests.push(request);
    if (this.responses.length === 0) {
      throw new Error('No mock S3 response configured');
    }
    return this.responses.shift() as HttpResponseData;
  }

  cancel(): void {
    this.cancelled = true;
  }
}

function configuration(): S3Configuration {
  return new S3Configuration(
    'https://nas.example.test:9000',
    'us-east-1',
    'notes-bucket'
  );
}

function credentials(): S3Credentials {
  return new S3Credentials(
    'AKIAIOSFODNN7EXAMPLE',
    'wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY'
  );
}

function listXml(
  contents: string,
  truncated: boolean,
  continuationToken?: string
): string {
  return '<?xml version="1.0" encoding="UTF-8"?>' +
    '<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">' +
    contents +
    `<IsTruncated>${truncated}</IsTruncated>` +
    (continuationToken ?
      `<NextContinuationToken>${continuationToken}</NextContinuationToken>` :
      '') +
    '</ListBucketResult>';
}

function objectXml(key: string, etag: string, size: number): string {
  return '<Contents>' +
    `<Key>${key}</Key>` +
    '<LastModified>2026-07-25T01:02:03.000Z</LastModified>' +
    `<ETag>${etag}</ETag>` +
    `<Size>${size}</Size>` +
    '</Contents>';
}

export default function s3ProviderTest(): void {
  describe('S3-compatible provider', () => {
    it('isolates baselines for different remote targets without exposing credentials', 0, () => {
      const first = configuration();
      const second = new S3Configuration(
        'https://nas.example.test:9000',
        'us-east-1',
        'notes-bucket',
        'AnotherNotebook'
      );

      expect(first.providerId().startsWith('s3-')).assertTrue();
      expect(first.providerId() === second.providerId()).assertFalse();
      expect(first.providerId().includes('notes-bucket')).assertFalse();
    });

    it('matches a fixed SigV4 authorization calculation', 0, async () => {
      const signer = new S3Signer(
        'us-east-1',
        's3.amazonaws.com',
        credentials(),
        new HarmonyS3Crypto()
      );

      const signed = await signer.sign(
        HttpMethod.GET,
        '/examplebucket/test.txt',
        [],
        new Uint8Array(0),
        new FixedClock().now()
      );

      expect(signed.authorization.endsWith(
        'Signature=8cd1f6f0adfa1de783bbe2acae664ae1f7e1075e3ac7cdaecd341ddf680f2cda'
      )).assertTrue();
      expect(S3Signer.encodePath('/bucket/照片 1.jpg'))
        .assertEqual('/bucket/%E7%85%A7%E7%89%87%201.jpg');
    });

    it('paginates full listings and emits tombstones for missing objects', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(new HttpResponseData(200, ''));
      transport.responses.push(new HttpResponseData(200, listXml(
        objectXml('Markbook/notes/a.md', '&quot;r1&quot;', 12),
        true,
        'next+/='
      )));
      transport.responses.push(new HttpResponseData(200, listXml(
        objectXml('Markbook/attachments/photo.jpg', '&quot;r2&quot;', 24),
        false
      )));
      const provider = new S3Provider(
        transport,
        configuration(),
        credentials(),
        new HarmonyS3Crypto(),
        new FixedClock()
      );

      await provider.connect();
      const first = await provider.listChanges(
        's3-full-scan-v1',
        [new FileSnapshot('notes/deleted.md', 4, undefined, 0, '"old"')]
      );
      const second = await provider.listChanges(
        first.cursor,
        [new FileSnapshot('notes/deleted.md', 4, undefined, 0, '"old"')]
      );

      expect(first.hasMore).assertTrue();
      expect(first.changes[0].relativePath).assertEqual('notes/a.md');
      expect(second.hasMore).assertFalse();
      expect(second.changes[0].relativePath).assertEqual('attachments/photo.jpg');
      expect(second.changes[1].relativePath).assertEqual('notes/deleted.md');
      expect(second.changes[1].deleted).assertTrue();
      expect(transport.requests[2].url.includes('continuation-token=next%2B%2F%3D'))
        .assertTrue();
    });

    it('uses conditional writes and returns the object ETag', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(new HttpResponseData(
        200,
        '',
        undefined,
        '"new-revision"'
      ));
      const provider = new S3Provider(
        transport,
        configuration(),
        credentials(),
        new HarmonyS3Crypto(),
        new FixedClock()
      );

      const revision = await provider.upload(
        'notes/new.md',
        new TestContentSource(new Uint8Array([1, 2, 3]))
      );

      expect(revision).assertEqual('"new-revision"');
      expect(transport.requests[0].method).assertEqual(HttpMethod.PUT);
      expect(transport.requests[0].headers.ifNoneMatch).assertEqual('*');
      expect(transport.requests[0].headers.authorization?.includes(
        'AWS4-HMAC-SHA256'
      )).assertTrue();
    });
  });
}
