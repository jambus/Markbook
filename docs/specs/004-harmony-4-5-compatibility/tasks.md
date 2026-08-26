# Tasks: Mate 双端适配

Android APK 实现由规格 `005` 跟踪，HarmonyOS 5/6 HAP 实现由规格 `006`
跟踪。本清单只负责两端共同契约和迁移验收。

- [x] T401 定义 Vault、附件、回收站、冲突副本和日期笔记路径规范，见
  `docs/contracts/vault-contract.md`。
- [x] T402 建立 `shared-testdata/`，覆盖中文、双向链接、标签和图片相对路径。
- [ ] T403 建立两端 Markdown 读写、图片命名和透视输出一致性测试。
- [ ] T404 在电脑 Obsidian 中打开两端生成的 Vault 并验证无需格式迁移。
- [ ] T405 验证 HarmonyOS 4 到 5/6 后重新授权原 Vault 并继续编辑。
- [ ] T406 验证跨端冲突保留、本地 `.trash/` 排除同步、远端可恢复删除、取消和离线恢复。
- [ ] T407 建立双安装包版本、签名、发布渠道和设备回归记录。
