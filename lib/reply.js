/** Return one from a handler to choose the status code; a plain value is sent as 200, `undefined` as 204. */
export class Reply {
  constructor(status, body) {
    this.status = status;
    this.body = body;
  }
}
