# Implementation Plan: HarmonyOS 4/5 兼容

## Strategy

使用单一 Stage HAP，以 API 12 编译、API 10 为最低兼容版本。公共功能只依赖
API 10；高版本增强必须放在可替换适配器后，且不能进入 API 10 启动路径。

## Compatibility Decisions

- 使用 `ACTION_IMAGE_CAPTURE` 代替 API 11 的 `cameraPicker`。
- 使用 `getContext` 和全局 `promptAction` 兼容 API 10。
- OneDrive refresh token 与 S3 凭据首版仅驻留内存。
- `useNormalizedOHMUrl` 设为 `false`，满足 API 10 构建约束。

## Risks

系统相机返回 URI、OAuth 浏览器回调和各 NAS 的 S3 兼容程度必须真机验证。
本机仅安装 API 21 SDK，因此当前构建是 API 10 兼容告警审计，不替代 API 10
SDK 和 HarmonyOS 4 真机验收。
