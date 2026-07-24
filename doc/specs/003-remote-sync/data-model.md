# Data Model: 同步

## FileSnapshot

- `relativePath`
- `size`
- `contentHash`：本地和基线必须是 SHA-256；远端 API 不提供内容摘要时可为空，
  以 `providerRevision` 判断远端变化。
- `modifiedAt`
- `providerRevision`：OneDrive eTag/cTag 或 WebDAV ETag。
- `providerItemId`：OneDrive 等服务的稳定远端 ID，用于将仅含 ID 的删除事件
  映射回上次成功路径。
- `providerParentId`：远端父目录 ID；OneDrive delta 不保证返回父路径，目录删除
  时用它展开该目录下已知文件的删除标记。
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

## SyncContentSource

- `size`：内容总字节数。
- `read(offset, maxBytes)`：按偏移读取有限大小的数据块。
- `close()`：释放文件或网络资源；成功与失败路径都必须调用。

## ProviderCredential

仅保存安全存储中的引用 ID。访问令牌、刷新令牌和密码不得进入普通配置文件。
