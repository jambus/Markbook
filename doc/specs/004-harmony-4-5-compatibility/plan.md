# Implementation Plan: Mate 手机适配

## Strategy

使用单一 Stage HAP，以当前可用 SDK 编译，并以 Mate 60 及后续 Mate 手机作为
主要验收对象。最低 HarmonyOS/API 版本在 Mate 60 真机验证后确定；不为保持旧版
系统而牺牲透视校正、文件访问、同步和安全存储体验。

## Compatibility Decisions

- 使用系统能力适配层隔离 Mate 系列不同 HarmonyOS 版本差异。
- Google Drive 与 NAS 作为互斥同步 Provider。
- 登录状态优先保存到系统安全存储。
- 保持当前构建约束配置，直到 Mate 60 真机确定最低版本。
- 使用 ArkUI 的弹性布局和安全区域 API，覆盖 Mate 系列的屏幕密度、横竖屏和
  字体缩放变化；不得按单一机型写死尺寸。
- 以 Mate 60 作为基线设备，至少增加一台后续 Mate 手机验证系统升级后的行为。

## Risks

系统相机返回 URI、Google 授权回调、四点透视校正、屏幕适配和 NAS 通用协议必须
在 Mate 60 与后续 Mate 手机真机验证。本机仅安装 API 21 SDK，当前构建不能替代
Mate 设备上的真实验收。
