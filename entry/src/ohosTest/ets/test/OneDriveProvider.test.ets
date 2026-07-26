import { describe, expect, it } from '@ohos/hypium';
import type { SyncContentSource } from '../../../main/ets/services/sync/SyncContent';
import {
  HttpMethod,
  HttpRequestData,
  HttpResponseData
} from '../../../main/ets/services/sync/http/HttpTransport';
import type { HttpTransport } from '../../../main/ets/services/sync/http/HttpTransport';
import type { TextCodec } from '../../../main/ets/services/sync/http/TextCodec';
import { OneDriveProvider } from '../../../main/ets/services/sync/onedrive/OneDriveProvider';

class TestTextCodec implements TextCodec {
  encode(value: string): Uint8Array {
    const result = new Uint8Array(value.length);
    for (let index = 0; index < value.length; index += 1) {
      result[index] = value.charCodeAt(index);
    }
    return result;
  }
}

class TestContentSource implements SyncContentSource {
  readonly size: number;
  private readonly content: Uint8Array;

  constructor(content: Uint8Array) {
    this.content = content;
    this.size = content.length;
  }

  async read(offset: number, maxBytes: number): Promise<Uint8Array> {
    return this.content.slice(offset, offset + maxBytes);
  }

  close(): void {
  }
}

class MockHttpTransport implements HttpTransport {
  requests: Array<HttpRequestData> = [];
  responses: Array<HttpResponseData> = [];
  cancelled: boolean = false;
  failAtRequest: number = -1;

  async request(request: HttpRequestData): Promise<HttpResponseData> {
    this.requests.push(request);
    if (this.requests.length - 1 === this.failAtRequest) {
      this.failAtRequest = -1;
      throw new Error('Injected network failure');
    }
    if (this.responses.length === 0) {
      throw new Error('No mock response configured');
    }
    return this.responses.shift() as HttpResponseData;
  }

  cancel(): void {
    this.cancelled = true;
  }
}

function rootResponse(): HttpResponseData {
  return new HttpResponseData(
    200,
    '{"id":"root-id","name":"Markbook",' +
      '"parentReference":{"path":"/drive/root:/Apps"},"folder":{}}'
  );
}

export default function oneDriveProviderTest(): void {
  describe('OneDriveProvider', () => {
    it('lists delta changes with bearer authentication', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(rootResponse());
      transport.responses.push(new HttpResponseData(
        200,
        '{"value":[{"id":"item-1","name":"a.md","size":10,"eTag":"r1",' +
          '"parentReference":{"path":"/drive/root:/Apps/Markbook/notes"},"file":{}}],' +
          '"@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?token=opaque"}'
      ));
      const provider = new OneDriveProvider(
        transport,
        'secret-token',
        new TestTextCodec()
      );

      await provider.connect();
      const page = await provider.listChanges();

      expect(page.changes[0].relativePath).assertEqual('notes/a.md');
      expect(transport.requests[1].headers.authorization)
        .assertEqual('Bearer secret-token');
    });

    it('creates a missing content folder and conditionally uploads a small file', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(rootResponse());
      transport.responses.push(new HttpResponseData(404, ''));
      transport.responses.push(new HttpResponseData(
        201,
        '{"id":"notes-id","name":"notes","folder":{}}'
      ));
      transport.responses.push(new HttpResponseData(
        201,
        '{"id":"item-1","name":"a.md","size":3,"eTag":"r1","file":{}}'
      ));
      const provider = new OneDriveProvider(
        transport,
        'secret-token',
        new TestTextCodec()
      );

      await provider.connect();
      const revision = await provider.upload(
        'notes/a.md',
        new TestContentSource(new Uint8Array([1, 2, 3]))
      );

      expect(revision).assertEqual('r1');
      expect(transport.requests[2].method).assertEqual(HttpMethod.POST);
      expect(transport.requests[3].method).assertEqual(HttpMethod.PUT);
      expect(transport.requests[3].headers.ifNoneMatch).assertEqual('*');
      expect(transport.requests[3].body?.length).assertEqual(3);
    });

    it('downloads only the requested byte range at the expected revision', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(rootResponse());
      transport.responses.push(new HttpResponseData(
        200,
        '{"id":"item-1","name":"a.md","size":4,"eTag":"r2","file":{}}'
      ));
      transport.responses.push(new HttpResponseData(
        206,
        undefined,
        new Uint8Array([8, 9])
      ));
      const provider = new OneDriveProvider(
        transport,
        'secret-token',
        new TestTextCodec()
      );

      await provider.connect();
      const source = await provider.download('notes/a.md', 'r2');
      const chunk = await source.read(1, 2);

      expect(chunk[0]).assertEqual(8);
      expect(transport.requests[2].headers.range).assertEqual('bytes=1-2');
      expect(transport.requests[2].headers.ifMatch).assertEqual('r2');
    });

    it('uploads a large photo in resumable 320 KiB-aligned fragments', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(rootResponse());
      transport.responses.push(new HttpResponseData(404, ''));
      transport.responses.push(new HttpResponseData(
        201,
        '{"id":"attachments-id","folder":{}}'
      ));
      transport.responses.push(new HttpResponseData(
        200,
        '{"uploadUrl":"https://upload.example.test/session"}'
      ));
      transport.responses.push(new HttpResponseData(202, '{}'));
      transport.responses.push(new HttpResponseData(202, '{}'));
      transport.responses.push(new HttpResponseData(
        201,
        '{"id":"photo-id","size":4194305,"eTag":"photo-r1","file":{}}'
      ));
      const provider = new OneDriveProvider(
        transport,
        'secret-token',
        new TestTextCodec()
      );

      await provider.connect();
      const revision = await provider.upload(
        'attachments/photo.jpg',
        new TestContentSource(new Uint8Array(4 * 1024 * 1024 + 1))
      );

      expect(revision).assertEqual('photo-r1');
      expect(transport.requests[4].headers.contentRange)
        .assertEqual('bytes 0-1638399/4194305');
      expect(transport.requests[4].headers.authorization).assertUndefined();
      expect(transport.requests[6].headers.contentRange)
        .assertEqual('bytes 3276800-4194304/4194305');
    });

    it('recovers when a completed upload response is lost', 0, async () => {
      const transport = new MockHttpTransport();
      transport.responses.push(rootResponse());
      transport.responses.push(new HttpResponseData(404, ''));
      transport.responses.push(new HttpResponseData(
        201,
        '{"id":"notes-id","folder":{}}'
      ));
      transport.responses.push(new HttpResponseData(
        200,
        '{"id":"item-1","size":3,"eTag":"recovered-r1","file":{}}'
      ));
      transport.responses.push(new HttpResponseData(
        206,
        undefined,
        new Uint8Array([1, 2, 3])
      ));
      transport.failAtRequest = 3;
      const provider = new OneDriveProvider(
        transport,
        'secret-token',
        new TestTextCodec()
      );

      await provider.connect();
      const revision = await provider.upload(
        'notes/a.md',
        new TestContentSource(new Uint8Array([1, 2, 3]))
      );

      expect(revision).assertEqual('recovered-r1');
      expect(transport.requests[4].url.includes('notes/a.md')).assertTrue();
      expect(transport.requests[5].headers.range).assertEqual('bytes=0-2');
    });
  });
}
