# Feature Specification: HarmonyOS 4/5 兼容

**Status**: In Progress  
**Priority**: P1

## User Stories

### US1 同一安装包运行（P1）

用户可以在 HarmonyOS 4.0 和 5.0 设备安装同一 HAP，并完成本地笔记、拍照和
手动同步的核心流程。

### US2 安全降级（P1）

低版本缺少安全存储能力时，应用不明文保存令牌或 NAS 密钥，而是在重启后要求
重新连接。

## Functional Requirements

- **FR-401** `compatibleSdkVersion` 必须为 `4.0.0(10)`，目标为 `5.0.0(12)`。
- **FR-402** 本地编译 SDK 可高于运行目标；当前工程使用已安装的
  `6.0.1(21)` 编译 SDK，不改变 API 10/12 兼容与目标约束。
- **FR-403** 核心运行路径不得无保护调用 API 11 及以上接口。
- **FR-404** 拍照必须使用 API 10 可用的系统 Ability 动作。
- **FR-405** API 10 不得持久化 OAuth refresh token 或 NAS secret key。
- **FR-405** 发布前必须分别在 HarmonyOS 4 和 5 真机完成冒烟验收。
