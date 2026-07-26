# Implementation Plan: 远端同步

## Architecture

`SyncEngine` 比较本地快照、远端变更和上次成功基线，生成操作计划；
`SyncProvider` 只负责协议 I/O；`SyncConflictResolver` 负责无协议依赖的冲突
决策。OneDrive 和 S3 Provider 共用 HTTP、基线与文件内容流契约。

## Constitution Check

- [x] Provider 与本地仓储边界已定义。
- [x] 冲突默认保留两份。
- [x] API 10 上凭据仅驻留内存，不做不安全持久化。
- [x] 增量状态只在整轮成功且两端摘要一致后提交。
- [ ] OneDrive 与 NAS 真实环境通过故障注入测试。

## Delivery Order

1. 定义文件快照、同步基线、操作计划和 Provider 契约。
2. 实现本地模拟 Provider，完成冲突和中断测试。
3. 实现 OneDrive PKCE、Graph delta 和可恢复上传。
4. 实现 S3 SigV4、ListObjectsV2 分页、ETag 条件写入和 HTTPS 校验。
5. 增加配置、进度、取消、错误摘要和手动同步 UI。
6. 完成多设备、弱网、令牌失效和 NAS 兼容性验证。
