# Implementation Plan: 本地 Markdown 笔记本

## Technical Context

- HarmonyOS Stage model，ArkTS/ArkUI，兼容 API 10，目标 API 12。
- 数据根目录为应用沙箱 `files/Markbook/`。
- Markdown 文件是事实源；索引仅用于搜索和列表性能。

## Constitution Check

- [x] 使用普通 Markdown 文件与相对附件路径。
- [x] 仓储层独立于 UI 和同步 Provider。
- [ ] 删除流程具备回收站和恢复验证。
- [x] 已实现防抖自动保存、返回/切页刷新和失败状态。
- [ ] 自动保存与异常恢复通过真机测试。
- [x] 1,000 篇笔记搜索逻辑具备 500 ms 自动化指标门槛。
- [ ] 在目标真机确认搜索输入无明显卡顿。

## Design

`NotebookRepository` 负责目录和原子文件写入；`MarkdownDocument` 处理标题等
纯文本规则；UI 通过 ViewModel/状态层调用仓储，不直接访问文件。保存采用
“临时文件 → flush → rename”替换目标文件。搜索索引可重建，不参与导出。

## Phases

1. 修正现有仓储为原子保存并补齐异常恢复。
2. 增加自动保存、删除/回收站与恢复。
3. 增加 Markdown 预览和可重建全文索引。
4. 完成单测、1,000 篇性能测试及手机/平板验收。
