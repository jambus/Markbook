# Contract: SyncProvider

Provider 负责协议转换，不决定冲突策略。

## Required Operations

- `connect()`：验证配置和凭据，不修改远端内容。
- `listChanges(cursor?)`：返回分页变化和新的 opaque 游标。
- `download(path, expectedRevision?)`：流式读取指定版本。
- `upload(path, source, expectedRevision?)`：条件写入，版本不符必须报冲突。
- `delete(path, expectedRevision?)`：条件删除。
- `cancel()`：尽快停止未完成网络操作。

## Invariants

- 相对路径必须规范化并禁止 `..` 逃逸笔记本根目录。
- 所有写操作必须幂等或携带可安全重试的操作 ID。
- Provider 不得记录凭据、正文或临时下载 URL。
- 返回的 revision 与 cursor 视为 opaque 字符串。
- 网络失败不得被解释为远端文件不存在。

