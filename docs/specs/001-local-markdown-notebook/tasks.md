# Tasks: 本地 Markdown 笔记本

当前已完成项表示现有 HarmonyOS HAP 实现状态。Android APK 对应实现由 `005`
跟踪，HAP 对齐由 `006` 跟踪；本规格只有两端均通过后才可标记完成。

## Phase 1 数据可靠性

- [x] T001 建立 `Note` 与 `MarkdownDocument` 模型。
- [x] T002 支持选择已有 Obsidian Vault，并建立 Markdown 读写仓储。
- [x] T003 在 `NotebookRepository` 实现临时文件原子替换。
- [ ] T004 [P] 添加空标题、重名和 Unicode 单测。
- [ ] T005 添加异常写入恢复测试。

## Phase 2 编辑工作流

- [x] T006 建立笔记列表、创建和编辑页面。
- [x] T007 实现 600 ms 防抖自动保存与未保存/保存失败状态。
- [ ] T008 实现回收站、恢复和永久删除。
- [ ] T009 [P] 完善空状态、错误状态和无障碍标签。

## Phase 3 浏览与搜索

- [ ] T010 选型并实现符合 CommonMark 的预览器。
- [x] T011 实现从 Markdown 文件重建的标题/正文内存搜索索引。
- [x] T012 [P] 建立 1,000 篇笔记、500 ms 上限的搜索指标测试。
- [x] T015 Android：实现撤销、重做、标题、加粗、斜体、标签、链接和表格的图标 Markdown 工具栏。
- [ ] T016 HAP：对齐 T015 的 Markdown 输出、选区、取消和无障碍语义。

## Phase 4 验收

- [ ] T013 分别在 HarmonyOS 4 APK 与 HarmonyOS 5/6 HAP 验证选择 Vault、创建
  每日笔记、编辑、重启和恢复。
- [x] T014 完成 HAP 构建检查；Hypium 真机执行待设备连接后完成。
