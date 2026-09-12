# Feature Specification: 项目设计知识库

**Status**: Complete  
**Priority**: P1  
**Depends on**: 001-local-markdown-notebook, 002-camera-attachments, 004-harmony-4-5-compatibility

## Goal

建立一套版本受控、可被两端客户端共同遵循的设计知识库，使每次功能开发和评审都能基于同一套体验原则、组件状态、编辑器交互与拍照流程做决策。

建设工作已经完成；日常入口和持续维护规则见 [`docs/design/README.md`](../../design/README.md)。

## User Stories

### US1 开发前获得明确体验约束（P1）

设计或开发人员实现页面和交互前，可快速查到产品原则、可复用组件和对应流程的必需行为，不必从实现代码中猜测。

**Acceptance**: 新增编辑器或拍照相关改动时，变更说明能引用相应设计文档，并完成评审清单中的相关项目。

### US2 双端体验一致（P1）

用户在 Android APK 与 HarmonyOS HAP 使用核心记录流程时，看到一致的信息层级、状态反馈和失败恢复语义，即使平台控件实现不同。

**Acceptance**: 两端的每日笔记编辑和拍照插入流程均能逐项通过设计评审清单。

## Functional Requirements

- **FR-701** 项目必须在 `docs/design/` 维护设计知识库，并以 Markdown 纳入版本控制。
- **FR-702** 知识库必须覆盖设计原则、组件规范、Markdown 编辑器交互、拍照到 Markdown 的流程和设计评审清单。
- **FR-706** 知识库必须将 Apple Human Interface Guidelines 的导航、上下文保留和目的性动效作为交互质量参考；参考不得替代 Android、HarmonyOS 的系统规范或改变跨端 Vault 契约。
- **FR-703** 所有核心流程文档必须说明正常、加载、空、错误与恢复状态，并明确用户可见反馈和不可丢失内容的边界。
- **FR-704** 设计规范不得与 Vault 相对路径、附件落盘、保存事务和跨端兼容性契约相冲突；这些规则以 `004` 的共享 Vault 契约为准。
- **FR-705** 新增或改变用户可见流程时，变更必须同步更新相关设计文档，并在代码评审或发布前完成对应清单检查。

## Out of Scope

- 固定某一端的 UI 框架、控件库或像素级视觉主题。
- 替代可执行的功能规格、测试用例或 Vault 数据契约。
- 在首版拍照流程中加入 OCR、标注、视频或云端识别能力。
