# Feature Specification: Mate 双端适配

**Status**: In Progress
**Priority**: P1
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments

## Product Decision

Markbook 提供两套安装包：

- HarmonyOS 4：Android APK，首要实现与验收对象为 Mate 60。
- HarmonyOS 5/6：原生 HarmonyOS Stage HAP，在 APK 核心闭环稳定后对齐。

两套客户端可以使用不同 UI 和平台 API，但必须直接读写同一种 Obsidian Vault，
不得通过 Markbook 专有数据库交换数据。

共享文件布局、附件命名、事务恢复、回收站和冲突副本规则详见
[`contracts/vault-contract.md`](contracts/vault-contract.md)。

## User Stories

### US1 HarmonyOS 4 快速记录（P1）

Mate 60 用户安装 APK 后，可以选择已有 Vault，编辑每日笔记，拍照并完成矩形裁剪
或四点透视校正；重启应用后笔记和图片仍可打开。

**Acceptance**: 在 HarmonyOS 4 Mate 60 上完成选择 Vault、编辑、自动保存、拍照、
校正、强制结束和恢复的完整流程。

### US2 HarmonyOS 5/6 原生使用（P2）

用户在 HarmonyOS 5/6 Mate 手机上安装 HAP，可以完成与 APK 相同的核心记录流程。

**Acceptance**: HAP 通过相同的本地笔记与拍照验收用例，Markdown 结果与 APK 一致。

### US3 系统升级后延续 Vault（P1）

用户从 HarmonyOS 4 升级到 5/6 后，可以在 HAP 中重新选择原 Vault 并继续编辑，
无需导入、转换或复制为 Markbook 私有格式。

## Functional Requirements

- **FR-401** Android APK 必须作为首个可用交付物，支持 Mate 60 HarmonyOS 4。
- **FR-402** HarmonyOS 5/6 必须交付独立 Stage HAP，不依赖 Android 兼容层。
- **FR-403** 两端必须遵循相同的 UTF-8 Markdown、Vault 目录、相对附件链接、
  回收站和冲突副本契约。
- **FR-404** Android 端必须通过系统文件选择能力取得 Vault 访问权，并在重启后
  恢复已授权目录；权限失效时提示重新选择，不得创建静默分叉副本。
- **FR-405** 两端必须调用各自平台的系统相机入口，并将返回内容复制到 Vault 后
  再写入 Markdown。
- **FR-406** 两端必须支持所见即所得编辑、持续自动保存、手动保存、直接插图、
  矩形裁剪、四点透视校正以及同时保留原图和校正图。
- **FR-407** 两端生成的同一功能结果必须通过共享的格式样例测试。
- **FR-408** 令牌和凭据必须使用对应平台的系统安全存储；低版本缺少能力时仅在
  进程内保留并要求重新连接。
- **FR-409** 页面必须适配 Mate 手机竖横屏、安全区域、字体放大和不同屏幕密度。
- **FR-410** 发布前必须分别记录 APK 与 HAP 的机型、系统版本、安装包版本、
  权限状态和验收结果。

## Delivery Priority

1. HarmonyOS 4 Mate 60 Android APK：本地笔记与拍照闭环。
2. HarmonyOS 5/6 原生 HAP：核心功能对齐与跨端 Vault 验证。
3. 两端 Google Drive/NAS 同步。

## Out of Scope

- 在两端共享 UI 或平台接入代码。
- 从 APK 自动迁移应用私有数据到 HAP；用户数据必须位于用户选择的 Vault。
- 为 HarmonyOS 5/6 继续发布 Android APK。
