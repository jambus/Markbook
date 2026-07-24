# Tasks: 远端同步

## Phase 1 协议无关核心

- [x] T301 定义 `SyncProvider`、`SyncResult` 和冲突动作。
- [x] T302 添加单边/双边变化冲突决策单测。
- [x] T303 定义文件快照、基线、删除标记和操作计划模型。
- [x] T304 实现 SHA-256 内容摘要和本地目录扫描。
- [x] T305 [P] 实现内存模拟 Provider 与故障注入测试。
- [x] T306 实现仅在完整成功后提交基线的 `SyncEngine`。

## Phase 2 OneDrive

- [ ] T307 调研并验证 HarmonyOS 系统浏览器 PKCE 回调。
- [x] T308 实现 Asset Store 分片令牌存储、refresh token 轮换和会话恢复。
- [x] T309 实现 Graph delta 分页、目录 ID 映射与 opaque delta link 持久化。
- [x] T310 实现条件小文件上传、图片可恢复上传和范围下载。

## Phase 3 WebDAV/NAS

- [x] T311 验证 Network Kit 自定义 WebDAV 方法支持（API 12 不支持
  `PROPFIND`/`MOVE`，需原生传输层或调整 NAS 首版协议）。
- [ ] T312 实现 PROPFIND/ETag 枚举和条件写入。
- [ ] T313 实现临时文件加 MOVE 的原子远端提交。
- [ ] T314 [P] 建立群晖、威联通和通用 WebDAV 兼容矩阵。

## Phase 4 体验与验收

- [ ] T315 实现远端配置、首次确认、进度和取消 UI。
- [ ] T316 实现脱敏错误摘要、退避重试和重新认证。
- [ ] T317 运行双设备冲突、弱网、中断和令牌失效验收。
