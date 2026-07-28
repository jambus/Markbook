# Implementation Plan: HarmonyOS 5/6 原生 HAP

## Technical Context

沿用根目录 `AppScope/` 与 `entry/` Stage 工程，使用 ArkTS/ArkUI、系统文件选择、
CameraPicker 和平台安全存储。现有 HAP 实现作为基础，但交付状态以 APK 基线功能
和共享数据样例重新验收。

## Delivery Phases

1. 修复 SDK/Hvigor 配套并稳定生成可签名 HAP。
2. 对齐 APK 的 Vault、编辑、保存和拍照闭环。
3. 通过共享格式样例及 APK 生成 Vault 的互操作测试。
4. 在 HarmonyOS 5/6 Mate 手机上完成权限、布局、重启和相机回归。

## Constitution Check

- 不引入 HAP 专有笔记格式。
- 所有保存、附件和冲突路径可恢复。
- 凭据使用 HarmonyOS 系统安全存储。
- 真机验收完成前不将 HAP 标记为双端对齐。
