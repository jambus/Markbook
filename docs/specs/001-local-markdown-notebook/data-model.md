# Data Model: 本地笔记本

## Notebook

- `rootPath`: 应用沙箱或用户授权目录。
- 固定子目录：`notes/`、`attachments/`、`.trash/`、`.markbook/index/`。

## Note

- `id`: 稳定唯一标识，首版可使用 UUID。
- `title`: 从 Markdown 首个一级标题派生。
- `content`: UTF-8 Markdown 全文。
- `modifiedAt`: 文件系统修改时间，仅用于排序，不作为冲突唯一依据。

## Attachment

- `id`: 唯一文件名。
- `relativePath`: 相对笔记本根目录的路径。
- `mediaType`: MIME 类型。
- `contentHash`: 用于去重与同步校验。

## Derived Index

保存文件路径、标题、分词结果和内容摘要。任何时候都必须能删除后从文件重建。
