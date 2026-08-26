# Markbook 项目文档

本目录是 Markbook 的唯一文档入口，采用 GitHub Spec Kit 的规格驱动方式，
执行顺序为：

`constitution → specify → clarify → plan → tasks → analyze → implement`

## 目录

```text
docs/
├── constitution.md                    # 不可协商的项目原则
├── RELEASE_NOTES.md                   # 版本范围、发布状态与迭代记录
├── product/
│   ├── vision.md
│   ├── roadmap.md
│   └── clarifications.md
├── design/                            # 跨端体验基线
│   ├── design_principles.md
│   ├── components.md
│   ├── note_editor.md
│   ├── capture_flow.md
│   └── review_checklist.md
└── specs/
    ├── 001-local-markdown-notebook/   # 能力域
    ├── 002-camera-attachments/        # 能力域
    ├── 003-remote-sync/               # 能力域
    ├── 004-harmony-4-5-compatibility/ # 双端契约与迁移
    ├── 005-harmonyos4-android-apk/    # 平台交付，优先
    ├── 006-harmonyos5-6-native-hap/   # 平台交付，后续
    ├── 007-design-knowledge-base/     # 横切基线
    └── 008-android-implementation-hardening/ # 加固，针对 005 已交付实现
```

## 规格分类

编号目录按职责分为四类，新增规格前先确定它属于哪一类：

| 类别 | 编号 | 定义什么 | 验收对象 |
| --- | --- | --- | --- |
| 能力域 | 001–003 | 用户可获得的功能，与平台无关 | 功能行为 |
| 平台交付 | 004–006 | 某一端如何落地能力域与共享契约 | 端上可安装产物 |
| 横切基线 | 007 | 所有端与所有规格共同遵守的约束 | 其他规格的合规性 |
| 加固 | 008 | 已交付实现与既有规格之间的偏差 | 缺陷消除与不回归 |

能力域规格回答“做什么”，平台交付规格回答“这一端怎么做”，横切基线规格回答
“所有改动都必须满足什么”，加固规格回答“已经做出来的东西哪里不符合前三者”。

## 加固类规格的使用规则

加固规格用于既有实现与已确认需求之间的偏差，不用于承载新功能。

- 加固规格**不新增用户可见能力**。一旦讨论中出现新能力，拆到对应的能力域
  规格，不要放进加固规格。
- 加固规格的每条 `FR-` 必须能追溯到它所修复的原始约束来源：能力域规格的功能
  需求、`004` 的 Vault 契约，或 `design/` 的体验规则。加固规格自身不产生新的
  产品约束。
- 加固规格的 `plan.md` 必须包含缺陷归因表（现象 → 根因），使每项任务都对应一个
  可复现的问题，而不是主观改进。
- 缺陷若同时存在于两端，加固规格只覆盖本端；另一端在其平台交付规格下另立任务，
  避免单端修复被误认为跨端已修复。
- 加固完成后，若暴露出原规格描述不足，需回头补充被违反的那条原始需求，而不是
  只在加固规格里记录。

`008` 即按此规则建立：它只处理 `005` 已交付的 Android 实现中违反 `001`、`002`、
`003`、`004` 契约与 `design/` 体验规则的部分，不改变任何功能范围。

## 版本与发布记录

当前 Android APK 以 `0.1.0` 作为首个开发基线。版本范围、验证状态和每次迭代摘要统一
维护在 [`RELEASE_NOTES.md`](RELEASE_NOTES.md)；安装包中的 `versionName`、单调递增的
`versionCode`、平台规格和 release notes 必须在候选版本生成前保持一致。

## 每个编号目录的文件

- `spec.md`：用户价值、场景、需求与验收标准，只描述“做什么”。
- `plan.md`：技术方案、工程边界、风险与 Constitution Check。
- `tasks.md`：按依赖顺序排列的可执行任务；`[P]` 表示可并行。
- `research.md`：需要调研或形成决策记录的技术问题。
- `data-model.md`：实体、字段、状态及生命周期。
- `contracts/`：Provider、协议或模块间契约。
- `quickstart.md`：关键验收路径与手工验证步骤。

## 交叉引用规则

`004` 定义双端数据契约和迁移，`005` 优先交付 HarmonyOS 4 Mate 60 Android
APK，`006` 随后对齐 HarmonyOS 5/6 原生 HAP；`001` 至 `003` 的功能需求默认
适用于两端。

`design/` 是开发前和设计评审时必须查阅的跨端体验基线。它规定用户可见的行为、
组件状态和评审标准；数据格式、持久化与同步规则仍以相应规格及其契约为准。新增
或改变用户流程时，先更新对应设计文档，再同步受影响功能规格的 `spec.md`、
`plan.md` 与 `tasks.md`。

发生冲突时的优先级为：`004` 的 Vault 契约 → 目标平台可用性 → `design/` 体验
基线 → 单个规格的实现偏好。

需求变更时先更新 `spec.md`，再同步计划与任务。未完成澄清的问题统一记录在
`product/clarifications.md`，不得在实现阶段静默假设。
