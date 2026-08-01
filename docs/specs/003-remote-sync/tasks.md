# Tasks: 远端同步

同步功能在 HarmonyOS 4 APK 核心闭环完成后启动，并分别在 APK 与 HAP 实现。
现有已完成项仅代表协议模型或 HAP 侧基础实现，不代表双端交付完成。

## Phase 1 协议无关核心

- [x] T301 定义 `SyncProvider`、`SyncResult` 和冲突动作。
- [x] T302 添加单边/双边变化冲突决策单测。
- [x] T303 定义文件快照、基线、删除标记和操作计划模型。
- [x] T304 实现 SHA-256 内容摘要和本地目录扫描。
- [x] T305 [P] 实现内存模拟 Provider 与故障注入测试。
- [x] T306 实现仅在完整成功后提交基线的 `SyncEngine`。

## Phase 2 Google Drive

- [x] T307 调研并验证 Google Drive 官方账号授权与 Drive scope；已配置 Mate 60 测试用 Android OAuth 客户端。
- [x] T308 Android：使用 Google Play 服务账号授权、短期令牌获取、重新认证和脱敏错误处理。
- [x] T309 Android：实现 Drive 文件夹选择、递归目录映射和全量远端枚举。
- [x] T310 Android：实现首次确认后的双向上传、下载、冲突保留和成功后完成记录。
- [ ] T310a Android：补齐 Drive changes 游标、条件写入、删除回收站、可恢复上传与范围下载。

## Phase 3 NAS

- [ ] T311 调研威联通 TS-251D 与极空间可共同支持的通用协议。
- [ ] T312 [P] 在局域网和外网分别验证 NAS 登录、目录读写、断点和条件写入。
- [ ] T313 实现 NAS 配置、连接、双向同步和取消 UI。
- [ ] T314 [P] 建立两台目标 NAS 的协议兼容矩阵。

## Phase 4 体验与验收

- [x] T315 Android：实现远端配置、首次确认、进度、取消和结果 UI。
- [ ] T316 实现脱敏错误摘要、退避重试和重新认证。
- [ ] T317 运行双设备冲突、弱网、中断、删除恢复和重新认证验收。
