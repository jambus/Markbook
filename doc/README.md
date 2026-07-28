# Markbook 规格文档

本目录采用 GitHub Spec Kit 的规格驱动方式，执行顺序为：

`constitution → specify → clarify → plan → tasks → analyze → implement`

## 目录

```text
doc/
├── constitution.md
├── product/
│   ├── vision.md
│   ├── roadmap.md
│   └── clarifications.md
└── specs/
    ├── 001-local-markdown-notebook/
    ├── 002-camera-attachments/
    ├── 003-remote-sync/
    ├── 004-harmony-4-5-compatibility/ # 双端契约与迁移
    ├── 005-harmonyos4-android-apk/    # 优先交付
    └── 006-harmonyos5-6-native-hap/  # 后续原生对齐
```

每个编号目录代表一个可独立验收的能力域：

- `spec.md`：用户价值、场景、需求与验收标准，只描述“做什么”。
- `plan.md`：技术方案、工程边界、风险与 Constitution Check。
- `tasks.md`：按依赖顺序排列的可执行任务；`[P]` 表示可并行。
- `research.md`：需要调研或形成决策记录的技术问题。
- `data-model.md`：实体、字段、状态及生命周期。
- `contracts/`：Provider、协议或模块间契约。
- `quickstart.md`：关键验收路径与手工验证步骤。

`004` 定义双端数据契约和迁移，`005` 优先交付 HarmonyOS 4 Mate 60 Android
APK，`006` 随后对齐 HarmonyOS 5/6 原生 HAP；`001` 至 `003` 的功能需求默认
适用于两端。

需求变更时先更新 `spec.md`，再同步计划与任务。未完成澄清的问题统一记录在
`product/clarifications.md`，不得在实现阶段静默假设。
