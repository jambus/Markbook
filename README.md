# Markbook

Markbook 是一个面向 Mate 手机的本地优先 Markdown 笔记本。笔记以普通 `.md`
文件保存，拍摄的图片会复制到同一 Vault 的 `attachments/` 目录，并以相对路径
插入正文。

产品采用两套客户端：HarmonyOS 4 优先交付 Android APK，HarmonyOS 5/6 交付
原生 Stage HAP。两端共享文件格式和验收样例，不共享平台 UI 代码。

产品规格、技术计划和任务清单统一维护在 [`doc/`](doc/README.md)，按
Spec Kit 的规格驱动流程推进。

## 当前工程状态

- `entry/` 已包含 HarmonyOS HAP 的本地笔记和拍照基础实现。
- `android/` 将作为下一阶段优先建立的 HarmonyOS 4 APK 工程。
- 两端以用户选择的 Obsidian Vault 为事实源。
- `SyncProvider` 契约为 Google Drive 和 NAS 同步预留统一语义。

## 工程环境

HAP 使用 DevEco Studio 和与其配套的 HarmonyOS SDK。Android APK 工程建立后
使用项目内 Gradle Wrapper 构建。

常用命令（DevEco 终端）：

```bash
ohpm install
hvigorw assembleHap
hvigorw test
```

Android APK 与 HarmonyOS HAP 必须分别在 HarmonyOS 4 Mate 60 和 HarmonyOS 5/6
Mate 真机完成安装、文件授权、拍照和重启恢复验证。

## 数据布局

运行时笔记本位于用户选择的 Obsidian Vault；示例约定为：

```text
Vault/
├── Daily Notes/
│   └── YYYY-MM-DD.md
└── attachments/
    ├── <uuid>-original.jpg
    └── <uuid>-corrected.jpg
```

同步实现应整体同步该目录，并保留 Markdown 中的相对图片链接。
