# Feature Specification: OneDrive 与 NAS 同步

**Status**: Draft / Needs Clarification  
**Priority**: P2  
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments

## User Stories

### US1 配置远端（P1）

用户可以连接 OneDrive 或 WebDAV/NAS，选择远端 Markbook 目录，并在明确同意后
开始同步。

### US2 手动双向同步（P1）

用户触发同步后，本地和远端新增、修改与删除得到一致处理，并看到进度和结果。

**Acceptance**: 在两台设备分别修改同一笔记后同步，两份内容均保留且结果可见。

### US3 离线与中断恢复（P1）

网络中断或应用退出不会损坏本地文件；下次同步从安全状态继续。

## Functional Requirements

- **FR-301** Provider 必须实现统一的枚举、下载、上传、删除和增量状态契约。
- **FR-302** OneDrive 必须使用 Authorization Code + PKCE 和最小权限。
- **FR-303** NAS 首选 WebDAV over HTTPS，并校验证书。
- **FR-304** 系统必须双向同步 Markdown、附件和受支持的元数据。
- **FR-305** 双边修改不得静默覆盖，默认保留冲突副本。
- **FR-306** 同步必须可取消、可重试并提供文件级错误摘要。
- **FR-307** 只有完整成功后才能更新同步基线。
- **FR-308** 凭据必须保存在 HarmonyOS 安全存储，日志中必须脱敏。
- **FR-309** 首次上传前必须展示数据目标和范围并取得用户确认。

