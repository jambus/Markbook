# Feature Specification: OneDrive 与 NAS 同步

**Status**: Draft / Needs Clarification  
**Priority**: P2  
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments

## User Stories

### US1 配置远端（P1）

用户可以连接 OneDrive 或 S3 兼容 NAS，选择远端 Markbook 前缀，并在明确同意后
开始同步。

### US2 手动双向同步（P1）

用户触发同步后，本地和远端新增、修改与删除得到一致处理，并看到进度和结果。

**Acceptance**: 在两台设备分别修改同一笔记后同步，两份内容均保留且结果可见。

### US3 离线与中断恢复（P1）

网络中断或应用退出不会损坏本地文件；下次同步从安全状态继续。

## Functional Requirements

- **FR-301** Provider 必须实现统一的枚举、下载、上传、删除和增量状态契约。
- **FR-302** OneDrive 必须使用 Authorization Code + PKCE 和最小权限。
- **FR-303** NAS 首版使用 S3 兼容 HTTPS 接口和 SigV4，不允许忽略证书错误。
- **FR-304** 首版必须双向同步 `notes/` 中的 Markdown 和
  `attachments/` 中的附件；本机 `.markbook/` 元数据不得上传。
- **FR-305** 双边修改不得静默覆盖，默认保留冲突副本。
- **FR-306** 同步必须可取消、可重试并提供文件级错误摘要。
- **FR-307** 只有完整成功后才能更新同步基线。
- **FR-308** HarmonyOS 4 上凭据和令牌只保留在进程内；不得降级为明文持久化。
  HarmonyOS 5 可在后续兼容实现中使用 Asset Store，日志始终必须脱敏。
- **FR-309** 首次上传前必须展示数据目标和范围并取得用户确认。
