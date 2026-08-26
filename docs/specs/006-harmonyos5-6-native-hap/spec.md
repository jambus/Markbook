# Feature Specification: HarmonyOS 5/6 原生 HAP

**Status**: In Progress
**Priority**: P2
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments,
004-harmony-4-5-compatibility, 005-harmonyos4-android-apk

## Goal

在 HarmonyOS 4 APK 核心闭环稳定后，为 HarmonyOS 5/6 提供原生 Stage HAP，
并保证原 Vault 可以继续使用。

## Functional Requirements

- **FR-601** HAP 必须使用 HarmonyOS 原生 Stage 模型和平台 API。
- **FR-602** 必须实现与首个 APK 相同的本地笔记和拍照 P1 能力。
- **FR-603** 必须通过规格 `004` 的共享 Markdown、附件和图片样例。
- **FR-604** 用户必须可以重新选择 APK 使用过的 Vault，无需数据格式转换。
- **FR-605** 平台权限或能力不同导致无法完全一致时，必须提供可见降级并记录差异。
- **FR-606** 必须分别在 HarmonyOS 5 与 HarmonyOS 6 Mate 手机上完成真机验收。

## Out of Scope

- 复用 Android UI 或将 APK 包装为 HAP。
- 在 APK 核心闭环完成前抢先扩展 HAP 独有功能。
