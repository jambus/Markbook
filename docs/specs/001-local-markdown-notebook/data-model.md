# Data Model: 本地笔记本

## Notebook

- `root`: 用户通过平台目录选择能力授权的 Vault；正文不得以应用沙箱副本作为事实源。
- `dailyNoteDirectory`: Vault 内可配置的每日笔记相对目录。
- 文件布局、附件目录、回收站、事务标记和冲突副本统一遵循
  [`docs/contracts/vault-contract.md`](../../contracts/vault-contract.md)，本模型不重复定义路径常量。

## Note

- `relativePath`: 笔记在 Vault 内的稳定相对路径，也是跨端识别笔记的依据。
- `title`: 从 Markdown 首个一级标题派生。
- `content`: UTF-8 Markdown 全文。
- `modifiedAt`: 文件系统修改时间，仅用于排序，不作为冲突唯一依据。

## Attachment

- `captureId`: 同一次拍摄的原图和校正图共享的标识。
- `relativePath`: 相对笔记本根目录的路径。
- `mediaType`: MIME 类型。
- `contentHash`: 用于去重与同步校验。

## Derived Index

保存文件路径、标题、分词结果和内容摘要。任何时候都必须能删除后从文件重建。
