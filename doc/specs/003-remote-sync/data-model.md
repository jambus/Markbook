# Data Model: 同步

## FileSnapshot

- `relativePath`
- `size`
- `contentHash`
- `modifiedAt`
- `providerRevision`：OneDrive eTag/cTag 或 WebDAV ETag。
- `deleted`

## SyncBaseline

- `providerId`
- `notebookId`
- `files`: 上次完整成功时的文件快照。
- `cursor`: Provider opaque 增量游标；客户端不得解析或修改。
- `completedAt`

## SyncOperation

- `type`: upload、download、delete-local、delete-remote、keep-both。
- `relativePath`
- `expectedRevision`: 条件操作所需的远端版本。
- `state`: pending、running、completed、failed、cancelled。

## ProviderCredential

仅保存安全存储中的引用 ID。访问令牌、刷新令牌和密码不得进入普通配置文件。

