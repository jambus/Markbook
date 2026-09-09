# Data Model: 同步

## FileSnapshot

- `relativePath`
- `size`
- `contentHash`：本地和基线必须是 SHA-256；远端 API 不提供内容摘要时可为空，
  以 `providerRevision` 判断远端变化。
- `modifiedAt`
- `providerRevision`：Provider 提供的稳定版本标识，例如 ETag 或变更版本。
- `providerItemId`：远端服务的稳定项目 ID，用于将仅含 ID 的删除事件
  映射回上次成功路径。
- `providerParentId`：远端父目录 ID；用于目录变化和删除事件映射。
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

## SyncJobState

- `providerId`、`providerName`、`targetName`：用于统一呈现，不含凭据或远端 URI。
- `status`: running、succeeded、failed、cancelled、interrupted。
- `startedAt`、`finishedAt`、`completed`、`total`、`message`：可恢复的任务进度与摘要。
- `summary`: uploaded、downloaded、unchanged、conflicts。
- `issues`: 最多五项脱敏文件级错误；系统通知仅提示在应用内查看详情。

每个 Vault 只有一个活动 `SyncJobState`。该状态为设备本地、可重建元数据，不能进入 Vault 或同步范围。

## SyncContentSource

- `size`：内容总字节数。
- `read(offset, maxBytes)`：按偏移读取有限大小的数据块。
- `close()`：释放文件或网络资源；成功与失败路径都必须调用。

## ProviderCredential

登录状态优先保存为系统安全存储中的引用；系统能力不足时仅在当前进程保留，
应用重启后重新认证。访问令牌、刷新令牌和密码不得进入普通配置文件。
