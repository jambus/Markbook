# Implementation Plan: 共享 Vault 与跨端互操作

## Strategy

采用双工程、单数据契约。Android 和 HarmonyOS 的实现及平台验收分别由 `005`、`006`
负责，本计划只建立共享契约、样例、互操作验证和跨端发布矩阵。

首版不抽取跨语言运行时代码。可共享部分限定为规格、Markdown/Vault 格式样例、
图片变换输入输出样例和验收用例，从而降低两端耦合。

## Planned Structure

```text
Markbook/
├── android/                  # HarmonyOS 4 APK，Kotlin/Gradle
│   └── app/src/
├── entry/                    # HarmonyOS 5/6 HAP，ArkTS/Hvigor
├── shared-testdata/          # 两端共同读取的 Markdown 与图片样例
├── docs/contracts/           # 跨客户端唯一 Vault 契约
└── docs/specs/               # 产品规格、计划和任务
```

## Interoperability Boundaries

- Android 平台 API、构建和设备适配属于 `005`。
- HarmonyOS 平台 API、构建、签名和设备适配属于 `006`。
- 共享契约：UTF-8 `.md`、Vault 原目录、POSIX 风格相对链接、
  `assets/<note-file-stem>/`、`<HHmmss>-<xxxx>-o.<ext>` 与
  `<HHmmss>-<xxxx>-c.<ext>` 成对命名及 `<HHmmss>-<xxxx>-v.{mp4|3gp}` 视频命名、冲突副本和回收站约定；两端继续兼容已有
  `attachments/` 中的 UUID 命名历史文件。
- 同步 Provider 在两端分别实现，但必须通过同一组文件级契约测试。

## Delivery Phases

1. 固化共享 Vault 契约和跨端样例。
2. 用共享样例验证 APK 生成的 Markdown、附件和恢复结果。
3. 让 HAP 重新授权并继续编辑 APK 生成的 Vault。
4. 比较两端输出，并在电脑 Obsidian 中验证无需迁移。
5. 验证跨端冲突、本地回收站语义和同步边界。
6. 建立双安装包版本、签名、设备和回归矩阵。

## Constitution Check

- Vault 文件仍是唯一事实源，不引入必须迁移的数据库。
- 两端写入操作均采用临时文件、原子替换和失败回滚。
- 相机返回内容落盘与 Markdown 链接插入保持事务式语义。
- 凭据使用平台安全存储，不进入 Vault、日志或普通配置。
- 每个交付物均需在对应系统真机验收。

## Risks

- HarmonyOS 4 Android 兼容层对目录授权、相机 URI、后台任务和 WebView 的行为
  可能与标准 Android 设备不同，必须以 Mate 60 真机为准。
- 手机系统升级后 APK 私有存储不会自动供 HAP 读取，因此任何用户内容都不能只
  存在应用沙箱；Vault 重新授权流程必须清晰。
- 两端独立实现可能产生 Markdown、附件命名和图片变换差异，应以共享样例阻止漂移。
