# Markbook

Markbook 是一个面向 HarmonyOS 的本地优先 Markdown 笔记本。笔记以普通
`.md` 文件保存，拍摄的图片会立即复制到同一笔记本的 `attachments/`
目录，并以相对路径插入正文。

产品规格、技术计划和任务清单统一维护在 [`doc/`](doc/README.md)，按
Spec Kit 的规格驱动流程推进。

## 当前能力

- 创建、浏览和编辑本地 Markdown 笔记。
- 通过系统 CameraPicker 拍照，避免自行维护相机预览与生命周期。
- 在应用沙箱中使用可迁移的 `notes/` + `attachments/` 目录结构。
- 提供 `SyncProvider` 边界，为 OneDrive、WebDAV 和 NAS 同步预留统一入口。

## 工程环境

使用 DevEco Studio 5.0.0 Release 或更高版本打开项目，并安装 HarmonyOS
API 12 SDK。首次打开后让 IDE 完成 ohpm/Hvigor 同步。

常用命令（DevEco 终端）：

```bash
ohpm install
hvigorw assembleHap
hvigorw test
```

当前机器未安装 DevEco Studio 命令行工具，因此提交前仍需在 DevEco Studio
中执行签名、真机拍照和 HAP 构建验证。

## 数据布局

运行时笔记本位于应用沙箱的 `files/Markbook/`：

```text
Markbook/
├── notes/
│   └── <timestamp>.md
└── attachments/
    └── photo-<timestamp>.jpg
```

同步实现应整体同步该目录，并保留 Markdown 中的相对图片链接。
