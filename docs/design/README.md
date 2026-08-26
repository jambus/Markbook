# Markbook 设计知识库

本目录是 Android APK 与 HarmonyOS HAP 的共同体验基线。数据格式、附件路径、事务恢复
和冲突规则以 [`docs/contracts/vault-contract.md`](../contracts/vault-contract.md) 为准；
设计文档只定义用户可见行为和平台间必须保持一致的语义。

## 文档职责

- [`design_principles.md`](design_principles.md)：跨端设计原则和决策顺序。
- [`components.md`](components.md)：导航、文件库、设置、同步状态和通用组件。
- [`note_editor.md`](note_editor.md)：Markdown 编辑、保存、退出和恢复。
- [`capture_flow.md`](capture_flow.md)：拍照、处理、附件落盘和插入。
- [`review_checklist.md`](review_checklist.md)：需求、代码和真机验收检查项。

## 持续维护规则

- 每项用户可见流程变更必须在实现前更新相关设计文档，再同步对应 `spec.md`、`plan.md`
  和 `tasks.md`。
- 每次核心流程候选发布前必须使用 `review_checklist.md` 完成评审，并记录平台例外、用户
  影响、临时处理和补齐计划。
- 平台差异可以改变控件表达，不能改变 Markdown、相对路径、保存时机、失败恢复和冲突语义。
- 已完成的设计知识库建设历史保留在 `007-design-knowledge-base`，日常工作不再在那里维护
  永久未完成的重复任务。
