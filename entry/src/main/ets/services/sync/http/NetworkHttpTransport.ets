import { http } from '@kit.NetworkKit';
import {
  HttpHeaders,
  HttpMethod,
  HttpRequestData,
  HttpResponseData,
  HttpResponseType
} from './HttpTransport';
import type { HttpTransport } from './HttpTransport';

class NetworkHeaders {
  Authorization?: string;
  Accept?: string;
  'Content-Type'?: string;
  'Content-Length'?: string;
  'Content-Range'?: string;
  'If-Match'?: string;
  'If-None-Match'?: string;
  Range?: string;
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
      if (request.responseType === HttpResponseType.BINARY) {
        const buffer = response.result as ArrayBuffer;
        return new HttpResponseData(
          response.responseCode,
          undefined,
          new Uint8Array(buffer)
        );
      }
      return new HttpResponseData(response.responseCode, response.result as string);
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
    if (method === HttpMethod.PUT) {
      return http.RequestMethod.PUT;
    }
    return http.RequestMethod.DELETE;
  }

  private headers(headers: HttpHeaders): NetworkHeaders {
    const result = new NetworkHeaders();
    if (headers.authorization !== undefined) {
      result.Authorization = headers.authorization;
    }
    if (headers.accept !== undefined) {
      result.Accept = headers.accept;
    }
    if (headers.contentType !== undefined) {
      result['Content-Type'] = headers.contentType;
    }
    if (headers.contentLength !== undefined) {
      result['Content-Length'] = headers.contentLength;
    }
    if (headers.contentRange !== undefined) {
      result['Content-Range'] = headers.contentRange;
    }
    if (headers.ifMatch !== undefined) {
      result['If-Match'] = headers.ifMatch;
    }
    if (headers.ifNoneMatch !== undefined) {
      result['If-None-Match'] = headers.ifNoneMatch;
    }
    if (headers.range !== undefined) {
      result.Range = headers.range;
    }
    return result;
  }
}
