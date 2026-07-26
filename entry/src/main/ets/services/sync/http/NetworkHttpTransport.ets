import { http } from '@kit.NetworkKit';
import {
  HttpHeaders,
  HttpMethod,
  HttpRequestData,
  HttpResponseData,
  HttpResponseType
} from './HttpTransport';
import type { HttpTransport } from './HttpTransport';

interface NetworkHeaders {
  Authorization?: string;
  Accept?: string;
  'Content-Type'?: string;
  'Content-Length'?: string;
  'Content-Range'?: string;
  'If-Match'?: string;
  'If-None-Match'?: string;
  Range?: string;
  'x-amz-content-sha256'?: string;
  'x-amz-date'?: string;
  'x-amz-security-token'?: string;
}

class NetworkResponseHeaders {
  ETag?: string;
}

export class NetworkHttpTransport implements HttpTransport {
  private readonly active: Array<http.HttpRequest> = [];

  async request(request: HttpRequestData): Promise<HttpResponseData> {
    const task = http.createHttp();
    this.active.push(task);
    try {
      const options: http.HttpRequestOptions = {
        method: this.method(request.method),
        header: this.headers(request.headers),
        expectDataType: request.responseType === HttpResponseType.BINARY ?
          http.HttpDataType.ARRAY_BUFFER :
          http.HttpDataType.STRING,
        usingCache: false,
        connectTimeout: 30000,
        readTimeout: 120000,
        maxLimit: 64 * 1024 * 1024
      };
      if (request.body) {
        options.extraData = request.body.slice().buffer;
      }

      const response = await task.request(request.url, options);
      const responseHeaders = this.responseHeaders(response.header);
      if (request.responseType === HttpResponseType.BINARY) {
        const buffer = response.result as ArrayBuffer;
        return new HttpResponseData(
          response.responseCode,
          undefined,
          new Uint8Array(buffer),
          responseHeaders.ETag
        );
      }
      return new HttpResponseData(
        response.responseCode,
        response.result as string,
        undefined,
        responseHeaders.ETag
      );
    } finally {
      const index = this.active.indexOf(task);
      if (index >= 0) {
        this.active.splice(index, 1);
      }
      task.destroy();
    }
  }

  cancel(): void {
    this.active.forEach((task: http.HttpRequest) => task.destroy());
    this.active.length = 0;
  }

  private method(method: HttpMethod): http.RequestMethod {
    if (method === HttpMethod.GET) {
      return http.RequestMethod.GET;
    }
    if (method === HttpMethod.POST) {
      return http.RequestMethod.POST;
    }
    if (method === HttpMethod.HEAD) {
      return http.RequestMethod.HEAD;
    }
    if (method === HttpMethod.PUT) {
      return http.RequestMethod.PUT;
    }
    return http.RequestMethod.DELETE;
  }

  private headers(headers: HttpHeaders): NetworkHeaders {
    return {
      Authorization: headers.authorization,
      Accept: headers.accept,
      'Content-Type': headers.contentType,
      'Content-Length': headers.contentLength,
      'Content-Range': headers.contentRange,
      'If-Match': headers.ifMatch,
      'If-None-Match': headers.ifNoneMatch,
      Range: headers.range,
      'x-amz-content-sha256': headers.xAmzContentSha256,
      'x-amz-date': headers.xAmzDate,
      'x-amz-security-token': headers.xAmzSecurityToken
    };
  }

  private responseHeaders(headers: Object): NetworkResponseHeaders {
    const normalized = JSON.stringify(headers)
      .replace(/"etag"\s*:/gi, '"ETag":');
    return JSON.parse(normalized) as NetworkResponseHeaders;
  }
}
