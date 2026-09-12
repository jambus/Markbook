# Feature Specification: 共享 Vault 与跨端互操作

**Status**: In Progress
**Priority**: P1
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments

## Goal

确保 Android APK、HarmonyOS Stage HAP 和电脑 Obsidian 可以围绕同一个用户 Vault
工作，不需要导入、格式转换或 Markbook 专有数据库。本规格只定义跨端契约、迁移和
互操作验收；Android 功能实现由 `005` 管理，HAP 功能实现由 `006` 管理。

## Platform References

Markbook 提供两套独立安装包：

- HarmonyOS 4：Android APK，首要实现与验收对象为 Mate 60。
- HarmonyOS 5/6：原生 HarmonyOS Stage HAP，在 APK 核心闭环稳定后对齐。

两套客户端可以使用不同 UI 和平台 API，但必须直接读写同一种 Obsidian Vault，
不得通过 Markbook 专有数据库交换数据。

共享文件布局、附件命名、事务恢复、回收站和冲突副本规则详见
[`docs/contracts/vault-contract.md`](../../contracts/vault-contract.md)。

## User Stories

### US1 两端继续使用同一个 Vault（P1）

用户在 Android APK 中创建或编辑笔记并插入照片或视频链接后，可以在 HAP 中重新选择同一个 Vault
继续编辑，不需要导出、复制或转换。

**Acceptance**: HAP 打开 APK 已写入的测试 Vault，笔记、原图、校正图和相对链接均可用；
继续编辑后 Android APK 和电脑 Obsidian 仍可读取。

### US2 系统升级后延续 Vault（P1）

用户从 HarmonyOS 4 升级到 5/6 后，可以在 HAP 中重新授权原 Vault 并继续工作，应用
不会创建静默分叉副本。

### US3 跨端结果一致且可迁移（P1）

用户在任一客户端生成的 Markdown、附件、回收站和冲突结果都符合共享契约，并可由电脑
Obsidian 直接读取。

## Functional Requirements

- **FR-401** APK、HAP、电脑 Obsidian 和同步 Provider 必须遵循
  [`docs/contracts/vault-contract.md`](../../contracts/vault-contract.md)。
- **FR-402** 用户必须可以在 HAP 中重新授权 APK 使用过的 Vault，并继续编辑而无需
  导入、转换、重命名或复制为私有格式。
- **FR-403** 两端生成的 Markdown、附件命名、相对链接、图片/视频输出、事务恢复和冲突结果
  必须通过同一组共享样例测试。
- **FR-404** 两端权限模型不同只允许改变授权步骤；权限失效时必须要求重新授权同一
  Vault，不得创建静默分叉副本。
- **FR-405** `.trash/` 的本地保留、附件延迟清理和冲突副本结果必须在两端语义一致；
  本地 `.trash/` 不得作为远端同步载荷。
- **FR-406** APK 或 HAP 生成的测试 Vault 必须能被电脑 Obsidian 直接读取，且另一端
  继续编辑后仍保持相同文件语义。
- **FR-407** 发布前必须维护 APK/HAP 的版本、签名配置名称、机型、系统版本、权限状态、
  共享样例和真机验收矩阵。
- **FR-408** 两端必须一致处理笔记与附件 bundle 移动、新的嵌套 `assets/<stem>/` 布局、链接重写和中断恢复；Android 单端完成不能标记为跨端完成。

## Out of Scope

- Android APK 或 HAP 内部的页面、组件和平台 API 实现；分别由 `005`、`006` 管理。
- 本地笔记、拍照和同步能力本身；分别由 `001`、`002`、`003` 管理。
- 在两端共享 UI 或平台接入代码。
- 从 APK 自动迁移应用私有数据到 HAP；用户数据必须位于用户选择的 Vault。
- 为 HarmonyOS 5/6 继续发布 Android APK。
