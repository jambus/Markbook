export enum HttpMethod {
  GET = 'GET',
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

  constructor(status: number, text?: string, body?: Uint8Array) {
    this.status = status;
    this.text = text;
    this.body = body;
  }
}

export interface HttpTransport {
  request(request: HttpRequestData): Promise<HttpResponseData>;
  cancel(): void;
}
