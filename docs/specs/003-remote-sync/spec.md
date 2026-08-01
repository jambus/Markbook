# Feature Specification: Google Drive 与 NAS 同步

**Status**: In progress (Android Google Drive manual sync)
**Priority**: P1
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments

## User Stories

### US1 配置远端（P1）

用户可以连接 Google Drive 或 NAS，选择其中一个远端同步目标，并在明确同意后
开始同步。

### US2 手动双向同步（P1）

用户触发同步后，本地和远端新增、修改与删除得到一致处理，并看到进度和结果。

**Acceptance**: 在两台设备分别修改同一笔记后同步，两份内容均保留且结果可见。

### US3 离线与中断恢复（P1）

网络中断或应用退出不会损坏本地文件；下次同步从安全状态继续。

### US4 选择性自动同步（P1）

用户默认手动触发同步，也可以开启自动同步提示；离线编辑后网络恢复时，应用
提示用户确认同步。

### US5 Android Google Drive Vault（P1）

Mate 60 用户在 Android APK 中登录已有 Google 账号，选择一个已有的 Google Drive
文件夹作为 Vault 根目录。手机 Markbook 与电脑 Obsidian 围绕该目录交换 Markdown
和图片，不需要把文件复制进 Markbook 私有空间。

## Functional Requirements

- **FR-301** Provider 必须实现统一的枚举、下载、上传、删除和增量状态契约。
- **FR-302** Google Drive Provider 必须使用官方授权流程和最小权限，不能要求
  用户把密码交给 Markbook。
- **FR-303** NAS Provider 必须支持威联通 TS-251D 和极空间的局域网、外网访问；
  具体通用协议须通过真实设备验证，不得依赖单一厂商私有 API。
- **FR-304** 首版必须双向同步整个 Vault 的 Markdown、照片附件和回收站内容，
  但不得主动同步 `.obsidian/` 配置目录或本机索引元数据。
- **FR-305** 双边修改不得静默覆盖，默认保留冲突副本。
- **FR-306** 同步必须可取消、可重试并提供文件级错误摘要。
- **FR-307** 只有完整成功后才能更新同步基线。
- **FR-308** 登录状态必须优先保存到系统安全存储；能力不足时只在当前进程保留，
  不得降级为明文持久化，日志始终必须脱敏。
- **FR-309** 首次上传前必须展示数据目标和范围并取得用户确认。
- **FR-310** Google Drive 与 NAS 必须是互斥的同步目标；切换目标前必须完成
  当前目标的同步或明确提示未同步内容。
- **FR-311** 同步必须支持增量传输、断点恢复、取消、重试和文件级错误摘要，
  面向几十 GB、较多照片的 Vault 不得每次全量上传。
- **FR-312** 删除操作必须同步到远端回收站，不得直接静默永久删除。
- **FR-313** 双边修改不得静默覆盖，默认保留带设备或时间信息的冲突副本。
- **FR-314** Android Google Drive 首阶段使用 Google Play 服务选择账号并以官方 Drive
  REST API 访问；只保存远端文件夹标识、基线和非敏感显示信息，访问令牌由系统服务管理。
- **FR-315** Android 首阶段必须让用户选择已有的 Drive 文件夹，并在首次实际
  写入前显示本地 Vault、Drive 文件夹和包含范围的确认页。
- **FR-316** Android 手动同步必须比较整个 Vault 中的 Markdown、`assets/` 与
  `.markbook/trash/`（若存在），排除 `.obsidian/`、临时文件和 Markbook 本机同步元数据。
- **FR-317** 首阶段不传播删除、不承诺断点续传或 Drive 增量游标；界面和结果必须明确
  说明这一限制。后续阶段补齐 FR-311、FR-312 的删除、增量和大文件恢复能力。
