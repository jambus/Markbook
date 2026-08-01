# Implementation Plan: Google Drive 与 NAS 同步

## Architecture

`SyncEngine` 比较本地快照、远端变更和上次成功基线，生成操作计划；
`SyncProvider` 只负责协议 I/O；`SyncConflictResolver` 负责无协议依赖的冲突
决策。Android APK 与 HarmonyOS HAP 分别实现该边界；Google Drive 和 NAS
Provider 共用文件内容流、基线、断点与错误契约及测试样例。

## Constitution Check

- [x] Provider 与本地仓储边界已定义。
- [x] 冲突默认保留两份。
- [x] 登录状态优先使用系统安全存储，能力不足时仅驻留内存。
- [x] 增量状态只在整轮成功且两端摘要一致后提交。
- [ ] Google Drive 与两台目标 NAS 真实环境通过故障注入测试。

## Delivery Order

1. 定义文件快照、同步基线、操作计划和 Provider 契约。
2. 实现本地模拟 Provider，完成冲突和中断测试。
3. 实现 Google Drive 官方授权、增量变更和可恢复上传。
4. 调研并实现同时适配威联通 TS-251D、极空间的通用 NAS Provider，验证局域网
   与外网访问、证书和权限行为。
5. 增加配置、进度、取消、错误摘要和手动同步 UI。
6. 完成多设备、弱网、令牌失效和 NAS 兼容性验证。
