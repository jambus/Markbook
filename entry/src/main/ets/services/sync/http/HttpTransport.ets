export enum HttpMethod {
  GET = 'GET',
  HEAD = 'HEAD',
  POST = 'POST',
  PUT = 'PUT',
  DELETE = 'DELETE'
}

export enum HttpResponseType {
  TEXT = 'text',
  BINARY = 'binary'
}

export class HttpHeaders {
  authorization?: string;
  accept?: string;
  contentType?: string;
  contentLength?: string;
  contentRange?: string;
  ifMatch?: string;
  ifNoneMatch?: string;
  range?: string;
  xAmzContentSha256?: string;
  xAmzDate?: string;
  xAmzSecurityToken?: string;
}

export class HttpRequestData {
  method: HttpMethod;
  url: string;
  headers: HttpHeaders;
  responseType: HttpResponseType;
  body?: Uint8Array;

  constructor(
    method: HttpMethod,
    url: string,
    headers: HttpHeaders = new HttpHeaders(),
    responseType: HttpResponseType = HttpResponseType.TEXT,
    body?: Uint8Array
  ) {
    this.method = method;
    this.url = url;
    this.headers = headers;
    this.responseType = responseType;
    this.body = body;
  }
}

export class HttpResponseData {
  status: number;
  text?: string;
  body?: Uint8Array;
  etag?: string;

  constructor(status: number, text?: string, body?: Uint8Array, etag?: string) {
    this.status = status;
    this.text = text;
    this.body = body;
    this.etag = etag;
  }
}

export interface HttpTransport {
  request(request: HttpRequestData): Promise<HttpResponseData>;
  cancel(): void;
}
